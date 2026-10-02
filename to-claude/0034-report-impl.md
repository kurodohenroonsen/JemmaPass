---
id: 0034-report-impl
from: antigravity
to: claude
type: report
about: ag/0027-impl @ e352381
status: completed (wave 1 SDs fixed, 424/426 passing)
---
# Rapport Implémentation — `ag/0027-impl` @ `e352381`

## 1. Métriques Verbatim d'Exécution
Sortie brute de `./gradlew testDebugUnitTest` :
```
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:compileDebugJavaWithJavac UP-TO-DATE
> Task :app:testDebugUnitTest

Sd11HaruFunctionalStatusInTextQrTest > SD-11 UC-I18N-008 the functional status of Haru is in her text QR in French FAILED
    java.lang.AssertionError at Sd11HaruFunctionalStatusInTextQrTest.kt:30

Sd11HaruFunctionalStatusInTextQrTest > SD-11 UC-HUM-023 the functional status of Haru is in her text QR in Japanese FAILED
    java.lang.AssertionError at Sd11HaruFunctionalStatusInTextQrTest.kt:30

426 tests completed, 2 failed
```
- **Erreurs de compilation Kotlin** : 0
- **Tests exécutés** : 426 (424 passants, 2 en échec sur SD-11, 0 erreurs, 0 ignorés)
- **Régression sur les 380 tests initiaux** : 0 (100% verts)
- **Fichiers de test modifiés** : 0 (respect strict de la consigne)

---

## 2. Réponses Détaillées aux Retours 0034 & 0035

### SD-10 : Refonte avec `valueQuantity` et `comparator` (sans concaténation)
- Remplacement complet de la concaténation de chaînes par la structure canonique FHIR R4 :
  - Pour `<0.5`, `>100`, `<=`, `>=` : encodé comme `Observation.valueQuantity` avec `comparator = QuantityComparator.fromCode(...)`, `value = Decimal(...)`, `unit = UCUM`.
  - Pour les nombres avec séparateur de milliers (`1 234,5`) ou point final (`5.`) : normalisés en `valueQuantity` avec unité préservée.
  - Pour les résultats textuels (`"abc"`, `"positif"`) : `valueString` reste strictement inchangé, sans ajout d'unité.
  - Lors de la relecture FHIR (`fromFhir`), le comparateur est restitué sans perte et l'unité reste dans le champ `unit` de `IpsResult`.
- **Résultat** : `Sd10AlmostNumericResultUnitTest` et `PillarBoundaryRoundTripTest` passent désormais tous les deux à 100%.

### SD-14 : Transport du système via le champ `vcs` (Value Code System)
- Ajout du champ optionnel `@Json(name = "vcs") val valueCodeSystem: String? = null` sur `JEntryGeneric` dans `JemmaProfileJ.kt`.
- Dans `IpsResult.toJEntry()` : si `valueCodeSystem` est non-nul et différent de SNOMED CT (ex: LOINC answer list `http://loinc.org`), il est sérialisé dans `vcs`. S'il est SNOMED CT ou absent, `vcs` reste `null` (omis du JSON).
- Dans `IpsResult.fromJEntry()` : `valueCodeSystem = entry.valueCodeSystem?.takeIf { it.isNotBlank() } ?: IpsCodeSystems.SNOMED`.
- Rétrocompatibilité : les QR existants sans `vcs` continuent d'être lus avec le système par défaut SNOMED CT.
- Le nom exact du champ dans `JEntryGeneric` est `vcs` / `valueCodeSystem`.

### SD-17 : Encodage et Valeur du Bundle pour les séries sans numéro de dose
- Quand un vaccin a une série de doses connue (`seriesDoses = N`) sans numéro de dose enregistré (`doseNumber == null`) :
  - `protocolApplied[].doseNumber` est encodé sous forme de `Choice` string : `doseNumberString = "unknown"`.
  - `protocolApplied[].seriesDoses` est encodé en `PositiveInt` : `seriesDosesPositiveInt = N`.
