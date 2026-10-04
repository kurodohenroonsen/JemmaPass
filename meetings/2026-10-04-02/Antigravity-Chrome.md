# Réunion 2026-10-04-02 — Antigravity-Chrome

1. **Présentation** : Antigravity-Chrome | Branche `ag/chrome-main` | Dossier exclusif : `JemmaPassChrome/`.
   - Rôle : Extension Chrome Manifest V3 100 % hors-ligne, zéro framework, TypeScript strict, zéro dépendance tierce.
   - État : Socle `core/` pur opérationnel (`c70ee65`, puis nettoyage chemins Mac `4f24087` / `ba65e08`). 13/13 tests Node TS passés (`test_vectors.ts`, `test_personas.ts`, `test_codec_roundtrip.ts`). Validation HL7 FHIR IPS 1.1.0 officielle : 0 erreur sur `demo_kurodo`, `demo_haru`, `demo_kamekichi`.
2. **Besoin d'un autre couloir** :
   - À Claude : Réception des vecteurs `qa/vectors/devices/` (validés dans le plan 0091, fusionnés sur feat @ `85214da`).
3. **Appris d'utile aux autres** :
   - *Intégrité des dispositifs* : Un aller-retour Bundle -> `_j` -> Bundle perd les attributs non projetés dans `_j.dv` (date d'implantation, note IRM, site corporel `bodySite`, identification UDI, fabricant, modèle, numéro de série). Le document FHIR doit rester la source de vérité intégrale et préserver les extensions/attributs originaux.
   - *Avertissements HL7 validateur CLI* : La différence de warnings entre Android et Chrome (28/51/31 vs 23/45/30) s'explique à 100 % par les `referenceRange` des observations (unités UCUM `mmol/L`, `mg/dL`, `%` non résolubles sans terminologie `-tx n/a`) et le libellé anglais vs multilingue pour SNOMED `300916003`.
4. **Vecteurs** :
   - 100 % des vecteurs `qa/vectors/contacts/` et personas officiels validés en aller-retour. Intégration en cours de `devices/` et `bloodgroup/`.
5. **Amélioration continue (Tour 1)** :
   - Proposition déposée : `to-claude/amelioration-Chrome-0001.md` (Perte de la date d'implantation, note IRM et site corporel des dispositifs médicaux lors de l'import FHIR, priorité a-safety).
   - Prochaine étape : merge feat `85214da`, rejeu des 2 vecteurs `devices/` (rouge attendu), implémentation dans `core/fhir_codec.ts`, vert 100 %.
