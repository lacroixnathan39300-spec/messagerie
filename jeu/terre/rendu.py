"""Images PNG : la Terre avec villes, aéroports et avions ; une zone zoomée avec bâtiments et véhicules."""
from .terre_reelle import couleur, ecrire_png
from .zoom import (BATIMENT, CHAMP, IMMEUBLE, MAISON, MONTAGNE, PISTE, ROUTE, TERMINAL, VIDE)

MER = (150, 190, 220)
COULEUR_ZONE = {
    VIDE: (232, 232, 220), ROUTE: (90, 90, 95), MAISON: (210, 120, 80), IMMEUBLE: (150, 150, 170),
    BATIMENT: (110, 110, 130), CHAMP: (200, 220, 140), PISTE: (60, 60, 60), TERMINAL: (240, 200, 60),
    MONTAGNE: (150, 120, 100),
}
COULEUR_VEHICULE = {"voiture": (250, 250, 250), "bus": (255, 200, 0), "avion": (220, 30, 30)}


def _dessiner_point(image, largeur, hauteur, x, y, couleur_point, rayon=1):
    for dy in range(-rayon, rayon + 1):
        for dx in range(-rayon, rayon + 1):
            px, py = x + dx, y + dy
            if 0 <= px < largeur and 0 <= py < hauteur:
                image[py][px] = couleur_point


def carte_terre(monde, villes, aeroports, avions, chemin, echelle=2):
    """Terre avec villes (points rouges), aéroports (points noirs) et avions (points rouge vif).
    `avions` : liste de (lat, lon)."""
    largeur, hauteur = monde.largeur, monde.hauteur
    image = [[MER] * largeur for _ in range(hauteur)]
    for j in range(hauteur):
        for i in range(largeur):
            if monde.est_terre(i, j):
                image[j][i] = couleur(monde.altitude.get((i, j), 0))

    def pixel(lat, lon):
        return int((lon + 180) / 0.5) % largeur, min(hauteur - 1, max(0, int((90 - lat) / 0.5)))

    for v in villes:
        x, y = pixel(v["lat"], v["lon"])
        _dessiner_point(image, largeur, hauteur, x, y, (200, 0, 0), 1)
    for a in aeroports:
        x, y = pixel(a["lat"], a["lon"])
        _dessiner_point(image, largeur, hauteur, x, y, (20, 20, 20), 0)
    for lat, lon in avions:
        x, y = pixel(lat, lon)
        _dessiner_point(image, largeur, hauteur, x, y, (255, 0, 120), 1)

    grande = []
    for ligne in image:
        agrandie = [p for p in ligne for _ in range(echelle)]
        grande.extend([agrandie] * echelle)
    ecrire_png(chemin, largeur * echelle, hauteur * echelle, grande)


def zone_png(zone, chemin, echelle=2):
    """Zone zoomée : bâtiments, routes, champs, piste ; véhicules par-dessus."""
    cote = zone.cote
    image = [[COULEUR_ZONE[zone.cases[y * cote + x]] for x in range(cote)] for y in range(cote)]
    for v in zone.vehicules:
        _dessiner_point(image, cote, cote, v.x, v.y, COULEUR_VEHICULE[v.type], 0 if v.type != "bus" else 1)
    grande = []
    for ligne in image:
        agrandie = [p for p in ligne for _ in range(echelle)]
        grande.extend([agrandie] * echelle)
    ecrire_png(chemin, cote * echelle, cote * echelle, grande)
