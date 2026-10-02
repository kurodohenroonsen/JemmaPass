# Micro cas d'usage — JEMMA Pass

Catalogue dérivé du code (lecture, pas d'exécution) : **577 cas**, dont la majorité sans couverture
automatique aujourd'hui. Chaque cas a un identifiant stable, un type (nominal / alternatif / erreur /
limite), le résultat attendu, la couverture actuelle (test JVM, contrôle `verify_profiles`, étape
device) et le risque santé.

| Fichier | Périmètre | Cas |
|---|---|---|
| [01-pillar-editing.md](01-pillar-editing.md) | édition des 11 piliers | 219 |
| [02-safety-and-emergency.md](02-safety-and-emergency.md) | contrôles croisés, alertes, QR, PDF, SOS, assistant, scan | 178 |
| [03-data-integrity-and-people.md](03-data-integrity-and-people.md) | stockage, imports, multi-profils, conformité, i18n, accessibilité, diversité | 180 |

Chaque fichier se termine par « Trous de couverture prioritaires » et « Défauts probables repérés à
la lecture du code ». Règle de travail : un défaut n'est corrigé qu'après vérification dans le code
par l'intégrateur, avec un test qui porte l'identifiant du cas (`UC-…`).

## Défauts confirmés et corrigés

État de la preuve : tous les correctifs ci-dessous sont couverts par des tests unitaires JVM
(306 `@Test` au commit `178b379`). **Aucun n'a encore été vérifié sur appareil** — cycle device 24
à faire. Les tests sont sous `JemmaPassAndroidDemo/app/src/test/`.

