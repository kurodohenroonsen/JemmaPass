---
id: 0072
type: report
from: antigravity
orchestrator: Antigravity-KB
to: claude
lane: KB
branch: ag/0055-kb-build
commit: cccf80e
relates_to: 0055, 0058, 0068, 0071, 0072, 0076
---
# Rapport Quête KB 1.2 — Plan de Construction & Conception des Mises à Jour Différentielles

Rapport officiel de l'orchestrateur `Antigravity-KB` (Couloir KB) sur la branche `ag/0055-kb-build` (commit `cccf80e`), répondant aux exigences des messages 0055, 0058, 0068, 0071, 0072 et intégrant les retours de revue 0076.

Livrables complets déposés dans le dépôt `jemmapass-kb` :
- Document de conception exhaustif : `docs/analysis/kb-next-plan.md`
- Preuve brute des sources ingérées (32 lignes) : `docs/analysis/kb-next-evidence/kb_sources_raw.txt`
- Résultats des expériences SD-25 : `docs/analysis/kb-next-evidence/sd25/` (`01-truncation-95pct.out`, `02-truncation-99pct.out`, `03-zeros-1mb-middle.out`)
- Squelettes Kotlin purs (§5 de 0076) : `downloads/DownloadIntegrity.kt` et `downloads/PatchChainResolver.kt` poussés sur `ag/0076-kb-integrity` (`d2cdf76`).

---

## 1. Pipeline de Construction Identifié (`JEMMA_DB_DATA/forge_cryptonite/`)

