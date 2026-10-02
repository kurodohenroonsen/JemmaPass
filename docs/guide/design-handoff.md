# JemmaPass — Dossier de passation design

Destinataire : équipe design. État du code : branche `feat/ips-18-pillars-cleanup`, commit `8a675b3` (2 octobre 2026).

Tout ce qui suit vient du code et des fichiers de chaînes. Rien n'a été vérifié à l'œil : cette lane ne peut pas ouvrir les images. Les points à contrôler visuellement sont listés au chapitre 4 et dans `captures-manquantes.md`.

Chemins abrégés :

- `…/` = `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/`
- `res/` = `JemmaPassAndroidDemo/app/src/main/res/`

## 1. Inventaire des écrans

Une seule activité : `MainActivity` → `res/layout/activity_main.xml` (hôte de navigation + barre du bas). Graphe : `res/navigation/nav_graph.xml`. Destination de départ : `dest_permissions`.

Barre du bas (`res/menu`) : « Profils » (`dest_profiles`), « SOS/Secours » (`dest_radar`), « Réglages » (`dest_settings`). Elle est masquée sur les écrans plein cadre listés dans `MainActivity.FULL_BLEED_DESTINATION_NAMES`.

### Parcours principal

| Fragment | Layout | Rôle |
|---|---|---|
| `ui/permissions/PermissionsFragment` | `fragment_permissions` (+ `view_permission_card`) | Premier lancement : 5 cartes d'autorisation, « Tout accorder », « Continuer vers la démo » |
| `ui/profiles/ProfilesFragment` | `fragment_profiles` (+ `item_profile_summary`, `view_profiles_empty_state`, `view_demo_not_ready_banner`) | Liste des profils ; état vide « Bienvenue à bord » (3 cartes) ; bouton « Nouveau profil » ; icône d'import par QR ; appui long = menu |
| `ui/profiles/detail/ProfileDetailFragment` | `fragment_profile_detail` (+ `view_pillar_tile`, `view_pillar_tile_stub`) | Écran « Profil » : grille des 18 piliers (repliable), nom, bandeau rouge, bandeau ambre, sections en lecture, liste « ⚡ ALERTES », action « Exporter » |
| `ui/profile/perso/PatientEditFragment` | `fragment_perso_edit` (+ `view_assistant_cta_banner`) | Identité : création et modification du profil |
| `ui/profile/allergies/AllergiesEditFragment` | `fragment_allergies_edit` (+ `item_allergy_row`) | Liste des allergies |
| `ui/profile/allergies/AllergyFormBottomSheet` | `bottom_sheet_allergy_form` | Formulaire allergie ; contrôle croisé à l'enregistrement |
| `ui/profile/medications/MedicationsEditFragment` | `fragment_medications_edit` (+ `item_medication_row`) | Liste des médicaments |
| `ui/profile/medications/MedicationFormBottomSheet` | `bottom_sheet_medication_form` (+ `dialog_kb_drug_picker`) | Formulaire médicament : 5 boutons de voie, contrôle croisé |
| `ui/profile/pastproblems/PastProblemsEditFragment` | `fragment_past_problems_edit` (+ `item_past_problem_row`) | **Un seul fragment pour trois écrans** : Antécédents (`dest_past_problems`), Problèmes actifs (`dest_problems`), Autonomie et handicaps (`dest_functional`). Le titre et l'emoji changent selon l'argument `kind` |
| `ui/profile/pastproblems/PastProblemFormBottomSheet` | `bottom_sheet_past_problem_form` | Formulaire commun à ces trois écrans |
| `ui/profile/immunizations/ImmunizationsEditFragment` / `ImmunizationFormBottomSheet` | `fragment_immunizations_edit`, `item_immunization_row` / `bottom_sheet_immunization_form` | Vaccinations |
| `ui/profile/procedures/ProceduresEditFragment` / `ProcedureFormBottomSheet` | `fragment_procedures_edit`, `item_procedure_row` / `bottom_sheet_procedure_form` | Interventions |
| `ui/profile/devices/DevicesEditFragment` / `DeviceFormBottomSheet` | `fragment_devices_edit`, `item_device_row` / `bottom_sheet_device_form` | Dispositifs médicaux |
| `ui/profile/results/ResultsEditFragment` / `ResultFormBottomSheet` | `fragment_results_edit`, `item_result_row` / `bottom_sheet_result_form` | Résultats ; fenêtre de conflit de groupe sanguin (dans le formulaire et sur la liste) |
| `ui/profile/pregnancy/PregnancyEditFragment` | `fragment_pregnancy_edit` | Grossesses : écran unique, sans liste |
| `ui/common/IpsCodePickerDialog`, `KbDrugPickerDialog`, `KbConditionPicker` | `dialog_ips_code_picker`, `dialog_kb_drug_picker` | Sélecteurs avec recherche |
| `ui/profile/common/CrossCheckAlertDialog` | (dialogue Material, texte construit en code) | Fenêtre « Interaction clinique détectée » |
| `ui/export/QrViewerFragment` | `fragment_qr_viewer` | QR : onglets Compact / Texte / FHIR, drapeaux de langue, navigation entre images, enregistrer, partager |
| `qr/JemmaPdfExporter` + `pdf/PdfPillarLayout` | (dessin sur `Canvas`, pas de layout) | Carte PDF A4, 2 pages |
| `ui/profiles/import_qr/QrImportScanFragment` | `fragment_qr_import_scan` | Import d'un profil par QR, lecture multi-images |
| `ui/settings/SettingsFragment` | `fragment_settings` (+ `view_model_card_v2`, `view_kb_card`, `view_preflight_row`) | Téléchargements, base de connaissances, langue (inactive), personas de démo, confidentialité, effacement |

### Assistant (photo, voix)

| Fragment | Layout | Rôle |
|---|---|---|
| `ui/assistant/AddItemWithAssistantBottomSheet` | `bottom_sheet_add_with_assistant` | Choix du mode d'ajout ; modes inactifs à 35 % d'opacité |
| `ui/assistant/AssistantPipelineFragment` | `fragment_assistant_pipeline` (+ `view_assistant_progress_step`) | Scan d'une boîte : 4 étapes |
| `ui/assistant/AssistantMultiPreviewFragment` | `fragment_assistant_multi_preview` (+ `view_assistant_med_row`) | Aperçu de plusieurs médicaments détectés |
| `ui/assistant/AssistantPatientPipelineFragment` | `fragment_assistant_patient_pipeline` | Lecture d'une pièce d'identité |
| `ui/profile/perso/chat/PatientChatFragment` | `fragment_patient_chat` | Dictée de l'identité |
| `ui/profile/allergies/chat/AllergiesChatFragment` | `fragment_allergies_chat` (+ `view_chat_msg_*`) | Dictée d'une allergie |

