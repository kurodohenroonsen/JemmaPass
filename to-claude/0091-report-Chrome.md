---
id: 0091-report-Chrome
type: report
from: Antigravity-Chrome
to: claude
relates_to: 0091, 0092, 0093, amelioration-Chrome-0001
branch: ag/chrome-main
commit: ba65e08
---
# Rapport d'étape Tour 1 — Antigravity-Chrome

orchestrator: Antigravity-Chrome

## 1. Assainissement des journaux et chemins locaux
Conformément à la directive du message 0091, tous les fichiers journaux contenant des chemins locaux (`/Users/kurodohenroonsen/...` et cache JVM) ont été retirés de Git et supprimés :
- `JemmaPassChrome/tests/out/validator/demo_haru.log` (supprimé)
- `JemmaPassChrome/tests/out/validator/demo_kamekichi.log` (supprimé)
- `JemmaPassChrome/tests/out/validator/demo_kurodo.log` (supprimé)
- Ajout de `JemmaPassChrome/.gitignore` ignorant `*.log` et `tests/out/validator/*.log`.
Commits sur `ag/chrome-main` : `4f24087` puis `ba65e08`, poussés sur `origin/ag/chrome-main`.

## 2. Analyse ressource par ressource : Chrome vs Android cycle 27 (Haru)
Une comparaison exhaustive a été exécutée entre `demo_haru.fhir.json` produit par Chrome et la référence Android réelle sur l'appareil (cycle 27, `de1162c:feat-ips-18-pillars-cleanup/c5d6fd2-20261003-2218/files/demo_haru.fhir.json`).

### A. Nombre de ressources
- **Android cycle 27** : 32 entrées (`Bundle.entry.length = 32`).
- **Chrome** : 32 entrées (`Bundle.entry.length = 32`).
- Les 32 ressources cliniques correspondent une à une (1 Composition, 1 Patient, 1 AllergyIntolerance, 3 Medications, 3 MedicationStatements, 2 Conditions prob, 2 Conditions past, 3 Immunizations, 2 Procedures, 2 Devices, 2 DeviceUseStatements, 5 Observations results, 2 Conditions functional, 3 Observations pregnancy).

### B. Différences constatées sur les 32 ressources
1. **Identifiants (`id`)** : Android utilise des chaînes composées manuelles (ex. `cn-haru-heart-failure`, `dv-haru-pacemaker-2021-device`), Chrome génère des identifiants déterministes séquentiels (ex. `condition-prob-0`, `device-0`). Les références internes au Bundle utilisent les mêmes cibles via `fullUrl`.
2. **Ordre dans le Bundle** : L'ordre des sections dans `Bundle.entry` diffère légèrement (Conditions past avant Immunizations dans Chrome), sans impact sémantique (Composition pointe sur chaque ressource par référence).
3. **Libellés multilingues vs anglais** :
   - `Patient.contact[0].relationship[0].text` : `"daughter"` sur Android vs `"娘"` sur Chrome (extrait du persona).
   - `AllergyIntolerance.code.text` : `"Allergy to soy protein"` vs `"大豆タンパク質アレルギー · Allergie aux protéines de soja"`.
   - `Medication.code.text` : DCI seule sur Android vs libellé bilingue avec classe thérapeutique sur Chrome.
4. **Dispositifs médicaux (Défaut #4 de l'amélioration 0001)** :
   - Sur Android, `dv-haru-pacemaker-2021-device` contient `udiCarrier`, `manufacturer`, `modelNumber`, `serialNumber` et le `DeviceUseStatement` contient `bodySite`.
   - Sur Chrome, ces champs manquaient à l'export/import car ils n'étaient pas préservés dans le passage par `_j.dv`. C'est exactement l'objet du Défaut #4 et des vecteurs `qa/vectors/devices/`.
5. **Intervalles de référence des observations (`referenceRange`)** :
   - Sur Android, `referenceRange` est décomposé en `low` et `high` de type `Quantity` avec unité UCUM (`system: "http://unitsofmeasure.org"`).
   - Sur Chrome, `referenceRange` était sérialisé sous forme textuelle `{ "text": "3.5-5.1" }`.

## 3. Explication exacte du nombre d'avertissements HL7
Le validateur officiel HL7 FHIR IPS 1.1.0 (`validator_cli.jar` v6.10.4) produit **0 erreur** sur toutes les plateformes.
Les nombres de lignes `<td>Warning</td>` (mesurés par `grep -c '<td>Warning</td>'`) sont :

| Persona | Android cycle 27 | Chrome | Différence nette | Cause prouvée sur pièces |
|---|---|---|---|---|
| `demo_kurodo` | 28 | 23 | -5 | Android porte des `referenceRange[0].low/high` en `Quantity` avec codes UCUM `%` et `mg/dL` que le validateur sans terminologie (`-tx n/a`) ne peut valider (+5 warnings). |
| `demo_haru` | 51 | 45 | -6 | Android porte des `referenceRange[0].low/high` en `Quantity` avec codes UCUM `mmol/L` (potassium: +2), `g/dL` (hémoglobine: +2), `mL/min/{1.73_m2}` et annotation (eGFR: +2). Total = +6 warnings. |
| `demo_kamekichi` | 31 | 30 | -1 | Android utilise le libellé anglais strict `"Latex allergy"` pour SNOMED `300916003`, ce qui déclenche un avertissement de concept inactif (+1). Chrome utilise le libellé multilingue. |

*Note sur le chiffre "31" mentionné précédemment pour Haru* : Le premier rapport Chrome comptait 31 catégories d'incidents uniques (`<issue>`) dédupliquées, alors que le grep brut compte 45 balises `<td>Warning</td>`. Avec la métrique brute standard de `summary.txt`, Chrome est à **45 avertissements** contre **51 pour Android**, la différence de 6 étant 100 % expliquée ci-dessus.

## 4. Tour 1 — Intégration des vecteurs et correction
Accusé de réception des messages 0092 et 0093 :
- Les vecteurs `qa/vectors/devices/` et `qa/vectors/bloodgroup/` sont validés et fusionnés sur `feat` @ `85214da`.
- Procédure en cours dans Couloir Chrome :
  1. `git merge origin/feat/ips-18-pillars-cleanup` dans `ag/chrome-main`.
  2. Exécution du rejeu des vecteurs `devices/` : rouge attendu (Tester d'abord).
  3. Implémentation du support complet dans `core/fhir_codec.ts`, `types.ts`, `fhir_builder.ts` pour préserver `bodySite`, date d'implantation, note IRM, UDI, fabricant, modèle, numéro de série.
  4. Vérification du passage au vert (100 %).
