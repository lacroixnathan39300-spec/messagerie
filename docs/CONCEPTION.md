# Messagerie chiffrée (projet) — conception v0

## 1. Objectif
Messagerie Android discrète, chiffrée de bout en bout, sans serveur de stockage central.
Modes éphémères à la Snapchat. Module premier d'une suite multi-outils.

## 2. Modes de transport (du plus direct au plus discret)
1. **Local** : Wi-Fi Direct / Bluetooth / LAN, deux téléphones à proximité.
2. **Direct internet** : WebRTC entre deux téléphones en ligne. STUN pour traverser les box, TURN en secours.
3. **Oignon** : le message traverse 3 relais (téléphones volontaires), chaque relais ne connaît que le saut précédent et le suivant.
4. **Serveur de secours** : paquet chiffré relayé et supprimé à livraison (TTL court). Ne stocke rien d'autre.

Le client essaie 1, puis 2, puis 3, puis 4. Le choix est invisible pour l'utilisateur.

**Politique de routage (retenue) :**
- Chaque message attend dans une file d'attente sur le téléphone de l'expéditeur.
- Ordre de tentative : téléphone proche (mode 1) → direct internet (mode 2) → oignon (mode 3) → serveur de secours (mode 4).
- Si un mode échoue ou devient indisponible en cours de route, le message repart de l'étape suivante, puis la priorité est réévaluée à chaque cycle (par exemple toutes les minutes).
- Dès qu'un mode plus direct redevient disponible (ex. retour du Wi-Fi), le message y repasse.
- Le message n'est retiré de la file qu'après accusé de réception du destinataire.

## 3. Réseau en oignon (principe)
- Chaque message est enveloppé en **couches de chiffrement** : une couche par relais, la plus interne pour le destinataire.
- Chaque relais déchiffre sa couche, voit uniquement **le saut suivant**, puis transmet.
- Paquets de **taille fixe** (ex. 2 Ko) pour que la taille ne révèle rien.
- Relais choisis au hasard parmi les téléphones disponibles, avec des clés temporaires.
- Format inspiré de **Sphinx** (paquets à en-tête chiffré, sans métadonnée en clair).

**Limites honnêtes :**
- Latence : 3 sauts sur des téléphones qui s'endorment, soit plusieurs secondes à quelques minutes.
- Il faut assez de téléphones relais en ligne. Le mode est donc réservé aux situations où les autres modes échouent, ou activable par l'utilisateur.
- Ce n'est pas l'anonymat de Tor : un adversaire qui contrôle à la fois le premier et le dernier relais peut corréler les deux bouts. Le réseau réduit le risque, il ne l'élimine pas.

## 4. Chiffrement
- Protocole **libsignal** (double ratchet, clés de session). Pas de protocole maison.
- Couche oignon : X25519 + ChaCha20-Poly1305 par saut.
- Clés stockées dans le Keystore Android.

## 5. Éphémère
- Durée de vie réglable par message (ex. 1 h, 24 h, après lecture, vue unique).
- Accusé de réception chiffré : déclenche l'effacement sur les deux téléphones.
- Vue unique pour photos et vidéos. Capture d'écran bloquée via `FLAG_SECURE` (non garanti contre une photo de l'écran).

## 6. Identité
- Pas de numéro de téléphone.
- Identifiants de destinataire **tournants** pour que les relais ne puissent pas les relier entre eux.
- Clé publique partagée par QR code ou lien.

## 7. Décisions
- Première version : Android uniquement.
- **Appli séparée**, sans lien avec Pilote.
- **Mode oignon activé par défaut** quand suffisamment de relais sont disponibles. Sinon, bascule automatique sur le serveur de secours.

## 8. Ordre de travail
1. Chiffrement + connexion directe (mode 2).
2. Serveur de secours (mode 4).
3. Éphémères et vue unique.
4. Mode local (mode 1).
5. Relais oignon (mode 3), après test sur plusieurs téléphones.