### SOS et secours

| Fragment | Layout | Rôle |
|---|---|---|
| `ui/radar/SosRescueHubFragment` | `fragment_sos_rescue_hub` | Entrée de l'onglet SOS/Secours |
| `ui/radar/ModeSelectionBottomSheet` | `bottomsheet_mode_selection` | « Choisis ton rôle » |
| `ui/radar/SosBroadcastFragment` | `fragment_sos_broadcast` | Diffusion SOS (victime) |
| `ui/radar/RadarFragment` | `fragment_radar` (+ `item_victim_card`, `item_rescuer_badge`) | Radar secouriste |
| `ui/radar/RescueQrScanFragment` | `fragment_qr_import_scan` (réutilisé) | Scan d'un badge papier, relais aux secouristes |
| `ui/radar/PatientDetailFragment` | `fragment_patient_detail` (+ `view_livescan_pipeline`, `view_medscan_pipeline`) | Fiche patient secouriste ; résultat du scan de médicament (étiquettes verte / ambre) |
| `ai/livescan/JemmaLiveScanFragment` | `fragment_jemma_live_scan` (+ `view_livescan_overlay`) | Caméra du scan de médicament |
| `ui/radar/TriageSaltBottomSheet` | (pas de layout dédié relevé) | Triage |

### Écrans présents mais hors parcours ou provisoires

