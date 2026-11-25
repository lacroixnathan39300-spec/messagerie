"""Tests de finition : visualisation SVG, et critère de survie de la phase 1 (île par défaut)."""
import os
import random
import sys
import unittest
import xml.dom.minidom

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from run_demo import joueur_demo  # noqa: E402
from terre.monde import creer_ile  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.simulation import Simulation  # noqa: E402
from terre.visu import carte_svg  # noqa: E402


class TestVisu(unittest.TestCase):
    def test_carte_svg_est_du_xml_valide(self):
        monde = creer_ile(4)
        r = random.Random(4)
        cases = monde.cases_terre()
        sim = Simulation(monde, [Personne(i, *r.choice(cases)) for i in range(10)], graine=4)
        sim.tour_suivant()
        svg = carte_svg(sim)
        xml.dom.minidom.parseString(svg)  # lève une erreur si le SVG est cassé
        self.assertIn("tour 1", svg)


class TestSurvieIleParDefaut(unittest.TestCase):
    """Critère de la phase 1 : 20 personnes, île par défaut (12 buissons), une année, avec le joueur de démonstration."""

    def test_le_groupe_survit_une_annee_sur_l_ile_par_defaut(self):
        for graine in (1, 2, 3):
            monde = creer_ile(graine)
            r = random.Random(graine)
            cases = monde.cases_terre()
            personnes = [Personne(i, *r.choice(cases)) for i in range(20)]
            sim = Simulation(monde, personnes, graine, joueur=joueur_demo(graine))
            for _ in range(365):
                sim.tour_suivant()
            self.assertGreaterEqual(len(sim.vivantes()), 15, f"graine {graine}")


if __name__ == "__main__":
    unittest.main()
