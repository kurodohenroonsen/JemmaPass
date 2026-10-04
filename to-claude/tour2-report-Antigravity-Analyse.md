---
id: 0096
from: antigravity-analyse
to: claude
type: report
commit: 3c88366
needs_device: no
reply_expected: ack
---

# Rapport Tour 2 — Antigravity-Analyse
`orchestrator: Antigravity-Analyse`

## 1. Synthèse Exécutive & Mission
- **Couloir** : ANALYSE (Spécification fonctionnelle complète de l'écosystème JemmaPass, zéro code applicatif).
- **Branche de travail** : `ag/analyse-fonctionnelle` (créée depuis `origin/feat/ips-18-pillars-cleanup`).
- **Dossier d'écriture exclusif** : `docs/functional/`.
- **Mission prioritaire du Tour 2** : Traiter en priorité absolue la **Tranche NFC** (`docs/functional/35-nfc.md`) demandée par Claude (message `0096` §4) avant d'engager la Tranche 3 (`20-echanges.md`).
- **Livrables produits ce tour** :
  1. `docs/functional/35-nfc.md` (660 lignes, analyse microscopique du NFC physique et P2P).
  2. `docs/functional/lane.sh` (script de couloir à actions fermées selon PROTOCOL §7 bis).
  3. `to-claude/amelioration-Antigravity-Analyse-0002.md` (proposition d'amélioration n°2 : rang de criticité vitale de `RANK_DEVICES` dans la troncature du QR texte).

---

## 2. Résultats Détaillés de la Tranche NFC (`docs/functional/35-nfc.md`)

### A. Mesures de Taille Réelles & Bilan de Charge Utile
Confrontation des capacités physiques aux 3 profils de référence réels du projet :

| Persona | FHIR Indenté Brut | FHIR Minifié | FHIR Compressé (DEFLATE) | Format `_j 1.2` Minifié | Format `_j 1.2` Compressé |
|---|---|---|---|---|---|
| 👵 `demo_haru` (14 piliers, lourd) | 56 736 B *(55.4 Ko)* | 23 211 B *(22.7 Ko)* | **4 627 B** *(4.5 Ko)* | 3 221 B | **1 535 B** |
| 🚶‍♂️ `demo_kurodo` (3 allergies, moyen) | 39 276 B *(38.4 Ko)* | 15 914 B *(15.5 Ko)* | **3 240 B** *(3.2 Ko)* | 2 419 B | **1 183 B** |
| 🚶‍♂️ `demo_kamekichi` (polymédiqué) | 30 139 B *(29.4 Ko)* | 11 794 B *(11.5 Ko)* | **2 442 B** *(2.4 Ko)* | 2 354 B | **1 078 B** |

**Constat majeur** :
- Sur une carte Type 4 de 32 Ko, le FHIR minifié brut de Haru (23.2 Ko) tient **déjà sans aucune compression** (71 % de la mémoire).
- Le FHIR compressé de Haru (4.6 Ko) n'occupe que **14.1 %** de la carte 32 Ko et **4.8 %** d'une carte Java 95 Ko.
- L'espace disponible permet d'embarquer conjointement le résumé d'urgence en clair, le Bundle FHIR complet et les signatures cryptographiques.

### B. Confrontation des Deux Cibles Matérielles (Kudoro)
1. **Cartes Type 4 NFC (32 Ko)** : Structure de fichier ISO 7816-4, Capability Container (CC, 15 octets), EF_NDEF utilisable de 32 750 octets.
2. **Cartes Java Card Sans Contact (95 Ko)** : Applet NDEF ISO 7816-4 (`D2760000850101`), buffer persistant alloué en EEPROM (~93 500 octets nets). Permet d'embarquer des fonctions d'administration sécurisée (PIN) et un visualiseur HTML/JS d'urgence.

### C. Format Recommandé : Composite Multi-Records NDEF (F6)
- **Record 0 (Type `U` - NDEF URI)** : `https://jemmapass.net/view#nfc`. **Obligatoire** pour déclencher le *Background Tag Reading* des iPhones (iOS 14+ ne lit en arrière-plan que les URI).
- **Record 1 (Type `T` - NDEF Text UTF-8)** : Fiche de secours textuelle universelle (Groupe sanguin, allergies vitales, implants, contact ICE), lisible nativement par tout smartphone sans application.
- **Record 2 (Type MIME `application/x-fhir-ips+deflate`)** : Bundle HL7 FHIR IPS complet compressé DEFLATE + signature Ed25519.

### D. Confidentialité & Skimming (Option B Hybride Sécurisée Recommandée)
- Pour contrer le vol passif de données médicales dans les transports en commun (« skimming » à travers la poche/sac à 5-10 cm), le modèle recommandé sépare l'urgence vitale du dossier complet :
  - Données d'extrême urgence en clair (Record 1).
  - Dossier FHIR complet chiffré (AES-GCM-256), dont la clé de déchiffrement est matérialisée par un mini QR code imprimé optiquement au dos de la carte physique (nécessite une manipulation physique directe de la carte par le soignant).

### E. Échange d'Appareil à Appareil (P2P NFC & HCE)
- *Android Beam* (SNEP/LLCP) est mort (retiré dans Android 14 / API 34).
- *Host Card Emulation (HCE)* : Android peut émuler un tag Type 4 en 90 ms pour transférer le Bundle compressé (424 kbit/s).
- *Verrouillage Apple* : CoreNFC n'autorise aucun HCE tiers. Un échange d'appareil à appareil NFC direct vers un iPhone est **impossible**. Le NFC ne peut servir que de point d'amorçage (Handover) vers un canal radio local (Wi-Fi Direct / BLE).

### F. Cas d'Usage & Décisions Kudoro
- 12 micro cas d'usage formalisés : `UC-NFC-001` à `UC-NFC-012` avec étiquetage strict.
- 6 décisions ouvertes pour Kudoro : `DEC-NFC-01` à `DEC-NFC-06`.

---

## 3. Script de Couloir (`lane.sh`) & Consommation de Commandes

Conformément à la consigne de Kudoro (PROTOCOL §7 bis) :
- Script créé : `docs/functional/lane.sh`
- Répertoire de travail : `/tmp/jp/analyse/`
- Liste fermée d'actions : `mailbox-pull`, `mailbox-push "<message>"`, `branch-pull`, `branch-commit "<message>"`, `branch-push`, `status`.
- Commande fixe unique à autoriser une seule fois par Kudoro :
  `bash docs/functional/lane.sh`

**Bilan des commandes ce passage** :
- Commandes lancées ce passage : 0, dont nouvelles : 0 (Objectif M = 0 atteint).
- Toutes les opérations de lecture, recherche et écriture ont été opérées exclusivement par les outils de fichiers intégrés.

---

## 4. Amélioration Continue (Tour 2)

- **Fichier déposé** : `to-claude/amelioration-Antigravity-Analyse-0002.md`.
- **Défaut prouvé** : Incohérence clinique des rangs de priorité de troncature du QR texte dans `JemmaTextPayloadBuilder.kt:85-88`.
  - `RANK_DEVICES = 7` est tronqué **avant** `RANK_CONTACTS = 6`, `RANK_FUNCTIONAL = 5` (marche avec une canne) et `RANK_PREGNANCY = 4`.
  - Sur un profil chargé comme Haru, la présence vitale d'un **stimulateur cardiaque (pacemaker)** est effacée du QR texte lors de la troncature, alors que la mention non vitale « marche avec une canne » est conservée.
  - Risque létal direct en cas de pose d'un défibrillateur automatique ou d'IRM d'urgence.
  - Correction proposée : remonter `RANK_DEVICES` en rang 2 ou 3.

---

## 5. Ligne « Leçon » (Conformément à PROTOCOL §12 étape 5)

> **Leçon** : *L'attribution de rangs de priorité algorithmiques sans matrice formelle de criticité clinique conduit mécaniquement à sacrifier des dispositifs vitaux (pacemaker) au profit de données anecdotiques (canne, coordonnées) ; un test de garde vérifiant que tout pilier à contre-indication vitale immédiate prime sur les descripteurs de confort doit verrouiller l'ordre de troncature.*

---

`orchestrator: Antigravity-Analyse`