| Fragment | Layout | État |
|---|---|---|
| `ui/stubs/PillarStubFragment` | `fragment_pillar_stub` | Fiche d'information des 7 piliers inactifs (dont Contacts) |
| `ui/profiles/PillarsGalleryFragment` | `fragment_pillars_gallery` | Galerie technique des 18 piliers |
| `ui/export/ExportHubFragment` | — | Bouchon (« Hub d'export ») |
| `ui/profile/contacts/ContactsEditFragment`, `ContactFormBottomSheet` | `fragment_contacts_edit`, `bottom_sheet_contact_form` | Codés, mais pilier `contacts` marqué `isActive = false` |
| `ui/home/HomeFragment`, `ui/splash/SplashFragment`, `ui/settings/BenchmarkFragment` | `fragment_home`, `fragment_splash`, `fragment_benchmark` | Présents dans le graphe de navigation, hors parcours de départ (accueil et écran de lancement d'une version précédente, outil de comparaison de modèles) |
| Layouts sans usage constaté dans le code | `dialog_profile_create_choice`, `dialog_profile_quick_create`, `dialog_profile_long_press_menu`, `item_profile_active`, `item_profile_other` | La liste utilise `item_profile_summary` ; le menu d'appui long est un dialogue Material construit en code |

### Les 11 piliers actifs (`…/pillars/PillarMetadata.kt`)

Le libellé de la tuile vient de `pillar_*_title`. Le titre de l'écran vient d'une autre chaîne. Ils diffèrent pour 6 piliers sur 11.

| Clé | Emoji | Libellé de la tuile (fr) | Titre de l'écran (fr) |
|---|---|---|---|
| `patient` | 📝 | Patient | Identité |
| `allergies` | ⚠️ | Allergies | Allergies |
| `medications` | 💊 | Médicaments | Médicaments |
| `conditions` | 🩺 | Pathologies actives | **Problèmes actifs** |
| `pastProblems` | 📜 | Antécédents médicaux | Antécédents médicaux |
| `immunizations` | 💉 | Vaccinations | Vaccinations |
| `procedures` | 🏥 | Procédures | **Interventions** |
| `devices` | 📟 | Dispositifs médicaux | Dispositifs médicaux |
| `functional` | ♿ | Statut fonctionnel | **Autonomie et handicaps** |
| `pregnancy` | 🤰 | Grossesse | **Grossesses** |
| `results` | 🧪 | Résultats labo | **Résultats** |

Inactifs (7) : `contacts` 👥, `advanceDirectives` 📜, `consents` ✍️, `goals` 🎯, `encounters` 🚑, `occupational` 💼, `providers` 👨‍⚕️. `advanceDirectives` et `pastProblems` partagent l'emoji 📜.

## 2. Système de couleurs et de formulation de sécurité, tel qu'implémenté

Principe écrit dans le code : « No alert is only good news when the cross-checks really ran » (`…/ui/profiles/detail/SafetyBannerDecision.kt`).

### 2.1 Écran « Profil »

Décision : `SafetyBannerDecision.decide()` (Kotlin pur). Rendu : `ProfileDetailFragment.renderProfile()` (lignes 275-320). Vues : `res/layout/fragment_profile_detail.xml`.

| Élément | Condition | Texte (fr) | Couleur | Fichier |
|---|---|---|---|---|
| Bandeau rouge `profile_detail_alert_banner` | au moins une alerte majeure | « ⚠ %d alerte(s) MAJEURE(S) détectée(s) » | fond `jemma_alert_red_bg` `#DC2626`, texte blanc, 15sp gras | `res/values/colors_jemma_profile_detail.xml`, `strings_jemma_profiles.xml` |
| Bandeau rouge (même vue) | alertes, aucune majeure | « ⚠ Autres alertes détectées » | idem | idem |
| Bandeau ambre `profile_detail_safety_status_banner` | base de connaissances indisponible | « ⓘ Contrôles de sécurité non effectués — base de connaissances indisponible » + saut de ligne + « L'absence d'alerte ne signifie pas l'absence de risque. » | fond `jemma_warning` `#F59E0B`, texte noir, 15sp gras | `res/values/colors.xml`, `strings_jemma_safety_status.xml` |
| Bandeau ambre (même vue) | contrôle partiel, nombre connu | pluriel `profile_detail_safety_incomplete_count` + même phrase finale | idem | idem |
| Bandeau ambre (même vue) | contrôle partiel, nombre inconnu (0) | `profile_detail_safety_incomplete_generic` + même phrase finale | idem | idem |
| Aucun bandeau | contrôles complets, aucune alerte | — | — | — |

Faits à retenir :

- Le rouge et l'ambre sont indépendants. Les deux peuvent s'afficher, l'ambre sous le rouge (« alerts never hide it »).
- **Il n'y a pas d'état vert sur cet écran.** « Vérifié, rien trouvé » se traduit par l'absence de bandeau.
- Le nombre du bandeau ambre est `KbCheckReport.unverifiedItems`, compté par les contrôles (commit `8a675b3`).
- Liste « ⚡ ALERTES » : couleurs de gravité prises dans le système Android, pas dans la palette (`ProfileDetailFragment.severityColor()` : `holo_red_dark`, `holo_orange_dark`, `holo_orange_light`, `holo_green_dark`, `darker_gray`).

### 2.2 Formulaires médicament et allergie

| Élément | Condition | Texte (fr) | Couleur | Fichier |
|---|---|---|---|---|
| Fenêtre « Interaction clinique détectée » | le contrôle trouve un conflit | titre `xchk_title` précédé de 🔴 / 🟠 / 🟡 / ⚪ ; boutons « Enregistrer quand même » / « Revoir » | titre coloré : `#EF4444` majeur, `#F97316` modéré, `#EAB308` mineur, `#9CA3AF` inconnu (constantes en dur) | `…/ui/profile/common/CrossCheckAlertDialog.kt`, `strings_xchk.xml` |
| Fenêtre « ⚠ Contrôle de sécurité incomplet » (médicament) | contrôle non exécuté, médicament non reconnu, ou médicaments existants sans code | `medication_form_xchk_gap_title` + un des messages `medication_form_xchk_gap_*` ; mêmes boutons | dialogue Material par défaut, **aucune couleur ambre** ; seul le « ⚠ » du titre signale l'état | `MedicationFormBottomSheet.confirmSafetyGapsThen()`, `MedicationFormLogic.kt` |
| Fenêtre « ⚠ Contrôle de sécurité incomplet » (allergie) | idem côté allergie | `allergy_form_xchk_gap_title` + un des messages `allergy_form_xchk_gap_*` ; mêmes boutons | idem | `AllergyFormBottomSheet.confirmSafetyGapsThen()`, `AllergyFormSafetyLogic.kt`, `strings_jemma_verdict_ui.xml` |
| Rien | contrôle complet, aucun conflit | enregistrement direct, sans message | — | `FormCrossCheckOutcome.kt` (`isClean`) |

- Le bouton positif est « Enregistrer quand même » dans les deux fenêtres. Le bouton négatif est « Revoir ». Retour arrière et appui hors fenêtre valent « Revoir ».
- **Pas de confirmation verte dans les formulaires.** Les chaînes `medication_form_xchk_clean` (« Aucune interaction détectée avec ton profil actuel. »), `allergy_form_xchk_clean`, `*_xchk_in_progress` et la couleur `form_validation_ok` `#2E7D32` existent mais ne sont référencées nulle part dans le code.

### 2.3 Scan de médicament par un secouriste

Décision : `…/ui/radar/LiveScanVerdictBadge.kt` (Kotlin pur). Rendu : `PatientDetailFragment.buildLiveScanStatusBadge()` et bloc `RenderedWithVerdict`.

| Élément | Condition | Texte (fr) | Couleur |
|---|---|---|---|
| Étiquette verte | verdict CLEAN **et** contrôle entièrement exécuté | « ✓ Aucune interaction détectée » | fond `#15803D`, texte blanc 14sp |
| Étiquette ambre | rien n'a été vérifié | « ⚠ Non vérifié — le contrôle de sécurité n'a pas été exécuté » | fond `#B45309` (`LIVESCAN_BADGE_AMBER`), texte blanc |
| Étiquette ambre | contrôle partiel | « ⚠ Contrôle incomplet — vérification partielle » | idem |
| Bandeau de résultat | majeur / modéré / mineur / propre | préfixes 🚨 / ⚠️ / 🟡 / ✅ | `#DC2626` / `#EA580C` / `#CA8A04` / `#15803D` |
| Voix | non vérifié / incomplet | `tts_scan_not_checked`, `tts_scan_incomplete`, `tts_scan_incomplete_note` | — (`…/ai/livescan/TtsStaticVerdict.kt`, `ScanSafety.kt`) |

C'est le seul endroit où le vert apparaît, et seulement pour un contrôle complet et propre (`LiveScanVerdictBadge.isGreen`).

### 2.4 QR texte et carte PDF : rien n'est coupé en silence

- QR texte (`…/qr/JemmaTextPayloadBuilder.kt`) : plafond de 1 800 octets, toujours une seule image. Les lignes retirées sont signalées par « ✂️ … +N » dans la section, puis « ✂️ … [ FICHE INCOMPLÈTE ] » avec les icônes des sections touchées. Ordre de retrait : autonomie, grossesse, vaccinations, résultats, interventions, antécédents, dispositifs, coordonnées du patient, contacts, maladies, médicaments, allergies. L'identité n'est jamais retirée.
- Carte PDF (`…/pdf/PdfPillarLayout.kt`, `…/qr/JemmaPdfExporter.kt`) : 13 lignes pour allergies + maladies, 18 lignes pour les médicaments. Au-delà, la dernière ligne devient « +N … » en gras. Allergies triées par gravité. Lettres H / L / ? (une gravité inconnue s'imprime « ? », jamais « L »).

### 2.5 Contrastes calculés (WCAG, texte sur fond fixe)

| Usage | Texte / fond | Rapport |
|---|---|---|
| Bandeau rouge | blanc / `#DC2626` | 4,83 |
| Bandeau ambre | noir / `#F59E0B` | 9,78 |
| Étiquette ambre du scan | blanc / `#B45309` | 5,02 |
| Étiquette verte du scan | blanc / `#15803D` | 5,02 |
| Bandeau « modéré » du scan | blanc / `#EA580C` | **3,56** |
| Bandeau « mineur » du scan | blanc / `#CA8A04` | **2,94** |
| Texte secondaire | `#94A3B8` / `#0F172A` | 6,96 |

Les deux valeurs en gras sont sous le seuil AA de 4,5 pour du texte de 14sp.

## 3. Accessibilité : faits trouvés dans le code

**Orientation.** `MainActivity` est verrouillée en portrait (`AndroidManifest.xml`, `android:screenOrientation="portrait"`, `configChanges="uiMode"`). `supportsRtl="true"`.

**Thème.** `Theme.Jemma` hérite de `Theme.Material3.DayNight.NoActionBar`, mais la palette est sombre dans les deux modes (`jemma_bg` `#0F172A`, `jemma_surface` `#1E293B`).

**Zones vivantes (`accessibilityLiveRegion`).**

- Bandeau ambre du profil : `polite` (`fragment_profile_detail.xml`). Sa description devient « Attention : » + le texte (`profile_detail_safety_status_desc`).
- Étapes de l'assistant : `polite` (`view_assistant_progress_step.xml`).
- **Le bandeau rouge n'a pas de zone vivante.** Un lecteur d'écran n'annonce pas son apparition.

**Descriptions de contenu.**

- Présentes sur les boutons d'effacement des formulaires (`*_clear_desc`), les boutons « + » (`*_fab_add_desc`), la navigation entre images du QR (`qr_prev_frame`, `qr_next_frame`, `qr_play_pause`), l'image du QR (`qr_image_cd`), les modes de l'assistant (`assistant_mode_*_cd`).
- Étiquettes du scan : description longue dédiée (`livescan_badge_not_verified_desc`, `livescan_badge_incomplete_desc`).
- En dur et en anglais : `item_profile_summary.xml` (« Set as current profile », « Delete profile ») ; bouton retour `cd_navigate_back_icon` = « Go back », défini uniquement dans `res/values` (aucune traduction).
- Chaîne technique lue telle quelle : chaque drapeau de langue du QR a pour description `qr_lang_<code>` (`QrViewerFragment.setupLangChips()`). Elle sert de sélecteur aux tests.
- Sigles de triage en dur (« WAIT », « EVAL »…) dans `fragment_patient_detail.xml` et `item_victim_card.xml`.

**Aides TalkBack des formulaires.** `…/ui/profile/common/FormA11yHelpers.kt` (rôle de sélecteur, état sélectionné, annonce) n'est appelé que par `ContactFormBottomSheet`, formulaire d'un pilier inactif. Les chaînes `allergy_form_a11y_*`, `medication_form_a11y_*`, `perso_a11y_*`, `xchk_a11y_role`, `xchk_button_*_a11y` existent mais ne sont pas branchées.

**Titres.** `accessibilityHeading` : uniquement dans `bottom_sheet_add_with_assistant.xml` et `fragment_assistant_multi_preview.xml`.

**Tailles de texte.** Toutes en `sp` (elles suivent le réglage système). Répartition dans `res/layout` : 9sp ×9, 10sp ×36, 11sp ×69, 12sp ×124, 13sp ×69, 14sp ×55, 15sp ×74, 16sp ×35, 17sp ×4, 18sp et plus ×184. Soit 238 occurrences à 12sp ou moins. Exemples : libellé des tuiles 11sp, compteur des tuiles 9sp, onglets du QR 12sp, boutons de voie 12sp, libellés de champ 11sp, sous-titre de la grille 11sp, bandeaux de sécurité 15sp gras.

**Cibles tactiles.** Étoile et corbeille de la liste des profils : 52 × 52 dp. Flèches du QR : 44 × 44 dp. Icône des tuiles : 40 × 40 dp (la tuile entière est cliquable).

**Couleur seule.** Les états de sécurité portent toujours un symbole ou un texte en plus de la couleur (⚠, ⓘ, ✓, 🔴/🟠/🟡). Les modes inactifs de l'assistant sont signalés par l'opacité (35 %) et un cadenas.

## 4. Risques visuels à contrôler

1. **Cinq boutons de voie sur une ligne.** `bottom_sheet_medication_form.xml` : `MaterialButtonToggleGroup` pleine largeur, 5 boutons à poids égal, emoji + texte, 12sp. Libellés : fr « 💊 Orale / 💉 IV/IM / 🧴 Topique / 🩹 SC / 🫁 Inhalée » ; de « Topisch », « Inhalativ » ; nl « Inhalatie » ; zh « 静脉/肌注 ». Risque de troncature ou de retour à la ligne sur écran étroit ou avec une grande police. Captures A13, A14.
2. **Budgets de lignes de la carte PDF non vérifiés à l'œil.** 13 et 18 lignes de 8 pt sont des constantes (`PdfPillarLayout.COLUMN1_ROWS`, `MEDICATION_ROWS`). Aucune capture du PDF n'existe. À contrôler : chevauchement avec le QR du quadrant, lisibilité à 8 pt une fois plié, largeur des noms longs, rendu des emoji de titre à l'impression, ligne « +N ». Captures B23, B24.
3. **Chaînes longues en de / nl / ja / zh.**
   - Bandeau ambre : jusqu'à 3 lignes à 15sp gras en allemand (« Sicherheitsprüfungen unvollständig — %d Einträge konnten nicht überprüft werden » + phrase finale).
   - Fenêtres de sécurité : boutons « Enregistrer quand même » / « Revoir » ; message du conflit de groupe sanguin en trois paragraphes ; bouton « Le modifier dans l'identité ».
   - Étiquettes du scan sur une seule vue (`WRAP_CONTENT`) : « ⚠ Non vérifié — le contrôle de sécurité n'a pas été exécuté ».
   - Libellés de tuile à 11sp dans une grille à 4 colonnes : « Antécédents médicaux », « Dispositifs médicaux », « Directives anticipées », « Données professionnelles ».
4. **Écrans en deux langues.** `values-de`, `values-nl` et `values-zh-rCN` contiennent 26 fichiers sur 50. Manquent notamment `strings_jemma.xml`, `strings_jemma_pillars.xml`, `strings_jemma_profiles.xml`, `strings_xchk.xml`, `strings_blindage.xml`, `strings_jemma_qr_import.xml`, `strings_jemma_settings.xml`, `strings_tts.xml`. Conséquence dans ces trois langues : le bandeau ambre et les fenêtres « contrôle incomplet » sont traduits, mais le bandeau rouge, les titres de tuile, les boutons « Save anyway » / « Review » et les phrases vocales d'alerte restent en anglais. Le japonais est complet (50 fichiers).
5. **Ambre non réservé à la sécurité.** `jemma_warning` et `jemma_accent` ont la même valeur `#F59E0B`. L'accent sert à des éléments décoratifs (étiquettes, « ✨ Expliqué simplement », pastilles F:/P: des médicaments). Trois ambres / oranges différents coexistent : `#F59E0B` (bandeau), `#B45309` (étiquette du scan), `#EA580C` (bandeau « modéré » du scan).
6. **« Non vérifié » ressemble à « modéré » sur le scan.** Quand le résultat n'est pas vérifié, le bandeau principal du scan prend la couleur et le préfixe du niveau modéré (`#EA580C`, ⚠️) ; seule l'étiquette en dessous est ambre `#B45309`.
7. **Fenêtres « contrôle incomplet » sans couleur.** Elles ont l'apparence d'un dialogue ordinaire. La fenêtre d'interaction, elle, colore son titre.
8. **Rouges multiples.** `#DC2626` (bandeau, palette), `#EF4444` (titre de la fenêtre d'interaction), `#B91C1C` et `#7F1D1D` (verdicts du scan), `holo_red_dark` (liste des alertes), `#C62828` (`xchk_destructive`, défini dans `colors_blindage.xml`, référencé nulle part).
9. **Rangée de 25 drapeaux sur le QR texte.** Trois (EN, FR, JA) sont à 19 points avec contour bleu, les 22 autres à 12 points. Le layout déclare 3 puces statiques que le code remplace.
10. **Contenu brut sous le QR.** La carte « 🔍 CONTENU ENCODÉ / ENCODED PAYLOAD » affiche le texte encodé à 11sp, y compris pour les onglets Compact et FHIR (suite de caractères illisible).
11. **Grille des piliers repliée par défaut.** Seule la première rangée est visible : Patient, Allergies, Médicaments, Contacts d'urgence. La quatrième tuile est un pilier inactif (cadenas).
12. **Emoji comme icônes.** Tous les pictogrammes de rubrique et d'état sont des emoji : rendu variable selon le fabricant et la version d'Android (🫁, 🩹, 🩺, 🧪 sont récents).

## 5. Écarts entre le code et l'attendu

Le guide utilisateur décrit le comportement du code. Les écarts sont notés ici.

| # | Attendu | Ce que fait le code | Où |
|---|---|---|---|
| 1 | Vouvoiement cohérent | Les chaînes françaises mélangent « tu » et « vous » : « Touche un pilier pour le modifier », « Choisis d'abord un médicament », « Es-tu sûr·e… » d'un côté ; « Appuyez sur + », « Créez votre premier profil », « Demandez à un pharmacien ou à un médecin » de l'autre. La fenêtre de conflit de groupe sanguin tutoie (« modifie-le là — vérifie-le d'abord »), les phrases vocales vouvoient | `res/values-fr/*` |
| 2 | Changer la langue dans l'application | La partie « Langue de l'interface » des réglages contient une liste déroulante qui n'est reliée à rien (`settings_lang_picker` n'apparaît dans aucun fichier Kotlin ; commentaire « TODO future delivery »). Aucun `localeConfig` dans le manifeste. L'interface suit la langue du système | `SettingsFragment.kt`, `fragment_settings.xml` |
| 3 | Un bandeau vert « vérifié » | Vert uniquement sur le scan secouriste. Sur le profil et dans les formulaires, « vérifié et propre » = aucun message | § 2 |
| 4 | Message d'import cohérent | Après « Importer », le profil est bien enregistré, mais le message dit « Profil « … » prêt (la persistence arrive en v2.3.0b). » | `qr_import_done_template`, `QrImportScanFragment.kt` |
| 5 | Import de 3 formats | La carte d'accueil annonce « Détection automatique de 3 formats ». Le lecteur n'accepte que le format compact (`_j2:`) et l'ancien JSON `_j 1.2`, plus leurs images multiples. Un QR « Texte » est ignoré sans message ; un QR du troisième onglet aboutit à « QR non lisible ». La consigne affichée est technique : « Cadrez le QR code _j 1.2 émis par JemmaPass » | `JemmaPayloadCodec.detectKind()`, `profiles_cta_qr_subtitle`, `qr_import_hint` |
| 6 | « Partager » partage le QR | Sur l'écran QR, « Partager » génère la carte PDF puis ouvre le partage. `qr_action_pdf` (« Export PDF ») n'est pas utilisée | `QrViewerFragment.setupStubButtons()` |
| 7 | Libellés identiques tuile / écran | 6 piliers sur 11 changent de nom entre la tuile et l'écran (tableau du § 1) | `strings_jemma_pillars.xml` vs `*_edit_title` |
| 8 | Sous-titres à jour | Écran Identité : « FHIR Patient · IPS · 4 piliers actifs ». Fiche d'un pilier inactif : pastille « Actif · édition bientôt » | `perso_edit_hero_subtitle`, `pillar_chip_active` |
| 9 | Aucun jargon à l'écran | Jargon visible par l'utilisateur : onglet « 🏥 FHIR », « Type FHIR », « Substance (ATC / RxNorm) », « Unité (UCUM) », « Substance (SNOMED IPS) », « Médicament (KB DIAMOND) », « Statut FHIR MedicationStatement », « Date de naissance requise (IPS) », « — Conformité IPS complète — », « (given name) », « Identification (FHIR Patient) », puce de format « 📦 _j2 · …B · 1 frame », texte de confidentialité « (Room) ». Le guide a dû les paraphraser | `res/values-fr/*` |
| 10 | Textes traduisibles | Textes en dur dans le code : choix d'export « 📱 QR Codes (Application & Fallback) » et « 📄 Pass PDF A4 (Pliable en 4) » ; titre du partage « Partager le pass PDF / Imprimer » ; « Ouverture de l'assistant… » ; « Erreur d'enregistrement : … » ; « Né(e) le » / « Groupe sanguin » de la fenêtre d'import (fr / ja / en seulement) ; indicateur « 🧩 2 / 5 » ; « 🔍 CONTENU ENCODÉ / ENCODED PAYLOAD » | `ProfileDetailFragment.kt`, `JemmaPdfExporter.kt`, `QrImportScanFragment.kt`, `fragment_qr_viewer.xml` |
| 11 | Alertes en français | Dans la liste et le détail des alertes, la gravité s'affiche en anglais (« MAJOR », « Sévérité : Major ») et la description vient de la base en anglais (« Warfarin interacts with Hypertension »). Constaté dans les rapports des runs `4473749` et `e093854`. La fenêtre d'interaction prévient : « ⓘ Les informations détaillées sont en anglais » ; le détail d'alerte du profil ne le dit pas | `ProfileDetailFragment.showDrugDiseaseBottomSheet()` |
| 12 | Message exact pour un contrôle partiel | Formulaire médicament : pour un contrôle exécuté en partie, le code réutilise le message « … n'a pas pu s'exécuter. Rien n'a été vérifié pour ce médicament. » (commentaire : « no dedicated string yet »). Le formulaire allergie a sa chaîne dédiée `allergy_form_xchk_gap_incomplete` | `MedicationFormBottomSheet.kt` l. 751-755 |
| 13 | Boutons de dialogue uniques | Deux jeux de chaînes pour la même fenêtre : `xchk_save_anyway` / `xchk_cancel` (« Enregistrer quand même » / « Revoir », utilisés) et `xchk_button_save_anyway` / `xchk_button_cancel` (« Sauver quand même » / « Annuler », non utilisés) | `strings_xchk.xml`, `strings_blindage.xml` |
| 14 | Nom de l'application | Le nom affiché est « JemmaAppDemo » (`jemma_app_name`) | `strings_jemma.xml` |
| 15 | Carte PDF dans la langue de l'interface | Langue du résumé local : langue préférée du profil si `fr` ou `ja`, sinon langue du système si `fr` ou `ja`, sinon anglais. Les 22 autres langues du QR texte ne sont pas proposées pour le PDF | `JemmaPdfExporter.exportPdf()` |
| 16 | Rotation | Le rapport `f7c500e` teste la rotation (« état du formulaire préservé »). Le manifeste verrouille aujourd'hui le portrait | `AndroidManifest.xml` |
| 17 | Vocabulaire médical uniforme | Le même pilier s'appelle « Conditions » (carte de profil, section du profil, fiche secouriste), « Pathologies actives » (tuile) et « Problèmes actifs » (écran) | `strings_jemma_profiles.xml`, `strings_jemma_pillars.xml`, `strings_jemma_problems_edit.xml` |

## 6. Chaînes récentes à faire relire par des locuteurs natifs

Chaînes de sécurité ajoutées par les commits `513b637`, `ee830d5`, `178b379`, `8a675b3`. Colonne `en` = `res/values` (langue par défaut). `⏎` = saut de ligne. `—` = forme absente dans cette langue (le japonais et le chinois n'ont pas de forme plurielle « one »).

Points d'attention pour la relecture :

- registre (tu / vous, du / Sie, je / u) : voir écart n° 1 ;
- le français des fenêtres tutoie, celui des phrases vocales vouvoie ;
- « base de connaissances », « contrôle de sécurité » et « vérifié » doivent rester les mêmes d'une chaîne à l'autre ;
- longueur en allemand et en néerlandais (risque n° 3).

#### `strings_jemma_safety_status.xml`

| Clé | fr | en | de | nl | ja | zh |
|---|---|---|---|---|---|---|
| `profile_detail_safety_not_checked` | ⓘ Contrôles de sécurité non effectués — base de connaissances indisponible | ⓘ Safety checks not run — knowledge base unavailable | ⓘ Sicherheitsprüfungen nicht durchgeführt — Wissensdatenbank nicht verfügbar | ⓘ Veiligheidscontroles niet uitgevoerd — kennisbank niet beschikbaar | ⓘ 安全性チェックは未実施です — ナレッジベースを利用できません | ⓘ 未执行安全检查 — 知识库不可用 |
| `profile_detail_safety_incomplete_generic` | ⓘ Contrôles de sécurité incomplets — certains éléments n'ont pas pu être vérifiés | ⓘ Safety checks incomplete — some items could not be verified | ⓘ Sicherheitsprüfungen unvollständig — einige Einträge konnten nicht überprüft werden | ⓘ Veiligheidscontroles onvolledig — sommige items konden niet worden geverifieerd | ⓘ 安全性チェックは未完了です — 一部の項目を確認できませんでした | ⓘ 安全检查不完整 — 部分项目无法核实 |
| `profile_detail_safety_status_hint` | L'absence d'alerte ne signifie pas l'absence de risque. | No alert shown does not mean no risk. | Keine angezeigte Warnung bedeutet nicht, dass kein Risiko besteht. | Geen waarschuwing betekent niet dat er geen risico is. | 警告が表示されていなくても、リスクがないとは限りません。 | 未显示警报并不代表没有风险。 |
| `profile_detail_safety_status_desc` | Attention : %1$s | Warning: %1$s | Achtung: %1$s | Let op: %1$s | 注意：%1$s | 注意：%1$s |
| `profile_detail_safety_incomplete_count [one]` | ⓘ Contrôles de sécurité incomplets — %d élément n'a pas pu être vérifié | ⓘ Safety checks incomplete — %d item could not be verified | ⓘ Sicherheitsprüfungen unvollständig — %d Eintrag konnte nicht überprüft werden | ⓘ Veiligheidscontroles onvolledig — %d item kon niet worden geverifieerd | — | — |
| `profile_detail_safety_incomplete_count [other]` | ⓘ Contrôles de sécurité incomplets — %d éléments n'ont pas pu être vérifiés | ⓘ Safety checks incomplete — %d items could not be verified | ⓘ Sicherheitsprüfungen unvollständig — %d Einträge konnten nicht überprüft werden | ⓘ Veiligheidscontroles onvolledig — %d items konden niet worden geverifieerd | ⓘ 安全性チェックは未完了です — %d 件の項目を確認できませんでした | ⓘ 安全检查不完整 — %d 个项目无法核实 |

#### `strings_jemma_scan_safety.xml`

| Clé | fr | en | de | nl | ja | zh |
|---|---|---|---|---|---|---|
| `tts_scan_not_checked` | Vérification impossible. Ce médicament n'a pas été contrôlé. Demandez à un pharmacien ou à un médecin avant de le donner. | Safety check could not be done. This medication was not verified. Ask a pharmacist or a doctor before giving it. | Sicherheitsprüfung nicht möglich. Dieses Medikament wurde nicht geprüft. Fragen Sie vor der Gabe einen Apotheker oder Arzt. | Veiligheidscontrole niet mogelijk. Dit geneesmiddel is niet gecontroleerd. Vraag een apotheker of arts voordat u het geeft. | 安全確認ができませんでした。この薬は確認されていません。投与する前に薬剤師または医師に相談してください。 | 无法完成安全检查。此药物未经核对。给药前请咨询药剂师或医生。 |
| `tts_scan_incomplete` | Vérification incomplète. Une partie du profil n'a pas pu être contrôlée. Demandez à un pharmacien ou à un médecin avant de le donner. | Safety check incomplete. Part of the profile could not be verified. Ask a pharmacist or a doctor before giving it. | Sicherheitsprüfung unvollständig. Ein Teil des Profils konnte nicht geprüft werden. Fragen Sie vor der Gabe einen Apotheker oder Arzt. | Veiligheidscontrole onvolledig. Een deel van het profiel kon niet worden gecontroleerd. Vraag een apotheker of arts voordat u het geeft. | 安全確認が不完全です。プロフィールの一部を確認できませんでした。投与する前に薬剤師または医師に相談してください。 | 安全检查不完整。部分档案内容无法核对。给药前请咨询药剂师或医生。 |
| `tts_scan_incomplete_note` | La vérification est incomplète. Demandez à un pharmacien ou à un médecin. | The check was incomplete. Ask a pharmacist or a doctor. | Die Prüfung war unvollständig. Fragen Sie einen Apotheker oder Arzt. | De controle was onvolledig. Vraag een apotheker of arts. | 確認は不完全です。薬剤師または医師に相談してください。 | 检查不完整。请咨询药剂师或医生。 |

#### `strings_jemma_verdict_ui.xml`

| Clé | fr | en | de | nl | ja | zh |
|---|---|---|---|---|---|---|
| `livescan_badge_not_verified` | ⚠ Non vérifié — le contrôle de sécurité n'a pas été exécuté | ⚠ Not verified — safety check did not run | ⚠ Nicht geprüft — Sicherheitsprüfung wurde nicht ausgeführt | ⚠ Niet gecontroleerd — veiligheidscontrole is niet uitgevoerd | ⚠ 未確認 — 安全性チェックは実行されていません | ⚠ 未核查 — 安全检查未运行 |
| `livescan_badge_incomplete` | ⚠ Contrôle incomplet — vérification partielle | ⚠ Check incomplete — not fully verified | ⚠ Prüfung unvollständig — nicht vollständig geprüft | ⚠ Controle onvolledig — niet volledig gecontroleerd | ⚠ チェック未完了 — 一部のみ確認済み | ⚠ 检查不完整 — 仅部分核查 |
| `livescan_badge_not_verified_desc` | Attention : le contrôle de sécurité n'a pas été exécuté. Ce médicament n'a pas été vérifié. Demandez à un pharmacien ou à un médecin. | Warning: the safety check did not run. This medication was not verified. Ask a pharmacist or a doctor. | Warnung: Die Sicherheitsprüfung wurde nicht ausgeführt. Dieses Medikament wurde nicht geprüft. Fragen Sie einen Apotheker oder Arzt. | Waarschuwing: de veiligheidscontrole is niet uitgevoerd. Dit geneesmiddel is niet gecontroleerd. Vraag een apotheker of arts. | 警告：安全性チェックは実行されていません。この薬は確認されていません。薬剤師または医師に相談してください。 | 警告：安全检查未运行。此药物未经核查。请咨询药剂师或医生。 |
| `livescan_badge_incomplete_desc` | Attention : le contrôle de sécurité est incomplet. Une partie du profil n'a pas pu être vérifiée. Demandez à un pharmacien ou à un médecin. | Warning: the safety check is incomplete. Part of the profile could not be verified. Ask a pharmacist or a doctor. | Warnung: Die Sicherheitsprüfung ist unvollständig. Ein Teil des Profils konnte nicht geprüft werden. Fragen Sie einen Apotheker oder Arzt. | Waarschuwing: de veiligheidscontrole is onvolledig. Een deel van het profiel kon niet worden gecontroleerd. Vraag een apotheker of arts. | 警告：安全性チェックは不完全です。プロフィールの一部を確認できませんでした。薬剤師または医師に相談してください。 | 警告：安全检查不完整。档案中的部分内容无法核查。请咨询药剂师或医生。 |
| `allergy_form_xchk_gap_title` | ⚠ Contrôle de sécurité incomplet | ⚠ Safety check incomplete | ⚠ Sicherheitsprüfung unvollständig | ⚠ Veiligheidscontrole onvolledig | ⚠ 安全性チェックが不完全です | ⚠ 安全检查不完整 |
| `allergy_form_xchk_gap_not_run` | Le contrôle de sécurité avec les médicaments de ce profil n'a pas pu s'exécuter. Rien n'a été vérifié pour cette allergie. | The safety check against the medications of this profile could not run. Nothing was verified for this allergy. | Die Sicherheitsprüfung gegen die Medikamente dieses Profils konnte nicht ausgeführt werden. Für diese Allergie wurde nichts geprüft. | De veiligheidscontrole met de geneesmiddelen van dit profiel kon niet worden uitgevoerd. Voor deze allergie is niets gecontroleerd. | このプロフィールの薬との安全性チェックを実行できませんでした。このアレルギーについては何も確認されていません。 | 未能对此档案中的药物运行安全检查。此过敏未经任何核查。 |
| `allergy_form_xchk_gap_unchecked_meds` | Ces médicaments n'ont PAS pu être contrôlés avec cette allergie (pas de code, ou recherche en échec) : %1$s | These medications could NOT be checked against this allergy (no code, or the lookup failed): %1$s | Diese Medikamente konnten NICHT gegen diese Allergie geprüft werden (kein Code oder Abfrage fehlgeschlagen): %1$s | Deze geneesmiddelen konden NIET met deze allergie worden gecontroleerd (geen code, of de opzoeking is mislukt): %1$s | 次の薬はこのアレルギーと照合できませんでした（コードがない、または照会に失敗）: %1$s | 以下药物无法与此过敏进行核对（没有编码，或查询失败）：%1$s |
| `allergy_form_xchk_gap_incomplete` | Le contrôle de sécurité avec les médicaments de ce profil n'a été exécuté qu'en partie. Certains médicaments n'ont pas été vérifiés pour cette allergie. | The safety check against the medications of this profile ran only partly. Some medications were not verified for this allergy. | Die Sicherheitsprüfung gegen die Medikamente dieses Profils wurde nur teilweise ausgeführt. Einige Medikamente wurden für diese Allergie nicht geprüft. | De veiligheidscontrole met de geneesmiddelen van dit profiel is slechts gedeeltelijk uitgevoerd. Sommige geneesmiddelen zijn voor deze allergie niet gecontroleerd. | このプロフィールの薬との安全性チェックは一部しか実行されませんでした。一部の薬はこのアレルギーについて確認されていません。 | 对此档案中药物的安全检查仅部分运行。部分药物未针对此过敏进行核查。 |

#### `strings_jemma_blood_conflict.xml`

| Clé | fr | en | de | nl | ja | zh |
|---|---|---|---|---|---|---|
| `result_form_blood_conflict_title` | Le groupe sanguin ne correspond pas au profil | Blood group does not match the profile | Blutgruppe stimmt nicht mit dem Profil überein | Bloedgroep komt niet overeen met het profiel | 血液型がプロフィールと一致しません | 血型与个人资料不一致 |
| `result_form_blood_conflict_message` | Le groupe sanguin du profil est %1$s. Ce résultat indique %2$s.⏎⏎Le groupe sanguin de l'identité fait référence : un profil ne contient qu'un seul groupe sanguin, ce résultat n'est donc pas enregistré.⏎⏎Si le groupe sanguin de l'identité est erroné, modifie-le là — vérifie-le d'abord sur un compte rendu de laboratoire ou une carte de groupe sanguin. | The profile’s blood group is %1$s. This result says %2$s.⏎⏎The blood group in identity is the reference: a profile holds only one blood group, so this result is not saved.⏎⏎If the blood group in identity is wrong, change it there — check it against a laboratory report or a blood group card first. | Die Blutgruppe des Profils ist %1$s. Dieses Ergebnis nennt %2$s.⏎⏎Die Blutgruppe in der Identität ist die Referenz: Ein Profil enthält nur eine Blutgruppe, deshalb wird dieses Ergebnis nicht gespeichert.⏎⏎Ist die Blutgruppe in der Identität falsch, ändere sie dort — prüfe sie vorher anhand eines Laborberichts oder eines Blutgruppenausweises. | De bloedgroep van het profiel is %1$s. Dit resultaat vermeldt %2$s.⏎⏎De bloedgroep in de identiteit is de referentie: een profiel bevat maar één bloedgroep, dus dit resultaat wordt niet opgeslagen.⏎⏎Is de bloedgroep in de identiteit fout, wijzig hem dan daar — controleer hem eerst met een laboratoriumverslag of een bloedgroepkaart. | プロフィールの血液型は %1$s です。この結果は %2$s となっています。⏎⏎本人情報の血液型が基準です。プロフィールに登録できる血液型は1つだけのため、この結果は保存されません。⏎⏎本人情報の血液型が誤っている場合は、そちらで変更してください。変更前に検査報告書または血液型カードで確認してください。 | 个人资料中的血型为 %1$s，此结果为 %2$s。⏎⏎以身份信息中的血型为准：一份资料只能有一个血型，因此不会保存此结果。⏎⏎如果身份信息中的血型有误，请在那里修改——修改前请先对照化验报告或血型卡核实。 |
| `result_form_blood_conflict_change` | Le modifier dans l'identité | Change it in identity | In der Identität ändern | Wijzigen in identiteit | 本人情報で変更する | 在身份信息中修改 |
| `result_form_blood_conflict_cancel` | Annuler | Cancel | Abbrechen | Annuleren | キャンセル | 取消 |
| `result_form_blood_conflict_unreadable` | une valeur illisible | an unreadable value | ein nicht lesbarer Wert | een onleesbare waarde | 読み取れない値 | 无法识别的值 |

#### `medication_form_xchk_gap_* (strings_jemma_medications_edit.xml)`

| Clé | fr | en | de | nl | ja | zh |
|---|---|---|---|---|---|---|
| `medication_form_xchk_gap_title` | ⚠ Contrôle de sécurité incomplet | ⚠ Safety check incomplete | ⚠ Sicherheitsprüfung unvollständig | ⚠ Veiligheidscontrole onvolledig | ⚠ 安全性チェックが不完全です | ⚠ 安全检查不完整 |
| `medication_form_xchk_gap_not_run` | Le contrôle de sécurité (allergies, interactions, pathologies) n'a pas pu s'exécuter. Rien n'a été vérifié pour ce médicament. | The safety check (allergies, interactions, conditions) could not run. Nothing was verified for this medication. | Die Sicherheitsprüfung (Allergien, Wechselwirkungen, Erkrankungen) konnte nicht ausgeführt werden. Für dieses Medikament wurde nichts geprüft. | De veiligheidscontrole (allergieën, interacties, aandoeningen) kon niet worden uitgevoerd. Voor dit geneesmiddel is niets gecontroleerd. | 安全性チェック（アレルギー・相互作用・疾患）を実行できませんでした。この薬については何も確認されていません。 | 安全检查（过敏、相互作用、疾病）未能运行。此药物未经任何核查。 |
| `medication_form_xchk_gap_unresolved` | « %1$s » n'est pas reconnu par la base de connaissances : aucun contrôle de sécurité (allergies, interactions, pathologies) n'a été fait pour lui. | “%1$s” was not recognised by the knowledge base: no safety check (allergies, interactions, conditions) was done for it. | „%1$s“ wurde von der Wissensdatenbank nicht erkannt: Es wurde keine Sicherheitsprüfung (Allergien, Wechselwirkungen, Erkrankungen) dafür durchgeführt. | “%1$s” wordt niet herkend door de kennisbank: er is geen veiligheidscontrole (allergieën, interacties, aandoeningen) voor uitgevoerd. | 「%1$s」は知識ベースで認識されませんでした。この薬の安全性チェック（アレルギー・相互作用・疾患）は行われていません。 | 知识库无法识别“%1$s”：未对其进行安全检查（过敏、相互作用、疾病）。 |
| `medication_form_xchk_gap_uncoded_meds` | Ces médicaments n'ont pas de code et n'ont PAS été inclus dans le contrôle de sécurité : %1$s | These medications have no code and were NOT included in the safety check: %1$s | Diese Medikamente haben keinen Code und wurden NICHT in die Sicherheitsprüfung einbezogen: %1$s | Deze geneesmiddelen hebben geen code en zijn NIET meegenomen in de veiligheidscontrole: %1$s | 次の薬にはコードがないため、安全性チェックの対象に含まれていません: %1$s | 以下药物没有编码，未纳入安全检查：%1$s |

#### `medication_form_route_inhaled (strings_jemma_medications_edit.xml)`

| Clé | fr | en | de | nl | ja | zh |
|---|---|---|---|---|---|---|
| `medication_form_route_inhaled` | 🫁 Inhalée | 🫁 Inhaled | 🫁 Inhalativ | 🫁 Inhalatie | 🫁 吸入 | 🫁 吸入 |
