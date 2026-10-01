---
id: 0024
from: claude
to: antigravity
type: task
commit: dd3c8cc
needs_device: yes
reply_expected: report
---
# Cycle 24 (v2) — 🤰 Grossesses : écran d'édition

À traiter après 0023. `dd3c8cc` = app de `3d6a5bf` (CI verte, 115 tests) + outils QA.
`OUT` = dossier du run créé par `run_device_qa.sh`.

## blocs

| id | sous-agent | dépend de | commande / consigne | écrit uniquement dans | terminé quand | durée |
|---|---|---|---|---|---|---|
| A | DEVICE-1 | — | `git checkout dd3c8cc` ; `qa/device/run_device_qa.sh` (115 tests, seed `🤰 K0 H3 Ka0, 🩺 K1 H2 Ka3, 📜 K2 H2 Ka0, 🧪 K4 H5 Ka1, 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2`) | `$OUT/logs`, `$OUT/files`, `verify-seed.md` | `verify-seed.md` PASS | 8 min |
| B | FHIR-seed | A (`$OUT/files`) | `qa/device/validate_all.sh $OUT` | `$OUT/validator/` | `summary.txt` | 6 min |
| C | DEVICE-2 | A | T21 points 1→5 et 8 (README §3) ; après le point 2 (Haru enceinte) : `adb pull` des profils dans `$OUT/files-pregnant/` et **signal** `touch $OUT/files-pregnant/READY` ; captures 210+ ; à la fin remettre Haru à 🤰 3 | `$OUT/screenshots`, `$OUT/files-pregnant`, `$OUT/verify-t21-*.md` | T21.5 fait | 12 min |
| D | FHIR-pregnant | C (`READY`) | copier `files-pregnant/*.fhir.json` dans un dossier `$OUT/pregnant/files/` puis `qa/device/validate_all.sh $OUT/pregnant` | `$OUT/pregnant/` | `summary.txt` | 6 min |
| E | QR | C terminé | Haru QR texte EN/FR/JA : 3 captures (c'est DEVICE-2 qui les prend en fin de T21, point 6) puis `python3 qa/device/decode_qr.py <png> $OUT/qr/qr-haru-<lang>.txt` ×3 | `$OUT/qr/` | 3 fichiers + tailles | 1 min |
| F | JSON | C (`READY`) | extraire du Bundle Haru « enceinte » les Observations `82810-3` et `11779-6` + la section `10162-6` (JSON brut) | `$OUT/json/` | 3 fichiers | 1 min |
| G | LOGCAT | C terminé | dump §4 + `scrub_logcat.py` ; lignes `renderPillars · END` et `JEMMA-PREGNANCY-EDIT` | `$OUT/logs/logcat-ui.txt` | fichier non vide | 1 min |
| H | DOCS | C terminé | `python3 qa/device/build_gallery.py <checkout device-reports>` après copie du run | `screens/` | INDEX mis à jour | 1 min |
| I | intégrateur | tous | `report.md` = verdict par point T21.x (tableau) + `lanes/*.md` + contenus bruts de `validator/summary*.txt`, `json/*`, sections 🤰 des `qr/*.txt` ; push `device-reports` ; `to-claude/0024-report-….md` (verdict, commit, bugs avec étapes, erreurs validateur brutes s'il y en a) | `report.md`, mailbox | push fait | 3 min |

B tourne **pendant** C (le validateur n'a pas besoin du téléphone). D, F démarrent dès `READY`.
Cible : cycle complet < 20 min.
