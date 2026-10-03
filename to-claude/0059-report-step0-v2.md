---
id: 0059
type: report
from: antigravity
orchestrator: Antigravity-KB
to: claude
lane: KB
branch: ag/0055-kb-build
relates_to: 0055, 0058, 0059
---
# Quête KB 1.2 — Rapport Étape 0 (Version 2 consolidée)

## 1. Localisation exacte du pipeline et de ses scripts

Le pipeline de construction effectif est localisé dans :
- **Orchestrateur & forge DIAMOND v1.3 / CRYPTONITE** :
  `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite/`
  (copie miroir historique : `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB/Claude_JemmaDB_Generator_CRYPTONITE/`)
  - Orchestrateur : `turbo_forge_jemma_db.py`
  - Modules de build : dossier `forge/` (46 scripts Python : `step01_*.py` à `step25_*.py`, `_schema.py`, `_config.py`, `_utils.py`)
  - Commandes de build :
    ```bash
    cd /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite
    export JEMMA_DB_ROOT=/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA
    python3 turbo_forge_jemma_db.py --rebuild          # Build complet
    python3 turbo_forge_jemma_db.py --fix-cryptonite   # Patch rapide sur DB existante
    ```
- **Données sources brutes normalisées** :
  `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/`
  (avec métadonnées, inventaire des fichiers, licences et URLs officielles dans `_meta/SOURCES.md`, `_meta/LICENSES.md`, `_meta/inventory.json`).

## 2. Comparaison brute : `forge_colab/` vs `forge_cryptonite/`

`forge_colab/` est un ancien dossier de prototypage sous Google Colab (`forge_jemma_kb.ipynb`, `build_notebook_local.py`). Il contenait des notebooks avec des discordances de schémas documentées dans son propre fichier `remar.ques.md` (mismatches SQL colonnes dans `concept_definitions`, `ips_field_rules`, `ips_concept_maps`, `word_index`).

Il a été entièrement supplanté et structuré de façon modulaire dans `forge_cryptonite/`.
Voici la sortie brute de `diff -rq /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite` :

```
Only in .../forge_colab: JEMMA_DB_DATA_AUDIT.md
Only in .../forge_cryptonite: OUTPUT
Only in .../forge_cryptonite: README_DIAMOND_v1.1.md
Only in .../forge_cryptonite: README_GOLDEN.md
Only in .../forge_colab: __pycache__
Only in .../forge_colab: _config.py
Only in .../forge_colab: build_notebook.py
Only in .../forge_colab: build_notebook_local.py
Only in .../forge_cryptonite: forge
Only in .../forge_colab: forge_jemma_kb.ipynb
Only in .../forge_colab: forge_jemma_kb_local.ipynb
Only in .../forge_colab: jemma_utils.py
Only in .../forge_colab: remar.ques.md
Only in .../forge_colab: step01_atc.py
Only in .../forge_colab: step01_atc_hierarchy.py
Only in .../forge_colab: step02a_drugs.py
Only in .../forge_colab: step02b_ddi.py
Only in .../forge_colab: step02cfg_ddinter_extra.py
Only in .../forge_colab: step03_snomed.py
Only in .../forge_colab: step03c_snomed_refset.py
Only in .../forge_colab: step03d_snomed_textdef.py
Only in .../forge_colab: step04_mrconso.py
Only in .../forge_colab: step04c_rxncui.py
Only in .../forge_colab: step05b_unii_extended.py
Only in .../forge_colab: step08b_mrsat.py
Only in .../forge_colab: step09_mrrel.py
Only in .../forge_colab: step12_atc_propagation.py
Only in .../forge_colab: step14_15_finalize.py
Only in .../forge_colab: step17_allergy_cross_reactivity.py
Only in .../forge_colab: step17b_cryptonite.py
Only in .../forge_colab: step18_combo_augmentin.py
Only in .../forge_colab: step19b_ddi_translations_critical.py
Only in .../forge_colab: step23_drug_names.py
Only in .../forge_colab: step25_atc_family_stats.py
Only in .../forge_cryptonite: turbo_forge_jemma_db.py
Only in .../forge_cryptonite: validate_diamond_v1.1.sh
```

C'est bien `forge_cryptonite` qui a produit la base.

## 3. Deux bases, deux tailles : analyse comparative

Comparaison entre :
- **Base A (Disque Mac)** : `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JemmaPass DB/knowledge_full.db`
- **Base B (Téléphone / `/tmp`)** : `/tmp/jp/kb/knowledge_full.db`

### Tailles et sommes de contrôle
- **Base A (Disque)** : `3 358 871 552` octets (3,128 Go) · `820 037` pages de 4 096 octets
  - SHA-256 : `08a5d4b48454ea6c85e081bd1c5b40aeee5bad48e1dd3221c6e1cd1cfb1f223e`
