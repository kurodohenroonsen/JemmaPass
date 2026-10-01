# QA sur appareil — protocole pour l'agent d'exécution (Antigravity)

_Branche `feat/ips-18-pillars-cleanup` · Pixel 9 branché en USB au Mac de Kudoro_

Tu es l'agent **exécutant** : tu construis, installes, testes sur le téléphone et
**publies un rapport**. Tu ne modifies pas la branche de feature. Si un test
échoue, tu documentes (preuves : captures, logcat, fichiers) — tu ne corriges pas.
L'agent **architecte** (Claude, via GitHub) lit le rapport et livre les correctifs.

## 0. Règles

1. **Aucune donnée personnelle** ne sort du téléphone : le dépôt est public.
   Seuls les profils de démo (`demo_kurodo`, `demo_haru`, `demo_kamekichi`)
   apparaissent dans le rapport, les captures et les fichiers publiés. Si un profil
   réel est visible à l'écran, ne publie pas la capture.
2. Ne jamais `pm clear` / désinstaller : les 6 Go (Gemma 4 + KB) resteraient à retélécharger.
   Le script ne touche qu'aux fichiers `demo_*` du dossier `profiles/`
   (sauvegarde complète dans `out/…/backup/`, non publiée).
3. Le rapport est publié sur la branche **`device-reports`** (orpheline), jamais
   sur `feat/ips-18-pillars-cleanup`. Format : §5.
4. Un bloqueur (build cassé, appareil absent) = rapport partiel publié + arrêt.
5. Les logs publiés sont des sorties brutes de `adb logcat`, jamais éditées à la main :
   le seul traitement autorisé est `qa/device/scrub_logcat.py` (suppression des lignes
   mentionnant un profil non-démo, avec compteur en fin de fichier).
6. **Intégrité des preuves** : une capture prouve l'étape pendant laquelle elle a été
   prise. Ne copie/renomme jamais une capture pour illustrer un autre cas ; si une
   capture manque, rejoue l'étape. Une preuve obtenue « par accident » (par ex. une
   saisie qui a échoué) se documente comme telle dans le rapport.
7. **Aucun identifiant matériel** dans ce qui est publié : le script masque déjà le
   numéro de série dans `adb-devices.txt` ; ne le recopie pas dans `report.md` ni
   `env.txt` (modèle + version Android suffisent).
8. **Sorties citées = sorties brutes** : un texte décodé (QR), une sortie de validateur,
   un résultat SQL se recopient par copier-coller depuis la sortie de la commande, jamais
   reformulés ni « résumés » ligne par ligne. Les fichiers correspondants sont publiés dans
   le dossier du run (`qr/qr-<persona>-<lang>.txt`, `validator/<persona>.txt`) ; un tableau
   qui cite une preuve absente du dossier publié est invalide.

## 1. Préparation

```
git fetch origin
git checkout feat/ips-18-pillars-cleanup
git pull --ff-only
adb devices -l
chmod +x qa/device/run_device_qa.sh
```

Prérequis sur le Mac : JDK/Android Studio (le projet compile déjà chez Kudoro), `adb`,
`python3` (stdlib uniquement). Si plusieurs appareils : `export ADB_SERIAL=<serial>`.

## 2. Partie automatisée

```
qa/device/run_device_qa.sh
```

Le script écrit tout dans `qa/device/out/<sha7>-<horodatage>/` :

| Étape | Ce qu'elle fait | Preuve |
|---|---|---|
| 1 | `:app:testDebugUnitTest` — attendu **50 tests, 0 échec** | `logs/unit-tests.log` |
| 2 | `:app:assembleDebug` | `logs/assemble.log` |
| 3 | `adb install -r -g` (permissions runtime accordées, données conservées) | `logs/install.log` |
| 4 | sauvegarde de `profiles/`, suppression des seuls fichiers `demo_*` → re-seed | `backup/` (non publié) |
| 5 | lancement, 15 s, pull de `profiles/`, logcat filtré puis scrubbé (pas de capture : la liste des profils peut montrer un profil réel) | `files/demo_*.json`, `logs/logcat-seed.txt` |
| 6 | `verify_profiles.py` — invariants P1…P8 sur les 3 personas et les 3 piliers natifs, attendu 💉 Kurodo=4 · Haru=3 · Kamekichi=0, 🏥 Kurodo=2 · Haru=2, 📟 Haru=2 | `verify-seed.md`, `steps.md` |

`verify_profiles.py` contrôle : Bundle `document` + Composition en tête, URNs
`urn:uuid:` déterministes (UUID v3), et pour chaque pilier natif (💉 `im`/`Immunization`,
🏥 `pr`/`Procedure`, 📟 `dv`/`DeviceUseStatement` + `Device`) : projection `_j.<clé>` ⇄
ressources (codes + dates), section LOINC (`11369-6` / `47519-4` / `46264-8`) pointant
exactement sur les fullUrl des ressources, profil `*-uv-ips` + référence patient + statut,
`DeviceUseStatement.device` résolu vers un `Device` (profil `Device-uv-ips`), sections
legacy (allergies/médicaments/problèmes) toujours présentes. Options : `--expect`
(vaccins), `--expect-pr`, `--expect-dv`.

## 3. Protocole UI (à dérouler avec `ui.py`, une capture par point de contrôle)

`ui.py` pilote l'écran via `uiautomator dump` + `input tap` (tous les contrôles ont
un `resource-id`). Adapte si l'écran diffère, et **note toute déviation** dans le rapport.
Langue attendue de l'appareil : FR ou EN (les libellés ci-dessous sont donnés en EN / FR).
Limite connue : `ui.py type` passe par `adb shell input text`, qui ne sait pas saisir
les caractères non-ASCII (accents, kana) — utilise des chaînes de test ASCII
(`Operation du genou 1998`) et signale-le si un libellé attendu contient un accent.

