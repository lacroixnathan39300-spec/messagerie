"""Assemble l'appli web (dossier app/dist) : moteur Python, données de la Terre, Pyodide, interface.

Usage : python3 tools/construire_app.py <dossier du paquet npm pyodide>
Le paquet se récupère avec `npm pack pyodide` puis `tar -xzf`. Il fournit le moteur Python du navigateur.
"""
import json
import os
import shutil
import sys

RACINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, RACINE)
from terre.terre_reelle import ecrire_png  # noqa: E402
SRC = os.path.join(RACINE, "app", "src")
DIST = os.path.join(RACINE, "app", "dist")
FICHIERS_PYODIDE = ("pyodide.mjs", "pyodide.asm.mjs", "pyodide.asm.wasm", "pyodide-lock.json", "python_stdlib.zip")


def copier(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy(src, dst)


def main(dossier_pyodide):
    if os.path.exists(DIST):
        shutil.rmtree(DIST)
    os.makedirs(DIST)
    for nom in ("index.html", "principal.js", "sw.js", "manifest.webmanifest"):
        copier(os.path.join(SRC, nom), os.path.join(DIST, nom))
    copier(os.path.join(SRC, "moteur.py"), os.path.join(DIST, "moteur.py"))

    python_files = []
    pkg = os.path.join(RACINE, "terre")
    for nom in sorted(os.listdir(pkg)):
        if nom.endswith(".py"):
            copier(os.path.join(pkg, nom), os.path.join(DIST, "terre", nom))
            python_files.append(f"terre/{nom}")
    donnees = os.path.join(pkg, "donnees")
    for nom in sorted(os.listdir(donnees)):
        copier(os.path.join(donnees, nom), os.path.join(DIST, "terre", "donnees", nom))
        python_files.append(f"terre/donnees/{nom}")
    python_files.append("moteur.py")

    pyodide_files = []
    os.makedirs(os.path.join(DIST, "pyodide"), exist_ok=True)
    for nom in FICHIERS_PYODIDE:
        chemin = os.path.join(dossier_pyodide, nom)
        if os.path.exists(chemin):
            copier(chemin, os.path.join(DIST, "pyodide", nom))
            pyodide_files.append(f"pyodide/{nom}")

    ecrire_png(os.path.join(DIST, "icone.png"), 192, 192,
               [[(29, 78, 216) if (x - 96) ** 2 + (y - 96) ** 2 < 72 ** 2 else (15, 23, 42)
                 for x in range(192)] for y in range(192)])

    with open(os.path.join(DIST, "fichiers.json"), "w", encoding="utf-8") as f:
        json.dump(python_files, f)
    cache = ["./", "index.html", "principal.js", "manifest.webmanifest", "icone.png", "fichiers.json",
             "cache.json"] + python_files + pyodide_files
    with open(os.path.join(DIST, "cache.json"), "w", encoding="utf-8") as f:
        json.dump(cache, f)
    print(f"app prête : {DIST} ({len(python_files)} fichiers Python, {len(pyodide_files)} fichiers Pyodide)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
