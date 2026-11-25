"""Tests des langues inventées : création, dérive, emprunts, traduction et attribution aux peuples."""
import os
import random
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre import langue  # noqa: E402
from terre.monde import Monde  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.simulation import Simulation  # noqa: E402


class TestLangue(unittest.TestCase):
    def test_une_langue_est_deterministe(self):
        self.assertEqual(langue.creer(1, "7"), langue.creer(1, "7"))

    def test_deux_peuples_ont_des_langues_differentes(self):
        a, b = langue.creer(1, "7"), langue.creer(1, "8")
        self.assertNotEqual(a["lexique"], b["lexique"])

    def test_tous_les_concepts_ont_un_mot_unique(self):
        lex = langue.creer(3, "1")["lexique"]
        self.assertEqual(set(lex), set(langue.CONCEPTS))
        self.assertEqual(len(set(lex.values())), len(lex))

    def test_derive_change_un_seul_mot(self):
        l = langue.creer(2, "1")
        avant = dict(l["lexique"])
        langue.deriver(l, random.Random(4))
        changes = [c for c in avant if avant[c] != l["lexique"][c]]
        self.assertEqual(len(changes), 1)

    def test_emprunt_copie_un_mot_du_voisin(self):
        a, b = langue.creer(1, "1"), langue.creer(1, "2")
        rng = random.Random(9)
        langue.emprunter(a, b, rng)
        self.assertTrue(any(a["lexique"][c] == b["lexique"][c] for c in langue.CONCEPTS))

    def test_traduction_seulement_pour_les_concepts_appris(self):
        l = langue.creer(1, "1")
        mot = l["lexique"]["eau"]
        self.assertIn("(sens inconnu)", langue.traduire(mot, l, set()))
        self.assertEqual(langue.traduire(mot, l, {"eau"}), "eau")

    def test_mot_inconnu(self):
        self.assertIn("mot inconnu", langue.traduire("xyzxyz", langue.creer(1, "1"), set()))


class TestLanguesDansLaSimulation(unittest.TestCase):
    def test_un_peuple_recoit_une_langue_et_l_enfant_l_herite(self):
        monde = Monde(10, 1, {(x, 0) for x in range(10)})
        groupe = [Personne(i, i % 3, 0) for i in range(6)]
        sim = Simulation(monde, groupe, graine=1)
        sim._attribuer_langues()
        self.assertEqual(len({p.langue for p in groupe}), 1)
        self.assertTrue(groupe[0].langue)
        self.assertIn(groupe[0].langue, sim.langues)

    def test_la_langue_est_sauvegardee_avec_la_partie(self):
        monde = Monde(10, 1, {(x, 0) for x in range(10)})
        groupe = [Personne(i, i % 3, 0) for i in range(6)]
        sim = Simulation(monde, groupe, graine=1)
        sim._attribuer_langues()
        self.assertTrue(sim.langues)


if __name__ == "__main__":
    unittest.main()