Notation : `UI = python3 qa/device/ui.py`, `OUT = dossier de sortie du script`.

### T1 — Fiche profil Kurodo : tuile + section vaccins

```
$UI stop && $UI launch && $UI wait 4
$UI tap --text "Kurodo"
$UI wait 3
$UI screenshot $OUT/screenshots/10-detail-kurodo.png
$UI exists --text "IMMUNIZATIONS (4)"
```
FR : `VACCINATIONS (4)`. Attendu : section « 💉 IMMUNIZATIONS (4) » avec 4 lignes
(encéphalite japonaise 2023, Tdap 2022, COVID 2021, hépatite A+B 2016), et dans la
grille des piliers (déplier `profile_detail_pillars_header` si besoin) la tuile 💉
**active** avec le badge **4** (plus de cadenas).

```
$UI tap --id profile_detail_pillars_header
$UI wait 1
$UI screenshot $OUT/screenshots/11-detail-pillars.png
$UI tap --id profile_detail_tile_immunizations
$UI wait 2
$UI screenshot $OUT/screenshots/12-immunizations-list.png
$UI exists --text "4 immunizations"
```
FR : `4 vaccinations`. Attendu : 4 cartes triées par date décroissante, sous-titres
`2023-01-20 · dose 2/2`, `2022-05-17 · lot AC52B213BC`, `2021-06-11 · dose 2/2 · lot FD0168`,
`2016-03-02 · dose 3/3` ; ligne muette fabricant/praticien/note.

### T2 — Création (picker catalogue)

```
$UI tap --id immunizations_fab_add
$UI wait 2
$UI screenshot $OUT/screenshots/20-form-empty.png
$UI tap --id immunization_form_vaccine_card
$UI wait 2
$UI screenshot $OUT/screenshots/21-picker.png
$UI tap --id picker_search
$UI type "influenza"
$UI wait 1
$UI tap --text "Seasonal influenza"
$UI wait 1
$UI screenshot $OUT/screenshots/22-form-vaccine-picked.png
```
FR : `--text "grippe saisonnière"`. (Le champ de recherche contient aussi « influenza » :
cible bien le libellé de l'item, pas le champ.) Attendu : le picker liste d'abord les
vaccins courants (emoji), puis les produits SNOMED ; le champ texte libre disparaît une
fois un vaccin choisi ; la croix ✕ apparaît.

```
$UI tap --id immunization_form_date_row
$UI wait 2
$UI screenshot $OUT/screenshots/23-date-picker.png
$UI tap --text "OK"
$UI tap --id immunization_form_dose_number
$UI type "1"
$UI tap --id immunization_form_lot
$UI type "QA-LOT-001"
$UI tap --id immunization_form_save_btn
$UI wait 2
$UI screenshot $OUT/screenshots/24-list-after-create.png
$UI exists --text "5 immunizations"
```
Attendu : toast « Immunizations saved » (FR « Vaccinations enregistrées »), 5 cartes,
la nouvelle en tête (date du jour) avec `dose 1 · lot QA-LOT-001`.

Vérification disque après création :
```
adb pull /sdcard/Android/data/be.heyman.android.jemmapassdemo/files/profiles $OUT/pull-t2/
python3 qa/device/verify_profiles.py $OUT/pull-t2/profiles --only demo_kurodo --expect demo_kurodo=5 --title "T2 after create" --markdown $OUT/verify-t2.md
```
Attendu : PASS, et dans `demo_kurodo.fhir.json` une 5e ressource `Immunization`
avec `lotNumber: "QA-LOT-001"`, `protocolApplied[0].doseNumberPositiveInt: 1`.

### T3 — Édition puis suppression

```
$UI tap --text "QA-LOT-001"
$UI wait 2
$UI exists --text "Edit immunization"
$UI tap --id immunization_form_manufacturer
$UI type "QA%sLab"
$UI tap --id immunization_form_save_btn
$UI wait 2
$UI exists --text "QA Lab"
$UI screenshot $OUT/screenshots/30-after-edit.png
```
FR : `Modifier la vaccination`. Attendu : toujours 5 cartes (édition = même id, pas de doublon).

Suppression, **deux chemins** à tester :

a) bouton **Delete** / **Supprimer** du formulaire (nouveau, mode édition) :
```
$UI tap --text "QA Lab"
$UI wait 2
$UI screenshot $OUT/screenshots/31-form-delete-btn.png
$UI tap --id immunization_form_delete_btn
$UI wait 1
$UI screenshot $OUT/screenshots/32-delete-dialog.png
$UI tap --text "Delete"
$UI wait 2
$UI exists --text "4 immunizations"
```
(`tap --text` privilégie désormais le nœud cliquable : le bouton, pas le titre du dialogue.
FR : `--text "Supprimer"`.)

b) appui long sur une carte, en recréant d'abord une entrée jetable (T2 en 30 s), puis :
```
$UI longpress --text "QA-LOT-001"
$UI wait 1
$UI screenshot $OUT/screenshots/33-longpress-dialog.png
$UI tap --text "Delete"
$UI wait 2
$UI exists --text "4 immunizations"
adb pull /sdcard/Android/data/be.heyman.android.jemmapassdemo/files/profiles $OUT/pull-t3/
python3 qa/device/verify_profiles.py $OUT/pull-t3/profiles --only demo_kurodo --expect demo_kurodo=4 --title "T3 after delete" --markdown $OUT/verify-t3.md
```

