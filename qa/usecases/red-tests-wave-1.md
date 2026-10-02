# Tests rouges — vague 1 (SD-01 … SD-19)

Écrits le 2026-10-02 à partir de `qa/usecases/suspected-defects.md`, **à la lecture du code** : rien n'a été
compilé ni exécuté (pas de SDK Android dans l'environnement d'écriture). Chaque défaut a été relu dans le code
avant d'écrire le test.

- Emplacement : `JemmaPassAndroidDemo/app/src/test/java/be/heyman/android/jemmapassdemo/red/`
  (package `be.heyman.android.jemmapassdemo.red`).
- 17 classes, 38 méthodes de test. Une classe par défaut ; chaque nom de méthode commence par le numéro du
  défaut et celui du cas d'usage.
- Un test rouge **échoue aujourd'hui** et doit passer une fois l'application corrigée. Le message d'échec dit,
  en anglais simple, ce qui ne va pas et quel est le comportement attendu.
- Les tests n'appellent que des API qui existent aujourd'hui (signatures relues dans les sources) : ils doivent
  compiler sans toucher au code principal.

Lancer la vague seule : `./gradlew :app:testDebugUnitTest --tests "be.heyman.android.jemmapassdemo.red.*"`

Chemins : `…/` = `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/`.

## Tableau

« Attendu aujourd'hui » : **FAIL** = échec déduit de la seule lecture du code de l'application ;
**FAIL (lu + SDK)** = échec si le SDK FHIR se comporte comme le reste du code le suppose ;
**FAIL (calculé)** = échec prévu par un portage Python, jamais exécuté sur le code réel ;
**unknown** = rien ne permet de trancher à la lecture.

