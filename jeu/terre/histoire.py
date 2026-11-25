"""Histoire : la chronique de la partie, en phrases, à partir de ce qui s'est réellement passé."""

NOMS_CAUSES = {"faim": "de faim", "vieillesse": "de vieillesse", "soif": "de soif"}


def chronique(sim):
    """Liste (tour, phrase) triée par tour, décrivant les grands événements de la partie."""
    evenements = []
    for tour, nom in sim.culture.epoques:
        evenements.append((tour, f"Entrée à l'époque : {nom}"))
    for tour, action, nom in sim.culture.historique:
        if action == "inventée":
            evenements.append((tour, f"Découverte : {nom.replace('_', ' ')}"))
        else:
            evenements.append((tour, f"Perte du savoir : {nom.replace('_', ' ')}"))
    for tour, ids in sim.schismes:
        evenements.append((tour, f"Schisme : {len(ids)} personnes se séparent du groupe"))
    for tour, peuple in sim.accidents:
        evenements.append((tour, f"Accident nucléaire chez le peuple {peuple}"))
    for tour, identifiant, cause in sim.morts:
        if cause in NOMS_CAUSES and tour % 100 == 0:
            evenements.append((tour, f"Une personne meurt {NOMS_CAUSES[cause]}"))
    evenements.sort(key=lambda e: e[0])
    return evenements
