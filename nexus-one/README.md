# NEXUS ONE (PERSONAL OS)

Launcher Android personnel, écrit en Kotlin et Jetpack Compose. Android 8 minimum.

## État : 10 phases écrites et compilées, PAS ENCORE TESTÉES sur téléphone

Chaque version est compilée et publiée automatiquement (GitHub Actions, onglet Releases, `nexus-one-N`).
Les tests automatiques couvrent seulement quelques fonctions simples (lecture d'heure, codes météo).
Le comportement à l'écran n'a pas été vérifié sur un vrai téléphone.

| Phase | Contenu |
|---|---|
| 1. Accueil | Grille des applis, lancement, déclaré comme écran d'accueil |
| 2. Dock, tiroir, dossiers | Dock de 4 applis, recherche, dossiers (créer, ajouter, retirer, supprimer) |
| 3. Personnalisation | Colonnes, taille des icônes, noms, accent, fond, ordre A-Z, thèmes |
| 4. Widgets et gestes | Horloge, météo (Open-Meteo, gratuit), batterie ; glisser bas = réglages, haut = recherche |
| 5. Notifications et panneaux | Pastilles, liste, effacement ; panneau rapide (lampe, Wi-Fi, Bluetooth, volume) |
| 6. Gabriel | Assistant local à commandes, écrit ou vocal, réponse à voix haute (pas d'IA, pas de serveur) |
| 7. Automatisations | Routines de commandes Gabriel, manuelles ou chaque jour à heure fixe |
| 8. Multimédia et voiture | Lecteur (titre, précédent, pause, suivant) ; mode voiture à gros boutons |
| 9. Profils, sauvegarde, sécurité | Export/import JSON, profils nommés, PIN, applis masquées |
| 10. Optimisation, tests | Liste des applis mise à jour à l'installation, icônes en cache, tests unitaires |

## Commandes de Gabriel

`ouvre <appli>`, `lampe` / `éteins la lampe`, `heure`, `date`, `batterie`, `météo`,
`minuteur 10 minutes`, `réveil 7 h 30`, `appelle 0612345678`, `cherche <sujet>`,
`wifi`, `bluetooth`, `paramètres`, `aide`.

## Autorisations à donner à la première utilisation

- Choisir NEXUS ONE comme application d'accueil (Android le demande en appuyant sur le bouton Accueil).
- Accès aux notifications : nécessaire pour les pastilles, la liste et le lecteur multimédia.
  Le bouton « Notifs » ouvre le bon écran.

## Limites Android

- Le launcher ne modifie pas directement le Wi-Fi ou le Bluetooth : Android l'interdit, il ouvre les écrans de réglage.
- Les routines programmées ne tournent que si le launcher est actif (normalement toujours, c'est l'accueil).
  Sur Xiaomi (MIUI/HyperOS), désactiver l'optimisation de batterie pour NEXUS ONE.
- Le PIN protège les réglages et les applis masquées du launcher, pas le téléphone. Le verrouillage reste celui d'Android.
- Les APK de test sont signés avec une clé de débogage.
