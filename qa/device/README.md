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
| 1 | `:app:testDebugUnitTest` — attendu **30 tests, 0 échec** | `logs/unit-tests.log` |
| 2 | `:app:assembleDebug` | `logs/assemble.log` |
| 3 | `adb install -r -g` (permissions runtime accordées, données conservées) | `logs/install.log` |
| 4 | sauvegarde de `profiles/`, suppression des seuls fichiers `demo_*` → re-seed | `backup/` (non publié) |
| 5 | lancement, 15 s, pull de `profiles/`, logcat filtré puis scrubbé (pas de capture : la liste des profils peut montrer un profil réel) | `files/demo_*.json`, `logs/logcat-seed.txt` |
| 6 | `verify_profiles.py` — invariants P1…P8 sur les 3 personas, attendu Kurodo=4, Haru=3, Kamekichi=0 | `verify-seed.md`, `steps.md` |

`verify_profiles.py` contrôle : Bundle `document` + Composition en tête, URNs
`urn:uuid:` déterministes (UUID v3), projection `_j.im` ⇄ ressources `Immunization`
(codes + dates), section LOINC `11369-6` pointant exactement sur les fullUrl des
Immunization, profil `Immunization-uv-ips` + référence patient, sections legacy
(allergies/médicaments/problèmes) toujours présentes.

## 3. Protocole UI (à dérouler avec `ui.py`, une capture par point de contrôle)

`ui.py` pilote l'écran via `uiautomator dump` + `input tap` (tous les contrôles ont
un `resource-id`). Adapte si l'écran diffère, et **note toute déviation** dans le rapport.
Langue attendue de l'appareil : FR ou EN (les libellés ci-dessous sont donnés en EN / FR).

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

## 4. Logcat de fin

```
adb logcat -d -s JEMMA-PROFILES:* JEMMA-CODEC:* JEMMA-IMMUNIZATIONS-EDIT:* JEMMA-IMMUNIZATIONS-FORM:* JEMMA-IMMUNIZATIONS-ADAPTER:* JEMMA-PROFILE-DETAIL:* JEMMA-QR:* AndroidRuntime:E > $OUT/logs/logcat-ui.txt
python3 qa/device/scrub_logcat.py $OUT/logs/logcat-ui.txt
```
Le buffer logcat peut avoir tourné (les tags JEMMA sont bavards) : si le fichier est
vide, relance la séquence concernée puis re-dumpe immédiatement — ne reconstruis
jamais un log à la main.

## 5. Rapport et publication

Remplis `qa/device/report-template.md` → `$OUT/report.md` (une ligne par test T1…T8,
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
