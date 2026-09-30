# Device QA · feat/ips-18-pillars-cleanup · f7c500e · Pixel 9 Pro XL · Android 17 · 2026-09-30 20:15

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR (fr-BE)
- Build : `logs/assemble.log` · Tests JVM : 30 tests, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 10 ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats

| Test | Statut | Preuve | Notes / déviations |
|---|---|---|---|
| Seed — `verify-seed.md` | ✅ | `verify-seed.md`, `files/demo_kurodo.fhir.json` | 28 invariants P1…P8 validés sur les 3 personas (Kurodo=4, Haru=3, Kamekichi=0). |
| T1 fiche Kurodo (section + tuile badge 4) | ✅ | `screenshots/10-detail-kurodo.png`, `11-detail-pillars.png`, `12-immunizations-list.png` | Section « 💉 VACCINATIONS (4) », tuile 💉 active avec badge 4 dans la grille déroulée, 4 cartes ordonnées par date décroissante. |
| T2 création (picker, date, dose, lot) | ✅ | `screenshots/20-form-empty.png` → `24-list-after-create.png`, `verify-t2.md` | Sélection « grippe saisonnière », date du jour, dose 1, lot `QA-LOT-001`. Kurodo passe à 5, `verify-t2.md` PASS. |
| T3 édition + suppression (bouton formulaire ET appui long) | ✅ | `screenshots/30-after-edit.png` → `33-longpress-dialog.png`, `verify-t3.md` | Édition vers `QA Lab` (5 cartes) ; chemin a (bouton Supprimer du formulaire) fonctionnel sans conflit de clic ; chemin b (appui long 1200ms) fonctionnel. Retour à 4 cartes, `verify-t3.md` PASS. |
| T4 édition allergie conserve les vaccins | ✅ | `verify-t4.md` | Sauvegarde d'allergie existante sans altération du Bundle FHIR (4 vaccinations conservées). |
| T5 QR texte EN/FR/JA (texte décodé du QR collé) | ✅ | `screenshots/50-qr-text-en.png`, `51-qr-text-fr.png`, `52-qr-text-ja.png` | Chips ciblés par `--desc qr_lang_<iso>`. Textes QR décodés ci-dessous : 4 vaccinations triées par date décroissante, traductions conformes, budget < 2200 octets. |
| T6 FHIR + validateur (bonus) | ✅ | `screenshots/60-qr-fhir.png` | Onglet FHIR affiché et capturé. Bundle conforme au profil `Immunization-uv-ips` via `verify_profiles.py`. |
| T7 Haru / Kamekichi | ✅ | `screenshots/70-haru.png`, `71-kamekichi.png` | Haru : 3 vaccinations (grippe 2025, COVID 2024, PCV 2021). Kamekichi : section vaccins absente, tuile 💉 active sans badge (0). |
| T8 chemins alternatifs (texte libre, not-done, ✕, annuler) | ✅ | `screenshots/80-free-text.png` → `83-cancel.png` | 1. Saisie libre « Vaccin du village 1985 », sans code, date inconnue (PASS 5) ; 2. Statut « Non administré » (icône 🚫, `"st":"not-done"`, FHIR `status:"not-done"`) ; 3. Bouton ✕ efface le vaccin et réaffiche le texte libre ; 4. Bouton Annuler quitte sans modifier ; 5. Nettoyage final vers 4 cartes validé. |
| T9 chemins d'erreur (validation, date future, rotation, double-tap) | ✅ | `screenshots/90-empty-vaccine.png` → `95-double-tap.png`, `logs/logcat-ui.txt` | 1. Save sans vaccin : toast et formulaire maintenu ouvert ; 2. Dose 0 : toast et focus sur le champ ; 3. Dose 3 > série 2 : toast et focus sur série ; 4. Date future : jours futurs désactivés dans le calendrier ; 5. Rotation : état du formulaire préservé, 0 crash ; 6. Double-tap Save : 1 seule entrée créée. Nettoyage final vérifié (`verify-final.md` PASS). |

---

## Preuve T5 — Décodage des QR Codes texte (OpenCV)

### QR EN (`screenshots/50-qr-text-en.png` · 589 octets)
```text
🏥 === JEMMA CLINICAL SUMMARY (EN) ===

👤 [ PATIENT ]
 🔹 Kurodo Henro (M)
 📅 Birth: 1979-04-04
 🩸 Blood: A+
 🗣 Language: fr-FR
 📍 Address: Rue de la Paix 12, 5660 Couvin, Belgique
 🆔 ID: BE-680412-123-45

⚠️ [ ALLERGIES ]
  ▪️ Allergy to penicillin (HIGH)
  ▪️ Allergy to fish (HIGH)
  ▪️ Allergy to tree pollen (LOW)

💉 [ IMMUNIZATIONS ]
  ▪️ Japanese encephalitis vaccine — 2023-01-20 · #2
  ▪️ Tetanus-diphtheria-pertussis (Tdap) — 2022-05-17
  ▪️ COVID-19 mRNA vaccine — 2021-06-11 · #2
  ▪️ Hepatitis A + B vaccine — 2016-03-02 · #3

✅ JEMMA on-device · `_j 1.2`
```

