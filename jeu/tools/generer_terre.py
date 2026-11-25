"""Construit la grille de la Terre utilisée par le jeu (à lancer une seule fois, avec Pillow).

Entrées : deux images équirectangulaires (relief en niveaux de gris, et carte terre/mer), issues du
paquet npm three-globe (licence MIT). Vérifié sur des lieux connus : le Pacifique est en mer,
l'Himalaya et les Alpes ont un relief fort, l'Amazonie est basse.

Sortie : terre/donnees/terre_0_5deg.txt, 360 lignes de 720 caractères, du nord (ligne 0)
au sud, d'ouest (colonne 0) en est.
  '.'        : mer
  '0' à '9'  : terre, classe d'altitude (9 = relief le plus fort)

Usage : python3 tools/generer_terre.py relief.png eau.png
"""
import os
import sys

from PIL import Image

LARGEUR, HAUTEUR = 720, 360  # 0,5 degré par case
SEUIL_TERRE = 128  # sur la carte eau : blanc = mer, sombre = terre
RELIEF_MAX = 175  # relief observé pour les plus hautes chaînes ; au-delà, classe 9


def main(chemin_relief, chemin_eau, sortie):
    relief = Image.open(chemin_relief).convert("L")
    eau = Image.open(chemin_eau).convert("L")
    rw, rh = relief.size
    ew, eh = eau.size
    lignes = []
    for j in range(HAUTEUR):
        ligne = []
        for i in range(LARGEUR):
            # Moyenne sur le bloc de pixels correspondant à la case.
            x0, x1 = i * ew // LARGEUR, (i + 1) * ew // LARGEUR
            y0, y1 = j * eh // HAUTEUR, (j + 1) * eh // HAUTEUR
            pixels = [eau.getpixel((x, y)) for x in range(x0, max(x1, x0 + 1))
                      for y in range(y0, max(y1, y0 + 1))]
            fraction_terre = sum(1 for p in pixels if p < SEUIL_TERRE) / len(pixels)
            if fraction_terre < 0.5:
                ligne.append(".")
                continue
            rx0, rx1 = i * rw // LARGEUR, (i + 1) * rw // LARGEUR
            ry0, ry1 = j * rh // HAUTEUR, (j + 1) * rh // HAUTEUR
            valeurs = [relief.getpixel((x, y)) for x in range(rx0, max(rx1, rx0 + 1))
                       for y in range(ry0, max(ry1, ry0 + 1))]
            moyenne = sum(valeurs) / len(valeurs)
            classe = min(9, int(moyenne * 9 / RELIEF_MAX))
            ligne.append(str(classe))
        lignes.append("".join(ligne))
    os.makedirs(os.path.dirname(sortie), exist_ok=True)
    with open(sortie, "w", encoding="ascii") as f:
        f.write("\n".join(lignes) + "\n")
    print(f"écrit : {sortie} ({sum(l.count('.') for l in lignes)} cases de mer)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    racine = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    main(sys.argv[1], sys.argv[2], os.path.join(racine, "terre", "donnees", "terre_0_5deg.txt"))
