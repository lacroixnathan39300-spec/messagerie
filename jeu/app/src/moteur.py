"""Pont entre le moteur Python (exécuté dans le navigateur par Pyodide) et l'interface.

L'interface appelle ces fonctions et reçoit des données simples (dictionnaires, listes, texte).
"""
import json
import random

from terre import economie, histoire, sauvegarde, zoom
from terre.personne import Personne
from terre.priere import TEXTES
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
    ETAT["population"] = charger_population()
    return etat()
