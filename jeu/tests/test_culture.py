"""Tests de la phase 4 : techniques, pertes, époques et transmission."""
import os
import random
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre import culture as culture_module  # noqa: E402
from terre import simulation as sim_module  # noqa: E402
from terre.culture import Culture  # noqa: E402
from terre.monde import Monde  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.simulation import Simulation  # noqa: E402


def groupe(n, savoirs=()):
    personnes = [Personne(i, 0, 0) for i in range(n)]
    for p in personnes:
        p.savoirs.update(savoirs)
    return personnes


class TestTechniques(unittest.TestCase):
    def test_technique_simple_apparait_dans_un_groupe(self):
        c = Culture()
        g = groupe(5)
        c.mettre_a_jour(1, g, [g], random.Random(1), proba_invention=1.0)
        self.assertIn("outil_pierre", c.connues)
        self.assertTrue(any(p.savoirs for p in g))

    def test_agriculture_demande_le_feu_et_un_grand_groupe(self):
        c = Culture()
        petit = groupe(10)
        c.mettre_a_jour(1, petit, [petit], random.Random(1), proba_invention=1.0)
        self.assertNotIn("agriculture", c.connues)
        c.connues.add("feu")
        for p in petit:
            p.savoirs.add("feu")
        c.mettre_a_jour(2, petit, [petit], random.Random(1), proba_invention=1.0)
        self.assertNotIn("agriculture", c.connues)  # 10 < 25 personnes
        grand = groupe(30, savoirs={"feu"})
        c.mettre_a_jour(3, grand, [grand], random.Random(1), proba_invention=1.0)
        self.assertIn("agriculture", c.connues)

    def test_technique_perdue_quand_personne_ne_la_garde(self):
        c = Culture()
        c.connues.add("arc")
        personnes = groupe(3)
        c.mettre_a_jour(5, personnes, [personnes], random.Random(1), proba_invention=0.0)
        self.assertNotIn("arc", c.connues)
        self.assertIn((5, "perdue", "arc"), c.historique)

    def test_petit_groupe_oublie_une_technique_trop_difficile_a_pratiquer(self):
        c = Culture()
        c.connues.add("arc")
        petit = groupe(3, savoirs={"arc"})
        c.mettre_a_jour(1, petit, [petit], random.Random(1), proba_invention=0.0, proba_oubli=1.0)
        self.assertFalse(any("arc" in p.savoirs for p in petit))


class TestEpoques(unittest.TestCase):
    def test_epoque_suit_les_techniques(self):
        c = Culture()
        self.assertEqual(c.epoque(), "Paléolithique")
        c.connues.add("agriculture")
        self.assertEqual(c.epoque(), "Néolithique")
        c.connues.add("ecriture")
        self.assertEqual(c.epoque(), "Antiquité")

    def test_changement_d_epoque_est_enregistre(self):
        c = Culture()
        c.connues.update({"agriculture"})
        personnes = groupe(3, savoirs={"agriculture"})
        c.mettre_a_jour(7, personnes, [personnes], random.Random(1), proba_invention=0.0)
        self.assertEqual(c.epoques[-1], (7, "Néolithique"))


class TestTransmission(unittest.TestCase):
    def test_savoir_passe_entre_voisins(self):
        monde = Monde(5, 5, {(x, y) for x in range(5) for y in range(5)})
        a = Personne(0, 2, 2, savoirs={"feu"})
        b = Personne(1, 3, 2)
        sim = Simulation(monde, [a, b], graine=1)
        with mock.patch.object(culture_module, "PROBA_TRANSMISSION", 1.0):
            with mock.patch("terre.simulation.PROBA_TRANSMISSION", 1.0):
                sim._echanger(a, b)
        self.assertIn("feu", b.savoirs)

    def test_groupes_separes_par_la_distance(self):
        monde = Monde(40, 1, {(x, 0) for x in range(40)})
        a = Personne(0, 0, 0)
        b = Personne(1, 1, 0)
        c = Personne(2, 30, 0)
        sim = Simulation(monde, [a, b, c], graine=1)
        tailles = sorted(len(g) for g in sim._groupes())
        self.assertEqual(tailles, [1, 2])


if __name__ == "__main__":
    unittest.main()
