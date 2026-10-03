# Antigravity-Chrome — Réunion 2026-10-04-01

`orchestrator: Antigravity-Chrome`

1. **Présentation & Avancement** :
   - Branche : `ag/chrome-main`, dossier : `JemmaPassChrome/`.
   - Module `core/` pur (TypeScript strict sans DOM/chrome.*) développé et testé sous Node 22 : 13/13 tests passés en 5.3s (rejeu des 6 vecteurs contacts `qa/vectors/contacts/` ct-001..006, 3 personas démo, roundtrip compression `_j2`).
   - Validateur HL7 FHIR IPS 1.1.0 (`validator_cli.jar`) : 0 erreur sur `demo_kurodo.fhir.json` (23 warnings conformes à Android).

2. **Besoins d'autres couloirs** :
   - **Antigravity-UX** : Spécification des écrans Chrome (popup d'extension 400x600 px vs page d'onglet dédiée) pour la navigation entre les 18 piliers et le bouton d'urgence.
   - **Antigravity-Analyse** : Cartographie Tranche 2 des formulaires d'édition pour aligner les champs modifiables hors-ligne.

3. **Ce que j'ai appris d'utile aux autres** :
   - Dans le validateur HL7 IPS 1.1.0, les `DeviceUseStatement` exigent `timing[x]` (1..1) : mettre `_timingDateTime` avec extension `data-absent-reason: unknown` si la date est inconnue.
   - Les observations de résultats exigent un terme officiel LOINC strict dans `coding[0].display` (ex: `882-1` = `"ABO and Rh group [Type] in Blood"`), le libellé convivial voyageant dans `text`.

4. **Vecteurs manquants (par ordre de priorité)** :
   - 1. `qa/vectors/allergies/` : formats avec et sans criticité / statut clinique.
   - 2. `qa/vectors/medications/` : posologies fractionnées, unités non-UCUM.
   - 3. `qa/vectors/results/` : réconciliation groupe sanguin 882-1 vs `p.bt`.

5. **Analyse et UX** : Lu `00-carte.md` (Tranche 1). En attente des retours UX avant de coder l'interface HTML fine.

6. **Amélioration continue (Tour 1)** :
   - Déposé `to-claude/amelioration-Chrome-0001.md` (priorité a-safety) : préservation de `timingDateTime`, `note` ("MRI-conditional") et `bodySite` lors de l'import de `DeviceUseStatement` FHIR dans `fhir_codec.ts`.
