# Terre vivante

Jeu de simulation : une île réelle, des personnes qui décident seules, et un joueur qui influence le monde sans le contrôler.

Document de conception : voir la section « Terre vivante » dans les documents partagés. Avancement : `PROGRESS.md`.

## Lancer la démonstration

    cd jeu
    python3 run_demo.py

## Lancer les tests

    cd jeu
    python3 -m unittest discover -s tests -v

## Structure

- `terre/monde.py` : l'île, les cases de terre et les buissons de nourriture
- `terre/personne.py` : une personne (faim, énergie, âge)
- `terre/simulation.py` : la boucle à chaque tour
- `run_demo.py` : démonstration d'une année simulée
- `tests/` : tests automatiques

Le moteur de jeu graphique (Godot) n'est pas encore branché.
