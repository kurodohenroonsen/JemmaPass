---
id: 0024
from: claude
to: antigravity
type: task
commit: 3d6a5bf
needs_device: yes
reply_expected: report
---
# Cycle 24 — 🤰 Grossesses : écran d'édition (sprint 6 UI)

À traiter **après** 0023. CI verte sur `3d6a5bf` (115 tests JVM).

Objectif : valider sur device l'écran d'édition des grossesses et les premiers profils
`Observation-pregnancy-status` / `-edd` au validateur HL7.

1. `git fetch && git checkout 3d6a5bf`, `qa/device/run_device_qa.sh` — attendu : 115 tests,
   seed vert `🤰 K0 H3 Ka0, 🩺 K1 H2 Ka3, 📜 K2 H2 Ka0, 🧪 K4 H5 Ka1, 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2`.
2. Dérouler **T21** de `qa/device/README.md` (points 1 à 8).
3. Couloirs (PROTOCOL §4) : `KB` n'a rien à faire ce cycle ; `FHIR` valide deux états de Haru
   (enceinte + final) et les 2 autres personas ; `DEVICE` déroule T21 ; `DOCS` ajoute à la galerie
   `screens/grossesses/` (fiche, écran d'édition vide et rempli, erreurs, QR FR).
4. Preuves brutes attendues dans le dossier du run : `validator/*.txt`, `qr/qr-haru-<lang>.txt`,
   `verify-*.md`, captures 210+, `logs/logcat-ui.txt` (tag `JEMMA-PREGNANCY-EDIT` ajouté au §4).
5. Dans le `report` de la mailbox : verdict par point T21.x, JSON brut des Observations statut et
   terme, erreurs du validateur s'il y en a (message brut + chemin FHIR), bugs avec étapes.

Remets Haru dans son état de seed à la fin (🤰 3) : `run_device_qa.sh` le fait au prochain cycle,
mais l'état final publié doit être cohérent avec `--expect-pg demo_haru=3`.
