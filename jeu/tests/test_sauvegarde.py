"""Tests de la sauvegarde locale : une partie rechargée doit reprendre exactement là où elle était."""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre.monde import creer_ile  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.sauvegarde import charger, sauvegarder  # noqa: E402
from terre.simulation import Simulation  # noqa: E402
from run_demo import joueur_demo  # noqa: E402


def partie(graine=2, tours=120):
    monde = creer_ile(graine, nb_buissons=30)
    cases = monde.cases_terre()
    import random
    r = random.Random(graine)
    personnes = [Personne(i, *r.choice(cases)) for i in range(15)]
    sim = Simulation(monde, personnes, graine, joueur=joueur_demo(graine))
    for _ in range(tours):
        sim.tour_suivant()
    return sim


class TestSauvegarde(unittest.TestCase):
    def setUp(self):
        self.dossier = tempfile.mkdtemp()
        self.chemin = os.path.join(self.dossier, "partie.json")

    def test_sauvegarde_puis_chargement_donne_le_meme_bilan(self):
        sim = partie()
        sauvegarder(sim, self.chemin)
        recharge = charger(self.chemin, joueur=joueur_demo(2))
        self.assertEqual(sim.bilan(), recharge.bilan())

    def test_la_partie_reprend_de_facon_identique(self):
        sim = partie()
        sauvegarder(sim, self.chemin)
        recharge = charger(self.chemin, joueur=joueur_demo(2))
        for _ in range(60):
            sim.tour_suivant()
            recharge.tour_suivant()
        self.assertEqual(sim.bilan(), recharge.bilan())

    def test_fichier_lisible_et_sans_fichier_temporaire(self):
        sauvegarder(partie(tours=5), self.chemin)
        self.assertTrue(os.path.exists(self.chemin))
        self.assertFalse(os.path.exists(self.chemin + ".tmp"))

    def test_version_inconnue_refusee(self):
        with open(self.chemin, "w", encoding="utf-8") as f:
            f.write('{"version": 99}')
        with self.assertRaises(ValueError):
            charger(self.chemin)


if __name__ == "__main__":
    unittest.main()
