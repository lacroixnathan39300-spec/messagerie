"""Tests des systèmes de vie : soif, sciences, énergie et électricité, accidents, histoire et sauvegarde."""
import os
import random
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from terre import culture as culture_module  # noqa: E402
from terre import economie  # noqa: E402
from terre.culture import Culture, TECHNIQUES  # noqa: E402
from terre.histoire import chronique  # noqa: E402
from terre.monde import Monde  # noqa: E402
from terre.personne import Personne  # noqa: E402
from terre.sauvegarde import charger, sauvegarder  # noqa: E402
from terre.simulation import Simulation  # noqa: E402
from terre.terre_reelle import charger as charger_terre  # noqa: E402


def ligne(longueur):
    return Monde(longueur, 1, {(x, 0) for x in range(longueur)})


class TestSoif(unittest.TestCase):
    def test_sans_eau_definie_pas_de_soif(self):
        monde = ligne(10)
        p = Personne(0, 5, 0, soif=99)
        sim = Simulation(monde, [p], graine=1)
        sim.tour_suivant()
        self.assertTrue(p.vivante)

    def test_une_personne_assoiffee_meurt_loin_de_l_eau(self):
        monde = ligne(200)
        monde.points_eau = {(0, 0)}
        p = Personne(0, 150, 0, soif=0)
        sim = Simulation(monde, [p], graine=1)
        for _ in range(200):
            sim.tour_suivant()
            if not p.vivante:
                break
        self.assertFalse(p.vivante)
        self.assertEqual(sim.morts[0][2], "soif")

    def test_une_personne_assoiffee_va_boire_au_point_d_eau_proche(self):
        monde = ligne(30)
        monde.points_eau = {(10, 0)}
        p = Personne(0, 5, 0, soif=80)
        sim = Simulation(monde, [p], graine=1)
        sim._chercher_eau(p)
        self.assertEqual(p.case, (6, 0))

    def test_boire_calme_la_soif(self):
        monde = ligne(5)
        monde.points_eau = {(2, 0)}
        p = Personne(0, 2, 0, soif=70)
        sim = Simulation(monde, [p], graine=1)
        sim._chercher_eau(p)
        self.assertLess(p.soif, 70)


class TestSciences(unittest.TestCase):
    def test_l_electricite_exige_la_physique(self):
        c = Culture()
        c.connues.add("electricite")
        personnes = [Personne(i, 0, 0, savoirs=set()) for i in range(200)]
        c.mettre_a_jour(1, personnes, [personnes], random.Random(1), proba_invention=1.0)
        self.assertNotIn("electricite", c.connues - {"electricite"})
        self.assertIn("physique", TECHNIQUES["electricite"].prerequis)

    def test_une_science_apparait_dans_un_grand_groupe_instruit(self):
        c = Culture()
        c.connues.update({"ecriture", "mathematiques"})
        groupe = [Personne(i, 0, 0, savoirs={"ecriture"}) for i in range(60)]
        c.mettre_a_jour(2, groupe, [groupe], random.Random(1), proba_invention=1.0)
        self.assertIn("astronomie", c.connues)

    def test_l_ere_numerique_suit_l_informatique(self):
        c = Culture()
        c.connues.add("informatique")
        self.assertEqual(c.epoque(), "Ère numérique")


class TestEnergie(unittest.TestCase):
    def test_sans_technique_il_n_y_a_pas_d_electricite(self):
        monde = ligne(20)
        groupe = [Personne(i, i, 0) for i in range(6)]
        sim = Simulation(monde, groupe, graine=1)
        sim.culture.connues.add("feu")
        sim._attribuer_langues()
        bilans = economie.bilan_energie(sim)
        self.assertTrue(bilans)
        self.assertNotIn("nucleaire", bilans[0]["sources"])

    def test_pas_assez_de_production_donne_une_penurie(self):
        monde = ligne(20)
        groupe = [Personne(i, i, 0) for i in range(6)]
        sim = Simulation(monde, groupe, graine=1)
        sim.culture.connues.update({"feu", "electricite", "physique", "mathematiques"})
        sim.culture._epoque = "Époque nucléaire"
        with mock.patch.dict(economie.CONSOMMATION, {"Époque nucléaire": 1000.0}):
            sim._attribuer_langues()
            bilan = economie.bilan_energie(sim)[0]
        self.assertEqual(bilan["statut"], "pénurie")


class TestAccidents(unittest.TestCase):
    def test_un_accident_nucleaire_est_consigne_et_blesse_les_voisins(self):
        monde = ligne(20)
        groupe = [Personne(i, i % 3, 0, energie=80) for i in range(6)]
        sim = Simulation(monde, groupe, graine=1)
        sim.culture.connues.update({"feu", "electricite", "physique", "physique_nucleaire"})
        sim.culture._epoque = "Époque nucléaire"
        sim._attribuer_langues()
        for p in groupe:
            p.langue = "x"
        with mock.patch.dict(economie.CONSOMMATION, {"Époque nucléaire": 0.0}), \
                mock.patch.object(economie, "PROBA_ACCIDENT_NUCLEAIRE", 1.0), \
                mock.patch.object(economie, "sources_disponibles", return_value=["nucleaire"]):
            economie.verifier_accidents(sim)
        self.assertEqual(len(sim.accidents), 1)
        self.assertLess(groupe[0].energie, 80)


class TestHistoire(unittest.TestCase):
    def test_la_chronique_raconte_les_decouvertes_et_les_epoques(self):
        monde = ligne(20)
        groupe = [Personne(i, i % 4, 0) for i in range(30)]
        sim = Simulation(monde, groupe, graine=2)
        for _ in range(3):
            sim.tour_suivant()
        sim.culture.historique.append((5, "inventée", "feu"))
        sim.culture.epoques.append((9, "Néolithique"))
        textes = [phrase for _, phrase in chronique(sim)]
        self.assertIn("Découverte : feu", textes)
        self.assertIn("Entrée à l'époque : Néolithique", textes)


class TestSauvegardeDesSystemes(unittest.TestCase):
    def test_la_soif_et_les_donnees_de_la_terre_sont_sauvegardees(self):
        monde = charger_terre()
        p = Personne(0, *monde.cases_terre()[1000], soif=42.0)
        sim = Simulation(monde, [p], graine=1)
        dossier = tempfile.mkdtemp()
        chemin = os.path.join(dossier, "partie.json")
        sauvegarder(sim, chemin)
        recharge = charger(chemin)
        self.assertAlmostEqual(recharge.personnes[0].soif, 42.0)
        self.assertEqual(recharge.monde.grille, "0.5deg")
        self.assertEqual(len(recharge.monde.altitude), len(monde.altitude))
        self.assertEqual(recharge.monde.points_eau, monde.points_eau)


class TestTerreAvecVie(unittest.TestCase):
    def test_un_groupe_sur_la_terre_reelle_survit_quelques_mois(self):
        monde = charger_terre()
        rng = random.Random(4)
        cotes = sorted(monde.points_eau)
        personnes = [Personne(i, *rng.choice(cotes)) for i in range(20)]
        sim = Simulation(monde, personnes, graine=4)
        for _ in range(120):
            sim.tour_suivant()
        self.assertGreater(len(sim.vivantes()), 10)


if __name__ == "__main__":
    unittest.main()