### T4 — Non-régression : une édition d'allergie ne perd pas les vaccins

Depuis la fiche Kurodo : tuile **Allergies** → ouvrir une allergie → Save sans rien
changer (ou modifier la note) → retour.
```
adb pull /sdcard/Android/data/be.heyman.android.jemmapassdemo/files/profiles $OUT/pull-t4/
python3 qa/device/verify_profiles.py $OUT/pull-t4/profiles --only demo_kurodo --expect demo_kurodo=4 --title "T4 allergy edit keeps immunizations" --markdown $OUT/verify-t4.md
```
Attendu : PASS (règle « édition locale → le Bundle reste autoritaire »).

### T5 — Canal QR texte 25 langues

Fiche Kurodo → bouton **Export** (toolbar, `id` = `menu_profile_detail_export`) → option
« 📱 QR Codes… » → onglet `qr_tab_text` → chips de langue (créés dynamiquement : cible
leur `content-description` `qr_lang_<iso>`).
```
$UI tap --id menu_profile_detail_export
$UI tap --text "QR Codes"
$UI wait 3
$UI tap --id qr_tab_text
$UI tap --desc qr_lang_en
$UI wait 2
$UI screenshot $OUT/screenshots/50-qr-text-en.png
$UI tap --desc qr_lang_fr
$UI wait 2
$UI screenshot $OUT/screenshots/51-qr-text-fr.png
$UI tap --desc qr_lang_ja
$UI wait 2
$UI screenshot $OUT/screenshots/52-qr-text-ja.png
```
Preuve : décoder le QR de chaque capture (`python3 -c "import cv2;print(cv2.QRCodeDetector().detectAndDecode(cv2.imread('$OUT/screenshots/50-qr-text-en.png'))[0])"`,
`pip install opencv-python-headless` si besoin) et coller le texte décodé dans le rapport.
Attendu : une section `💉 [ IMMUNIZATIONS ]` (EN) / `💉 [ VACCINATIONS ]` (FR) /
`💉 [ 予防接種 ]` (JA), 4 lignes **triées par date décroissante**
(`… — 2023-01-20 · #2` en premier), libellés localisés. Note si la section est tronquée
par le plafond 2200 octets.

### T6 — Onglet FHIR + validateur (bonus, si réseau)

Onglet `qr_tab_fhir` → capture. Puis, hors appareil, valider `files/demo_kurodo.fhir.json`
sur https://www.ipsviewer.com/classic (rendu de la section Immunizations) et, si le
validateur HL7 est disponible localement, `java -jar validator_cli.jar demo_kurodo.fhir.json -version 4.0.1 -ig hl7.fhir.uv.ips`.
Colle le résumé (erreurs/avertissements) dans le rapport.

### T7 — Haru + Kamekichi (lecture seule)

Fiche Haru : section « 💉 … (3) » (grippe 2025, COVID 2024, PCV 2021), libellés en
japonais si l'appareil est en JA. Fiche Kamekichi : pas de section vaccins, tuile
💉 active avec badge absent (0). Captures `70-haru.png`, `71-kamekichi.png`.

### T8 — Chemins alternatifs du formulaire

1. **Vaccin en texte libre** (sans code) : FAB → ne rien choisir dans le picker,
   saisir `Vaccin du village 1985` dans `immunization_form_text`, laisser la date
   inconnue, Save. Attendu : carte avec le texte libre et « Date unknown » /
   « Date inconnue » ; dans `demo_kurodo.fhir.json` la ressource a
   `vaccineCode.text` sans `coding` et `occurrenceString: "unknown"` ;
   `verify_profiles.py … --expect demo_kurodo=5` PASS.
2. **Statut « Not done »** : éditer cette carte → `immunization_form_status_row` →
   « Not done » / « Non administré » → Save. Attendu : icône 🚫 et statut dans le
   sous-titre ; `status: "not-done"` dans le Bundle ; `"st":"not-done"` dans `_j.im`.
3. **Désélection du vaccin** : FAB → choisir un vaccin → ✕ (`immunization_form_vaccine_clear`).
   Attendu : le champ texte libre réapparaît, le vaccin est vide.
4. **Annuler** : remplir puis `immunization_form_cancel_btn`. Attendu : aucune carte
   ajoutée, aucun fichier modifié (`verify` inchangé).
5. Nettoyage : supprimer la carte « Vaccin du village 1985 » → retour à 4.

### T9 — Chemins d'erreur du formulaire

| Cas | Action | Attendu |
|---|---|---|
| Aucun vaccin | FAB → Save direct | toast « Pick a vaccine or type its name » / « Choisis un vaccin ou saisis son nom », formulaire toujours ouvert |
| Dose 0 | vaccin choisi, `immunization_form_dose_number` = `0`, Save | toast « Dose numbers must be positive whole numbers », focus sur le champ |
| Dose > série | dose `3`, série `2`, Save | toast « The dose number cannot exceed… », focus sur la série |
| Date future | ouvrir le sélecteur de date | les jours après aujourd'hui sont désactivés |
| Rotation / retour | formulaire rempli, rotation de l'écran (ou `adb shell settings put system user_rotation 1` puis `0`) | pas de crash (`AndroidRuntime:E` vide) ; noter si les saisies sont perdues |
| Double-tap Save | deux taps rapides sur Save | une seule carte créée |

Chaque cas : capture + ligne dans le rapport. Puis `verify_profiles.py … --expect demo_kurodo=4` doit rester PASS.

### T10 — Procédures (🏥 pilier natif, sprint 2)

