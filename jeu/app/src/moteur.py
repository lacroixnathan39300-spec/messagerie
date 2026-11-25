"""Pont entre le moteur Python (exécuté dans le navigateur par Pyodide) et l'interface.

L'interface appelle ces fonctions et reçoit des données simples (dictionnaires, listes, texte).
"""
import json
import random

from terre import economie, histoire, sauvegarde, zoom
from terre.personne import Personne
from terre.priere import TEXTES
from terre.monde import NOURRITURE_MAX
from terre.ressources import charger_population
from terre.simulation import Simulation
from terre.terre_reelle import charger, semer_buissons

ETAT = {"sim": None, "population": None}
NB_PERSONNES_DEPART = 30
CHEMIN_SAUVEGARDE = "/partie.json"


def nouvelle_partie(graine=1):
    monde = charger()
    rng = random.Random(graine)
    cotes = sorted(monde.points_eau)
    personnes = [Personne(i, *rng.choice(cotes)) for i in range(NB_PERSONNES_DEPART)]
    BASE["terre"] = set(monde.cases_terre())
    ETAT["sim"] = Simulation(monde, personnes, graine)
    ETAT["population"] = charger_population()
    return etat()


def _sim():
    if ETAT["sim"] is None:
        nouvelle_partie()
    return ETAT["sim"]


def avancer(tours):
    sim = _sim()
    for _ in range(tours):
        sim.tour_suivant()
    return etat()


RAYON_FOUDRE = 3
RAYON_PINCEAU = 2
BASE = {"terre": None}  # terres d'origine de la carte, pour retrouver les changements après un chargement


def agir(outil, i, j, rayon=2):
    """Pouvoir du joueur sur la case (i, j) : humain, buisson, foudre."""
    sim = _sim()
    if outil in ("humain", "buisson") and not sim.monde.est_terre(i, j):
        return {"ok": False, "message": "C'est la mer.", "etat": etat()}
    if outil == "humain":
        sim.personnes.append(Personne(sim._prochain_id, i, j))
        sim._prochain_id += 1
        message = "Une personne apparaît."
    elif outil == "buisson":
        sim.monde.nourriture[(i, j)] = NOURRITURE_MAX
        message = "Un buisson pousse."
    elif outil == "pluie":
        arrosees = 0
        for dx in range(-rayon, rayon + 1):
            for dy in range(-rayon, rayon + 1):
                if dx * dx + dy * dy > rayon * rayon:
                    continue
                c = (i + dx, j + dy)
                if sim.monde.est_terre(*c):
                    sim.monde.nourriture[c] = min(NOURRITURE_MAX, sim.monde.nourriture.get(c, 0) + 1)
                    arrosees += 1
        message = f"Il pleut sur {arrosees} cases : les buissons repoussent."
    elif outil == "feu":
        touches = [p for p in sim.vivantes()
                   if abs(p.x - i) <= rayon and abs(p.y - j) <= rayon]
        for p in touches:
            sim._mourir(p, "feu")
        for dx in range(-rayon, rayon + 1):
            for dy in range(-rayon, rayon + 1):
                sim.monde.nourriture.pop((i + dx, j + dy), None)
        message = f"Le feu ravage la zone : {len(touches)} morts, les buissons brûlent."
    elif outil == "montagne":
        posees = 0
        for dx in range(-rayon, rayon + 1):
            for dy in range(-rayon, rayon + 1):
                if dx * dx + dy * dy > rayon * rayon:
                    continue
                c = (i + dx, j + dy)
                if sim.monde.est_terre(*c) and c not in sim.monde.montagnes:
                    sim.monde.montagnes.add(c)
                    posees += 1
        message = f"Des montagnes se dressent ({posees} cases)." if posees else "Il faut de la terre pour une montagne."
    elif outil in ("terre", "mer"):
        voulu = outil == "terre"
        noyes = 0
        for dx in range(-rayon, rayon + 1):
            for dy in range(-rayon, rayon + 1):
                if dx * dx + dy * dy > rayon * rayon:
                    continue
                x, y = i + dx, j + dy
                if sim.monde.est_terre(x, y) == voulu or not (0 <= x < sim.monde.largeur and 0 <= y < sim.monde.hauteur):
                    continue
                sim.monde.poser_terre(x, y, voulu)
                if not voulu:
                    for p in sim.vivantes():
                        if p.x == x and p.y == y:
                            sim._mourir(p, "noyade")
                            noyes += 1
        message = "Une terre apparaît." if voulu else f"La mer submerge la terre ({noyes} noyés)."
    elif outil == "foudre":
        touches = [p for p in sim.vivantes()
                   if abs(p.x - i) <= RAYON_FOUDRE and abs(p.y - j) <= RAYON_FOUDRE]
        for p in touches:
            sim._mourir(p, "foudre")
        message = f"La foudre frappe : {len(touches)} morts."
    else:
        return {"ok": False, "message": "Outil inconnu.", "etat": etat()}
    return {"ok": True, "message": message, "etat": etat()}