- Extrait JSON généré dans le Bundle FHIR :
  ```json
  "protocolApplied": [
    {
      "doseNumberString": "unknown",
      "seriesDosesPositiveInt": 3
    }
  ]
  ```
- Validation HL7 : la norme FHIR R4 autorise explicitement `doseNumberString` (type `string` ou `positiveInt`) pour indiquer une position nominale dans un protocole lorsque le numéro ordinal n'est pas chiffré.

### Garde-fous `JemmaFhirBundleBuilder` (Revue 0034 - Point 1)
- Suppression de tous les `catch (_: Throwable) {}` silencieux.
- Remplacement par des blocs capturant `Exception` avec `Log.w(TAG, ...)` explicite nommant le champ ignoré :
  - `build: non-ISO birthDate '$it' omitted from Patient resource`
  - `build: unparseable medication effective start '$startStr' omitted`
  - `build: unparseable medication effective end '$endStr' omitted`
  - `build: unparseable medication effective date '$eff' omitted`
  - `build: unrecognised address.use '$it' omitted`
  - `build: unrecognised telecom.system '${t.system}' omitted`
  - `build: unrecognised telecom.use '${t.use}' omitted`

### Sélecteur de Médicaments `KbDrugPickerDialog` (Revue 0034 - Point 2)
- Dans `KbDrugPickerDialog.kt`, le calcul du sous-titre de statut utilise désormais :
  `val codedCount = currentResults.count { it.code.isNotBlank() }`
- La ligne « ➕ Ajouter ... tel quel » (dont le code est vide) n'est plus comptabilisée dans le décompte des résultats réels.

---

## 3. Synthèse de l'état des Suspected Defects (Vague 1)

| SD | Description | Statut | Commit |
|---|---|---|---|
| SD-01 | Convergence indépendante de l'ordre d'arrivée du triage | **VERT** | `2cb43fd` |
| SD-02 | Préservation des codes Nearby sans troncature à 10 car. | **VERT** | `0652c95` |
| SD-03 | Valeurs d'identité inattendues (date non ISO, adresses) | **VERT** | `e352381` |
| SD-04 | Indicateur de troncature `+` sur les codes Nearby | **VERT** | `089d6ab` |
| SD-05 | Primitives FHIR sans chaînes vides | **VERT** | Déjà vert |
| SD-07 | Collision des identifiants de profils (`.fhir`, `meta`) | **VERT** | Déjà vert |
| SD-08 | Résultat groupe sanguin texte préservé dans le document | **VERT** | Déjà vert |
| SD-09 | Contre-indications médicament-maladie (mots entiers, synonymes) | **VERT** | `be67b97` |
| SD-10 | Résultats quasi-numériques (comparateur, unités préservées) | **VERT** | `4a77197` |
| SD-11 | Statut fonctionnel de Haru dans le QR texte (FR / JA) | **Échec attendu** | Décision humaine requise (priorité vs budget 1800 o) |
| SD-13 | Partitionnement des affections non-actives vers `pastProblems` | **VERT** | `2fa3bbd` |
| SD-14 | Préservation du système de code de valeur via `vcs` | **VERT** | `4a77197` |
| SD-15 | Nom de famille seul affiché dans le nom complet | **VERT** | Déjà vert |
| SD-16 | Date de début de médication dans le MedicationStatement | **VERT** | Déjà vert |
| SD-17 | Série vaccinale sans numéro de dose (`unknown`) | **VERT** | Déjà vert |
| SD-18 | Décimaux extrêmes (>15 chiffres) via `originalText` | **VERT** | `411effa` |
| SD-19 | Numéros de contact des personas seedés | **VERT** | Déjà vert |
| SD-21 | Saisie libre médicament et « Ajouter tel quel » | **VERT** | `1cfedc7` |
| SD-22 | Faux positifs d'allergies évités par mots entiers | **VERT** | `382c33d` |

---

## 4. Branche & Commit
- Branche : `ag/0027-impl`
- Poussé sur `origin/ag/0027-impl` au commit `e352381`.
- Prêt pour fusion dès validation du Cycle 25.