Personas : Kurodo = appendicectomie 1995-07-12 + coloscopie 2024-02-19 ; Haru = pontage
coronarien 2015-09-02 + césarienne « 1975 » (année seule) ; Kamekichi = aucune.

```
$UI stop && $UI launch && $UI wait 4
$UI tap --text "Kurodo"
$UI wait 3
$UI screenshot $OUT/screenshots/100-detail-kurodo-procedures.png
$UI exists --text "PROCEDURES (2)"
$UI tap --id profile_detail_pillars_header
$UI wait 1
$UI tap --id profile_detail_tile_procedures
$UI wait 2
$UI screenshot $OUT/screenshots/101-procedures-list.png
$UI exists --text "2 procedures"
```
FR : `INTERVENTIONS (2)`, `2 interventions`. Attendu : section « 🏥 » sous les vaccins
(2 lignes, coloscopie d'abord), tuile 🏥 **active** avec badge **2**, liste de 2 cartes
triées par date décroissante (`2024-02-19 · ✅ Completed / Réalisée`, `1995-07-12 · …`),
ligne muette « CHU … · Dr … » si renseignée.

**Création (picker catalogue)** :
```
$UI tap --id procedures_fab_add
$UI wait 2
$UI screenshot $OUT/screenshots/102-procedure-form-empty.png
$UI tap --id procedure_form_code_card
$UI wait 2
$UI screenshot $OUT/screenshots/103-procedure-picker.png
$UI tap --text "Cholecystectomy"
$UI wait 1
$UI tap --id procedure_form_date_row
$UI wait 2
```
FR : `--text "Cholécystectomie"`. Choisir une date passée dans le calendrier (OK), puis
`procedure_form_body_site` = `Abdomen`, `procedure_form_location` = `CHU Demo`,
`procedure_form_note` = `Test QA`, `procedure_form_save_btn`. Attendu : toast
« Procedures saved » / « Interventions enregistrées », 3 cartes, la nouvelle en tête si sa
date est la plus récente. Capture `104-procedure-created.png`, puis :
```
adb pull /sdcard/Android/data/be.heyman.android.jemmapassdemo/files/profiles $OUT/pull-t10/
python3 qa/device/verify_profiles.py $OUT/pull-t10/profiles --only demo_kurodo --expect demo_kurodo=4 --expect-pr demo_kurodo=3 --title "T10 procedure created" --markdown $OUT/verify-t10.md
```
Vérifie dans `demo_kurodo.fhir.json` : ressource `Procedure` avec
`code.coding[0].code = 38102005`, `performedDateTime`, `bodySite.text`, `location.display`,
`note[0].text`, profil `Procedure-uv-ips` ; section LOINC `47519-4` à 3 références ; dans
`demo_kurodo.json` `pr[]` a 3 entrées (`c`, `dt`, `d_display`, `d` = note).

**Recherche KB** (picker) : `$UI tap --id drug_picker_search`, taper `appendic` → attendu :
l'entrée catalogue « 🔪  Appendectomy » / « 🔪  Appendicectomie » en tête (les alias EN/FR/JA
et le code sont cherchés), puis des résultats KB (`terminology_codes.category = 'Procedure'`,
FTS5) **sans doublon** de libellé ni de code, libellés FR quand `ips_valuesets_translations`
en a un pour le code SNOMED, et le hint du champ = « Cherche une intervention (FR, EN,
SNOMED…) ». Capture `105-procedure-kb-search.png`.
Choisir un résultat **KB** (hors catalogue), Save, et noter dans le rapport le `code` et le
`system` écrits dans le Bundle (attendu : SNOMED `http://snomed.info/sct` quand la KB
connaît le mapping, sinon `urn:umls` + CUI — les deux sont acceptés, note lequel).

**Édition + suppression** : ouvrir la carte « Cholecystectomy » → changer le statut
(`procedure_form_status_row` → « In progress » / « En cours ») → Save → carte avec ⏳ ;
rouvrir → `procedure_form_delete_btn` → confirmer « Delete » / « Supprimer ». Supprimer
aussi l'entrée KB par **appui long** sur la carte. Captures `106-procedure-edited.png`,
`107-procedure-delete-dialog.png`, `108-procedures-back-to-2.png`. Puis
`verify_profiles.py … --expect-pr demo_kurodo=2` PASS.

### T11 — Dispositifs médicaux (📟 pilier natif, sprint 2)

Personas : Haru = pacemaker (UDI `(01)00643169007222(21)PJN1234567`, Medtronic, 2021-03-15)
+ appareil auditif (Phonak, « 2019-06 », mois seul) ; Kurodo / Kamekichi = aucun.

```
$UI stop && $UI launch && $UI wait 4
$UI tap --text "Haru"
$UI wait 3
$UI screenshot $OUT/screenshots/110-detail-haru-devices.png
$UI exists --text "MEDICAL DEVICES (2)"
$UI tap --id profile_detail_pillars_header
$UI wait 1
$UI tap --id profile_detail_tile_devices
$UI wait 2
$UI screenshot $OUT/screenshots/111-devices-list.png
$UI exists --text "2 devices"
```
FR : `DISPOSITIFS MÉDICAUX (2)`, `2 dispositifs`. Attendu : cartes ❤️ pacemaker
(`2021-03-15 · ✅ In use / En place`, ligne « Medtronic … · UDI (01)… ») et 👂 appareil
auditif (`2019-06 · …`), tuile 📟 active avec badge 2. Sur la fiche Kurodo : pas de
section 📟 et tuile sans badge.

