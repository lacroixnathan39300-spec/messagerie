"""Prépare les données réelles du jeu à partir des paquets npm `globe.gl` et `airports-data`.

Sources (téléchargées une fois avec npm, hors du jeu) :
- ne_110m_populated_places_simple.geojson (globe.gl) : villes, avec leur population.
- world_population.csv (globe.gl) : points de population (lat, lng, population).
- airports.json (airports-data, données OpenFlights) : aéroports.

Sorties dans terre/donnees/ :
- population_0_5deg.txt : 360 lignes de 720 caractères. '.' = pas d'habitant, '0' à '9' = densité
  (échelle logarithmique de la population de la case de 0,5°).
- villes.json : liste {nom, lat, lon, population}.
- aeroports.json : liste {nom, iata, lat, lon, pays}, uniquement les aéroports qui ont un code IATA.

Usage : python3 tools/generer_donnees.py <dossier_globe.gl/package> <dossier_airports-data/package>
"""
import csv
import json
import math
import os
import sys

LARGEUR, HAUTEUR = 720, 360


def case_de(lat, lon):
    i = int((lon + 180) / 0.5) % LARGEUR
    j = min(HAUTEUR - 1, max(0, int((90 - lat) / 0.5)))
    return i, j


def population(dossier_globe, sortie):
    totaux = [[0.0] * LARGEUR for _ in range(HAUTEUR)]
    chemin = os.path.join(dossier_globe, "example", "datasets", "world_population.csv")
    with open(chemin, encoding="utf-8") as f:
        for ligne in csv.DictReader(f):
            i, j = case_de(float(ligne["lat"]), float(ligne["lng"]))
            totaux[j][i] += float(ligne["pop"])
    lignes = []
    for j in range(HAUTEUR):
        lignes.append("".join(
            "." if totaux[j][i] <= 0 else str(min(9, int(math.log10(totaux[j][i] + 1) * 1.8)))
            for i in range(LARGEUR)))
    with open(sortie, "w", encoding="ascii") as f:
        f.write("\n".join(lignes) + "\n")


def villes(dossier_globe, sortie):
    chemin = os.path.join(dossier_globe, "example", "datasets", "ne_110m_populated_places_simple.geojson")
    with open(chemin, encoding="utf-8") as f:
        donnees = json.load(f)
    resultat = [
        {"nom": p["properties"]["name"], "lat": p["geometry"]["coordinates"][1],
         "lon": p["geometry"]["coordinates"][0], "population": p["properties"].get("pop_max") or 0}
        for p in donnees["features"]
    ]
    with open(sortie, "w", encoding="utf-8") as f:
        json.dump(resultat, f, ensure_ascii=False)


def aeroports(dossier_airports, sortie):
    with open(os.path.join(dossier_airports, "airports.json"), encoding="utf-8") as f:
        donnees = json.load(f)
    resultat = [
        {"nom": a["name"], "iata": a["iata"], "lat": a["latitude"], "lon": a["longitude"],
         "pays": a["country"]}
        for a in donnees if a.get("iata")
    ]
    with open(sortie, "w", encoding="utf-8") as f:
        json.dump(resultat, f, ensure_ascii=False)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    racine = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "terre", "donnees")
    os.makedirs(racine, exist_ok=True)
    population(sys.argv[1], os.path.join(racine, "population_0_5deg.txt"))
    villes(sys.argv[1], os.path.join(racine, "villes.json"))
    aeroports(sys.argv[2], os.path.join(racine, "aeroports.json"))
    print("données écrites dans", racine)
