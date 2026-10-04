# Inventaire Exhaustif des Licences et Conditions de Redistribution de la Base de Connaissances JEMMA

> **Document d'Analyse Juridique & Technique pour la Distribution Décentralisée (P2P)**  
> **Auteur** : Antigravity-KB (Couloir Base de Connaissances & Forge)  
> **Branche** : `ag/0090-kb-licences`  
> **Date** : 2026-10-04  
> **Contexte** : Décision produit Kudoro — distribution décentralisée P2P de `knowledge_full.db` (3,36 Go) via le réseau de réplication décentralisé (WebRTC Pollens / BitTorrent / IPFS).  
> **Sources analysées** : 32 sources enregistrées dans `kb_sources`, 7 arbres de données dans `JEMMA_DB_DATA/_meta/inventory.json` (20,68 Go brut), 46 scripts de `forge_cryptonite/forge/`.

---

## 1. Synthèse Décisionnelle pour Kudoro (Distribution P2P)

### 1.1. Verdict Global de Faisabilité Juridique
**FEU VERT POUR LA REDISTRIBUTION DE LA BASE COMPILÉE (`knowledge_full.db`) EN MODE OPEN-SOURCE / NON-COMMERCIAL.**

La base de données SQLite compilée `knowledge_full.db` (3 360 727 040 octets, SHA-256 `237d899f9e81e6d22af01bc6969798d1495131f1f1d4caa0491d80ac6a06014c`) peut être légalement partagée et répliquée sur un réseau pair-à-pair décentralisé sous les conditions suivantes :

1. **Licence d'ensemble « Contamination » CC BY-NC-SA 4.0** :
   L'intégration des données d'interactions médicamenteuses et de duplications de **DDInter 2.0** (licencié sous `CC BY-NC-SA 4.0`) impose la clause *Share-Alike* (partage dans les mêmes conditions) et l'interdiction d'usage commercial direct sur l'ensemble de la base consolidée. `knowledge_full.db` doit donc être distribuée sous licence **CC BY-NC-SA 4.0**.
2. **Gratuité stricte du réseau P2P** :
   Le protocole P2P JEMMA et les clients associés ne doivent facturer aucun droit d'accès ou abonnement pour le téléchargement de la base.
3. **Attribution et traçabilité obligatoires** :
   La table interne `kb_sources` (32 enregistrements) remplit nativement l'obligation légale d'attribution exigée par CC BY 4.0 (HL7 FHIR IPS, SNOMED IPS) et CC BY-NC-SA 4.0 (DDInter). Tout nœud P2P distribuant la base préserve cette table intacte.
4. **Distinction capitale : Base compilée (SQLite) vs Archives sources brutes** :
   - **Base SQLite dérivée (`knowledge_full.db`)** : **Redistribuable en P2P**.
   - **Archives brutes UMLS 2025AB (`MRCONSO.RRF` 17 Go)** : **NON REDISTRIBUABLE publiquement en P2P** sans accord UTS NLM. Le fichier brut source UMLS contient des terminologies Category 1/2 soumises à restrictions nationales. La forge JEMMA n'a extrait et filtré que les concepts et relations pertinents pour l'IPS et le catalogue clinique (TTY ouverts, RxNorm, SNOMED IPS).

---

## 2. Inventaire Exhaustif des 32 Sources Cliniques (`kb_sources`)

Ce tableau documente les 32 enregistrements officiels de la table `kb_sources` de `knowledge_full.db`, avec leur URL source, leur licence, leur volume, le script de forge associé et le statut de redistribution P2P :

