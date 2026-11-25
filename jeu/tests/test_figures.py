"""Tests de la phase 6 : figures qui décident et parlent à partir de leur mémoire, hors connexion."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre.figures import Figure, politique  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.priere import Priere  # noqa: E402


class TestDecisions(unittest.TestCase):
    def test_une_figure_qui_a_vu_ses_prieres_refusees_refuse(self):
        p = Personne(0, 0, 0)
        p.ajouter_souvenir("sa prière a été refusée (faim)", 1.0)
        self.assertEqual(Figure(p, "Anna").choix_priere(), "refuser")

    def test_une_figure_comblee_exauce(self):
        p = Personne(0, 0, 0)
        p.ajouter_souvenir("sa prière a été exaucée (faim)", 1.0)
        self.assertEqual(Figure(p, "Anna").choix_priere(), "exaucer")

    def test_la_prudence_retient_le_refus(self):
        p = Personne(0, 0, 0)
        p.ajouter_souvenir("sa prière a été refusée (faim)", 0.5)
        self.assertEqual(Figure(p, "Anna", {"prudence": 0.9}).choix_priere(), "modifier")

    def test_sans_souvenir_la_figure_modifie(self):
        self.assertEqual(Figure(Personne(0, 0, 0), "Anna").choix_priere(), "modifier")

    def test_les_souvenirs_recents_pesent_selon_leur_poids(self):
        p = Personne(0, 0, 0)
        p.ajouter_souvenir("sa prière a été refusée (faim)", 0.2)
        p.ajouter_souvenir("sa prière a été exaucée (faim)", 1.0)
        self.assertEqual(Figure(p, "Anna").choix_priere(), "exaucer")


class TestParole(unittest.TestCase):
    def test_la_figure_cite_son_souvenir_dominant(self):
        p = Personne(0, 0, 0)
        p.ajouter_souvenir("a trouvé à manger en (2, 2)", 0.4)
        p.ajouter_souvenir("sa prière a été refusée (faim)", 0.9)
        phrase = Figure(p, "Anna").dire()
        self.assertIn("sa prière a été refusée (faim)", phrase)

    def test_sans_souvenir_elle_n_a_rien_a_dire(self):
        self.assertIn("rien à dire", Figure(Personne(0, 0, 0), "Anna").dire())


class TestPolitique(unittest.TestCase):
    def test_politique_suit_la_figure_et_ignore_les_autres(self):
        p = Personne(7, 0, 0)
        p.ajouter_souvenir("sa prière a été refusée (faim)", 1.0)
        repondre = politique([Figure(p, "Anna")])
        priere = Priere(0, 7, "faim", 0.9, 1)
        self.assertEqual(repondre(priere, None), "refuser")
        autre = Priere(1, 8, "faim", 0.9, 1)
        self.assertEqual(repondre(autre, None), "ignorer")


if __name__ == "__main__":
    unittest.main()
