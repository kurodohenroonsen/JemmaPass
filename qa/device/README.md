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
| 5 | lancement, 15 s, capture, pull de `profiles/`, logcat filtré | `screenshots/01-launch.png`, `files/demo_*.json`, `logs/logcat-seed.txt` |
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

Suppression : appui long sur la carte `QA Lab` (`ui.py` ne fait pas d'appui long —
utilise `adb shell input swipe X Y X Y 1200` sur le centre de la carte, coordonnées via
`$UI find --text "QA Lab"`), puis bouton **Delete** / **Supprimer**.
```
$UI screenshot $OUT/screenshots/31-delete-dialog.png
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

Fiche Kurodo → bouton **Export** (toolbar, `desc` = "Export") → option
« 📱 QR Codes… » → onglet `qr_tab_text` → chip `qr_lang_en` puis `qr_lang_fr`.
```
$UI tap --desc "Export"
$UI tap --text "QR Codes"
$UI wait 3
$UI tap --id qr_tab_text
$UI tap --id qr_lang_en
$UI wait 2
$UI text --text "IMMUNIZATIONS"
$UI screenshot $OUT/screenshots/50-qr-text-en.png
$UI tap --id qr_lang_fr
$UI wait 2
$UI text --text "VACCINATIONS"
$UI screenshot $OUT/screenshots/51-qr-text-fr.png
```
Attendu : le texte du payload (`qr_payload_text`) contient une section
`💉 [ IMMUNIZATIONS ]` (EN) / `💉 [ VACCINATIONS ]` (FR) avec 4 lignes
`Tdap — 2022-05-17 · #…`. Note si la section est tronquée par le plafond 2200 octets.

### T6 — Onglet FHIR + validateur (bonus, si réseau)

Onglet `qr_tab_fhir` → capture. Puis, hors appareil, valider `files/demo_kurodo.fhir.json`
sur https://www.ipsviewer.com/classic (rendu de la section Immunizations) et, si le
validateur HL7 est disponible localement, `java -jar validator_cli.jar demo_kurodo.fhir.json -version 4.0.1 -ig hl7.fhir.uv.ips`.
Colle le résumé (erreurs/avertissements) dans le rapport.

### T7 — Haru + Kamekichi (lecture seule)

Fiche Haru : section « 💉 … (3) » (grippe 2025, COVID 2024, PCV 2021), libellés en
japonais si l'appareil est en JA. Fiche Kamekichi : pas de section vaccins, tuile
💉 active avec badge absent (0). Captures `70-haru.png`, `71-kamekichi.png`.

### T8 — Régression rapide hors périmètre (5 min)

Radar SOS : mode Rescue → radar → STOP, sans crash (`AndroidRuntime:E` vide dans
`adb logcat -d`). Médicaments Haru : ouvrir/fermer. Une ligne par item dans le rapport.

## 4. Logcat de fin

```
adb logcat -d -s JEMMA-PROFILES:* JEMMA-CODEC:* JEMMA-IMMUNIZATIONS-EDIT:* JEMMA-IMMUNIZATIONS-FORM:* JEMMA-IMMUNIZATIONS-ADAPTER:* JEMMA-PROFILE-DETAIL:* JEMMA-QR:* AndroidRuntime:E > $OUT/logs/logcat-ui.txt
```

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
