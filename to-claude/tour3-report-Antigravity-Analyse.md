---
id: 0100
from: antigravity-analyse
to: claude
type: report
relates_to: 0099
branch: ag/analyse-fonctionnelle
commit: 2e9bc99
needs_device: no
reply_expected: ack
---

# Rapport Tour 3 — Antigravity-Analyse
`orchestrator: Antigravity-Analyse`

commandes lancées ce passage : 3, dont nouvelles : 0

## 1. État et Livrables
- **Branche** : `ag/analyse-fonctionnelle` (dérivée de `origin/feat/ips-18-pillars-cleanup` @ `f06dcd3`).
- **Commit HEAD** : `2e9bc99` (poussé proprement sans force sur `origin/ag/analyse-fonctionnelle`).
- **Livrables de ce tour** :
  1. **Purge et nettoyage strict (§10)** : Revert propre `6d5f0ee` du commit pollué `0e79464`, suivi du réengagement du seul fichier `docs/functional/10-existant-android.md` au commit `f4781ee`. Zéro fichier alien (`.build/`, Chrome, `jp.sh`) sur la branche.
  2. **Refonte intégrale de `docs/functional/35-nfc.md` (Tranche NFC)** :
     - Chaque affirmation technique étayée par son URL officielle et sa citation verbatim entre guillemets (NFC Forum Type 4, ISO/IEC 7816-4, Android HCE `HostApduService`, dépréciation Android Beam, restrictions Apple CoreNFC Background Reading, W3C Web NFC).
     - Remplacement de la proposition erronée Ed25519 par la signature matérielle universelle du projet : **ECDSA P-256 (`secp256r1`) avec SHA-256** (Android Keystore / Apple Secure Enclave).
     - Publication des mesures de taille réelles brutes extraites des profils du dépôt (`files/demo_*.fhir.json` et `_j 1.2`).

## 2. Sortie Brute des Mesures de Taille Réelles
Fichiers mesurés dans l'arborescence du projet (sorties du cycle d'intégration et profils `JemmaPersonasSeeder.kt`) :

```text
- demo_haru.fhir.json       :  55 624 octets (unminified JSON, 1 461 lignes)
- demo_kurodo.fhir.json     :  39 195 octets (unminified JSON, 1 032 lignes)
- demo_kamekichi.fhir.json  :  30 055 octets (unminified JSON,   790 lignes)
- demo_haru.json (_j 1.2)   :   4 646 octets (unminified JSON,   228 lignes)
- demo_kurodo.json (_j 1.2) :   3 558 octets (unminified JSON,   173 lignes)
- demo_kamekichi.json (_j)  :   3 407 octets (unminified JSON,   158 lignes)
```

Tableau synthétique des charges utiles :

| Persona | Profil Clinique | FHIR Indenté Brut | FHIR Minifié | FHIR Minifié Compressé (DEFLATE) | Format `_j 1.2` Minifié | Format `_j 1.2` Compressé |
|---|---|---|---|---|---|---|
| 👵 **`demo_haru`** | 32 entrées (stimulateur, anticoagulant, 3 vaccins, 2 chirurgies, 2 pb, 5 rés, 3 obs, 2 fs, contact) | **55 624 o** *(54,3 Ko)* | **23 211 o** *(22,7 Ko)* | **4 627 o** *(4,5 Ko)* | **3 221 o** *(3,1 Ko)* | **1 535 o** *(1,5 Ko)* |
| 🚶‍♂️ **`demo_kurodo`** | 18 entrées (3 allergies dont choc pénicilline, A+, 4 vaccins, 2 chirurgies, 1 pb, 2 ant, 4 rés, contact) | **39 195 o** *(38,3 Ko)* | **15 914 o** *(15,5 Ko)* | **3 240 o** *(3,2 Ko)* | **2 419 o** *(2,4 Ko)* | **1 183 o** *(1,2 Ko)* |
| 🚶‍♂️ **`demo_kamekichi`** | 19 entrées (5 médicaments, 3 cardiopathies, B+, 1 rés, contact) | **30 055 o** *(29,4 Ko)* | **11 794 o** *(11,5 Ko)* | **2 442 o** *(2,4 Ko)* | **2 354 o** *(2,3 Ko)* | **1 078 o** *(1,1 Ko)* |

**Constat d'adéquation capacitive** :
- Sur une carte Type 4 de 32 Ko (32 750 octets NDEF net), le Bundle FHIR minifié de Haru tient déjà sans compression (70,8 % d'occupation). Compressé en DEFLATE, il n'occupe que **14,1 %** de la puce, laissant plus de 28 Ko disponibles pour le résumé d'urgence en clair et les signatures.
- Sur une carte Java Card de 95 Ko, le FHIR compressé n'occupe que **4,8 %** de la mémoire.

## 3. Amélioration Continue
- **Proposition Tour 2 (`amelioration-Analyse-0002.md`)** : `RANK_DEVICES = 7` (vérifié dans `JemmaTextPayloadBuilder.kt:82-93`) a été validée par Claude avec priorité a). Les tests sont en cours de rédaction par Claude (`UC-QRT-030..`).
- **Tour 3** : **Tour à vide**. Vérification effectuée sur le code existant du module SOS/Radar et des intent-filters (`AndroidManifest.xml`). L'intégralité du canal NFC étant documentée et corrigée, aucune nouvelle proposition n'est émise ce tour afin de stabiliser la Tranche 35 avant l'engagement de la Tranche 3.

## 4. Leçon
Une spécification fonctionnelle d'architecture radio et matérielle ne peut tolérer aucune conjecture non sourcée. Citer précisément chaque documentation officielle (NFC Forum, Apple CoreNFC, Android HCE) avec citation textuelle et confronter les chiffres théoriques aux fichiers réels du dépôt permet d'asseoir des choix techniques incontestables et d'éviter les dérives d'implémentation (notamment le piège du non-déclenchement en arrière-plan d'iOS sans enregistrement URI).
