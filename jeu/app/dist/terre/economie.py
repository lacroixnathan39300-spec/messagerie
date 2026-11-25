"""Énergie et électricité par peuple, et accidents nucléaires.

Modèle d'indicateurs (pas une économie complète) :
- Besoin : chaque personne consomme selon l'époque (plus l'humanité est équipée, plus elle consomme).
- Production : chaque source n'est disponible qu'avec la technique qui la rend possible, et selon
  le territoire du peuple (relief, côte, latitude, population). Les ressources sont des estimations.
- Statut : « suffisant » si la production couvre le besoin, sinon « pénurie ».
"""
# Consommation par personne selon l'époque (unités arbitraires, croissantes).
from .peuples import peuples  # noqa: E402

CONSOMMATION = {
    "Paléolithique": 0.0, "Néolithique": 0.1, "Antiquité": 0.3, "Époque moderne": 0.6,
    "Époque industrielle": 2.0, "Époque nucléaire": 3.0, "Ère numérique": 4.0,
}
PROBA_ACCIDENT_NUCLEAIRE = 0.00002  # par tour, et par peuple qui produit de l'électricité nucléaire
RAYON_ACCIDENT = 6
PERTE_ENERGIE_ACCIDENT = 30.0


def sources_disponibles(culture, territoire):
    """Liste des sources d'énergie possibles pour un peuple. `territoire` : liste de (case, ressources)."""
    connues = culture.connues
    sources = []
    if "feu" in connues:
        sources.append("bois")
    if "electricite" in connues:
        if any(r.get("altitude", 0) >= 4 for _, r in territoire):
            sources.append("hydro")
        if any(r.get("cotiere") for _, r in territoire):
            sources.append("eolien")
        if any(abs(r.get("latitude", 90)) < 35 for _, r in territoire):
            sources.append("solaire")
        if any(r.get("eau") for _, r in territoire):
            sources.append("charbon")
    if "physique_nucleaire" in connues and any(r.get("population", 0) >= 6 for _, r in territoire):
        sources.append("nucleaire")
    return sources


PRODUCTION = {"bois": 0.2, "hydro": 0.6, "eolien": 0.4, "solaire": 0.3, "charbon": 1.2, "nucleaire": 2.5}


def _ressources_case(monde, case):
    """Indicateurs d'une case, à partir de ce que la carte connaît (relief, côte, eau, population)."""
    altitude = getattr(monde, "altitude", {}).get(case, 0)
    cotiere = any(not monde.est_terre(case[0] + dx, case[1] + dy)
                  for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    eau = monde.points_eau is not None and case in monde.points_eau
    latitude = 90 - (case[1] + 0.5) * 0.5 if getattr(monde, "grille", None) == "0.5deg" else 0
    population = getattr(monde, "population", {}).get(case, 0)
    return {"altitude": altitude, "cotiere": cotiere, "eau": eau,
            "latitude": latitude, "population": population}


def bilan_energie(sim):
    """Bilan énergétique par peuple : besoin, production, sources et statut."""
    par_personne = CONSOMMATION.get(sim.culture.epoque(), 0.0)
    resultat = []
    for peuple in peuples(sim._groupes()):
        territoire = [(p.case, _ressources_case(sim.monde, p.case)) for p in peuple.membres]
        taille = len(peuple.membres)
        besoin = par_personne * taille
        sources = sources_disponibles(sim.culture, territoire)
        production = sum(PRODUCTION[s] * taille for s in sources)
        statut = "suffisant" if production >= besoin else "pénurie"
        resultat.append({"peuple": peuple, "membres": taille, "besoin": round(besoin, 2),
                         "production": round(production, 2), "sources": sources, "statut": statut})
    return resultat


def verifier_accidents(sim, rng=None):
    """Un peuple qui produit du nucléaire peut subir un accident : il est consigné dans l'histoire."""
    rng = rng or sim.rng
    for bilan in bilan_energie(sim):
        if "nucleaire" not in bilan["sources"] or rng.random() >= PROBA_ACCIDENT_NUCLEAIRE:
            continue
        membres = bilan["peuple"].membres
        centre = rng.choice(membres)
        sim.accidents.append((sim.tour, bilan["peuple"].identifiant))
        for p in sim.vivantes():
            if sim._distance(p.case, centre.case) <= RAYON_ACCIDENT:
                p.energie = max(0.0, p.energie - PERTE_ENERGIE_ACCIDENT)
                p.ajouter_souvenir("a subi un accident nucléaire", 1.0)
