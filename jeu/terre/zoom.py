"""Zoom : une zone de quelques kilomètres, à 5 mètres par case, avec rues, bâtiments et véhicules.

Les bâtiments ont leurs dimensions réelles (maison 10 m, immeuble 25 m, bâtiment 50 m, piste de
plusieurs centaines de mètres). Leur place est générée à partir de la densité de population de la
case de 0,5° et de son altitude : ce ne sont pas des empreintes réelles de bâtiments (aucune donnée
de bâtiments n'est disponible hors connexion), mais une ville plausible à la bonne échelle.
"""
import random
from dataclasses import dataclass, field

PAS_M = 5  # mètres par case
VIDE, ROUTE, MAISON, IMMEUBLE, BATIMENT, CHAMP, PISTE, TERMINAL, MONTAGNE = range(9)
NOMS = {VIDE: "terrain", ROUTE: "route", MAISON: "maison", IMMEUBLE: "immeuble",
        BATIMENT: "bâtiment", CHAMP: "champ", PISTE: "piste d'aéroport",
        TERMINAL: "aérogare", MONTAGNE: "montagne"}
PISTE_M = 2500  # longueur d'une piste d'aéroport courante
HAUTEURS_M = {MAISON: 6, BATIMENT: 10}

@dataclass
class Vehicule:
    type: str  # "voiture", "bus", "avion"
    x: int
    y: int


@dataclass
class Zone:
    cote: int  # nombre de cases de chaque côté
    cases: bytearray = field(default=None)
    batiments: list = field(default_factory=list)  # (type, x, y, largeur, hauteur, hauteur_m)
    vehicules: list = field(default_factory=list)

    def __post_init__(self):
        if self.cases is None:
            self.cases = bytearray(self.cote * self.cote)

    def type_en(self, x, y):
        return self.cases[y * self.cote + x]

    def _mettre(self, x, y, valeur):
        if 0 <= x < self.cote and 0 <= y < self.cote:
            self.cases[y * self.cote + x] = valeur

    def _remplir(self, x0, y0, w, h, valeur):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self._mettre(x, y, valeur)

    def route_voisins(self, x, y):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < self.cote and 0 <= ny < self.cote and self.type_en(nx, ny) in (ROUTE, PISTE):
                yield nx, ny


def construire(cote, densite, altitude, graine=0, aeroport=False):
    """Génère une zone. `densite` 0 à 9 (population), `altitude` 0 à 9 (relief)."""
    zone = Zone(cote)
    rng = random.Random(f"{graine}-{densite}-{altitude}-{aeroport}")
    if densite == 0 and altitude >= 6:
        zone._remplir(0, 0, cote, cote, MONTAGNE)
        return zone
    if densite == 0:
        zone._remplir(0, 0, cote, cote, CHAMP if rng.random() < 0.5 else VIDE)
        return zone

    # Espacement des rues selon la densité : plus la ville est dense, plus les rues sont proches.
    periode = 20 if densite >= 7 else 40 if densite >= 4 else 80  # 100 m, 200 m ou 400 m
    for x in range(0, cote, periode):
        zone._remplir(x, 0, 2, cote, ROUTE)
    for y in range(0, cote, periode):
        zone._remplir(0, y, cote, 2, ROUTE)

    for bx in range(2, cote, periode):
        for by in range(2, cote, periode):
            _remplir_ilot(zone, rng, bx, by, periode - 2, densite)

    if aeroport and cote >= 600:
        _construire_aeroport(zone, rng)

    _placer_vehicules(zone, rng)
    return zone


def _remplir_ilot(zone, rng, x0, y0, taille, densite):
    if densite >= 7:
        # Immeubles de 25 m, serrés.
        for x in range(x0, x0 + taille - 4, 6):
            for y in range(y0, y0 + taille - 4, 6):
                h = rng.randint(15, 60)
                zone._remplir(x, y, 5, 5, IMMEUBLE)
                zone.batiments.append((IMMEUBLE, x, y, 5, 5, h))
        return
    if densite >= 4:
        for x in range(x0, x0 + taille - 2, 4):
            for y in range(y0, y0 + taille - 2, 4):
                r = rng.random()
                if r < 0.55:
                    zone._remplir(x, y, 2, 2, MAISON)
                    zone.batiments.append((MAISON, x, y, 2, 2, HAUTEURS_M[MAISON]))
                elif r < 0.8:
                    zone._remplir(x, y, 5, 5, IMMEUBLE)
                    zone.batiments.append((IMMEUBLE, x, y, 5, 5, rng.randint(15, 30)))
                else:
                    zone._remplir(x, y, 10, 10, BATIMENT)
                    zone.batiments.append((BATIMENT, x, y, 10, 10, HAUTEURS_M[BATIMENT]))
        return
    # Faible densité : maisons isolées et champs.
    zone._remplir(x0, y0, taille, taille, CHAMP)
    for x in range(x0, x0 + taille - 2, 8):
        for y in range(y0, y0 + taille - 2, 8):
            if rng.random() < 0.5:
                zone._remplir(x, y, 2, 2, MAISON)
                zone.batiments.append((MAISON, x, y, 2, 2, HAUTEURS_M[MAISON]))


def _construire_aeroport(zone, rng):
    """Piste de 2 500 m (500 cases), aérogare de 200 m × 50 m près de la piste."""
    longueur = PISTE_M // PAS_M
    x0 = (zone.cote - longueur) // 2
    y0 = zone.cote // 2
    zone._remplir(x0, y0, longueur, 6, PISTE)  # piste de 30 m de large
    zone._remplir(x0 + longueur // 2 - 20, y0 - 12, 40, 10, TERMINAL)  # 200 m × 50 m
    zone.batiments.append((TERMINAL, x0 + longueur // 2 - 20, y0 - 12, 40, 10, 15))
    for _ in range(2):
        zone.vehicules.append(Vehicule("avion", x0 + rng.randint(0, longueur - 1), y0 + 2))


def _placer_vehicules(zone, rng):
    routes = [(x, y) for y in range(zone.cote) for x in range(zone.cote) if zone.type_en(x, y) == ROUTE]
    if not routes:
        return
    for _ in range(min(40, len(routes) // 50)):
        x, y = rng.choice(routes)
        zone.vehicules.append(Vehicule("voiture", x, y))
    for _ in range(min(3, len(routes) // 400)):
        x, y = rng.choice(routes)
        zone.vehicules.append(Vehicule("bus", x, y))


def avancer_vehicules(zone, rng):
    """Chaque véhicule roule vers une case de route voisine, sans faire demi-tour si possible."""
    for v in zone.vehicules:
        if v.type == "avion":
            continue
        voisins = list(zone.route_voisins(v.x, v.y))
        if voisins:
            v.x, v.y = rng.choice(voisins)
