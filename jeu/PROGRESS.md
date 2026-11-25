# Terre vivante : avancement

![Avancement](avancement.svg)

Mis à jour à chaque tour de travail. Une tâche n'est cochée que si elle est vérifiée (tests ou démonstration). Pour recalculer la barre : `python3 avancement.py`.

## Avancement global : 87 %

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

## Phase 5 : Monde (100 %)

- [x] Carte réelle de la Terre au 0,5° (720 × 360 cases) avec relief et chaînes montagneuses. Source : cartes de relief et de côtes du paquet npm `three-globe` (licence MIT), vérifiées sur des lieux connus. Ce ne sont pas les fichiers NASA d'origine, mais des données de même nature. Construite par `tools/generer_terre.py`.
- [x] Zoom : planète, région, territoire, personne (vitesse du temps par échelle)
- [x] Plusieurs peuples qui échangent ou se font la guerre (les peuples en guerre perdent de l'énergie et s'irritent)

## Phase 6 : Figures (100 %)

- [x] Figures clés qui parlent hors connexion : phrases à partir de modèles, sans modèle de langage ni clé d'API (choix du joueur)
- [x] Décisions cohérentes avec la mémoire du personnage (poids des souvenirs, prudence)

## Phase 7 : Hors connexion (100 %)

- [x] Sauvegarde locale de la partie dans un fichier JSON (écriture atomique, reprise identique)
- [x] Langues inventées par chaque peuple, qui dérivent et s'empruntent, traduites pour le joueur
- [x] Apprentissage du vocabulaire par le joueur au fil de la partie (exposition aux peuples observés, un concept à la fois)

## Phase 8 : Mondes générés (100 %)

- [x] Monde procédural sans bord, généré à la demande par morceaux de 64 cases, à partir d'une graine
- [x] Formes au choix : continents ou île, et monde borné si on veut une taille fixe
- [x] Carte fournie par l'utilisateur (image PGM texte) pour une forme réelle, comme la Terre
- [x] Sauvegarde d'un monde infini (la graine suffit, seuls les buissons modifiés sont stockés)
- [x] La Terre réelle est jouable à 0,5° (1 case ≈ 55 km à l'équateur). Le 1:1 (1 km par case) reste impossible en mémoire : 800 millions de cases.

- Limite connue : les identifiants de peuples changent quand les groupes se recomposent, donc il y a plus de langues que de peuples (9 langues pour 2 peuples en démonstration).

## Phase 9 : Zoom, bâtiments et ressources (55 %)

- [x] Zoom sur une zone de quelques kilomètres, à 5 m par case
- [x] Bâtiments à leurs dimensions réelles : maison 10 m, immeuble 25 m, bâtiment 50 m, aérogare 200 m × 50 m
- [x] Routes, voitures et bus qui roulent sur les routes
- [x] Aéroport : piste de 2 500 m, aérogare, avions au sol
- [x] Villes et aéroports réels (243 villes, 5 653 aéroports avec code IATA), population réelle par case de 0,5°
- [x] Vols réels entre aéroports, positionnés sur le grand cercle
- [ ] Bâtiments réels (empreintes) et routes réelles : aucune donnée hors connexion, les zones sont générées selon la densité
- [ ] Ressources réelles (forêts, mines, eau) : aujourd'hui des estimations à partir du relief, de la mer et de la latitude
- [ ] Lignes de bus réelles et véhicules qui suivent un itinéraire
- [ ] Avions animés en temps réel sur la carte du jeu (aujourd'hui une image à un instant donné)
- [ ] Économie : ressources produites et consommées par les peuples

## Phase 10 : Vie quotidienne et savoirs (45 %)

- [x] Soif : eau douce estimée (côtes et zones peuplées), personnes qui cherchent à boire, mort de soif (si la carte a des points d'eau)
- [x] Énergie et électricité : sources possibles selon les techniques connues (bois, hydro, éolien, solaire, charbon, nucléaire), besoin par époque, statut suffisant ou pénurie
- [x] Nucléaire : production possible avec la physique nucléaire et une population dense ; les accidents sont consignés et blessent les voisins
- [x] Sciences apprises comme les techniques : mathématiques, médecine, histoire, astronomie, physique, électricité, physique nucléaire, informatique
- [x] Histoire : chronique des découvertes, époques, schismes, accidents et morts (`terre/histoire.py`)
- [ ] Nourriture réaliste : cultures selon le climat, stocks, faim qui dépend de l'agriculture
- [ ] Rivières réelles pour l'eau douce (aujourd'hui côtes et zones peuplées)
- [ ] Santé : médecine qui agit sur la mortalité et l'espérance de vie
- [ ] Économie : échanges de ressources entre peuples
- [ ] Effets de la pénurie d'électricité sur les personnes (foi, colère, santé)
- [ ] Centrales et installations placées sur la carte, et non seulement comptées

## Phase 11 : Application jouable (57 %)

- [x] Moteur dans le navigateur (Pyodide) : carte de la Terre, personnes, buissons, prières, énergie, histoire
- [x] Vue zoomée d'une zone à 5 m par case au toucher d'une personne
- [x] Sauvegarde automatique dans le navigateur du téléphone
- [x] Testée dans Chrome (mobile) : démarrage, avancement, prières, zone zoomée
- [ ] Hébergement en ligne (GitHub Pages) : nécessite d'activer Pages dans le dépôt
- [ ] APK Android : le SDK Android n'est pas disponible ici ; à faire après l'hébergement (Trusted Web Activity)
- [ ] Cache hors connexion : refusé dans les pages publiées ; fonctionne après hébergement