| SD | Classe#méthode | Attendu aujourd'hui | Fichiers principaux à modifier (probables) | Notes d'acceptation propres au défaut |
|---|---|---|---|---|
| SD-01 | `Sd01TriageOrderTest#SD-01 UC-SOS-021 two phones that receive deceased and stabilized in a different order show the same status` | FAIL | `…/triage/StatusResolver.kt` (`apply`) | Les règles deux à deux de `shouldOverwrite` sont figées par `StatusResolverTest` : la convergence ne peut pas venir d'un changement de ces règles. `apply` doit garder plus d'un événement par victime (par exemple le dernier « décédé » et le dernier statut vivant) et en déduire le statut de la même façon sur tous les téléphones. |
| SD-01 | `Sd01TriageOrderTest#SD-01 UC-SOS-020 deceased and stabilized created in the same second give the same status on every phone` | FAIL | idem | idem |
| SD-01 | `Sd01TriageOrderTest#SD-01 UC-SOS-021 a third phone that receives a duplicate through a relay agrees with the two others` | FAIL | idem | idem |
| SD-02 | `Sd02NearbyCodeIntegrityTest#SD-02 UC-SOS-012 a vaccine code longer than 10 characters is never shortened` | FAIL | `…/sos/JemmaNearbyEndpointCodec.kt` (`encodeVictimCodes` : `take(10)`) | Le segment reste ≤ 131 octets (`NearbyEndpointBudgetTest`). |
| SD-02 | `Sd02NearbyCodeIntegrityTest#SD-02 UC-SOS-012 an allergy code of 18 digits is whole or absent` | FAIL | idem | idem |
| SD-02 | `Sd02NearbyCodeIntegrityTest#SD-02 UC-SOS-012 a code that holds a dot arrives as one code` | FAIL | `…/sos/JemmaNearbyEndpointCodec.kt` (`encodeVictimCodes`, `decodeVictim` : `split('.')`) | Changer le séparateur change le format radio : un téléphone d'une version antérieure doit continuer à décoder sans planter. |
| SD-03 | `Sd03UnexpectedIdentityValuesTest#SD-03 UC-IMP-013 a birth date that is not ISO still gives a document` | FAIL (lu + SDK) | `…/qr/JemmaFhirBundleBuilder.kt` (`FhirDate.fromString`, l. 131) ; garde dans `…/ui/export/QrViewerFragment.kt` (`buildFhir`) | La valeur illisible est omise, jamais remplacée par une valeur inventée. |
| SD-03 | `Sd03UnexpectedIdentityValuesTest#SD-03 UC-IMP-013 an unknown address use still gives a document` | FAIL (lu + SDK) | `…/qr/JemmaFhirBundleBuilder.kt` (`buildPatientAddresses`) | idem |
| SD-03 | `Sd03UnexpectedIdentityValuesTest#SD-03 UC-QRF-013 an unknown telecom system still gives a document` | FAIL (lu + SDK) | `…/qr/JemmaFhirBundleBuilder.kt` (`buildPatientTelecoms`) | idem |
| SD-03 | `Sd03UnexpectedIdentityValuesTest#SD-03 UC-QRF-013 an unknown telecom use still gives a document` | FAIL (lu + SDK) | idem | idem |
| SD-04 | `Sd04NearbyTruncationNoticeTest#SD-04 UC-SOS-011 a medication list that was cut does not look like a complete list` | FAIL | `…/sos/JemmaNearbyEndpointCodec.kt` (`encodeVictimCodes`) | Le signe « liste incomplète » doit tenir dans les 131 octets. |
| SD-04 | `Sd04NearbyTruncationNoticeTest#SD-04 UC-SOS-011 a complete list is still received as it was sent` | FAIL | `…/sos/JemmaNearbyEndpointCodec.kt` (`decodeVictim`, `Decoded.VictimCodes`) ; affichage côté secouriste (`…/sos/JemmaNearbySosService.kt`, écran radar) | Le résultat décodé d'une liste coupée doit différer de celui d'une liste complète ; une liste complète arrive inchangée. |
| SD-05 | `Sd05NoEmptyPrimitiveTest#SD-05 UC-FHIR-011 an allergy without a code has no empty code in the document` | FAIL (lu + SDK) | `…/qr/JemmaFhirBundleBuilder.kt` (l. 214) | Le libellé reste dans `code.text`. |
| SD-05 | `Sd05NoEmptyPrimitiveTest#SD-05 UC-FHIR-014 a medication without a code has no empty code in the document` | FAIL (lu + SDK) | `…/qr/JemmaFhirBundleBuilder.kt` (l. 250) | idem |
| SD-05 | `Sd05NoEmptyPrimitiveTest#SD-05 UC-QRF-008 an emergency contact with a phone only has no empty name in the document` | FAIL (lu + SDK) | `…/qr/JemmaFhirBundleBuilder.kt` (l. 152) | Le contact et son téléphone restent dans le document. |
| SD-07 | `Sd07ProfileIdLayoutCollisionTest#SD-07 UC-IMP-009 an id ending in fhir is rejected because it would overwrite the document of another profile` | FAIL | `…/profiles/ProfileFiles.kt` (`safeIdOrNull`) | Les identifiants acceptés de `ProfileIdHostileListTest` (`a.b`, `A1.b2-C3_d4`, `demo_haru`…) restent acceptés. |
| SD-07 | `Sd07ProfileIdLayoutCollisionTest#SD-07 UC-MPR-014 the id meta is rejected because the profile list ignores that file` | FAIL | idem | idem |
| SD-08 | `Sd08BloodGroupTextResultTest#SD-08 UC-RES-005 a matching blood group result typed as text keeps its date and laboratory in the document` | FAIL | `…/qr/JemmaFhirBundleBuilder.kt` (`reconcileBloodGroup`) : s'aligner sur `…/ips/IpsBloodGroup.kt` (`reconcile` / `labelOf`) | Toujours un seul résultat 882-1 dans le Bundle, en accord avec `p.bt` (`IpsBundleConsistencyTest`, `IpsBloodGroupSyncTest`). |
| SD-09 | `Sd09DrugDiseaseMatchingTest#SD-09 UC-DDS-010 a term that is only a piece of another word is not a match` | FAIL | `…/kb/DrugDiseaseTerms.kt` (`matches`) | Correction mécanique (frontière de mot). `DrugDiseaseTermsTest` reste vert. |
| SD-09 | `Sd09DrugDiseaseMatchingTest#SD-09 UC-DDS-008 a different disease that shares a word is not a match` | FAIL | `…/kb/DrugDiseaseTerms.kt` (`matches`) | **Décision clinique à confirmer.** `DrugDiseaseTermsTest` exige que « heart failure » corresponde toujours à « Congestive Heart Failure » : la frontière de mot ne suffit pas, il faut une liste de qualificatifs qui désignent une autre maladie. |
| SD-09 | `Sd09DrugDiseaseMatchingTest#SD-09 UC-DDS-013 usual synonyms of a condition are matched` | FAIL | `…/kb/DrugDiseaseTerms.kt` (`VARIANTS`, `candidates`) | **Décision clinique à confirmer** (liste des synonymes). |
| SD-09 | `Sd09DrugDiseaseMatchingTest#SD-09 UC-DDS-016 an irregular plural does not block a match` | FAIL | `…/kb/DrugDiseaseTerms.kt` (`singular`) | — |
| SD-10 | `Sd10AlmostNumericResultUnitTest#SD-10 UC-RES-015 the unit of a value that is not a plain number stays in the compact projection` | FAIL | `…/ips/IpsResult.kt` (`toJEntry`, `fromJEntry`) | Sans nouveau champ `_j` : `u` existe déjà. Le signe « < » / « > » et le nombre restent lisibles. |
| SD-10 | `Sd10AlmostNumericResultUnitTest#SD-10 UC-RES-015 the unit of a value that is not a plain number stays in the document` | FAIL | `…/ips/IpsFhirCodec.kt` (`observationValue`, `fromFhir(Observation)`) ; `…/ui/profile/results/ResultFormBottomSheet.kt` | idem |
| SD-11 | `Sd11HaruFunctionalStatusInTextQrTest#SD-11 UC-I18N-008 the functional status of Haru is in her text QR in French` | FAIL (calculé) | `…/qr/JemmaTextPayloadBuilder.kt`, `…/qr/JemmaTranslations.kt` (voire `…/qr/JemmaPersonasSeeder.kt`) | **Décision à prendre par une personne.** Le plafond de 1 800 octets et l'ordre de retrait sont figés par `QrTextBudgetTest`, `TextQrAllLanguagesTest` et `TextQrProbe.assertPriority` : le correctif doit raccourcir le texte, ou la priorité est revue avec ces tests. |
| SD-11 | `Sd11HaruFunctionalStatusInTextQrTest#SD-11 UC-HUM-023 the functional status of Haru is in her text QR in Japanese` | FAIL (calculé) | idem | idem |
| SD-13 | `Sd13ResolvedProblemImportTest#SD-13 UC-IMP-017 a problem imported as resolved does not become an active problem` | FAIL | `…/ips/IpsProblem.kt` (`IpsProblemStatus.normalize`, `fromJCondition`) ; `…/ips/IpsImmunization.kt` (`IpsNativePillars.fromJEntries`) ; `…/ips/IpsPastProblem.kt` | — |
| SD-13 | `Sd13ResolvedProblemImportTest#SD-13 UC-PRB-017 a problem imported as resolved is not lost either` | FAIL | idem | Le problème est gardé (antécédent, ou problème non actif) : le supprimer n'est pas un correctif. |
| SD-14 | `Sd14CodedValueSystemTest#SD-14 UC-RES-024 a LOINC answer code does not come back from the projection as a SNOMED code` | FAIL | `…/ips/IpsResult.kt` (`toJEntry`, `fromJEntry`) | Deux issues acceptées : le système voyage avec le code, ou la valeur revient en texte sans code. Pas de champ `_j` renommé. |
| SD-15 | `Sd15FamilyNameOnlyDisplayNameTest#SD-15 UC-HUM-005 a profile with a family name only shows that name` | FAIL | `…/qr/JemmaProfileJ.kt` (`displayName`) | — |
| SD-15 | `Sd15FamilyNameOnlyDisplayNameTest#SD-15 UC-HUM-005 a blank given name does not hide the family name` | FAIL | idem | — |
| SD-16 | `Sd16MedicationStartDateTest#SD-16 UC-MED-009 the start date of a treatment is in the document` | FAIL | `…/qr/JemmaFhirBundleBuilder.kt` (`MedicationStatement`, l. 270-304) | Une date `eff` illisible ne doit pas faire échouer le Bundle (voir SD-03). |
| SD-16 | `Sd16MedicationStartDateTest#SD-16 UC-FHIR-005 the start and the end of a finished treatment are in the document` | FAIL | idem | Forme `début/fin` décrite dans `JMedication.effective`. |
| SD-17 | `Sd17SeriesWithoutDoseNumberTest#SD-17 UC-VAC-012 a vaccine series is kept when the dose number is unknown` | FAIL | `…/ips/IpsFhirCodec.kt` (`toFhir(IpsImmunization)`, l. 364-377 ; `fromFhir(Immunization)`) | Le numéro de dose n'est pas inventé (il revient `null`). En FHIR R4, `protocolApplied.doseNumber[x]` est obligatoire : passer par `doseNumberString`. |
| SD-18 | `Sd18ExtremeDecimalTest#SD-18 UC-RES-014 very large values are exact` | unknown | `…/ips/IpsFhirCodec.kt` (l. 581, `quantity` l. 664-672) si échec | À exécuter. S'il passe, SD-18 est clos pour ces valeurs. |
| SD-18 | `Sd18ExtremeDecimalTest#SD-18 UC-RES-014 very small values are exact` | unknown | idem | idem |
| SD-19 | `Sd19SeededContactsTest#SD-19 UC-QRT-005 every seeded emergency contact can be reached by phone or e-mail` | FAIL | `…/qr/JemmaPersonasSeeder.kt` (contacts de Kurodo et de Kamekichi) | Numéros fictifs. Les textes QR des personas restent sous 1 800 octets dans les 25 langues (`TextQrAllLanguagesTest`). |
| SD-19 | `Sd19SeededContactsTest#SD-19 UC-PAT-016 every seeded emergency contact has a relation of the catalogue` | FAIL | idem (`friend` → `FRND`) | `PersonaSeedIntegrityTest` reste vert. |