| Cas | Défaut | Correctif | Commit | Test |
|---|---|---|---|---|
| UC-STO | Bundle FHIR en échec avalé : l'ancien Bundle regagnait sur la modification suivante | Bundle périmé supprimé, `_j` fait foi jusqu'à la prochaine sauvegarde réussie | `6ff4d31` | `ProfileFilesTest` |
| UC-STO | écritures non atomiques (profil tronqué si l'app est tuée) | `ProfileFiles.writeAtomic` (fichier temporaire + renommage) | `6ff4d31` | `ProfileFilesTest` |
| UC-STO | deux sauvegardes rapprochées s'écrasaient | verrou unique sur lecture-modification-écriture | `6ff4d31` | — (pas de test de concurrence) |
| UC-STO | `sid` d'un QR utilisé tel quel comme nom de fichier | `ProfileFiles.safeIdOrNull` | `6ff4d31` | `ProfileFilesTest` |
| UC-AI | médicament introuvable → `is_clean=true` | `is_clean=false`, `checked=false`, avertissement explicite | `6ff4d31`, repris dans `KbSafety` (`513b637`) | `KbSafetyTest` |
| UC-SAFE-KB | KB absente ou médicament non résolu : les contrôles répondaient « rien à signaler » | `KbSafety` : verdict NOT_CHECKED / INCOMPLETE / CLEAN / ALERT ; seul CLEAN autorise « rien à signaler » | `513b637` | `KbSafetyTest` |
| UC-SAFE-KB | interaction : sévérité = première ligne lue | la ligne la plus sévère de la paire est gardée | `513b637` | `KbSafetyTest` |
| UC-SAFE-UI | « non vérifié » invisible sur la fiche profil | bandeau ambre, avec le nombre d'entrées non vérifiées | `ee830d5`, `178b379` | `SafetyBannerDecisionTest`, `SafetyBannerUnverifiedCountTest` |
| UC-SAFE-UI | badge vert du radar affiché sans contrôle complet | vert seulement si verdict CLEAN et contrôle complet | `178b379` | `LiveScanVerdictBadgeTest` |
| UC-SAFE-UI | formulaire allergie : enregistrement silencieux quand le contrôle n'a pas tourné | dialogue « contrôle incomplet » | `178b379` | `AllergyFormSafetyLogicTest` |
| UC-SAFE-SCAN | scan live, phrase vocale : « sûr » dit sans vérification ; exception = rien à signaler | rien ne dit « sûr » hors verdict CLEAN ; exception → NOT_CHECKED ; phrase TTS choisie d'après le verdict | `ee830d5` | `ScanSafetyTest`, `MedScanSafetyTest` |
| UC-SAFE-SCAN | formulaire médicament : `null` = « aucune interaction » ; médicaments sans code ignorés sans le dire | `FormCrossCheckOutcome` (rien à vérifier / échec / terminé) ; médicaments sans code nommés, dialogue « contrôle incomplet » | `513b637`, `ee830d5` | `FormCrossCheckOutcomeTest`, `MedicationFormLogicTest`, `MedicationFormSafetyVerdictTest` |
| UC-SAFE-SCAN | scan de médicament : étape de contrôle marquée OK sans appel d'outil | OK seulement si l'outil a été appelé pendant ce scan ; sinon NON VÉRIFIÉ et le texte du modèle est écarté (voir reste n° 1) | `178b379` | `MedScanStepSafetyTest` |
| UC-SAFE-SCAN | outils d'explication : le JSON donné au modèle ne disait pas si le contrôle avait tourné | champs `verdict`, `checked`, `instruction` | `178b379` | `ExplainSafetyTest` |
| UC-QR-TEXT | QR texte : plafond 2200 octets pour une trame de 1800 | plafond 1800 octets UTF-8, lignes retirées par rang de section, marqueur `✂️ …` ; contacts d'urgence ajoutés | `513b637` | `QrTextBudgetTest` |
| UC-QR-FRAME | QR multi-trames jamais réassemblé | `JemmaQrFrameAssembler` branché dans les deux écrans de scan | `513b637` | `QrFrameAssemblerTest` |
| UC-PDF | allergies au-delà de 3 perdues ; criticité inconnue imprimée « L » | tri par criticité, « ? » pour l'inconnue, ligne « +N » | `513b637` | `PdfPillarLayoutTest` |
| UC-ALG | une seule réaction conservée à l'édition | toutes les réactions conservées | `513b637` | `AllergyFormMergeTest` |
| UC-ALG / UC-MED / UC-PAT | double appui sur Enregistrer → doublon | `SingleShotGuard` (la logique est testée, pas son branchement dans les écrans) | `513b637` | `FormEditGuardsTest` |
| UC-ALG / UC-MED / UC-PAT | dates dans le futur acceptées | `IsoDateRules.isFuture` | `513b637` | `FormEditGuardsTest` |
| UC-PAT | listes d'adresses / contacts / identifiants tronquées ; date de naissance complète obligatoire | `PatientFormMerge` ; année seule ou année-mois acceptée | `513b637` | `PatientFormMergeTest`, `FormEditGuardsTest` |
| UC-FHIR | `MedicationStatement.status` toujours `active` ; dose à virgule absente du FHIR | statut lu dans `md[].ms` ; virgule lue comme séparateur décimal | `513b637` | `FhirMedicationBundleTest` |
| UC-MED-ROUTE | voie « inhalée » convertie en injection | voie `H` = SNOMED 447694001 (catalogue, Bundle, hydrateur) | `ee830d5`, `178b379` | `MedicationRouteTest`, `MedicationRouteEnumTest` |
| UC-FHIR / UC-BLOOD | Observation 882-1 désynchronisée après import, doublons | une seule 882-1, alignée sur `p.bt`, reconnue par son contenu | `513b637`, `ee830d5` | `IpsBundleConsistencyTest`, `IpsBloodGroupSyncTest`, `IpsBloodGroupTest` |
| UC-BLOOD | résultat 882-1 contredisant le groupe du profil accepté | refusé à la saisie (formulaire résultats) ; conflits renvoyés par `reconcile` | `178b379` | `ResultBloodGroupGuardTest`, `BloodGroupConflictTest` |
| UC-FHIR | Observations de grossesse relues aussi comme résultats | les deux piliers restent disjoints après aller-retour | `513b637` | `IpsBundleConsistencyTest` |

## Préfixes de cas utilisés dans les tests

| Préfixe | Sujet | Classes de test |
|---|---|---|
| UC-SAFE-KB | verdict des contrôles KB | `KbSafetyTest` |
| UC-SAFE-UI | bandeau fiche, badge radar, formulaire allergie | `SafetyBannerDecisionTest` (01–09), `SafetyBannerUnverifiedCountTest` (10–12), `LiveScanVerdictBadgeTest` (20–23), `AllergyFormSafetyLogicTest` (24–29) |
| UC-SAFE-SCAN | scan live, scan de médicament, voix, formulaire médicament, explications | `ScanSafetyTest` (01–05), `MedScanSafetyTest` (11–14), `FormCrossCheckOutcomeTest` (21–24), `MedScanStepSafetyTest` (30–36), `ExplainSafetyTest` (37) |
| UC-QR-TEXT | budget du QR texte (cite aussi UC-QRT du catalogue) | `QrTextBudgetTest` |
| UC-QR-FRAME | réassemblage multi-trames (cite aussi UC-QRF) | `QrFrameAssemblerTest` |
| UC-PDF | ordre, débordement, criticité | `PdfPillarLayoutTest` |
| UC-FHIR | statut et dose des médicaments, 882-1, grossesse | `FhirMedicationBundleTest` (008, 024, 026), `IpsBundleConsistencyTest` (025, 027) |
| UC-BLOOD | groupe sanguin | `IpsBloodGroupSyncTest` (01–06), `IpsBloodGroupTest` (03), `BloodGroupConflictTest` (10–12), `ResultBloodGroupGuardTest` (13–16) |
| UC-MED-ROUTE | voie inhalée ; verdict à l'enregistrement d'un médicament | `MedicationRouteTest` (01–04), `MedicationFormSafetyVerdictTest` (10–14), `MedicationRouteEnumTest` (20–22) |

Les préfixes du catalogue (UC-ALG, UC-MED, UC-PAT, UC-HUM, UC-STO) sont portés par
`AllergyFormMergeTest`, `MedicationFormLogicTest`, `PatientFormMergeTest`, `FormEditGuardsTest`
et `ProfileFilesTest`.

## À vérifier puis corriger (ordre de risque)

Vérifié dans le code au commit `178b379`.

1. Scan de médicament : le contrôle dépend toujours de l'appel de l'outil par le modèle. L'absence
   d'appel est détectée (étape NON VÉRIFIÉ), le contrôle n'est pas forcé par le code.
2. `FormCrossCheckHelper.checkNewMedicationAgainstProfile` (ancienne méthode) renvoie toujours
   `null` aussi bien pour « rien à vérifier » que pour une exception. Les nouveaux appelants passent
   par `checkNewMedication`.
3. Médicaments enregistrés sans code : ils sont maintenant signalés, mais toujours pas comparés.
4. Groupe sanguin : un résultat 882-1 saisi à la main sans date ni détail est indiscernable d'une
   copie dérivée (`IpsBloodGroup.looksDerived`) ; hors formulaire (import), il est remplacé par la
   valeur du profil.
5. Groupe sanguin : `ProfilesRepository.bloodGroupConflictFlow` n'est lu par aucun écran au
   commit `178b379` : un conflit résolu à l'écriture (import) est seulement journalisé. Un
   branchement dans `ResultsEditFragment` est en cours, non commité.
6. QR multi-trames : les trames ne portent ni identifiant de lot ni somme de contrôle. Deux lots de
   même taille peuvent se mélanger ; l'erreur n'apparaît qu'au décodage.
7. `QrViewerFragment` : le défilement automatique ne démarre que pour le canal FHIR.
8. PDF : noms tronqués (16 à 22 caractères) ; seulement JA / FR / EN ; allergies inactives imprimées
   comme les actives ; budgets de lignes (`COLUMN1_ROWS` = 13, `MEDICATION_ROWS` = 18) non vérifiés
   visuellement.
9. `JPatient.bd` : une date partielle (année seule, année-mois) est imprimée telle quelle dans le PDF
   et le QR texte.
10. Traductions de/nl/ja/zh des nouveaux textes de sécurité non relues par un locuteur natif ; les
    autres langues retombent sur le texte par défaut.
11. `SingleShotGuard` et le verrou d'écriture : pas de test du branchement dans les écrans ni de test
    de concurrence.
12. Pilier ♿ : pas d'outil Gemma (`getFocusProfile…`) pour l'état fonctionnel.
