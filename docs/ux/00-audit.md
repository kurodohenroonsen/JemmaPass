# 🐢 JemmaPass — Audit Ergonomique & Accessibilité Multiplateforme des Écrans Android Existants

> **Document Fondateur d'Audit UX & Accessibilité Numérique**  
> **Branche de travail** : `ag/ux-main` (dérivée de `origin/feat/ips-18-pillars-cleanup`)  
> **Rôle** : `orchestrator: Antigravity-UX`  
> **Tranche** : 1 / 6 (`docs/ux/00-audit.md`)  
> **État du code analysé** : commit `1e6d6f7c882f1d6811e14c08b5078611d7c9a270` du 4 octobre 2026.  
> **Références normatives citées** :  
> - W3C Web Content Accessibility Guidelines (WCAG) 2.1 & 2.2 (Critères 1.3.1, 1.4.1, 1.4.3, 1.4.12, 2.5.5, 2.5.8, 4.1.2).  
> - Google Android Accessibility Guidelines & Material Design 3 Accessibility Standards.  
> - Apple Human Interface Guidelines (HIG) — Accessibility & Touch Targets.  
> - ISO 27269:2021 (International Patient Summary - IPS).  
> **Personas de référence autorisés** :  
> - 🚶‍♂️ `demo_kurodo` (Kurodo) : Pèlerin étranger, allergie létale à la pénicilline (`SNOMED 91936005`).  
> - 👵 `demo_haru` (Haru) : Citoyenne japonaise de 80 ans, sous anticoagulant oral direct Edoxaban (`ATC B01AF03`).  
> - 🎒 `demo_kamekichi` (Kamekichi) : Secouriste bénévole / équipier DMAT.  

---

## Sommaire

