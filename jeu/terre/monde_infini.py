"""Mondes générés à la demande : terrain procédural sans bord, ou carte fournie par l'utilisateur.

- `MondeInfini` : terre générée par morceaux (chunks) de 64 cases, à partir d'une graine. Le monde
  n'a pas de bord : on génère seulement ce que les personnes atteignent. Deux mondes avec la même
  graine et la même forme sont identiques.
- `monde_depuis_pgm` : une carte que tu fournis (image PGM, format texte P2 que tout éditeur d'image
  peut exporter, par exemple une projection de la Terre). Blanc = terre, noir = mer.

Une vraie carte de la Terre au 1:1 (environ 40 000 × 20 000 km) ne tient pas en mémoire d'un téléphone
case par case. Avec une case = 1 km ce serait 800 millions de cases. Le monde infini ne charge que
ce qui compte, et une carte fournie peut être générée à une échelle plus grossière.
"""
import random
from functools import lru_cache

from .monde import NOURRITURE_MAX, Monde

TAILLE_CHUNK = 64
PAS_GRAND = 24  # taille des grandes formes (continents)
PAS_PETIT = 8  # taille des détails (côtes)
SEUIL_TERRE = 0.5
PROBA_BUISSON = 0.02
FORMES = ("continents", "ile")


@lru_cache(maxsize=None)
def _valeur(graine, gx, gy, pas):
    return random.Random(f"{graine}-{pas}-{gx}-{gy}").random()


def _bruit(graine, x, y, pas):
    gx, gy = x // pas, y // pas
    fx, fy = (x - gx * pas) / pas, (y - gy * pas) / pas
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = _valeur(graine, gx, gy, pas)
    b = _valeur(graine, gx + 1, gy, pas)
    c = _valeur(graine, gx, gy + 1, pas)
    d = _valeur(graine, gx + 1, gy + 1, pas)
    haut = a + (b - a) * sx
    bas = c + (d - c) * sx
    return haut + (bas - haut) * sy


class MondeInfini:
    def __init__(self, graine=0, forme="continents", seuil=SEUIL_TERRE, limite=None):
        """`limite` : (largeur, hauteur) pour un monde borné, centré sur l'origine. Sinon, sans bord."""
        if forme not in FORMES:
            raise ValueError(f"forme inconnue : {forme}")
        self.graine = graine
        self.forme = forme
        self.seuil = seuil
        self.limite = limite
        self.largeur, self.hauteur = limite if limite else (None, None)
        self.nourriture = {}
        self._chunks_charges = set()

    def _hauteur_brute(self, x, y):
        h = 0.65 * _bruit(self.graine, x, y, PAS_GRAND) + 0.35 * _bruit(self.graine + 1, x, y, PAS_PETIT)
        if self.forme == "ile":
            h -= 0.6 * ((x / 60.0) ** 2 + (y / 60.0) ** 2)
        return h

    def _dans_limite(self, x, y):
        if not self.limite:
            return True
        L, H = self.limite
        return -L // 2 <= x < L - L // 2 and -H // 2 <= y < H - H // 2

    def _est_terre_brut(self, x, y):
        return self._dans_limite(x, y) and self._hauteur_brute(x, y) > self.seuil

    def _charger(self, cx, cy):
        """Place les buissons d'un morceau, une seule fois, de façon déterministe."""
        if (cx, cy) in self._chunks_charges:
            return
        self._chunks_charges.add((cx, cy))
        rng = random.Random(f"{self.graine}-buissons-{cx}-{cy}")
        for x in range(cx * TAILLE_CHUNK, (cx + 1) * TAILLE_CHUNK):
            for y in range(cy * TAILLE_CHUNK, (cy + 1) * TAILLE_CHUNK):
                quantite = 1 + rng.random() * (NOURRITURE_MAX - 1)
                # Un buisson déjà connu (vidé ou restauré depuis une sauvegarde) n'est jamais réinitialisé.
                if (self._est_terre_brut(x, y) and rng.random() < PROBA_BUISSON
                        and (x, y) not in self.nourriture):
                    self.nourriture[(x, y)] = quantite

    def _charger_autour(self, x, y):
        cx, cy = x // TAILLE_CHUNK, y // TAILLE_CHUNK
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                self._charger(cx + dx, cy + dy)

    def est_terre(self, x, y):
        self._charger_autour(x, y)
        return self._est_terre_brut(x, y)

    def voisins_terre(self, x, y):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if self.est_terre(x + dx, y + dy):
                yield (x + dx, y + dy)

    def cases_terre(self, rayon=TAILLE_CHUNK):
        """Cases de terre près de l'origine (pour placer les personnes au départ)."""
        return [(x, y) for x in range(-rayon, rayon) for y in range(-rayon, rayon)
                if self.est_terre(x, y)]

    def repousser(self, taux=0.02):
        for case, quantite in self.nourriture.items():
            if quantite < NOURRITURE_MAX:
                self.nourriture[case] = min(NOURRITURE_MAX, quantite + taux)

    def cueillir(self, case):
        if self.nourriture.get(case, 0) >= 1:
            self.nourriture[case] -= 1
            return True
        return False


def monde_depuis_pgm(chemin, largeur, hauteur):
    """Charge une carte PGM (P2, texte). Pixel clair = terre, sombre = mer. Mise à l'échelle au besoin."""
    with open(chemin, encoding="ascii") as f:
        lignes = [l.split("#", 1)[0] for l in f]
    jetons = " ".join(lignes).split()
    if not jetons or jetons[0] != "P2":
        raise ValueError("format non pris en charge : il faut une image PGM texte (P2)")
    w, h, maxval = int(jetons[1]), int(jetons[2]), int(jetons[3])
    pixels = [int(v) for v in jetons[4:4 + w * h]]
    if len(pixels) != w * h:
        raise ValueError("image PGM incomplète")
    terre = set()
    for y in range(hauteur):
        for x in range(largeur):
            sx = x * w // largeur
            sy = y * h // hauteur
            if pixels[sy * w + sx] * 255 // maxval > 127:
                terre.add((x, y))
    return Monde(largeur, hauteur, terre)
