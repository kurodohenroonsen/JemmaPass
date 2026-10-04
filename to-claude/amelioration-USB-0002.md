---
id: amelioration-USB-0002
from: antigravity-usb
to: claude
type: proposal
tour: 2
priorite: b) vie privée
orchestrator: Antigravity-USB
---
# Amélioration continue — USB-0002 : Conteneur chiffré au repos `.jemma.enc` sur clé USB (Web Crypto)

`orchestrator: Antigravity-USB`

## 1. Constat et pièces
- **Constat** :
  Sur la clé USB, le passeport de santé complet est actuellement manipulé soit en format brut `.fhir.json`, soit en projection compacte `_j 1.2` (`.json`). Si la clé USB physique est égarée, oubliée sur un ordinateur public (cybercafé, bibliothèque, hôpital) ou volée, l'intégralité du dossier médical du patient est directement accessible à quiconque monte la clé sur son ordinateur.
- **Pièce vérifiée sous `file://` (Sondes `probe.html` et `probe_chrome_raw.json` / `probe_firefox_raw.json`)** :
  - `crypto.subtle` est pleinement supporté et actif sous `file://` (origine sécurisée reconnue par Chrome et Firefox).
  - PBKDF2 (100 000 itérations SHA-256) et AES-GCM 256 bits fonctionnent en local pur sans aucune dépendance ni bibliothèque tierce (durée de dérivation : ~18 ms sur le matériel testé).
  - Le module `core/session.js` (validé Tour 1, 9/9 tests passés) garantit déjà l'isolation en mémoire vive volatile sans écriture sur les stockages persistants de l'hôte (`IndexedDB`, `localStorage`).

---

## 2. Ce que ça coûte à une vraie personne
Un patient qui confie ses données les plus intimes à une clé USB de secours (traitements psychiatriques, sérologie, antécédents de fausse couche, allergies mortelles) risque une exposition publique immédiate en cas de perte de son trousseau de clés. N'importe quel tiers branchant la clé peut lire en clair ses informations médicales et ses contacts familiaux sans barrière.

---

## 3. Correction proposée (faisable en un tour)
Implémenter dans `core/crypto.js` un module pur de scellement et de déchiffrement de conteneur `.jemma.enc` :
1. **Chiffrement** :
   - Dérivation d'une clé AES-GCM-256 via `PBKDF2` (sel aléatoire 16 octets, 100 000 itérations, HMAC-SHA-256) à partir d'une phrase de passe ou d'un code PIN choisi par le patient.
   - Chiffrement du JSON FHIR / `_j 1.2` en AES-GCM (IV aléatoire 12 octets).
   - Format de fichier en sortie `.jemma.enc` binaire compact ou enveloppe JSON autonome (sel, IV, données chiffrées, tag d'authentification).
2. **Déchiffrement** :
   - Dérivation de la clé à la volée en mémoire vive lors de la saisie de la phrase secrète.
   - Injection directe du résultat déchiffré dans `session.load(json)` sans jamais écrire les octets déchiffrés sur le disque.
   - En cas d'erreur de clé : rejet immédiat avec `DOMException / OperationError` sans altérer l'état de la session.
3. **Zéro trace** :
   - À l'appel de `session.close()`, tous les buffers et clés en mémoire sont écrasés.

---

## 4. Ce qu'elle risque de casser
- **Accès d'urgence soignant** : Un soignant en réanimation n'aura pas le mot de passe du patient inconscient.
  - *Mitigation* : Le scellement chiffré `.jemma.enc` protège le dossier complet. La fiche secouriste vitale (groupe sanguin, allergies vitales, implants) peut rester lisible soit via un mini fichier de secours texte `URGENCE.txt` non chiffré, soit par un QR code optique imprimé sur l'étiquette physique de la clé, selon l'arbitrage demandé à Kudoro (`DECISIONS-KUDORO.md`).
- **Compatibilité navigateurs anciens** : `crypto.subtle` requiert un navigateur moderne (Chrome 37+, Firefox 34+, Safari 11+). Sous `file://`, tous les navigateurs récents le traitent en contexte sécurisé.

---

## 5. Comment on saura que c'est corrigé
- Claude écrit la spécification et les tests de contrat dans `tests/crypto.test.js`.
- Tests unitaires vérifiant :
  1. `encrypt(plaintext, passphrase)` produit un conteneur valide.
  2. `decrypt(ciphertext, passphrase)` restitue exactement le JSON initial.
  3. `decrypt(ciphertext, wrongPassphrase)` échoue systématiquement sans fuite de clair.
  4. L'altération d'un seul octet du conteneur est détectée (intégrité AES-GCM).
  5. Aucune donnée n'est persistée sur le disque hôte.

---

`orchestrator: Antigravity-USB`
