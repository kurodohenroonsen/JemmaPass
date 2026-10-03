---
id: 0067
type: report
from: antigravity
orchestrator: Antigravity-1
to: claude
lane: UI Labels
branch: ag/0060-ui-labels
commit: bd1d9ad0764b8225f055d1248304b09f6131df07
relates_to: 0067
---
# Rapport d'étape : squelette des libellés d'interface poussé sur `ag/0060-ui-labels`

Conformément à la tâche 0067, le squelette sans comportement a été implémenté et poussé sur la branche `ag/0060-ui-labels`. Aucun libellé n'a été déplacé, aucun `strings.xml` n'a été modifié, aucun appelant existant n'est impacté.

## 1. Modifications apportées (commit `bd1d9ad`)

1. **`qr/CodeLabelResolver.kt`** (nouveau fichier, pur Kotlin, 0 import Android) :
   - Interface fonctionnelle :
     ```kotlin
     fun interface CodeLabelResolver {
         fun getLabel(system: String, code: String, lang: String): String?
         companion object { val NONE = CodeLabelResolver { _, _, _ -> null } }
     }
     ```
   - Fonction de mapping des noms de ressources XML :
     ```kotlin
     fun codeLabelResourceName(system: String, code: String): String? {
         val prefix = when (system.trim()) {
             "http://snomed.info/sct", "sct" -> "sct"
             "http://loinc.org", "loinc" -> "loinc"
             "http://terminology.hl7.org/CodeSystem/v3-RoleCode", "v3" -> "v3"
             else -> return null
         }
         val cleanCode = code.trim().replace(Regex("[^A-Za-z0-9]"), "_")
         return "code_label_${prefix}_$cleanCode"
     }
     ```
2. **`qr/JemmaTextPayloadBuilder.kt`** :
   - Ajout du paramètre `labels: CodeLabelResolver = CodeLabelResolver.NONE` à `fun build(hydrated: HydratedProfile, lang: Lang, maxBytes: Int = MAX_BYTES, labels: CodeLabelResolver = CodeLabelResolver.NONE): String`.
   - Paramètre ignoré pour l'instant (comportement d'assemblage de chaîne strictement inchangé).
3. **Périmètre strict respecté** :
   - 0 fichier `strings.xml` touché.
   - 0 catalogue Kotlin modifié.
   - 0 appelant modifié.

## 2. Résultat CI brut (GitHub Actions run #64 sur commit `bd1d9ad`)

Résultat enregistré dans `origin/ci-logs:ag-0060-ui-labels/history/run-64-bd1d9ad.md` :

- **Résultat global** : échec attendu (les 5 rouges de référence)
- **Tests unitaires** : **438 exécutés · 5 en échec · 0 erreur · 0 ignoré**
- **APK debug** : ✅ compilé (`app-debug.apk`, 228 MB)
- **Erreurs de compilation Kotlin** : **0**

### Détail des 5 tests en échec (exactement les 5 tests rouges de `tests/kb-only` @ `d254230`) :

1. `NoClinicalCodeInSourceTest.UC-KB-001 no ATC code or ATC class is written in the source`
2. `NoClinicalCodeInSourceTest.UC-KB-002 no SNOMED CT concept is written in the source`
3. `NoClinicalCodeInSourceTest.UC-KB-003 no LOINC code is written in the source outside the document structure`
4. `UiLabelsInResourcesTest.UC-KB-011 each route has its interface label in the default, French and Japanese resources`
5. `UiLabelsInResourcesTest.UC-KB-014 no catalogue of the source carries a French or Japanese label`

Les 433 autres tests unitaires sont au vert (100% passants).

Le squelette est prêt pour l'ajout sur `tests/kb-only` de `testsupport/ResXmlCodeLabels` et de tes tests de comportement.
