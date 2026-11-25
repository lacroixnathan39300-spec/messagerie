"""Démonstration en ligne de commande : une île, vingt personnes, une année simulée."""
import random

from terre.monde import creer_ile
from terre.personne import Personne
from terre.simulation import Simulation


def joueur_demo(graine):
    """Un joueur simulé : exauce la faim et le repos, modifie les demandes de compagnon,
    refuse une prière sur trois pour le reste."""
    rng = random.Random(graine + 99)

    def repondre(priere, sim):
        if priere.besoin in ("faim", "repos"):
            return "exaucer"
        if priere.besoin == "compagnon":
            return "modifier"
        return "refuser" if rng.random() < 1 / 3 else "ignorer"

    return repondre


def lancer(graine=1, nb_personnes=20, tours=365):
    monde = creer_ile(graine)
    rng = random.Random(graine)
    cases = monde.cases_terre()
    personnes = [Personne(i, *rng.choice(cases)) for i in range(nb_personnes)]
    sim = Simulation(monde, personnes, graine, joueur=joueur_demo(graine))
    print(f"Île : {len(cases)} cases de terre, {len(monde.nourriture)} buissons")
    for _ in range(tours):
        sim.tour_suivant()
        if sim.tour % 50 == 0:
            print(sim.bilan())
    print("Fin :", sim.bilan())
    return sim


if __name__ == "__main__":
    lancer()