def decider(identifiant_priere, choix):
    ok = _sim().decider(identifiant_priere, choix)
    return {"ok": ok, "etat": etat()}


def etat():
    sim = _sim()
    vivantes = sim.vivantes()
    prieres = [
        {"id": pr.identifiant, "auteur": pr.auteur, "besoin": pr.besoin, "texte": TEXTES[pr.besoin]}
        for pr in sim.prieres_en_attente()
    ]
    eau = sim._eau_active()
    modifs = [[x, y, 1 if v else 0] for (x, y), v in getattr(sim.monde, "modifs", {}).items()]
    montagnes = [list(c) for c in getattr(sim.monde, "montagnes", set())]
    return {
        "bilan": sim.bilan(),
        "personnes": [
            {"id": p.identifiant, "x": p.x, "y": p.y, "soif": round(p.soif, 1), "faim": round(p.faim, 1),
             "foi": round(p.foi, 2), "schismatique": p.schismatique}
            for p in vivantes
        ],
        "buissons": [[x, y] for (x, y), q in sim.monde.nourriture.items() if q >= 1],
        "prieres": prieres,
        "chronique": [{"tour": t, "texte": txt} for t, txt in histoire.chronique(sim)[-30:]],
        "energie": [
            {"membres": b["membres"], "besoin": b["besoin"], "production": b["production"],
             "sources": b["sources"], "statut": b["statut"]}
            for b in economie.bilan_energie(sim)
        ],
        "eau_active": eau,
        "modifs": modifs,
        "montagnes": montagnes,
    }


def zone(i, j, cote=200):
    """Zone zoomée centrée sur la case (i, j) de la Terre : types de cases et véhicules."""
    sim = _sim()
    population = ETAT["population"]
    pop = population[j][i] if population and j < len(population) else "."
    classe = 0 if pop == "." else int(pop)
    altitude = sim.monde.altitude.get((i, j), 0)
    z = zoom.construire(cote, densite=classe, altitude=altitude, graine=sim.graine + i * 1000 + j,
                        aeroport=False)
    return {
        "cote": z.cote,
        "cases": "".join(str(c) for c in z.cases),
        "batiments": [{"type": zoom.NOMS[t], "x": x, "y": y, "l": w, "h_m": h}
                      for t, x, y, w, _, h in z.batiments[:500]],
        "vehicules": [{"type": v.type, "x": v.x, "y": v.y} for v in z.vehicules],
    }


def sauver():
    sauvegarde.sauvegarder(_sim(), CHEMIN_SAUVEGARDE)
    with open(CHEMIN_SAUVEGARDE, encoding="utf-8") as f:
        return f.read()


def charger_texte(texte):
    with open(CHEMIN_SAUVEGARDE, "w", encoding="utf-8") as f:
        f.write(texte)
    ETAT["sim"] = sauvegarde.charger(CHEMIN_SAUVEGARDE, joueur=None)
    BASE["terre"] = set(charger().cases_terre())
    actuelle = set(ETAT["sim"].monde.cases_terre())
    for c in actuelle - BASE["terre"]:
        ETAT["sim"].monde.modifs[c] = True
    for c in BASE["terre"] - actuelle:
        ETAT["sim"].monde.modifs[c] = False
    ETAT["population"] = charger_population()
    return etat()
