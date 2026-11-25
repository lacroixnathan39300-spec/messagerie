"""Tests des mondes générés : déterminisme, absence de bord, frontières de morceaux, carte PGM,
simulation et sauvegarde sur un monde infini."""
import os
import random
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre.monde_infini import MondeInfini, monde_depuis_pgm  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.sauvegarde import charger, sauvegarder  # noqa: E402
from terre.simulation import Simulation  # noqa: E402


def fraction_terre(monde, cote=200):
    cases = [(x, y) for x in range(-cote // 2, cote // 2) for y in range(-cote // 2, cote // 2)]
    return sum(monde.est_terre(x, y) for x, y in cases) / len(cases)


class TestGeneration(unittest.TestCase):
    def test_meme_graine_meme_monde(self):
        a, b = MondeInfini(5), MondeInfini(5)
        for x in range(-80, 80, 7):
            for y in range(-80, 80, 7):
                self.assertEqual(a.est_terre(x, y), b.est_terre(x, y))

    def test_graine_differente_autre_monde(self):
        a, b = MondeInfini(5), MondeInfini(6)
        differences = sum(a.est_terre(x, y) != b.est_terre(x, y)
                          for x in range(-60, 60) for y in range(-60, 60))
        self.assertGreater(differences, 0)

    def test_proportion_de_terre_raisonnable(self):
        f = fraction_terre(MondeInfini(3))
        self.assertGreater(f, 0.15)
        self.assertLess(f, 0.7)

    def test_pas_de_bord_loin_de_l_origine(self):
        monde = MondeInfini(3)
        trouvee = any(monde.est_terre(x, y) for x in range(100000, 100200) for y in range(0, 200))
        self.assertTrue(trouvee)

    def test_frontiere_entre_morceaux_sans_couture(self):
        monde = MondeInfini(9)
        for y in range(-30, 30):
            a = monde.est_terre(63, y)
            b = monde.est_terre(64, y)
            self.assertIsInstance(a, bool)
            self.assertIsInstance(b, bool)

    def test_forme_ile_entoure_d_ocean(self):
        monde = MondeInfini(4, forme="ile")
        self.assertFalse(monde.est_terre(2000, 2000))

    def test_monde_borne(self):
        monde = MondeInfini(4, limite=(50, 50))
        self.assertFalse(monde.est_terre(40, 0))
        self.assertFalse(monde.est_terre(-40, 0))

    def test_forme_inconnue_refusee(self):
        with self.assertRaises(ValueError):
            MondeInfini(1, forme="n'importe quoi")


class TestCarteFournie(unittest.TestCase):
    def test_pgm_texte_donne_la_terre(self):
        dossier = tempfile.mkdtemp()
        chemin = os.path.join(dossier, "carte.pgm")
        with open(chemin, "w", encoding="ascii") as f:
            f.write("P2\n2 2\n255\n255 0\n0 255\n")
        monde = monde_depuis_pgm(chemin, 2, 2)
        self.assertEqual(sorted(monde.cases_terre()), [(0, 0), (1, 1)])

    def test_pgm_format_inconnu_refuse(self):
        dossier = tempfile.mkdtemp()
        chemin = os.path.join(dossier, "carte.png")
        with open(chemin, "wb") as f:
            f.write(b"\x89PNG")
        with self.assertRaises(ValueError):
            monde_depuis_pgm(chemin, 2, 2)


class TestSimulationEtSauvegarde(unittest.TestCase):
    def test_une_simulation_tourne_sur_un_monde_infini(self):
        monde = MondeInfini(7)
        depart = [c for c in monde.cases_terre() if abs(c[0]) < 10 and abs(c[1]) < 10][:20]
        personnes = [Personne(i, *c) for i, c in enumerate(depart)]
        sim = Simulation(monde, personnes, graine=7)
        for _ in range(100):
            sim.tour_suivant()
        self.assertGreater(len(sim.vivantes()), 0)

    def test_sauvegarde_d_un_monde_infini_avec_buissons_vides(self):
        dossier = tempfile.mkdtemp()
        chemin = os.path.join(dossier, "partie.json")
        monde = MondeInfini(7)
        depart = [c for c in monde.cases_terre() if abs(c[0]) < 10 and abs(c[1]) < 10][:20]
        personnes = [Personne(i, *c) for i, c in enumerate(depart)]
        sim = Simulation(monde, personnes, graine=7)
        for _ in range(80):
            sim.tour_suivant()
        sauvegarder(sim, chemin)
        recharge = charger(chemin)
        self.assertEqual(sim.bilan(), recharge.bilan())
        self.assertEqual(sim.monde.nourriture, recharge.monde.nourriture)
        for _ in range(40):
            sim.tour_suivant()
            recharge.tour_suivant()
        self.assertEqual(sim.bilan(), recharge.bilan())


if __name__ == "__main__":
    unittest.main()
