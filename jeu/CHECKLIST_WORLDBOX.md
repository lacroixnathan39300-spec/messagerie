# Checklist : Terre vivante vs WorldBox

Légende : ✅ fait · 🟡 partiel · ❌ à faire

## Interface
- ✅ Carte plein écran avec barre d'outils en bas (catégories)
- ✅ Pause / lecture, vitesse ×1 / ×5 / ×20
- ✅ Zoom (boutons et molette)
- ✅ Pincer pour zoomer sur téléphone
- ✅ Taille du pinceau réglable (petit / moyen / grand)
- ✅ Panneau d'infos sur une personne : âge, couple, enfants, langue, savoirs, soif, faim, foi
- ✅ Sauvegardes multiples (trois emplacements)
- 🟡 Menu de réglages : on peut couper l'affichage des villes et le son des actions ; choix de langue pas encore prévu (l'application est en français)

## Terrain
- ✅ Terre ajoutée sur la mer
- ✅ Mer qui submerge la terre (noie les personnes)
- ✅ Montagnes (pinceau, visibles et sauvegardées ; l'effet sur la vie n'est pas encore simulé)
- 🟡 Forêts : zones tropicales et boréales couvertes de forêt (approximatif, sans données de végétation) ; les zones de buissons serrés restent teintées en vert foncé
- ✅ Neige sur les plus hauts sommets de la Terre réelle (altitude)
- ✅ Lave : pinceau qui pose des cases mortelles, qui refroidissent après 40 jours
- ✅ Désert : pinceau qui retire les buissons (aucun n'y pousse)
- 🟡 Désert naturel : déserts placés approximativement dans les zones subtropicales (sans données climatiques)
- 🟡 Rivières et lacs : rivières qui descendent des montagnes jusqu'à la mer, lacs dans les cuvettes (tracé approximatif, calculé à partir de l'altitude)
- ✅ Personnages en pixels quand on zoome : humains, moutons, loups, poissons, dragons et zombies sont dessinés comme de petits sprites
- ✅ Affichage net de la terre ajoutée à petit zoom (vérifié : la terre peinte reste visible à l'échelle de la carte entière)

## Êtres
- 🟡 Personnes (naissance, mort, faim, soif, prières)
- ✅ Animaux : moutons et loups marchent sur la terre (le loup mange le mouton), poissons nagent dans la mer ; sauvegardés
- 🟡 Créatures spéciales : dragon (vole partout et brûle la terre) et zombie (tue les personnes voisines) ; d'autres à venir
- 🟡 Rencontres entre peuples : une ligne rouge relie deux peuples en guerre et proches (code testé, guerre pas encore observée)

## Civilisation
- 🟡 Peuples, guerres, échanges, schismes : chaque peuple a sa couleur sur la carte et les schismatiques sont en rouge ; guerres et échanges calculés mais rarement observés
- ✅ Villes du monde réel (243 points, noms en zoom) et villages fondés par les peuples (marqueurs)
- 🟡 Bâtiments : les villages grandissent d'une maison tous les 30 jours (jusqu'à 8), dessinés en maisons à petit détail quand on zoome
- ✅ Routes : deux villages proches et peuplés sont reliés par une route sur la terre
- ✅ Véhicules : des voitures roulent sur les routes entre villages, dessinées en petites voitures quand on zoome

## Pouvoirs et catastrophes
- ✅ Personne (ajout)
- ✅ Buisson (nourriture)
- ✅ Foudre (tue dans un petit rayon)
- ✅ Pluie (fait repousser les buissons ; neige et sécheresse à faire)
- ✅ Feu (se propage chaque jour sur la terre et brûle les buissons)
- ✅ Tremblement de terre : effet immédiat, puis répliques pendant 4 jours autour de l'épicentre
- ✅ Tornade (se déplace chaque jour et tue ce qu'elle traverse)
- ✅ Météorite (tue dans une zone large et creuse un cratère de mer)
- ✅ Peste (se transmet entre voisins et tue chaque jour une partie des malades)
- 🟡 Bombes : une explosion tue les personnes proches et enflamme la terre ; pas encore d'armes à proprement parler

## Monde
- ✅ Carte réelle de la Terre (1 case = environ 55 km)
- ✅ Changer de carte : île ou archipel générés selon la graine (menu « Nouvelle partie »)
- ✅ Rendu façon bac à sable : couleurs vives, pinceau en cercle au survol, barre d'outils arrondie, déserts et forêts aux bords plus doux (dessins et règles propres au projet)
- ✅ Zoom centré sur le doigt ou le curseur, carte qui occupe toute la largeur
- ❌ Partager une carte avec d'autres joueurs

## Technique
- ✅ Fonctionne hors connexion sur téléphone (APK)
- ❌ Mises à jour de l'APK sans perdre la partie (clé de signature stable)
- 🟡 Performances sur téléphone ancien : mesuré sur ordinateur (environ 25 ms de calcul par jour avec 430 personnes, après optimisation des groupes de personnes) ; pas encore testé sur un vrai téléphone ancien
