"""Démonstration en ligne de commande : une île, vingt personnes, une année simulée."""
import random

from terre.monde import creer_ile
from terre.personne import Personne
from terre.simulation import Simulation


def lancer(graine=1, nb_personnes=20, tours=365):
    monde = creer_ile(graine)
    rng = random.Random(graine)
    cases = monde.cases_terre()
    personnes = [Personne(i, *rng.choice(cases)) for i in range(nb_personnes)]
    sim = Simulation(monde, personnes, graine)
    print(f"Île : {len(cases)} cases de terre, {len(monde.nourriture)} buissons")
    for _ in range(tours):
        sim.tour_suivant()
        if sim.tour % 50 == 0:
            print(sim.bilan())
    print("Fin :", sim.bilan())
    return sim


if __name__ == "__main__":
    lancer()
