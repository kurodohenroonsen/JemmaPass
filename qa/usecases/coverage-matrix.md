# Matrice de couverture des 577 micro cas d'usage

Produite par le couloir « jeu de tests » le 2026-10-02, **à la lecture du code** : aucun test n'a été
exécuté ici (pas de SDK Android dans cet environnement). Les 66 nouveaux tests JVM ont été écrits et
vérifiés ligne à ligne contre l'implémentation ; ils restent à lancer une première fois
(`:app:testDebugUnitTest`). Les totaux « après » supposent qu'ils passent.

## Comment lire

| Statut | Sens |
|---|---|
| **couvert** | un test JVM affirme le résultat attendu du cas lui-même |
| **partiel** | un test JVM affirme la règle ou le codec derrière le cas ; l'écran, le dépôt (`ProfilesRepository`) ou la KB ne sont pas exercés : le reste demande l'appareil |
| **JVM à écrire** | testable en JVM sans toucher au code principal, pas encore écrit |
| **JVM à écrire (échouerait)** | même chose, mais le test échouerait aujourd'hui : le comportement attendu n'existe pas (voir `suspected-defects.md` ou le catalogue) |
| **JVM après extraction** | testable en JVM seulement après avoir sorti la logique d'une classe Android (changement du code principal) |
| **appareil** | écran, caméra, KB sur l'appareil, système de fichiers réel |
| **jugement humain** | décision clinique ou produit, relecture par un locuteur, accessibilité |

« Couvert » est strict : une ligne n'est couverte que si un test affirme vraiment ce comportement. Les cas
dont seule la logique est testée sont « partiels », jamais « couverts ». « Avant » = les 314 tests JVM
existants ; « après » = avec les 66 tests ajoutés.

## Totaux

### Par fichier

| | Cas | Couvert avant | Partiel avant | Couvert après | Partiel après | JVM à écrire | dont échouerait | JVM après extraction | Appareil | Jugement humain |
|---|---|---|---|---|---|---|---|---|---|---|
| `01-pillar-editing.md` | 219 | 14 | 77 | **34** | 78 | 9 | 8 | 0 | 92 | 6 |
| `02-safety-and-emergency.md` | 178 | 16 | 40 | **21** | 42 | 19 | 17 | 11 | 78 | 7 |
| `03-data-integrity-and-people.md` | 180 | 25 | 32 | **35** | 34 | 15 | 11 | 0 | 86 | 10 |
| **Total** | 577 | 55 | 149 | **90** | 154 | 43 | 36 | 11 | 256 | 23 |

### Par niveau de risque

| | Cas | Couvert avant | Partiel avant | Couvert après | Partiel après | JVM à écrire | dont échouerait | JVM après extraction | Appareil | Jugement humain |
|---|---|---|---|---|---|---|---|---|---|---|
| risque haut | 209 | 15 | 71 | **19** | 75 | 13 | 11 | 5 | 90 | 7 |
| risque moyen | 249 | 26 | 55 | **33** | 57 | 23 | 19 | 6 | 116 | 14 |
| risque bas | 119 | 14 | 23 | **38** | 22 | 7 | 6 | 0 | 50 | 2 |
| **Total** | 577 | 55 | 149 | **90** | 154 | 43 | 36 | 11 | 256 | 23 |

En bref : **55 couverts + 149 partiels avant** (204 cas touchés par un test JVM), **90 couverts + 154 partiels après** (244 cas touchés). 55 cas changent de statut ; 195 cas reçoivent au moins un nouveau test.

Ce que la JVM ne pourra jamais couvrir seule : tout ce qui passe par `ProfilesRepository` (écriture des deux fichiers,
profil courant, import, semis), les formulaires, les sélecteurs, la KB réelle (SQL), le widget, la diffusion à
proximité, la caméra. Les 256 cas « appareil » et les restes des cas « partiels » sont repris, pour le risque haut,
dans `qa/device/scenarios/`.

## Nouveaux tests JVM