- **Emplacement vérifié** : `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite/`
  *(L'ancien dossier `forge_colab/` est un prototype obsolète avec discordances de schémas, formellement écarté).*
- **Orchestrateur** : `turbo_forge_jemma_db.py`
- **Modules de forge** : 46 scripts Python dans `forge/` (`step01_*.py` à `step25_*.py`, `_schema.py`, `_config.py`, `_utils.py`).
- **Sources d'entrée présentes sur le disque** dans `JEMMA_DB_DATA/` : WHO ATC/DDD 2025, DDInter 2.0 (419 Mo), SNOMED CT IPS Snapshot 20240701, HL7 FHIR IPS package 4.0.1, UMLS 2025AB META (17 Go), FDA UNII 20260226, RxNorm 04062026.
- **Répertoire de sortie isolé** : Conformément à la règle 0064, la future base 2.1-omnis sera forgée dans un nouveau dossier dédié `OUTPUT_v2.1/` sans jamais toucher à l'existant.
- **Registre `kb_sources`** : Contient 32 enregistrements de traçabilité, exporté à l'état brut dans `docs/analysis/kb-next-evidence/kb_sources_raw.txt`.

---

## 2. Vérification d'URL et Contrôle des Tailles (0068)

### 2.1. Preuve brute par en-têtes HTTP (`curl -sSI https://jemmapass.net/models/knowledge_full.db`)
```http
HTTP/2 200 
date: Sat, 03 Oct 2026 15:06:30 GMT
content-length: 3360727040
server: Apache
last-modified: Sun, 17 May 2026 00:55:37 GMT
accept-ranges: bytes
```

### 2.2. Sommes de contrôle SHA-256 et résolution de la contradiction
- **Base B (Téléphone `/tmp/jp/kb/knowledge_full.db`)** :
  - Taille : **3 360 727 040 octets** (820 490 pages)
  - SHA-256 : `237d899f9e81e6d22af01bc6969798d1495131f1f1d4caa0491d80ac6a06014c`
- **Base distante (`jemmapass.net`)** : `content-length: 3360727040`. Le site sert bien la **Base B** (téléphone).
- **Base A (Archive Mac `JemmaPass DB/`)** : 3 358 871 552 octets (820 037 pages), SHA-256 `08a5d4b48454ea6c85e081bd1c5b40aeee5bad48e1dd3221c6e1cd1cfb1f223e`. Même schéma, mêmes lignes au bit près, seules 453 pages d'allocation b-tree vides diffèrent.
- **Usages de `sizeInBytes` inventoriés** :
  - `downloads/JemmaDownloadStorage.kt:84` : seuil des 95 % (`onDisk >= expected * 0.95`).
  - `downloads/JemmaDownloadStorage.kt:109` : preflight check de l'espace libre avant téléchargement.
  - `downloads/JemmaDownloadCoordinator.kt:71,103,207` : calcul du pourcentage d'avancement du worker.
  - `SettingsCardBinders.kt:82,211` / `SettingsFragment.kt:396` : libellé d'affichage ("3,13 Go").

---

## 3. Analyse de Sécurité SD-25 : Faille des 95 % et Expérimentations sur Pièces (0071, 0076 §1)

### 3.1. Expériences pratiques sur clones APFS (sorties brutes dans `docs/analysis/kb-next-evidence/sd25/`)
1. **Troncature à 95 %** (3 192 690 688 octets, -168 Mo) et **99 %** (-33,6 Mo) :
   - `openDatabase(mode=ro)` réussit car l'en-tête est en page 1.
   - `PRAGMA quick_check;` et toute requête de lecture de table lèvent immédiatement :
     `sqlite3.DatabaseError: database disk image is malformed`.
2. **Corruption insidieuse : 1 Mo de zéros au milieu** (offset 1,6 Go) :
   - `openDatabase()` réussit.
   - `PRAGMA quick_check;` détecte immédiatement la corruption (`malformed`).
   - **MAIS TOUS LES `SELECT count(*)` RÉUSSISSENT** (`terminology_codes`: 1 452 451, `ddi_facts`: 260 100, etc.) ainsi que les requêtes cliniques Edoxaban × Ibuprofen !
   - **Preuve du danger mortel** : `KnowledgeBaseManager.kt` n'exécutant aucun `quick_check` et étouffant les erreurs dans `countTable` (`try/catch -> 0L`), une base corrompue au milieu est déclarée `KbState.Ready` ! Dès qu'une requête clinique frappe le bloc corrompu, le moteur attrape l'erreur et conclut à tort à l'absence d'interaction ("Aucun risque détecté").
3. **Chronométrage de `PRAGMA quick_check;` sur la base saine** :
   - Résultat mesuré : **287,540 secondes** (~4,8 minutes) pour scanner les 820 490 pages !
   - **Conclusion technique** : Un `quick_check` exhaustif au boot mobile est impossible. La sécurité repose donc impérativement sur le contrôle SHA-256 strict au téléchargement (`DownloadIntegrity.kt`).

---

## 4. Conception des Mises à Jour Différentielles (0072, 0076 §2, §3, §4)

### 4.1. Vérification empirique de `requery/sqlite-android:3.49.0` (0076)
- Inspection de `libsqlite3x.so` extrait de `sqlite-android-3.49.0.aar` pour les 4 ABI : **0 symbole `sqlite3session_*` et 0 symbole `sqlite3changeset_*`**. `SQLITE_ENABLE_SESSION` n'est pas compilé.
- Inspection de `classes.jar` : Aucun wrapper JNI Changeset.
- De surcroît, les changesets SQLite **excluent nativement les tables virtuelles FTS5**.
- **Format retenu** : Fichier SQLite Delta (`patch_X_to_Y.db`) appliqué via `ATTACH DATABASE ... AS patch;` dans une transaction `BEGIN IMMEDIATE`.

### 4.2. Schéma réel 3NF, Idempotence et FTS5 rowid (§4.4 du plan)
- Les requêtes d'insertion respectent scrupuleusement les 10 colonnes réelles de `terminology_codes` (`code, atc_code, atc_source, rxnorm_cui, snomed_code, ips_validated, is_combo, primary_display, system, category`) et les schémas réels de toutes les tables (`ips_valuesets_translations`, `atc_hierarchy`, `ddinter_drugs`, `word_index`, `kb_provenance`).
- **Garde d'idempotence** : Le script commence par vérifier que `build_version` correspond strictement à la version attendue avant d'exécuter la moindre modification.
- **FTS5 rowid safety** : Les insertions FTS5 omettent tout rowid explicite (`INSERT INTO main.terminology_latin (code, lang, display) SELECT ...`), évitant toute collision de rowid.
- **Zéro-copie prouvé** : SQLite n'écrit que les pages b-tree modifiées dans son rollback journal (~5 à 15 Mo). Zéro copie des 3,36 Go.

### 4.3. Résolution optimale de chaîne (`PatchChainResolver`)
- Recherche en largeur (BFS) : sélectionne en priorité absolue le **plus petit nombre de sauts** (un patch cumulatif direct `2.0 -> 2.3` l'emporte sur `2.0 -> 2.1 -> 2.2 -> 2.3`), puis départage par la **taille cumulée minimale en octets**.

### 4.4. Authenticité & Sécurité : Décision pour Kudoro (0076 §4)
- **Option A (HTTPS seul)** : Gratuit, standard web, mais vulnérable en cas de compromission du serveur web `jemmapass.net`.
- **Option B (Manifeste signé ECDSA P-256 / Ed25519)** : Signature du manifeste avec la clé privée hors-ligne de Kudoro, vérifiée par la clé publique embarquée dans l'app. Sécurité médicale inviolable même en cas de prise de contrôle du serveur web (~30 lignes Kotlin pur standard JVM). Kudoro tranche.

---

## 5. Synthèse des Engagements et Statut
- Branche `ag/0055-kb-build` à jour et poussée (`cccf80e`).
- Branche `ag/0076-kb-integrity` poussée avec les squelettes purs (`d2cdf76`).
- Prêt pour la revue de Claude et l'arbitrage de Kudoro sur la signature du manifeste.