**Création avec UDI** (sur Kurodo, pour laisser Haru intact) : fiche Kurodo → tuile 📟 →
`devices_fab_add` → `device_form_code_card` → « Insulin pump » / « Pompe à insuline » →
`device_form_udi` = `(01)00643169007222(21)QA0001` → `device_form_manufacturer` = `Demo
Med` → `device_form_model` = `QA-1` → `device_form_serial` = `SN-QA-1` → date passée →
`device_form_body_site` = `Abdomen` → Save. Captures `112-device-form.png`,
`113-device-created.png`.
```
adb pull /sdcard/Android/data/be.heyman.android.jemmapassdemo/files/profiles $OUT/pull-t11/
python3 qa/device/verify_profiles.py $OUT/pull-t11/profiles --only demo_kurodo --only demo_haru --expect demo_kurodo=4 --expect-pr demo_kurodo=2 --expect-dv demo_kurodo=1 --expect-dv demo_haru=2 --title "T11 device created" --markdown $OUT/verify-t11.md
```
Vérifie dans `demo_kurodo.fhir.json` : **deux** ressources par dispositif — `Device`
(`type.coding[0].code = 69805005`, `udiCarrier[0].deviceIdentifier` + `carrierHRF`,
`manufacturer`, `modelNumber`, `serialNumber`, `patient`, profil `Device-uv-ips`) et
`DeviceUseStatement` (`device.reference` = fullUrl du Device, `timingDateTime`,
`bodySite.text`, profil `DeviceUseStatement-uv-ips`) ; section LOINC `46264-8` pointant
sur le `DeviceUseStatement` ; `_j.dv[0]` avec `c`, `dt`, `d_display`.

**Édition + suppression** : ouvrir la carte → statut « Removed / no longer used » /
« Retiré » → Save → icône ⏹, `status: inactive` sur le `Device` et `completed` sur le
`DeviceUseStatement`, `"st":"inactive"` dans `_j.dv` ; puis `device_form_delete_btn` →
confirmer → retour à 0 dispositif sur Kurodo. Captures `114-device-edited.png`,
`115-devices-back-to-0.png`. Vérifier que Haru a toujours ses 2 dispositifs
(`--expect-dv demo_haru=2`).

### T12 — Chemins alternatifs et d'erreur (🏥 + 📟)

| Cas | Action | Attendu |
|---|---|---|
| Procédure en texte libre | FAB 🏥 → rien dans le picker, `procedure_form_text` = `Opération du genou 1998`, date inconnue, Save | carte avec le texte, « Date unknown » ; Bundle : `code.text` sans `coding`, `performedString: "unknown"` ; `verify … --expect-pr demo_kurodo=3` PASS |
| Aucune procédure | FAB 🏥 → Save direct | erreur inline rouge sous le champ texte « Pick a procedure or type its name » / « Choisis une intervention ou saisis son nom », formulaire ouvert, `Log.w JEMMA-PROCEDURES-FORM` |
| ✕ procédure | choisir une procédure → `procedure_form_code_clear` | le champ texte libre réapparaît |
| Annuler | remplir → `procedure_form_cancel_btn` | aucune carte, fichiers inchangés |
| Dispositif en texte libre | FAB 📟 → `device_form_text` = `Plaque tibia gauche`, Save | Bundle : `Device.type.text` + `deviceName[0]` (`patient-reported-name`), pas de `coding` |
| Aucun dispositif | FAB 📟 → Save direct | erreur inline « Pick a device or type its name » / « Choisis un dispositif … » |
| UDI invalide | dispositif choisi, `device_form_udi` = `ABC`, Save | erreur inline « This does not look like a UDI… » / « Ça ne ressemble pas à un UDI… », focus sur le champ, s'efface dès la saisie |
| UDI GTIN nu | `device_form_udi` = `00643169007222`, Save | accepté (14 chiffres) |
| Date future | ouvrir un sélecteur de date (🏥 et 📟) | jours après aujourd'hui désactivés |
| Rotation | formulaire 🏥 rempli, rotation | pas de crash (`AndroidRuntime:E` vide) |
| Double-tap Save | 🏥 puis 📟, deux taps rapides | une seule carte à chaque fois |
| Nettoyage | supprimer les cartes de test | Kurodo : 🏥 2 · 📟 0 ; `verify` PASS |

Captures `120-…` (une par cas) + ligne dans le rapport.

### T13 — Canaux : QR texte 🏥/📟 + non-régression

Fiche **Haru** → Export → « QR Codes » → `qr_tab_text` → `qr_lang_en`, `qr_lang_fr`,
`qr_lang_ja` (captures `130-qr-text-en.png`, `131-…-fr.png`, `132-…-ja.png`), décodage
OpenCV comme en T5. Attendu : après `💉 [ IMMUNIZATIONS ]`, une section
`🏥 [ PROCEDURES ]` / `🏥 [ INTERVENTIONS ]` / `🏥 [ 処置・手術歴 ]` (2 lignes, 2015 avant
1975) puis `📟 [ MEDICAL DEVICES ]` / `📟 [ DISPOSITIFS MÉDICAUX ]` / `📟 [ 医療機器 ]`
(2 lignes, libellés du catalogue localisés : « Stimulateur cardiaque (pacemaker) », « 心臓ペースメーカー »).
Note si la section est tronquée par le plafond 2200 octets (Haru a maintenant 3 + 2 + 2 entrées natives).

Non-régression courte : T1 (section 💉 Kurodo à 4), T4 (édition allergie → `verify` complet
PASS avec `--expect demo_kurodo=4 --expect-pr demo_kurodo=2`), T7 (Kamekichi : ni 💉 ni 🏥
ni 📟, tuiles actives sans badge). Résultat dans `verify-t13.md`.

