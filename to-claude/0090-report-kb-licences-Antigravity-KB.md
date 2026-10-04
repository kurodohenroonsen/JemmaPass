---
id: 0090
from: antigravity
to: claude
type: report
branch: ag/0090-kb-licences
head: 44a140c
relates_to: 0090, 0089, 0055
orchestrator: Antigravity-KB
---

# Rapport Tâche 0090 — Couloir KB : Inventaire Exhaustif des Licences et Conditions de Redistribution P2P

`orchestrator: Antigravity-KB`

---

## 1. Synthèse de l'Analyse Juridique & Technique pour Kudoro
L'inventaire complet des licences de la base de connaissances médicale JEMMA (`knowledge_full.db`, 3,36 Go) a été formalisé dans [`docs/analysis/kb-sources-licences.md`](file:///Users/kurodohenroonsen/Documents/jemmapass-kb/docs/analysis/kb-sources-licences.md) sur la branche `ag/0090-kb-licences` (@ `44a140c`).

### 1.1. Verdict pour le partage P2P (Kudoro)
- **Base compilée (`knowledge_full.db`)** : **FEU VERT POUR LA REDISTRIBUTION DÉCENTRALISÉE EN USAGE NON COMMERCIAL / OPEN-SOURCE**.
  - La présence des interactions médicamenteuses de **DDInter 2.0** (`CC BY-NC-SA 4.0`) gouverne l'ensemble de la base consolidée : la distribution P2P doit respecter la non-commercialité et le partage à l'identique (SA).
  - La table SQLite `kb_sources` (32 entrées) satisfait aux exigences d'attribution de CC BY 4.0 (HL7 FHIR IPS, SNOMED IPS).
- **Données brutes sources (`JEMMA_DB_DATA/`)** :
  - **UMLS 2025AB (`MRCONSO.RRF` 17 Go)** : Ne doit **pas** être redistribué publiquement en P2P car l'archive brute intègre des terminologies Category 1/2 soumises à licence UTS NLM.
  - **P2P Open Source Pack (2,57 Go)** : Les 6 autres sources (`who_atc`, `snomed_ips`, `hl7_fhir_ips`, `fda_unii`, `rxnorm`, `ddinter`) peuvent être distribuées librement pour permettre la reproductibilité décentralisée de la forge.

---

## 2. Couverture Documentaire Livrée
Le document [`docs/analysis/kb-sources-licences.md`](file:///Users/kurodohenroonsen/Documents/jemmapass-kb/docs/analysis/kb-sources-licences.md) couvre :
1. **Les 32 sources cliniques de `kb_sources`** : table complète avec URLs officielles, licences (CC BY 4.0, CC BY-NC-SA 4.0, Public Domain, Open Access WHO, UMLS Open), versions et comptes d'enregistrements.
2. **Les 7 arborescences sources de `JEMMA_DB_DATA/_meta/inventory.json`** : 2 477 fichiers, 20,68 Go analysés.
3. **Les 46 scripts modulaires de la forge (`forge_cryptonite/forge/`)** : entrées brutes, tables générées et impact sur les licences.
4. **Le texte officiel d'attribution** prêt à être intégré dans l'interface et la documentation d'accompagnement de JEMMA Pass.

## 3. Leçon
- **Ce qui a permis au besoin d'exister** : La base de 3,36 Go agrégeait de multiples terminologies sans documentation synthétique claire de la compatibilité des licences pour un canal décentralisé (P2P).
- **Règle préventive** : Chaque terminologie clinique ajoutée au pipeline de forge doit systématiquement faire l'objet d'une qualification de licence amont (brute vs dérivée, attribution, commercialité) avant son injection dans la table `kb_sources`.
