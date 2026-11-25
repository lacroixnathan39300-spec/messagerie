"""Boucle de simulation : à chaque tour, les personnes vieillissent, décident et agissent."""
import random

RAYON_VISION = 6
PERTE_FAIM = 0.5
PERTE_ENERGIE = 0.3
REPOS = 1.0
REPAS = 30.0
ENERGIE_MAX = 100.0


class Simulation:
    def __init__(self, monde, personnes, graine=0):
        self.monde = monde
        self.personnes = list(personnes)
        self.tour = 0
        self.rng = random.Random(graine)
        self.morts = []  # liste de (tour, identifiant, cause)

    def vivantes(self):
        return [p for p in self.personnes if p.vivante]

    def tour_suivant(self):
        self.tour += 1
        for p in self.vivantes():
            p.age += 1
            p.faim += PERTE_FAIM
        for p in self.vivantes():
            self._agir(p)
        for p in self.vivantes():
            if p.meurt_de_faim():
                self._mourir(p, "faim")
            elif p.meurt_de_vieillesse():
                self._mourir(p, "vieillesse")
        self.monde.repousser()

    def bilan(self):
        vivantes = self.vivantes()
        faim_moyenne = sum(p.faim for p in vivantes) / len(vivantes) if vivantes else 0.0
        return {
            "tour": self.tour,
            "vivantes": len(vivantes),
            "morts": len(self.morts),
            "faim_moyenne": round(faim_moyenne, 1),
        }

    def _agir(self, p):
        if p.a_faim():
            self._chercher_nourriture(p)
        elif p.est_fatigue():
            p.energie = min(ENERGIE_MAX, p.energie + REPOS)
        else:
            self._errer(p)
            p.energie = max(0.0, p.energie - PERTE_ENERGIE)

    def _chercher_nourriture(self, p):
        cible = self._buisson_proche(p)
        if cible is None:
            self._errer(p)
        elif cible == p.case:
            if self.monde.cueillir(cible):
                p.faim = max(0.0, p.faim - REPAS)
        else:
            self._pas_vers(p, cible)
        p.energie = max(0.0, p.energie - PERTE_ENERGIE)

    def _buisson_proche(self, p):
        candidats = [
            case for case, quantite in self.monde.nourriture.items()
            if quantite >= 1 and self._distance(p.case, case) <= RAYON_VISION
        ]
        if not candidats:
            return None
        return min(candidats, key=lambda case: (self._distance(p.case, case), case))

    def _pas_vers(self, p, cible):
        meilleurs = [
            voisin for voisin in self.monde.voisins_terre(p.x, p.y)
            if self._distance(voisin, cible) < self._distance(p.case, cible)
        ]
        if meilleurs:
            p.x, p.y = min(meilleurs)
        else:
            self._errer(p)

    def _errer(self, p):
        voisins = sorted(self.monde.voisins_terre(p.x, p.y))
        if voisins:
            p.x, p.y = self.rng.choice(voisins)

    def _mourir(self, p, cause):
        p.vivante = False
        self.morts.append((self.tour, p.identifiant, cause))

    @staticmethod
    def _distance(a, b):
        return abs(a[0] - b[0]) + abs(a[1] - b[1])