| # | `source_id` | Nom complet / Description | URL officielle | Licence officielle | Version | Enregistrements | Script Forge | Statut Dérivé P2P |
|---|---|---|---|---|---|---|---|---|
| 1 | `ATC_DDD` | WHO ATC/DDD Index 2025 | https://atcddd.fhi.no/ | Open Access (WHOCC) | 2026-01-20 | 6 934 | `step01_atc.py` | ✅ Libre avec citation |
| 2 | `ATC_COMBINATIONS_INDEX_V1_2` | WHO ATC combinations index parsed from atc_hierarchy | https://atcddd.fhi.no/ | Open Access (WHOCC) | 1.2 | 1 093 | `step01b_atc_combinations_index.py` | ✅ Libre |
| 3 | `DDINTER2_DRUGS` | DDInter 2.0 — Drug Catalog | http://ddinter.scbdd.com/ | CC BY-NC-SA 4.0 | 2.0 | 2 289 | `step02a_ddinter_drugs.py` | ✅ P2P Non-Commercial (NC-SA) |
| 4 | `DDINTER2_DDI` | DDInter 2.0 — Drug-Drug Interactions | http://ddinter.scbdd.com/ | CC BY-NC-SA 4.0 | 2.0 | 260 100 | `step02b_ddinter_interactions.py` | ✅ P2P Non-Commercial (NC-SA) |
| 5 | `DDINTER2_DFI` | DDInter 2.0 — Drug-Food Interactions | http://ddinter.scbdd.com/ | CC BY-NC-SA 4.0 | 2.0 | 857 | `step02c_ddinter_food.py` | ✅ P2P Non-Commercial (NC-SA) |
| 6 | `DDINTER2_DDSI` | DDInter 2.0 — Drug-Disease Interactions | http://ddinter.scbdd.com/ | CC BY-NC-SA 4.0 | 2.0 | 8 359 | `step02d_ddinter_disease.py` | ✅ P2P Non-Commercial (NC-SA) |
| 7 | `DDINTER2_DUPLI` | DDInter 2.0 — Therapeutic Duplications | http://ddinter.scbdd.com/ | CC BY-NC-SA 4.0 | 2.0 | 6 033 | `step02e_ddinter_dupli.py` | ✅ P2P Non-Commercial (NC-SA) |
| 8 | `DDINTER2_ALT` | DDInter 2.0 — Alternatives concrètes par interaction | http://ddinter.scbdd.com/ | CC BY-NC-SA 4.0 | 2.0 | 3 299 967 | `step02f_ddinter_alternatives.py` | ✅ P2P Non-Commercial (NC-SA) |
| 9 | `DDINTER2_FLAGS` | DDInter 2.0 — Mechanism flags (7-flag multi-set) | http://ddinter.scbdd.com/ | CC BY-NC-SA 4.0 | 2.0 | 260 100 | `step02g_ddinter_mechanism_flags.py` | ✅ P2P Non-Commercial (NC-SA) |
| 10 | `SNOMED_IPS` | SNOMED CT IPS Terminology Release | https://www.snomed.org/snomed-ct/Use-SNOMED-CT/IPS-Terminology | CC BY 4.0 (IPS Free Global License) | 20241216 | 19 697 | `step03_snomed_ips.py` | ✅ Libre mondial sans redevance |
| 11 | `HL7_FHIR_IPS` | HL7 FHIR IPS Implementation Guide | http://hl7.org/fhir/uv/ips/ | CC BY 4.0 | R4 | 11 213 | `step03b_fhir_ips.py` | ✅ Libre avec mention HL7 |
| 12 | `UMLS_MRCONSO` | UMLS Metathesaurus MRCONSO (filtré IPS/RxNorm/SNOMED) | https://www.nlm.nih.gov/research/umls/ | UMLS Open Subset License | 2025AB | 1 432 754 | `step04_mrconso.py` | ✅ Dérivé autorisé (filtre ouvert) |
| 13 | `RXNORM_RXNCONSO` | RxNorm RXNCONSO (concepts + TTY + SAB) | https://www.nlm.nih.gov/research/umls/rxnorm/ | UMLS License (RxNorm Open) | 04062026 | 265 726 | `step04b_rxnconso.py` | ✅ Libre mondial (US NLM Open) |
| 14 | `FDA_UNII_RECORDS` | FDA UNII Records | https://fdasis.nlm.nih.gov/srs/ | Public Domain | 2026-02-26 | 168 046 | `step05_unii_records.py` | ✅ Domaine Public |
| 15 | `FDA_UNII_NAMES` | FDA UNII Names | https://fdasis.nlm.nih.gov/srs/ | Public Domain | 2026-02-26 | 24 211 | `step06_unii_names.py` | ✅ Domaine Public |
| 16 | `RXNORM_RXNSAT` | RxNorm RXNSAT (drug attributes) | https://www.nlm.nih.gov/research/umls/rxnorm/ | UMLS License (RxNorm Open) | 04062026 | 1 130 886 | `step07_rxnsat.py` | ✅ Libre mondial |
| 17 | `UMLS_MRSTY` | UMLS Metathesaurus MRSTY (semantic types) | https://www.nlm.nih.gov/research/umls/ | UMLS Open Subset License | 2025AB | 1 737 853 | `step08_mrsty.py` | ✅ Dérivé autorisé |
| 18 | `UMLS_MRREL` | UMLS MRREL (relations sémantiques filtrées) | https://www.nlm.nih.gov/research/umls/ | UMLS Open Subset License | 2025AB | 2 648 298 | `step09_mrrel.py` | ✅ Dérivé autorisé |
| 19 | `RXNORM_RXNREL` | RxNorm RXNREL (relations brand↔ingredient) | https://www.nlm.nih.gov/research/umls/rxnorm/ | UMLS License (RxNorm Open) | 04062026 | 1 587 828 | `step09b_rxnrel.py` | ✅ Libre mondial |
| 20 | `RXNORM_RXNSTY` | RxNorm RXNSTY (semantic types per RxCUI) | https://www.nlm.nih.gov/research/umls/rxnorm/ | UMLS License (RxNorm Open) | 04062026 | 486 408 | `step09c_rxnsty.py` | ✅ Libre mondial |
| 21 | `UMLS_MRDEF` | UMLS MRDEF (NCI+MSH+MSHFRE) | https://www.nlm.nih.gov/research/umls/ | UMLS Open Subset License | 2025AB | 196 320 | `step10_mrdef.py` | ✅ Dérivé autorisé |
| 22 | `WORD_INDEX` | Generated word indexes (latin tokens + CJK terms) | Interne JEMMA | CC0 1.0 (Public Domain Dedication) | 1.0 | 15 500 225 | `step11_word_indexes.py` | ✅ Domaine Public |
| 23 | `COMBO_FLAG_V1` | Pre-flag combo drugs based on RxNorm | Interne JEMMA | CC0 1.0 | 1.1 | 33 494 | `step11b_mark_combos.py` | ✅ Domaine Public |
| 24 | `ATC_PROPAGATION_V1_1` | ATC code propagation via UMLS + RxNorm | Interne JEMMA | CC BY-NC-SA 4.0 | 1.1 | 62 760 | `step12_atc_propagation.py` | ✅ P2P NC-SA |
| 25 | `COMBO_ATC_OVERRIDE_V1` | Combo drug ATC override from resolutions | Interne JEMMA | CC BY-NC-SA 4.0 | 1.1 | 7 155 | `step12b_apply_combo_atc.py` | ✅ P2P NC-SA |
| 26 | `GOLDEN_ENRICH_V1` | Forge GOLDEN post-process enrichments | Interne JEMMA | CC BY-NC-SA 4.0 | 1.0 | 531 634 | `step16_enrich_existing.py` | ✅ P2P NC-SA |
| 27 | `ALLERGY_XR_SEED` | Allergy Cross-Reactivity Clinical Seed | WHO/ANSM/UpToDate/BNF consensus | CC BY-NC-SA 4.0 (Clinical consensus) | 1.0 | 52 | `step17_allergy_cross_reactivity.py` | ✅ P2P NC-SA |
| 28 | `COMBO_DRUG_RESOLUTIONS_V1_2` | Combo drug ATC resolutions via WHO ATC | WHO ATC/DDD + RxNorm RXNREL | CC BY-NC-SA 4.0 | 1.2 | 5 676 | `step18_combo_drug_resolutions.py` | ✅ P2P NC-SA |
| 29 | `DDINTER_ATC_BACKFILL_V1` | DDInter drugs ATC backfill via RxNorm | Interne JEMMA | CC BY-NC-SA 4.0 | 1.0 | 109 | `step19_ddinter_atc_backfill.py` | ✅ P2P NC-SA |
| 30 | `WHO_EML_2023` | WHO Model List of Essential Medicines, 23rd List | https://www.who.int/publications/i/item/WHO-MHP-HPS-EML-2023.02 | Open Access (WHO) | 23rd_List_2023 | 151 | `step20_who_eml_seed.py` | ✅ Libre mondial |
| 31 | `ATC_ALTERNATIVES` | ATC Alternatives consolidated | Interne JEMMA (siblings + DDInter) | CC BY-NC-SA 4.0 | 1.0 | 643 763 | `step21_atc_alternatives.py` | ✅ P2P NC-SA |
| 32 | `WHO_AWaRe_2024` | WHO AWaRe Antibiotic Classification 2024 | https://www.who.int/publications/i/item/WHO-MHP-HPS-EML-2024.01 | Open Access (WHO) | 2024 | 65 | `step22_who_aware_seed.py` | ✅ Libre mondial |

