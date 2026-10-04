---
id: amelioration-Antigravity-KB-0002
couloir: KB
auteur: Antigravity-KB
date: 2026-10-04
relates_to: 0090, 0096, 0097
---
# Amélioration KB-0002 — Garde-fou d'audit automatique des SAB UMLS dans le pipeline de Forge

## 1. Constat et Pièce
- **Fichiers** : `forge_cryptonite/forge/step04_mrconso.py` (lignes 45-80) et `step09_mrrel.py` (lignes 35-70)
- **Constat** : Le pipeline de forge filtre le fichier `MRCONSO.RRF` (14,4 Go) et extrait 1 432 754 concepts/atomes pour peupler `terminology_concepts` et `terminology_atoms`. Cependant, le filtre applique des conditions sur les TTY (Term Types) et les langues, sans contrôle automatisé d'exclusion des SAB (Source Abbreviations) appartenant aux Catégories 1, 2, 3 ou 4 de l'Article 12 de l'UMLS Metathesaurus License Agreement.
- **Risque réel** : Une source comme `CPT` (Catégorie 3 — redevances obligatoires, interdiction d'usage en accès public Internet) ou `MSHFRE` (Catégorie 1 — interdiction de dérivation) pourrait être injectée par inadvertance dans la base SQLite compilée `knowledge_full.db`, exposant le projet à un contentieux de violation de propriété intellectuelle lors de la distribution en ligne ou P2P.

## 2. Ce que ça coûte à une vraie personne
Pour Kudoro et les utilisateurs de JemmaPass, une fuite d'un vocabulaire propriétaire de Catégorie 3 (ex. CPT de l'AMA) dans une base de données médicale distribuée publiquement entraîne un risque de mise en demeure juridique et de réclamation de redevances substantielles par les ayants droit.

## 3. Correction proposée
Dans `forge_cryptonite/forge/` :
1. Créer une liste d'exclusion explicite `FORBIDDEN_UMLS_SABS` contenant tous les vocabulaires des Catégories 1 à 4 non couverts par un accord spécifique pour l'IPS.
2. Ajouter une assertion bloquante dans `step04_mrconso.py` et `step15_report.py` qui vérifie par une requête SQL directe sur `terminology_atoms` :
   `SELECT DISTINCT sab FROM terminology_atoms WHERE sab IN (FORBIDDEN_SABS);`
   Si le compte est supérieur à 0, le build échoue immédiatement avec le détail des SAB incriminés.

## 4. Ce qu'elle risque de casser
Aucun risque sur les données cliniques existantes si la liste d'exclusion est calibrée sur les catégories strictes UMLS (RxNorm, SNOMED IPS, MeSH anglais et NCI restant autorisés en Catégorie 0).

## 5. Comment on saura que c'est corrigé
La sortie du script de validation `python3 turbo_forge_jemma_db.py --validate-licences` (ou lors du step15) affichera `SAB license audit: 0 forbidden sources detected across 1,432,754 atoms` et le test unitaire JVM vérifiera l'absence de concepts interdits.