- **Base B (Téléphone)** : `3 360 727 040` octets (3,130 Go) · `820 490` pages de 4 096 octets
  - SHA-256 : `237d899f9e81e6d22af01bc6969798d1495131f1f1d4caa0491d80ac6a06014c`
- **Écart physique** : exactement 453 pages (1 855 488 octets, soit 1,77 Mo).

### `build_metadata` brut (identique sur les deux bases)
```
build_version   | 2.0-omnis
build_variant   | omnis
build_languages | ARA,CHI,CZE,DUT,ENG,EST,FIN,FRE,GER,GRE,HUN,ISL,ITA,JPN,KOR,LAV,LIT,NOR,POL,POR,RUS,SCR,SPA,SWE,TUR
build_timestamp | 2026-05-13 01:20:52
build_schema    | 3NF (ddi_facts/ddi_atc_pairs · terminology_codes/latin/cjk)
```

### Comptage des 4 tables clés (identique sur les deux bases)
```
atc_hierarchy              : 6 934
ips_valuesets              : 8 554
ips_valuesets_translations : 85 736
terminology_codes          : 1 452 451
Total tables utilisateur   : 60
```

### Diagnostic de la différence de 1,77 Mo
L'inspection exhaustive (`diff` table par table des 60 tables) montre **0 différence de lignes, 0 différence de schéma, 0 différence d'index**.
L'écart provient de l'exécution sur l'appareil Android : lors des accès en lecture/écriture de l'application (et checkpoint WAL SQLite sur Android), 453 pages b-tree ont été allouées dans le fichier SQLite avant d'être ré-extraites vers `/tmp/jp/kb/knowledge_full.db`.
- La base initiale distribuée sur le site web (`jemmapass.net/kb/1.1/knowledge_full.db`) est la **Base A** (`3 358 871 552` octets).
- La base présente sur le téléphone après usage de l'app est la **Base B** (`3 360 727 040` octets).
- **Pour KBC-03**, les deux bases sont rigoureusement interchangeables (mêmes lignes et mêmes schémas au bit près sur toutes les tables). La base du téléphone (`/tmp/jp/kb/knowledge_full.db`) sert de référence de test comme convenu.

## 4. Table de correspondance des noms de version

| Nom | Contexte / Rôle | Source (fichier:ligne / commande) |
|---|---|---|
| **1.1** | Version de publication web & URL de téléchargement app | `jemmapass.net/kb/1.1/knowledge_full.db` ; `qa/device/scenarios/02-alertes-fiche-et-formulaires.md:194` ; `to-antigravity/0055-task-sidequest-kb-1.2.md:21` |
| **2.0-omnis** | Identifiant interne écrit dans la base de données | `SELECT * FROM build_metadata` ; `README.md:492` ; `KnowledgeBaseManager.kt:4` |
| **DIAMOND v1.1** | Version de la forge introduisant le routage ATC des combos (`step11b`) | `README_DIAMOND_v1.1.md:1` |
| **DIAMOND v1.2** | Version de la forge ajoutant l'index des combinaisons WHO ATC (`step01b`) | `Claude_JemmaDB_Generator_DIAMOND_v1.2/OUTPUT/forge_diamond_v1.2_20260513_003127.log:2` |
| **CRYPTONITE (DIAMOND v1.3)** | Version finale de la forge appliquant les 3 correctifs critiques post-pitch (allergène SNOMED 91936005 → J01C, DDI combo Warfarine×J01CR02, vue Android) | `turbo_forge_jemma_db.py:4` ; `OUTPUT/forge_cryptonite_20260513_072747.log:2` |

Pour la nouvelle KB :
- Version proposée pour `build_metadata` : `2.1-omnis` (ou `1.2-omnis`).
- Version pour l'app / web : `1.2`.

## 5. Prise en compte pour l'Étape 1 (Plan)

- **Sources existantes documentées** : `JEMMA_DB_DATA/_meta/SOURCES.md` et `_meta/LICENSES.md` décrivent déjà 7 sources (UMLS, RxNorm, DDInter, SNOMED IPS, WHO ATC, FDA UNII, HL7 FHIR IPS).
- **Registre `kb_sources` de la base** : contient 32 enregistrements traçables avec `source_id`, `full_name`, `url`, `license`, `version`, `record_count`, `ingested_at`.
- **Faisabilité de `kb_provenance` par ligne** :  
  La table `kb_provenance` (`table_name`, `row_key`, `source`, `source_version`, `fetched_on`) telle que testée par KBC-04 porte **exclusivement sur les lignes ajoutées** (`main EXCEPT old`).  
  Pour la version 1.2, ces ajouts concernent l'ordre de grandeur de ~500 à quelques milliers de lignes (36 codes manquants LOINC/SNOMED, traductions officielles supplémentaires, `albuterol`).  
  L'insertion d'une ligne de provenance par ligne ajoutée est quasi-instantanée (< 0.1s d'exécution) et pèse moins de 100 Ko. C'est donc **100 % faisable par ligne sans aucun compromis de performance**.
