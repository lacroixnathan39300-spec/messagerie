"""Pont entre le moteur Python (exécuté dans le navigateur par Pyodide) et l'interface.

L'interface appelle ces fonctions et reçoit des données simples (dictionnaires, listes, texte).
"""
import json
import math
import random

from terre import economie, histoire, sauvegarde, zoom
from terre.personne import AGE_ADULTE, Personne
from terre.priere import TEXTES
from terre.monde import NOURRITURE_MAX, creer_archipel, creer_ile
from terre.ressources import charger_population
from terre.simulation import Simulation
from terre.terre_reelle import charger, semer_buissons

ETAT = {"sim": None, "population": None}
NB_PERSONNES_DEPART = 30
CHEMIN_SAUVEGARDE = "/partie.json"


def nouvelle_partie(graine=1, carte="terre"):
    rng = random.Random(graine)
    if carte in ("ile", "archipel"):
        monde = creer_archipel(graine) if carte == "archipel" else creer_ile(graine)
        monde.points_eau = {c for c in monde.cases_terre()
                            if any(not monde.est_terre(c[0] + dx, c[1] + dy)
                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
        BASE["terre"] = None  # pas de carte de référence : tout est d'origine
        ETAT["carte"] = carte
    else:
        monde = charger()
        BASE["terre"] = set(monde.cases_terre())
        ETAT["carte"] = "terre"
    cotes = sorted(monde.points_eau)
    # Sur la carte terre, trois petits groupes de départ : assez serrés pour former des peuples.
    if carte == "terre":
        ancres = [rng.choice(cotes) for _ in range(3)]
        terre = set(monde.cases_terre())
        voisins = [[c for c in terre if abs(c[0] - a[0]) + abs(c[1] - a[1]) <= 2] for a in ancres]
        personnes = [Personne(i, *rng.choice(voisins[i % 3] or [ancres[i % 3]]))
                     for i in range(NB_PERSONNES_DEPART)]
    else:
        personnes = [Personne(i, *rng.choice(cotes)) for i in range(NB_PERSONNES_DEPART)]
    for p in personnes:  # le monde commence avec des adultes : sinon rien ne se passe avant 15 ans
        p.age = rng.randint(AGE_ADULTE, 45 * 365)
    ETAT["sim"] = Simulation(monde, personnes, graine)
    _reinitialiser_fleaux()
    if ETAT["carte"] == "terre":
        ETAT["deserts"] = _deserts_naturels(monde, rng)
    ETAT["forets"] = _forets_naturels(monde, rng) if carte == "terre" else []
    ETAT["rivieres"], ETAT["lacs"] = _rivieres(monde, rng) if getattr(monde, "altitude", None) else ([], [])
    ETAT["population"] = charger_population()
    return etat()


def _rivieres(monde, rng, nb_sources=250, pas_max=600):
    """Chaque rivière part d'une case haute et descend, pas à pas, vers la case voisine la plus basse.
    Elle s'arrête au bord de la mer, ou dans une cuvette : c'est alors un lac."""
    # Pas de rivières sur le continent glacé (au-delà de 66° sud) : la glace n'y coule pas en rivières.
    hauts = sorted(c for c, a in monde.altitude.items()
                   if a >= 6 and monde.est_terre(*c) and 90 - (c[1] + 0.5) * 0.5 > -66)
    if not hauts:
        return [], []
    rivieres, lacs, vues = [], [], set()
    for depart in rng.sample(hauts, min(nb_sources, len(hauts))):
        c, chemin = depart, []
        for _ in range(pas_max):
            if c in vues:
                break  # rejoint un cours déjà tracé
            chemin.append(c)
            voisins = [(c[0] + dx, c[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if any(not monde.est_terre(*v) for v in voisins):
                break  # la rivière atteint la mer
            alt = monde.altitude.get(c, 0)
            descente = [v for v in voisins if monde.altitude.get(v, 0) <= alt and v not in chemin]
            if not descente:
                lacs.append([c[0], c[1]])  # cuvette : l'eau s'accumule
                break
            c = min(descente, key=lambda v: (monde.altitude.get(v, 0), rng.random()))
        for k in chemin:
            if k not in vues:
                vues.add(k)
                rivieres.append([k[0], k[1]])
    return rivieres, lacs


def _forets_naturels(monde, rng):
    """Forêts approximatives : la forêt tropicale (près de l'équateur) et la forêt boréale (nord).
    Pas de données de végétation : une case sur deux de ces zones basses est couverte de forêt."""
    deserts = {tuple(c) for c in ETAT.get("deserts", [])}
    res = []
    bord = _bord_decale(rng)
    for (x, y) in sorted(monde.cases_terre()):
        if (x, y) in deserts or monde.altitude.get((x, y), 0) > 5:
            continue
        lat = 90 - (y + 0.5) * 0.5
        lat_effective = abs(lat) - bord[x % len(bord)]
        if lat_effective <= 10 or 50 <= lat_effective <= 68:
            if rng.random() < 0.5:
                res.append([x, y])
    return res


def _bord_decale(rng, largeur=720, amplitude=4.0, lissage=9):
    """Une limite de latitude qui ondule d'une colonne à l'autre, pour éviter les bandes droites.
    On lisse la houle sur quelques colonnes pour obtenir des courbes plutôt que des stries."""
    brut = [rng.uniform(-amplitude, amplitude) for _ in range(largeur)]
    demi = lissage // 2
    return [sum(brut[(x + k) % largeur] for k in range(-demi, demi + 1)) / lissage for x in range(largeur)]


def _deserts_naturels(monde, rng):
    """Déserts approximatifs : plaines intérieures des zones subtropicales (environ 18 à 32° de latitude).
    Pas de données climatiques : on se fonde sur la latitude, l'altitude et l'éloignement de la mer."""
    res = []
    bord = _bord_decale(rng)
    for (x, y) in monde.cases_terre():
        lat = 90 - (y + 0.5) * 0.5
        lat_effective = abs(lat) - bord[x % len(bord)]
        if not 18 <= lat_effective <= 32 or monde.altitude.get((x, y), 0) > 4:
            continue
        if rng.random() < 0.15:
            continue  # quelques trous, pour que la limite ne soit pas nette
        if all(monde.est_terre(x + dx, y + dy) for dx in range(-2, 3) for dy in range(-2, 3)):
            res.append([x, y])
            monde.nourriture.pop((x, y), None)
    return res


def _sim():
    if ETAT["sim"] is None:
        nouvelle_partie()
    return ETAT["sim"]


def avancer(tours):
    sim = _sim()
    for _ in range(tours):
        sim.tour_suivant()
        _propager(sim)
    return etat()


def _villes_cases():
    """Villes du monde réel, converties en cases de la carte (calculé une seule fois)."""
    if "_villes" not in ETAT:
        from terre.ressources import case_de, charger_villes
        ETAT["_villes"] = [[*case_de(v["lat"], v["lon"]), v["nom"]] for v in charger_villes()]
    return ETAT["_villes"]


DUREE_FEU = 6
PROBA_FEU = 0.3
PROBA_CONTAGION = 0.15
PROBA_MORT_PESTE = 0.02


def _reinitialiser_fleaux():
    ETAT["feux"] = {}      # case (x, y) -> jours restants de feu
    ETAT["peste"] = set()  # identifiants des personnes malades
    ETAT["tornades"] = []  # [x, y, dx, dy, jours restants]
    ETAT["animaux"] = []   # [x, y, espèce] : mouton, loup, poisson, dragon, zombie
    ETAT["villages"] = []  # [x, y, maisons] : villages fondés par les peuples
    ETAT["routes"] = []   # [x, y] : cases de route entre villages
    ETAT["vehicules"] = []  # [x, y] : véhicules qui roulent sur les routes
    ETAT["guerres"] = []    # [x1, y1, x2, y2] : peuples en guerre qui se touchent
    ETAT["laves"] = []      # [x, y, jours] : cases de lave, mortelles, refroidies après DUREE_LAVE
    ETAT["deserts"] = []    # [x, y] : cases de désert, sans buissons
    ETAT["rivieres"] = []   # [x, y] : cases d'eau courante, qui descendent des montagnes vers la mer
    ETAT["lacs"] = []       # [x, y] : cases d'eau stagnante, là où une rivière ne peut plus descendre
    ETAT["secousses"] = []  # [x, y, rayon, jours] : répliques d'un séisme
    ETAT["forets"] = []     # [x, y] : cases couvertes de forêt
    ETAT["jour_village"] = 0


DUREE_LAVE = 40        # jours avant qu'une case de lave refroidisse
INTERVALLE_VILLAGE = 30  # un peuple fonde un village tous les 30 jours au plus
MAISONS_MAX = 8


def _fonder_villages(sim):
    """Chaque peuple assez nombreux pose un village au centre de ses membres, s'il n'y en a pas déjà un proche."""
    from terre.peuples import peuples
    villages = ETAT.setdefault("villages", [])
    for peuple in peuples(sim._groupes()):
        cx = round(sum(p.x for p in peuple.membres) / len(peuple.membres))
        cy = round(sum(p.y for p in peuple.membres) / len(peuple.membres))
        if not sim.monde.est_terre(cx, cy):
            continue
        proche = next((v for v in villages if abs(v[0] - cx) <= 3 and abs(v[1] - cy) <= 3), None)
        if proche is None:
            villages.append([cx, cy, 1])  # 1 = nombre de maisons
        elif proche[2] < MAISONS_MAX:
            proche[2] += 1  # le village grandit tant que le peuple reste là


def _tracer_routes(sim):
    """Relie par une route deux villages assez proches et assez peuplés, sur la terre seulement."""
    villages = [v for v in ETAT.get("villages", []) if v[2] >= 3]
    routes = ETAT.setdefault("routes", [])
    cases = {tuple(c) for c in routes}
    for a in range(len(villages)):
        for b in range(a + 1, len(villages)):
            (x1, y1, _), (x2, y2, _) = villages[a], villages[b]
            if abs(x1 - x2) + abs(y1 - y2) > DISTANCE_ROUTE:
                continue
            n = max(abs(x2 - x1), abs(y2 - y1))
            for k in range(n + 1):
                x = round(x1 + (x2 - x1) * k / n)
                y = round(y1 + (y2 - y1) * k / n)
                if sim.monde.est_terre(x, y) and (x, y) not in cases:
                    cases.add((x, y))
                    routes.append([x, y])


DISTANCE_ROUTE = 12


def _reperer_guerres(sim):
    """Deux peuples en guerre dont les membres sont proches : une ligne de guerre les relie."""
    from terre.peuples import peuples, relation
    groupes = peuples(sim._groupes())
    lignes = []
    for a in range(len(groupes)):
        for b in range(a + 1, len(groupes)):
            if relation(groupes[a], groupes[b]) != "guerre":
                continue
            x1 = round(sum(p.x for p in groupes[a].membres) / len(groupes[a].membres))
            y1 = round(sum(p.y for p in groupes[a].membres) / len(groupes[a].membres))
            x2 = round(sum(p.x for p in groupes[b].membres) / len(groupes[b].membres))
            y2 = round(sum(p.y for p in groupes[b].membres) / len(groupes[b].membres))
            if max(abs(x1 - x2), abs(y1 - y2)) <= 12:
                lignes.append([x1, y1, x2, y2])
    ETAT["guerres"] = lignes


def _peut_aller(espece, terre):
    """Les poissons nagent dans la mer, les moutons, loups et zombies marchent sur la terre, le dragon vole partout."""
    if espece == "dragon":
        return True
    if espece == "poisson":
        return not terre
    return terre


def _propager(sim):
    """Évolution quotidienne du feu et de la peste."""
    ETAT["jour_village"] = ETAT.get("jour_village", 0) + 1
    if ETAT["jour_village"] >= INTERVALLE_VILLAGE:
        ETAT["jour_village"] = 0
        _fonder_villages(sim)
        _tracer_routes(sim)
        _reperer_guerres(sim)
    feux = ETAT.setdefault("feux", {})
    for c, restant in list(feux.items()):
        x, y = c
        for p in sim.vivantes():
            if p.x == x and p.y == y:
                sim._mourir(p, "feu")
        sim.monde.nourriture.pop(c, None)
        if restant <= 1:
            del feux[c]
            continue
        feux[c] = restant - 1
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            v = (x + dx, y + dy)
            if v not in feux and sim.monde.est_terre(*v) and random.random() < PROBA_FEU:
                feux[v] = DUREE_FEU

    routes_set = {tuple(r) for r in ETAT.get("routes", [])}
    vehicules = ETAT.setdefault("vehicules", [])
    if routes_set and len(vehicules) < max(1, len(routes_set) // 4) and random.random() < 0.05:
        vehicules.append(list(random.choice(sorted(routes_set))))
    for v in vehicules:
        voisins = [(v[0] + dx, v[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        voisins = [c for c in voisins if c in routes_set]
        if voisins:
            v[0], v[1] = random.choice(voisins)

    # La lave refroidit : chaque case vieillit d'un jour et redevient terre ordinaire.
    refroidies = []
    for c in ETAT.get("laves", []):
        if len(c) < 3:
            c.append(0)
        c[2] += 1
        if c[2] >= DUREE_LAVE:
            refroidies.append(c)
    for c in refroidies:
        ETAT["laves"].remove(c)
    laves = {(c[0], c[1]) for c in ETAT.get("laves", [])}
    for p in sim.vivantes():
        if (p.x, p.y) in laves:
            sim._mourir(p, "lave")

    animaux = ETAT.setdefault("animaux", [])
    for a in animaux:
        if random.random() < 0.5:
            dx, dy = random.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
            if _peut_aller(a[2], sim.monde.est_terre(a[0] + dx, a[1] + dy)):
                a[0] += dx
                a[1] += dy
    if any(a[2] == "loup" for a in animaux):
        moutons_manges = set()
        for loup in (a for a in animaux if a[2] == "loup"):
            for mouton in (a for a in animaux if a[2] == "mouton"):
                if abs(loup[0] - mouton[0]) <= 1 and abs(loup[1] - mouton[1]) <= 1:
                    moutons_manges.add(id(mouton))
        animaux[:] = [a for a in animaux if id(a) not in moutons_manges]
    for a in animaux:
        if a[2] == "dragon":
            # Le dragon brûle la terre sur laquelle il passe.
            if sim.monde.est_terre(a[0], a[1]) and random.random() < 0.2:
                ETAT.setdefault("feux", {})[(a[0], a[1])] = DUREE_FEU
        elif a[2] == "zombie":
            for p in sim.vivantes():
                if abs(p.x - a[0]) <= 1 and abs(p.y - a[1]) <= 1 and random.random() < 0.3:
                    sim._mourir(p, "zombie")
    # Les répliques : pendant quelques jours, de petites secousses tuent les personnes proches de l'épicentre.
    secousses = ETAT.setdefault("secousses", [])
    for sec in secousses:
        if random.random() < 0.3:
            # Une réplique frappe un point au hasard dans la zone de l'épicentre.
            px = sec[0] + random.randint(-sec[2] * 2, sec[2] * 2)
            py = sec[1] + random.randint(-sec[2] * 2, sec[2] * 2)
            for p in sim.vivantes():
                if abs(p.x - px) <= 1 and abs(p.y - py) <= 1 and random.random() < 0.5:
                    sim._mourir(p, "séisme")
        sec[3] -= 1
    secousses[:] = [sec for sec in secousses if sec[3] > 0]
    tornades = ETAT.setdefault("tornades", [])
    for t in tornades:
        t[0] += t[2]
        t[1] += t[3]
        t[4] -= 1
        for p in sim.vivantes():
            if abs(p.x - t[0]) <= 1 and abs(p.y - t[1]) <= 1:
                sim._mourir(p, "tornade")
    tornades[:] = [t for t in tornades if t[4] > 0]

    malades = ETAT.setdefault("peste", set())
    vivantes = {p.identifiant: p for p in sim.vivantes()}
    malades &= set(vivantes)
    for ident in list(malades):
        p = vivantes[ident]
        if random.random() < PROBA_MORT_PESTE:
            sim._mourir(p, "peste")
            malades.discard(ident)
            continue
        for autre in vivantes.values():
            if abs(autre.x - p.x) <= 1 and abs(autre.y - p.y) <= 1 and random.random() < PROBA_CONTAGION:
                malades.add(autre.identifiant)


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
        feux = ETAT.setdefault("feux", {})
        for dx in range(-rayon, rayon + 1):
            for dy in range(-rayon, rayon + 1):
                c = (i + dx, j + dy)
                if sim.monde.est_terre(*c):
                    feux[c] = DUREE_FEU
        message = f"Le feu prend : {len(touches)} morts, il se répand."
    elif outil == "seisme":
        rayon_seisme = rayon * 3
        touches = [p for p in sim.vivantes()
                   if abs(p.x - i) <= rayon_seisme and abs(p.y - j) <= rayon_seisme]
        for p in touches:
            sim._mourir(p, "séisme")
        for dx in range(-rayon_seisme, rayon_seisme + 1):
            for dy in range(-rayon_seisme, rayon_seisme + 1):
                sim.monde.nourriture.pop((i + dx, j + dy), None)
        ETAT.setdefault("secousses", []).append([i, j, rayon_seisme, 4])  # répliques pendant 4 jours
        message = f"Un tremblement de terre frappe : {len(touches)} morts."
    elif outil == "peste":
        # Une personne sur trois dans la zone meurt de la peste (effet immédiat).
        touches = [p for p in sim.vivantes()
                   if abs(p.x - i) <= rayon and abs(p.y - j) <= rayon]
        malades = ETAT.setdefault("peste", set())
        for p in touches[::3]:
            malades.add(p.identifiant)
        message = f"La peste frappe : {len(touches[::3])} malades, elle se transmet."
    elif outil == "tornade":
        angle = random.random() * 6.283
        dx, dy = round(math.cos(angle)), round(math.sin(angle))
        ETAT.setdefault("tornades", []).append([i, j, dx, dy, 15])
        message = "Une tornade se forme et part en tournoyant."
    elif outil == "bombe":
        # Une explosion : tout ce qui se trouve dans le rayon meurt, et la terre prend feu.
        touches = [p for p in sim.vivantes()
                   if (p.x - i) ** 2 + (p.y - j) ** 2 <= (rayon + 1) ** 2]
        for p in touches:
            sim._mourir(p, "bombe")
        for dx in range(-rayon - 1, rayon + 2):
            for dy in range(-rayon - 1, rayon + 2):
                if dx * dx + dy * dy <= (rayon + 1) ** 2 and sim.monde.est_terre(i + dx, j + dy):
                    ETAT.setdefault("feux", {})[(i + dx, j + dy)] = DUREE_FEU
        message = f"Une bombe explose : {len(touches)} morts, le feu gagne la zone."
    elif outil == "meteorite":
        touches = [p for p in sim.vivantes()
                   if abs(p.x - i) <= rayon * 2 and abs(p.y - j) <= rayon * 2]
        for p in touches:
            sim._mourir(p, "météorite")
        for dx in range(-rayon, rayon + 1):
            for dy in range(-rayon, rayon + 1):
                if dx * dx + dy * dy <= rayon * rayon and sim.monde.est_terre(i + dx, j + dy):
                    sim.monde.poser_terre(i + dx, j + dy, False)
        message = f"Une météorite s'écrase : {len(touches)} morts, un cratère s'ouvre."
    elif outil in ("mouton", "loup", "poisson", "dragon", "zombie"):
        terre = sim.monde.est_terre(i, j)
        if _peut_aller(outil, terre):
            ETAT.setdefault("animaux", []).append([i, j, outil])
            message = {"mouton": "Un mouton paît.", "loup": "Un loup rôde.", "poisson": "Un banc de poissons passe.",
                       "dragon": "Un dragon surgit et déploie ses ailes.", "zombie": "Un zombie titube hors de la tombe."}[outil]
        else:
            message = "Il faut la mer." if outil == "poisson" else "Il faut la terre."
    elif outil == "lave":
        posees = 0
        laves = ETAT.setdefault("laves", [])
        deja = {(c[0], c[1]) for c in laves}
        for dx in range(-rayon, rayon + 1):
            for dy in range(-rayon, rayon + 1):
                c = (i + dx, j + dy)
                if dx * dx + dy * dy <= rayon * rayon and sim.monde.est_terre(*c) and c not in deja:
                    laves.append([c[0], c[1], 0])
                    deja.add(c)
                    posees += 1
        message = f"La lave jaillit ({posees} cases)." if posees else "Il faut de la terre pour la lave."
    elif outil == "desert":
        posees = 0
        deserts = ETAT.setdefault("deserts", [])
        deja = {tuple(c) for c in deserts}
        for dx in range(-rayon, rayon + 1):
            for dy in range(-rayon, rayon + 1):
                c = (i + dx, j + dy)
                if dx * dx + dy * dy <= rayon * rayon and sim.monde.est_terre(*c):
                    sim.monde.nourriture.pop(c, None)
                    if c not in deja:
                        deserts.append([c[0], c[1]])
                        deja.add(c)
                        posees += 1
        message = f"Le désert s'étend ({posees} cases) : plus aucun buisson n'y pousse." if posees else "Il faut de la terre pour le désert."
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
    # Chaque personne porte le numéro de son peuple (ou -1), pour la colorier sur la carte.
    from terre.peuples import peuples as _peuples
    numero_peuple = {}
    for k, pe in enumerate(_peuples(sim._groupes())):
        for membre in pe.membres:
            numero_peuple[id(membre)] = k
    prieres = [
        {"id": pr.identifiant, "auteur": pr.auteur, "besoin": pr.besoin, "texte": TEXTES[pr.besoin]}
        for pr in sim.prieres_en_attente()
    ]
    eau = sim._eau_active()
    modifs = [[x, y, 1 if v else 0] for (x, y), v in getattr(sim.monde, "modifs", {}).items()]
    animaux = [list(a) for a in ETAT.get("animaux", [])]
    villes = _villes_cases() if ETAT.get("carte", "terre") == "terre" else []
    montagnes = [list(c) for c in getattr(sim.monde, "montagnes", set())]
    return {
        "bilan": sim.bilan(),
        "personnes": [
            {"id": p.identifiant, "x": p.x, "y": p.y, "soif": round(p.soif, 1), "faim": round(p.faim, 1),
             "foi": round(p.foi, 2), "schismatique": p.schismatique, "peuple": numero_peuple.get(id(p), -1),
             "age": round(p.age / 365, 1), "langue": p.langue or "aucune",
             "enfants": len(p.enfants), "marie": p.conjoint is not None,
             "savoirs": sorted(p.savoirs)}
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
        "animaux": animaux,
        "feux": [[x, y] for (x, y) in ETAT.get("feux", {})],
        "villages": [list(v) for v in ETAT.get("villages", [])],
        "routes": [list(r) for r in ETAT.get("routes", [])],
        "vehicules": [list(v) for v in ETAT.get("vehicules", [])],
        "guerres": [list(g) for g in ETAT.get("guerres", [])],
        "laves": [list(c) for c in ETAT.get("laves", [])],
        "deserts": [list(c) for c in ETAT.get("deserts", [])],
        "forets": [list(c) for c in ETAT.get("forets", [])],
        "rivieres": [list(c) for c in ETAT.get("rivieres", [])],
        "lacs": [list(c) for c in ETAT.get("lacs", [])],
        "largeur": sim.monde.largeur,
        "hauteur": sim.monde.hauteur,
        "terre": [list(c) for c in sim.monde.cases_terre()] if ETAT.get("carte", "terre") != "terre" else None,
        "villes": villes,
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
        d = json.load(f)
    # Les pouvoirs en cours (animaux, feu, peste, tornades) sont rangés à part dans la sauvegarde.
    d["extras"] = {
        "animaux": ETAT.get("animaux", []),
        "feux": [[x, y, t] for (x, y), t in ETAT.get("feux", {}).items()],
        "peste": sorted(ETAT.get("peste", set())),
        "tornades": ETAT.get("tornades", []),
        "villages": ETAT.get("villages", []),
        "routes": ETAT.get("routes", []),
        "vehicules": ETAT.get("vehicules", []),
        "laves": ETAT.get("laves", []),
        "deserts": ETAT.get("deserts", []),
        "rivieres": ETAT.get("rivieres", []),
        "lacs": ETAT.get("lacs", []),
        "secousses": ETAT.get("secousses", []),
        "forets": [list(c) for c in ETAT.get("forets", [])],
    }
    return json.dumps(d)


def charger_texte(texte):
    extras = json.loads(texte).get("extras", {})
    with open(CHEMIN_SAUVEGARDE, "w", encoding="utf-8") as f:
        f.write(texte)
    ETAT["sim"] = sauvegarde.charger(CHEMIN_SAUVEGARDE, joueur=None)
    _reinitialiser_fleaux()
    ETAT["animaux"] = extras.get("animaux", [])
    ETAT["feux"] = {(x, y): t for x, y, t in extras.get("feux", [])}
    ETAT["peste"] = set(extras.get("peste", []))
    ETAT["tornades"] = extras.get("tornades", [])
    ETAT["villages"] = extras.get("villages", [])
    ETAT["routes"] = extras.get("routes", [])
    ETAT["vehicules"] = extras.get("vehicules", [])
    ETAT["laves"] = extras.get("laves", [])
    ETAT["deserts"] = extras.get("deserts", [])
    ETAT["rivieres"] = extras.get("rivieres", [])
    ETAT["lacs"] = extras.get("lacs", [])
    ETAT["secousses"] = extras.get("secousses", [])
    ETAT["forets"] = extras.get("forets", [])
    actuelle = set(ETAT["sim"].monde.cases_terre())
    if ETAT["sim"].monde.largeur == 720:
        ETAT["carte"] = "terre"
        BASE["terre"] = set(charger().cases_terre())
        for c in actuelle - BASE["terre"]:
            ETAT["sim"].monde.modifs[c] = True
        for c in BASE["terre"] - actuelle:
            ETAT["sim"].monde.modifs[c] = False
    else:
        ETAT["carte"] = "ile" if ETAT["sim"].monde.largeur == 40 else "archipel"
        BASE["terre"] = None
    ETAT["population"] = charger_population()
    return etat()
