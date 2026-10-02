# 03 — Intégrité des données et diversité des personnes

Catalogue de micro cas d'usage dérivé de la lecture du code (octobre 2026, branche courante).
Sources lues : `profiles/ProfilesRepository.kt`, `ips/IpsFhirCodec.kt`, `ips/Ips*.kt`, `qr/JemmaProfileJ.kt`,
`qr/JemmaFhirBundleBuilder.kt`, `qr/JemmaPayloadCodec.kt`, `qr/JemmaPayloadPruner.kt`, `qr/JemmaTextPayloadBuilder.kt`,
`kb/KbTranslations.kt`, fragments `ui/profile/**`, `ui/profiles/**`, `ui/assistant/AssistantMultiPreviewFragment.kt`,
layouts `*edit*.xml` / `bottom_sheet_*`, `docs/ips-native-pillars.md`, `qa/device/verify_profiles.py`, `qa/device/README.md`,
tests JVM `app/src/test/**`.

Rappels de lecture :

- Deux fichiers par profil : `{id}.json` (`_j 1.2`, piliers hérités + projection) et `{id}.fhir.json` (Bundle, source de vérité des 8 piliers natifs : `im pr dv rs ph cn pg fs`).
- Lecture : Bundle d'abord, repli pilier par pilier sur `_j` quand le pilier est vide dans le Bundle (`readNativePillars`, `ifEmpty`).
- Écriture : `writeProfileFiles` écrit le JSON puis le Bundle ; un échec du Bundle est avalé (log seulement).
- `sourceFormat` : `MANUAL_EDIT` / `ASSISTANT_*` → Bundle autoritaire ; `DEMO_SEED` → seeder ; tout le reste (`COMPRESSED`, `LEGACY`, `UNKNOWN`) → le `_j` entrant gagne.
- Seul appelant d'import : `QrImportScanFragment` (`result.format.name`). Aucun chemin mesh n'appelle `saveProfile` (le mesh/SOS lit les fichiers, il n'importe pas).
- Aucun test JVM ne couvre `ProfilesRepository` (dépend d'Android) : tout ce qui est « dépôt » n'est couvert que par les étapes appareil T1…T22 et `verify_profiles.py`.
- `minSdk = 31` (Android 12), `allowBackup = false`, stockage dans `getExternalFilesDir()/profiles`.

Abréviations de couverture : `ImmCodec` = `ips/IpsImmunizationCodecTest.kt`, `ImmProj` = `ips/IpsImmunizationProjectionTest.kt`,
`ProcDev` = `ips/IpsProcedureDeviceCodecTest.kt`, `Result` = `ips/IpsResultCodecTest.kt`, `Past` = `ips/IpsPastProblemCodecTest.kt`,
`Problem` = `ips/IpsProblemCodecTest.kt`, `Preg` = `ips/IpsPregnancyCodecTest.kt`, `Func` = `ips/IpsFunctionalCodecTest.kt`,
`Blood` = `ips/IpsBloodGroupTest.kt`, `PastQr` = `qr/PastProblemsTextQrTest.kt`, `PregQr` = `qr/PregnancyTextQrTest.kt`,
`QrCodec` = `qr/JemmaQrCodecUnitTest.kt`. `P1…P8` = contrôles de `verify_profiles.py`. `T1…T22` = étapes de `qa/device/README.md`.

## 1. Stockage et modèle à deux fichiers (UC-STO)

| ID | Situation réelle (qui, pourquoi) | Type | Étapes | Résultat attendu | Couverture actuelle | Risque santé si ça casse |
|---|---|---|---|---|---|---|
| UC-STO-001 | Patient crée son premier profil | nominal | Identité → Enregistrer | `{id}.json` + `{id}.fhir.json` créés, `sid` = nom de fichier | P1, P7 (personas seulement) ; création manuelle : **AUCUNE** | moyen |
| UC-STO-002 | Patient ajoute un vaccin | nominal | Vaccins → + → Save | Bundle : 1 Immunization de plus ; `_j.im` reprojeté ; autres piliers intacts | T2, P4, P5, P6 | moyen |
| UC-STO-003 | Patient modifie une allergie alors qu'il a des vaccins | nominal | Allergies → éditer → Save | Les 8 piliers natifs restent ceux du Bundle | T4 (vaccins seulement), P4 | haut |
| UC-STO-004 | Idem, mais vérifier les 7 autres piliers natifs | alternatif | Éditer allergie sur Haru (pr dv rs ph cn pg fs peuplés) | Tous les compteurs inchangés, mêmes `id` | T13 partiel (im + pr) ; dv rs ph cn pg fs : **AUCUNE** | haut |
| UC-STO-005 | Édition d'un pilier natif n'efface pas un autre natif | nominal | Ajouter un résultat sur Haru | im pr dv ph cn pg fs identiques avant/après (codes et ids) | P4 par pilier après chaque T ; pas de comparaison d'ids : partiel | haut |
| UC-STO-006 | Édition d'identité n'efface ni allergies ni traitements | nominal | Identité → changer téléphone → Save | `al`, `md`, `ct` inchangés | **AUCUNE** | haut |
| UC-STO-007 | Détails « Bundle seulement » survivent à une édition d'un pilier hérité | nominal | Vaccin avec lot, fabricant, série 2/3 ; puis éditer un contact | lot, fabricant, `seriesDoses`, vaccinateur toujours dans le Bundle | **AUCUNE** | moyen |
| UC-STO-008 | Écriture du Bundle échoue, JSON réussi (KB absente, date de naissance non ISO importée, `use` d'adresse inconnu) | erreur | Provoquer une exception dans `hydrate`/`build` puis sauver un vaccin | L'utilisateur est averti ; la paire reste cohérente | **AUCUNE** | haut |
| UC-STO-009 | Après UC-STO-008, nouvelle édition d'un pilier natif | erreur | Sauver un 2e vaccin | Le 1er vaccin (présent dans `_j` seul) n'est pas perdu | **AUCUNE** (le code relit l'ancien Bundle : perte probable) | haut |
| UC-STO-010 | Après UC-STO-008, suppression du dernier élément d'un pilier | limite | Supprimer le seul dispositif alors que le Bundle n'a pas pu être réécrit | Le dispositif ne « ressuscite » pas au rechargement | **AUCUNE** | haut |
| UC-STO-011 | Pilier vide dans le Bundle, présent dans `_j` (profil antérieur au pilier natif) | alternatif | Bundle sans Procedure + `_j.pr` peuplé → ouvrir l'écran | Les entrées `_j` sont affichées, puis migrées au prochain Save | `ProcDev::legacyJArraysRebuildDeterministically`, `Past::legacyJArrayRebuild` (reconstruction pure) ; repli dépôt : **AUCUNE** | moyen |
| UC-STO-012 | Pilier présent dans le Bundle ET différent dans `_j` | limite | Modifier `_j.im` à la main, Bundle intact, recharger | Le Bundle gagne (édition locale) | **AUCUNE** | moyen |
| UC-STO-013 | Suppression de tous les éléments d'un pilier | limite | Supprimer les 4 vaccins de Kurodo | Bundle sans Immunization ni section ; `_j.im` vide ; rien ne revient | T21 étape 5 (pg), T22 étape 4 (fs), P5 « no section » ; im/pr/dv/rs/ph/cn à zéro : **AUCUNE** | moyen |
| UC-STO-014 | `{id}.fhir.json` absent (supprimé, jamais écrit) | alternatif | Effacer le fichier, ouvrir le profil, sauver | Piliers reconstruits depuis `_j` ; Bundle régénéré | **AUCUNE** | moyen |
| UC-STO-015 | `{id}.fhir.json` illisible (JSON tronqué) | erreur | Tronquer le fichier, ouvrir Vaccins | Repli sur `_j`, pas de crash, Bundle régénéré au Save | `ImmCodec::parseBundleRejectsGarbage` (parseur seul) ; dépôt : **AUCUNE** | moyen |
| UC-STO-016 | `{id}.json` corrompu, Bundle intact | erreur | Tronquer le JSON, relancer l'app | Profil signalé comme endommagé et récupérable | **AUCUNE** (le code l'ignore : il disparaît de la liste sans message) | haut |
| UC-STO-017 | `sid` absent dans le JSON | alternatif | Retirer `sid`, ouvrir, éditer un contact | Pas de doublon ; `sid` réinjecté = nom de fichier | P7 après écriture ; réparation au chargement : **AUCUNE** | moyen |
| UC-STO-018 | `sid` du JSON ≠ nom de fichier (fichier copié/renommé) | limite | Copier `a.json` en `b.json`, ouvrir `b`, éditer | Écrit dans `b`, jamais dans `a` | **AUCUNE** | haut |
| UC-STO-019 | Fichier copié : les ids des ressources et URN | limite | Suite de UC-STO-018, lire le Bundle de `b` | URN recalculés avec le sid `b`, ids d'entrées conservés | **AUCUNE** | bas |
| UC-STO-020 | Identifiants stables entre deux sauvegardes | nominal | Sauver deux fois sans changer | Mêmes `fullUrl`, mêmes `id` | `ImmCodec::bundleUrnsAreStableAcrossRebuilds`, `stableUrnsAreDeterministic`, P3 | moyen |
| UC-STO-021 | Ids changent quand on supprime une entrée d'un profil reconstruit depuis `_j` | limite | Profil importé (ids dérivés de l'index), supprimer l'entrée 0, sauver, relire | Les entrées restantes gardent leurs ids (elles sont désormais dans le Bundle) | **AUCUNE** | bas |
| UC-STO-022 | Entrée dupliquée à l'écran d'édition | erreur | Éditer une entrée dont l'id n'est plus dans la liste (mode EDIT → `add`) | Pas de doublon silencieux | **AUCUNE** | moyen |
| UC-STO-023 | Groupe sanguin miroir | nominal | Identité : groupe O+ → Save | 1 seule Observation 882-1 dérivée en tête des résultats | `Blood::syncKeepsExactlyOneBloodGroupResult`, P6c | haut |
| UC-STO-024 | Groupe sanguin retiré de l'identité | alternatif | Mettre « — » → Save | L'Observation dérivée disparaît ; un 882-1 saisi à la main reste | `Blood::syncKeepsExactlyOneBloodGroupResult` | haut |
| UC-STO-025 | Groupe sanguin saisi à la main ≠ identité (A+ vs O+) | limite | `p.bt` = A+, résultat 882-1 manuel = O+ | Contradiction signalée ; aujourd'hui les deux valeurs coexistent (`p.bt` en QR texte, O+ dans le Bundle) | **AUCUNE** | haut |
| UC-STO-026 | Groupe sanguin importé non reconnu (« O pos », « 0+ ») | limite | Importer `bt` = `0+` (zéro) | Pas d'Observation fausse ; libellé brut conservé | `Blood::bloodTypeLabelsNormalise…` (formes connues) ; zéro/inconnu : **AUCUNE** | haut |
| UC-STO-027 | Suppression d'un profil non courant | nominal | Corbeille → confirmer | Les 2 fichiers supprimés ; courant inchangé | **AUCUNE** | moyen |
| UC-STO-028 | Suppression d'un persona de démonstration | limite | Supprimer Haru, tuer l'app, relancer | Le persona ne revient pas sans action de l'utilisateur | **AUCUNE** (le code le re-sème au démarrage) | bas |
| UC-STO-029 | Premier lancement : semis des 3 personas | nominal | Installer, ouvrir | 3 profils, 2 fichiers chacun, pas d'écrasement aux lancements suivants | P1, P4b (`run_device_qa.sh`) | bas |
| UC-STO-030 | Re-semis manuel après modification d'un persona | alternatif | Modifier Kurodo, Réglages → semer les personas | L'utilisateur est prévenu que ses modifications sont écrasées | **AUCUNE** | bas |
| UC-STO-031 | Résumé de la liste ≠ contenu réel | limite | Bundle à 5 vaccins, `_j.im` à 4 (écriture JSON ratée) | Compteurs de la liste cohérents avec l'écran du pilier | **AUCUNE** | bas |

## 2. Imports (UC-IMP)

| ID | Situation réelle (qui, pourquoi) | Type | Étapes | Résultat attendu | Couverture actuelle | Risque santé si ça casse |
|---|---|---|---|---|---|---|
| UC-IMP-001 | Aidant scanne le QR `_j2:` d'un proche | nominal | Scanner → confirmer | Nouveau profil, id = `sid` du QR, Bundle reconstruit depuis `_j` | **AUCUNE** (encodeur/décodeur `_j2:` sans test JVM ; `QrCodec` ne teste que le découpage et le bitmap) | haut |
| UC-IMP-002 | QR ancien format JSON brut `{"_j":"1.2"…}` | alternatif | Scanner | Importé, format `LEGACY` | `ImmProj::legacyPayloadWithoutTheNewKeysStillParses` (Moshi seul) | moyen |
| UC-IMP-003 | QR sans `sid` (très ancien) | alternatif | Scanner deux fois le même QR | 2e scan : mise à jour, pas de doublon | **AUCUNE** (le code crée un UUID à chaque fois : doublon) | moyen |
| UC-IMP-004 | Re-scan d'un profil déjà présent et modifié localement | limite | Ajouter lot + vaccinateur localement, re-scanner l'ancien QR | L'utilisateur est averti que l'import écrase ses modifications | **AUCUNE** (dialogue identique à un premier import ; toast « mis à jour » après coup) | haut |
| UC-IMP-005 | Import : pilier vide dans le QR, peuplé localement | limite | Local : 3 dispositifs ; QR sans `dv` | Règle « import gagne » : dispositifs effacés, et l'utilisateur le sait | **AUCUNE** | haut |
| UC-IMP-006 | Import : détails absents de `_j` | limite | Changer de téléphone par QR | Perte attendue de : lot, fabricant, série, UDI, n° de série, site, issue, lieu, laboratoire ; documentée à l'écran | `ImmProj::rebuildFromProjectionPreservesWhatTheQrCarries` (ce qui survit) ; avertissement : **AUCUNE** | moyen |
| UC-IMP-007 | Import identique deux fois → mêmes ids FHIR | nominal | Scanner 2× | Ids et URN identiques | `ImmProj::rebuildIsDeterministic`, `Past::projectionIsDeterministic` | bas |
| UC-IMP-008 | Import d'un QR dont l'ordre des entrées a changé | limite | Même contenu, ordre différent | Mêmes ressources ; aujourd'hui l'index entre dans la graine : ids différents | **AUCUNE** | bas |
| UC-IMP-009 | `sid` hostile ou malformé (`../x`, `a/b`, `demo_haru`, `x.fhir`) | erreur | Forger un QR | Refus ou nettoyage ; jamais d'écriture hors dossier ni d'écrasement d'un autre profil | **AUCUNE** | haut |
| UC-IMP-010 | QR non JEMMA (billet, URL) | erreur | Scanner | Dialogue d'erreur, scan reprend | **AUCUNE** | bas |
| UC-IMP-011 | QR `_j2:` tronqué / base64 invalide | erreur | Scanner un QR abîmé | Échec propre, rien d'écrit | **AUCUNE** | bas |
| UC-IMP-012 | `_j` de version autre que 1.2 | erreur | Scanner `_j: "1.3"` | Refus explicite, message compréhensible | **AUCUNE** | moyen |
| UC-IMP-013 | Import avec date de naissance non ISO (`1956`, `05/02/1956`) | limite | Scanner | Profil lisible ; Bundle valide ou date omise | **AUCUNE** (`FhirDate.fromString` dans le try du Bundle : Bundle potentiellement jamais écrit) | haut |
| UC-IMP-014 | Import avec entrée grossesse au code inconnu | limite | `pg[].c` hors liste | Entrée conservée ou rejet signalé ; aujourd'hui elle est supprimée sans message | **AUCUNE** | moyen |
| UC-IMP-015 | Import d'un résultat portant un code de grossesse (ex. 82810-3 dans `rs`) | limite | Forger, importer, sauver 3 fois | 1 seule Observation ; pas de croissance du pilier grossesse | **AUCUNE** | moyen |
| UC-IMP-016 | Import avec statut inconnu (`st: "xyz"`) | limite | Scanner | Normalisé sur le défaut du pilier | `ImmCodec::statusIsNormalized`, `Result::statusInterpretationAndCategoryNormalise`, `Past::statusNormalizationKeepsOnlyPastStatuses`, `Problem::statusNormalization` | moyen |
| UC-IMP-017 | Import d'un problème « résolu » rangé dans `cn` | limite | `cn[].st = resolved` | Ne devient pas « actif » en silence | **AUCUNE** (`IpsProblemStatus.normalize` renvoie `active`) | haut |
| UC-IMP-018 | Médicaments ajoutés par l'assistant (photo d'ordonnance) | nominal | Traitements → assistant → cocher → Save | `md` augmenté ; piliers natifs intacts (Bundle autoritaire) | **AUCUNE** | haut |
| UC-IMP-019 | Assistant : cible implicite | erreur | Arriver au Save sans cible de handoff, avec un autre profil courant | Refus ; jamais d'écriture dans le profil courant d'une autre personne | **AUCUNE** (repli sur `currentProfileId`) | haut |
| UC-IMP-020 | Assistant : élément non résolu dans la KB | alternatif | Cocher un médicament « non identifié » | Non enregistré, et l'utilisateur le voit | **AUCUNE** | moyen |
| UC-IMP-021 | Assistant : même médicament ajouté deux fois | limite | Scanner deux fois la même boîte | Doublon signalé | **AUCUNE** | moyen |
| UC-IMP-022 | Profil reçu par mesh/SOS | alternatif | Secouriste reçoit une fiche | Affichage seul, rien n'est écrit dans `profiles/` | `sos/JemmaSosChunkCodecTest` (codec) ; non-écriture : **AUCUNE** | moyen |
| UC-IMP-023 | Canal QR FHIR : Bundle construit sans piliers stockés | alternatif | Onglet FHIR | Piliers reconstruits depuis la projection | `Problem::withoutNativePillarsTheBuilderFallsBackOnTheProjection`, T6 | bas |

## 3. Multi-profils (UC-MPR)

| ID | Situation réelle (qui, pourquoi) | Type | Étapes | Résultat attendu | Couverture actuelle | Risque santé si ça casse |
|---|---|---|---|---|---|---|
| UC-MPR-001 | Parent tient son profil + 2 enfants | nominal | Créer 3 profils | 3 paires de fichiers, ids distincts | **AUCUNE** | moyen |
| UC-MPR-002 | Choisir le profil courant (étoile) | nominal | Étoile sur « moi » | `currentProfileId` persistant, widget mis à jour | **AUCUNE** | haut |
| UC-MPR-003 | Premier vrai profil avec les 3 personas déjà semés | limite | Installer, créer son profil | Son profil devient courant (widget d'urgence) | **AUCUNE** (condition `size == 1` jamais vraie avec les personas) | haut |
| UC-MPR-004 | Retirer l'étoile | alternatif | Re-taper l'étoile | Aucun profil courant ; widget vide, pas la fiche d'un autre | **AUCUNE** | haut |
| UC-MPR-005 | Supprimer le profil courant | limite | Supprimer « moi » | Aucune promotion silencieuse d'une autre personne | **AUCUNE** (le code promeut le plus récemment modifié) | haut |
| UC-MPR-006 | Supprimer le profil courant alors que seul un persona reste | limite | Idem | Le widget ne montre pas Haru comme si c'était le porteur | **AUCUNE** | haut |
| UC-MPR-007 | Éditer un enfant pendant que « moi » est courant | nominal | Ouvrir enfant → Vaccins → + | Écrit dans le fichier de l'enfant (argument `profileId`) | **AUCUNE** | haut |
| UC-MPR-008 | Écran d'édition ouvert sans `profileId` | erreur | Navigation directe / deep link | Jamais d'écriture dans le profil courant par défaut | **AUCUNE** (`argProfileId ?: currentProfileId`) | haut |
| UC-MPR-009 | Deux profils au même nom (père et fils homonymes) | limite | Créer « Jean Dupont » deux fois | Distinguables dans la liste (date de naissance visible) | **AUCUNE** | haut |
| UC-MPR-010 | Tri de la liste après édition | alternatif | Éditer le 3e profil | Remonte en tête ; l'étoile ne bouge pas | **AUCUNE** | bas |
| UC-MPR-011 | Profil courant dont le fichier a disparu | erreur | Effacer le fichier du profil courant | Courant remis à zéro, message | **AUCUNE** | moyen |
| UC-MPR-012 | Exporter le QR du bon profil | nominal | Fiche enfant → Export | QR de l'enfant, pas du courant | T5 (un seul profil) ; multi : **AUCUNE** | haut |
| UC-MPR-013 | Aidant professionnel avec 15 profils | limite | Importer 15 QR | Liste fluide, rescans acceptables | **AUCUNE** | bas |
| UC-MPR-014 | Import d'un proche qui porte le même `sid` qu'un profil local | limite | QR dont `sid` = id local d'une autre personne | Conflit détecté sur l'identité (nom, naissance) avant écrasement | **AUCUNE** | haut |
| UC-MPR-015 | Personas de démonstration mêlés aux vraies personnes | limite | Ouvrir la liste en urgence | Personas marqués « démo » sans ambiguïté | **AUCUNE** | moyen |

## 4. Conformité HL7 IPS (UC-FHIR)

| ID | Situation réelle (qui, pourquoi) | Type | Étapes | Résultat attendu | Couverture actuelle | Risque santé si ça casse |
|---|---|---|---|---|---|---|
| UC-FHIR-001 | Médecin étranger ouvre le Bundle | nominal | Lire `{id}.fhir.json` | `document`, Composition en tête, 1 Patient | P2, `ImmCodec::bundleTypeIsDocumentWithCompositionFirst` | moyen |
| UC-FHIR-002 | Références internes résolubles | nominal | Vérifier tous les `fullUrl` | `urn:uuid` v3, uniques | P3, P3b | moyen |
| UC-FHIR-003 | Section par pilier présente si et seulement si peuplé | nominal | 8 piliers | Section LOINC ⇄ ressources | P5, `Past::noSectionWhenThePillarIsEmpty`, `ImmCodec::bundleWithoutNativePillarsHasNoImmunizationSection` | moyen |
| UC-FHIR-004 | Projection `_j` ⇄ ressources | nominal | Comparer codes | 1 entrée par ressource | P4, P4b | haut |
| UC-FHIR-005 | Profils `*-uv-ips`, référence patient, statut, date | nominal | Par ressource | Conformes | P6, tests `…JsonCarriesIpsEssentials` | moyen |
| UC-FHIR-006 | Sections IPS obligatoires quand le profil est vide | limite | Profil sans allergie ni traitement ni problème | Sections 48765-2 / 10160-0 / 11450-4 présentes avec « aucune information » | **AUCUNE** (P8 ne vérifie que si non vide ; le code omet la section) | moyen |
| UC-FHIR-007 | « Pas d'allergie connue » ≠ « non renseigné » | limite | Patient sûr de n'avoir aucune allergie | Distinction portée dans le Bundle et le QR texte | **AUCUNE** | haut |
| UC-FHIR-008 | Traitement arrêté | alternatif | `md[].ms = stopped` | `MedicationStatement.status = stopped` | **AUCUNE** (statut toujours `active` dans le builder) | haut |
| UC-FHIR-009 | Intolérance (pas allergie) | alternatif | `al[].tp = intolerance` | `type = intolerance` | **AUCUNE** (type toujours `allergy`) | moyen |
| UC-FHIR-010 | Allergie codée hors SNOMED (ATC, RxNorm) | alternatif | `al[].cs` = ATC | `coding.system` = ATC | **AUCUNE** (système toujours SNOMED) | moyen |
| UC-FHIR-011 | Allergie sans code (texte libre) | limite | Allergie « savon de grand-mère » | `code.text` seul, pas de `coding.code` vide | **AUCUNE** | moyen |
| UC-FHIR-012 | Réactions, date de début, catégorie d'allergie | alternatif | Allergie complète | Présentes dans le Bundle | **AUCUNE** | moyen |
| UC-FHIR-013 | Identifiants patient (NISS, passeport) | alternatif | Renseigner un identifiant | `Patient.identifier` émis | **AUCUNE** (non émis par le builder) | bas |
| UC-FHIR-014 | Contact d'urgence avec téléphone seul | limite | Contact sans nom | Pas de `name.text` vide | **AUCUNE** | bas |
| UC-FHIR-015 | Date inconnue | limite | Vaccin / intervention sans date | `occurrenceString` / `performedString` « unknown » ; résultat : data-absent-reason | `ImmCodec::unknownDateBecomesOccurrenceStringAndComesBackNull`, `ProcDev::procedureUnknownDateAndFreeTextAndStatuses`, `Result::undatedAndUnattributedResultsStillMeetTheIpsCardinalities`, T8, T12 | moyen |
| UC-FHIR-016 | Date partielle `YYYY` / `YYYY-MM` | limite | Césarienne « 1975 » | Conservée telle quelle | `ImmCodec::partialDatesAreKeptAsIs`, `Past::partialDatesSurvive` | moyen |
| UC-FHIR-017 | Imagerie sans jour précis | limite | Radio « 2019 » | Profil générique, pas radiologie | `Result::imagingWithoutDayPreciseDateUsesTheGenericResultsProfile` | bas |
| UC-FHIR-018 | Trois piliers Condition ne se mélangent pas | nominal | ph + cn + fs peuplés | Chacun dans sa section, relecture disjointe | `Func::threeConditionPillarsNeverMix`, `Problem::problemsAndPastProblemsLiveInTheirOwnSections`, `Past::activeProblemListConditionsAreNotPastProblems` | haut |
| UC-FHIR-019 | Grossesse ne fuit pas dans les résultats | nominal | Haru | 🧪 inchangé | `Preg::bundleSectionAndNoLeakIntoResults`, `Result::otherObservationKindsAreLeftToTheirOwnPillars`, T20 | moyen |
| UC-FHIR-020 | Bundle hérité : Conditions sans `id` | limite | Lire un Bundle d'avant sprint 5 | Problèmes : id stable ; antécédents : id stable aussi | `Problem::legacyProblemListConditionsAreReadWithAStableId` ; antécédents/vaccins sans id (id aléatoire à chaque lecture) : **AUCUNE** | bas |
| UC-FHIR-021 | Id de ressource hors `[A-Za-z0-9.-]{1,64}` | limite | `sid` avec `_` (personas), id long | Id nettoyé ; relecture cohérente | `Blood` (id dérivé) ; `fhirId` non appliqué à im/pr/dv/ph/cn/fs : **AUCUNE** | bas |
| UC-FHIR-022 | Dispositif : Device introuvable | erreur | DeviceUseStatement dont la référence ne résout pas | Entrée gardée (statut), pas de crash | P6b (présence) ; relecture dégradée : **AUCUNE** | moyen |
| UC-FHIR-023 | Validateur HL7 officiel | nominal | `validate_all.sh` | 0 erreur sur les 3 personas | T6, T20, T22 (appareil, réseau) | moyen |
| UC-FHIR-024 | Dose de médicament à virgule (« 0,5 ») | limite | Saisir 0,5 mg | `doseQuantity` 0.5 | **AUCUNE** (`toDoubleOrNull` : quantité omise, texte gardé) | moyen |

## 5. Langues et formats (UC-I18N)

| ID | Situation réelle (qui, pourquoi) | Type | Étapes | Résultat attendu | Couverture actuelle | Risque santé si ça casse |
|---|---|---|---|---|---|---|
| UC-I18N-001 | Utilisateur FR / JA | nominal | Téléphone en fr, ja | UI complète (1571 chaînes) | T1…T22 (captures) | bas |
| UC-I18N-002 | Utilisateur DE / NL / ZH | alternatif | Téléphone en de, nl, zh-CN | 554 chaînes traduites, le reste en anglais sans clé brute | **AUCUNE** | moyen |
| UC-I18N-003 | Utilisateur d'une des 19 autres langues (34 chaînes) | limite | Téléphone en es, ar, hi… | UI anglaise cohérente, pas de mélange illisible | **AUCUNE** | moyen |
| UC-I18N-004 | QR texte dans les 25 langues | nominal | Export → chips de langue | Titres de section traduits | T5, T13 (en/fr/ja) ; 22 autres : **AUCUNE** | haut |
| UC-I18N-005 | Clé de traduction absente dans une langue du QR | limite | Clé manquante en `th` | Repli anglais, jamais de titre vide | **AUCUNE** (`getLabel` renvoie `""` si absente aussi en EN) | moyen |
| UC-I18N-006 | Terme KB « Not Translated[…] » | limite | Tuberculose en JA | Terme anglais affiché, pas le marqueur | `PastQr::kbPlaceholdersAreDropped` ; écrans d'édition et pickers : **AUCUNE** | moyen |
| UC-I18N-007 | Libellé localisé prioritaire sur l'anglais | nominal | QR FR | Libellé FR | `PastQr::localisedLabelsWinOverTheEnglishTerm`, `PregQr::frenchQrPrintsTheLocalisedOutcomeLabel` | bas |
| UC-I18N-008 | QR texte plafonné à 2 200 octets | limite | Haru en JA / TH (UTF-8 3 octets) | Troncature signalée, allergies et traitements jamais coupés | T5 (« note si tronqué »), `QrCodec::testSplitMultiFrameAccurateByteBudgeting` (découpage) ; priorité des sections : **AUCUNE** | haut |
| UC-I18N-009 | Changement de langue en voyage | alternatif | Passer de fr à ja, rouvrir un profil | Données identiques ; `display` du Bundle inchangé (anglais officiel) | **AUCUNE** | moyen |
| UC-I18N-010 | Libellé capturé dans la langue de saisie | limite | Saisir en FR, lire en JA | Le libellé libre reste lisible ; pas de retraduction destructrice | `Result::officialDisplayInCodingFriendlyLabelInText` | bas |
| UC-I18N-011 | Valeur à virgule (« 5,4 ») | nominal | Résultat potassium 5,4 | `valueQuantity` 5.4 | `Result::integralAndCommaDecimalsSurviveTheDoubleJsonEncoding`, `decimalHelpers` | haut |
| UC-I18N-012 | Séparateur de milliers (« 1 200 », « 1.200,5 ») | limite | Plaquettes 250 000 | Pas d'interprétation fausse ; aujourd'hui bascule en texte libre | `Result::nonNumericTypedValueFallsBackToValueString` (repli) ; avertissement : **AUCUNE** | haut |
| UC-I18N-013 | Chiffres non latins (arabes orientaux, devanagari) | limite | Saisir ٥٫٤ | Converti ou refusé avec message | **AUCUNE** | moyen |
| UC-I18N-014 | Plage de référence à virgule ou négative | limite | « 3,5-5,1 », « ≤100 » | Bornes correctes | `Result::referenceRangeParsing` | moyen |
| UC-I18N-015 | Dates des formulaires en locale arabe | limite | Téléphone en ar, choisir une date | `yyyy-MM-dd` en chiffres latins (`Locale.ROOT`) | **AUCUNE** | moyen |
| UC-I18N-016 | Dates extraites par le chat allergies | limite | Locale à chiffres non latins, dicter une date | ISO en chiffres latins ; `"%04d".format` dépend de la locale | **AUCUNE** | moyen |
| UC-I18N-017 | Fuseau horaire (date choisie tard le soir au Japon) | limite | Choisir « aujourd'hui » à 23 h JST | Jour correct, pas la veille | **AUCUNE** | moyen |
| UC-I18N-018 | Interface RTL (arabe) | limite | Téléphone en ar | Mise en page miroir lisible, dates et codes LTR | **AUCUNE** (`supportsRtl=true`, aucun test) | moyen |
| UC-I18N-019 | Nom en écriture non latine | nominal | 田中 春, محمد | Stocké, affiché, exporté sans altération | **AUCUNE** | moyen |
| UC-I18N-020 | Dialogue d'import : textes codés en dur | limite | Importer en DE | « Born on », « Erreur d'enregistrement » : traduits ou au moins anglais | **AUCUNE** (FR/JA/EN en dur dans le fragment) | bas |
| UC-I18N-021 | Langue du patient (`p.lang`) | alternatif | Choisir ja-JP | `Patient.communication` BCP-47 ; QR texte l'indique | **AUCUNE** | moyen |
| UC-I18N-022 | Unités UCUM localisées (« µmol/L », « mg/dl ») | limite | Saisir une unité à la main | Unité conservée ; casse non corrigée en silence | P6 `unit-not-ucum` (système seulement) ; validité : **AUCUNE** | moyen |

## 6. Accessibilité (UC-A11Y)

| ID | Situation réelle (qui, pourquoi) | Type | Étapes | Résultat attendu | Couverture actuelle | Risque santé si ça casse |
|---|---|---|---|---|---|---|
| UC-A11Y-001 | Malvoyant : police système à 200 % | limite | Agrandir, ouvrir un formulaire | Rien de coupé, boutons Save atteignables | **AUCUNE** | moyen |
| UC-A11Y-002 | Textes en 10–12 sp | limite | Fiche profil, formulaires | Lisibles ; `item_profile_*` 10–11 sp, formulaires 12 sp | **AUCUNE** | moyen |
| UC-A11Y-003 | Tremblement : boutons ✕ de 36 dp | limite | Effacer le code / la date dans un formulaire | Cible ≥ 48 dp | **AUCUNE** (`*_code_clear`, `*_date_clear` à 36 dp dans les `bottom_sheet_*_form`) | moyen |
| UC-A11Y-004 | Tremblement : double appui sur Save d'un formulaire natif | erreur | 2 taps rapides | 1 seule carte | T9, T12 (vaccin, intervention, dispositif) ; résultats, antécédents : **AUCUNE** | moyen |
| UC-A11Y-005 | Tremblement : double appui sur Save de l'identité (nouveau profil) | erreur | 2 taps rapides | 1 seul profil | **AUCUNE** (pas de garde dans `PatientEditFragment`) | haut |
| UC-A11Y-006 | Appui long involontaire sur une carte | erreur | Maintenir trop longtemps | Menu annulable, pas de suppression directe | T3 b (appui long) ; annulation : **AUCUNE** | moyen |
| UC-A11Y-007 | Suppression : confirmation | nominal | Supprimer une allergie | Dialogue avec le nom de l'élément | T3 (vaccin) | haut |
| UC-A11Y-008 | TalkBack : lignes de liste | limite | Parcourir vaccins, allergies, traitements | Chaque ligne annonce nom + date + statut | **AUCUNE** (`item_*_row.xml` : 0 `contentDescription`) | moyen |
| UC-A11Y-009 | TalkBack : cartes de profil, étoile, corbeille | limite | Parcourir la liste des profils | Étoile et corbeille annoncées, état « courant » dit | **AUCUNE** (`item_profile_other/active.xml` : 0 `contentDescription`) | haut |
| UC-A11Y-010 | TalkBack : champs des formulaires | nominal | Formulaire vaccin | Chaque champ a un libellé (hint) | **AUCUNE** (hints présents, non vérifiés) | moyen |
| UC-A11Y-011 | TalkBack : statut par emoji seul (🚫, ⚠️) | limite | Vaccin « non administré » | Statut dit en mots | **AUCUNE** | moyen |
| UC-A11Y-012 | Daltonisme : criticité par couleur | limite | Allergie criticité haute | Texte ou icône en plus de la couleur | **AUCUNE** | moyen |
| UC-A11Y-013 | Erreur de validation | nominal | Save sans vaccin | Message inline + focus, lu par TalkBack | T9, T12 (visuel) ; annonce : **AUCUNE** | bas |
| UC-A11Y-014 | Toasts courts pour lecteur lent | limite | Enregistrer | Confirmation persistante ou relisible | **AUCUNE** | bas |
| UC-A11Y-015 | Orientation verrouillée portrait | limite | Téléphone fixé en paysage (support fauteuil) | Utilisable | T9 (rotation sans crash) | bas |
| UC-A11Y-016 | Thème sombre / contraste | limite | Mode nuit | Contraste suffisant (hint `#475569` sur fond sombre) | **AUCUNE** | bas |
| UC-A11Y-017 | Sélecteur de date pour une personne née en 1930 | limite | Choisir 1930 | Atteignable sans 1000 balayages ; saisie texte possible | **AUCUNE** | moyen |

## 7. Diversité humaine (UC-HUM)

| ID | Situation réelle (qui, pourquoi) | Type | Étapes | Résultat attendu | Couverture actuelle | Risque santé si ça casse |
|---|---|---|---|---|---|---|
| UC-HUM-001 | Personne sans date de naissance connue (réfugié, état civil lacunaire) | limite | Créer un profil sans date | Création possible avec année seule ou « inconnue » | **AUCUNE** (date complète obligatoire : création bloquée) | haut |
| UC-HUM-002 | Année de naissance seule | limite | Saisir 1950 | Acceptée, Bundle `birthDate` = `1950` | **AUCUNE** | haut |
| UC-HUM-003 | Profil importé à date partielle, puis édition d'identité | limite | Importer `bd: "1956"`, ouvrir Identité, Save | L'édition n'oblige pas à inventer un jour | **AUCUNE** | moyen |
| UC-HUM-004 | Mononyme (prénom seul, Kamekichi) | nominal | Profil sans nom de famille | Pas de `family` vide ; liste et QR corrects | `Blood::bundleHasAnIdentifierNoEmptyNamePartsAndNoHomeMadeExtension`, T7 | moyen |
| UC-HUM-005 | Mononyme rangé en nom de famille seul | limite | Importer `fn` seul | Dialogue d'import montre le nom | **AUCUNE** (`displayName()` renvoie « Profile inconnu ») | moyen |
| UC-HUM-006 | Nom très long ou composé | limite | 4 prénoms + particule | Non tronqué dans le QR et le Bundle | **AUCUNE** | bas |
| UC-HUM-007 | Genre « autre » / non précisé | alternatif | Choisir O ou rien | `other` / `unknown` ; aucune fonction bloquée | **AUCUNE** | bas |
| UC-HUM-008 | Enfant géré par un parent | nominal | Profil né en 2022 | Vaccins avec n° de dose et série ; contact = parent | T9 (dose/série) ; profil enfant : **AUCUNE** | haut |
| UC-HUM-009 | Nourrisson : calendrier vaccinal dense | limite | 15 vaccins, plusieurs le même jour | Tous distincts, tri stable, QR texte non tronqué en silence | **AUCUNE** | haut |
| UC-HUM-010 | Femme enceinte | nominal | Grossesse → Enceinte + terme | 82810-3 + EDD dans le Bundle | T21 étape 2, `Preg::roundTrips` | haut |
| UC-HUM-011 | Fin de grossesse | alternatif | Passer à « Non enceinte » | EDD retiré, bilan conservé avec ses ids | T21 étape 4 | haut |
| UC-HUM-012 | Statut « enceinte » périmé (2 ans plus tard) | limite | Ne jamais remettre à jour | Statut daté visible ; alerte d'ancienneté | **AUCUNE** | haut |
| UC-HUM-013 | Grossesse sur un profil de genre M ou d'un enfant | limite | Saisir | Autorisé (homme trans) ou confirmé, jamais refusé en silence | **AUCUNE** | moyen |
| UC-HUM-014 | Polypathologie : 12 problèmes, 15 traitements, 8 antécédents | limite | Saisir | Tout enregistré ; QR `_j2:` encore scannable ou multi-trames | `QrCodec::testSplitMultiFrameAccurateByteBudgeting` ; profil lourd bout en bout : **AUCUNE** | haut |
| UC-HUM-015 | Faible littératie : tout en texte libre | nominal | Vaccin « piqûre du village 1985 » | Accepté sans code ; `text` seul | `ImmCodec::freeTextVaccineWithoutCodeRoundTrips`, T8, T12, T22 | moyen |
| UC-HUM-016 | Texte libre avec emoji, retours ligne, guillemets | limite | Note « mal au ❤️ "fort" » sur 3 lignes | Aller-retour JSON, Bundle, QR sans perte | **AUCUNE** | bas |
| UC-HUM-017 | Texte libre très long (aidant bavard) | limite | Note de 2 000 caractères | Enregistrée ; QR prévient s'il déborde | **AUCUNE** | moyen |
| UC-HUM-018 | Champ texte fait d'espaces | limite | Saisir «    » | Traité comme vide, pas d'entrée fantôme | `ImmCodec::minimalEntryOmitsOptionalElements` (champs optionnels) ; nom vide : **AUCUNE** | bas |
| UC-HUM-019 | Voyageur change la langue du téléphone | alternatif | fr → ja à l'arrivée | Fiche lisible par le soignant local (QR texte JA) | T5 | haut |
| UC-HUM-020 | Hors ligne pendant des semaines | nominal | Mode avion, tout utiliser | Aucune fonction d'édition ni d'export ne demande le réseau | **AUCUNE** (implicite dans T1…T22) | haut |
| UC-HUM-021 | Changement de téléphone | limite | Ancien → nouveau par QR | Pertes listées à l'utilisateur (pas de sauvegarde : `allowBackup=false`) | **AUCUNE** | haut |
| UC-HUM-022 | Désinstallation / effacement des données | erreur | Désinstaller | Avertissement préalable : tout est perdu | **AUCUNE** | haut |
| UC-HUM-023 | Secouriste lit la fiche d'un inconnu inconscient | nominal | Widget / QR texte | Allergies, traitements, groupe sanguin, contacts d'abord | T5 ; ordre et complétude : **AUCUNE** | haut |
| UC-HUM-024 | Aidant saisit pour une personne âgée qui dicte | alternatif | Saisie rapide de 6 traitements | Aucun perdu si l'on enchaîne vite (voir UC-ROB-004) | **AUCUNE** | haut |
| UC-HUM-025 | Personne à deux adresses, deux nationalités, trois identifiants | limite | Importer puis éditer l'identité | Toutes les entrées conservées | **AUCUNE** (le formulaire réécrit 1 adresse, 1 identifiant, 1 téléphone + 1 courriel) | moyen |
| UC-HUM-026 | Personne sans téléphone ni adresse (sans-abri) | limite | Identité minimale | Profil valide, pas de champ obligatoire caché | **AUCUNE** | moyen |
| UC-HUM-027 | Handicap : canne, fauteuil, surdité | nominal | Autonomie → texte libre | Section 47420-5, hors liste de problèmes | T22, `Func::roundTripsAndStatuses` | moyen |
| UC-HUM-028 | Porteur de pacemaker | nominal | Dispositif + UDI | Device + DeviceUseStatement ; visible en urgence | T11, `ProcDev::deviceFullRoundTrip` | haut |

## 8. Robustesse (UC-ROB)

| ID | Situation réelle (qui, pourquoi) | Type | Étapes | Résultat attendu | Couverture actuelle | Risque santé si ça casse |
|---|---|---|---|---|---|---|
| UC-ROB-001 | App tuée pendant l'écriture du JSON | erreur | Tuer le processus pendant `writeText` | Ancien fichier intact (écriture atomique) | **AUCUNE** (écriture directe, pas de temporaire + renommage) | haut |
| UC-ROB-002 | App tuée entre JSON et Bundle | erreur | Tuer après le JSON | Paire recohérée au prochain lancement | **AUCUNE** | haut |
| UC-ROB-003 | Batterie vide pendant un import | erreur | Couper pendant l'import | Profil précédent intact | **AUCUNE** | haut |
| UC-ROB-004 | Deux modifications coup sur coup dans un même pilier | limite | Ajouter puis supprimer en < 1 s | État final = dernière action ; pas d'écritures croisées | **AUCUNE** (aucun verrou ; une coroutine par `persist()`) | haut |
| UC-ROB-005 | Deux piliers natifs modifiés en parallèle | limite | Save vaccins et Save résultats concurrents | Aucune mise à jour perdue (lecture-modification-écriture) | **AUCUNE** | haut |
| UC-ROB-006 | Écran hérité avec copie en mémoire périmée | limite | Allergies ouvert, autre écran modifie `md`, retour, ajouter une allergie | `md` récent non écrasé par la copie périmée | **AUCUNE** (Allergies ne recharge pas si `current != null`) | haut |
| UC-ROB-007 | Résultat de formulaire arrivant pendant le rechargement | limite | Traitements : retour de formulaire pendant `loadAndRender` | L'entrée ajoutée n'est pas effacée par le rechargement | **AUCUNE** | haut |
| UC-ROB-008 | Stockage plein à l'enregistrement d'un pilier natif | erreur | Remplir le disque, Save vaccin | Toast d'échec, ancien état intact | **AUCUNE** (toast prévu ; fichier possiblement tronqué) | haut |
| UC-ROB-009 | Stockage plein à l'enregistrement d'un pilier hérité | erreur | Remplir le disque, Save allergie | Pas de crash | **AUCUNE** (pas de `try/catch` autour de `saveProfile` dans Allergies, Traitements, Contacts, Identité) | haut |
| UC-ROB-010 | Stockage faible : JSON écrit, Bundle non | erreur | Disque presque plein | Voir UC-STO-008 | **AUCUNE** | haut |
| UC-ROB-011 | Android 12 (minSdk 31), petit écran, peu de RAM | limite | Appareil d'entrée de gamme | Toutes les fonctions de profil, sans modèle IA | **AUCUNE** | moyen |
| UC-ROB-012 | Android antérieur à 12 | erreur | Tenter l'installation sur Android 11 | Refus d'installation (attendu) ; documenté pour les personnes âgées à vieux téléphones | **AUCUNE** | moyen |
| UC-ROB-013 | Rotation / changement de configuration pendant un formulaire | limite | Remplir, basculer mode sombre | Pas de crash, saisie conservée | T9, T12 (pas de crash ; perte de saisie « à noter ») | bas |
| UC-ROB-014 | Mise en arrière-plan pendant un Save | limite | Home juste après Save | Écriture terminée (coroutine liée à la vue : annulation possible) | **AUCUNE** | moyen |
| UC-ROB-015 | Démarrage à froid : écran ouvert avant la fin du semis | limite | Ouvrir un profil dans la première seconde | Pas de liste vide trompeuse ni de profil « introuvable » | **AUCUNE** (`initialScanDone` passe à vrai avant la fin du semis) | moyen |
| UC-ROB-016 | KB absente ou en cours de copie | erreur | Sauver avant la fin de l'installation de la KB | Bundle écrit sans libellés ou réessayé | **AUCUNE** | haut |
| UC-ROB-017 | Fichier étranger dans `profiles/` (`meta.json`, `.tmp`, `.bak.json`) | limite | Déposer des fichiers | Ignorés ; un `x.bak.json` n'apparaît pas comme profil | **AUCUNE** | bas |
| UC-ROB-018 | Mise à jour de l'app sur des profils d'une version précédente | nominal | Installer par-dessus | Profils relus, piliers migrés au premier Save, rien perdu | T-couloirs sur données fraîches seulement : **AUCUNE** | haut |
| UC-ROB-019 | Widget d'urgence après chaque sauvegarde du profil courant | nominal | Modifier une allergie du profil courant | Widget à jour ; échec du widget sans effet sur l'écriture | **AUCUNE** | haut |
| UC-ROB-020 | Champ numérique hors bornes (dose 0, naissances vivantes > total, nombre géant) | erreur | Saisir | Refus avec message | T9, T21 étape 3 ; entier > 2³¹ : **AUCUNE** | moyen |

Total : 180 cas d'usage.

## Trous de couverture prioritaires

Risque haut d'abord. « JVM » = test Kotlin pur ; quand le dépôt est impliqué, la proposition suppose d'extraire la logique dans une
classe pure (`ProfileStore(dir: File, buildBundle: (JemmaProfileJ, IpsNativePillars) -> String)`) testable avec un dossier temporaire.

1. **UC-STO-008/009/010 — Bundle en échec, JSON écrit.** JVM : `ProfileStore` avec un `buildBundle` qui lève au 2e appel ; sauver vaccin A puis B ; relire : A et B présents.
2. **UC-ROB-001 — écriture non atomique.** JVM : écrire via un `FileWriter` injecté qui lève à mi-contenu ; l'ancien `{id}.json` doit se relire intact.
3. **UC-ROB-004/005 — écritures concurrentes.** JVM : 50 `saveNativePillars` en parallèle (`runTest` + `Dispatchers.IO`) sur deux piliers ; aucun élément perdu, JSON toujours analysable.
4. **UC-STO-016 — JSON corrompu.** JVM : tronquer `{id}.json` ; le scan doit renvoyer une entrée « endommagé » (et non l'omettre) et proposer la reconstruction depuis le Bundle.
5. **UC-IMP-009 — `sid` hostile.** JVM : `saveProfile` avec `sid` = `../x`, `a/b`, `x.fhir`, chaîne vide ; aucun fichier hors du dossier, aucun écrasement.
6. **UC-IMP-004/005 — import écrasant des modifications locales.** JVM : profil local enrichi (lot, 3 dispositifs) + import `COMPRESSED` du même `sid` ; le test fige la règle et exige un indicateur « écrase N éléments locaux » renvoyé à l'UI.
7. **UC-MPR-005/006 — suppression du profil courant.** JVM : 3 profils, supprimer le courant ; `currentProfileId` doit être `null` (ou choix explicite), jamais un persona.
8. **UC-MPR-003 — premier vrai profil non courant.** JVM : dépôt semé de 3 personas + 1 création ; le profil créé doit être courant.
9. **UC-IMP-019 / UC-MPR-008 — écriture dans le profil courant par défaut.** Appareil : courant = A, ouvrir l'assistant depuis B, vider le handoff, Save ; `verify_profiles` : A inchangé.
10. **UC-STO-004/005 — non-régression croisée des 8 piliers.** JVM : `ProfileStore` peuplé (Haru) ; pour chaque pilier, une édition ; comparer les ensembles d'ids des 7 autres avant/après.
11. **UC-STO-006/007 — édition héritée conservant les détails du Bundle.** JVM : vaccin avec lot + série, puis `saveProfile(MANUAL_EDIT)` avec un contact modifié ; relire lot et série.
12. **UC-FHIR-008 — traitement arrêté exporté actif.** JVM : `JemmaFhirBundleBuilder.build` avec `ms = "stopped"` ; `MedicationStatement.status == "stopped"`.
13. **UC-A11Y-005 / UC-IMP-003 — doublons de profil.** JVM : deux `saveProfile` du même contenu sans `sid` ; 1 seul fichier (clé d'idempotence) ; appareil : double tap sur Save de l'identité → 1 profil.
14. **UC-ROB-006/007 — copie mémoire périmée.** Appareil : Allergies ouvert, ajouter un traitement via l'assistant, revenir, ajouter une allergie ; `_j.md` contient encore le traitement.
15. **UC-HUM-001/002/003 — date de naissance inconnue ou partielle.** JVM : `build` avec `bd` = `null`, `"1950"`, `"1950-06"` ; Bundle valide, pas d'exception ; appareil : création sans date.
16. **UC-IMP-013 — date de naissance non ISO importée.** JVM : `build` avec `bd = "05/02/1956"` ; ne lève pas, `birthDate` omis.
17. **UC-IMP-017 — problème résolu importé devenu actif.** JVM : `IpsNativePillars.fromJEntries(cn = [st "resolved"])` ; l'entrée va en antécédents ou garde un statut non actif.
18. **UC-STO-025/026 — groupe sanguin contradictoire ou mal écrit.** JVM : `IpsBloodGroup.normalize("0+")`, `"O pos"`, et `sync` avec `p.bt` ≠ résultat manuel ; contradiction détectable.
19. **UC-I18N-008 / UC-HUM-014 — QR texte tronqué.** JVM : `JemmaTextPayloadBuilder.build` d'un profil lourd en JA et TH ; ≤ 2 200 octets, sections allergies et traitements complètes, marqueur de troncature présent.
20. **UC-IMP-001 — aller-retour `_j2:`.** JVM (Robolectric ou `java.util.Base64` injecté) : `decode(encode(p)) == p` pour les 3 personas et un profil à caractères non latins.

Suivants (risque moyen) : UC-FHIR-006/007 (sections obligatoires vides), UC-IMP-015 (croissance du pilier grossesse), UC-A11Y-008/009 (test Espresso `AccessibilityChecks.enable()` sur la liste des profils et un écran de pilier), UC-I18N-005 (toutes les clés × 25 langues non vides), UC-HUM-025 (identité à entrées multiples).

## Défauts probables repérés à la lecture du code

1. **Paire incohérente puis perte silencieuse** — `profiles/ProfilesRepository.kt:394-403` et `:327-351`. Le JSON est écrit, puis l'échec du Bundle est seulement journalisé (`catch (e: Throwable)`), et l'appelant reçoit `true` (toast « enregistré »). À la lecture suivante, `readNativePillars` préfère l'ancien Bundle dès qu'il se lit et que le pilier n'y est pas vide : la modification qui n'existe que dans `_j` est ignorée, puis effacée par la reprojection du Save suivant. Déclencheurs dans le même `try` : `hydrator.hydrate` (KB), `FhirDate.fromString(bd)` (`qr/JemmaFhirBundleBuilder.kt:124`), `AddressUse.fromCode` (`:453`), `ContactPointSystem.fromCode` (`:480`) sur des valeurs importées non contrôlées.
2. **Écritures non atomiques** — `profiles/ProfilesRepository.kt:394` et `:399` (`writeText` direct). Un arrêt en cours d'écriture laisse un JSON tronqué ; `loadProfile` renvoie alors `null` (`:430-435`) et `rescanFromDisk` saute le fichier (`:543-549`) : le profil disparaît de la liste sans message, le `.fhir.json` reste orphelin.
3. **Aucune exclusion mutuelle** — `profiles/ProfilesRepository.kt:300-324` (lecture-modification-écriture) et `:180-221`, sur `Dispatchers.IO`, sans `Mutex` ; chaque `persist()` des fragments lance sa coroutine (ex. `ui/profile/immunizations/ImmunizationsEditFragment.kt:234-238`). Deux sauvegardes rapprochées peuvent finir dans le désordre (l'ancienne liste gagne) ou écrire le même fichier en même temps.
4. **`sid` du QR utilisé tel quel comme nom de fichier** — `profiles/ProfilesRepository.kt:185-187` ; `qr/JemmaPayloadCodec.kt` (`parseAndRehydrate`) ne valide que `_j == "1.2"`. Un `sid` contenant `../` écrit hors de `profiles/` ; un `sid` `x.fhir` produit `x.fhir.json`, exclu du scan (`:519`) et confondu avec le Bundle du profil `x`.
5. **Suppression du profil courant : promotion silencieuse d'une autre personne** — `profiles/ProfilesRepository.kt:467-470` : le profil le plus récemment modifié devient courant (widget d'urgence), y compris un persona de démonstration ou un autre membre de la famille.
6. **Le premier vrai profil ne devient jamais courant** — `profiles/ProfilesRepository.kt:209` exige `_profilesFlow.value.size == 1`, alors que `ensureInitialScan` (`:139-150`) sème toujours les 3 personas ; `setCurrent` n'est appelé que depuis `ui/profiles/ProfilesFragment.kt:250-253`. Même bloc : un persona supprimé est re-semé à chaque démarrage (`:144`).
7. **L'édition d'identité tronque les listes structurées** — `ui/profile/perso/PatientEditFragment.kt:614-637` reconstruit `adrs`, `tels`, `ids` avec au plus une adresse, un téléphone + un courriel, un identifiant (seul le premier est chargé, `:320-347`) : les autres entrées d'un profil importé sont perdues au premier Save. Même fichier `:591` : une date de naissance non `YYYY-MM-DD` bloque tout enregistrement (création impossible sans date complète).
8. **Identité : ni garde anti double appui ni gestion d'erreur** — `ui/profile/perso/PatientEditFragment.kt:548-552` et `:675-676` : deux appuis sur un nouveau profil (`sid` nul) donnent deux UUID, donc deux profils. Le `saveProfile` relance l'exception (`ProfilesRepository.kt:202`) sans `try/catch` côté fragment, ici comme dans `allergies/AllergiesEditFragment.kt:343-344`, `medications/MedicationsEditFragment.kt:332-333`, `contacts/ContactsEditFragment.kt:260-261` : disque plein = crash.
9. **Bundle : champs cliniques hérités figés** — `qr/JemmaFhirBundleBuilder.kt:264` (`MedicationStatement` toujours `Active`, `ms` ignoré : un traitement arrêté est exporté actif), `:198` (type toujours `Allergy`, `tp` ignoré), `:206` (système toujours SNOMED, `cs` ignoré, code vide accepté), `:282` (`toDoubleOrNull` : dose « 0,5 » sans quantité).
10. **Pilier grossesse : appartenance par code seul** — `ips/IpsFhirCodec.kt:241` lit toute Observation dont le code LOINC est un code de grossesse, y compris une Observation du pilier Résultats (`resultsOf`, `:152-160`, la retient aussi par son profil). Une telle entrée (importable par `_j.rs`) est lue dans les deux piliers, réécrite deux fois avec le même id, et le pilier grossesse gagne une entrée à chaque sauvegarde.

Non confirmé (à vérifier sur appareil) : comportement exact de `FhirDate.fromString` sur une date non ISO ; chiffres produits par `"%04d".format` dans `ui/profile/allergies/chat/AllergiesChatFragment.kt:1423-1457` sous une locale à chiffres non latins.
