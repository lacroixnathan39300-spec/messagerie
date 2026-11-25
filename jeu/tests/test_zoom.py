"""Tests du zoom (bâtiments, véhicules, aéroport), des ressources et des vols réels."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre import zoom  # noqa: E402
from terre.ressources import (case_de, charger_aeroports, charger_population, charger_villes,  # noqa: E402
                              distance_km, position_sur_trajet, ressources, vols)
from terre.terre_reelle import charger  # noqa: E402


class TestZone(unittest.TestCase):
    def test_maison_fait_10_metres(self):
        z = zoom.construire(200, densite=5, altitude=1, graine=1)
        maisons = [b for b in z.batiments if b[0] == zoom.MAISON]
        self.assertTrue(maisons)
        self.assertEqual(maisons[0][3] * zoom.PAS_M, 10)

    def test_immeuble_fait_25_metres(self):
        z = zoom.construire(200, densite=9, altitude=1, graine=1)
        immeubles = [b for b in z.batiments if b[0] == zoom.IMMEUBLE]
        self.assertEqual(immeubles[0][3] * zoom.PAS_M, 25)

    def test_zone_dense_a_plus_de_batiments_qu_une_zone_rurale(self):
        dense = zoom.construire(200, densite=9, altitude=1, graine=1)
        rurale = zoom.construire(200, densite=1, altitude=1, graine=1)
        self.assertGreater(len(dense.batiments), len(rurale.batiments))

    def test_piste_d_aeroport_de_2500_metres(self):
        z = zoom.construire(800, densite=7, altitude=1, graine=2, aeroport=True)
        longueur = sum(1 for c in z.cases if c == zoom.PISTE) // 6
        self.assertEqual(longueur * zoom.PAS_M, 2500)

    def test_vehicules_roulent_sur_les_routes(self):
        z = zoom.construire(200, densite=7, altitude=1, graine=3)
        import random
        rng = random.Random(3)
        for _ in range(20):
            zoom.avancer_vehicules(z, rng)
        for v in z.vehicules:
            if v.type != "avion":
                self.assertIn(z.type_en(v.x, v.y), (zoom.ROUTE, zoom.PISTE))

    def test_montagne_sans_population(self):
        z = zoom.construire(50, densite=0, altitude=8, graine=1)
        self.assertEqual(z.type_en(10, 10), zoom.MONTAGNE)


class TestRessources(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.monde = charger()
        cls.pop = charger_population()

    def test_paris_est_tres_peuple(self):
        i, j = case_de(48.85, 2.35)
        self.assertGreaterEqual(ressources(self.monde, self.pop, i, j)["population"], 7)

    def test_mer_sans_ressources(self):
        i, j = case_de(0, -150)
        self.assertEqual(ressources(self.monde, self.pop, i, j)["type"], "mer")

    def test_villes_et_aeroports_reels_charges(self):
        self.assertGreater(len(charger_villes()), 200)
        self.assertTrue(any(a["iata"] == "CDG" for a in charger_aeroports()))


class TestVols(unittest.TestCase):
    def test_distance_paris_londres(self):
        d = distance_km((48.85, 2.35), (51.5, -0.12))
        self.assertGreater(d, 330)
        self.assertLess(d, 360)

    def test_position_au_milieu_du_trajet(self):
        lat, lon = position_sur_trajet((48.85, 2.35), (51.5, -0.12), 0.5)
        self.assertGreater(lat, 48.85)
        self.assertLess(lat, 51.5)

    def test_vols_entre_aeroports_dans_la_plage_de_distance(self):
        aeroports = charger_aeroports()
        trajets = vols(aeroports, 20, graine=1)
        self.assertEqual(len(trajets), 20)
        for a, b in trajets:
            d = distance_km((a["lat"], a["lon"]), (b["lat"], b["lon"]))
            self.assertTrue(300 <= d <= 8000)


if __name__ == "__main__":
    unittest.main()
