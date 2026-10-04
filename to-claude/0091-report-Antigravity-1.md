---
id: 0091
from: antigravity
to: claude
type: report
branch: ag/0091-qr-allergy-order
head: c6d0b36
relates_to: 0091
orchestrator: Antigravity-1
---

# Rapport Tour 1 — Défaut n°1 (Ordre de criticité des allergies sur QR texte) & Squelette Défaut n°2

`orchestrator: Antigravity-1`

---

## 1. Verdict Défaut n°1 (Allergies sur QR Texte)
- **Statut** : 100% VERT.
- **Branche** : `ag/0091-qr-allergy-order` @ `c6d0b36` (poussée sur `origin/ag/0091-qr-allergy-order`).
- **Tests** : 4 tests exécutés, 4 passés, 0 échec (`BUILD SUCCESSFUL in 4m 18s`).
- **Tests UC-QRT-020..023** :
  - `UC-QRT-020` (tri par criticité HIGH > UNABLE_TO_ASSESS > LOW) : ✅ PASS
  - `UC-QRT-021` (survie d'une allergie HIGH saisie en dernier lors d'une coupure) : ✅ PASS
  - `UC-QRT-022` (aucune allergie LOW imprimée si une HIGH a été coupée) : ✅ PASS
  - `UC-QRT-023` (ordre de saisie conservé au sein d'une même criticité — verrou) : ✅ PASS
- **Tests intacts** : aucune modification apportée à `QrTextAllergyOrderTest.kt`.

## 2. Ce qui a été implémenté
Dans [`JemmaTextPayloadBuilder.kt`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/0091-qr-allergy-order/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt#L185-L188) :
- Réutilisation directe de [`PdfPillarLayout.sortByCriticality`](https://github.com/kurodohenroonsen/JemmaPass/blob/feat/ips-18-pillars-cleanup/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/pdf/PdfPillarLayout.kt#L59) :
  ```kotlin
  part(
      "⚠️", "allergies_title", RANK_ALLERGIES,
      be.heyman.android.jemmapassdemo.pdf.PdfPillarLayout.sortByCriticality(hydrated.allergies) { it.criticality.name },
  ) { a -> formatAllergy(a) },
  ```
- Les allergies de criticité `HIGH` (rang 0) sont positionnées en tête de section, suivies de `UNABLE_TO_ASSESS` (rang 1), puis de `LOW` (rang 2).
- Le tri étant stable (`sortedBy`), l'ordre de saisie utilisateur au sein d'un même rang est scrupuleusement préservé (UC-QRT-023).
- Lors de la troncature par manque de place (`victim.lines.removeAt(victim.lines.size - 1)`), les lignes du bas (`LOW`) sont retirées en premier : une allergie vitale (`HIGH`) est ainsi garantie d'être imprimée en premier et coupée en dernier.

## 3. Squelette Défaut n°2 (Fiche Secouriste)
- Squelette pur sans import Android créé dans [`RescueAllergyFormat.kt`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/0091-rescue-allergy-line/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/pillars/RescueAllergyFormat.kt) :
  ```kotlin
  package be.heyman.android.jemmapassdemo.pillars

  data class RescueAllergyLine(val text: String, val severe: Boolean, val spoken: String)

  object RescueAllergyFormat {
      /** Ligne de la fiche secouriste pour une allergie reçue (`display`/`name`/`c`, criticité `s`/`criticality`, réaction `d`/`manifestations`). */
      fun line(entry: org.json.JSONObject, labels: be.heyman.android.jemmapassdemo.qr.CodeLabelResolver, lang: String): RescueAllergyLine = TODO()
  }
  ```
- Branche dédiée créée et poussée : [`ag/0091-rescue-allergy-line`](https://github.com/kurodohenroonsen/JemmaPass/tree/ag/0091-rescue-allergy-line) @ `6e9c767`.
- Prêt pour l'écriture des tests par Claude.

## 4. Leçon
- **Ce qui a permis au défaut d'exister** : La construction de la section du QR texte consommait la liste brute dans son ordre chronologique de saisie, alors que l'algorithme de réduction budgétaire supprimait systématiquement le dernier élément de la liste.
- **Règle préventive** : Tout canal d'urgence contraint en taille (QR texte, écran de synthèse, fiche secours, PDF) doit impérativement faire précéder sa troncature d'un tri clinique priorisant la gravité (`HIGH > UNKNOWN > LOW`). Le tri de `PdfPillarLayout` fait désormais référence commune pour le PDF et le QR texte.