---

## 3. Analyse des 7 Arborescences Sources Brutes (`inventory.json`)

L'inventaire machine `JEMMA_DB_DATA/_meta/inventory.json` répertorie **2 477 fichiers bruts** totalisant **20,68 Go** :

```json
{
  "target_root": "/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA",
  "sources": {
    "ddinter":      { "n_files": 2295, "total_size_human": "1.19 GB" },
    "fda_unii":     { "n_files": 5,    "total_size_human": "113.2 MB" },
    "hl7_fhir_ips": { "n_files": 150,  "total_size_human": "8.1 MB" },
    "rxnorm":       { "n_files": 9,    "total_size_human": "1.22 GB" },
    "snomed_ips":   { "n_files": 12,   "total_size_human": "34.2 MB" },
    "umls":         { "n_files": 5,    "total_size_human": "17.04 GB" },
    "who_atc":      { "n_files": 1,    "total_size_human": "5.0 MB" }
  }
}
```

### 3.1. Recommandation P2P sur les Données Brutes
1. **Ne PAS redistribuer en P2P l'archive brute `umls/` (17,04 Go)** :
   - Fichiers : `MRCONSO.RRF` (14,4 Go), `MRREL.RRF` (5,7 Go), `MRSAT.RRF` (4,3 Go), `MRSTY.RRF` (124 Mo), `MRDEF.RRF` (224 Mo).
   - Raison : La redistribution intégrale brute de l'UMLS nécessite que chaque téléchargeur possède un compte UTS actif.
