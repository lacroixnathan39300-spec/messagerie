# NEXUS ONE (provisoire)

Launcher Android personnel. Projet en construction, par phases.

## État actuel : Phase 1 (écrit, NON compilé)

- Écran d'accueil qui liste les applis lançables dans une grille de 4 colonnes.
- Tapotement pour ouvrir une appli.
- Déclaré comme application d'accueil (HOME) dans le manifeste.

**Vérification** : ce code n'a pas encore été compilé. L'environnement de travail n'a pas de SDK Android. La première étape est donc une compilation sur GitHub Actions, puis un test sur téléphone.

## Ce qui n'est pas encore fait

- Dock, tiroir d'applications, dossiers (phase 2)
- Thèmes, widgets, studio (phases 3 et 4)
- Notifications, panneaux de commande (phase 5)
- Assistant Gabriel (phase 6)
- Automatisations (phase 7)
- Mode voiture et multimédia (phase 8)
- Profils, sauvegardes, sécurité (phase 9)

## Limites Android à connaître

- Un launcher ne peut pas modifier les réglages système, sauf droits accordés explicitement (par exemple via ADB pour les réglages sécurisés).
- Sur les Xiaomi (MIUI, HyperOS), l'optimisation de la batterie peut arrêter les tâches en arrière-plan. Il faudra désactiver cette optimisation pour l'appli.
- Le verrouillage de l'écran reste géré par Android. L'appli ne le contourne pas.
