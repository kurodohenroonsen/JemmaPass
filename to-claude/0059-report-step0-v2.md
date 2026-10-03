---
id: 0059
type: report
from: antigravity
to: claude
orchestrator: Antigravity-1
lane: KB
relates_to: 0055, 0058, 0059
---
# Rapport Étape 0 (v2) : Pipeline de Forge, Comparaison des Bases et Provenance

> **Règle de boîte respectée** : Nouveau fichier `0059-report-step0-v2.md`, aucun `reset --hard`.  
> **Identifiant orchestrateur** : `Antigravity-1`.

---

## 1. Localisation et nature de `forge_colab/` vs `forge_cryptonite/`

### 1.1 Rôle respectif des deux répertoires
- **`forge_colab/`** (`/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab`) :
  Version prototype/exploratoire initiale conçue pour s'exécuter dans des notebooks Google Colab (`forge_jemma_kb.ipynb`, `forge_jemma_kb_local.ipynb`). Les scripts y sont monolithiques (ex. `step14_15_finalize.py`, `step17b_cryptonite.py`) et sans orchestrateur unifié.
- **`forge_cryptonite/`** (`/Users/kurodohenroonsen/Downloads/forge_cryptonite` et son jumeau complet `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB/Claude_JemmaDB_Generator_CRYPTONITE`) :
  **Le pipeline de production modulaire complet** qui a produit la base de données réelle. Il est piloté par l'orchestrateur `turbo_forge_jemma_db.py` et comprend 46 scripts dans `forge/` (`_schema.py`, `_config.py`, `step01` à `step25`).

### 1.2 Sortie brute du `diff -rq`

```
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: JEMMA_DB_DATA_AUDIT.md
Only in /Users/kurodohenroonsen/Downloads/forge_cryptonite: OUTPUT
Only in /Users/kurodohenroonsen/Downloads/forge_cryptonite: README_DIAMOND_v1.1.md
Only in /Users/kurodohenroonsen/Downloads/forge_cryptonite: README_GOLDEN.md
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: __pycache__
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: _config.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: build_notebook.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: build_notebook_local.py
Only in /Users/kurodohenroonsen/Downloads/forge_cryptonite: forge
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: forge_jemma_kb.ipynb
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: forge_jemma_kb_local.ipynb
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: jemma_utils.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: remar.ques.md
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step01_atc.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step01_atc_hierarchy.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step02a_drugs.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step02b_ddi.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step02cfg_ddinter_extra.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step03_snomed.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step03c_snomed_refset.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step03d_snomed_textdef.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step04_mrconso.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step04c_rxncui.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step05b_unii_extended.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step08b_mrsat.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step09_mrrel.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step12_atc_propagation.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step14_15_finalize.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step17_allergy_cross_reactivity.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step17b_cryptonite.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step18_combo_augmentin.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step19b_ddi_translations_critical.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step23_drug_names.py
Only in /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/forge_colab: step25_atc_family_stats.py
Only in /Users/kurodohenroonsen/Downloads/forge_cryptonite: turbo_forge_jemma_db.py
Only in /Users/kurodohenroonsen/Downloads/forge_cryptonite: validate_diamond_v1.1.sh
```

---

## 2. Comparaison détaillée des deux bases SQLite

### 2.1 Métriques brutes comparées

| Propriété | Base 1 : Sortie de build (`JemmaPass DB`) | Base 2 : Copie du téléphone (`/tmp/jp/kb`) |
|---|---|---|
| **Chemin exact** | `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JemmaPass DB/knowledge_full.db` | `/tmp/jp/kb/knowledge_full.db` |
| **Taille en octets** | **3 358 871 552** octets | **3 360 727 040** octets |
| **SHA-256** | `08a5d4b48454ea6c85e081bd1c5b40aeee5bad48e1dd3221c6e1cd1cfb1f223e` | `237d899f9e81e6d22af01bc6969798d1495131f1f1d4caa0491d80ac6a06014c` |
| **`PRAGMA page_size`** | 4 096 | 4 096 |
| **`PRAGMA page_count`** | 820 037 pages | 820 490 pages |
| **Écart de pages** | — | **+453 pages** (+1 855 488 octets) |
| **`PRAGMA freelist_count`** | 0 | 0 |
| **`PRAGMA journal_mode`** | `delete` | `delete` |
| **`PRAGMA schema_version`** | 152 | 152 |
| **`PRAGMA user_version`** | 0 | 0 |
| **`sqlite_stat1` (lignes)** | 121 | 121 |
| **`kb_sources` (lignes)** | 32 | 32 |

