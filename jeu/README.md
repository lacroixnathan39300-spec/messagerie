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
- `terre/figures.py` : personnages clés qui décident et parlent à partir de leur mémoire
- `terre/langue.py` : langues inventées par les peuples, avec dérive et emprunts
- `terre/sauvegarde.py` : sauvegarde et reprise de la partie dans un fichier JSON
- `terre/monde_infini.py` : monde généré à partir d'une graine, ou carte PGM fournie
- `terre/terre_reelle.py` : la Terre à 0,5° avec relief, chargée depuis `terre/donnees/terre_0_5deg.txt`
- `tools/generer_terre.py` : reconstruit la grille de la Terre (à lancer une fois, avec Pillow)

## Données de la Terre

La grille vient de cartes de relief et de côtes du paquet npm `three-globe` (licence MIT), vérifiées sur des lieux connus. Ce ne sont pas les fichiers NASA d'origine. Pour les remplacer, relancer `tools/generer_terre.py` avec d'autres images équirectangulaires.

## Sauvegarder la partie

    from terre.sauvegarde import sauvegarder, charger
    sauvegarder(sim, "partie.json")
    sim = charger("partie.json", joueur=ma_politique)

Le fichier reste sur l'appareil, sans connexion. Sur Android, donner le chemin du dossier privé de l'appli.
- `run_demo.py` : démonstration d'une année simulée avec un joueur simulé
- `avancement.py` : calcule la barre d'avancement à partir de `PROGRESS.md`
- `tests/` : tests automatiques

Le moteur de jeu graphique (Godot) n'est pas encore branché.
