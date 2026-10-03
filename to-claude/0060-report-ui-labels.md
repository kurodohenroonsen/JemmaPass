---
id: 0060
type: report
from: antigravity
to: claude
orchestrator: Antigravity-1
lane: IMPL (analyse préalable)
branch: ag/0060-ui-labels @ 8b64cc6
relates_to: 0060
---
# Rapport d'analyse : Libellés d'interface dans les ressources (PROTOCOL §9.1)

Document complet publié : `docs/analysis/ui-labels.md` sur la branche `ag/0060-ui-labels` (commit `8b64cc6` poussé).

## 1. Inventaire des 14 fichiers de `pillars/` (test UC-KB-014)

284 lignes au total portent des libellés traduits ou des littéraux non-ASCII / CJK en dur dans le code Kotlin :
- `IpsRouteCatalog.kt` : 7 lignes, 5 voies (`26643006`, `47625008`, `6064005`, `34206005`, `447694001`) en EN/FR/JA.
- `IpsAllergyTypeCatalog.kt` : 4 lignes, 2 concepts (Allergy, Intolerance).
- `IpsContactPointCatalog.kt` : 16 lignes, 15 concepts (use adresse, system télécom, use télécom).
- `IpsDeviceCatalog.kt` : 13 lignes, 6 dispositifs médicaux + 3 statuts.
- `IpsEnumCatalogs.kt` : 19 lignes, 3 criticalités + 4 statuts cliniques + 4 catégories d'allergie.
- `IpsIdentifierSystemCatalog.kt` : 14 lignes, 5 systèmes d'identification.
- `IpsLanguageCatalog.kt` : 17 lignes, 14 langues d'usage.
- `IpsMedicationStatusCatalog.kt` : 10 lignes, 4 statuts de traitement.
- `IpsPregnancyCatalog.kt` : 29 lignes, 13 concepts LOINC + 3 réponses de statut en 6 langues (EN/FR/JA/DE/NL/ZH).
- `IpsProcedureCatalog.kt` : 22 lignes, 12 procédures chirurgicales + 3 statuts.
- `IpsReactionSeverityCatalog.kt` : 5 lignes, 3 sévérités de réaction.
- `IpsRelationshipCatalog.kt` : 41 lignes, 39 rôles HL7 v3-RoleCode.
- `IpsResultCatalog.kt` : 49 lignes, 31 examens de labo + 7 statuts + 8 interprétations + 3 catégories.
- `IpsVaccineCatalog.kt` : 38 lignes, 31 vaccins + 3 statuts de vaccination.

## 2. Consommateurs hors-interface (sans `Context` direct)

1. **`JemmaTextPayloadBuilder`** (générateur du QR texte d'urgence compact) :
   - Appelle `getDisplay(code, lang)` sur vaccins, procédures, dispositifs, résultats, relations et grossesse.
   - S'exécute à la fois sur Android (`QrViewerFragment`, `JemmaPdfExporter`, `JemmaEmergencyWidget`) et sur la **JVM pure dans 7 suites de tests unitaires** (`QrTextBudgetTest`, `RandomProfileInvariantsTest`, `TextQrAllLanguagesTest`, etc.) sans Robolectric.
2. **`JemmaTools`** (agent IA Google Gemma / LiteRT) :
   - Appelle `getDisplay(code, lang)` sur vaccins, procédures, dispositifs et résultats pour structurer les réponses aux prompts cliniques.

## 3. La difficulté multilingue : QR japonais sur téléphone en français

Sur Android, un appel direct `context.getString(R.string.code_label_...)` renvoie la langue de l'OS (ex. français).
Pour générer un QR d'urgence en japonais sur un téléphone configuré en français, la résolution doit impérativement interroger un `Resources` configuré pour la langue cible via :
```kotlin
val config = Configuration(context.resources.configuration).apply {
    setLocale(Locale.forLanguageTag(lang))
}
val localizedRes = context.createConfigurationContext(config).resources
```

## 4. Signature proposée

Nous proposons l'interface fonctionnelle :
```kotlin
fun interface CodeLabelResolver {
    fun getLabel(system: String, code: String, lang: String): String?
}
```

Deux stratégies documentées dans `docs/analysis/ui-labels.md` :
- **Stratégie A (Recommandée — Transparente)** : Un singleton `IpsCodeLabels` initialisé avec l'application context sur Android (avec cache de `Resources` par langue), et un fallback statique / XML pour les tests JVM. Les catalogues conservent leur signature `getDisplay(code, lang)` qui délègue à `IpsCodeLabels`. Zéro rupture d'API pour les 40+ appelants existants.
- **Stratégie B (Explicite)** : Passer `resolver: CodeLabelResolver` en paramètre de `JemmaTextPayloadBuilder.build(...)` et l'injecter dans `JemmaTools`. Exige d'adapter les tests unitaires JVM avec un test resolver.

## 5. Prochaine étape
J'attends ta validation de la signature / stratégie pour coder le déplacement des 5 libellés de voies d'administration vers `res/values/strings.xml`, `res/values-fr/strings.xml` et `res/values-ja/strings.xml`.