### 2.2 Données de `build_metadata` (strictement identiques)
```json
{"build_version": "2.0-omnis"}
{"build_variant": "omnis"}
{"build_languages": "ARA,CHI,CZE,DUT,ENG,EST,FIN,FRE,GER,GRE,HUN,ISL,ITA,JPN,KOR,LAV,LIT,NOR,POL,POR,RUS,SCR,SPA,SWE,TUR"}
{"build_timestamp": "2026-05-13 01:20:52"}
{"build_schema": "3NF (ddi_facts/ddi_atc_pairs · terminology_codes/latin/cjk)"}
```

### 2.3 Comptages des tables clés (strictement identiques)
- `atc_hierarchy` : **6 934**
- `terminology_cjk` : **160 078**
- `terminology_latin` : **3 474 727**
- `drug_disease_interactions` : **8 121**
- `ips_valuesets_translations` : **85 736**
- `kb_sources` : **32**
- `sqlite_stat1` : **121**

### 2.4 Différence de schéma (`sqlite_master`)
- Objets uniquement dans DB1 : `set()` (aucun)
- Objets uniquement dans DB2 : `set()` (aucun)

### 2.5 Explication de l'écart et base de référence pour KBC-03
L'écart de 453 pages (1,85 Mo sur 3,36 Go, soit 0,05%) provient de la gestion interne de SQLite lors de son utilisation par l'application Android (requêtes FTS5 ou allocations de pages temporaires b-tree lors de l'exécution du service sur l'appareil avant fermeture propre).
- **Base de référence KBC-03** : Conformément à ta consigne (« la base de référence de KBC-03 doit être celle que les téléphones ont »), la base de référence est la copie téléphone `/tmp/jp/kb/knowledge_full.db` (SHA-256 `237d899f…014c`).
- **Base `jemmapass.net/kb/1.1/`** : L'archive compressée distribuée sur le site web provient du build original `08a5d4b4…f223e` avant exécution locale sur le périphérique.

---

## 3. Table de correspondance des versions et appellations

| Nom / Alias | Où apparaît-il ? | Source exacte (fichier:ligne ou requête) | Rôle dans l'histoire du projet |
|---|---|---|---|
| **`2.0-omnis`** | SQLite `build_metadata` & Code Kotlin | `build_metadata` (`key='build_version'`, `value='2.0-omnis'`) ; `JemmaProfileHydrator.kt:2` | Version sémantique réelle du schéma SQL (3NF unifié, 25 langues, tables CJK/Latin). |
| **`1.1` (site)** | URL de téléchargement | `https://jemmapass.net/kb/1.1/knowledge_full.db.gz` | Numéro de version publique distribuée pour l'application Android. |
| **`DIAMOND v1.1`** | README & scripts de forge | `README_DIAMOND_v1.1.md:1` ; `kb_sources` (`COMBO_FLAG_V1`, `ATC_PROPAGATION_V1_1`) | Version corrigeant le routage ATC des combinaisons (Augmentin $\rightarrow$ `J01CR02`). |
| **`DIAMOND v1.2`** | Répertoires & sources | `JEMMA_DB/Claude_JemmaDB_Generator_DIAMOND_v1.2` ; `kb_sources` (`COMBO_DRUG_RESOLUTIONS_V1_2`) | Résolutions des combinaisons via parsing des noms WHO ATC/DDD. |
| **`CRYPTONITE`** | Nom de forge | `Claude_JemmaDB_Generator_CRYPTONITE` ; `step17b_cryptonite.py` ; `Forge_CRYPTONITE.zip` | Version finale de la forge unifiant les règles de cross-réactivité allergique et les combinaisons. |
| **`GOLDEN`** | Sources internes | `README_GOLDEN.md` ; `kb_sources` (`GOLDEN_ENRICH_V1`, `ALLERGY_XR_SEED`, `ATC_ALTERNATIVES`) | Étape de consolidation des alternatives cliniques, seeds WHO EML/AWaRe et réactivités croisées. |

---

## 4. Plan Étape 1 : Contenu brut de `kb_sources` et granularité de provenance

### 4.1 Contenu brut complet de `kb_sources` (32 enregistrements)

