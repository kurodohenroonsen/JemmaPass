---
id: 0100
from: antigravity-1
to: claude
type: report
commit: 8bab143
needs_device: no
reply_expected: ack
---

# Rapport Tour 4 — Antigravity-1
`orchestrator: Antigravity-1`

## 1. Synthèse Exécutive
- **Couloir** : ANTIGRAVITY-1 (`JemmaPassAndroidDemo/`).
- **Périmètre exécuté** : Les deux tâches assignées au message 0100 §2, dans l'ordre strict prescrit.
- **Tâche 1 (`ag/0091-rescue-allergy-line`)** : Merge de `origin/tests/rescue-allergy-line-2` (`bf1cd1b`). Ressources `rescue_allergy_severe` créées dans les trois langues (`values`, `values-fr`, `values-ja`). Résolveur branché dans `PatientDetailFragment.kt` lisant `getString(R.string.rescue_allergy_severe)`. Couleur de la ligne sévère passée à `#F87171` (UX tranche 3 §E, ratio 5,29:1). Suite complète : **527 run · 0 failed · 0 ignored** (100 % succès). Poussé sur `origin/ag/0091-rescue-allergy-line` au commit `13aac2c`.
- **Tâche 2 (`ag/0100-qr-devices-rank`)** : Branche créée depuis `origin/tests/qr-devices-rank` (`8c924c7`). Modification minimale de `JemmaTextPayloadBuilder.kt` : `RANK_DEVICES = 5`, `RANK_FUNCTIONAL = 6`, `RANK_CONTACTS = 7`. Ordre d'affichage des sections inchangé. Les 5 tests `UC-QRT-030..034` sont 100 % verts, le verrou `UC-CT-020` reste vert. Poussé sur `origin/ag/0100-qr-devices-rank` au commit `8bab143`. Aucun fichier sous `src/test/` n'a été touché.

---

## 2. Tâche 1 : `ag/0091-rescue-allergy-line` (@ `13aac2c`)

### 2.1. Modifications apportées
1. **Ressource chaîne `rescue_allergy_severe` (PROTOCOL §9.1)** :
   - `values/strings.xml` : `<string name="rescue_allergy_severe">SEVERE</string>`
   - `values-fr/strings.xml` : `<string name="rescue_allergy_severe">GRAVE</string>`
   - `values-ja/strings.xml` : `<string name="rescue_allergy_severe">重度</string>`
2. **`PatientDetailFragment.kt`** :
   - Résolveur d'étiquettes :
     ```kotlin
     private val allergyLabelResolver = CodeLabelResolver { _, code, _ ->
         if (code.equals("high", ignoreCase = true)) {
             getString(R.string.rescue_allergy_severe)
         } else null
     }
     ```
   - Couleur de la ligne sévère dans `renderAllergiesRawFirst` et `renderAllergiesHydrated` :
     ```kotlin
     if (allergyLine.severe) {
         child.setTextColor(0xFFF87171.toInt())
     }
     ```
     (Remplacement de `0xFFEF4444` par `0xFFF87171`, spécification UX tranche 3 §E, 5,29:1).
   - Aucun mot de sévérité traduit en dur dans le code Kotlin (vérifié par `UC-RSQ-011`, 0 occurrence).

### 2.2. Sortie brute de la suite complète
Exécution sur `jemmapass-ag1` (branch `ag/0091-rescue-allergy-line`) :
```text
> Task :app:testDebugUnitTest

[Incubating] Problems report is available at: .../build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 5m 32s
37 actionable tasks: 19 executed, 18 up-to-date
```
Rapport HTML `app/build/reports/tests/testDebugUnitTest/index.html` :
- **527 tests run · 0 failures · 0 ignored** (100% success rate).
- `RescueAllergyLabelsInResourcesTest` :
  - `UC-RSQ-010 the severity word of the rescue line is a string resource in the three languages` : PASSED (0.185s).
  - `UC-RSQ-011 no translated severity word is written in the Kotlin sources of the rescue card` : PASSED (0.057s).

---

## 3. Tâche 2 : `ag/0100-qr-devices-rank` (@ `8bab143`)

### 3.1. Modifications apportées (`JemmaTextPayloadBuilder.kt`)
Modification minimale de 3 lignes pour intégrer `Analyse-0002` (priorité a) :
```kotlin
    private const val RANK_ALLERGIES = 1
    private const val RANK_MEDICATIONS = 2
    private const val RANK_CONDITIONS = 3
    private const val RANK_PREGNANCY = 4
    private const val RANK_DEVICES = 5
    private const val RANK_FUNCTIONAL = 6
    private const val RANK_CONTACTS = 7
    private const val RANK_PATIENT_EXTRA = 8   // address, phone, e-mail, national id
    private const val RANK_PAST_PROBLEMS = 9
    private const val RANK_PROCEDURES = 10
    private const val RANK_RESULTS = 11
    private const val RANK_IMMUNIZATIONS = 12
```
- Les dispositifs médicaux implantés (`RANK_DEVICES = 5`) sont désormais prioritaires lors de la troncature sur le statut fonctionnel (6), les contacts d'urgence (7), les extras patient (8), les antécédents (9), les actes (10), la biologie (11) et les vaccins (12).
- Les allergies (1) et médicaments (2) restent strictement prioritaires sur les dispositifs (verrou `UC-QRT-033`).
- Le statut fonctionnel (6) reste strictement prioritaire sur les contacts d'urgence (7) (verrou `UC-CT-020`).
- Au sein de la section dispositifs, le tri existant place les dispositifs actifs avant les dispositifs inactifs (`status == "active" || status.isNullOrBlank()`), et la troncature retirant en fin de liste (`lines.removeAt(lines.size - 1)`), les dispositifs inactifs sont sacrifiés avant les actifs (verrou `UC-QRT-034`).
- L'ordre d'affichage des sections dans le QR texte (`listOf(...)` ligne 183) n'a pas été modifié.
- Aucun fichier de test n'a été modifié (`git diff` : 1 seul fichier modifié, `JemmaTextPayloadBuilder.kt`).

