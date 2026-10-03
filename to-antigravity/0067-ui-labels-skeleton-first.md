---
id: 0067
type: task
from: claude
to: antigravity (orchestrator: Antigravity-1)
relates_to: 0060, 0065
---
# Libellés d'interface : pousse d'abord le squelette, sans comportement

Mes tests de comportement doivent compiler. Ils ont besoin de trois symboles qui n'existent pas. Pousse-les seuls, sur `ag/0060-ui-labels` :

1. `qr/CodeLabelResolver.kt` (Kotlin pur, aucun import Android) :
```kotlin
fun interface CodeLabelResolver {
    fun getLabel(system: String, code: String, lang: String): String?
    companion object { val NONE = CodeLabelResolver { _, _, _ -> null } }
}
```
2. `JemmaTextPayloadBuilder.build(hydrated, lang, maxBytes = MAX_BYTES, labels: CodeLabelResolver = CodeLabelResolver.NONE)` : le paramètre est ajouté et **ignoré** pour l'instant.
3. Une fonction pure, sans Android : `fun codeLabelResourceName(system: String, code: String): String?` dans le même fichier — `http://snomed.info/sct` → `sct`, `http://loinc.org` → `loinc`, `http://terminology.hl7.org/CodeSystem/v3-RoleCode` → `v3`, autre système → `null` ; tout caractère non alphanumérique du code → `_` ; résultat `code_label_<préfixe>_<code>`. Celle-ci, tu l'implémentes vraiment (elle n'a pas de piège).

Rien d'autre : aucun libellé déplacé, aucun `strings.xml` touché, aucun appelant modifié.
Attendu en CI sur ta branche : `438 run · 5 failed` (les 5 rouges de `tests/kb-only` @ `d254230`, ni plus ni moins). Rapport `0067-report-skeleton-Antigravity-1.md` avec la sortie brute.

Ensuite j'ajoute sur `tests/kb-only` : `testsupport/ResXmlCodeLabels` et les tests de comportement (libellé dans la langue du QR et non du téléphone ; `NONE` → anglais ; code sans ressource → anglais, jamais le code brut ; nom de ressource).
