"""Tests de la Terre réelle : grille, lieux connus, relief, PNG et simulation."""
import os
import random
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre.personne import Personne  # noqa: E402
from terre.simulation import Simulation  # noqa: E402
from terre.terre_reelle import carte_png, charger, semer_buissons  # noqa: E402


def case(lon, lat):
    """Coordonnées de la grille à 0,5° pour une longitude et une latitude."""
    return int((lon + 180) / 0.5), int((90 - lat) / 0.5)


class TestGrille(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.terre = charger()

    def test_dimensions_0_5_degre(self):
        self.assertEqual((self.terre.largeur, self.terre.hauteur), (720, 360))

    def test_pacifique_est_en_mer(self):
        self.assertFalse(self.terre.est_terre(*case(-150, 0)))

    def test_paris_est_sur_terre(self):
        self.assertTrue(self.terre.est_terre(*case(2.35, 48.85)))

    def test_himalaya_est_une_zone_de_relief_fort(self):
        i, j = case(86, 28)
        self.assertGreaterEqual(self.terre.altitude.get((i, j), 0), 6)

    def test_aconcagua_dans_les_andes(self):
        i, j = case(-70, -32.6)
        voisinage = [self.terre.altitude.get((a, b), 0)
                     for a in range(i - 3, i + 4) for b in range(j - 3, j + 4)]
        self.assertGreaterEqual(max(voisinage), 6)

    def test_nombre_de_terres_raisonnable(self):
        self.assertGreater(len(self.terre.cases_terre()), 60000)


class TestBuissons(unittest.TestCase):
    def test_semis_de_buissons_deterministe(self):
        a, b = charger(), charger()
        semer_buissons(a, 3)
        semer_buissons(b, 3)
        self.assertEqual(a.nourriture, b.nourriture)
        self.assertGreater(len(a.nourriture), 0)


class TestImage(unittest.TestCase):
    def test_png_valide_avec_la_bonne_taille(self):
        monde = charger()
        dossier = tempfile.mkdtemp()
        chemin = os.path.join(dossier, "terre.png")
        carte_png(monde, chemin, echelle=1)
        with open(chemin, "rb") as f:
            entete = f.read(24)
        self.assertEqual(entete[:8], b"\x89PNG\r\n\x1a\n")
        largeur, hauteur = struct.unpack(">II", entete[16:24])
        self.assertEqual((largeur, hauteur), (720, 360))


class TestSimulation(unittest.TestCase):
    def test_une_population_survit_sur_la_terre_reelle(self):
        monde = charger()
        semer_buissons(monde, 1)
        rng = random.Random(1)
        cases = monde.cases_terre()
        personnes = [Personne(i, *rng.choice(cases)) for i in range(30)]
        sim = Simulation(monde, personnes, graine=1)
        for _ in range(60):
            sim.tour_suivant()
        self.assertGreater(len(sim.vivantes()), 20)


if __name__ == "__main__":
    unittest.main()