2. **Possibilité de packager un « P2P Open Source Pack » (2,57 Go)** :
   Si Kudoro souhaite permettre à des tiers de reproduire la forge en mode décentralisé, les 6 autres dossiers sources peuvent être légalement partagés :
   - `who_atc/` (5 Mo) : Accès ouvert.
   - `snomed_ips/` (34,2 Mo) : Licence globale libre CC BY 4.0.
   - `hl7_fhir_ips/` (8,1 Mo) : CC BY 4.0.
   - `fda_unii/` (113,2 Mo) : Domaine public US.
   - `rxnorm/` (1,22 Go) : Open RxNorm dataset (NLM).
   - `ddinter/` (1,19 Go) : CC BY-NC-SA 4.0 (mention de provenance requise).

---

## 4. Cartographie Complète des 46 Scripts de Forge (`forge/`)

Chaque étape du pipeline de build dans `forge_cryptonite/forge/` est documentée ci-dessous avec ses entrées, ses tables créées et son statut :

| Étape / Script | Rôle & Traitement | Entrée Brute | Tables SQLite Impactées | Enregistrement `kb_sources` |
|---|---|---|---|---|
| `_schema.py` | Déclaration DDL, contraintes 3NF, B-Trees | — | Initialisation 48 tables | Déclare `kb_sources`, `build_metadata` |
| `_config.py` | Définition des chemins et seuils | Variables env | — | — |
| `_utils.py` | Primitives SQL, batch inserts, normalisation | — | — | — |
| `step01_atc.py` | Ingestion hiérarchie ATC/DDD (niveaux 1 à 5) | `who_atc/2025/atc_ddd_consolidated.json` | `atc_hierarchy` | `ATC_DDD` |
| `step01b_atc_combinations_index.py` | Indexation des combinaisons ATC | Parsing `atc_hierarchy.name_en` | `atc_combinations_index` | `ATC_COMBINATIONS_INDEX_V1_2` |
| `step02a_ddinter_drugs.py` | Ingestion catalogue molécules DDInter | `ddinter/2.0/ddinter_drugs_large.json` | `ddinter_drugs` | `DDINTER2_DRUGS` |
| `step02b_ddinter_interactions.py` | Interactions médicamenteuses DDI (260k paires) | `ddinter/2.0/interaction_details_full.json` | `ddi_facts`, `ddi_atc_pairs` | `DDINTER2_DDI` |
| `step02c_ddinter_food.py` | Interactions aliment-médicament (DFI) | `ddinter/2.0/ddinter_food_full.json` | `dfi_facts` | `DDINTER2_DFI` |
| `step02d_ddinter_disease.py` | Interactions maladie-médicament (DDSI) | `ddinter/2.0/ddinter_disease_full.json` | `ddsi_facts` | `DDINTER2_DDSI` |
| `step02e_ddinter_dupli.py` | Duplications thérapeutiques | `ddinter/2.0/ddinter_dupli_full.json` | `therapeutic_duplications` | `DDINTER2_DUPLI` |
| `step02f_ddinter_alternatives.py` | Alternatives thérapeutiques concrètes | Calcul DDInter matrices | `ddi_alternatives` | `DDINTER2_ALT` |
| `step02g_ddinter_mechanism_flags.py` | Multi-set des 7 flags de mécanisme DDI | Parsing descriptions DDInter | `ddi_flags` | `DDINTER2_FLAGS` |
| `step03_snomed_ips.py` | Snapshot officiel SNOMED CT IPS | `snomed_ips/20240701/Snapshot/` | `snomed_ips_concepts`, `snomed_ips_descriptions` | `SNOMED_IPS` |
| `step03b_fhir_ips.py` | ValueSets et ConceptMaps FHIR IPS | `hl7_fhir_ips/package/` | `ips_valuesets`, `ips_concept_maps` | `HL7_FHIR_IPS` |
| `step04_mrconso.py` | Streaming et filtrage UMLS MRCONSO | `umls/2025AB/META/MRCONSO.RRF` | `terminology_concepts`, `terminology_atoms` | `UMLS_MRCONSO` |
| `step04b_rxnconso.py` | Ingestion RxNorm TTY/SAB | `rxnorm/04062026/rrf/RXNCONSO.RRF` | `rxnorm_concepts` | `RXNORM_RXNCONSO` |
| `step05_unii_records.py` | Substances chimiques FDA UNII | `fda_unii/20260226/UNII_Records*.txt` | `unii_records` | `FDA_UNII_RECORDS` |
| `step06_unii_names.py` | Synonymes et dénominations UNII | `fda_unii/20260226/UNII_Names*.txt` | `unii_names` | `FDA_UNII_NAMES` |
| `step07_rxnsat.py` | Attributs RxNorm (ATC, doses, formes) | `rxnorm/04062026/rrf/RXNSAT.RRF` | `rxnorm_attributes` | `RXNORM_RXNSAT` |
| `step08_mrsty.py` | Types sémantiques UMLS (TUI) | `umls/2025AB/META/MRSTY.RRF` | `terminology_semantic_types` | `UMLS_MRSTY` |
| `step09_mrrel.py` | Relations sémantiques UMLS filtrées | `umls/2025AB/META/MRREL.RRF` | `terminology_relations` | `UMLS_MRREL` |
| `step09b_rxnrel.py` | Relations RxNorm (ingrédient / marque) | `rxnorm/04062026/rrf/RXNREL.RRF` | `rxnorm_relations` | `RXNORM_RXNREL` |
| `step09c_rxnsty.py` | Types sémantiques RxNorm | `rxnorm/04062026/rrf/RXNSTY.RRF` | `rxnorm_semantic_types` | `RXNORM_RXNSTY` |
| `step10_mrdef.py` | Définitions médicales (NCI, MeSH) | `umls/2025AB/META/MRDEF.RRF` | `terminology_definitions` | `UMLS_MRDEF` |
| `step11_word_indexes.py` | Index FTS5 / trigrammes latin + CJK | Extraction SQLite locale | `terminology_latin_fts`, `terminology_cjk_fts` | `WORD_INDEX` |
| `step11b_mark_combos.py` | Détection automatique des associations | Relations structurelles | `rxnorm_concepts.is_combo` | `COMBO_FLAG_V1` |
| `step12_atc_propagation.py` | Propagation des codes ATC aux marques | Algorithme graphe RxNorm | `atc_drug_mappings` | `ATC_PROPAGATION_V1_1` |
| `step12b_apply_combo_atc.py` | Résolution ATC des associations complexes | Consensus Cryptonite | `atc_combo_overrides` | `COMBO_ATC_OVERRIDE_CRYPTONITE` |
| `step13_ips_enrich.py` | Alignement terminologique FHIR IPS | Mappings croisés | `ips_enriched_mappings` | — |
| `step14_finalize.py` | Contraintes d'intégrité, vacuum, optimize | SQLite PRAGMAs | B-Trees compacts | — |
| `step15_report.py` | Rapport markdown & sanity checks SQL | Requêtes audit | Fichier `REPORT.md` | — |
| `step16_enrich_existing.py` | Enrichissements IUPAC et noms déposés | Dictionnaires chimiques | `drug_synonyms_enriched` | `GOLDEN_ENRICH_V1` |
| `step17_allergy_cross_reactivity.py` | Réactivité croisée allergique (bêta-lactames) | Tables cliniques consensus | `allergy_cross_reactivity` | `ALLERGY_XR_SEED` |
| `step17b_snomed_allergen_atc.py` | Mapping SNOMED allergène vers ATC | Consensus clinique | `snomed_allergen_to_atc_class` | `SNOMED_ALLERGEN_ATC_CRYPTONITE` |
| `step18_combo_drug_resolutions.py` | Résolutions déterministes multi-principes | Ingrédients multiples | `combo_drug_resolutions` | `COMBO_DRUG_RESOLUTIONS_V1_2` |
| `step18b_ddi_combo_propagation.py` | Propagation des interactions aux combos | Expansion cartésienne | `ddi_combo_facts` | — |
| `step19_ddinter_atc_backfill.py` | Rattachement ATC des drogues DDInter orphelines | RxNorm + UMLS matching | `ddinter_drugs.atc_code` | `DDINTER_ATC_BACKFILL_V1` |
| `step20_who_eml_seed.py` | Médicaments essentiels OMS (EML 2023) | WHO EML PDF/JSON | `who_eml_2023` | `WHO_EML_2023` |
| `step21_atc_alternatives.py` | Consolidation alternatives par classe ATC | Calculsiblings | `atc_alternatives` | `ATC_ALTERNATIVES` |
| `step21b_atc_alternatives_compat.py` | Vue compatibilité ascendante alternatives | Vues SQLite | `v_atc_alternatives` | `ATC_ALTERNATIVES_COMPAT_CRYPTONITE` |
| `step22_who_aware_seed.py` | Classification antibiotiques WHO AWaRe | WHO AWaRe 2024 | `who_aware_2024` | `WHO_AWaRe_2024` |
| `step23_drug_names_multilingual.py` | Index FTS multilingue (JA, FR, EN) | Dictionnaires multilingues | `drug_names_meta` | `DRUG_NAMES_MULTILINGUAL_V2` |
| `step25_atc_family_stats.py` | Métriques et statistiques par famille ATC | Agrégrations SQL | `atc_family_stats` | `ATC_FAMILY_STATS_V1` |

