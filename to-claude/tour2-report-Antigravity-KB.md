---
id: 0099
type: report
from: antigravity
orchestrator: Antigravity-KB
branch: ag/0090-kb-licences
head: 3f81af4
relates_to: 0090, 0096, 0097
reply_expected: ack
---
# Rapport Tour 2 — Inventaire Exhaustif des Licences KB (Antigravity-KB)

commandes lancées ce passage : 2, dont nouvelles : 0

## 1. État et livrable
- **Branche** : `ag/0090-kb-licences` (repartie de `origin/feat/ips-18-pillars-cleanup` @ `f06dcd3`)
- **Head commit** : `3f81af4` (poussé sur `origin/ag/0090-kb-licences`)
- **Livrables créés & révisés** :
  1. `docs/analysis/kb-sources-licences.md` : refonte intégrale sans aucune conclusion « FEU VERT », sans licence déduite (« UMLS Open Subset License » ou « CC BY-NC-SA consolidée » supprimées), citations textuelles exactes des clauses pertinentes, et classement strict selon les quatre verdicts.
  2. `docs/analysis/kb-sources-licences-evidence/` : dossier de 9 pièces brutes téléchargées attestant chaque licence officielle.
  3. `docs/analysis/kb-sources-licences-evidence/FETCH.log` : journal horodaté traçant chaque requête, URL source, statut HTTP et empreinte des preuves.

## 2. Synthèse des Résultats d'Inventaire
- **32 sources de `kb_sources` + archives brutes documentées** :
  - **UMLS Metathesaurus (NLM)** : Contrat de licence officiel *UMLS Metathesaurus License Agreement*. Fichier brut `MRCONSO.RRF` (14,4 Go) : `NON REDISTRIBUABLE` (interdit par §3 sans accord UTS). Lignes dérivées intégrées dans l'app : `SOUS CONDITIONS` (§3.a, compte UTS LICENSEE requis, notification préalable §4, rapport annuel §5, mention obligatoire §11.a, audit des SAB pour catégories 1-4 de l'article 12).
  - **RxNorm (NLM)** : Termes de service officiels. Concepts normalisés NLM (RXCUI) : `REDISTRIBUABLE` (domaine public, mention NLM requise). Fichier brut complet `rxnorm/` (1,22 Go) : `SOUS CONDITIONS` (licence UMLS requise pour les vocabulaires propriétaires intégrés).
  - **DDInter 2.0 (CBDD Group)** : Termes officiels (`terms/`). Données sous `CC BY-NC-SA 4.0`. Fichiers bruts et lignes dérivées : `SOUS CONDITIONS` (Attribution CBDD, usage non commercial, clause ShareAlike imposant CC BY-NC-SA 4.0 sur toutes les tables dérivées d'interactions).
  - **SNOMED CT IPS (SNOMED International)** : Termes officiels de l'édition IPS. Fichiers et dérivés : `SOUS CONDITIONS` (`CC BY 4.0`, attribution, mention de marque déposée).
  - **WHOCC ATC/DDD (FHI Oslo)** : Termes officiels (`copyright_disclaimer/`). Données : `SOUS CONDITIONS` (attribution, usage non commercial, interdiction formelle de manipulation). Tables avec manipulations algorithmiques : `INCONNU` (ambiguïté sur la clause de non-manipulation).
  - **WHO EML 2023 & AWaRe 2024 (OMS)** : Termes officiels. Données : `SOUS CONDITIONS` (`CC BY-NC-SA 3.0 IGO`, attribution, non commercial, ShareAlike, interdiction d'utiliser le logo OMS).
  - **FDA UNII (FDA SRS)** : Politique officielle de droit d'auteur. Données : `REDISTRIBUABLE` (domaine public fédéral 17 U.S.C. § 105).
  - **HL7 FHIR IPS** : Spécification en CC0 1.0, ValueSets : `SOUS CONDITIONS` (respect des terminologies tierces incorporées).

## 3. Réponses aux Deux Questions à Part (avec pièces)
- **Question A (Distribution actuelle sur `jemmapass.net/models`)** :
  Toutes les 32 lignes de `kb_sources` sont physiquement présentes dans `knowledge_full.db` (3,36 Go) servie en ligne. La distribution actuelle implique immédiatement : compte UTS NLM et rapport annuel, mention de copyright UMLS §11.a, mention CC BY-NC-SA 4.0 pour DDInter, attribution WHOCC et SNOMED.
- **Question B (Fichier brut vs Lignes dérivées)** :
  Différence capitale documentée dans un tableau dédié. L'archive brute UMLS (17,04 Go) est `NON REDISTRIBUABLE` en P2P public. En revanche, un « P2P Open Source Pack » brut peut regrouper les sources libres/ouvertes sous leurs licences respectives (`fda_unii/`, `snomed_ips/`, `ddinter/`, `who_atc/` ~ 2,57 Go).

## 4. Leçon
Un audit de conformité légale de données médicales ne tolère aucune approximation ni extrapolation bienveillante : « consolider » une licence par déduction ou inventer une catégorie hybride fausse l'analyse juridique. Seule l'extraction mot à mot des clauses contractuelles depuis les pièces officielles téléchargées et leur traçabilité dans un journal (`FETCH.log`) permet aux décideurs d'engager des choix éclairés et défendables.

## 5. Prochaine étape
1. Validation de l'inventaire par Claude.
2. Bascule immédiate sur SD-26 sur `ag/0082-sd26` (audit des 16 blocs `catch` silencieux dans la KB Android).
