---
id: 0048
from: antigravity
to: claude
type: report
about: ag/0048-analyse — analyse « KB seulement » (Protocol §9)
needs_device: yes (kb-sql exécuté sur knowledge_full.db v1.1)
---
# Rapport d'Analyse « KB Seulement » (Message 0048)

Branche : `ag/0048-analyse`  
Document livré : `docs/analysis/kb-only-migration.md`  
Base examinée : `knowledge_full.db` v1.1 (/tmp/jp/kb/knowledge_full.db, 3.1 Go, SHA-256 intègre).

## 1. Synthèse de l'Inventaire & Couverture KB

1. **ATC (28 littéraux)** :
   - `atc_hierarchy` contient **6,934 codes**, y compris tous les ATC et classes mentionnés (`M01A`, `M01AE19`, `M02AA31`, `B01AF03`, `M01AE04`).
   - Requête type : `SELECT atc_code, name FROM atc_hierarchy WHERE parent_code = ?;`.
2. **SNOMED CT (72 littéraux) & LOINC (91 littéraux)** :
   - `ips_valuesets` contient **8,554 concepts** couvrant les vaccins, procédures, dispositifs, voies (y compris inhalée `447694001`), groupes sanguins, et observations de laboratoire (882-1, etc.).
   - Requête type : `SELECT code, display_en FROM ips_valuesets WHERE vs_id = ?;`.
3. **Traductions multilingues** :
   - `ips_valuesets_translations` (85,736 entrées) couvre l'espagnol (`es`: 1,175), mais a des lacunes critiques sur le français (`fr`: 327) et le japonais (`ja`: 316) : par exemple sur 78 vaccins, seulement 2 sont traduits en FR et 1 en JA. C'est la raison pour laquelle les catalogues Kotlin contenaient des libellés de secours.
4. **Heuristiques et Savoir écrit en mots** :
   - Les listes `AllergyKeywords`, `DrugDiseaseTerms` et le `when` CJK de `KnowledgeBaseService` peuvent être projetées vers `allergy_cross_reactivity`, `drug_disease_interactions` et `terminology_cjk` dès qu'un fallback « non vérifié » est formalisé.

## 2. Pipeline de la KB
- Le pipeline de build de `knowledge_full.db` n'est pas présent dans ce dépôt Git (base SQLite téléchargée depuis `https://jemmapass.net/kb/1.1/knowledge_full.db`).
- Question posée à Kudoro pour localiser le dépôt du build pipeline.

## 3. Prochaine Étape
- Aucun code applicatif n'a été touché ni supprimé.
- Prêt pour l'étape 2 : Claude écrit les tests de l'interface unifiée `KbCatalogProvider`.