### T14 — Résultats (🧪 pilier natif, sprint 3)

Personas : Kurodo = groupe sanguin A+ (dérivé de `p.bt`) + HbA1c 5.6 % (N) + LDL 131 mg/dL (H) +
créatinine 0.9 mg/dL ; Haru = O+ (dérivé) + potassium 4.1 + hémoglobine 11.8 g/dL (L) + DFG 48 (L)
+ radio thoracique (imagerie, texte) ; Kamekichi = B+ (dérivé) seul.

1. **Fiche + liste** : fiche Haru → section « 🧪 RÉSULTATS (5) » (valeurs + 🔺/🔻 pour H/L) ;
   tuile 🧪 active avec badge 5 → liste `results_recycler` (valeur à droite, `5 résultats`).
   Captures `140-detail-haru-results.png`, `141-results-list.png`.
2. **Groupe sanguin dérivé** : tap ou appui long sur « Groupe sanguin ABO / Rhésus · O+ » →
   toast « Le groupe sanguin vient du pilier Patient — modifie-le là », aucun formulaire.
3. **Création numérique** (Kurodo) : FAB → `result_form_code_card` → taper `kaliemie` → « Potassium
   (kaliémie) » ; l'unité passe à `mmol/L` ; `result_form_value` = `5,9` (virgule) ; interprétation
   « Élevé » ; réf. basse `3,5`, haute `5,1` ; date passée ; Save. Attendu : carte « 5.9 mmol/L »,
   🔺, « réf. 3.5-5.1 ». `verify_profiles.py … --expect-rs demo_kurodo=5` PASS ; dans le Bundle
   `valueQuantity.value` = 5.9, `code` = `mmol/L`, `system` = UCUM, `referenceRange[0]`.
4. **Texte libre + imagerie** : FAB → `result_form_text` = `Echographie abdominale`, catégorie
   « Imagerie », valeur `Normale`, Save → Bundle : `code.text` sans coding, `valueString`,
   catégorie `imaging`, profil `Observation-results-radiology-uv-ips` si la date est au jour près,
   sinon `Observation-results-uv-ips` (contrainte ips-1, cycle 15).
5. **Erreurs** : Save vide → erreur inline sur le nom ; test choisi mais valeur vide → erreur
   inline « Saisis la valeur du résultat » ; réf. basse `9` / haute `3` → « La borne basse ne peut
   pas dépasser la borne haute » ; réf. `abc` → « Les bornes de référence doivent être des nombres ».