## Ce qui doit rester vrai après chaque correctif

1. Les 380 tests existants restent verts. Plusieurs d'entre eux bornent le correctif (colonne de droite du
   tableau) : `StatusResolverTest`, `NearbyEndpointBudgetTest`, `DrugDiseaseTermsTest`,
   `ProfileIdHostileListTest`, `QrTextBudgetTest`, `TextQrAllLanguagesTest`, `PersonaSeedIntegrityTest`.
2. Le validateur HL7 donne 0 erreur sur les Bundles des 3 personas (Kurodo, Haru, Kamekichi).
3. Aucun nom de champ `_j` n'est changé.
4. Les fichiers du dossier `red/` ne sont pas modifiés par la personne qui corrige. Si un test paraît faux,
   le signaler au lieu de le changer.
5. Un test de cette vague qui passe sans correctif (possible pour les lignes « lu + SDK », « calculé » et
   « unknown ») est à signaler : le défaut correspondant est alors à reclasser.

## Défauts sans test, et pourquoi

| SD | Raison | API proposée |
|---|---|---|
| SD-06 (profil vide → CLEAN) | Défaut réel, mais aucun point d'entrée existant ne permet un test rouge qui puisse passer : `CrossCheckResult` ne sait pas combien d'entrées du profil ont été comparées, et le seul endroit qui le sait (`KbCrossCheck.checkOneDrugAgainstProfile`) a besoin de la base de connaissances. Le test proposé par le spécialiste contredit en plus deux tests existants (`KbSafetyTest.ucSafeKb02_nothingToCheck_isCleanEvenWithoutKb` fige `pillarStatus(…, itemsToCheck = 0) == CHECKED` ; `ucSafeKb02_kbPresentNoHit_isClean` exige CLEAN pour le même rapport « trois piliers CHECKED »). Il faut d'abord une **décision d'une personne** (nouveau verdict, ou INCOMPLETE). | needs API : `KbCheckReport(…, itemsCompared: Int)` avec `KbSafetyVerdict.NOTHING_TO_COMPARE`, ou une fonction pure `KbSafety.verdict(status: KbCheckStatus, totalHits: Int, itemsCompared: Int): KbSafetyVerdict` |
| SD-12 (ré-import de son propre QR : UDI, série, lot effacés) | Défaut confirmé à la lecture (`ProfilesRepository.resolveNativePillars`, branche `else -> fromJ`). La décision est dans une fonction privée d'une classe qui demande un `Context` Android et l'hydrateur : pas de chemin JVM pur. Le test proposé compare la sortie de `IpsNativePillars.fromJEntries` aux données du Bundle : il resterait rouge après un correctif fait au bon endroit (la fusion avec le Bundle stocké), ou pousserait le correctif dans une fonction qui ne connaît pas le profil stocké. | needs API : `IpsNativePillars.mergeImported(stored: IpsNativePillars, incoming: IpsNativePillars): IpsNativePillars` (fonction pure appelée par `resolveNativePillars` ; garde UDI, série, lot, fabricant quand le code et la date sont les mêmes) |
| SD-20 (divers) | Hors du périmètre de cette vague (SD-01 à SD-19) ; aucun test fourni par le spécialiste. | — |

