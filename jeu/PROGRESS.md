# Terre vivante : avancement

![Avancement](avancement.svg)

Mis à jour à chaque tour de travail. Une tâche n'est cochée que si elle est vérifiée (tests ou démonstration). Pour recalculer la barre : `python3 avancement.py`.

## Avancement global : 93 %

Calcul : moyenne des pourcentages des six phases. Phase 1 à 78 %, phases 2 à 6 non commencées.

## Phase 1 : Prototype (100 %)

- [x] Île générée depuis une graine, toujours la même pour une graine donnée
- [x] Personnes avec faim, énergie et âge
- [x] Déplacements vers la nourriture et exploration au hasard
- [x] Nourriture qui repousse, plafonnée au maximum
- [x] Mort de faim et de vieillesse
- [x] Tests automatiques (7 tests, tous passent)
- [x] Démonstration en ligne de commande (`python3 run_demo.py`)
- [x] Critère de réussite : le groupe survit une année simulée sur l'île par défaut, avec le joueur de démonstration (test sur 3 îles, au moins 15 survivants sur 20)
- [x] Visualisation : carte SVG de l'île (terre, buissons, personnes, prières en attente), générée par `terre/visu.py`. La version téléphone (Godot) reste à faire.

Écart avec le document de conception : l'île est générée et non tirée d'une carte réelle. La carte réelle relève de la phase 5.

## Phase 2 : Relations (100 %)

- [x] Communication entre personnes (face à face, rencontres entre voisins)
- [x] Rumeurs qui se déforment en passant de bouche en bouche (fiabilité qui baisse à chaque passage)
- [x] Mémoire des événements avec oubli progressif
- [x] Relations de confiance et d'affection
- [x] Mariages et familles (mariage entre adultes, naissances entre conjoints)

Constat initial : sur une année, aucun couple ne se forme, car les rencontres sont rares. Avec le joueur de démonstration (phase 3), deux couples se forment. La fréquence reste à équilibrer.

## Phase 3 : Prières (100 %)

- [x] Génération des prières à partir des besoins (faim, repos, compagnon)
- [x] File des prières et choix du joueur (exaucer, refuser, modifier, ignorer ; expiration après 20 tours)
- [x] Foi qui évolue selon les réponses
- [x] Conséquences des refus (colère, schismes)

## Phase 4 : Culture (100 %)

- [x] Techniques qui apparaissent selon la taille du groupe (outil, feu, arc, poterie, agriculture, écriture, imprimerie)
- [x] Techniques qui se perdent quand personne ne les pratique (petits groupes qui oublient)
- [x] Époques et étapes de l'évolution culturelle (Paléolithique, Néolithique, Antiquité, Époque moderne)

## Phase 5 : Monde (67 %)

- [ ] Carte réelle de la Terre (données NASA). Bloqué : il faut télécharger les données, le bac à sable n'y a pas accès. Une carte PGM fournie par toi (`monde_depuis_pgm`) peut la remplacer.
- [x] Zoom : planète, région, territoire, personne (vitesse du temps par échelle)
- [x] Plusieurs peuples qui échangent ou se font la guerre (les peuples en guerre perdent de l'énergie et s'irritent)

## Phase 6 : Figures (100 %)

- [x] Figures clés qui parlent hors connexion : phrases à partir de modèles, sans modèle de langage ni clé d'API (choix du joueur)
- [x] Décisions cohérentes avec la mémoire du personnage (poids des souvenirs, prudence)

## Phase 7 : Hors connexion (100 %)

- [x] Sauvegarde locale de la partie dans un fichier JSON (écriture atomique, reprise identique)
- [x] Langues inventées par chaque peuple, qui dérivent et s'empruntent, traduites pour le joueur
- [x] Apprentissage du vocabulaire par le joueur au fil de la partie (exposition aux peuples observés, un concept à la fois)

## Phase 8 : Mondes générés (80 %)

- [x] Monde procédural sans bord, généré à la demande par morceaux de 64 cases, à partir d'une graine
- [x] Formes au choix : continents ou île, et monde borné si on veut une taille fixe
- [x] Carte fournie par l'utilisateur (image PGM texte) pour une forme réelle, comme la Terre
- [x] Sauvegarde d'un monde infini (la graine suffit, seuls les buissons modifiés sont stockés)
- [ ] Échelle 1:1 avec la Terre : impossible en mémoire case par case (800 millions de cases à 1 km). À prévoir avec une échelle plus grossière (par exemple 1 case = 10 km) et une carte fournie.

- Limite connue : les identifiants de peuples changent quand les groupes se recomposent, donc il y a plus de langues que de peuples (9 langues pour 2 peuples en démonstration).
