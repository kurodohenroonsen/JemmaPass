# Décisions de Kudoro

Une ligne par décision : date · qui demande · question · options · état.

## Prises

- 2026-10-04 · purge de `device-reports` : oui → faite (`de1162c`).
- 2026-10-04 · mises à jour de la KB : distribution **pair-à-pair** (réponse de Kudoro : « P2P »). Lecture de Claude, relue par Kudoro sans correction : de téléphone à téléphone, hors-ligne, en plus du téléchargement. Conséquence technique : un fichier reçu d'un pair ne peut être accepté que s'il est signé (manifeste signé, clé publique dans l'app).
- 2026-10-04 · partage pair-à-pair des bases sources de la KB : **on partage celles qui sont disponibles**. Préalable : recherche des droits d'utilisation source par source, avec la citation du texte de licence (tâche 0090, Antigravity-KB). Seules les sources au verdict `REDISTRIBUABLE` (ou `SOUS CONDITIONS` avec conditions respectées) sont partagées.
- 2026-10-04 · iOS : **Swift natif**.
- 2026-10-04 · relation saisie tout en majuscules (« MAMAN ») absente du QR texte : **accepté** tel quel. Limite documentée dans `isRoleCode`, rien à coder.

- 2026-10-04 · dossier sur carte NFC (cartes Type 4 32 K et cartes Java 95 K déjà en main) et profil d'appareil à appareil par NFC : à étudier → recherche confiée à Antigravity-Analyse (0096, `docs/functional/35-nfc.md`).
- 2026-10-04 · commandes : regrouper un maximum d'actions par commande autorisée et réutiliser les commandes déjà autorisées (PROTOCOL §7 bis).
- 2026-10-04 07:42 · iOS-0002 : le Bundle FHIR R4 est le document maître persistant (`<sid>.fhir.json`) sur toutes les plateformes ; `_j` est une projection : **oui** (changement de périmètre).
- 2026-10-04 07:42 · USB-0002 : conteneur chiffré au repos sur la clé : **oui, « faites au mieux »** (Web Crypto natif, tests d'abord).
- 2026-10-04 07:42 · nettoyage de `0e79464` sur `ag/analyse-fonctionnelle` : **commit de revert**, pas de push forcé.

## En attente

- DEC-01 à DEC-12 de l'analyse fonctionnelle (`docs/functional/00-carte.md` §6, branche `ag/analyse-fonctionnelle`).
- Support GitHub : purge des commits déréférencés de `device-reports`.
- USB (rapport usb-0001) : (1) zéro persistance automatique sur l'ordinateur hôte ; (3) périmètre d'édition de la première version. En attendant : rien d'écrit sur l'hôte. ((2) tranché le 07:42 : conteneur chiffré, oui.)
- UX (rapport ux-0001) : DEC-UX-01 à DEC-UX-08 (couleurs d'alerte, statut décédé, masquage des codes, troncature des notes, tailles de texte et de cibles, libellés du lecteur d'écran, contenu encodé replié) — `docs/ux/00-audit.md`, branche `ag/ux-main`.
- Numéro de série du téléphone présent dans l'historique de la boîte `agent-mailbox` (fichier `DEVICE-LOCK-0028.md`, retiré de la tête) : réécrire l'historique de la boîte (push forcé, tous les orchestrateurs doivent se resynchroniser) / laisser. Moins sensible qu'une donnée personnelle ; des fragments du numéro figurent déjà dans `qa/device/jp.sh`.
- Libellés du document FHIR sur une plateforme **sans base de connaissances** (Chrome, clé USB, iOS aujourd'hui) : sur Android le libellé d'une allergie ou d'un médicament vient de la base (libellé officiel du code), pas du profil. Options : (a) ces plateformes écrivent le libellé stocké dans le profil (`d`, `t`) et le document diffère d'Android ; (b) elles embarquent un extrait de la base pour les codes du profil ; (c) le profil transporte lui-même le libellé officiel, écrit par Android. Voir message 0098.