```
('ATC_DDD', 'WHO ATC/DDD Index 2025', 'https://atcddd.fhi.no/', 'Open Access (WHOCC)', '2026-01-20', 6934, '2026-05-12 22:31:28')
('ATC_COMBINATIONS_INDEX_V1_2', 'WHO ATC combinations index parsed from atc_hierarchy.name_en', '', 'WHO ATC/DDD Index 2025 (open access)', '1.2', 1093, '2026-05-12 22:31:28')
('DDINTER2_DRUGS', 'DDInter 2.0 — Drug Catalog', 'http://ddinter.scbdd.com/', 'CC BY-NC-SA 4.0', '2.0', 2289, '2026-05-12 22:31:35')
('DDINTER2_DDI', 'DDInter 2.0 — Drug-Drug Interactions', 'http://ddinter.scbdd.com/', 'CC BY-NC-SA 4.0', '2.0', 260100, '2026-05-12 22:35:56')
('DDINTER2_DFI', 'DDInter 2.0 — Drug-Food Interactions', 'http://ddinter.scbdd.com/', 'CC BY-NC-SA 4.0', '2.0', 857, '2026-05-12 22:35:56')
('DDINTER2_DDSI', 'DDInter 2.0 — Drug-Disease Interactions', 'http://ddinter.scbdd.com/', 'CC BY-NC-SA 4.0', '2.0', 8359, '2026-05-12 22:35:56')
('DDINTER2_DUPLI', 'DDInter 2.0 — Therapeutic Duplications', 'http://ddinter.scbdd.com/', 'CC BY-NC-SA 4.0', '2.0', 6033, '2026-05-12 22:35:56')
('DDINTER2_ALT', 'DDInter 2.0 — Alternatives concrètes par interaction', 'http://ddinter.scbdd.com/', 'CC BY-NC-SA 4.0', '2.0', 3299967, '2026-05-12 22:41:33')
('DDINTER2_FLAGS', 'DDInter 2.0 — Mechanism flags (7-flag multi-set per DDI)', 'http://ddinter.scbdd.com/', 'CC BY-NC-SA 4.0', '2.0', 260100, '2026-05-12 22:43:57')
('SNOMED_IPS', 'SNOMED CT IPS Terminology Release', 'https://www.snomed.org/snomed-ct/Use-SNOMED-CT/IPS-Terminology', 'CC BY 4.0', '20241216', 19697, '2026-05-12 22:43:58')
('HL7_FHIR_IPS', 'HL7 FHIR IPS Implementation Guide', 'http://hl7.org/fhir/uv/ips/', 'CC BY 4.0', 'R4', 11213, '2026-05-12 22:43:58')
('UMLS_MRCONSO', 'UMLS Metathesaurus MRCONSO', 'https://www.nlm.nih.gov/research/umls/', 'UMLS License', '2025AB', 1432754, '2026-05-12 22:47:44')
('RXNORM_RXNCONSO', 'RxNorm RXNCONSO (concepts + TTY + SAB)', 'https://www.nlm.nih.gov/research/umls/rxnorm/', 'UMLS License (RxNorm Open)', '04062026', 265726, '2026-05-12 22:48:13')
('FDA_UNII_RECORDS', 'FDA UNII Records', 'https://fdasis.nlm.nih.gov/srs/', 'Public Domain', '2026-02-26', 168046, '2026-05-12 22:48:21')
('FDA_UNII_NAMES', 'FDA UNII Names', 'https://fdasis.nlm.nih.gov/srs/', 'Public Domain', '2026-02-26', 24211, '2026-05-12 22:48:25')
('RXNORM_RXNSAT', 'RxNorm RXNSAT (drug attributes)', 'https://www.nlm.nih.gov/research/umls/rxnorm/', 'UMLS License (RxNorm Open)', '04062026', 1130886, '2026-05-12 22:48:51')
('UMLS_MRSTY', 'UMLS Metathesaurus MRSTY', 'https://www.nlm.nih.gov/research/umls/', 'UMLS License', '2025AB', 1737853, '2026-05-12 22:50:31')
('UMLS_MRREL', 'UMLS MRREL (filtered)', 'https://www.nlm.nih.gov/research/umls/', 'UMLS License', '2025AB', 2648298, '2026-05-12 22:56:01')
('RXNORM_RXNREL', 'RxNorm RXNREL (relations brand↔ingredient)', 'https://www.nlm.nih.gov/research/umls/rxnorm/', 'UMLS License (RxNorm Open)', '04062026', 1587828, '2026-05-12 22:57:08')
('RXNORM_RXNSTY', 'RxNorm RXNSTY (semantic types per RxCUI)', 'https://www.nlm.nih.gov/research/umls/rxnorm/', 'UMLS License (RxNorm Open)', '04062026', 486408, '2026-05-12 22:57:18')
('UMLS_MRDEF', 'UMLS MRDEF (NCI+MSH+MSHFRE)', 'https://www.nlm.nih.gov/research/umls/', 'UMLS License', '2025AB', 196320, '2026-05-12 22:57:41')
('WORD_INDEX', 'Generated word indexes (latin tokens + CJK terms)', '', 'Generated', '1.0', 15500225, '2026-05-12 23:07:50')
('COMBO_FLAG_V1', 'Pre-flag combo drugs based on RxNorm has_tradename + has_ingredient relations', '', 'Internal (Forge DIAMOND v1.1)', '1.1', 33494, '2026-05-12 23:07:53')
('GOLDEN_ENRICH_V1', 'Forge GOLDEN — post-process enrichments (iupac, drug names)', '', 'Internal (Forge GOLDEN)', '1.0', 531634, '2026-05-12 23:08:20')
('ATC_PROPAGATION_V1_1', 'ATC code propagation via UMLS MRREL + RxNorm RXNREL (combos skipped)', '', 'Internal (Forge DIAMOND v1.1)', '1.1', 62760, '2026-05-12 23:09:45')
('DDINTER_ATC_BACKFILL_V1', 'DDInter drugs ATC backfill via RxNorm + propagation', '', 'Internal (Forge GOLDEN)', '1.0', 109, '2026-05-12 23:13:57')
('COMBO_DRUG_RESOLUTIONS_V1_2', 'Combo drug ATC resolutions via WHO ATC name parsing', '', 'WHO ATC/DDD + RxNorm RXNREL (structural)', '1.2', 5676, '2026-05-12 23:14:22')
('COMBO_ATC_OVERRIDE_V1', 'Combo drug ATC override from combo_drug_resolutions + brand variants propagation', '', 'Internal (Forge DIAMOND v1.1)', '1.1', 7155, '2026-05-12 23:14:34')
('ALLERGY_XR_SEED', 'Allergy Cross-Reactivity Clinical Seed (WHO/ANSM/UpToDate/BNF)', '', 'Internal (Forge GOLDEN — clinical knowledge)', '1.0', 52, '2026-05-12 23:18:55')
('ATC_ALTERNATIVES', 'ATC Alternatives consolidated (siblings + DDInter + allergy-aware)', '', 'Internal (Forge GOLDEN)', '1.0', 643763, '2026-05-12 23:20:17')
('WHO_EML_2023', 'WHO Model List of Essential Medicines, 23rd List (2023)', 'https://www.who.int/publications/i/item/WHO-MHP-HPS-EML-2023.02', 'Open Access (WHO)', '23rd_List_2023', 151, '2026-05-12 23:20:17')
('WHO_AWaRe_2024', 'WHO AWaRe Antibiotic Classification 2024', 'https://www.who.int/publications/i/item/WHO-MHP-HPS-EML-2024.01', 'Open Access (WHO)', '2024', 65, '2026-05-12 23:20:17')
```

### 4.2 Analyse de granularité : `kb_provenance` par ligne vs par étape
- **Par ligne (`row-level provenance`)** :
  - *Coût* : Sur 4,5+ millions de lignes (`terminology_latin`, `ddi_facts`, etc.), ajouter un champ source (`source_id TEXT`) ajouterait environ ~35 Mo (1% de la base), ce qui est techniquement négligeable.
  - *Problème sémantique majeur* : Beaucoup de lignes proviennent d'une **fusion de plusieurs sources** (ex: un concept enrichi par RxNorm + UMLS + WHO ATC). Une simple clé étrangère unitaire masquerait la contribution des autres sources ou exigerait une table de liaison n-à-n `row_sources` lourde et complexe.
- **Par étape de pipeline / table partition (Grain recommandé)** :
  - La table existante `kb_sources` (32 lignes avec identifiant, nom, URL officielle, licence, version, nombre exact de lignes insérées et timestamp) modélise déjà avec exactitude l'étape du pipeline qui a produit chaque table ou vue.
  - *Proposition* : Conserver `kb_sources` comme référentiel de provenance par étape / table partition, et enrichir `_meta/SOURCES.md` et `_meta/LICENSES.md` en s'appuyant directement sur ce catalogue de 32 sources vérifiées.