6. **Test codé** : FAB → « Groupe sanguin ABO / Rhésus » → le champ valeur/unité disparaît,
   remplacé par le sélecteur 🩸 ; Save sans choix → toast « Choisis le groupe sanguin ».
   (Un 882-1 saisi à la main remplace le dérivé : vérifie qu'il n'y a qu'un seul 882-1 dans le Bundle.)
7. **Édition / suppression** des entrées de test (bouton et appui long) → Kurodo revient à 🧪 4 ;
   `verify … --expect-rs demo_kurodo=4` PASS.
8. **QR texte** : Haru EN/FR/JA → section `🧪 [ RESULTS ]` / `[ RÉSULTATS ]` / `[ 検査結果 ]` après
   📟, lignes « Potassium: 4.1 mmol/L — 2026-02-10 », « … : 11.8 g/dL (L) … », groupe « O+ ».

### T15 — Antécédents médicaux (📜 pilier natif, sprint 4)

Personas : Kurodo = appendicite (1995-07-10 → 1995-07-12, modérée) + pneumonie (2018-02 →
2018-03, légère) ; Haru = infarctus du myocarde (2015-08-27 → 2015-09, sévère) + tuberculose
(1962 → 1963) ; Kamekichi = aucun. Codes SNOMED du `problems-snomed-ct-ips-free-set` (KB),
`Coding.display` en anglais, libellés FR/JA lus dans la KB à l'affichage.

1. **Fiche + liste** : fiche Haru → section « 📜 ANTÉCÉDENTS (2) » (libellés FR de la KB +
   période) ; tuile 📜 active avec badge 2 → liste `past_problems_recycler` (« 2 antécédents »,
   sous-titre « 2015-08-27 → 2015-09 · ✅ Guérie », détails « 🔴 Sévère · … »).
   Captures `160-detail-haru-past-problems.png`, `161-past-problems-list.png`.
2. **Création codée** (Kurodo) : FAB → `past_problem_form_code_card` → picker des problèmes IPS →
   taper `rougeole` → choisir la rougeole ; Début → « Année seulement » → `1975` ; Fin → « Année
   seulement » → `1975` ; statut « Guérie » ; sévérité « Légère » ; Save. Attendu : carte avec le
   libellé FR, « 1975 → 1975 · ✅ Guérie ». `verify_profiles.py … --expect-ph demo_kurodo=3` PASS ;
   dans le Bundle : `Condition` profil `Condition-uv-ips`, `clinicalStatus` = `resolved`,
   `code.coding[0]` SNOMED avec un `display` **anglais** (pas le libellé FR), `onsetDateTime` =
   `abatementDateTime` = `1975`, `severity` = `LA6752-5`, référencée par la section `11348-0`.
3. **Texte libre** : FAB → `past_problem_form_text` = `Hepatite virale enfance`, statut
   « Inactive », aucune date, Save → Bundle : `code.text` sans coding, `clinicalStatus` =
   `inactive`, pas d'`onsetDateTime` ni d'`abatementDateTime`, pas de `severity`.
4. **Erreurs** : Save vide → erreur inline sur le nom ; Début date exacte `2010-05-01`, Fin
   « Année seulement » `2005` → message rouge « La fin ne peut pas précéder le début » sous la fin,
   toast au Save et rien n'est enregistré ; année `1850` → toast « Saisis une année entre 1900 et
   cette année ».
5. **Édition** : tap « Appendicite » → sévérité « Sévère » → Save → Bundle `severity` =
   `LA6750-9`, `_j.ph[].sv` identique (P6d PASS). Puis remets « Modérée ».
6. **Suppression** des entrées de test (bouton Supprimer et appui long) → Kurodo revient à 📜 2 ;
   `verify … --expect-ph demo_kurodo=2` PASS.
7. **QR texte** : Haru EN/FR/JA → section `📜 [ PAST ILLNESSES ]` / `[ ANTÉCÉDENTS MÉDICAUX ]` /
   `[ 既往歴 ]` après 🧪, lignes « Myocardial infarction — 2015-08-27 → 2015-09 »,
   « Tuberculosis — 1962 → 1963 » ; taille en octets ≤ 2 200 (1 frame).
8. **Validateur HL7** : 3 personas, `-ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a` → 0 erreur.
9. **KB** (copie hors dépôt, supprimée ensuite) :
   `SELECT code, display_en FROM ips_valuesets WHERE vs_id='problems-snomed-ct-ips-free-set' AND code IN ('74400008','233604007','22298006','56717001');`
   puis la même chose sur `ips_valuesets_translations` pour `lang IN ('fr','ja')` — colle la
   sortie brute dans le rapport (les 4 codes du seed doivent y être).

### T16 — Problèmes actifs (🩺 pilier natif, sprint 5)

Personas : Kurodo = hypercholestérolémie (2026-01, légère) ; Haru = insuffisance cardiaque
(2020-11, modérée) + IRC stade 3 (2022) ; Kamekichi = HTA essentielle (2010) + fibrillation
auriculaire (2018-06, modérée) + angor (2021). Avant ce sprint `_j.cn` était vide partout : les
contrôles médicament × maladie (`KbCrossCheck`) ont maintenant de quoi travailler.

1. **Seed** : `run_device_qa.sh` vert avec 🩺 K1 H2 Ka3. Recopie (règle 8) les lignes
   `JEMMA-HYDRATOR … ✅ hydrated · … cn=… · drug×disease=…` pour Haru et Kamekichi.
2. **Fiche + tuile** : fiche Kamekichi → section conditions (3, libellés KB) ; tuile 🩺 active
   avec badge 3 → écran « Problèmes actifs » (`past_problems_hero_emoji` = 🩺, « 3 problèmes
   actifs »). Captures `180-detail-kamekichi-problems.png`, `181-problems-list.png`.
3. **Création codée** (Kurodo) : FAB → le formulaire n'a PAS de bloc « Guérison / fin » ; picker →
   `diabete` → un diabète de type 2 ; Début « Année seulement » `2019` ; statut « 🩺 Active » ;
   sévérité « Modérée » ; Save. Bundle : `Condition` `Condition-uv-ips`, `category` =
   `problem-list-item`, `clinicalStatus` = `active`, `onsetDateTime` = `2019`, pas
   d'`abatementDateTime`, référencée par la section `11450-4` (display « Problem list -
   Reported ») ; `_j.cn[]` = `{c, d_display, st: "active", s: "moderate", dt: "2019"}`.
   `verify … --expect-cn demo_kurodo=2` PASS.
4. **Édition** : statut → « ⚠️ Rechute » → Bundle `clinicalStatus` = `relapse`, `_j.cn[].st` =
   `relapse` (P6e PASS). **Suppression** (appui long) → retour à 🩺 1.
5. **Non-mélange** : Haru garde 📜 2 et 🩺 2 (`--expect-ph demo_haru=2 --expect-cn demo_haru=2`) ;
   aucune Condition « resolved » dans 11450-4, aucune « active » dans 11348-0.
6. **QR texte** Haru EN/FR/JA → section conditions avec insuffisance cardiaque + IRC (fichiers
   `qr/qr-haru-<lang>.txt`, règle 8).
7. **Validateur HL7** 3 personas → 0 erreur (fichiers `validator/<persona>.txt`).
8. **KB** (copie hors dépôt) : `display_en` + traductions fr/ja des codes 13644009, 84114007,
   433144002, 59621000, 49436004, 194828000 dans `problems-snomed-ct-ips-free-set` (sortie brute).

### T17 — Médicament × maladie (cycle 18 : 0 alerte)

Correctif `kb/DrugDiseaseTerms` : la condition est cherchée avec ses termes anglais (display
SNOMED stocké, display primaire KB) puis le libellé localisé, dans les deux sens de contenance.

1. **Dump KB brut** (copie hors dépôt, règle 8) dans `kb/drug-disease.txt` :
   `SELECT drug_atc, disease_name_en, severity FROM drug_disease_interactions WHERE drug_atc IN ('C07AB07','B01AA03','M01AE01','G04BE03','C01DA08','C03CA01','R06AX26','R05DA09') ORDER BY drug_atc, severity, disease_name_en;`
   puis `SELECT COUNT(*) FROM drug_disease_interactions;`.
2. **Hydrateur** : relance le seed puis ouvre Kamekichi et Haru ; recopie les lignes
   `JEMMA-HYDRATOR … drug×disease=N` (attendu : N > 0 pour Kamekichi si le dump contient
   hypertension / heart failure / angina / atrial fibrillation pour ses ATC).