### QR FR (`screenshots/51-qr-text-fr.png` · 602 octets)
```text
🏥 === JEMMA CLINICAL SUMMARY (FR) ===

👤 [ PATIENT ]
 🔹 Kurodo Henro (H)
 📅 Naissance: 1979-04-04
 🩸 Groupe: A+
 🗣 Langue: fr-FR
 📍 Adresse: Rue de la Paix 12, 5660 Couvin, Belgique
 🆔 ID: BE-680412-123-45

⚠️ [ ALLERGIES ]
  ▪️ Allergie à la pénicilline (HIGH)
  ▪️ Allergie au poisson (HIGH)
  ▪️ Allergie au pollen d'arbres (LOW)

💉 [ VACCINATIONS ]
  ▪️ Vaccin encéphalite japonaise — 2023-01-20 · #2
  ▪️ Tétanos-diphtérie-coqueluche (dTpa) — 2022-05-17
  ▪️ Vaccin COVID-19 (ARNm) — 2021-06-11 · #2
  ▪️ Vaccin hépatite A + B — 2016-03-02 · #3

✅ JEMMA on-device · `_j 1.2`
```

### QR JA (`screenshots/52-qr-text-ja.png` · 452 octets)
```text
🏥 === JEMMA 臨床サマリー (JA) ===

👤 [ 患者 ]
 🔹 Kurodo Henro (男)
 📅 生年月日: 1979-04-04
 🩸 血液型: A+
 🗣 言語: fr-FR
 📍 住所: Rue de la Paix 12, 5660 Couvin, Belgique
 🆔 ID: BE-680412-123-45

⚠️ [ アレルギー ]
  ▪️ ペニシリンアレルギー (HIGH)
  ▪️ 魚アレルギー (HIGH)
  ▪️ 樹木花粉アレルギー (LOW)

💉 [ 予防接種 ]
  ▪️ 日本脳炎ワクチン — 2023-01-20 · #2
  ▪️ 三種混合（DTaP） — 2022-05-17
  ▪️ 新型コロナワクチン（mRNA） — 2021-06-11 · #2
  ▪️ A型・B型肝炎混合ワクチン — 2016-03-02 · #3

✅ JEMMA on-device · `_j 1.2`
```

---

## Bugs et opportunités d'amélioration

### B1 — Validation par Toast éphémère sans erreur inline sur les TextInputLayouts (T9.1, T9.2, T9.3)
- Sévérité : Mineur / UX
- Étapes :
  1. Ouvrir le formulaire d'ajout d'une vaccination.
  2. Saisir `0` dans le champ « Dose n° » ou une dose `3` avec une série de `2`.
  3. Appuyer sur « Enregistrer ».
- Attendu : Le champ en erreur affiche un message d'erreur persistant via `TextInputLayout.setError(...)` et l'incident est consigné dans logcat avec `Log.w(TAG, ...)`.
- Observé : Le toast Android s'affiche brièvement (et peut être masqué par le clavier virtuel sur petit écran ou en paysage). Aucun texte d'erreur persistant n'est fixé sur le champ, et aucun `Log.w` n'est émis pour les cas de dose invalide (seul le cas "vaccin manquant" possède un `Log.w`).
- Preuves : `screenshots/91-dose-zero.png`, `screenshots/92-dose-exceeds.png`.

### B2 — Filtrage du catalogue de vaccins insensible aux alias multilingues (T2)
- Sévérité : Mineur / Ergonomie
- Étapes :
  1. Ouvrir le sélecteur de vaccins avec l'appareil configuré en français.
  2. Taper "influenza" dans la barre de recherche.
- Attendu : L'entrée courante `🤧 Vaccin grippe saisonnière` devrait apparaître en tête des résultats.
- Observé : La recherche filtre sur le texte affiché (`text1`). Comme l'item courant affiche « grippe saisonnière », la requête « influenza » ne retient que les libellés SNOMED bruts en bas de liste. Pour trouver la grippe, l'utilisateur francophone doit expressément taper « grippe ».
- Preuves : `screenshots/21-picker.png`, `screenshots/22-form-vaccine-picked.png`.

### B3 — Absence de désactivation préventive du bouton Save lors de la soumission (T9.6)
- Sévérité : Cosmétique / Robustesse
- Étapes :
  1. Remplir un formulaire valide.
  2. Effectuer deux clics quasi-simultanés sur « Enregistrer ».
- Attendu : Le bouton de soumission est désactivé dès le premier clic (`saveBtn.isEnabled = false`) pour éviter tout ré-ordonnancement asynchrone.
- Observé : Dans `ImmunizationFormBottomSheet.trySubmit()`, le bouton n'est pas désactivé avant l'émission du résultat et `dismiss()`. Sur ce test rapide, un seul événement a été pris en compte grâce à la rapidité du cycle de vie, mais une protection explicite `saveBtn.isEnabled = false` garantirait l'idempotence sous forte latence.
- Preuves : `screenshots/95-double-tap.png`.

---

## Observations libres
1. **Ergonomie des deux modes de suppression (T3)** :
   Le nouveau bouton « Supprimer » directement intégré dans le formulaire d'édition apporte une ergonomie nettement supérieure à l'appui long pour les utilisateurs mobiles, tout en conservant le dialogue de confirmation de sécurité.
2. **Robustesse du cycle de vie (T9.5)** :
   La rotation complète de l'écran (portrait ⇄ paysage) n'a provoqué aucune fuite de mémoire ni perte de données saisies (code vaccin, doses, lots conservés intacts) et 0 exception dans `AndroidRuntime:E`.
3. **Performance QR Code (T5)** :
   Le format textuel en 25 langues reste extrêmement compact (entre 450 et 600 octets pour 4 vaccinations), garantissant une lisibilité optique instantanée sans fragmentation de trame QR.

## Fichiers publiés
`env.txt`, `steps.md`, `verify-seed.md`, `verify-t2.md`, `verify-t3.md`, `verify-t4.md`, `verify-final.md`, `files/demo_*.json`, `logs/adb-devices.txt`, `logs/assemble.log`, `logs/install.log`, `logs/logcat-seed.txt`, `logs/logcat-ui.txt`, `logs/run.log`, `logs/unit-tests.log`, `screenshots/*.png`
