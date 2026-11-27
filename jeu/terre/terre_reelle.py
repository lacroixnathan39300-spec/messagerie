"""La Terre réelle, à l'échelle de 0,5° (environ 55 km à l'équateur par case).

Les données viennent de `terre/donnees/terre_0_5deg.txt`, construit une fois par
`tools/generer_terre.py`. Aucune connexion ni dépendance externe n'est nécessaire.

Chaque case de terre a une altitude (classe 0 à 9). Les chaînes montagneuses sont les classes hautes :
les personnes les traversent plus difficilement (voir `Simulation._errer`).
"""
import os
import random

from .monde import NOURRITURE_MAX, Monde

CHEMIN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "donnees", "terre_0_5deg.txt")
MONTAGNE = 6  # classe à partir de laquelle la case est considérée comme montagneuse
PROBA_BUISSON_TERRE = 0.03


def charger(chemin=CHEMIN, buissons=True):
    """Renvoie un Monde (720 × 360 cases) avec l'altitude et l'eau de chaque case de terre.
    Par défaut, des buissons de nourriture sont semés pour que la carte soit jouable."""
    with open(chemin, encoding="ascii") as f:
        lignes = [l.rstrip("\n") for l in f if l.strip()]
    hauteur = len(lignes)
    largeur = len(lignes[0])
    terre, altitude = set(), {}
    for j, ligne in enumerate(lignes):
        for i, c in enumerate(ligne):
            if c != ".":
                terre.add((i, j))
                altitude[(i, j)] = int(c)
    monde = Monde(largeur, hauteur, terre)
    monde.altitude = altitude
    monde.grille = "0.5deg"
    monde.population = _lire_population(CHEMIN_POPULATION)
    monde.points_eau = _points_eau(monde)
    if buissons:
        semer_buissons(monde, 0)
    return monde


CHEMIN_POPULATION = os.path.join(os.path.dirname(os.path.abspath(__file__)), "donnees", "population_0_5deg.txt")


def _lire_population(chemin):
    """Dictionnaire {case: classe de population (1 à 9)} ; vide si le fichier manque."""
    if not os.path.exists(chemin):
        return {}
    population = {}
    with open(chemin, encoding="ascii") as f:
        for j, ligne in enumerate(l.rstrip("\n") for l in f):
            for i, c in enumerate(ligne):
                if c != ".":
                    population[(i, j)] = int(c)
    return population


def _points_eau(monde):
    """Eau douce estimée : les côtes, et les cases où vit une population d'au moins la classe 3.
    Les rivières ne sont pas encore modélisées."""
    eau = {c for c in monde.cases_terre()
           if any(not monde.est_terre(c[0] + dx, c[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    eau |= {c for c, classe in monde.population.items() if classe >= 3 and monde.est_terre(*c)}
    return eau


def semer_buissons(monde, graine=0):
    """Place des buissons sur la terre, plus rarement sur les montagnes."""
    rng = random.Random(graine)
    for case in monde.cases_terre():
        proba = PROBA_BUISSON_TERRE * (0.3 if monde.altitude.get(case, 0) >= MONTAGNE else 1.0)
        if rng.random() < proba:
            monde.nourriture[case] = 1 + rng.random() * (NOURRITURE_MAX - 1)


def couleur(altitude):
    """Couleur d'une case de terre selon son altitude (vert, puis brun, puis blanc en altitude)."""
    palette = [
        (120, 170, 90), (135, 180, 95), (160, 185, 100), (185, 185, 110), (195, 175, 115),
        (180, 150, 105), (160, 130, 95), (140, 115, 95), (200, 195, 190), (250, 250, 250),
    ]
    return palette[min(9, max(0, altitude))]


def ecrire_png(chemin, largeur, hauteur, pixels):
    """Écrit une image PNG RGB sans bibliothèque externe. `pixels` : liste de lignes de (r, g, b)."""
    import struct
    import zlib

    def bloc(type_, donnees):
        corps = type_ + donnees
        return struct.pack(">I", len(donnees)) + corps + struct.pack(">I", zlib.crc32(corps) & 0xFFFFFFFF)

    brut = bytearray()
    for ligne in pixels:
        brut.append(0)  # filtre « aucun » pour cette ligne
        for r, g, b in ligne:
            brut += bytes((r, g, b))
    png = (b"\x89PNG\r\n\x1a\n"
           + bloc(b"IHDR", struct.pack(">IIBBBBB", largeur, hauteur, 8, 2, 0, 0, 0))
           + bloc(b"IDAT", zlib.compress(bytes(brut), 9))
           + bloc(b"IEND", b""))
    with open(chemin, "wb") as f:
        f.write(png)


def carte_png(monde, chemin, echelle=2):
    """Image de la Terre, mer bleue, terre colorée par altitude, agrandie de `echelle`."""
    mer = (150, 190, 220)
    lignes = []
    for j in range(monde.hauteur):
        ligne = []
        for i in range(monde.largeur):
            if monde.est_terre(i, j):
                ligne.append(couleur(monde.altitude.get((i, j), 0)))
            else:
                ligne.append(mer)
        lignes.append(ligne)
    grande = []
    for ligne in lignes:
        agrandie = [p for p in ligne for _ in range(echelle)]
        for _ in range(echelle):
            grande.append(agrandie)
    ecrire_png(chemin, monde.largeur * echelle, monde.hauteur * echelle, grande)