3. **Affichage** : capture de chaque alerte médicament × maladie sur la fiche
   (`190-dd-kamekichi.png`, `191-dd-haru.png`) et texte recopié tel quel.
4. Interface en **japonais** (`cmd locale set-app-locales … --locales ja`, puis retour `fr`) :
   même nombre d'alertes qu'en français (le calcul ne dépend plus de la langue).

### T18 — Couverture DDInter + pluriels (cycle 19)

1. Haru (fexofénadine R06AX26 × « Kidney Diseases ») : attendu `drug×disease=1` et une alerte
   Moderate « … + Maladie rénale chronique stade 3 » (capture `195-dd-haru.png`, texte recopié).
2. Couverture DDInter (copie KB hors dépôt) → `kb/ddinter-coverage.txt`, sorties brutes :
   `SELECT COUNT(*), COUNT(drug_atc) FROM drug_disease_interactions;`
   `SELECT drug_name, drug_atc, COUNT(*) FROM drug_disease_interactions WHERE lower(drug_name) IN ('ibuprofen','furosemide','bisoprolol','sildenafil','dextromethorphan','fexofenadine','warfarin','isosorbide dinitrate') GROUP BY 1,2;`
   `SELECT d.name, d.primary_atc, d.atc_codes FROM ddinter_drugs d WHERE lower(d.name) IN ('ibuprofen','furosemide','bisoprolol','sildenafil');`

### T19 — ATC primaire DDInter (cycle 20)

Les règles DDInter sont rattachées à l'ATC *primaire* du médicament (ibuprofène G02CC01,
furosémide C03EB01, bisoprolol C07FX04, sildénafil G01AE10) : la requête inclut désormais tous
les médicaments DDInter dont `atc_codes` contient l'ATC du profil.

1. Dump brut (copie KB hors dépôt) → `kb/ddinter-persona-rules.txt` :
   `SELECT d.name, i.disease_name_en, i.severity FROM drug_disease_interactions i JOIN ddinter_drugs d ON d.ddinter_id = i.drug_ddinter_id WHERE (',' || d.atc_codes || ',') GLOB '*,M01AE01,*' OR (',' || d.atc_codes || ',') GLOB '*,C07AB07,*' OR (',' || d.atc_codes || ',') GLOB '*,G04BE03,*' OR (',' || d.atc_codes || ',') GLOB '*,C03CA01,*' ORDER BY 1,3,2;`
2. Lignes `JEMMA-HYDRATOR … drug×disease=N` de Kamekichi et Haru (copie brute) ; chaque alerte
   affichée recopiée telle quelle (captures `200-dd-kamekichi.png`, `201-dd-haru.png`).
3. Pour chaque alerte : vérifie qu'elle correspond à une ligne du dump (médicament + maladie).
   Signale toute alerte qui te paraît cliniquement absurde (faux positif de correspondance).

## 4. Logcat de fin

```
adb logcat -d -s JEMMA-PROFILES:* JEMMA-CODEC:* JEMMA-IMMUNIZATIONS-EDIT:* JEMMA-IMMUNIZATIONS-FORM:* JEMMA-IMMUNIZATIONS-ADAPTER:* JEMMA-PROCEDURES-EDIT:* JEMMA-PROCEDURES-FORM:* JEMMA-PROCEDURES-ADAPTER:* JEMMA-DEVICES-EDIT:* JEMMA-DEVICES-FORM:* JEMMA-DEVICES-ADAPTER:* JEMMA-RESULTS-EDIT:* JEMMA-RESULTS-FORM:* JEMMA-RESULTS-ADAPTER:* JEMMA-PASTPROBLEMS-EDIT:* JEMMA-PASTPROBLEMS-FORM:* JEMMA-PASTPROBLEMS-ADAPTER:* JEMMA-KB-CONDITION-PICKER:* JEMMA-SNOMED-CAT:* JEMMA-PROFILE-DETAIL:* JEMMA-HYDRATOR:* JEMMA-QR:* AndroidRuntime:E > $OUT/logs/logcat-ui.txt
python3 qa/device/scrub_logcat.py $OUT/logs/logcat-ui.txt
```
⚠️ `logcat -s` n'accepte pas de joker (`JEMMA-*` ne filtre rien) : utilise la liste
explicite ci-dessus. Le scrub applique de toute façon une liste blanche
(`JEMMA-*`, `AndroidRuntime`) — ne jamais publier un `adb logcat -d` sans `-s`.
Le buffer logcat peut avoir tourné (les tags JEMMA sont bavards) : si le fichier est
vide, relance la séquence concernée puis re-dumpe immédiatement — ne reconstruis
jamais un log à la main.

## 5. Rapport et publication

Remplis `qa/device/report-template.md` → `$OUT/report.md` (une ligne par test T1…T19,
statut ✅ / ❌ / ⚠️ / ⏭, preuve = nom de capture ou fichier, déviations, bugs avec
étapes de reproduction). Puis :

```
cd <racine du dépôt>
git fetch origin device-reports
git worktree add ../jemmapass-device-reports device-reports
SLUG=feat-ips-18-pillars-cleanup
DEST=../jemmapass-device-reports/$SLUG/$(basename $OUT)
mkdir -p "$DEST"
rsync -a --exclude pull/ --exclude 'pull-*/' --exclude backup/ "$OUT"/ "$DEST"/
cd ../jemmapass-device-reports
git add -A
git commit -m "qa(device): $SLUG $(basename $OUT) — <PASS|FAIL> · <n> ✅ · <m> ❌"
git push origin device-reports
```

Le message de commit résume le verdict ; `report.md` est la source de vérité.
Termine ta session en indiquant le chemin publié et le verdict global.
