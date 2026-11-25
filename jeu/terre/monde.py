"""Carte de l'île : cases de terre et buissons de nourriture."""
import random

NOURRITURE_MAX = 3


class Monde:
    def __init__(self, largeur, hauteur, terre):
        self.largeur = largeur
        self.hauteur = hauteur
        self._terre = set(terre)
        self.nourriture = {}  # (x, y) -> quantité entre 0 et NOURRITURE_MAX
        # Points d'eau douce. None = la soif n'est pas simulée sur cette carte.
        self.points_eau = None
        # Changements faits par le joueur : (x, y) -> True (terre ajoutée) ou False (mer ajoutée).
        self.modifs = {}
        # Montagnes posées par le joueur (sous-ensemble des cases de terre).
        self.montagnes = set()

    def poser_terre(self, x, y, terre):
        if terre:
            self._terre.add((x, y))
        else:
            self._terre.discard((x, y))
        self.modifs[(x, y)] = terre

    def est_terre(self, x, y):
        return (x, y) in self._terre

    def cases_terre(self):
        return sorted(self._terre)

    def voisins_terre(self, x, y):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) in self._terre:
                yield (x + dx, y + dy)

    def repousser(self, taux=0.02):
        """Chaque buisson regagne un peu de nourriture à chaque tour."""
        for case, quantite in self.nourriture.items():
            if quantite < NOURRITURE_MAX:
                self.nourriture[case] = min(NOURRITURE_MAX, quantite + taux)

    def cueillir(self, case):
        """Retire une unité de nourriture. Renvoie True si c'était possible."""
        if self.nourriture.get(case, 0) >= 1:
            self.nourriture[case] -= 1
            return True
        return False


def creer_ile(graine, largeur=40, hauteur=40, nb_buissons=12):
    """Crée une île irrégulière, toujours la même pour une graine donnée."""
    rng = random.Random(graine)
    cx, cy = (largeur - 1) / 2, (hauteur - 1) / 2
    rayon = min(largeur, hauteur) / 2 - 3
    terre = set()
    for y in range(hauteur):
        for x in range(largeur):
            distance = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if distance + rng.uniform(-1.5, 1.5) < rayon:
                terre.add((x, y))
    monde = Monde(largeur, hauteur, terre)
    for case in rng.sample(sorted(terre), min(nb_buissons, len(terre))):
        monde.nourriture[case] = NOURRITURE_MAX
    return monde
