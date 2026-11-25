# Terre vivante

Jeu de simulation : une île réelle, des personnes qui décident seules, et un joueur qui influence le monde sans le contrôler.

Document de conception : voir la section « Terre vivante » dans les documents partagés. Avancement : `PROGRESS.md`, avec la barre `avancement.svg`.

## Lancer la démonstration

    cd jeu
    python3 run_demo.py

La démonstration utilise un joueur simulé qui répond aux prières (exauce la faim et le repos, modifie les demandes de compagnon, refuse parfois le reste).

## Lancer les tests

    cd jeu
    python3 -m unittest discover -s tests -v

## Mettre à jour la barre d'avancement

Après avoir coché une tâche dans `PROGRESS.md` :

    cd jeu
    python3 avancement.py

Le script recalcule les pourcentages par phase et le pourcentage global, puis réécrit `avancement.svg`.

## Structure

- `terre/monde.py` : l'île, les cases de terre et les buissons de nourriture
- `terre/personne.py` : une personne (faim, énergie, âge, souvenirs, rumeurs, foi, colère)
- `terre/simulation.py` : la boucle à chaque tour (actions, rencontres, rumeurs, mariages, naissances, prières, schismes)
- `terre/priere.py` : les prières, leurs textes et les besoins qui les déclenchent
- `terre/culture.py` : techniques, pertes de savoirs et époques
- `terre/peuples.py` : groupes assez nombreux, échange ou guerre entre eux
- `terre/echelles.py` : zoom (planète, région, territoire, personne) et vitesse du temps
- `run_demo.py` : démonstration d'une année simulée avec un joueur simulé
- `avancement.py` : calcule la barre d'avancement à partir de `PROGRESS.md`
- `tests/` : tests automatiques

Le moteur de jeu graphique (Godot) n'est pas encore branché.
