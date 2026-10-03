# Décisions de Kudoro

Une ligne par décision : date · qui demande · question · options · état.

## Prises
- 2026-10-04 · purge de `device-reports` : oui → faite (`de1162c`).
- 2026-10-04 · mises à jour de la KB : distribution **pair-à-pair** (réponse de Kudoro : « P2P »). Lecture de Claude, à confirmer : de téléphone à téléphone, hors-ligne, en plus du téléchargement. Conséquence technique : un fichier reçu d'un pair ne peut être accepté que s'il est signé (manifeste signé, clé publique dans l'app).

## En attente
- Partage pair-à-pair des bases sources de la KB (intention de Kudoro, 2026-10-04) : dépend de l'inventaire des droits source par source (0090). Rien n'est partagé avant.
- iOS : Swift natif (pris par défaut) ou KMP.
- Relation saisie tout en majuscules (« MAMAN ») absente du QR texte : accepter / normaliser la casse à la saisie.
- DEC-01 à DEC-12 de l'analyse fonctionnelle (`docs/functional/00-carte.md` §6, branche `ag/analyse-fonctionnelle`).
- Support GitHub : purge des commits déréférencés de `device-reports`.
- USB (rapport usb-0001) : (1) zéro persistance automatique sur l'ordinateur hôte ; (2) format du conteneur chiffré par mot de passe ; (3) périmètre d'édition de la première version. En attendant : rien d'écrit sur l'hôte.
- UX (rapport ux-0001) : DEC-UX-01 à DEC-UX-08 (couleurs d'alerte, statut décédé, masquage des codes, troncature des notes, tailles de texte et de cibles, libellés du lecteur d'écran, contenu encodé replié) — `docs/ux/00-audit.md`, branche `ag/ux-main`.