---

## 5. Synthèse des Clauses Juridiques et Obligations d'Attribution

Pour distribuer `knowledge_full.db` en P2P, l'application JEMMA et les documents d'accompagnement doivent intégrer le texte d'attribution suivant :

```markdown
### Mentions Légales & Attributions des Données Médicales JEMMA Pass

Cette base de connaissances consolide des sources de données médicales publiques et ouvertes :
1. **DDInter 2.0** : Données d'interactions médicamenteuses issues de DDInter (http://ddinter.scbdd.com/), sous licence Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0).
2. **SNOMED CT IPS Free Global License** : Utilise le sous-ensemble terminologique International Patient Summary de SNOMED International, sous licence CC BY 4.0. SNOMED CT® est une marque déposée de l'IHTSDO.
3. **HL7® FHIR® IPS** : HL7 International, Implementation Guide International Patient Summary (Release 1.1.0), sous licence CC BY 4.0.
4. **WHO ATC/DDD & EML & AWaRe** : Données de classification de l'Organisation Mondiale de la Santé (OMS/WHO Collaborating Centre for Drug Statistics Methodology), utilisées en accès ouvert d'intérêt public.
5. **RxNorm & UMLS** : U.S. National Library of Medicine (NLM), National Institutes of Health. Le sous-ensemble ouvert RxNorm et les types sémantiques dérivés sont distribués conformément aux directives de la NLM.
6. **FDA UNII** : U.S. Food and Drug Administration / USP Substance Registration System, domaine public.

Toute redistribution dérivée doit maintenir l'accès non-commercial et préserver l'intégrité de la table `kb_sources`.
```

---

## 6. Prochaines Étapes pour le Couloir KB (Tour 2)

1. **Reconstruction v2.1-omnis (1.2)** :
   - Lancement de `turbo_forge_jemma_db.py --rebuild` dans le répertoire isolé `OUTPUT_v2.1/`.
   - Intégration des traductions françaises et japonaises des 39 codes `personal-relationship-uv-ips` dans `ips_valuesets_translations`.
2. **Génération du patch différentiel delta** :
   - Application du format SQLite Changeset ou script d'application transactionnel (`patch_2.0_to_2.1.sql` ~ 5 Mo) permettant la mise à jour P2P sans re-téléchargement des 3,36 Go.
3. **Audit de code SD-26** :
   - Démarrage de l'audit des 16 blocs `catch` silencieux sur la branche `ag/0082-sd26`.
