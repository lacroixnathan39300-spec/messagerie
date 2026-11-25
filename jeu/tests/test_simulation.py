"""Tests des phases 1 et 2 : carte, besoins, nourriture, rumeurs, relations, mariages et naissances."""
import os
import random
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre import simulation as sim_module  # noqa: E402
from terre.monde import NOURRITURE_MAX, Monde, creer_ile  # noqa: E402
from terre.personne import AGE_ADULTE, Personne  # noqa: E402
from terre.simulation import Simulation  # noqa: E402


def petite_ile():
    terre = {(x, y) for x in range(5) for y in range(5)}
    return Monde(5, 5, terre)


def grande_ligne(longueur=20):
    return Monde(longueur, 1, {(x, 0) for x in range(longueur)})


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


class TestBesoins(unittest.TestCase):
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


class TestRumeurs(unittest.TestCase):
    def test_rumeur_se_transmet_entre_voisins(self):
        monde = petite_ile()
        a = Personne(0, 2, 2)
        b = Personne(1, 3, 2)
        a.rumeurs.append({"sujet": "buisson", "valeur": (4, 4), "fiabilite": 1.0})
        sim = Simulation(monde, [a, b], graine=1)
        sim._rencontres()
        self.assertTrue(any(r["sujet"] == "buisson" for r in b.rumeurs))

    def test_rumeur_perd_en_fiabilite_a_chaque_passage(self):
        monde = petite_ile()
        a = Personne(0, 2, 2)
        b = Personne(1, 3, 2)
        a.rumeurs.append({"sujet": "buisson", "valeur": (4, 4), "fiabilite": 1.0})
        sim = Simulation(monde, [a, b], graine=1)
        sim._rencontres()
        recue = next(r for r in b.rumeurs if r["sujet"] == "buisson")
        self.assertLess(recue["fiabilite"], 1.0)

    def test_guide_vers_un_buisson_connu_par_rumeur(self):
        monde = grande_ligne(20)
        p = Personne(0, 0, 0, faim=80)
        p.rumeurs.append({"sujet": "buisson", "valeur": (15, 0), "fiabilite": 1.0})
        Simulation(monde, [p], graine=1).tour_suivant()
        self.assertEqual(p.case, (1, 0))


class TestSouvenirs(unittest.TestCase):
    def test_souvenirs_s_estompent(self):
        p = Personne(0, 0, 0)
        p.ajouter_souvenir("un événement", 1.0)
        for _ in range(1000):
            p.oublier()
        self.assertEqual(p.souvenirs, [])


class TestFamilles(unittest.TestCase):
    def test_mariage_entre_adultes_qui_s_apprecient(self):
        monde = petite_ile()
        a = Personne(0, 2, 2, age=AGE_ADULTE)
        b = Personne(1, 2, 2, age=AGE_ADULTE)
        sim = Simulation(monde, [a, b], graine=1)
        sim.relations[(0, 1)] = {"confiance": 0.9, "affection": 0.9}
        sim._rencontres()
        self.assertEqual(a.conjoint, 1)
        self.assertEqual(b.conjoint, 0)

    def test_pas_de_mariage_avec_un_enfant(self):
        monde = petite_ile()
        a = Personne(0, 2, 2, age=AGE_ADULTE)
        b = Personne(1, 2, 2, age=10)
        sim = Simulation(monde, [a, b], graine=1)
        sim.relations[(0, 1)] = {"confiance": 0.9, "affection": 0.9}
        sim._rencontres()
        self.assertIsNone(a.conjoint)

    def test_naissance_entre_conjoints(self):
        monde = petite_ile()
        a = Personne(0, 2, 2, age=AGE_ADULTE, conjoint=1)
        b = Personne(1, 2, 2, age=AGE_ADULTE, conjoint=0)
        sim = Simulation(monde, [a, b], graine=1)
        with mock.patch.object(sim_module, "PROBA_NAISSANCE", 1.0):
            sim._naissances()
        self.assertEqual(sim.naissances, 1)
        self.assertEqual(len(sim.vivantes()), 3)
        self.assertEqual(a.enfants, [2])


if __name__ == "__main__":
    unittest.main()
