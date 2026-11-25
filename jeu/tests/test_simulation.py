"""Tests de la phase 1 : carte, besoins, déplacements, nourriture et mort."""
import os
import random
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre.monde import NOURRITURE_MAX, Monde, creer_ile  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.simulation import Simulation  # noqa: E402


def petite_ile():
    terre = {(x, y) for x in range(5) for y in range(5)}
    return Monde(5, 5, terre)


class TestIle(unittest.TestCase):
    def test_ile_a_de_la_terre_et_des_buissons(self):
        monde = creer_ile(3)
        self.assertGreater(len(monde.cases_terre()), 300)
        self.assertEqual(len(monde.nourriture), 12)

    def test_meme_graine_meme_ile(self):
        self.assertEqual(creer_ile(7).cases_terre(), creer_ile(7).cases_terre())

    def test_repousse_plafonne_au_maximum(self):
        monde = creer_ile(3)
        case = next(iter(monde.nourriture))
        monde.nourriture[case] = 0.5
        for _ in range(1000):
            monde.repousser()
        self.assertEqual(monde.nourriture[case], NOURRITURE_MAX)


class TestPersonne(unittest.TestCase):
    def test_mange_au_buisson(self):
        monde = petite_ile()
        monde.nourriture[(2, 2)] = 3
        p = Personne(0, 2, 2, faim=70)
        Simulation(monde, [p], graine=1).tour_suivant()
        self.assertLess(p.faim, 70)
        self.assertLess(monde.nourriture[(2, 2)], 3)

    def test_meurt_de_faim_sans_nourriture(self):
        monde = petite_ile()
        p = Personne(0, 2, 2, faim=99.7)
        sim = Simulation(monde, [p], graine=1)
        sim.tour_suivant()
        self.assertFalse(p.vivante)
        self.assertEqual(sim.morts[0][2], "faim")

    def test_se_dirige_vers_un_buisson_visible(self):
        monde = petite_ile()
        monde.nourriture[(4, 2)] = 3
        p = Personne(0, 0, 2, faim=80)
        Simulation(monde, [p], graine=1).tour_suivant()
        self.assertEqual(p.case, (1, 2))

    def test_groupe_survit_une_annee_avec_nourriture(self):
        monde = creer_ile(5, nb_buissons=60)
        rng = random.Random(5)
        cases = monde.cases_terre()
        personnes = [Personne(i, *rng.choice(cases)) for i in range(20)]
        sim = Simulation(monde, personnes, graine=5)
        for _ in range(365):
            sim.tour_suivant()
        self.assertGreater(len(sim.vivantes()), 0)


if __name__ == "__main__":
    unittest.main()
