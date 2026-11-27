"""Ressources et transport à l'échelle de la Terre (0,5°), avec des données réelles quand elles existent.

- Population : réelle (grille de population de globe.gl, classes 0 à 9).
- Villes et aéroports : réels (Natural Earth, OpenFlights).
- Eau, nourriture, bois, pierre, minerais : ESTIMÉS à partir du relief, de la mer et de la latitude.
  Il n'y a pas de carte de ressources hors connexion ; ces valeurs sont des indications de jeu.
- Vols : trajets réels entre aéroports réels, positionnés sur le grand cercle.
"""
import json
import math
import os
import random

DOSSIER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "donnees")


def _lire_grille(nom):
    with open(os.path.join(DOSSIER, nom), encoding="ascii") as f:
        return [l.rstrip("\n") for l in f if l.strip()]


def charger_population():
    """Renvoie la grille de population : lignes de caractères '.' ou '0' à '9'."""
    return _lire_grille("population_0_5deg.txt")


def charger_villes():
    with open(os.path.join(DOSSIER, "villes.json"), encoding="utf-8") as f:
        return json.load(f)


def charger_aeroports():
    with open(os.path.join(DOSSIER, "aeroports.json"), encoding="utf-8") as f:
        return json.load(f)


def case_de(lat, lon):
    return int((lon + 180) / 0.5) % 720, min(359, max(0, int((90 - lat) / 0.5)))


def latlon_de(i, j):
    return 90 - (j + 0.5) * 0.5, (i + 0.5) * 0.5 - 180


def ressources(monde, population, i, j):
    """Estimations de ressources pour une case de 0,5°. `monde` : Monde de terre_reelle."""
    lat, _ = latlon_de(i, j)
    pop = population[j][i]
    pop_classe = 0 if pop == "." else int(pop)
    if not monde.est_terre(i, j):
        return {"type": "mer", "population": 0}
    altitude = monde.altitude.get((i, j), 0)
    cotiere = any(not monde.est_terre(i + dx, j + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    return {
        "type": "terre",
        "population": pop_classe,
        "eau": cotiere or pop_classe >= 3,
        "nourriture": altitude <= 4,
        "bois": altitude <= 5 and 35 <= abs(lat) <= 65,
        "pierre": altitude >= 6,
        "minerais": altitude >= 8,
    }


def distance_km(a, b):
    """Distance du grand cercle entre deux points (lat, lon) en degrés, en km."""
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def position_sur_trajet(a, b, t):
    """Position à la fraction t (0 à 1) du grand cercle de a vers b, en (lat, lon)."""
    def vers_vecteur(lat, lon):
        la, lo = math.radians(lat), math.radians(lon)
        return (math.cos(la) * math.cos(lo), math.cos(la) * math.sin(lo), math.sin(la))

    va, vb = vers_vecteur(*a), vers_vecteur(*b)
    angle = math.acos(max(-1.0, min(1.0, sum(x * y for x, y in zip(va, vb)))))
    if angle == 0:
        return a
    s1, s2 = math.sin((1 - t) * angle) / math.sin(angle), math.sin(t * angle) / math.sin(angle)
    x, y, z = (s1 * p + s2 * q for p, q in zip(va, vb))
    return math.degrees(math.asin(z)), math.degrees(math.atan2(y, x))


def vols(aeroports, nombre, graine=0, distance_min=300, distance_max=8000):
    """Tire des paires d'aéroports réels reliés par un vol plausible (distance entre deux seuils)."""
    rng = random.Random(graine)
    resultat = []
    essais = 0
    while len(resultat) < nombre and essais < nombre * 200:
        essais += 1
        a, b = rng.choice(aeroports), rng.choice(aeroports)
        d = distance_km((a["lat"], a["lon"]), (b["lat"], b["lon"]))
        if distance_min <= d <= distance_max:
            resultat.append((a, b))
    return resultat