Tous sous `JemmaPassAndroidDemo/app/src/test/java/be/heyman/android/jemmapassdemo/`. Deux fichiers d'aide, sans test :
`testsupport/ProfileFixtures.kt` (hydratation sans KB, miroir de la projection de `writeProfileFiles`) et
`testsupport/TextQrProbe.kt` (lecture d'un QR texte section par section).

| Clé | Classe · méthode |
|---|---|
| | **`qr/PersonaSeedIntegrityTest`** (11 tests) |
| p1 | `UC-STO-029 the three personas exist with the expected number of entries per pillar` |
| p2 | `UC-FHIR-004 every seeded entry survives build then parse` |
| p3 | `UC-STO-023 each persona has exactly one blood group result and it is the profile group` |
| p4 | `UC-STO-020 a rebuild of an unchanged persona gives the same entries and the same ids` |
| p5 | `UC-FHIR-003 a section exists exactly when its pillar has entries and lists each entry once` |
| p6 | `UC-FHIR-001 the document starts with the Composition then the Patient with the right identity` |
| p7 | `UC-QRF-006 allergies and medications of the personas are in the document, in order` |
| p8 | `UC-IMP-002 the _j file of each persona is read back identical` |
| p9 | `UC-IMP-023 the FHIR QR channel rebuilt from the projection keeps every entry the QR carries` |
| p10 | `UC-IMP-007 importing the same persona payload twice gives the same resources` |
| p11 | `UC-QRF-017 pruning for the compact QR removes empty fields only` |
| | **`ips/PillarBoundaryRoundTripTest`** (12 tests) |
| b1 | `UC-STO-005 boundary entries of the 8 native pillars survive the Bundle unchanged` |
| b2 | `UC-FHIR-018 with every pillar full each entry is listed by its own section and by no other` |
| b3 | `UC-IMP-001 the projection of boundary entries re-exports identical after an import` |
| b4 | `UC-IMP-016 unknown codes and unknown statuses from a payload never crash and fall back to the pillar default` |
| b5 | `UC-HUM-018 blank strings are never stored as empty values` |
| b6 | `UC-FHIR-016 a date is exported at the precision it was given, never shifted` |
| b7 | `UC-FHIR-015 a date that does not exist is exported as unknown, never as a neighbouring day` |
| b8 | `UC-RES-014 zero, negative and comma decimals keep their exact value` |
| b9 | `UC-GRO-010 a count of zero is an observation and an empty count is none` |
| b12 | `UC-ANT-010 an end date is compared with the start date at their common precision` |
| b10 | `UC-PAT-010 identity, allergies and medications with non latin and very long text are read back identical from the _j file` |
| b11 | `UC-I18N-019 the document carries the identity, every allergy and every medication as typed` |
| | **`qr/TextQrAllLanguagesTest`** (10 tests) |
| t1 | `UC-I18N-004 there are 25 languages and each has its own header` |
| t2 | `UC-I18N-005 every label exists and is not blank in each of the 25 languages` |
| t3 | `UC-QRT-004 no language is missing a key or carries an extra one` |
| t4 | `UC-QRT-010 every persona fits one QR frame in each of the 25 languages` |
| t5 | `UC-HUM-023 identity, allergies, medications, conditions and contacts are always printed` |
| t6 | `UC-QRT-008 every entry of every pillar is printed or announced as left out` |
| t7 | `UC-QRT-003 no null literal, no KB placeholder and no empty title in any language` |
| t8 | `UC-QRT-002 the native pillar sections carry the localised title of the language` |
| t9 | `UC-QRT-017 dates are printed as stored and the most recent entry comes first` |
| t10 | `UC-QRT-007 a profile with a name only still gives a valid payload in every language` |
| | **`kb/KbSafetyTruthTableTest`** (10 tests) |
| k10 | `ucSafeKb10_verdictTable_statusTimesHits` |
| k11 | `ucSafeKb11_reportTable_overallIsTheWorstPillar` |
| k12 | `ucSafeKb12_profileTable_neverCleanUnlessEveryPillarIsChecked` |
| k13 | `ucSafeKb13_profileTable_anAlertOnAnyPillarIsAlwaysAnAlert` |
| k14 | `ucSafeKb14_crossCheckTable_resolvedCandidateWithoutHit` |
| k15 | `ucSafeKb15_crossCheckTable_unknownCandidateIsNeverCheckedWhateverThePillarsSay` |
| k16 | `ucSafeKb16_crossCheckTable_aHitIsAlwaysAnAlert` |
| k17 | `ucSafeKb17_assistantFields_table` |
| k18 | `ucSafeKb18_pillarStatusTable_withSomethingToCheck` |
| k19 | `ucSafeKb19_unverifiedItemsTable_neverMoreThanTheEntriesAndNeverNegative` |
| | **`profiles/ProfileIdHostileListTest`** (5 tests) |
| h1 | `UC-IMP-009 every hostile id of the list is rejected` |
| h2 | `UC-IMP-009 a hostile id stays rejected with surrounding blanks` |
| h3 | `UC-IMP-009 plain ids are kept exactly and the limit is 64 characters` |
| h4 | `UC-IMP-009 an accepted id can only name a file directly inside the profiles folder` |
| h5 | `UC-ROB-001 a profile written under a boundary id is whole and alone in its folder` |
| | **`qr/RandomProfileInvariantsTest`** (5 tests) |
| r1 | `UC-STO-005 random profiles - the Bundle is a valid document that reads back to the same entries` |
| r2 | `UC-IMP-001 random profiles - the _j file reads back identical and an import re-exports the same _j` |
| r3 | `UC-QRF-002 random profiles - the compact payload survives pruning and the multi-frame split` |
| r4 | `UC-QRT-008 random profiles - the text QR fits one frame and never loses an entry in silence` |
| r5 | `UC-IMP-007 the generator itself is deterministic` |
| | **`triage/StatusResolverTest`** (5 tests) |
| s1 | `UC-SOS-020 a deceased event older than the current status is rejected, whatever the current status` |
| s2 | `UC-SOS-020 leaving deceased needs more than the grace window or an explicit cancel` |
| s3 | `UC-SOS-021 between two living statuses the latest event wins and a tie is decided the same way everywhere` |
| s4 | `UC-SOS-020 the resolver keeps one status per victim and applies the same rules` |
| s5 | `UC-SOS-020 wire codes are parsed strictly and an unknown code is dropped` |
| | **`sos/NearbyEndpointBudgetTest`** (6 tests) |
| n1 | `UC-SOS-010 a short list of codes reaches the rescuer whole and in order` |
| n2 | `UC-SOS-011 fifty medication codes never overflow a segment and the first ones are kept in order` |
| n3 | `UC-SOS-011 an empty section is still announced` |
| n4 | `UC-SOS-011 random code lists always fit and what is received is what was sent, never something else` |
| n5 | `UC-SOS-014 a non latin name is cut on a character boundary and the header fits` |
| n6 | `UC-SOS-015 anything that is not a JEMMA segment is ignored without a crash` |
| | **`qr/PatientBirthDateBundleTest`** (2 tests) |
| d1 | `UC-HUM-002 a birth date known to the year or to the month is exported as given` |
| d2 | `UC-HUM-001 an unknown birth date gives a document without birthDate, not an invented one` |

Total : 66 méthodes de test dans 9 classes.

## 01 — Édition des piliers (`01-pillar-editing.md`)

| ID | Risque | Avant | Après | Tests JVM existants | Nouveaux tests JVM | Reste / remarque |
|---|---|---|---|---|---|---|
| UC-PAT-001 | haut | partiel | **partiel** | PatientFormMergeTest#UC-PAT-001 ; IpsBloodGroupTest#bundleHasAnIdentifierNoEmptyNamePartsAndNoHomeMadeExtension | PersonaSeedIntegrityTest#p6 · PillarBoundaryRoundTripTest#b11 | formulaire et toast : appareil |
| UC-PAT-002 | haut | partiel | **partiel** | IpsBloodGroupTest#syncKeepsExactlyOneBloodGroupResult ; IpsBloodGroupSyncTest#UC-BLOOD-02 | PersonaSeedIntegrityTest#p3 | écran : appareil |
| UC-PAT-003 | haut | partiel | **partiel** | IpsBloodGroupTest#syncKeepsExactlyOneBloodGroupResult ; IpsBundleConsistencyTest#UC-FHIR-027 sans groupe sanguin | RandomProfileInvariantsTest#r1 | écran : appareil |
| UC-PAT-004 | bas | — | **appareil** |  |  | la validation est dans PatientEditFragment |
| UC-PAT-005 | moyen | partiel | **partiel** | FormEditGuardsTest#UC-PAT-005 ; PatientFormMergeTest#UC-PAT-005 | PatientBirthDateBundleTest#d1 | d1 dépend du SDK FHIR (signalé) |
| UC-PAT-006 | moyen | partiel | **partiel** | FormEditGuardsTest#UC-PAT-006 (2 tests) |  | calendrier : appareil |
| UC-PAT-007 | moyen | partiel | **partiel** | FormEditGuardsTest#UC-PAT-007 |  | branchement de la garde dans l'écran : appareil (README, reste n° 11) |
| UC-PAT-008 | bas | — | **appareil** |  |  |  |
| UC-PAT-009 | moyen | — | **appareil** |  |  | mort du processus : aucune action dans le vocabulaire appareil (am kill) |
| UC-PAT-010 | moyen | partiel | **partiel** | JemmaSosChunkCodecTest#header chunk round-trips | PillarBoundaryRoundTripTest#b10 · PillarBoundaryRoundTripTest#b11 · RandomProfileInvariantsTest#r4 | stockage, Bundle et plafond du QR vérifiés ; affichage : appareil |
| UC-PAT-011 | moyen | partiel | **partiel** | PatientFormMergeTest#UC-PAT-011 (6 tests) | PillarBoundaryRoundTripTest#b11 | écran : appareil |
| UC-PAT-012 | bas | — | **appareil** |  |  |  |
| UC-PAT-013 | haut | — | **appareil** |  |  | chemin MANUAL_EDIT du dépôt ; X avec un ProfileStore extrait de ProfilesRepository |
| UC-PAT-014 | moyen | — | **appareil** |  |  | X (ProfilesRepository.loadProfile) |
| UC-PAT-015 | moyen | — | **appareil** |  |  |  |
| UC-PAT-016 | haut | — | **appareil** |  |  |  |
| UC-PAT-017 | haut | — | **JVM à écrire** |  |  | PhoneNumberHelper.validate est pur ; l'enregistrement « quand même » est dans ContactFormBottomSheet (appareil) |
| UC-PAT-018 | bas | partiel | **partiel** | IpsVaccineCatalogTest#searchAliasesLetAFrenchUserTypeTheInternationalName |  | sélecteur : appareil |
| UC-PAT-019 | moyen | — | **appareil** |  |  |  |
| UC-ALG-001 | haut | partiel | **partiel** | AllergyFormMergeTest#UC-ALG-001 | PersonaSeedIntegrityTest#p7 · PillarBoundaryRoundTripTest#b11 | formulaire : appareil |
| UC-ALG-002 | haut | — | **appareil** |  |  | aussi une décision produit (allergène en texte libre) |
| UC-ALG-003 | moyen | — | **appareil** |  |  | T4 |
| UC-ALG-004 | haut | partiel | **partiel** | AllergyFormMergeTest#UC-ALG-004 (6 tests) | PillarBoundaryRoundTripTest#b10 | écran : appareil |
| UC-ALG-005 | moyen | — | **appareil** |  |  | côté Bundle : SD-05 |
| UC-ALG-006 | moyen | — | **appareil** |  |  |  |
| UC-ALG-007 | haut | — | **JVM à écrire (échouerait)** |  |  | section obligatoire 48765-2 absente quand la liste est vide (catalogue 01, trou n° 10) |
| UC-ALG-008 | bas | — | **appareil** |  |  |  |
| UC-ALG-009 | moyen | partiel | **partiel** | FormEditGuardsTest#UC-ALG-009 |  | branchement de la garde : appareil |
| UC-ALG-010 | moyen | — | **appareil** |  |  |  |
| UC-ALG-011 | bas | partiel | **partiel** | FormEditGuardsTest#UC-ALG-011 | PillarBoundaryRoundTripTest#b10 | calendrier : appareil |
| UC-ALG-012 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b10 · PillarBoundaryRoundTripTest#b11 · RandomProfileInvariantsTest#r4 | ui.py ne sait pas saisir de caractères non ASCII |
| UC-ALG-013 | bas | — | **partiel** |  | PillarBoundaryRoundTripTest#b11 | deux entrées conservées ; l'avertissement de doublon attendu n'existe pas (H) |
| UC-ALG-014 | haut | — | **appareil** |  |  | demande un appareil sans KB (aucune action pour cela) |
| UC-ALG-015 | moyen | partiel | **partiel** | IpsVaccineCatalogTest#searchAliasesLetAFrenchUserTypeTheInternationalName |  | sélecteur : appareil |
| UC-ALG-016 | bas | — | **appareil** |  |  |  |
| UC-ALG-017 | haut | partiel | **partiel** | AllergyFormSafetyLogicTest#UC-SAFE-UI-24..28 |  | la correspondance KB elle-même : appareil |
| UC-ALG-018 | haut | partiel | **partiel** | FormCrossCheckOutcomeTest#ucSafeScan21_exception ; AllergyFormSafetyLogicTest#UC-SAFE-UI-25 | KbSafetyTruthTableTest#k14 | dialogue : appareil |
| UC-ALG-019 | haut | — | **appareil** |  |  | T4, T13 |
| UC-ALG-020 | moyen | — | **appareil** |  |  | X (édition par index dans AllergiesEditFragment) |
| UC-MED-001 | haut | partiel | **partiel** | MedicationFormLogicTest#UC-MED-001 | PersonaSeedIntegrityTest#p7 | formulaire : appareil |
| UC-MED-002 | haut | partiel | **partiel** | MedicationFormLogicTest#UC-MED-002 |  | décision produit (texte libre) : H |
| UC-MED-003 | haut | partiel | **partiel** | MedicationFormLogicTest#UC-MED-003 (2 tests) |  | écran : appareil |
| UC-MED-004 | haut | — | **appareil** |  |  |  |
| UC-MED-005 | moyen | — | **JVM à écrire (échouerait)** |  |  | section obligatoire 10160-0 absente quand la liste est vide |
| UC-MED-006 | bas | — | **appareil** |  |  |  |
| UC-MED-007 | moyen | partiel | **partiel** | FormEditGuardsTest#UC-MED-007 |  | branchement de la garde : appareil |
| UC-MED-008 | moyen | — | **appareil** |  |  |  |
| UC-MED-009 | bas | — | **partiel** |  | PillarBoundaryRoundTripTest#b10 | `eff` survit au fichier `_j` ; il n'est jamais exporté dans le Bundle (SD-16) |
| UC-MED-010 | bas | partiel | **partiel** | FormEditGuardsTest#UC-MED-010 |  | calendrier : appareil |
| UC-MED-011 | moyen | — | **appareil** |  |  | X (contrôle de la dose dans MedicationFormBottomSheet) |
| UC-MED-012 | haut | partiel | **partiel** | FhirMedicationBundleTest#UC-FHIR-024 virgule décimale | PillarBoundaryRoundTripTest#b11 | `v` garde la virgule dans `_j` (choix du correctif) |
| UC-MED-013 | moyen | partiel | **partiel** | FhirMedicationBundleTest#UC-FHIR-024 illisible ou ambiguë | RandomProfileInvariantsTest#r1 | acceptation de 99999 / NaN par le formulaire : appareil |
| UC-MED-014 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b10 · PillarBoundaryRoundTripTest#b11 · RandomProfileInvariantsTest#r4 |  |
| UC-MED-015 | moyen | — | **partiel** |  | PillarBoundaryRoundTripTest#b11 | deux entrées conservées ; pas d'avertissement de doublon (H) |
| UC-MED-016 | haut | partiel | **partiel** | MedicationFormLogicTest#UC-MED-016 |  | demande un appareil sans KB |
| UC-MED-017 | moyen | — | **appareil** |  |  |  |
| UC-MED-018 | bas | — | **appareil** |  |  |  |
| UC-MED-019 | haut | partiel | **partiel** | MedicationFormSafetyVerdictTest#UC-MED-ROUTE-10..13 ; FormCrossCheckOutcomeTest#ucSafeScan24 | KbSafetyTruthTableTest#k16 | correspondance KB et dialogue : appareil |
| UC-MED-020 | haut | partiel | **partiel** | MedicationFormSafetyVerdictTest#UC-MED-ROUTE-10..13 | KbSafetyTruthTableTest#k16 | correspondance KB et dialogue : appareil |
| UC-MED-021 | haut | couvert | **couvert** | MedicationRouteTest#UC-MED-ROUTE-01..04 ; MedicationRouteEnumTest#UC-MED-ROUTE-20..22 ; FhirMedicationBundleTest#UC-FHIR-026 | PillarBoundaryRoundTripTest#b11 |  |
| UC-MED-022 | haut | — | **appareil** |  |  | chemin de T4 |
| UC-MED-023 | moyen | partiel | **partiel** | MedicationFormLogicTest#UC-MED-023 (2 tests) |  | index périmé : appareil |
| UC-PRB-001 | haut | partiel | **partiel** | IpsProblemCodecTest#jsonCarriesIpsEssentials ; #fullRoundTrip | PersonaSeedIntegrityTest#p2 · PillarBoundaryRoundTripTest#b1 | T16.3 |
| UC-PRB-002 | moyen | partiel | **partiel** | IpsProblemCodecTest#fullRoundTrip | PillarBoundaryRoundTripTest#b1 |  |
| UC-PRB-003 | moyen | partiel | **partiel** | IpsProblemCodecTest#statusNormalization | PillarBoundaryRoundTripTest#b1 | T16.4 |
| UC-PRB-004 | moyen | — | **appareil** |  |  |  |
| UC-PRB-005 | moyen | — | **JVM à écrire (échouerait)** |  |  | section obligatoire 11450-4 absente quand la liste est vide |
| UC-PRB-006 | bas | — | **appareil** |  |  |  |
| UC-PRB-007 | moyen | — | **appareil** |  |  |  |
| UC-PRB-008 | moyen | — | **appareil** |  |  |  |
| UC-PRB-009 | bas | — | **appareil** |  |  |  |
| UC-PRB-010 | bas | partiel | **couvert** | IpsProblemCodecTest#fullRoundTrip (ASCII seulement) | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b3 | lisibilité de la carte : appareil |
| UC-PRB-011 | moyen | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b3 | deux Conditions aux identifiants distincts |
| UC-PRB-012 | moyen | — | **appareil** |  |  |  |
| UC-PRB-013 | moyen | — | **appareil** |  |  |  |
| UC-PRB-014 | bas | — | **appareil** |  |  | course dans le formulaire |
| UC-PRB-015 | haut | partiel | **partiel** | IpsProblemCodecTest#problemsAndPastProblemsLiveInTheirOwnSections ; IpsFunctionalCodecTest#threeConditionPillarsNeverMix | PersonaSeedIntegrityTest#p5 · PillarBoundaryRoundTripTest#b2 | T16.5 |
| UC-PRB-016 | moyen | couvert | **couvert** | IpsProblemCodecTest#projectionKeepsTheLegacyShape ; #legacyProblemListConditionsAreReadWithAStableId | PillarBoundaryRoundTripTest#b4 |  |
| UC-PRB-017 | moyen | — | **JVM à écrire (échouerait)** | IpsProblemCodecTest#statusNormalization fige le comportement inverse |  | SD-13 : un problème résolu revient actif |
| UC-PRB-018 | haut | partiel | **partiel** | DrugDiseaseTermsTest (5 tests) |  | T18, T19 |
| UC-ANT-001 | moyen | partiel | **partiel** | IpsPastProblemCodecTest#fullRoundTrip ; #projectionContract | PersonaSeedIntegrityTest#p2 | T15.2 |
| UC-ANT-002 | moyen | partiel | **partiel** | IpsPastProblemCodecTest#freeTextUndatedRemission | PillarBoundaryRoundTripTest#b1 | T15.3 |
| UC-ANT-003 | bas | partiel | **partiel** | IpsPastProblemCodecTest#severityUsesTheIpsLoincAnswers | PillarBoundaryRoundTripTest#b1 | T15.5 |
| UC-ANT-004 | moyen | — | **appareil** |  |  |  |
| UC-ANT-005 | bas | couvert | **couvert** | IpsPastProblemCodecTest#noSectionWhenThePillarIsEmpty | PersonaSeedIntegrityTest#p5 · RandomProfileInvariantsTest#r1 |  |
| UC-ANT-006 | bas | — | **appareil** |  |  |  |
| UC-ANT-007 | moyen | — | **appareil** |  |  |  |
| UC-ANT-008 | moyen | — | **appareil** |  |  |  |
| UC-ANT-009 | moyen | partiel | **partiel** | IpsPastProblemCodecTest#abatementBeforeOnsetIsRejected | PillarBoundaryRoundTripTest#b12 | message du formulaire : appareil |
| UC-ANT-010 | bas | partiel | **couvert** | IpsPastProblemCodecTest#abatementBeforeOnsetIsRejected | PillarBoundaryRoundTripTest#b12 · PillarBoundaryRoundTripTest#b1 |  |
| UC-ANT-011 | bas | — | **appareil** |  |  |  |
| UC-ANT-012 | bas | partiel | **couvert** | IpsPastProblemCodecTest#freeTextUndatedRemission | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b3 |  |
| UC-ANT-013 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b3 |  |
| UC-ANT-014 | moyen | — | **appareil** |  |  |  |
| UC-ANT-015 | bas | — | **appareil** |  |  |  |
| UC-ANT-016 | bas | partiel | **partiel** | PastProblemsTextQrTest#localisedLabelsWinOverTheEnglishTerm | TextQrAllLanguagesTest#t8 | libellés de la KB : appareil |
| UC-ANT-017 | moyen | couvert | **couvert** | IpsPastProblemCodecTest#legacyJArrayRebuild ; #projectionIsDeterministic ; #projectionContract | PillarBoundaryRoundTripTest#b3 |  |
| UC-ANT-018 | moyen | — | **jugement humain** |  |  | pas d'action « déplacer vers les antécédents » : décision produit |
| UC-VAC-001 | moyen | partiel | **partiel** | IpsImmunizationCodecTest#fullEntrySurvivesFhirRoundTrip ; #fhirJsonCarriesTheIpsEssentials | PersonaSeedIntegrityTest#p2 | T2 |
| UC-VAC-002 | moyen | partiel | **partiel** | IpsImmunizationCodecTest#freeTextVaccineWithoutCodeRoundTrips ; #unknownDateBecomesOccurrenceStringAndComesBackNull | PillarBoundaryRoundTripTest#b1 | T8.1 |
| UC-VAC-003 | bas | — | **appareil** |  |  |  |
| UC-VAC-004 | moyen | — | **appareil** |  |  |  |
| UC-VAC-005 | bas | couvert | **couvert** | IpsImmunizationCodecTest#bundleWithoutNativePillarsHasNoImmunizationSection | PersonaSeedIntegrityTest#p5 |  |
| UC-VAC-006 | bas | — | **appareil** |  |  |  |
| UC-VAC-007 | moyen | — | **appareil** |  |  |  |
| UC-VAC-008 | moyen | — | **appareil** |  |  |  |
| UC-VAC-009 | moyen | partiel | **partiel** | IpsImmunizationCodecTest#partialDatesAreKeptAsIs | PillarBoundaryRoundTripTest#b6 | le formulaire ne permet pas de saisir une année seule |
| UC-VAC-010 | moyen | — | **appareil** |  |  |  |
| UC-VAC-011 | bas | — | **partiel** |  | PillarBoundaryRoundTripTest#b4 | codec : 0 n'est pas un numéro de dose ; messages du formulaire : appareil |
| UC-VAC-012 | bas | — | **JVM à écrire (échouerait)** |  |  | SD-17 : la série est perdue sans numéro de dose |
| UC-VAC-013 | haut | partiel | **partiel** | IpsImmunizationCodecTest#statusIsNormalized | PillarBoundaryRoundTripTest#b1 | le libellé QR reste « (not-done) » brut (J!) |
| UC-VAC-014 | moyen | partiel | **partiel** | IpsImmunizationCodecTest#statusIsNormalized | PillarBoundaryRoundTripTest#b1 | exclusion du QR secouriste : H |
| UC-VAC-015 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 |  |
| UC-VAC-016 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b3 |  |
| UC-VAC-017 | bas | partiel | **partiel** | IpsVaccineCatalogTest (5 tests) |  | sélecteur sans KB : appareil |
| UC-VAC-018 | bas | partiel | **partiel** | IpsVaccineCatalogTest#searchAliasesLetAFrenchUserTypeTheInternationalName |  | sélecteur : appareil |
| UC-VAC-019 | bas | — | **appareil** |  |  |  |
| UC-VAC-020 | haut | — | **appareil** |  |  | T4, T13 |
| UC-VAC-021 | moyen | couvert | **couvert** | IpsImmunizationProjectionTest#rebuildIsDeterministic ; #rebuildFromProjectionPreservesWhatTheQrCarries ; #legacyPayloadWithoutTheNewKeysStillParses | PersonaSeedIntegrityTest#p9 · PillarBoundaryRoundTripTest#b3 |  |
| UC-PRO-001 | moyen | partiel | **partiel** | IpsProcedureDeviceCodecTest#procedureFullRoundTrip ; #procedureJsonCarriesIpsEssentials | PersonaSeedIntegrityTest#p2 | T10 |
| UC-PRO-002 | moyen | — | **appareil** |  |  |  |
| UC-PRO-003 | moyen | partiel | **partiel** | IpsProcedureDeviceCodecTest#procedureUnknownDateAndFreeTextAndStatuses | PillarBoundaryRoundTripTest#b1 | T12 |
| UC-PRO-004 | moyen | partiel | **partiel** | IpsProcedureDeviceCodecTest#procedureUnknownDateAndFreeTextAndStatuses | PillarBoundaryRoundTripTest#b1 | aller-retour des six statuts ; libellé QR brut (J!) |
| UC-PRO-005 | moyen | — | **appareil** |  |  |  |
| UC-PRO-006 | bas | — | **couvert** |  | PersonaSeedIntegrityTest#p5 · RandomProfileInvariantsTest#r1 |  |
| UC-PRO-007 | bas | — | **appareil** |  |  |  |
| UC-PRO-008 | moyen | — | **appareil** |  |  |  |
| UC-PRO-009 | moyen | — | **appareil** |  |  |  |
| UC-PRO-010 | moyen | partiel | **partiel** | IpsProcedureDeviceCodecTest#procedureUnknownDateAndFreeTextAndStatuses | PersonaSeedIntegrityTest#p2 · PillarBoundaryRoundTripTest#b6 | édition de l'entrée de 1975 : appareil |
| UC-PRO-011 | bas | — | **appareil** |  |  |  |
| UC-PRO-012 | bas | partiel | **couvert** | IpsProcedureDeviceCodecTest (accents français) | PillarBoundaryRoundTripTest#b1 |  |
| UC-PRO-013 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b3 |  |
| UC-PRO-014 | moyen | — | **appareil** |  |  |  |
| UC-PRO-015 | bas | — | **appareil** |  |  |  |
| UC-PRO-016 | bas | partiel | **partiel** | IpsProcedureDeviceCatalogTest#procedureCodesAreUniqueSnomedIdentifiersWithThreeLabels | TextQrAllLanguagesTest#t8 | T13 |
| UC-PRO-017 | haut | — | **appareil** |  |  |  |
| UC-PRO-018 | moyen | couvert | **couvert** | IpsProcedureDeviceCodecTest#legacyJArraysRebuildDeterministically ; #procedureProjectionContract | PillarBoundaryRoundTripTest#b3 |  |
| UC-DEV-001 | haut | partiel | **partiel** | IpsProcedureDeviceCodecTest#deviceFullRoundTrip ; #deviceJsonCarriesIpsEssentials | PersonaSeedIntegrityTest#p2 | T11 |
| UC-DEV-002 | moyen | partiel | **partiel** | IpsProcedureDeviceCodecTest#deviceStatusesAndFreeText | PillarBoundaryRoundTripTest#b1 | T12 |
| UC-DEV-003 | haut | partiel | **partiel** | IpsProcedureDeviceCodecTest#deviceStatusesAndFreeText | PillarBoundaryRoundTripTest#b1 | T11 |
| UC-DEV-004 | moyen | — | **appareil** |  |  |  |
| UC-DEV-005 | bas | — | **couvert** |  | PersonaSeedIntegrityTest#p5 · RandomProfileInvariantsTest#r1 |  |
| UC-DEV-006 | bas | — | **appareil** |  |  |  |
| UC-DEV-007 | moyen | — | **appareil** |  |  |  |
| UC-DEV-008 | moyen | — | **appareil** |  |  |  |
| UC-DEV-009 | moyen | partiel | **partiel** | IpsProcedureDeviceCatalogTest#udiPlausibilityRejectsTypos |  | erreur en ligne : appareil |
| UC-DEV-010 | moyen | partiel | **partiel** | IpsProcedureDeviceCatalogTest#udiPlausibilityAcceptsTheIssuingAgencyFormats |  | T12 |
| UC-DEV-011 | moyen | — | **jugement humain** |  |  | contrôle UDI indulgent par choix : une clé fausse doit-elle avertir ? |
| UC-DEV-012 | bas | — | **partiel** |  | PersonaSeedIntegrityTest#p2 · PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b6 | édition de l'entrée 2019-06 : appareil |
| UC-DEV-013 | bas | — | **appareil** |  |  |  |
| UC-DEV-014 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 |  |
| UC-DEV-015 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b3 |  |
| UC-DEV-016 | moyen | — | **appareil** |  |  |  |
| UC-DEV-017 | bas | — | **appareil** |  |  |  |
| UC-DEV-018 | bas | partiel | **partiel** | IpsProcedureDeviceCatalogTest#deviceCodesAreUniqueSnomedIdentifiersWithThreeLabels | TextQrAllLanguagesTest#t8 | T13 |
| UC-DEV-019 | haut | — | **appareil** |  |  |  |
| UC-DEV-020 | moyen | couvert | **couvert** | IpsProcedureDeviceCodecTest#deviceProjectionContract ; #legacyJArraysRebuildDeterministically | PillarBoundaryRoundTripTest#b3 |  |
| UC-DEV-021 | haut | — | **JVM à écrire (échouerait)** |  |  | SD-12 : UDI, série, lot perdus au ré-import |
| UC-RES-001 | haut | partiel | **partiel** | IpsResultCodecTest#integralAndCommaDecimalsSurviveTheDoubleJsonEncoding ; #numericJsonCarriesIpsEssentials | PersonaSeedIntegrityTest#p2 · PillarBoundaryRoundTripTest#b8 | T14.3 |
| UC-RES-002 | moyen | partiel | **partiel** | IpsResultCodecTest#imagingWithoutDayPreciseDateUsesTheGenericResultsProfile | PersonaSeedIntegrityTest#p2 · PillarBoundaryRoundTripTest#b1 | T14.4 |
| UC-RES-003 | haut | partiel | **partiel** | IpsBloodGroupTest#syncKeepsExactlyOneBloodGroupResult ; ResultBloodGroupGuardTest#UC-BLOOD-13..16 | PersonaSeedIntegrityTest#p3 | T14.6 |
| UC-RES-004 | haut | — | **appareil** |  |  |  |
| UC-RES-005 | haut | partiel | **partiel** | ResultBloodGroupGuardTest#UC-BLOOD-13..16 ; BloodGroupConflictTest#UC-BLOOD-10..12 ; IpsBloodGroupSyncTest#UC-BLOOD-03 |  | SD-08 : un résultat concordant saisi en texte est écarté par le constructeur du Bundle |
| UC-RES-006 | haut | couvert | **couvert** | IpsBloodGroupSyncTest#UC-BLOOD-01 ; #UC-BLOOD-02 ; IpsBundleConsistencyTest#UC-FHIR-027 copie importée du groupe sanguin | PersonaSeedIntegrityTest#p9 |  |
| UC-RES-007 | moyen | — | **appareil** |  |  |  |
| UC-RES-008 | bas | partiel | **couvert** | IpsBundleConsistencyTest#UC-FHIR-027 sans groupe sanguin | RandomProfileInvariantsTest#r1 | état vide à l'écran : appareil |
| UC-RES-009 | bas | — | **appareil** |  |  |  |
| UC-RES-010 | moyen | — | **appareil** |  |  |  |
| UC-RES-011 | moyen | — | **appareil** |  |  |  |
| UC-RES-012 | moyen | — | **appareil** |  |  |  |
| UC-RES-013 | moyen | — | **appareil** |  |  | X (contrôle des bornes dans ResultFormBottomSheet) |
| UC-RES-014 | moyen | partiel | **partiel** | IpsResultCodecTest#decimalHelpers | PillarBoundaryRoundTripTest#b8 · PillarBoundaryRoundTripTest#b1 | 0, négatif, virgule : couverts ; 25 chiffres : non affirmé (SD-18, à exécuter) |
| UC-RES-015 | haut | — | **JVM à écrire (échouerait)** | IpsResultCodecTest#nonNumericTypedValueFallsBackToValueString (sans unité) |  | SD-10 : l'unité d'une valeur presque numérique est perdue |
| UC-RES-016 | moyen | — | **jugement humain** |  |  | cohérence interprétation / plage : règle clinique à définir |
| UC-RES-017 | moyen | — | **appareil** |  |  |  |
| UC-RES-018 | bas | couvert | **couvert** | IpsResultCodecTest#undatedAndUnattributedResultsStillMeetTheIpsCardinalities ; #imagingWithoutDayPreciseDateUsesTheGenericResultsProfile | PillarBoundaryRoundTripTest#b6 · PillarBoundaryRoundTripTest#b1 |  |
| UC-RES-019 | bas | — | **appareil** |  |  |  |
| UC-RES-020 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 |  |
| UC-RES-021 | bas | partiel | **partiel** | IpsResultCatalogTest#aliasesLetAFrenchUserTypeTheUsualShorthand |  | sélecteur : appareil |
| UC-RES-022 | bas | partiel | **partiel** | IpsResultCatalogTest#aliasesLetAFrenchUserTypeTheUsualShorthand | TextQrAllLanguagesTest#t8 | T14.8 |
| UC-RES-023 | haut | partiel | **partiel** | IpsPregnancyCodecTest#bundleSectionAndNoLeakIntoResults ; IpsResultCodecTest#otherObservationKindsAreLeftToTheirOwnPillars | PillarBoundaryRoundTripTest#b2 · RandomProfileInvariantsTest#r1 | T14.7 |
| UC-RES-024 | moyen | partiel | **partiel** | IpsResultCodecTest#projectionRebuildsTheThreeValueKinds | PillarBoundaryRoundTripTest#b3 | SD-14 : le système de code d'une valeur codée est perdu en passant par `_j` |
| UC-GRO-001 | haut | partiel | **partiel** | IpsPregnancyCodecTest#roundTrips ; #jsonCarriesTheIpsProfilesAndValueTypes | PillarBoundaryRoundTripTest#b1 | T21.2 |
| UC-GRO-002 | moyen | partiel | **partiel** | IpsPregnancyCodecTest#projection | PersonaSeedIntegrityTest#p2 | T21.1 |
| UC-GRO-003 | haut | — | **appareil** |  |  |  |
| UC-GRO-004 | moyen | — | **appareil** |  |  |  |
| UC-GRO-005 | moyen | — | **appareil** |  |  | X (règle dans PregnancyEditFragment) |
| UC-GRO-006 | moyen | — | **appareil** |  |  | X (règle dans PregnancyEditFragment) |
| UC-GRO-007 | moyen | — | **jugement humain** |  |  | quelles règles de cohérence obstétricale veut-on |
| UC-GRO-008 | moyen | — | **appareil** |  |  |  |
| UC-GRO-009 | haut | — | **jugement humain** |  |  | quand un statut « enceinte » est-il périmé : règle clinique à définir |
| UC-GRO-010 | bas | partiel | **partiel** | IpsPregnancyCodecTest#roundTrips (count = 0) | PillarBoundaryRoundTripTest#b9 · PillarBoundaryRoundTripTest#b1 | limite à deux chiffres du champ : appareil |
| UC-GRO-011 | moyen | — | **appareil** |  |  |  |
| UC-GRO-012 | bas | — | **appareil** |  |  |  |
| UC-GRO-013 | moyen | — | **appareil** |  |  |  |
| UC-GRO-014 | moyen | — | **jugement humain** |  |  | grossesse sur un profil masculin ou âgé : avertir ou non |
| UC-GRO-015 | bas | — | **appareil** |  |  |  |
| UC-GRO-016 | bas | — | **partiel** |  | PillarBoundaryRoundTripTest#b1 | le codec garde notes et statuts multiples ; l'écran reconstruit sans eux (appareil) |
| UC-GRO-017 | bas | partiel | **partiel** | IpsPregnancyCatalogTest#formatLocalisesStatusOutcomeAndEdd ; PregnancyTextQrTest#frenchQrPrintsTheLocalisedOutcomeLabel | TextQrAllLanguagesTest#t8 | T21.6 |
| UC-GRO-018 | haut | partiel | **partiel** | IpsPregnancyCodecTest#bundleSectionAndNoLeakIntoResults | PersonaSeedIntegrityTest#p2 · PersonaSeedIntegrityTest#p5 | T21.2 |
| UC-GRO-019 | bas | couvert | **couvert** | IpsPregnancyCodecTest#projection | PillarBoundaryRoundTripTest#b4 · PillarBoundaryRoundTripTest#b3 |  |
| UC-FON-001 | haut | partiel | **partiel** | IpsFunctionalCodecTest#roundTripsAndStatuses ; #projection | PersonaSeedIntegrityTest#p2 | création codée à l'écran : appareil |
| UC-FON-002 | haut | partiel | **partiel** | IpsFunctionalCodecTest#roundTripsAndStatuses | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b2 | T22.3 |
| UC-FON-003 | moyen | partiel | **partiel** | IpsFunctionalCodecTest#projection | PillarBoundaryRoundTripTest#b1 | T22.4 |
| UC-FON-004 | moyen | — | **appareil** |  |  |  |
| UC-FON-005 | bas | — | **couvert** |  | PersonaSeedIntegrityTest#p5 · RandomProfileInvariantsTest#r1 |  |
| UC-FON-006 | bas | — | **appareil** |  |  |  |
| UC-FON-007 | moyen | — | **appareil** |  |  |  |
| UC-FON-008 | moyen | — | **appareil** |  |  |  |
| UC-FON-009 | bas | — | **appareil** |  |  |  |
| UC-FON-010 | bas | — | **appareil** |  |  |  |
| UC-FON-011 | moyen | couvert | **couvert** | IpsFunctionalCodecTest#threeConditionPillarsNeverMix | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b2 |  |
| UC-FON-012 | moyen | couvert | **couvert** | IpsFunctionalCodecTest#functionalStatusNeverFeedsTheDrugDiseaseProjection | PersonaSeedIntegrityTest#p2 |  |
| UC-FON-013 | moyen | — | **appareil** |  |  |  |
| UC-FON-014 | bas | — | **appareil** |  |  |  |
| UC-FON-015 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 |  |
| UC-FON-016 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b3 |  |
| UC-FON-017 | moyen | — | **JVM à écrire (échouerait)** |  |  | le suffixe de statut du QR texte est le code anglais brut |
| UC-FON-018 | bas | couvert | **couvert** | IpsFunctionalCodecTest#projection | PillarBoundaryRoundTripTest#b3 |  |

## 02 — Sécurité clinique et canaux d'urgence (`02-safety-and-emergency.md`)

| ID | Risque | Avant | Après | Tests JVM existants | Nouveaux tests JVM | Reste / remarque |
|---|---|---|---|---|---|---|
| UC-DDI-001 | haut | — | **appareil** |  |  | KB (vue SQL) |
| UC-DDI-002 | moyen | — | **appareil** |  |  |  |
| UC-DDI-003 | haut | — | **appareil** |  |  |  |
| UC-DDI-004 | haut | — | **appareil** |  |  |  |
| UC-DDI-005 | haut | — | **appareil** |  |  |  |
| UC-DDI-006 | haut | partiel | **partiel** | KbSafetyTest#ucSafeKb04_unverifiedEntry_makesTheCheckIncomplete ; SafetyBannerDecisionTest#UC-SAFE-UI-03 ; #UC-SAFE-UI-09 ; ProfileUnverifiedCountTest | KbSafetyTruthTableTest#k12 | bandeau à l'écran : appareil |
| UC-DDI-007 | moyen | — | **appareil** |  |  |  |
| UC-DDI-008 | haut | — | **JVM après extraction** |  |  | la normalisation des noms est dans KnowledgeBaseService.resolveDrug |
| UC-DDI-009 | haut | — | **appareil** |  |  |  |
| UC-DDI-010 | haut | — | **appareil** |  |  |  |
| UC-DDI-011 | moyen | — | **appareil** |  |  |  |
| UC-DDI-012 | haut | partiel | **partiel** | KbSafetyTest#ucSafeKb05_mostSevereRowWins_whateverTheRowOrder ; #ucSafeKb05_severityRankOrder |  | usage par la requête de l'hydrateur : appareil |
| UC-DDI-013 | moyen | — | **partiel** |  | KbSafetyTruthTableTest#k13 | compteurs « majeur » vérifiés ; ordre à l'écran : appareil |
| UC-DDI-014 | moyen | — | **appareil** |  |  |  |
| UC-DDI-015 | haut | partiel | **partiel** | KbSafetyTest#ucSafeKb01 (5 tests) ; SafetyBannerDecisionTest#UC-SAFE-UI-02 | KbSafetyTruthTableTest#k11 · KbSafetyTruthTableTest#k12 | demande un appareil sans KB |
| UC-DDI-016 | haut | partiel | **partiel** | MedicationFormSafetyVerdictTest#UC-MED-ROUTE-10..13 | KbSafetyTruthTableTest#k16 | dialogue : appareil |
| UC-DDI-017 | haut | partiel | **partiel** | FormCrossCheckOutcomeTest#ucSafeScan22_uncodedMedications_areReportedNotSkipped ; MedicationFormLogicTest#UC-MED-023 |  | toujours pas comparé, seulement signalé (README, reste n° 3) |
| UC-DDI-018 | haut | partiel | **partiel** | MedicationFormLogicTest#UC-MED-002 ; MedicationFormSafetyVerdictTest#UC-MED-ROUTE-11 | KbSafetyTruthTableTest#k15 | dialogue : appareil |
| UC-ALM-001 | haut | — | **appareil** |  |  |  |
| UC-ALM-002 | haut | — | **appareil** |  |  |  |
| UC-ALM-003 | haut | — | **appareil** |  |  |  |
| UC-ALM-004 | haut | — | **JVM après extraction** |  |  | inferAtcFromAllergyName est privée et dupliquée (hydrateur, KbCrossCheck) |
| UC-ALM-005 | haut | — | **JVM après extraction** |  |  | même extraction |
| UC-ALM-006 | haut | — | **appareil** |  |  |  |
| UC-ALM-007 | moyen | — | **JVM après extraction** |  |  | même extraction |
| UC-ALM-008 | haut | — | **JVM après extraction** |  |  | même extraction |
| UC-ALM-009 | moyen | — | **JVM après extraction** |  |  | même extraction |
| UC-ALM-010 | moyen | — | **JVM après extraction** |  |  | même extraction |
| UC-ALM-011 | haut | — | **JVM après extraction** |  |  | même extraction |
| UC-ALM-012 | haut | — | **appareil** |  |  |  |
| UC-ALM-013 | moyen | — | **jugement humain** |  |  | une allergie inactive ou résolue doit-elle encore alerter |
| UC-ALM-014 | haut | — | **partiel** |  | KbSafetyTruthTableTest#k13 | une alerte de criticité inconnue reste une alerte et n'est pas comptée majeure ; bandeau : appareil |
| UC-ALM-015 | haut | partiel | **partiel** | AllergyFormSafetyLogicTest#UC-SAFE-UI-24 ; #UC-SAFE-UI-27 |  | dialogue : appareil |
| UC-ALM-016 | haut | partiel | **partiel** | AllergyFormSafetyLogicTest#UC-SAFE-UI-26 ; FormCrossCheckOutcomeTest#ucSafeScan22 |  | dialogue : appareil |
| UC-ALM-017 | moyen | — | **JVM après extraction** |  |  | computeAllergySeverity est dans la classe du dialogue |
| UC-ALM-018 | moyen | — | **appareil** |  |  |  |
| UC-ALM-019 | haut | partiel | **partiel** | KbSafetyTest#ucSafeKb01_kbAbsent_hydratedProfileIsNotClean ; SafetyBannerDecisionTest#UC-SAFE-UI-02 | KbSafetyTruthTableTest#k12 | demande un appareil sans KB |
| UC-DDS-001 | haut | partiel | **partiel** | DrugDiseaseTermsTest#englishTermsComeFirstAndLocalisedLast ; #matchingWorksBothWays |  | T17.2, T19.2 |
| UC-DDS-002 | haut | — | **appareil** |  |  | T17.4 |
| UC-DDS-003 | haut | partiel | **partiel** | DrugDiseaseTermsTest#pluralsDoNotBlockAMatch |  | T18.1 |
| UC-DDS-004 | haut | couvert | **couvert** | DrugDiseaseTermsTest#kidneyAndRenalAreInterchangeable |  |  |
| UC-DDS-005 | haut | — | **appareil** |  |  | SQL |
| UC-DDS-006 | moyen | couvert | **couvert** | DrugDiseaseTermsTest#englishTermsComeFirstAndLocalisedLast |  |  |
| UC-DDS-007 | moyen | couvert | **couvert** | DrugDiseaseTermsTest#pluralsDoNotBlockAMatch |  |  |
| UC-DDS-008 | moyen | — | **JVM à écrire (échouerait)** |  |  | SD-09 : correspondance par sous-chaîne, fausse contre-indication |
| UC-DDS-009 | moyen | — | **JVM à écrire (échouerait)** |  |  | SD-09 |
| UC-DDS-010 | moyen | — | **JVM à écrire (échouerait)** |  |  | SD-09 |
| UC-DDS-011 | bas | couvert | **couvert** | DrugDiseaseTermsTest#matchingWorksBothWays |  |  |
| UC-DDS-012 | bas | — | **JVM à écrire (échouerait)** |  |  | SD-09 |
| UC-DDS-013 | haut | — | **JVM à écrire (échouerait)** |  |  | SD-09 : contre-indication manquée |
| UC-DDS-014 | haut | — | **JVM à écrire (échouerait)** |  |  | SD-09 |
| UC-DDS-015 | haut | — | **jugement humain** |  |  | pas d'ontologie : que dire à l'utilisateur |
| UC-DDS-016 | moyen | partiel | **partiel** | DrugDiseaseTermsTest#pluralsDoNotBlockAMatch |  | « psychoses » n'est pas reconnu (SD-09) |
| UC-DDS-017 | haut | — | **JVM à écrire (échouerait)** |  |  | SD-09 : un libellé seulement français ne correspond jamais |
| UC-DDS-018 | haut | partiel | **partiel** | ProfileUnverifiedCountTest#UC-SAFE-UI-30 ; #UC-SAFE-UI-32 |  | une condition en texte libre japonais est-elle comptée « non vérifiée » : appareil |
| UC-DDS-019 | haut | partiel | **JVM à écrire** | DrugDiseaseTermsTest#blanksAndDuplicatesAreDropped |  | abréviations (HTA, IRC) : test JVM d'une ligne, non écrit |
| UC-DDS-020 | moyen | — | **appareil** |  |  | X |
| UC-DDS-021 | moyen | partiel | **partiel** | IpsFunctionalCodecTest#functionalStatusNeverFeedsTheDrugDiseaseProjection |  | antécédents : l'hydrateur ne lit que `cn` (constaté à la lecture) |
| UC-DDS-022 | haut | partiel | **partiel** | ProfileUnverifiedCountTest (5 tests) ; SafetyBannerDecisionTest#UC-SAFE-UI-09 | KbSafetyTruthTableTest#k12 | bandeau : appareil |
| UC-DDS-023 | haut | partiel | **partiel** | KbSafetyTest#ucSafeKb01 ; #ucSafeKb04 | KbSafetyTruthTableTest#k10 · KbSafetyTruthTableTest#k12 | demande un appareil sans KB |
| UC-DDS-024 | haut | partiel | **partiel** | MedicationFormSafetyVerdictTest#UC-MED-ROUTE-10..13 | KbSafetyTruthTableTest#k16 | dialogue : appareil |
| UC-DDS-025 | moyen | — | **appareil** |  |  |  |
| UC-ALR-001 | haut | partiel | **partiel** | SafetyBannerDecisionTest#UC-SAFE-UI-05 ; #UC-SAFE-UI-07 | KbSafetyTruthTableTest#k13 | bandeau : appareil |
| UC-ALR-002 | haut | partiel | **partiel** | SafetyBannerDecisionTest#UC-SAFE-UI-07 | KbSafetyTruthTableTest#k13 | bandeau : appareil |
| UC-ALR-003 | moyen | partiel | **partiel** | SafetyBannerDecisionTest#UC-SAFE-UI-01 ; #UC-SAFE-UI-08 | KbSafetyTruthTableTest#k12 | écran : appareil |
| UC-ALR-004 | haut | — | **appareil** |  |  |  |
| UC-ALR-005 | moyen | — | **appareil** |  |  |  |
| UC-ALR-006 | moyen | — | **appareil** |  |  |  |
| UC-ALR-007 | bas | — | **appareil** |  |  |  |
| UC-ALR-008 | moyen | — | **appareil** |  |  |  |
| UC-ALR-009 | haut | — | **appareil** |  |  | demande un appareil sans KB |
| UC-ALR-010 | moyen | — | **JVM après extraction** |  |  | clé de cache construite dans ProfileDetailFragment |
| UC-ALR-011 | moyen | partiel | **partiel** | AllergyFormSafetyLogicTest#UC-SAFE-UI-29 |  | retour système sur le dialogue : appareil |
| UC-ALR-012 | moyen | — | **JVM après extraction** |  |  | computeMedSeverity est dans CrossCheckAlertDialog |
| UC-ALR-013 | haut | — | **appareil** |  |  |  |
| UC-ALR-014 | moyen | — | **appareil** |  |  |  |
| UC-QRT-001 | haut | partiel | **partiel** | QrTextBudgetTest#ucQrText02 ; #ucQrText06 | TextQrAllLanguagesTest#t4 · TextQrAllLanguagesTest#t5 | scan par un appareil photo : appareil (T5) |
| UC-QRT-002 | moyen | partiel | **partiel** | PregnancyTextQrTest ; PastProblemsTextQrTest#localisedLabelsWinOverTheEnglishTerm | TextQrAllLanguagesTest#t8 · TextQrAllLanguagesTest#t9 · TextQrAllLanguagesTest#t6 | libellés de la KB : appareil |
| UC-QRT-003 | haut | partiel | **partiel** | PastProblemsTextQrTest#kbPlaceholdersAreDropped | TextQrAllLanguagesTest#t5 · TextQrAllLanguagesTest#t7 · TextQrAllLanguagesTest#t8 | 25 langues vérifiées sans KB ; traductions venant de la KB : appareil |
| UC-QRT-004 | moyen | partiel | **couvert** | QrTextBudgetTest#ucQrText08 | TextQrAllLanguagesTest#t3 · TextQrAllLanguagesTest#t2 |  |
| UC-QRT-005 | haut | couvert | **couvert** | QrTextBudgetTest#ucQrText03 | TextQrAllLanguagesTest#t5 |  |
| UC-QRT-006 | haut | — | **JVM à écrire (échouerait)** |  |  | pas de ligne « aucune allergie connue / non renseigné » (absent du README ; catalogue 02, trou n° 19) |
| UC-QRT-007 | bas | partiel | **couvert** | PregnancyTextQrTest | TextQrAllLanguagesTest#t10 |  |
| UC-QRT-008 | haut | couvert | **couvert** | QrTextBudgetTest#ucQrText04 | TextQrAllLanguagesTest#t6 · RandomProfileInvariantsTest#r4 |  |
| UC-QRT-009 | haut | couvert | **couvert** | QrTextBudgetTest#ucQrText05 | RandomProfileInvariantsTest#r4 |  |
| UC-QRT-010 | haut | couvert | **couvert** | QrTextBudgetTest#ucQrText01 ; #ucQrText06 | TextQrAllLanguagesTest#t4 · RandomProfileInvariantsTest#r4 |  |
| UC-QRT-011 | bas | couvert | **couvert** | QrTextBudgetTest#ucQrText04 ; #ucQrText07 | RandomProfileInvariantsTest#r4 |  |
| UC-QRT-012 | moyen | — | **jugement humain** |  | TextQrAllLanguagesTest#t4 | le texte arabe est valide ; l'ordre de lecture dans un lecteur générique demande une personne |
| UC-QRT-013 | moyen | — | **JVM à écrire (échouerait)** |  |  | nom d'énumération brut (HIGH, UNABLE_TO_ASSESS) dans les 25 langues |
| UC-QRT-014 | moyen | — | **JVM à écrire (échouerait)** |  |  | réaction et statut de l'allergie non imprimés |
| UC-QRT-015 | moyen | couvert | **couvert** | QrTextBudgetTest#ucQrText02 | TextQrAllLanguagesTest#t5 |  |
| UC-QRT-016 | bas | — | **JVM à écrire (échouerait)** | PastProblemsTextQrTest fige « (inactive) » |  | codes de statut anglais bruts |
| UC-QRT-017 | bas | partiel | **couvert** | PastProblemsTextQrTest#localisedLabelsWinOverTheEnglishTerm | TextQrAllLanguagesTest#t9 |  |
| UC-QRT-018 | moyen | — | **appareil** |  |  |  |
| UC-QRT-019 | haut | — | **appareil** |  |  | mode avion : aucune action dans le vocabulaire appareil |
| UC-QRT-020 | haut | — | **appareil** |  |  | demande un appareil sans KB |
| UC-QRT-021 | moyen | partiel | **partiel** | JemmaQrCodecUnitTest#testQrBitmapEncoderBasicEncoding | TextQrAllLanguagesTest#t4 | décodage de l'image : appareil |
| UC-QRT-022 | moyen | — | **appareil** |  |  |  |
| UC-QRF-001 | haut | — | **partiel** |  | PersonaSeedIntegrityTest#p11 · RandomProfileInvariantsTest#r3 · PillarBoundaryRoundTripTest#b3 | élagage, trames et reconstruction depuis `_j` vérifiés ; deflate + Base64 (android) et le scan : appareil |
| UC-QRF-002 | haut | partiel | **partiel** | QrFrameAssemblerTest (7 tests) | RandomProfileInvariantsTest#r3 | scan des trames : appareil |
| UC-QRF-003 | moyen | — | **appareil** |  |  |  |
| UC-QRF-004 | moyen | couvert | **couvert** | JemmaQrCodecUnitTest#testSplitMultiFrameAccurateByteBudgeting ; #testSplitSingleFrameWithinLimit ; QrFrameAssemblerTest#ucQrFrame05 | RandomProfileInvariantsTest#r3 |  |
| UC-QRF-005 | bas | couvert | **couvert** | JemmaQrCodecUnitTest#testSplitEmptyPayloadSafe ; #testQrBitmapEncoderEmptyContentReturnsNull |  |  |
| UC-QRF-006 | moyen | partiel | **partiel** | IpsImmunizationCodecTest#bundleTypeIsDocumentWithCompositionFirst ; IpsBloodGroupTest#bundleHasAnIdentifierNoEmptyNamePartsAndNoHomeMadeExtension | PersonaSeedIntegrityTest#p6 · PersonaSeedIntegrityTest#p7 · RandomProfileInvariantsTest#r1 | validateur HL7 : appareil |
| UC-QRF-007 | moyen | — | **JVM à écrire (échouerait)** |  |  | sections obligatoires absentes pour un profil vide |
| UC-QRF-008 | moyen | — | **JVM à écrire (échouerait)** |  |  | SD-05 : Coding avec un code vide |
| UC-QRF-009 | moyen | — | **JVM à écrire (échouerait)** |  |  | système de code de l'allergie forcé à SNOMED |
| UC-QRF-010 | bas | — | **JVM à écrire (échouerait)** |  |  | Coding.display dans la langue de l'interface pour allergies et médicaments |
| UC-QRF-011 | moyen | — | **jugement humain** |  |  | verificationStatus « confirmed » pour une allergie déclarée par le patient |
| UC-QRF-012 | moyen | couvert | **couvert** | FhirMedicationBundleTest#UC-FHIR-024 (2 tests) | PillarBoundaryRoundTripTest#b11 |  |
| UC-QRF-013 | moyen | — | **JVM à écrire (échouerait)** |  |  | SD-03 : une date de naissance non ISO fait lever le constructeur (onglet FHIR sans garde) |
| UC-QRF-014 | bas | couvert | **couvert** | IpsImmunizationCodecTest#bundleUrnsAreStableAcrossRebuilds ; #stableUrnsAreDeterministic | PersonaSeedIntegrityTest#p4 · RandomProfileInvariantsTest#r1 |  |
| UC-QRF-015 | moyen | couvert | **couvert** | IpsProblemCodecTest#problemsAndPastProblemsLiveInTheirOwnSections ; IpsFunctionalCodecTest#threeConditionPillarsNeverMix | PillarBoundaryRoundTripTest#b2 · RandomProfileInvariantsTest#r1 |  |
| UC-QRF-016 | moyen | — | **appareil** |  |  | X (JemmaPayloadCodec dépend de android.util.Base64) |
| UC-QRF-017 | haut | — | **partiel** |  | PersonaSeedIntegrityTest#p11 · RandomProfileInvariantsTest#r3 | élagage vérifié ; encodage / décodage complet : appareil |
| UC-QRF-018 | moyen | — | **appareil** |  |  |  |
| UC-PDF-001 | haut | — | **appareil** |  |  |  |
| UC-PDF-002 | haut | partiel | **partiel** | PdfPillarLayoutTest#ucPdf002 (4 tests) |  | rendu : appareil |
| UC-PDF-003 | moyen | partiel | **partiel** | PdfPillarLayoutTest#ucPdf003 (2 tests) |  | rendu : appareil |
| UC-PDF-004 | moyen | partiel | **partiel** | PdfPillarLayoutTest#ucPdf004 |  | rendu : appareil |
| UC-PDF-005 | haut | partiel | **partiel** | PdfPillarLayoutTest#ucPdf005 (2 tests) |  | rendu : appareil |
| UC-PDF-006 | moyen | — | **appareil** |  |  | X |
| UC-PDF-007 | bas | — | **appareil** |  |  |  |
| UC-PDF-008 | haut | — | **appareil** |  |  | impression puis scan |
| UC-PDF-009 | moyen | — | **appareil** |  |  |  |
| UC-PDF-010 | moyen | — | **appareil** |  |  |  |
| UC-PDF-011 | bas | — | **appareil** |  |  | X (nom de fichier construit dans JemmaPdfExporter) |
| UC-PDF-012 | moyen | — | **appareil** |  |  |  |
| UC-SOS-001 | haut | — | **appareil** |  |  |  |
| UC-SOS-002 | moyen | — | **appareil** |  |  |  |
| UC-SOS-003 | haut | — | **appareil** |  |  |  |
| UC-SOS-004 | moyen | — | **appareil** |  | StatusResolverTest#s2 | arrêt du widget : appareil ; la règle d'annulation du triage est vérifiée |
| UC-SOS-005 | moyen | — | **appareil** |  |  |  |
| UC-SOS-006 | moyen | — | **appareil** |  |  |  |
| UC-SOS-007 | haut | — | **appareil** |  |  |  |
| UC-SOS-008 | haut | — | **appareil** |  |  |  |
| UC-SOS-009 | moyen | — | **appareil** |  |  |  |
| UC-SOS-010 | haut | partiel | **partiel** | JemmaSosChunkCodecTest#header chunk round-trips | NearbyEndpointBudgetTest#n1 · NearbyEndpointBudgetTest#n5 | deux téléphones : appareil |
| UC-SOS-011 | haut | — | **partiel** |  | NearbyEndpointBudgetTest#n2 · NearbyEndpointBudgetTest#n4 · NearbyEndpointBudgetTest#n3 | tient dans le segment et garde les premiers codes ; le secouriste n'est pas prévenu que la liste est incomplète (SD-04) |
| UC-SOS-012 | haut | — | **JVM à écrire (échouerait)** |  |  | SD-02 : les codes de plus de 10 caractères sont tronqués (les vaccins de Haru eux-mêmes) |
| UC-SOS-013 | haut | — | **jugement humain** |  |  | les entrées en texte libre ne peuvent pas être diffusées : que montrer au secouriste |
| UC-SOS-014 | moyen | — | **couvert** |  | NearbyEndpointBudgetTest#n5 |  |
| UC-SOS-015 | moyen | couvert | **couvert** | JemmaSosChunkCodecTest#decode of too-short bytes returns null cleanly | NearbyEndpointBudgetTest#n6 |  |
| UC-SOS-016 | haut | — | **appareil** |  |  |  |
| UC-SOS-017 | haut | — | **appareil** |  |  |  |
| UC-SOS-018 | haut | — | **appareil** |  |  |  |
| UC-SOS-019 | moyen | — | **appareil** |  |  |  |
| UC-SOS-020 | haut | — | **couvert** |  | StatusResolverTest#s1 · StatusResolverTest#s2 · StatusResolverTest#s4 |  |
| UC-SOS-021 | haut | — | **partiel** |  | StatusResolverTest#s3 | les égalités sont déterministes ; horloges décalées et ordre d'arrivée : SD-01 |
| UC-SOS-022 | moyen | — | **JVM à écrire** |  |  | TtlPolicy est du Kotlin pur ; non examiné dans cette passe |
| UC-AI-001 | haut | — | **appareil** |  |  |  |
| UC-AI-002 | haut | partiel | **partiel** | KbSafetyTest#ucSafeKb04_candidateUnknownToKb_isNotCheckedEvenWithLegacyDefaults ; #ucSafeKb01_kbAbsent_llmToolAnswerSaysNotChecked | KbSafetyTruthTableTest#k15 · KbSafetyTruthTableTest#k17 | l'appel d'outil dans JemmaTools : appareil |
| UC-AI-003 | haut | partiel | **partiel** | KbSafetyTest#ucSafeKb01_kbAbsent_llmToolAnswerSaysNotChecked | KbSafetyTruthTableTest#k17 | demande un appareil sans KB |
| UC-AI-004 | haut | — | **appareil** |  |  |  |
| UC-AI-005 | haut | — | **appareil** |  |  |  |
| UC-AI-006 | haut | — | **appareil** |  |  |  |
| UC-AI-007 | haut | — | **appareil** |  |  |  |
| UC-AI-008 | haut | — | **appareil** |  |  |  |
| UC-AI-009 | moyen | — | **appareil** |  |  |  |
| UC-AI-010 | haut | — | **jugement humain** |  |  | pas d'outil pour grossesse, autonomie, contacts (README, reste n° 12) |
| UC-AI-011 | haut | — | **appareil** |  |  |  |
| UC-AI-012 | moyen | — | **appareil** |  |  |  |
| UC-AI-013 | bas | — | **appareil** |  |  |  |
| UC-AI-014 | moyen | — | **appareil** |  |  |  |
| UC-AI-015 | moyen | — | **appareil** |  |  |  |
| UC-AI-016 | bas | — | **appareil** |  |  | X |
| UC-SCAN-001 | haut | — | **appareil** |  |  |  |
| UC-SCAN-002 | haut | partiel | **partiel** | MedScanStepSafetyTest#UC-SAFE-SCAN-30..35 |  | scan réel : appareil |
| UC-SCAN-003 | haut | — | **appareil** |  |  |  |
| UC-SCAN-004 | haut | — | **appareil** |  |  |  |
| UC-SCAN-005 | haut | — | **appareil** |  |  |  |
| UC-SCAN-006 | haut | — | **appareil** |  |  |  |
| UC-SCAN-007 | moyen | — | **appareil** |  |  |  |
| UC-SCAN-008 | haut | partiel | **partiel** | ScanSafetyTest#ucSafeScan01 (4 tests) ; LiveScanVerdictBadgeTest#UC-SAFE-UI-21 |  | scan sans modèle : appareil |
| UC-SCAN-009 | moyen | — | **appareil** |  |  |  |
| UC-SCAN-010 | haut | — | **jugement humain** | KbSafetyTest#ucSafeKb02_nothingToCheck_isCleanEvenWithoutKb fige CLEAN |  | SD-06 : un profil vide reçoit le verdict CLEAN |
| UC-SCAN-011 | moyen | — | **appareil** |  |  |  |
| UC-SCAN-012 | moyen | — | **appareil** |  |  |  |

## 03 — Intégrité des données et diversité des personnes (`03-data-integrity-and-people.md`)

| ID | Risque | Avant | Après | Tests JVM existants | Nouveaux tests JVM | Reste / remarque |
|---|---|---|---|---|---|---|
| UC-STO-001 | moyen | — | **appareil** |  |  |  |
| UC-STO-002 | moyen | — | **appareil** |  |  |  |
| UC-STO-003 | haut | — | **appareil** |  |  |  |
| UC-STO-004 | haut | — | **partiel** |  | PersonaSeedIntegrityTest#p4 · PillarBoundaryRoundTripTest#b1 · RandomProfileInvariantsTest#r1 | une sauvegarde faite depuis ce qui a été relu garde les 8 piliers et leurs identifiants ; chemin du dépôt : appareil |
| UC-STO-005 | haut | partiel | **partiel** | allers-retours par pilier (IpsXxxCodecTest) ; IpsFunctionalCodecTest#threeConditionPillarsNeverMix | PillarBoundaryRoundTripTest#b1 · RandomProfileInvariantsTest#r1 | chemin du dépôt : appareil |
| UC-STO-006 | haut | — | **appareil** |  |  | X (ProfileStore à extraire de ProfilesRepository) |
| UC-STO-007 | moyen | — | **appareil** |  |  | X (ProfileStore à extraire de ProfilesRepository) |
| UC-STO-008 | haut | — | **appareil** |  |  | corrigé dans ProfilesRepository, pas de test JVM possible sans extraction |
| UC-STO-009 | haut | — | **appareil** |  |  | idem |
| UC-STO-010 | haut | — | **appareil** |  |  | idem |
| UC-STO-011 | moyen | partiel | **partiel** | IpsProcedureDeviceCodecTest#legacyJArraysRebuildDeterministically ; IpsPastProblemCodecTest#legacyJArrayRebuild | PersonaSeedIntegrityTest#p9 | repli du dépôt : appareil |
| UC-STO-012 | moyen | — | **appareil** |  |  | X |
| UC-STO-013 | moyen | partiel | **partiel** | IpsPastProblemCodecTest#noSectionWhenThePillarIsEmpty | PersonaSeedIntegrityTest#p5 · RandomProfileInvariantsTest#r1 | suppression à l'écran : appareil |
| UC-STO-014 | moyen | partiel | **partiel** | IpsProblemCodecTest#withoutNativePillarsTheBuilderFallsBackOnTheProjection | PersonaSeedIntegrityTest#p9 | chemin du dépôt : appareil |
| UC-STO-015 | moyen | partiel | **partiel** | IpsImmunizationCodecTest#parseBundleRejectsGarbage |  | repli du dépôt : appareil |
| UC-STO-016 | haut | — | **appareil** |  |  | X |
| UC-STO-017 | moyen | — | **appareil** |  |  | X |
| UC-STO-018 | haut | — | **appareil** |  |  | X |
| UC-STO-019 | bas | — | **appareil** |  |  |  |
| UC-STO-020 | moyen | couvert | **couvert** | IpsImmunizationCodecTest#bundleUrnsAreStableAcrossRebuilds ; #stableUrnsAreDeterministic | PersonaSeedIntegrityTest#p4 · RandomProfileInvariantsTest#r1 |  |
| UC-STO-021 | bas | — | **appareil** |  |  | X |
| UC-STO-022 | moyen | — | **appareil** |  |  | X |
| UC-STO-023 | haut | couvert | **couvert** | IpsBloodGroupTest#syncKeepsExactlyOneBloodGroupResult | PersonaSeedIntegrityTest#p3 |  |
| UC-STO-024 | haut | couvert | **couvert** | IpsBloodGroupTest#syncKeepsExactlyOneBloodGroupResult |  |  |
| UC-STO-025 | haut | couvert | **couvert** | IpsBloodGroupSyncTest#UC-BLOOD-03 ; BloodGroupConflictTest#UC-BLOOD-10..12 |  |  |
| UC-STO-026 | haut | couvert | **couvert** | IpsBloodGroupTest#bloodTypeLabelsNormaliseAndMapToTheKbConfirmedSnomedCodes ; IpsBundleConsistencyTest#UC-FHIR-027 sans groupe sanguin | RandomProfileInvariantsTest#r1 |  |
| UC-STO-027 | moyen | — | **appareil** |  |  |  |
| UC-STO-028 | bas | — | **appareil** |  |  |  |
| UC-STO-029 | bas | — | **partiel** |  | PersonaSeedIntegrityTest#p1 · PersonaSeedIntegrityTest#p2 | les données du semis et leurs compteurs ; le semis lui-même : appareil (run_device_qa.sh) |
| UC-STO-030 | bas | — | **appareil** |  |  |  |
| UC-STO-031 | bas | — | **appareil** |  |  |  |
| UC-IMP-001 | haut | — | **partiel** |  | PillarBoundaryRoundTripTest#b3 · RandomProfileInvariantsTest#r2 · RandomProfileInvariantsTest#r3 · PersonaSeedIntegrityTest#p11 | reconstruction depuis `_j`, élagage, trames ; deflate + Base64 et le scan : appareil |
| UC-IMP-002 | moyen | partiel | **partiel** | IpsImmunizationProjectionTest#legacyPayloadWithoutTheNewKeysStillParses | PersonaSeedIntegrityTest#p8 · PillarBoundaryRoundTripTest#b10 | scan : appareil |
| UC-IMP-003 | moyen | — | **appareil** |  |  | X |
| UC-IMP-004 | haut | — | **appareil** |  |  |  |
| UC-IMP-005 | haut | — | **appareil** |  |  | X |
| UC-IMP-006 | moyen | partiel | **partiel** | IpsImmunizationProjectionTest#rebuildFromProjectionPreservesWhatTheQrCarries | PersonaSeedIntegrityTest#p9 | l'avertissement à l'utilisateur : appareil |
| UC-IMP-007 | bas | couvert | **couvert** | IpsImmunizationProjectionTest#rebuildIsDeterministic ; IpsPastProblemCodecTest#projectionIsDeterministic | PersonaSeedIntegrityTest#p10 · PillarBoundaryRoundTripTest#b3 · RandomProfileInvariantsTest#r5 |  |
| UC-IMP-008 | bas | — | **JVM à écrire** |  |  | les identifiants changent avec l'ordre des entrées (index dans la graine) : risque bas, non écrit |
| UC-IMP-009 | haut | partiel | **partiel** | ProfileFilesTest#hostileOrBrokenIdsAreRejected ; #plainIdsAreKept | ProfileIdHostileListTest#h1 · ProfileIdHostileListTest#h2 · ProfileIdHostileListTest#h3 · ProfileIdHostileListTest#h4 | SD-07 : les identifiants finissant par « .fhir » sont acceptés |
| UC-IMP-010 | bas | — | **appareil** |  |  |  |
| UC-IMP-011 | bas | — | **appareil** |  |  | X |
| UC-IMP-012 | moyen | — | **appareil** |  |  | X |
| UC-IMP-013 | haut | — | **JVM à écrire (échouerait)** |  |  | SD-03 |
| UC-IMP-014 | moyen | — | **jugement humain** | IpsPregnancyCodecTest#projection fige l'abandon silencieux | PillarBoundaryRoundTripTest#b4 | conserver, ou rejeter avec un message : décision |
| UC-IMP-015 | moyen | couvert | **couvert** | IpsBundleConsistencyTest#UC-FHIR-025 (2 tests) |  |  |
| UC-IMP-016 | moyen | couvert | **couvert** | IpsImmunizationCodecTest#statusIsNormalized ; IpsResultCodecTest#statusInterpretationAndCategoryNormalise ; IpsPastProblemCodecTest#statusNormalizationKeepsOnlyPastStatuses ; IpsProblemCodecTest#statusNormalization | PillarBoundaryRoundTripTest#b4 |  |
| UC-IMP-017 | haut | — | **JVM à écrire (échouerait)** |  |  | SD-13 |
| UC-IMP-018 | haut | — | **appareil** |  |  |  |
| UC-IMP-019 | haut | — | **appareil** |  |  |  |
| UC-IMP-020 | moyen | — | **appareil** |  |  |  |
| UC-IMP-021 | moyen | — | **appareil** |  |  |  |
| UC-IMP-022 | moyen | — | **appareil** |  |  |  |
| UC-IMP-023 | bas | couvert | **couvert** | IpsProblemCodecTest#withoutNativePillarsTheBuilderFallsBackOnTheProjection | PersonaSeedIntegrityTest#p9 · RandomProfileInvariantsTest#r2 |  |
| UC-MPR-001 | moyen | — | **appareil** |  |  |  |
| UC-MPR-002 | haut | — | **appareil** |  |  |  |
| UC-MPR-003 | haut | — | **appareil** |  |  | X |
| UC-MPR-004 | haut | — | **appareil** |  |  |  |
| UC-MPR-005 | haut | — | **appareil** |  |  | X |
| UC-MPR-006 | haut | — | **appareil** |  |  | X |
| UC-MPR-007 | haut | — | **appareil** |  |  |  |
| UC-MPR-008 | haut | — | **appareil** |  |  |  |
| UC-MPR-009 | haut | — | **appareil** |  |  |  |
| UC-MPR-010 | bas | — | **appareil** |  |  |  |
| UC-MPR-011 | moyen | — | **appareil** |  |  |  |
| UC-MPR-012 | haut | — | **appareil** |  |  |  |
| UC-MPR-013 | bas | — | **appareil** |  |  |  |
| UC-MPR-014 | haut | — | **appareil** |  |  | X ; voir aussi SD-07 |
| UC-MPR-015 | moyen | — | **appareil** |  |  |  |
| UC-FHIR-001 | moyen | couvert | **couvert** | IpsImmunizationCodecTest#bundleTypeIsDocumentWithCompositionFirst | PersonaSeedIntegrityTest#p6 · RandomProfileInvariantsTest#r1 |  |
| UC-FHIR-002 | moyen | partiel | **couvert** | IpsImmunizationCodecTest#stableUrnsAreDeterministic ; #bundleUrnsAreStableAcrossRebuilds | PersonaSeedIntegrityTest#p5 · RandomProfileInvariantsTest#r1 · PillarBoundaryRoundTripTest#b2 |  |
| UC-FHIR-003 | moyen | couvert | **couvert** | IpsPastProblemCodecTest#noSectionWhenThePillarIsEmpty ; IpsImmunizationCodecTest#bundleWithoutNativePillarsHasNoImmunizationSection | PersonaSeedIntegrityTest#p5 · RandomProfileInvariantsTest#r1 |  |
| UC-FHIR-004 | haut | partiel | **couvert** | tests de projection par pilier | PersonaSeedIntegrityTest#p2 · RandomProfileInvariantsTest#r1 · RandomProfileInvariantsTest#r2 |  |
| UC-FHIR-005 | moyen | partiel | **partiel** | les cinq tests …JsonCarriesIpsEssentials | PersonaSeedIntegrityTest#p7 | profils des allergies et médicaments non vérifiés ; validateur : appareil |
| UC-FHIR-006 | moyen | — | **JVM à écrire (échouerait)** |  |  | sections obligatoires absentes pour un profil vide |
| UC-FHIR-007 | haut | — | **JVM à écrire (échouerait)** |  |  | « aucune allergie connue » indiscernable de « non renseigné » |
| UC-FHIR-008 | haut | couvert | **couvert** | FhirMedicationBundleTest#UC-FHIR-008 | PillarBoundaryRoundTripTest#b11 |  |
| UC-FHIR-009 | moyen | — | **JVM à écrire (échouerait)** |  |  | AllergyIntolerance.type vaut toujours « allergy » |
| UC-FHIR-010 | moyen | — | **JVM à écrire (échouerait)** |  |  | système de code de l'allergie toujours SNOMED |
| UC-FHIR-011 | moyen | — | **JVM à écrire (échouerait)** |  |  | SD-05 |
| UC-FHIR-012 | moyen | — | **JVM à écrire (échouerait)** |  |  | réactions, début et catégorie de l'allergie non exportés |
| UC-FHIR-013 | bas | — | **JVM à écrire (échouerait)** |  |  | Patient.identifier n'est pas émis |
| UC-FHIR-014 | bas | — | **JVM à écrire (échouerait)** |  |  | SD-05 : name.text vide pour un contact sans nom |
| UC-FHIR-015 | moyen | couvert | **couvert** | IpsImmunizationCodecTest#unknownDateBecomesOccurrenceStringAndComesBackNull ; IpsProcedureDeviceCodecTest#procedureUnknownDateAndFreeTextAndStatuses ; IpsResultCodecTest#undatedAndUnattributedResultsStillMeetTheIpsCardinalities | PillarBoundaryRoundTripTest#b7 · PillarBoundaryRoundTripTest#b1 |  |
| UC-FHIR-016 | moyen | couvert | **couvert** | IpsImmunizationCodecTest#partialDatesAreKeptAsIs ; IpsPastProblemCodecTest#partialDatesSurvive | PillarBoundaryRoundTripTest#b6 |  |
| UC-FHIR-017 | bas | couvert | **couvert** | IpsResultCodecTest#imagingWithoutDayPreciseDateUsesTheGenericResultsProfile |  |  |
| UC-FHIR-018 | haut | couvert | **couvert** | IpsFunctionalCodecTest#threeConditionPillarsNeverMix ; IpsProblemCodecTest#problemsAndPastProblemsLiveInTheirOwnSections ; IpsPastProblemCodecTest#activeProblemListConditionsAreNotPastProblems | PillarBoundaryRoundTripTest#b2 · RandomProfileInvariantsTest#r1 |  |
| UC-FHIR-019 | moyen | couvert | **couvert** | IpsPregnancyCodecTest#bundleSectionAndNoLeakIntoResults ; IpsResultCodecTest#otherObservationKindsAreLeftToTheirOwnPillars ; IpsBundleConsistencyTest#UC-FHIR-025 | PillarBoundaryRoundTripTest#b2 |  |
| UC-FHIR-020 | bas | partiel | **partiel** | IpsProblemCodecTest#legacyProblemListConditionsAreReadWithAStableId |  | antécédents et vaccins sans id : id aléatoire à chaque lecture ; J, non écrit |
| UC-FHIR-021 | bas | partiel | **partiel** | IpsResultCodecTest#undatedAndUnattributedResultsStillMeetTheIpsCardinalities (id assaini) | PersonaSeedIntegrityTest#p3 | fhirId n'est appliqué qu'aux résultats et à la grossesse |
| UC-FHIR-022 | moyen | — | **JVM à écrire** |  |  | DeviceUseStatement dont le Device manque : facile à construire, non écrit |
| UC-FHIR-023 | moyen | — | **appareil** |  |  | validateur HL7, réseau |
| UC-FHIR-024 | moyen | couvert | **couvert** | FhirMedicationBundleTest#UC-FHIR-024 (2 tests) |  |  |
| UC-I18N-001 | bas | — | **appareil** |  |  |  |
| UC-I18N-002 | moyen | — | **appareil** |  |  | possible par script sur res/values-*, pas par test JVM |
| UC-I18N-003 | moyen | — | **appareil** |  |  |  |
| UC-I18N-004 | haut | partiel | **couvert** | QrTextBudgetTest#ucQrText03 (3 langues) | TextQrAllLanguagesTest#t1 · TextQrAllLanguagesTest#t4 · TextQrAllLanguagesTest#t5 · TextQrAllLanguagesTest#t8 |  |
| UC-I18N-005 | moyen | partiel | **couvert** | QrTextBudgetTest#ucQrText08 | TextQrAllLanguagesTest#t2 · TextQrAllLanguagesTest#t3 |  |
| UC-I18N-006 | moyen | partiel | **partiel** | PastProblemsTextQrTest#kbPlaceholdersAreDropped | TextQrAllLanguagesTest#t7 | écrans d'édition et sélecteurs : appareil |
| UC-I18N-007 | bas | couvert | **couvert** | PastProblemsTextQrTest#localisedLabelsWinOverTheEnglishTerm ; PregnancyTextQrTest#frenchQrPrintsTheLocalisedOutcomeLabel |  |  |
| UC-I18N-008 | haut | couvert | **couvert** | QrTextBudgetTest#ucQrText04 ; #ucQrText05 ; #ucQrText06 | TextQrAllLanguagesTest#t4 · TextQrAllLanguagesTest#t6 · RandomProfileInvariantsTest#r4 | voir SD-11 pour ce que Haru perd dans sa propre langue |
| UC-I18N-009 | moyen | — | **appareil** |  |  |  |
| UC-I18N-010 | bas | couvert | **couvert** | IpsResultCodecTest#officialDisplayInCodingFriendlyLabelInText | PillarBoundaryRoundTripTest#b1 |  |
| UC-I18N-011 | haut | couvert | **couvert** | IpsResultCodecTest#integralAndCommaDecimalsSurviveTheDoubleJsonEncoding ; #decimalHelpers | PillarBoundaryRoundTripTest#b8 |  |
| UC-I18N-012 | haut | partiel | **partiel** | IpsResultCodecTest#nonNumericTypedValueFallsBackToValueString ; FhirMedicationBundleTest#UC-FHIR-024 illisible ou ambiguë | PillarBoundaryRoundTripTest#b8 | pas d'avertissement à l'utilisateur : appareil |
| UC-I18N-013 | moyen | — | **JVM à écrire** |  |  | IpsDecimal avec des chiffres arabes orientaux : test JVM d'une ligne, non écrit |
| UC-I18N-014 | moyen | couvert | **couvert** | IpsResultCodecTest#referenceRangeParsing | RandomProfileInvariantsTest#r2 |  |
| UC-I18N-015 | moyen | — | **appareil** |  |  |  |
| UC-I18N-016 | moyen | — | **appareil** |  |  | X |
| UC-I18N-017 | moyen | partiel | **partiel** | FormEditGuardsTest#UC-PAT-006 aujourd'hui = jour local |  | sélecteurs de date : appareil |
| UC-I18N-018 | moyen | — | **jugement humain** |  |  | la mise en page de droite à gauche demande un lecteur de la langue |
| UC-I18N-019 | moyen | — | **couvert** |  | PillarBoundaryRoundTripTest#b10 · PillarBoundaryRoundTripTest#b11 · RandomProfileInvariantsTest#r1 · RandomProfileInvariantsTest#r4 | affichage : appareil |
| UC-I18N-020 | bas | — | **appareil** |  |  |  |
| UC-I18N-021 | moyen | — | **partiel** |  | TextQrAllLanguagesTest#t5 | le QR texte imprime la langue ; Patient.communication n'est pas vérifié |
| UC-I18N-022 | moyen | — | **JVM à écrire** |  |  | validité UCUM d'une unité saisie : non écrit |
| UC-A11Y-001 | moyen | — | **appareil** |  |  |  |
| UC-A11Y-002 | moyen | — | **jugement humain** |  |  |  |
| UC-A11Y-003 | moyen | — | **appareil** |  |  | possible par script sur les layouts |
| UC-A11Y-004 | moyen | — | **appareil** |  |  |  |
| UC-A11Y-005 | haut | partiel | **partiel** | FormEditGuardsTest#UC-PAT-007 |  | branchement de la garde : appareil |
| UC-A11Y-006 | moyen | — | **appareil** |  |  |  |
| UC-A11Y-007 | haut | — | **appareil** |  |  |  |
| UC-A11Y-008 | moyen | — | **appareil** |  |  | TalkBack |
| UC-A11Y-009 | haut | — | **appareil** |  |  | TalkBack |
| UC-A11Y-010 | moyen | — | **appareil** |  |  |  |
| UC-A11Y-011 | moyen | — | **jugement humain** |  |  |  |
| UC-A11Y-012 | moyen | — | **jugement humain** |  |  |  |
| UC-A11Y-013 | bas | — | **appareil** |  |  |  |
| UC-A11Y-014 | bas | — | **jugement humain** |  |  |  |
| UC-A11Y-015 | bas | — | **appareil** |  |  |  |
| UC-A11Y-016 | bas | — | **jugement humain** |  |  |  |
| UC-A11Y-017 | moyen | — | **appareil** |  |  |  |
| UC-HUM-001 | haut | partiel | **partiel** | FormEditGuardsTest#UC-HUM-002 fige le refus d'une date absente | PatientBirthDateBundleTest#d2 | le Bundle accepte l'absence de date ; le formulaire la refuse : décision (H) |
| UC-HUM-002 | haut | partiel | **partiel** | FormEditGuardsTest#UC-PAT-005 | PatientBirthDateBundleTest#d1 | d1 dépend du SDK FHIR (signalé) |
| UC-HUM-003 | moyen | partiel | **partiel** | PatientFormMergeTest#UC-PAT-005 |  | écran : appareil |
| UC-HUM-004 | moyen | couvert | **couvert** | IpsBloodGroupTest#bundleHasAnIdentifierNoEmptyNamePartsAndNoHomeMadeExtension | PersonaSeedIntegrityTest#p6 · TextQrAllLanguagesTest#t5 |  |
| UC-HUM-005 | moyen | — | **JVM à écrire (échouerait)** |  |  | SD-15 : un profil qui n'a qu'un nom de famille s'affiche « Profile inconnu » |
| UC-HUM-006 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b10 · PillarBoundaryRoundTripTest#b11 · RandomProfileInvariantsTest#r1 |  |
| UC-HUM-007 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b11 · RandomProfileInvariantsTest#r1 |  |
| UC-HUM-008 | haut | — | **appareil** |  |  |  |
| UC-HUM-009 | haut | — | **partiel** |  | PillarBoundaryRoundTripTest#b1 · RandomProfileInvariantsTest#r4 | les vaccins du même jour restent distincts ; le QR texte dit quand il coupe ; ordre à l'écran : appareil |
| UC-HUM-010 | haut | partiel | **partiel** | IpsPregnancyCodecTest#roundTrips | PillarBoundaryRoundTripTest#b1 | T21 |
| UC-HUM-011 | haut | — | **appareil** |  |  |  |
| UC-HUM-012 | haut | — | **jugement humain** |  |  |  |
| UC-HUM-013 | moyen | — | **jugement humain** |  |  |  |
| UC-HUM-014 | haut | partiel | **partiel** | JemmaQrCodecUnitTest#testSplitMultiFrameAccurateByteBudgeting ; QrFrameAssemblerTest | RandomProfileInvariantsTest#r1 · RandomProfileInvariantsTest#r2 · RandomProfileInvariantsTest#r3 · RandomProfileInvariantsTest#r4 | scan d'un QR multi-trames : appareil |
| UC-HUM-015 | moyen | couvert | **couvert** | IpsImmunizationCodecTest#freeTextVaccineWithoutCodeRoundTrips | PillarBoundaryRoundTripTest#b1 · RandomProfileInvariantsTest#r1 |  |
| UC-HUM-016 | bas | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 · PillarBoundaryRoundTripTest#b10 · RandomProfileInvariantsTest#r1 |  |
| UC-HUM-017 | moyen | — | **couvert** |  | PillarBoundaryRoundTripTest#b1 · RandomProfileInvariantsTest#r4 |  |
| UC-HUM-018 | bas | partiel | **partiel** | IpsImmunizationCodecTest#minimalEntryOmitsOptionalElements | PillarBoundaryRoundTripTest#b5 | nom fait d'espaces : appareil |
| UC-HUM-019 | haut | partiel | **partiel** | QrTextBudgetTest#ucQrText06 | TextQrAllLanguagesTest#t4 · TextQrAllLanguagesTest#t5 | T5 |
| UC-HUM-020 | haut | — | **appareil** |  |  |  |
| UC-HUM-021 | haut | — | **appareil** |  |  |  |
| UC-HUM-022 | haut | — | **jugement humain** |  |  |  |
| UC-HUM-023 | haut | partiel | **couvert** | QrTextBudgetTest#ucQrText05 | TextQrAllLanguagesTest#t5 · RandomProfileInvariantsTest#r4 |  |
| UC-HUM-024 | haut | — | **appareil** |  |  |  |
| UC-HUM-025 | moyen | partiel | **partiel** | PatientFormMergeTest#UC-PAT-011 (6 tests) | PillarBoundaryRoundTripTest#b11 | écran : appareil |
| UC-HUM-026 | moyen | — | **partiel** |  | TextQrAllLanguagesTest#t10 · PatientBirthDateBundleTest#d2 | écran : appareil |
| UC-HUM-027 | moyen | partiel | **partiel** | IpsFunctionalCodecTest#roundTripsAndStatuses | PersonaSeedIntegrityTest#p2 | T22 |
| UC-HUM-028 | haut | partiel | **partiel** | IpsProcedureDeviceCodecTest#deviceFullRoundTrip | PersonaSeedIntegrityTest#p2 | T11 |
| UC-ROB-001 | haut | partiel | **partiel** | ProfileFilesTest#atomicWriteReplacesAndLeavesNoTempFile ; #failedWriteKeepsThePreviousVersion | ProfileIdHostileListTest#h5 | tuer le processus pendant l'écriture : appareil |
| UC-ROB-002 | haut | — | **appareil** |  |  | X |
| UC-ROB-003 | haut | — | **appareil** |  |  |  |
| UC-ROB-004 | haut | — | **appareil** |  |  | X (README, reste n° 11 : pas de test de concurrence) |
| UC-ROB-005 | haut | — | **appareil** |  |  | X |
| UC-ROB-006 | haut | — | **appareil** |  |  |  |
| UC-ROB-007 | haut | — | **appareil** |  |  |  |
| UC-ROB-008 | haut | partiel | **partiel** | ProfileFilesTest#failedWriteKeepsThePreviousVersion |  | disque plein : appareil |
| UC-ROB-009 | haut | — | **appareil** |  |  |  |
| UC-ROB-010 | haut | — | **appareil** |  |  |  |
| UC-ROB-011 | moyen | — | **appareil** |  |  |  |
| UC-ROB-012 | moyen | — | **appareil** |  |  |  |
| UC-ROB-013 | bas | — | **appareil** |  |  |  |
| UC-ROB-014 | moyen | — | **appareil** |  |  |  |
| UC-ROB-015 | moyen | — | **appareil** |  |  |  |
| UC-ROB-016 | haut | — | **appareil** |  |  |  |
| UC-ROB-017 | bas | — | **appareil** |  |  | X ; voir SD-07 (l'identifiant « meta ») |
| UC-ROB-018 | haut | — | **appareil** |  |  |  |
| UC-ROB-019 | haut | — | **appareil** |  |  |  |
| UC-ROB-020 | moyen | — | **partiel** |  | PillarBoundaryRoundTripTest#b4 · PillarBoundaryRoundTripTest#b1 | codec : dose 0 écartée, très grands nombres conservés ; messages du formulaire : appareil |

## Testable en JVM, pas encore écrit

### Sans toucher au code principal (le test passerait)

- **UC-PAT-017** (haut) — PhoneNumberHelper.validate est pur ; l'enregistrement « quand même » est dans ContactFormBottomSheet (appareil)
- **UC-DDS-019** (haut) — abréviations (HTA, IRC) : test JVM d'une ligne, non écrit
- **UC-SOS-022** (moyen) — TtlPolicy est du Kotlin pur ; non examiné dans cette passe
- **UC-IMP-008** (bas) — les identifiants changent avec l'ordre des entrées (index dans la graine) : risque bas, non écrit
- **UC-FHIR-022** (moyen) — DeviceUseStatement dont le Device manque : facile à construire, non écrit
- **UC-I18N-013** (moyen) — IpsDecimal avec des chiffres arabes orientaux : test JVM d'une ligne, non écrit
- **UC-I18N-022** (moyen) — validité UCUM d'une unité saisie : non écrit

### Sans toucher au code principal, mais le test échouerait aujourd'hui

Le code de ces tests est dans `suspected-defects.md` quand le défaut a été relevé en écrivant les tests ;
sinon le cas renvoie au catalogue (« ⚠ code »).

- risque haut : UC-ALG-007, UC-DEV-021, UC-RES-015, UC-DDS-013, UC-DDS-014, UC-DDS-017, UC-QRT-006, UC-SOS-012, UC-IMP-013, UC-IMP-017, UC-FHIR-007
- risque moyen : UC-MED-005, UC-PRB-005, UC-PRB-017, UC-FON-017, UC-DDS-008, UC-DDS-009, UC-DDS-010, UC-QRT-013, UC-QRT-014, UC-QRF-007, UC-QRF-008, UC-QRF-009, UC-QRF-013, UC-FHIR-006, UC-FHIR-009, UC-FHIR-010, UC-FHIR-011, UC-FHIR-012, UC-HUM-005
- risque bas : UC-VAC-012, UC-DDS-012, UC-QRT-016, UC-QRF-010, UC-FHIR-013, UC-FHIR-014

### Après extraction de la logique hors d'une classe Android

- risque haut : UC-DDI-008, UC-ALM-004, UC-ALM-005, UC-ALM-008, UC-ALM-011
- risque moyen : UC-ALM-007, UC-ALM-009, UC-ALM-010, UC-ALM-017, UC-ALR-010, UC-ALR-012

De nombreux cas « appareil » du fichier 03 (UC-STO, UC-IMP, UC-MPR, UC-ROB) deviendraient testables en JVM avec
un `ProfileStore(dir, buildBundle)` extrait de `ProfilesRepository` : ils sont notés « X » dans la colonne de droite.

## Cas à risque haut sans aucun test JVM après cette passe

115 cas. Par statut :

- JVM à écrire (échouerait) (11) : UC-ALG-007, UC-DEV-021, UC-RES-015, UC-DDS-013, UC-DDS-014, UC-DDS-017, UC-QRT-006, UC-SOS-012, UC-IMP-013, UC-IMP-017, UC-FHIR-007
- JVM à écrire (2) : UC-PAT-017, UC-DDS-019
- JVM après extraction (5) : UC-DDI-008, UC-ALM-004, UC-ALM-005, UC-ALM-008, UC-ALM-011
- appareil (90) : UC-PAT-013, UC-PAT-016, UC-ALG-002, UC-ALG-014, UC-ALG-019, UC-MED-004, UC-MED-022, UC-VAC-020, UC-PRO-017, UC-DEV-019, UC-RES-004, UC-GRO-003, UC-DDI-001, UC-DDI-003, UC-DDI-004, UC-DDI-005, UC-DDI-009, UC-DDI-010, UC-ALM-001, UC-ALM-002, UC-ALM-003, UC-ALM-006, UC-ALM-012, UC-DDS-002, UC-DDS-005, UC-ALR-004, UC-ALR-009, UC-ALR-013, UC-QRT-019, UC-QRT-020, UC-PDF-001, UC-PDF-008, UC-SOS-001, UC-SOS-003, UC-SOS-007, UC-SOS-008, UC-SOS-016, UC-SOS-017, UC-SOS-018, UC-AI-001, UC-AI-004, UC-AI-005, UC-AI-006, UC-AI-007, UC-AI-008, UC-AI-011, UC-SCAN-001, UC-SCAN-003, UC-SCAN-004, UC-SCAN-005, UC-SCAN-006, UC-STO-003, UC-STO-006, UC-STO-008, UC-STO-009, UC-STO-010, UC-STO-016, UC-STO-018, UC-IMP-004, UC-IMP-005, UC-IMP-018, UC-IMP-019, UC-MPR-002, UC-MPR-003, UC-MPR-004, UC-MPR-005, UC-MPR-006, UC-MPR-007, UC-MPR-008, UC-MPR-009, UC-MPR-012, UC-MPR-014, UC-A11Y-007, UC-A11Y-009, UC-HUM-008, UC-HUM-011, UC-HUM-020, UC-HUM-021, UC-HUM-024, UC-ROB-002, UC-ROB-003, UC-ROB-004, UC-ROB-005, UC-ROB-006, UC-ROB-007, UC-ROB-009, UC-ROB-010, UC-ROB-016, UC-ROB-018, UC-ROB-019
- jugement humain (7) : UC-GRO-009, UC-DDS-015, UC-SOS-013, UC-AI-010, UC-SCAN-010, UC-HUM-012, UC-HUM-022
