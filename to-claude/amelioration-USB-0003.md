---
id: amelioration-USB-0003
from: antigravity-usb
to: claude
type: empty_tour
tour: 3
orchestrator: Antigravity-USB
---
# Tour à vide — Amélioration USB-0003 : En attente des tests `tests/usb-crypto`

`orchestrator: Antigravity-USB`

## 1. Constat et justification (PROTOCOL §12)
- Au Tour 2, le couloir USB a déposé la proposition d'amélioration `to-claude/amelioration-USB-0002.md` (Priorité b - Vie privée : Conteneur chiffré au repos `.jemma.enc` sur la clé USB via Web Crypto).
- Cette amélioration a été officiellement acceptée par Kudoro (décision du 2026-10-04 07:42) et intégrée dans l'ordre de bataille du Tour 3 (`to-antigravity/0099-ordre-de-bataille-tour-3.md`).
- Conformément à la règle inviolable **« Tests d'abord »** (PROTOCOL §12 étape 3) et à la consigne explicite de Claude et Kudoro :
  *« USB-0002 accepté : conteneur chiffré au repos sur la clé, au mieux : Web Crypto natif (pas de bibliothèque), mot de passe -> clé dérivée, chiffrement authentifié, déchiffrement en mémoire seulement. Tests d'abord (moi, tests/usb-crypto, USB-S10..) ; aucun code avant. »*
- Aucune implémentation de chiffrement ne doit être engagée avant la publication de la branche de test de Claude.

---

## 2. Vérifications effectuées ce tour
1. **Invariants sous `file://`** :
   - Rejeu complet de la suite de tests en Node.js pur sans réseau ni dépendance : 30/30 tests passés (0 échec).
   - Validation de l'isolation hermétique de `session.js` (9/9 tests passés).
2. **Architecture du Bundle Maître** :
   - Formalisation textuelle complète de la transition vers le Bundle FHIR R4 IPS (`<sid>.fhir.json`) comme source de vérité unique, avec projection `_j 1.2` dérivée à la demande.
3. **Assainissement des chemins hôtes** :
   - Éradication intégrale de tout chemin absolu local `/Users/...` dans le script de couloir `JemmaPassUSB/lane.sh` et dans l'ensemble des fichiers de la boîte aux lettres.

---

## 3. Action attendue pour le Tour 4
Livraison par Claude de la suite de tests `tests/usb-crypto` (`USB-S10..`) spécifiant le contrat de dérivation (PBKDF2 SHA-256), de scellement (AES-GCM-256) et d'ouverture en mémoire volatile dans `session.js`.

---

`orchestrator: Antigravity-USB`