### 3.2. Résultats des tests `QrTextDevicesRankTest` & `ContactsPillarTest`
- `UC-QRT-030 an implanted device is not dropped while functional status lines are still printed` : **PASSED**
- `UC-QRT-031 an implanted device is not dropped while contact lines are still printed` : **PASSED**
- `UC-QRT-032 no device is dropped while any lower section still has a line` : **PASSED**
- `UC-QRT-033 allergies and medications still go before devices - lock` : **PASSED**
- `UC-QRT-034 inside the devices section an inactive device is dropped before an active one - lock` : **PASSED**
- `UC-CT-020 emergency contacts hold rank 6 when the text QR is over budget - lock` : **PASSED**

### 3.3. Constat et diagnostic sur `RandomProfileInvariantsTest` (UC-QRT-008)
Sur la suite de 521 tests, 520 passent. Un seul échec est relevé :
```text
RandomProfileInvariantsTest > UC-QRT-008 random profiles - the text QR fits one frame and never loses an entry in silence FAILED
    java.lang.AssertionError: profile(6) EN ♿ lost entries while 📟 (less important) is still printed expected:<2> but was:<0>
    at be.heyman.android.jemmapassdemo.testsupport.TextQrProbe.assertPriority(TextQrProbe.kt:157)
    at be.heyman.android.jemmapassdemo.qr.RandomProfileInvariantsTest.UC-QRT-008(RandomProfileInvariantsTest.kt:399)
```
**Pièce et diagnostic** :
1. Dans `TextQrProbe.kt:147-150` (classe d'assertion utilitaire dans `src/test/.../testsupport`), la liste d'ordre est codée en dur selon l'ancienne décision du 2026-10-02 (UC-QRT-009, message 0037 A) :
   ```kotlin
   val order = listOf(
       ALLERGIES, MEDICATIONS, CONDITIONS, PREGNANCY, FUNCTIONAL, CONTACTS,
       DEVICES, PATIENT, PAST_PROBLEMS, PROCEDURES, RESULTS, IMMUNIZATIONS,
   )
   ```
2. Dans cette liste historique, `FUNCTIONAL` (`♿`) est avant `DEVICES` (`📟`), signifiant que `DEVICES` était considéré comme « moins important » que `FUNCTIONAL`.
3. Lorsque `profile(6)` déborde du budget, `JemmaTextPayloadBuilder` applique la nouvelle règle vitale d'`Analyse-0002` (`UC-QRT-030`) et retire `FUNCTIONAL` avant `DEVICES`.
4. `TextQrProbe.assertPriority` échoue donc en constatant que `FUNCTIONAL` a été coupé alors que `DEVICES` est imprimé.
5. Conformément à PROTOCOL §8 (« *Un test jugé faux → le dire dans le rapport, ne pas le modifier* ») et à la consigne « *sans toucher au test* », Antigravity-1 n'a pas modifié `TextQrProbe.kt`. Claude pourra aligner `TextQrProbe.order` (en déplaçant `DEVICES` avant `FUNCTIONAL`) lors de la fusion.

---

## 4. Bilan des Commandes Shell (PROTOCOL §7 bis)
- **Commandes lancées ce passage** : 6 exécutions de la commande unique autorisée `bash qa/device/lane-main.sh` via `/tmp/jp/main/task.txt`.
- **Nouvelles commandes** : **0**.
- Zéro commande shell libre (`python3 -c`, `cat << EOF`, etc.). Aucun chemin absolu `/Users/...` dans le code poussé ni dans ce rapport.

---

## 5. Ligne « Leçon » (Conformément à PROTOCOL §12 étape 5)

> **Leçon** : *Lorsqu'une règle métier d'arbitrage de budget d'urgence est révisée pour des motifs vitaux (ici la priorité clinique d'un stimulateur cardiaque sur la mention d'une canne de marche), les assertions transversales de test (comme `TextQrProbe.assertPriority`) qui figeaient l'ancienne hiérarchie doivent être identifiées en amont pour éviter un faux conflit entre le nouveau test unitaire et le test d'intégration des profils aléatoires.*

---

`orchestrator: Antigravity-1`
