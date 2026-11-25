"""Tests de la phase 5 : peuples, guerres et zoom."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre import echelles  # noqa: E402
from terre.monde import Monde  # noqa: E402
from terre.peuples import peuples, relation  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.simulation import Simulation  # noqa: E402


def ligne(longueur=60):
    return Monde(longueur, 1, {(x, 0) for x in range(longueur)})


class TestPeuples(unittest.TestCase):
    def test_deux_groupes_eloignes_font_deux_peuples(self):
        monde = ligne()
        a = [Personne(i, i, 0) for i in range(6)]
        b = [Personne(10 + i, 40 + i, 0) for i in range(6)]
        sim = Simulation(monde, a + b, graine=1)
        self.assertEqual(len(peuples(sim._groupes())), 2)

    def test_petit_groupe_n_est_pas_un_peuple(self):
        monde = ligne()
        a = [Personne(i, i, 0) for i in range(3)]
        sim = Simulation(monde, a, graine=1)
        self.assertEqual(peuples(sim._groupes()), [])

    def test_guerre_quand_la_colere_est_forte(self):
        monde = ligne()
        a = [Personne(i, i, 0, colere=0.9) for i in range(6)]
        b = [Personne(10 + i, 14 + i, 0, colere=0.9) for i in range(6)]
        pa, pb = peuples([a, b])
        self.assertEqual(relation(pa, pb), "guerre")

    def test_pas_de_guerre_sans_colere(self):
        monde = ligne()
        a = [Personne(i, i, 0) for i in range(6)]
        b = [Personne(10 + i, 14 + i, 0) for i in range(6)]
        pa, pb = peuples([a, b])
        self.assertEqual(relation(pa, pb), "echange")

    def test_guerre_use_les_deux_camps(self):
        monde = ligne()
        a = [Personne(i, i, 0, colere=0.9) for i in range(6)]
        b = [Personne(10 + i, 14 + i, 0, colere=0.9) for i in range(6)]
        sim = Simulation(monde, a + b, graine=1)
        energie_avant = a[0].energie
        sim._conflits()
        self.assertEqual(sim.guerres, 1)
        self.assertLess(a[0].energie, energie_avant)


class TestZoom(unittest.TestCase):
    def test_zoom_avant_puis_arriere(self):
        e = echelles.PLANETE
        self.assertEqual(echelles.zoomer(e).nom, "region")
        self.assertEqual(echelles.dezoomer(echelles.zoomer(e)).nom, "planete")

    def test_planete_passe_une_annee_par_seconde(self):
        self.assertEqual(echelles.jours_pour(echelles.PLANETE, 1), 365)

    def test_personne_est_en_pause(self):
        self.assertEqual(echelles.jours_pour(echelles.PERSONNE, 10), 0)


if __name__ == "__main__":
    unittest.main()