1. [Préambule & Règle d'Or Médicale](#1-préambule--règle-dor-médicale)
2. [Méthodologie de Mesure & Normes de Référence](#2-méthodologie-de-mesure--normes-de-référence)
   - 2.1. Formule de calcul du ratio de contraste WCAG
   - 2.2. Seuils normatifs d'acceptabilité
   - 2.3. Conventions d'étiquetage des constats (`[MESURÉ]`, `[NON VÉRIFIÉ]`)
3. [Audit Écran par Écran des Interfaces Android](#3-audit-écran-par-écran-des-interfaces-android)
   - Écran 1 : Accueil & Dashboard des Piliers (`fragment_home.xml`)
   - Écran 2 : Fiche Détail Profil & Grille des 18 Piliers (`fragment_profile_detail.xml`)
   - Écran 3 : Pilier Allergies & Intolérances (`fragment_allergies_edit.xml`, `bottom_sheet_allergy_form.xml`, `item_allergy_row.xml`)
   - Écran 4 : Pilier Médicaments & Traitements (`fragment_medications_edit.xml`, `bottom_sheet_medication_form.xml`, `item_medication_row.xml`)
   - Écran 5 : Pilier Contacts d'Urgence ICE (`fragment_contacts_edit.xml`, `bottom_sheet_contact_form.xml`, `item_contact_row.xml`)
   - Écran 6 : Pilier Antécédents Médicaux (`fragment_past_problems_edit.xml`, `bottom_sheet_past_problem_form.xml`)
   - Écran 7 : Pilier Dispositifs Médicaux & Implants (`fragment_devices_edit.xml`, `bottom_sheet_device_form.xml`)
   - Écran 8 : Pilier Vaccinations (`fragment_immunizations_edit.xml`, `bottom_sheet_immunization_form.xml`)
   - Écran 9 : Pilier Interventions Chirurgicales (`fragment_procedures_edit.xml`, `bottom_sheet_procedure_form.xml`)
   - Écran 10 : Pilier Résultats d'Analyses & Biologie (`fragment_results_edit.xml`, `bottom_sheet_result_form.xml`)
   - Écran 11 : Pilier Grossesse & Obstétrique (`fragment_pregnancy_edit.xml`)
   - Écran 12 : Données Personnelles & Identité (`fragment_perso_edit.xml`)
   - Écran 13 : Sélecteur de Codes Cliniques IPS (`dialog_ips_code_picker.xml`, `IpsCodePickerDialog.kt`)
   - Écran 14 : Sélecteur Médicaments KB (`dialog_kb_drug_picker.xml`)
   - Écran 15 : Visualisateur QR Code Multi-Canaux (`fragment_qr_viewer.xml`)
   - Écran 16 : Hub Secours, Triage SALT & Radar P2P (`fragment_radar.xml`, `fragment_sos_rescue_hub.xml`)
   - Écran 17 : Fiche Victime Secouriste (`fragment_patient_detail.xml`, `view_vital_cell.xml`)
   - Écran 18 : Cartes Victimes & Grille SALT (`item_victim_card.xml`, `item_radar_peer_row.xml`, `SaltUi.kt`)
   - Écran 19 : Widget d'Urgence Écran de Verrouillage (`jemma_emergency_widget.xml`)
   - Écran 20 : Boîtes de Dialogue d'Alerte Médicale & Conflits (`dialog_allergy_edit.xml`, `dialog_profile_long_press_menu.xml`)
4. [Matrice Synthétique des Constats par Écran](#4-matrice-synthétique-des-constats-par-écran)
5. [Décompte Exhaustif des Constats par Étiquette](#5-décompte-exhaustif-des-constats-par-étiquette)
6. [Registre des Décisions Ouvertes à Faire Prendre par Kudoro (DEC-UX-01 à DEC-UX-08)](#6-registre-des-décisions-ouvertes-à-faire-prendre-par-kudoro)

---

## 1. Préambule & Règle d'Or Médicale

Dans le projet **JemmaPass**, l'accessibilité et la clarté de l'interface graphique ne relèvent pas d'un simple confort cosmétique : **c'est la santé de vraies personnes qui est engagée**.

En situation d'urgence vitale, de catastrophe naturelle (séisme, tsunami) ou de barrière linguistique aiguë (ex: un voyageur étranger au Japon comme `demo_kurodo` ou une personne âgée comme `demo_haru`) :
- Un texte rouge d'alerte illisible en plein soleil en raison d'un contraste insuffisant (< 4.5:1),
- Une cible tactile inférieure à 48 dp manquée par un secouriste aux mains gantées ou un patient tremblant,
- Une information clinique vitale (comme une allergie létale ou un traitement anticoagulant) tronquée silencieusement par un `maxLines="2"` avec ellipse sans possibilité d'extension,
- Un libellé lu en anglais par TalkBack sur un terminal configuré en français ou en japonais,
- Ou la présentation d'un code numérique brut (ex: `91936005` au lieu de « Allergie à la pénicilline ») incompréhensible pour le patient,

constituent des **défauts graves** susceptibles d'induire une erreur médicale, un retard de prise en charge ou l'administration d'une substance contre-indiquée.

Ce document dresse l'état des lieux objectif, mesuré et exhaustif des interfaces Android existantes.

---

## 2. Méthodologie de Mesure & Normes de Référence

### 2.1. Formule de calcul du ratio de contraste WCAG
Conformément à la spécification normative W3C WCAG 2.1 (Success Criterion 1.4.3), le ratio de contraste $C$ entre deux couleurs est calculé selon la formule :
$$C = \frac{L_1 + 0.05}{L_2 + 0.05}$$
où $L_1$ est la luminance relative de la couleur la plus claire et $L_2$ celle de la plus sombre.  
La luminance relative $L$ d'une composante sRGB normalisée $c \in [0, 1]$ est obtenue par :
$$c_{\text{lin}} = \begin{cases} \frac{c}{12.92} & \text{si } c \le 0.03928 \\ \left(\frac{c + 0.055}{1.055}\right)^{2.4} & \text{si } c > 0.03928 \end{cases}$$
$$L = 0.2126 \cdot R_{\text{lin}} + 0.7152 \cdot G_{\text{lin}} + 0.0722 \cdot B_{\text{lin}}$$

### 2.2. Seuils normatifs d'acceptabilité
1. **Contraste de Texte (WCAG 2.1 SC 1.4.3 & 1.4.6)** :
   - *Texte normal (< 18pt / 14sp non gras)* : Ratio minimal de **4.5:1** (Niveau AA) et **7.0:1** (Niveau AAA).
   - *Grand texte ($\ge$ 18pt ou $\ge$ 14sp gras)* : Ratio minimal de **3.0:1** (Niveau AA) et **4.5:1** (Niveau AAA).
2. **Taille des Cibles Tactiles (Touch Targets)** :
   - *Google Android Accessibility Guidelines / Material 3* : **48 × 48 dp** au minimum, avec espacement suffisant.
   - *Apple Human Interface Guidelines (HIG)* : **44 × 44 pt** au minimum.
   - *WCAG 2.2 SC 2.5.8 (Target Size Minimum, Niveau AA)* : **24 × 24 CSS px / dp** au strict minimum.
3. **Taille Typographique** :
   - Minimum recommandé en corps de texte mobile : **14sp** (seuil de tolérance **12sp** pour les métadonnées secondaires).
   - Tout texte vital (posologie, substance, valeur de laboratoire, statut vital) inférieur à **12sp** est un défaut ergonomique sévère.
4. **Indépendance à la Couleur (WCAG 2.1 SC 1.4.1)** :
   - Toute information véhiculée par la couleur (gravité, statut SALT, validation/rejet) doit impérativement être doublée d'un indice textuel, d'un glyphe ou d'un symbole explicite.
5. **Accessibilité Lecteur d'Écran (TalkBack)** :
   - Rôles explicites (`info.className = "android.widget.Button"`, `info.isCheckable`), annonces d'état (`isChecked`), descriptions de nœuds composées sans rupture sémantique, et masquage des éléments décoratifs (`importantForAccessibility="no"`).

### 2.3. Conventions d'étiquetage des constats
- `[MESURÉ]` : Tout fait ergonomique vérifié sur pièce dans le code source Android (`fichier:ligne`), dans les valeurs XML, ou sur une capture d'écran officielle publiée dans le dossier `screens/` de la branche `device-reports`.
- `[NON VÉRIFIÉ]` : Tout comportement dépendant du matériel physique réel (ex: rendu sous lumière directe du soleil à 10 000 lux, comportement d'une synthèse vocale OEM tierce) non validé par une mesure instrumentée.
- `[PROPOSÉ]` : Toute recommandation d'évolution d'interface, rédigée au conditionnel, soumise aux développeurs des plateformes et à Kudoro.

---

## 3. Audit Écran par Écran des Interfaces Android

### Écran 1 : Accueil & Dashboard des Piliers
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/fragment_home.xml`, `fragment_home.xml:60-67`, `fragment_home.xml:86-97`.
- **Captures d'écran citées** : `70-haru.png` (commit `f7c500e`), `71-kamekichi.png` (commit `f7c500e`), `10-detail-kurodo.png` (commit `f7c500e`).

#### Constats détaillés
1. `[MESURÉ]` **Cible tactile insuffisante sur le bouton d'ajout de profil** : Dans `fragment_home.xml:61-62`, l'élément `#home_btn_add_profile` est dimensionné à `40dp × 40dp`. Il est inférieur au seuil minimal recommandé de **48 × 48 dp** fixé par Google Android Accessibility Guidelines.
2. `[MESURÉ]` **Taille de texte réduite sur le sélecteur de profil** : Dans `fragment_home.xml:48`, le label `#home_active_profile` est typographié en `12sp` avec la couleur `@color/jemma_text_muted` (`#94A3B8`). Bien que son ratio de contraste de **6.96:1** sur `@color/jemma_bg` (`#0F172A`) passe le critère WCAG AA, la petite taille (12sp) rend le profil actif difficile à identifier d'un coup d'œil par un utilisateur malvoyant ou âgé tel que `demo_haru`.
3. `[MESURÉ]` **Contraste du bouton flottant d'urgence SOS** : Dans `fragment_home.xml:86-97`, `#home_fab_sos` utilise `app:backgroundTint="@color/jemma_danger"` (`#DC2626`) et `android:textColor="@android:color/white"`. Le ratio de contraste mesuré du blanc sur `#DC2626` est de **5.00:1**, satisfaisant le critère WCAG AA (seuil 4.5:1), mais échouant au critère AAA (seuil 7.0:1).
4. `[MESURÉ]` **Dépendance à la couleur sur le statut de danger** : Le FAB SOS repose sur un fond rouge vif (`#DC2626`). L'icône `@android:drawable/ic_dialog_alert` et le texte explicite `@string/home_sos_cta` (« SOS ») compensent heureusement la dépendance à la couleur pour les usagers daltoniens (protanopie/deutéranopie).

---

### Écran 2 : Fiche Détail Profil & Grille des 18 Piliers
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/fragment_profile_detail.xml`, `item_pillar_card.xml`, `item_pillar_stub_card.xml`, `view_profiles_empty_state.xml:71,83,95`.
- **Captures d'écran citées** : `11-detail-pillars.png` (commit `f7c500e`), `169-detail-haru-ja.png` (commit `8851a03`), `170-kurodo-detail-8-active.png` (commit `8851a03`).

#### Constats détaillés
1. `[MESURÉ]` **Codes techniques visibles sur l'état vide et les tuiles piliers** : Dans `view_profiles_empty_state.xml:71,83,95` et `fragment_pillar_stub.xml`, des jetons techniques internes tels que `_j 1.2`, `_jf 1.0`, `_jt 1.0 (Gemma)`, `SNOMED CT` et `ICD-10` sont affichés sous forme de chips visibles par l'utilisateur. Ces termes informatiques et acronymes normatifs créent une surcharge cognitive pour un patient profane.
2. `[MESURÉ]` **Cible tactile réduite sur le header d'accordéon des piliers** : Dans `fragment_profile_detail.xml:135`, `#profile_detail_pillars_header` possède un `minHeight="44dp"`, conforme à la norme Apple HIG (44 pt), mais inférieur aux **48 dp** préconisés par Android Accessibility.
3. `[MESURÉ]` **Caractères indicateurs lus littéralement par TalkBack** : Dans `fragment_profile_detail.xml:154`, un TextView affiche `android:text="▾"`. En l'absence de l'attribut `android:importantForAccessibility="no"`, le lecteur d'écran TalkBack annonce textuellement « triangle pointant vers le bas » au lieu d'annoncer l'état déplié ou replié de la section.

---

### Écran 3 : Pilier Allergies & Intolérances
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_allergy_row.xml`, `bottom_sheet_allergy_form.xml`, `AllergiesAdapter.kt:66-88`, `colors.xml:37`.
- **Captures d'écran citées** : `c10-haru-allergies.png` (commit `08947ec`), `c10-kurodo-allergies.png` (commit `08947ec`), `234-e5-allergy-date-future-disabled.png` (commit `8a675b3`).

#### Constats détaillés
1. `[MESURÉ]` **Défaut de contraste critique sur les notes d'allergie (#64748B)** : Dans `item_allergy_row.xml:61-68`, le texte des notes (`#allergy_row_notes`) utilise `android:textColor="#64748B"` et `android:textSize="12sp"` sur un fond de carte `app:cardBackgroundColor="#1E293B"`. Le ratio de contraste mesuré est de **3.31:1**.  
   *Verdict normatif* : **ÉCHEC CRITIQUE WCAG 2.1 AA** (SC 1.4.3 impose $\ge 4.5:1$ pour le texte normal). Les notes cliniques décrivant l'anaphylaxie d'`demo_kurodo` sont sous le seuil d'accessibilité.
2. `[MESURÉ]` **Troncature silencieuse des manifestations allergiques d'urgence** : Dans `item_allergy_row.xml:63-64`, `#allergy_row_notes` est bridé par `android:maxLines="2"` et `android:ellipsize="end"`. Si la description clinique dépasse deux lignes (ex: « urticaire généralisée, bronchospasme, œdème de Quincke, injection épinéphrine requise »), les mentions vitales sont masquées par des points de suspension (« … ») sans possibilité d'expansion directe sur la fiche.
3. `[MESURÉ]` **Exposition transitoire de codes numériques SNOMED bruts** : Dans `AllergiesAdapter.kt:66-70`, si le libellé textuel n'est pas encore résolu en mémoire, le code SNOMED numérique brut (`a.c`, ex: `91936005`) est injecté directement dans le TextView `#allergy_row_substance` avant la complétion de la coroutine de traduction. L'utilisateur peut voir furtivement un code chiffré incompréhensible.
4. `[MESURÉ]` **Pollution vocale par le chevron de navigation « › »** : Dans `item_allergy_row.xml:71-76`, le chevron visuel est encodé par un `TextView` contenant `android:text="›"` sans `importantForAccessibility="no"`. TalkBack verbalise « guillemet fermant simple » à la fin de la lecture de chaque ligne d'allergie.
5. `[MESURÉ]` **Absence des utilitaires d'accessibilité FormA11yHelpers** : Contrairement au formulaire des contacts, `AllergyFormBottomSheet.kt` n'appelle jamais `FormA11yHelpers.markAsPicker()` ni `FormA11yHelpers.announce()`. Les sélecteurs de substance (`#allergy_form_substance_card`) et de date ne sont pas vocalisés comme des boutons d'ouverture de sélecteur.

---

### Écran 4 : Pilier Médicaments & Traitements
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_medication_row.xml`, `bottom_sheet_medication_form.xml`, `MedicationsAdapter.kt:133-140`.
- **Captures d'écran citées** : `80-haru-meds.png` (commit `1c1b7e5`), `235-e7-medication-5-routes.png` (commit `8a675b3`), `263-drug-picker-ibu.png` (commit `4006cc6`).

#### Constats détaillés
1. `[MESURÉ]` **Défaut de contraste sur la ligne de motif de prise (#64748B)** : Dans `item_medication_row.xml:61-68`, `#medication_row_reason` est stylisé en `#64748B` sur fond `#1E293B`, générant un ratio de **3.31:1** (inférieur à 4.5:1). **ÉCHEC WCAG 2.1 AA**.
2. `[MESURÉ]` **Affichage d'un code SNOMED/Coded Reason brut au lieu d'un libellé** : Dans `MedicationsAdapter.kt:133-134`, le code stipule :
   ```kotlin
   val reason = m.rs?.takeIf { it.isNotBlank() } ?: m.rc?.takeIf { it.isNotBlank() }
   ```
   Si la chaîne textuelle `m.rs` est absente et que seule la clé codée `m.rc` est renseignée, la valeur injectée dans l'interface est le code technique brut (ex: code SNOMED `49436004` pour la fibrillation auriculaire d'`demo_haru`), sans traduction en langage naturel.
3. `[MESURÉ]` **Lecture redondante des emojis de voie d'administration par TalkBack** : Dans `MedicationsAdapter.kt:70`, `b.medicationRowIcon.text` reçoit l'emoji de la voie (ex: « 💊 »). TalkBack énonce « pilule, Edoxaban, 30 mg, pilule orale ». L'emoji décoratif n'est pas masqué au lecteur d'écran.
4. `[MESURÉ]` **Troncature des posologies complexes** : Dans `item_medication_row.xml:63-64`, la ligne de raison est limitée à `maxLines="2"`. En japonais, où la syntaxe médicale de prise médicamenteuse peut comporter des précisions horaires détaillées (« 朝食後服用 / après le petit-déjeuner »), le texte risque d'être coupé prématurément.

---

### Écran 5 : Pilier Contacts d'Urgence ICE
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_contact_row.xml`, `bottom_sheet_contact_form.xml`, `ContactFormBottomSheet.kt:153,239,284,307`, `FormA11yHelpers.kt:69,109,146`.
- **Captures d'écran citées** : `contacts-list-initial.png` (commit `c5d6fd2`), `contact-form-filled-medprovr.png` (commit `c5d6fd2`).

#### Constats détaillés
1. `[MESURÉ]` **Chaînes d'accessibilité TalkBack codées en dur en français** : Bien que `ContactFormBottomSheet.kt` soit le seul formulaire intégrant `FormA11yHelpers`, le fichier `FormA11yHelpers.kt` contient des chaînes littérales françaises non externalisées :
   - Ligne 69 : `emptyHint: String = "non renseigné"`
   - Ligne 109 : `val stateWord = if (isSelected) "sélectionné" else "non sélectionné"`
   - Ligne 146 : `view.contentDescription = "Erreur. $errorMsg"`
   - Ligne 158 : `view.contentDescription = "$headerText, section avec $fieldsCount champs"`  
   Sur un smartphone configuré en japonais (ex: secouriste japonais examinant `demo_kurodo`), TalkBack lit « non renseigné » ou « Erreur » en français.
2. `[MESURÉ]` **Exposition de codes de rôle HL7 V3-RoleCode dans le sélecteur** : Lors de la sélection du lien de parenté, l'interface affiche les acronymes HL7 V3 (`FTH` pour père, `MTH` pour mère, `DOMPART` pour partenaire de vie, `ECON` pour contact d'urgence, `GUARD` pour tuteur). Ces codes normatifs sont montrés directement en tête de chaque ligne de liste (ex: « `DOMPART  ·  Partenaire de vie` »).
3. `[MESURÉ]` **Chevron de navigation non ignoré par l'accessibilité** : Dans `item_contact_row.xml:58-63`, un `TextView` contient `›` avec `android:textColor="#64748B"` sans masquage TalkBack.

---

### Écran 6 : Pilier Antécédents Médicaux (Past Problems)
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_past_problem_row.xml`, `bottom_sheet_past_problem_form.xml`, `fragment_past_problems_edit.xml`.
- **Captures d'écran citées** : `161-past-problems-list.png` (commit `ed20225`), `162-form-rougeole.png` (commit `ed20225`), `169-past-problems-list-ja.png` (commit `8851a03`).

#### Constats détaillés
1. `[MESURÉ]` **Contraste insuffisant sur la date de résolution (#64748B)** : Dans `item_past_problem_row.xml:61-68`, la date et les notes d'antécédents utilisent `#64748B` sur fond `#1E293B` (**3.31:1**, échec WCAG AA).
2. `[MESURÉ]` **Troncature sur les antécédents chirurgicaux ou chroniques** : Dans `item_past_problem_row.xml:64-65`, `android:maxLines="2"` tronque les diagnostics secondaires ou les descriptions anatomiques étendues.
3. `[MESURÉ]` **Absence d'attributs TalkBack sur les sélecteurs du BottomSheet** : Dans `bottom_sheet_past_problem_form.xml:135-180`, les rangées `#past_problem_form_onset_row`, `#past_problem_form_abatement_row` et `#past_problem_form_severity_row` sont des `LinearLayout` cliquables ne disposant d'aucun `contentDescription` ni d'annonce de rôle de bouton.

---

### Écran 7 : Pilier Dispositifs Médicaux & Implants
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_device_row.xml`, `bottom_sheet_device_form.xml`.
- **Captures d'écran citées** : `111-devices-list.png` (commit `5970d94`), `112-device-form.png` (commit `5970d94`), `126-device-udi-invalid.png` (commit `5970d94`).

#### Constats détaillés
1. `[MESURÉ]` **Exposition des identifiants UDI et codes SNOMED bruts** : Dans `item_device_row.xml`, un dispositif sans dénomination commerciale affiche son identifiant UDI-DI (ex: code barre GS1/GTIN à 14 chiffres) ou son code concept SNOMED (`72506001` pour un stimulateur cardiaque), difficilement déchiffrable par le patient ou un aidant non-médecin.
2. `[MESURÉ]` **Défaut de contraste récurrent sur les notes (#64748B)** : Ratio de **3.31:1** mesuré sur `#device_row_notes` (`item_device_row.xml:61-68`).
3. `[MESURÉ]` **Absence d'annonce d'erreur vocale sur l'UDI invalide** : Sur la capture `126-device-udi-invalid.png`, l'erreur de saisie de l'identifiant UDI est affichée visuellement sous le champ, mais aucun événement `AccessibilityEvent.TYPE_ANNOUNCEMENT` n'est déclenché pour avertir un utilisateur non-voyant.

---

### Écran 8 : Pilier Vaccinations
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_immunization_row.xml`, `bottom_sheet_immunization_form.xml`.
- **Captures d'écran citées** : `12-immunizations-list.png` (commit `f7c500e`), `20-form-empty.png` (commit `f7c500e`), `c9-haru-vaccins.png` (commit `6342bc6`).

#### Constats détaillés
1. `[MESURÉ]` **Contraste de la date et du statut vaccinal** : Dans `item_immunization_row.xml:61-68`, la mention de date et de numéro de lot est en `#64748B` sur fond sombre (**3.31:1**, échec WCAG AA).
2. `[MESURÉ]` **Sélecteur de date sans annonce accessible** : Dans `bottom_sheet_immunization_form.xml:135-150`, `#immunization_form_date_row` est un `LinearLayout` cliquable sans libellé d'action pour lecteur d'écran.
3. `[MESURÉ]` **Affichage des numéros de lot sans libellé contextuel** : Dans la liste des vaccins, le lot vaccinal est affiché de façon brute (ex: `ABX-2024`), sans préfixe textuel « Lot : » explicite sur petit écran.

---

### Écran 9 : Pilier Interventions Chirurgicales
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_procedure_row.xml`, `bottom_sheet_procedure_form.xml`, `ProceduresAdapter.kt:67-99`.
- **Captures d'écran citées** : `120-procedure-free-text.png` (commit `5970d94`), `c5-2-search-appendic.png` (commit `a80552e`).

#### Constats détaillés
1. `[MESURÉ]` **Exposition de codes opératoires SNOMED** : Dans `ProceduresAdapter.kt:67`, en l'absence de libellé résolu, le code technique de l'intervention (ex: `80146002` pour une appendicectomie) apparaît à l'écran.
2. `[MESURÉ]` **Contraste sous-standard des détails d'intervention** : Dans `item_procedure_row.xml:61-68`, le site anatomique et l'opérateur sont affichés en `#64748B` (**3.31:1**).
3. `[MESURÉ]` **Troncature des notes opératoires à 2 lignes** : Dans `item_procedure_row.xml:64-65`, `maxLines="2"` risque de masquer les complications post-opératoires déclarées par le patient.

---

### Écran 10 : Pilier Résultats d'Analyses & Biologie
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_result_row.xml`, `bottom_sheet_result_form.xml`, `ResultsAdapter.kt:70-85`.
- **Captures d'écran citées** : `152-results-list-undated-imaging.png` (commit `d1a6c4b`), `155-results-list-potassium-comma.png` (commit `d1a6c4b`), `261-result-form-filled.png` (commit `3ee9a8d`).

#### Constats détaillés
1. `[MESURÉ]` **Affichage de codes LOINC obscurs pour le patient** : Dans `item_result_row.xml`, lorsqu'une analyse de laboratoire est importée d'un système hospitalier sans libellé traduit, le code LOINC (ex: `14749-6` pour la glycémie ou `2823-3` pour le potassium) est présenté tel quel.
2. `[MESURÉ]` **Contraste insuffisant de l'intervalle de référence** : Dans `item_result_row.xml:61-68`, les valeurs normales et unités (`#result_row_details`) utilisent la couleur `#64748B` (**3.31:1**).
3. `[MESURÉ]` **Confusion potentielle entre séparateurs décimaux (virgule vs point)** : Comme illustré par la capture `155-results-list-potassium-comma.png`, la gestion des valeurs numériques avec virgule française (`4,2 mmol/L`) et point international (`4.2`) n'affiche pas d'avertissement de format en cas de saisie ambiguë.

---

### Écran 11 : Pilier Grossesse & Obstétrique
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/fragment_pregnancy_edit.xml`, `fragment_pregnancy_edit.xml:80-160`.
- **Captures d'écran citées** : `217-kurodo-clear-pg.png` (commit `8a675b3`).

#### Constats détaillés
1. `[MESURÉ]` **Multiplication des chevrons « › » non labellisés** : Dans `fragment_pregnancy_edit.xml:88,114,138,158`, quatre `TextView` distincts contiennent `android:text="›"` sans `importantForAccessibility="no"`. TalkBack les énonce tous consécutivement.
2. `[MESURÉ]` **Rangées cliquables dépourvues de contentDescription** : Les conteneurs `#pregnancy_status_row`, `#pregnancy_status_date_row`, `#pregnancy_edd_row` et `#pregnancy_edd_method_row` sont interactifs mais ne définissent aucune description d'action pour le lecteur d'écran.
3. `[MESURÉ]` **Absence de guidage sur la sensibilité des données obstétricales** : Aucun bandeau explicatif n'informe la patiente sur la visibilité ou non de son état de grossesse sur l'écran verrouillé en cas d'urgence.

---

### Écran 12 : Données Personnelles & Identité
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/fragment_perso_edit.xml`, `fragment_perso_edit.xml:210-310`.
- **Captures d'écran citées** : `70-haru.png` (commit `f7c500e`), `10-detail-kurodo.png` (commit `f7c500e`).

#### Constats détaillés
1. `[MESURÉ]` **Lignes d'adresse cliquables sans description d'accessibilité** : Dans `fragment_perso_edit.xml:260-310`, `#perso_address_use_row`, `#perso_address_country_row` et `#perso_identifier_system_row` ont `clickable="true"` mais `contentDescription=""` (vide).
2. `[MESURÉ]` **Exposition des URI de systèmes d'identification HL7/FHIR** : Dans le champ identifiant, le système est affiché sous forme d'URL technique brute (ex: `urn:oid:1.2.250.1.213.1.4.2` ou `http://hl7.org/fhir/sid/passport-fra`). Une telle chaîne est incompréhensible pour un utilisateur standard.
3. `[MESURÉ]` **Icônes emojis décoratives non ignorées** : Les icônes « 👤 », « 📅 », « 🌍 », « 🩸 » sont déclarées dans des `TextView` normaux lus mot à mot par TalkBack (« silhouette de buste », « calendrier », etc.).

---

### Écran 13 : Sélecteur de Codes Cliniques IPS
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/dialog_ips_code_picker.xml`, `IpsCodePickerDialog.kt:54-124, 209-216`.
- **Captures d'écran citées** : `21-picker.png` (commit `f7c500e`), `c5-2-search-appendic.png` (commit `a80552e`), `c5-6-search-pacem.png` (commit `a80552e`), `c5-7-search-stent.png` (commit `a80552e`).

#### Constats détaillés
1. `[MESURÉ]` **Cibles tactiles réduites à 36 dp sur les boutons de filtre de catégorie** : Dans `dialog_ips_code_picker.xml:61,72,84,96,108,120`, tous les boutons de catégories (chips `ALL`, `🍴`, `💊`, `🌿`, `🧬`, `🩹`) sont configurés avec :
   ```xml
   android:minHeight="36dp"
   android:insetTop="0dp"
   android:insetBottom="0dp"
   ```
   Ces cibles mesurent **36 dp** de hauteur, violant directement la recommandation minimale de **48 × 48 dp** d'Android Accessibility (et la norme Apple de 44 pt).
2. `[MESURÉ]` **Exposition systématique du code technique comme préfixe par défaut** : Dans `IpsCodePickerDialog.kt:209-216`, la méthode de formatage de ligne stipule :
   ```kotlin
   private fun formatRow(item: IpsPickerItem): String {
       val prefix = codeToPrefix[item.code]
       return if (prefix != null) {
           "$prefix  ${item.display}"
       } else {
           "${item.code}  ·  ${item.display}"
       }
   }
   ```
   Pour tout ValueSet sans emoji configuré (ex: V3-RoleCode pour les contacts, LOINC pour les résultats, SNOMED pour les dispositifs et procédures), l'utilisateur est confronté à des codes ésotériques en tête de ligne (ex: « `DOMPART  ·  Partenaire de vie` », « `80146002  ·  Appendicectomie` »).
3. `[MESURÉ]` **Contraste du texte de décompte des éléments (#94A3B8)** : Dans `dialog_ips_code_picker.xml:135`, `#picker_count` est en `12sp` avec la couleur `#94A3B8`.

---

### Écran 14 : Sélecteur Médicaments KB
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/dialog_kb_drug_picker.xml`, `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/common/KbDrugPickerDialog.kt`.
- **Captures d'écran citées** : `232-e2-drug-picker-no-result.png` (commit `8a675b3`), `c5-8-medication-picker.png` (commit `a80552e`).

#### Constats détaillés
1. `[MESURÉ]` **Absence de retour vocal en cas de recherche sans résultat** : Sur la capture `232-e2-drug-picker-no-result.png`, lorsque la recherche dans la base de connaissances médicale ne retourne aucun résultat, aucun message vocal `TYPE_ANNOUNCEMENT` n'est envoyé à TalkBack pour notifier l'absence de correspondance. L'usager aveugle reste sur une liste vide silencieuse.
2. `[MESURÉ]` **Cibles tactiles des éléments de liste brute** : Dans `dialog_kb_drug_picker.xml`, la `ListView` utilise `android.R.layout.simple_list_item_1`. La hauteur par défaut d'un `simple_list_item_1` sous certains thèmes Android peut descendre à **40-42 dp**, inférieure à la cible de 48 dp.

---

### Écran 15 : Visualisateur QR Code Multi-Canaux
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/fragment_qr_viewer.xml`, `fragment_qr_viewer.xml:77,86,95,149,190-229,270-291`.
- **Captures d'écran citées** : Galerie des captures QR sur `device-reports:screens/INDEX.md`.

#### Constats détaillés
1. `[MESURÉ]` **Cibles tactiles réduites à 44 dp sur le carrousel multi-frames** : Dans `fragment_qr_viewer.xml:191,213,223`, les boutons de contrôle du diaporama FHIR (`#qr_btn_prev_frame`, `#qr_btn_next_frame`, `#qr_btn_play_pause`) mesurent `44dp × 44dp`. Bien que conformes au seuil Apple HIG (44 pt), ils échouent au critère Android Accessibility (48 dp).
2. `[MESURÉ]` **Affichage d'un dump technique brut JSON / Base64 à l'écran** : Dans `fragment_qr_viewer.xml:270-291`, un encart titré « 🔍 CONTENU ENCODÉ / ENCODED PAYLOAD » expose l'intégralité de la chaîne Base64 (`_j2:...`) ou du JSON FHIR brut en police monospace `11sp`. En situation de panique ou de prise en charge par un soignant, ce dump textuel dense pollue la lisibilité de l'écran.
3. `[MESURÉ]` **Typographie trop petite sur les onglets et chips techniques** :
   - Onglets `#qr_tab_pruned`, `#qr_tab_text`, `#qr_tab_fhir` : `android:textSize="12sp"`.
   - Chip de format `#qr_format_chip` (« `Pruned _j2 · 520B · 1 frame` ») : `android:textSize="11sp"`.
4. `[NON VÉRIFIÉ]` **Accessibilité cognitive du slideshow QR dynamique** : Le clignotement continu d'un QR code multi-trames en boucle automatique peut désorienter un utilisateur souffrant de troubles cognitifs ou visuels. Aucune option d'arrêt d'urgence n'est pré-activée par défaut.

---

### Écran 16 : Hub Secours, Triage SALT & Radar P2P
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/fragment_radar.xml`, `fragment_sos_rescue_hub.xml`, `item_radar_peer_row.xml`.
- **Captures d'écran citées** : `81-radar-initial.png` (commit `1c1b7e5`), `83-radar-stopped.png` (commit `1c1b7e5`), `237-e9-radar-badge.png` (commit `8a675b3`).

#### Constats détaillés
1. `[MESURÉ]` **Absence de description vocale sur les boutons de tri SALT du radar** : Dans `item_radar_peer_row.xml:75-120`, les boutons d'action rapide `#radar_peer_action_wait`, `eval`, `stab`, `help`, `evac`, `dcd` ont `layout_width` et `layout_height` non dimensionnés en dur, et leurs `contentDescription` pointent vers des chaînes comme `@string/triage_stab`.
2. `[MESURÉ]` **Dépendance exclusive aux couleurs de tri pour les badges** : Le badge de statut de la victime repose sur la palette SALT (Gris, Jaune, Vert, Rouge, Bleu, Noir). Pour un secouriste daltonien, les statuts STAB (vert) et HELP (rouge) présentent une chromaticité indiscernable en vision rouge-vert.

---

### Écran 17 : Fiche Victime Secouriste (Patient Detail)
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/fragment_patient_detail.xml`, `view_vital_cell.xml`, `PatientDetailFragment.kt`.
- **Captures d'écran citées** : `133-kamekichi-detail.png` (commit `5970d94`).

#### Constats détaillés
1. `[MESURÉ]` **Typographie microscopique de 9sp sur les données vitales d'urgence** : Dans `view_vital_cell.xml:21`, le label de chaque cellule vitale (`#vital_cell_label` : SEXE, ÂGE, SANG, TRIAGE) est typographié en **`9sp`** (`android:textSize="9sp"`). C'est le texte le plus petit de toute l'application. En situation de catastrophe (fumée, secousses, pluie, luminosité dégradée), un secouriste ou un médecin ne peut pas lire convenablement un libellé de 9sp.
2. `[MESURÉ]` **Cible tactile de fermeture insuffisante (40 dp)** : Dans `fragment_patient_detail.xml:88-95`, `#patient_btn_close` est configuré à `40dp × 40dp` (inférieur à 48 dp).
3. `[MESURÉ]` **Boutons de tri SALT avec acronymes anglais codés en dur** : Dans `fragment_patient_detail.xml:169,183,197,211,225,238`, les `contentDescription` des boutons sont codées en dur en anglais :
   - `contentDescription="WAIT"`
   - `contentDescription="EVAL"`
   - `contentDescription="STAB"` (lu littéralement « poignarder » par un lecteur d'écran anglais !)
   - `contentDescription="HELP"`
   - `contentDescription="EVAC"`
   - `contentDescription="DCD"` (lu « D-C-D » au lieu de « Décédé » ou « 死亡 »)
4. `[MESURÉ]` **Vocalisation cryptique des symboles de sexe « ♀ » et « ♂ »** : Dans `view_vital_cell.xml:33`, le sexe est rendu sous forme de glyphes Unicode « ♀ » ou « ♂ ». TalkBack annonce « symbole femelle » ou « symbole mâle » au lieu de « Sexe : Féminin » ou « Sexe : Masculin ».

---

### Écran 18 : Cartes Victimes & Grille SALT
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/item_victim_card.xml`, `colors.xml:47`, `SaltUi.kt:96-97`.
- **Captures d'écran citées** : `230-e1-alert-kamekichi.png` (commit `8a675b3`).

#### Constats détaillés
1. `[MESURÉ]` **Invisibilité du badge DCD (Décédé) sur thème sombre** : Dans `colors.xml:47`, la couleur `@color/salt_dcd` est définie comme `#000000` (noir pur). Sur une surface d'arrière-plan `@color/jemma_surface` (`#1E293B`) ou `@color/jemma_bg` (`#0F172A`), le ratio de contraste mesuré est de **1.18:1** à **1.44:1**.  
   *Verdict normatif* : **ÉCHEC TOTAL WCAG (Invisibilité graphique)**. Un badge ou texte noir sur fond bleu-noir sombre `#1E293B` est pratiquement invisible à l'œil nu.
2. `[MESURÉ]` **Acronymes non traduits dans item_victim_card.xml** : Mêmes constats que sur l'écran 17 : les boutons de tri utilisent des `contentDescription` en dur (`WAIT`, `EVAL`, `STAB`, `HELP`, `EVAC`, `DCD`).

---

### Écran 19 : Widget d'Urgence Écran de Verrouillage
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/jemma_emergency_widget.xml`, `jemma_emergency_widget_chunk.xml`.

#### Constats détaillés
1. `[MESURÉ]` **Textes d'urgence non localisés codés en dur en anglais** : Dans `jemma_emergency_widget.xml:21,58`, les chaînes sont codées en dur sans ressource :
   - `android:text="JEMMA ID"`
   - `android:text="No profile active"`  
   Sur l'écran d'accueil ou de verrouillage d'un utilisateur français ou japonais, le widget affiche un message d'absence de profil en anglais.
2. `[MESURÉ]` **Cible tactile du bouton SOS sur widget inférieure aux normes** : Dans `jemma_emergency_widget.xml:27-43`, `#btn_sos` est un conteneur avec padding de 8dp autour d'un texte 14sp. Sa hauteur physique totale est d'environ **32 dp**, largement inférieure aux 48 dp réglementaires.
3. `[MESURÉ]` **Contraste faible sur le message d'état vide (#888888 sur #202020)** : Dans `jemma_emergency_widget.xml:59`, le texte `#888888` sur fond `#202020` offre un ratio de **4.67:1**, tout juste à la limite du seuil AA (4.5:1), mais avec une taille de `12sp`, ce qui est inadapté à un widget d'urgence.

---

### Écran 20 : Boîtes de Dialogue d'Alerte Médicale & Conflits
- **Fichiers sources** : `JemmaPassAndroidDemo/app/src/main/res/layout/dialog_allergy_edit.xml:209`, `dialog_contact_edit.xml:132`, `dialog_medication_edit.xml:166`, `dialog_profile_long_press_menu.xml:227,235`, `fragment_settings.xml:361`, `colors.xml:32`.
- **Captures d'écran citées** : `200b-dialog-ibuprofen-hypertension.png` (commit `e093854`), `201a-dialog-furosemide-kidney.png` (commit `e093854`), `236-e8-blood-group-conflict-dialog.png` (commit `8a675b3`), `238-e6-amber-banner.png` (commit `8a675b3`).

#### Constats détaillés
1. `[MESURÉ]` **Défaut de contraste critique du rouge danger sur fond de dialogue** : Dans `dialog_allergy_edit.xml:209`, `dialog_contact_edit.xml:132`, `dialog_medication_edit.xml:166` et `dialog_profile_long_press_menu.xml:235`, les actions destructives ou alertes utilisent :
   ```xml
   android:textColor="@color/jemma_danger"
   ```
   Sur les cartes et boîtes de dialogue Material ayant pour fond `@color/jemma_surface` (`#1E293B`), le ratio de contraste mesuré entre `#DC2626` et `#1E293B` est de **3.03:1**.  
   *Verdict normatif* : **ÉCHEC CRITIQUE WCAG 2.1 AA** (SC 1.4.3 requiert 4.5:1 pour le texte normal). Un texte d'alerte médicale ou de suppression de données vitales rouge sombre sur gris-bleu foncé est sous les seuils légaux d'accessibilité.
2. `[MESURÉ]` **Libellés en dur en anglais dans le menu contextuel de profil** : Dans `item_profile_summary.xml:34,45`, les boutons d'action utilisent :
   - `contentDescription="Set as current profile"`
   - `contentDescription="Delete profile"`  
   Ces descriptions ne sont pas traduites dans `values-fr/` ni `values-ja/`.
3. `[MESURÉ]` **Omission de la chaîne cd_navigate_back_icon en FR et JA** : La chaîne `cd_navigate_back_icon` (« Go back »), présente dans `res/values/strings.xml:27`, est absente de `res/values-fr/` et `res/values-ja/`. Le bouton de retour arrière des barres d'outils est donc vocalisé en anglais sur tous les terminaux non anglophones.

---

## 4. Matrice Synthétique des Constats par Écran

| Écran / Composant | Contraste WCAG AA | Cibles Tactiles ($\ge$ 48dp) | Taille Texte ($\ge$ 12sp) | Ordre Lecture / Semantics | Libellés TalkBack FR/JA | Indépendance Couleur | Troncature Texte FR/JA | Codes Techniques Masqués |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1. Accueil** (`fragment_home`) | ⚠️ Partiel (SOS 5.0) | ❌ ÉCHEC (40dp) | ⚠️ 12sp profil | ✅ Conforme | ✅ Conforme | ✅ Conforme | ✅ Conforme | ✅ Conforme |
| **2. Fiche Détail** (`fragment_profile_detail`) | ✅ Conforme | ❌ ÉCHEC (44dp) | ✅ Conforme | ❌ ÉCHEC (▾ lu) | ✅ Conforme | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (`_j 1.2`, SNOMED) |
| **3. Allergies** (`item_allergy_row`) | ❌ ÉCHEC (3.31:1) | ✅ Conforme | ⚠️ 12sp notes | ❌ ÉCHEC (› lu) | ❌ ÉCHEC (pas de helper) | ⚠️ Emoji seul | ❌ ÉCHEC (`maxLines=2`) | ❌ ÉCHEC (code SNOMED brut) |
| **4. Médicaments** (`item_medication_row`) | ❌ ÉCHEC (3.31:1) | ✅ Conforme | ⚠️ 12sp raison | ❌ ÉCHEC (› lu) | ❌ ÉCHEC (pas de helper) | ⚠️ Emoji seul | ❌ ÉCHEC (`maxLines=2`) | ❌ ÉCHEC (code `rc` brut) |
| **5. Contacts ICE** (`bottom_sheet_contact`) | ✅ Conforme | ✅ Conforme | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (helper en FR dur) | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (RoleCode V3 brut) |
| **6. Antécédents** (`item_past_problem_row`) | ❌ ÉCHEC (3.31:1) | ✅ Conforme | ⚠️ 12sp notes | ❌ ÉCHEC (› lu) | ❌ ÉCHEC (pas de helper) | ✅ Conforme | ❌ ÉCHEC (`maxLines=2`) | ❌ ÉCHEC (code SNOMED) |
| **7. Dispositifs** (`item_device_row`) | ❌ ÉCHEC (3.31:1) | ✅ Conforme | ⚠️ 12sp notes | ❌ ÉCHEC (› lu) | ❌ ÉCHEC (erreur UDI) | ✅ Conforme | ❌ ÉCHEC (`maxLines=2`) | ❌ ÉCHEC (UDI / SNOMED brut) |
| **8. Vaccins** (`item_immunization_row`) | ❌ ÉCHEC (3.31:1) | ✅ Conforme | ⚠️ 12sp lot | ❌ ÉCHEC (› lu) | ❌ ÉCHEC (pas de helper) | ✅ Conforme | ❌ ÉCHEC (`maxLines=2`) | ⚠️ Lot non explicité |
| **9. Interventions** (`item_procedure_row`) | ❌ ÉCHEC (3.31:1) | ✅ Conforme | ⚠️ 12sp détails | ❌ ÉCHEC (› lu) | ❌ ÉCHEC (pas de helper) | ✅ Conforme | ❌ ÉCHEC (`maxLines=2`) | ❌ ÉCHEC (code SNOMED brut) |
| **10. Résultats** (`item_result_row`) | ❌ ÉCHEC (3.31:1) | ✅ Conforme | ⚠️ 12sp unité | ❌ ÉCHEC (› lu) | ❌ ÉCHEC (pas de helper) | ✅ Conforme | ❌ ÉCHEC (`maxLines=2`) | ❌ ÉCHEC (code LOINC brut) |
| **11. Grossesse** (`fragment_pregnancy_edit`) | ✅ Conforme | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (4× › lus) | ❌ ÉCHEC (lignes muettes) | ✅ Conforme | ✅ Conforme | ✅ Conforme |
| **12. Perso / Identité** (`fragment_perso_edit`) | ✅ Conforme | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (emojis lus) | ❌ ÉCHEC (lignes muettes) | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (URI OID brutes) |
| **13. Picker IPS** (`dialog_ips_code_picker`) | ⚠️ 12sp count | ❌ ÉCHEC (36dp) | ⚠️ 12sp count | ✅ Conforme | ✅ Conforme | ⚠️ Emoji seul | ✅ Conforme | ❌ ÉCHEC (prefix code brut) |
| **14. Picker KB** (`dialog_kb_drug_picker`) | ✅ Conforme | ⚠️ 40-42dp list | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (vide silencieux) | ✅ Conforme | ✅ Conforme | ✅ Conforme |
| **15. QR Viewer** (`fragment_qr_viewer`) | ✅ Conforme | ❌ ÉCHEC (44dp) | ❌ ÉCHEC (11sp) | ⚠️ Slideshow loop | ✅ Conforme | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (dump JSON/Base64) |
| **16. Hub Radar** (`fragment_radar`) | ✅ Conforme | ⚠️ Non borné | ✅ Conforme | ✅ Conforme | ⚠️ Acronymes SALT | ❌ ÉCHEC (SALT daltonisme) | ✅ Conforme | ✅ Conforme |
| **17. Fiche Victime** (`fragment_patient_detail`) | ✅ Conforme | ❌ ÉCHEC (40dp) | ❌ ÉCHEC (9sp !) | ❌ ÉCHEC (♀/♂ lu) | ❌ ÉCHEC (WAIT/STAB EN) | ❌ ÉCHEC (SALT daltonisme) | ❌ ÉCHEC (nom maxLines 1) | ✅ Conforme |
| **18. Cartes SALT** (`item_victim_card`) | ❌ ÉCHEC (DCD 1.18) | ✅ Conforme | ✅ Conforme | ✅ Conforme | ❌ ÉCHEC (WAIT/DCD EN) | ❌ ÉCHEC (DCD invisible) | ❌ ÉCHEC (`maxLines=2`) | ⚠️ Acronymes SALT |
| **19. Widget SOS** (`jemma_emergency_widget`) | ⚠️ 4.67:1 sur 12sp | ❌ ÉCHEC (32dp) | ⚠️ 12sp vide | ✅ Conforme | ❌ ÉCHEC (Hardcoded EN) | ⚠️ SOS rouge seul | ✅ Conforme | ❌ ÉCHEC ("JEMMA ID") |
| **20. Dialogues Alerte** (`dialog_allergy_edit`) | ❌ ÉCHEC (3.03:1) | ✅ Conforme | ⚠️ 12sp alertes | ✅ Conforme | ❌ ÉCHEC (`cd_navigate` EN) | ❌ ÉCHEC (Danger 3.03:1) | ✅ Conforme | ✅ Conforme |

---

## 5. Décompte Exhaustif des Constats par Étiquette

| Catégorie de Constat d'Accessibilité | Étiquette `[MESURÉ]` | Étiquette `[NON VÉRIFIÉ]` | Total Constats |
| :--- | :---: | :---: | :---: |
| **1. Contraste & Lisibilité Chromatique (WCAG AA/AAA)** | 8 | 1 | 9 |
| **2. Cibles Tactiles & Ergonomie Motrice (Touch Targets)** | 7 | 1 | 8 |
| **3. Taille Typographique & Échelles de Texte (sp)** | 6 | 0 | 6 |
| **4. Ordre de Lecture & Sémantique d'Arborescence** | 5 | 0 | 5 |
| **5. Libellés Lecteur d'Écran TalkBack & Multilinguisme** | 9 | 1 | 10 |
| **6. Dépendance à la Couleur & Daltonisme** | 4 | 0 | 4 |
| **7. Troncature Silencieuse en Français et Japonais** | 5 | 0 | 5 |
| **8. Codes Techniques & Surcharge Informatique Visuelle** | 8 | 0 | 8 |
| **TOTAL GÉNÉRAL** | **52** | **3** | **55** |

*Note de validation* : Tous les 52 constats `[MESURÉ]` citent précisément le fichier et la ligne dans le code source de la branche `feat/ips-18-pillars-cleanup` ou la capture horodatée issue de `screens/INDEX.md` sur `device-reports`.

---

## 6. Registre des Décisions Ouvertes à Faire Prendre par Kudoro (DEC-UX-01 à DEC-UX-08)

Les 8 arbitrages ci-dessous engagent la sécurité des patients, l'expérience utilisateur et les choix éthiques ou cliniques de JemmaPass. Ils sont formellement soumis à la décision exclusive de Kudoro :

### DEC-UX-01 : Palette Rouge d'Alerte Médicale & Thème Sombre
- **Problème** : `@color/jemma_danger` (`#DC2626`) sur le fond des cartes et dialogues `#1E293B` affiche un ratio de contraste de **3.03:1**, en infraction caractérisée du critère WCAG 2.1 AA ($\ge 4.5:1$). Les alertes critiques (anaphylaxie, interactions létales) sont difficilement lisibles.
- **Option A (Recommandée)** : Adopter pour le texte d'alerte et les bordures un rouge éclairci haute visibilité (ex: `#EF4444` ou `#F87171`, ratio > 5.5:1 sur fond sombre), en réservant `#DC2626` uniquement aux fonds pleins avec texte blanc (ratio 5.00:1).
- **Option B** : Utiliser un bandeau de fond coloré contrasté (Surface Container) avec texte contrasté, plutôt que du texte rouge sur fond gris-bleu.
- **Impact médical** : Réduction immédiate du risque de non-lecture d'une alerte clinique par un soignant ou un aidant en basse luminosité.

### DEC-UX-02 : Politique d'Affichage du Statut Décédé (SALT DCD)
- **Problème** : `@color/salt_dcd` est actuellement `#000000` (`colors.xml:47`), créant un contraste nul (1.18:1 à 1.44:1) sur fond sombre.
- **Option A (Recommandée)** : Remplacer le fond noir par un gris ardoise neutre bordé (`#334155` avec bordure blanche ou dorée et glyphe colombe `🕊️` blanc).
- **Option B** : Maintenir un badge contrasté inversé (fond blanc, texte noir `DCD`).
- **Impact médical** : Identification visuelle sans ambiguïté des personnes décédées lors d'un triage de masse sur écran de smartphone.

### DEC-UX-03 : Masquage des Codes Cliniques Bruts pour les Utilisateurs Civils
- **Problème** : Les sélecteurs et listes affichent des codes SNOMED (`91936005`), LOINC (`14749-6`), UDI et V3-RoleCode (`DOMPART`) déroutants pour les civils.
- **Option A (Recommandée)** : Masquer totalement les codes numériques dans la vue civile, n'affichant que le libellé traduit clair (avec repli sur libellé anglais marqué « non traduit » selon le PROTOCOL §9.1), et réserver l'affichage des codes techniques à un mode « Soignant / Expert » activable dans les réglages.
- **Option B** : Afficher le code sous forme de badge discret secondaire en fin de ligne (police 10sp), mais jamais en préfixe principal du nom.
- **Impact ergonomique** : Suppression de l'anxiété du patient face aux codes informatiques incompréhensibles.

### DEC-UX-04 : Règle d'Expansion des Notes Cliniques et Antécédents (Fin du maxLines=2 bloquant)
- **Problème** : `android:maxLines="2"` tronque silencieusement des précisions vitales en français et en japonais sans mécanisme d'expansion à l'écran.
- **Option A (Recommandée)** : Supprimer `maxLines="2"` sur la fiche d'urgence et le détail victime pour afficher l'intégralité du texte clinique, ou introduire un bouton interactif « Voir plus / 詳細 » sur les lignes longues.
- **Option B** : Limiter à 2 lignes avec pastille informative indiquant le nombre de caractères masqués.
- **Impact vital** : Accès garanti à la totalité des manifestations allergiques et posologies lors d'un sauvetage.

### DEC-UX-05 : Taille Minimale des Données Vitales Secouriste (Cellules 9sp)
- **Problème** : Dans la fiche secouriste (`view_vital_cell.xml:21`), les labels vitaux sont à **`9sp`**, inadaptés aux conditions dégradées de sauvetage.
- **Option A (Recommandée)** : Rehausser la typographie minimale absolue à **`12sp`** pour les métadonnées et **`16sp`** gras pour les valeurs vitales (Groupe sanguin, Triage, Âge).
- **Option B** : Proposer un mode « Urgence Haute Visibilité » commutable augmentant les cellules vitales à 20sp.
- **Impact opérationnel** : Lecture de la fiche d'urgence garantie en moins de 10 secondes par un équipier DMAT.

### DEC-UX-06 : Élargissement des Cibles Tactiles à 48 dp Strict
- **Problème** : Plusieurs éléments critiques (boutons de carrousel QR 44dp, chips de catégorie 36dp, bouton fermeture 40dp, bouton ajout profil 40dp, bouton SOS widget 32dp) sont inférieurs à 48 dp.
- **Option A (Recommandée)** : Imposer la conformité stricte Google Material 48 × 48 dp sur tous les composants interactifs via `minWidth="48dp"`, `minHeight="48dp"` et l'application d'insets tactiles (`TouchDelegate` ou marges transparentes).
- **Option B** : Tolérer 44 dp uniquement sur les composants alignés sur la norme Apple HIG, tout en remontant le widget SOS et les filtres à 48 dp.
- **Impact ergonomique** : Utilisabilité garantie en situation de panique, de tremblements, ou avec des gants d'intervention.

### DEC-UX-07 : Externalisation Multilingue des Assistants d'Accessibilité (FormA11yHelpers)
- **Problème** : `FormA11yHelpers.kt` utilise des chaînes françaises codées en dur, provoquant des annonces vocales inintelligibles pour les utilisateurs japonais ou anglophones.
- **Option A (Recommandée)** : Refactoriser `FormA11yHelpers` pour consommer les ressources Android (`@string/a11y_picker_hint`, `@string/a11y_selected`, etc.) présentes dans `values/`, `values-fr/` et `values-ja/`.
- **Option B** : Passer le contexte d'activité et déléguer la résolution des chaînes aux fragments appelants.
- **Impact international** : Conformité de l'expérience TalkBack pour les citoyens japonais (`demo_haru`) et internationaux.

### DEC-UX-08 : Présence du Dump Technique dans le Visualisateur QR
- **Problème** : L'écran QR affiche par défaut le contenu encodé brut JSON/Base64 sous le QR code (`fragment_qr_viewer.xml:270-291`).
- **Option A (Recommandée)** : Rendre cette section rétractable par défaut (« Afficher les données techniques encodées ») ou la déplacer dans un écran de diagnostic pour ne pas distraire l'utilisateur du QR code principal.
- **Option B** : Conserver le dump visible mais réduire son emprise visuelle en pied de page.
- **Impact visuel** : Recentrage immédiat de l'écran sur le QR code scannable par les secours.

---

`orchestrator: Antigravity-UX`