Aucun des défauts SD-01 à SD-19 n'a été écarté comme « pas un défaut » à la relecture.

## Écarts par rapport aux tests proposés dans `suspected-defects.md`

- SD-01 : les retards sont séparés (1, 20, 30 s d'une part, même seconde d'autre part) et un troisième
  téléphone reçoit les événements en double.
- SD-04 : le test vérifie aussi le résultat décodé, pas seulement le segment émis.
- SD-05 : le test lit le JSON du Bundle (codes vides sous `code.coding`, nom vide du contact) au lieu de
  chercher `: ""` dans tout le texte, pour ne pas dépendre d'une autre chaîne vide sans rapport.
- SD-08 : le test demande que la date et le laboratoire du résultat gardé soient dans le Bundle, et non
  l'égalité stricte des objets, pour laisser le correctif libre de coder la valeur.
- SD-09 : quatre méthodes, pour séparer le correctif mécanique des deux décisions cliniques.
- SD-10 : le test accepte l'unité dans `u` ou à côté de la valeur ; il demande en plus que « < » / « > » et le
  nombre restent lisibles.
- SD-13 : statuts `inactive` et `remission` ajoutés ; une seconde méthode refuse la suppression du problème.
- SD-14 : le test accepte les deux issues décrites par le spécialiste (système conservé, ou valeur en texte).

## Risques de compilation

Non compilé. Points à surveiller au premier lancement :

- `IpsFhirCodec.json.decodeFromString(json)` (SD-17, SD-18) : écrit comme dans `IpsImmunizationCodecTest`,
  sans import supplémentaire.
- `org.json.JSONObject` (SD-05, SD-16) : dépendance de test déjà utilisée par `PersonaSeedIntegrityTest`.
- `android.util.Log` appelé par `JemmaNearbyEndpointCodec` (SD-02, SD-04) : couvert par
  `unitTests.isReturnDefaultValues = true`, comme pour `NearbyEndpointBudgetTest`.
- Noms de méthodes entre accents graves avec espaces et tirets : même forme que les tests existants.
