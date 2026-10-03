# Analyse technique : Libellés d'interface des codes médicaux (PROTOCOL §9.1)

> **Tâche 0060** · Branche `ag/0060-ui-labels` (sur `tests/kb-only` @ `d254230`)  
> **Décision produit** : Les libellés courts des codes curés par l'application sont des **textes d'interface** (`strings.xml`, ressources `code_label_<system>_<code>`).  
> **Auteur** : Antigravity · **Date** : 2026-10-03  

---

## 1. Inventaire exhaustif des 14 fichiers de `pillars/` (Règle UC-KB-014)

Le test `UC-KB-014` interdit la présence de libellés traduits (champs `displayFr`, `displayJa`, etc., ou chaînes contenant des caractères non-ASCII / CJK) en dur dans le code Kotlin sous `pillars/`.

Voici l'inventaire exact des 14 fichiers impactés, le nombre de lignes touchées (284 au total), le nombre de concepts/codes et les fonctions actuelles :

| # | Fichier | Lignes touchées | Codes / Concepts | Langues en dur | Fonctions d'accès actuelles |
|---|---|:---:|:---:|:---:|---|
| 1 | `IpsRouteCatalog.kt` | **7** | 5 voies d'administration | EN, FR, JA | `getDisplay(shortCode, lang)`, `byShortCode(shortCode)` |
| 2 | `IpsAllergyTypeCatalog.kt` | **4** | 2 types (Allergy, Intolerance) | EN, FR, JA | `getDisplay(code, lang)`, `byCode(code)` |
| 3 | `IpsContactPointCatalog.kt` | **16** | 15 codes (Address use: 5, Telecom system: 5, Telecom use: 5) | EN, FR, JA | `IpsAddressUseCatalog.getDisplay(code, lang)`, `IpsTelecomSystemCatalog.getDisplay`, `IpsTelecomUseCatalog.getDisplay` |
| 4 | `IpsDeviceCatalog.kt` | **13** | 6 dispositifs médicaux + 3 statuts | EN, FR, JA | `IpsDeviceCatalog.getDisplay(code, lang)`, `IpsDeviceStatusCatalog.getDisplay` |
| 5 | `IpsEnumCatalogs.kt` | **19** | 3 criticalités + 4 statuts cliniques + 4 catégories d'allergie | EN, FR, JA | `IpsCriticalityCatalog.getDisplay(code, lang)`, `IpsClinicalStatusCatalog.getDisplay`, `IpsAllergyCategoryCatalog.getDisplay` |
| 6 | `IpsIdentifierSystemCatalog.kt` | **14** | 5 systèmes d'identification (BE, FR, JP, US, Passeport) | EN, FR, JA | `IpsIdentifierSystemCatalog.getDisplay(short, lang)` |
| 7 | `IpsLanguageCatalog.kt` | **17** | 14 langues d'usage | EN, FR, JA | `IpsLanguageCatalog.getDisplay(tag, lang)` |
| 8 | `IpsMedicationStatusCatalog.kt` | **10** | 4 statuts de traitement | EN, FR, JA | `IpsMedicationStatusCatalog.getDisplay(code, lang)` |
| 9 | `IpsPregnancyCatalog.kt` | **29** | 13 concepts LOINC + 3 réponses LOINC de statut | EN, FR, JA, DE, NL, ZH (6 langues) | `label(code, lang)`, `answer(code, lang)`, `format(obs, lang)` |
| 10 | `IpsProcedureCatalog.kt` | **22** | 12 procédures chirurgicales + 3 statuts | EN, FR, JA | `IpsProcedureCatalog.getDisplay(code, lang)`, `IpsProcedureStatusCatalog.getDisplay` |
| 11 | `IpsReactionSeverityCatalog.kt` | **5** | 3 sévérités de réaction | EN, FR, JA | `IpsReactionSeverityCatalog.getDisplay(code, lang)` |
| 12 | `IpsRelationshipCatalog.kt` | **41** | 39 rôles HL7 v3-RoleCode (personal-relationship) | EN, FR, JA | `getDisplay(code, lang)`, `sortedByDisplay(lang)`, `compactLabel(lang)` |
| 13 | `IpsResultCatalog.kt` | **49** | 31 analyses labo + 7 statuts + 8 interprétations + 3 catégories | EN, FR, JA | `IpsResultCatalog.getDisplay(code, lang)`, `IpsResultStatusCatalog.getDisplay`, `IpsResultInterpretationCatalog.getDisplay`, `IpsResultCategoryCatalog.getDisplay` |
| 14 | `IpsVaccineCatalog.kt` | **38** | 31 vaccins + 3 statuts de vaccination | EN, FR, JA | `IpsVaccineCatalog.getDisplay(code, lang)`, `IpsImmunizationStatusCatalog.getDisplay` |
| **Total** | **14 fichiers** | **284** | **149 concepts médicaux** | — | — |

---

## 2. Cartographie des consommateurs : Interface vs Hors-Interface (Headless)

### 2.1 Écrans UI (avec accès direct à Android `Context` / `Resources`)
Ces composants s'exécutent dans le cycle de vie Android et possèdent déjà une référence `context: Context` (ou `requireContext()`) :
- **Traitements & Voies** : `MedicationFormBottomSheet`, `MedicationsAdapter`
- **Contacts & Relations** : `ContactFormBottomSheet`, `ContactsAdapter`
- **Dispositifs** : `DeviceFormBottomSheet`, `DevicesAdapter`, `DevicesEditFragment`
- **Vaccinations** : `ImmunizationFormBottomSheet`, `ImmunizationsAdapter`, `ImmunizationsEditFragment`
- **Résultats d'examens** : `ResultFormBottomSheet`, `ResultsAdapter`, `ResultsEditFragment`
- **Procédures** : `ProcedureFormBottomSheet`, `ProceduresAdapter`, `ProceduresEditFragment`
- **Profil & Identifiants** : `PatientEditFragment`
- **Détail du profil** : `ProfileDetailFragment`

Pour ces écrans, la résolution d'une ressource d'interface dans la langue active de l'appareil est triviale via `context.getString(resId)`.

### 2.2 Consommateurs Hors-Interface (Headless sans `Context` direct)
C'est ici que réside la difficulté technique :

1. **`JemmaTextPayloadBuilder` (QR texte d'urgence)**
   - **Nature** : Singleton Kotlin pur (`object`), sans import Android UI, exécutable à la fois sur Android et sur JVM classique.
   - **Rôle** : Construit le payload textuel compact d'urgence en 25 langues (`Lang.EN`, `Lang.FR`, `Lang.JA`, etc.).
   - **Appels directs actuels** :
     - `IpsPregnancyCatalog.format(pg, lang.isoCode)`
     - `IpsRelationshipCatalog.getDisplay(c.r, lang.isoCode)`
     - `IpsVaccineCatalog.getDisplay(im.c, langCode)`
     - `IpsProcedureCatalog.getDisplay(pr.c, langCode)`
     - `IpsDeviceCatalog.getDisplay(dv.c, langCode)`
     - `IpsResultCatalog.getDisplay(rs.c, lang.isoCode)`
   - **Consommateurs de `JemmaTextPayloadBuilder`** :
     - `QrViewerFragment` (Android UI, possède un `Context`)
     - `JemmaPdfExporter` (Android background/export, reçoit `context: Context`)
     - `JemmaEmergencyWidget` (Android AppWidget, reçoit `context: Context`)
     - **Tests unitaires JVM purs (7 suites de tests, sans Robolectric)** : `PastProblemsTextQrTest`, `PregnancyTextQrTest`, `QrTextBudgetTest`, `RandomProfileInvariantsTest`, `TextQrAllLanguagesTest`, `Sd11HaruFunctionalStatusInTextQrTest`, `Sd21FreeTextMedicationTest`.

2. **`JemmaTools` (Agent d'IA Google Gemma / LiteRT)**
   - **Nature** : Classe `@Singleton` injectée par Hilt/Dagger, implémentant `ToolSet` pour l'inférence locale LLM.
   - **Rôle** : Fournit au modèle les listes structurées d'analyses, dispositifs, vaccins et procédures pour le raisonnement clinique.
   - **Appels directs actuels** :
     - `IpsVaccineCatalog.getDisplay(im.c, lang)`
     - `IpsProcedureCatalog.getDisplay(pr.c, lang)`
     - `IpsDeviceCatalog.getDisplay(dv.c, lang)`
     - `IpsResultCatalog.getDisplay(rs.c, lang)`
     - Note : `lang` est déterminé par `Locale.getDefault().language.lowercase().take(2)`.
   - **Injection actuelle** : `@Inject constructor(private val kb: KnowledgeBaseService, private val xcheck: KbCrossCheck)`. N'injecte pas encore `Context`.

---

## 3. La problématique multilingue et multi-environnement

### Le piège du téléphone en français produisant un QR en japonais
Sur Android, l'appel standard `context.getString(R.string.code_label_...)` interroge la configuration active de l'appareil (`Locale.getDefault()`).
Si un utilisateur francophone en voyage au Japon génère le QR texte en japonais (`Lang.JA`), un simple `context.getString(...)` produira **le libellé français**, ce qui viole l'exigence du QR traduit !

Pour obtenir la chaîne japonaise depuis un `Context` Android, il faut impérativement charger les ressources configurées pour la locale cible :
```kotlin
fun getLocalizedResources(context: Context, lang: String): Resources {
    val config = Configuration(context.resources.configuration)
    config.setLocale(Locale.forLanguageTag(lang))
    return context.createConfigurationContext(config).resources
}
```

### Le piège des tests unitaires JVM purs
Les tests unitaires dans `app/src/test/` (comme `TextQrAllLanguagesTest`, `QrTextBudgetTest`) s'exécutent sur la JVM locale sans framework Android ni Robolectric. Toute dépendance obligatoire et stricte à `android.content.Context` rendrait ces tests immédiatement inopérants.

---

## 4. Architecture proposée et Signature de remplacement

### 4.1 Contrat de nommage des ressources (PROTOCOL §9.1)
Le nom de ressource suit scrupuleusement la règle :
$$\text{code\_label\_}\langle\text{sct}|\text{loinc}|\text{v3}\rangle\text{\_}\langle\text{code\_normalisé}\rangle$$
Tout caractère non alphanumérique du code est remplacé par `_`.
- Voie orale SNOMED CT `26643006` $\rightarrow$ `code_label_sct_26643006`
- Voie respiratoire SNOMED CT `447694001` $\rightarrow$ `code_label_sct_447694001`
- Statut grossesse LOINC `82810-3` $\rightarrow$ `code_label_loinc_82810_3`
- Rôle HL7 v3 `DOMPART` $\rightarrow$ `code_label_v3_DOMPART`

### 4.2 Signature d'interface proposée : `CodeLabelResolver`

Nous proposons une interface fonctionnelle légère :

```kotlin
package be.heyman.android.jemmapassdemo.pillars

fun interface CodeLabelResolver {
    /**
     * Résout le libellé d'interface d'un code médical curé pour une langue cible.
     *
     * @param system Identifiant court du système : "sct", "loinc", "v3"
     * @param code Code médical d'origine (ex: "26643006", "82810-3", "FTH")
     * @param lang Code langue ISO / BCP-47 cible (ex: "fr", "ja", "en")
     * @return Le libellé traduit trouvé dans les ressources, ou null si non traduit / absent.
     */
    fun getLabel(system: String, code: String, lang: String): String?
}
```

### 4.3 Deux stratégies d'intégration au choix

#### Stratégie A : Résolveur Contextuel Singleton avec Provider de secours (Recommandée)
Un objet singleton `IpsCodeLabels` fait le pont entre Android et le mode headless :
- **En environnement Android** : Initialisé lors du `Application.onCreate()` avec le `ApplicationContext`. Il met en cache les instances `Resources` localisées par langue (`ConcurrentHashMap<String, Resources>`).
- **En environnement JVM / Test** : Si le contexte est absent, il interroge un dictionnaire en mémoire statique ou lit directement les XML de ressources (comme le fait déjà `UiLabelsInResourcesTest`).
- **Signature dans les catalogues** :
  ```kotlin
  object IpsRouteCatalog {
      fun getDisplay(shortCode: String?, lang: String, resolver: CodeLabelResolver? = null): String {
          val entry = byShortCode(shortCode) ?: return shortCode.orEmpty()
          return (resolver ?: IpsCodeLabels).getLabel("sct", entry.snomedCode, lang)
              ?: entry.displayEn // fallback KB marqué non traduit
      }
  }
  ```
  *Avantage majeur* : **Aucune rupture d'API** pour les 40+ points d'appels existants (`getDisplay(code, lang)` continue de fonctionner tel quel en appelant le résolveur implicite, tout en permettant l'injection explicite).

#### Stratégie B : Injection explicite systématique
Modifier la signature de `JemmaTextPayloadBuilder.build` pour accepter un `CodeLabelResolver` :
```kotlin
fun build(
    hydrated: HydratedProfile,
    lang: Lang,
    maxBytes: Int = MAX_BYTES,
    resolver: CodeLabelResolver = AndroidCodeLabelResolver.INSTANCE
): String
```
Et injecter `@ApplicationContext context: Context` dans le constructeur de `JemmaTools` :
```kotlin
@Singleton
class JemmaTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kb: KnowledgeBaseService,
    private val xcheck: KbCrossCheck,
)
```
*Avantage* : Purisme architectural, zéro singleton implicite.  
*Inconvénient* : Nécessite d'adapter les 7 classes de tests unitaires JVM pour leur injecter un mock ou un résolveur de test.

---

## 5. Cas d'application immédiat : Famille 1 (Les 5 voies d'administration)

Pour passer les tests rouges `UC-KB-011` à `UC-KB-013` :
1. **Ressources `res/values/strings.xml`** (Défaut / Anglais) :
   - `code_label_sct_26643006` = `Oral`
   - `code_label_sct_47625008` = `Injection`
   - `code_label_sct_6064005` = `Topical`
   - `code_label_sct_34206005` = `Subcutaneous`
   - `code_label_sct_447694001` = `Inhaled`
2. **Ressources `res/values-fr/strings.xml`** (Français) :
   - `code_label_sct_26643006` = `Orale`
   - `code_label_sct_47625008` = `Injection`
   - `code_label_sct_6064005` = `Topique`
   - `code_label_sct_34206005` = `Sous-cutané`
   - `code_label_sct_447694001` = `Inhalée`
3. **Ressources `res/values-ja/strings.xml`** (Japonais) :
   - `code_label_sct_26643006` = `経口`
   - `code_label_sct_47625008` = `注射`
   - `code_label_sct_6064005` = `外用`
   - `code_label_sct_34206005` = `皮下`
   - `code_label_sct_447694001` = `吸入`

Les textes sont conservés au caractère près (aucun texte réinventé).  
Dans `IpsRouteCatalog.kt`, les champs `displayFr` et `displayJa` sont retirés du `RouteEntry`, satisfaisant `UC-KB-014` pour ce fichier.

---

## 6. Prochaine étape
Nous attendons la validation de cette analyse et le choix entre la **Stratégie A** (recommandée, transparente) et la **Stratégie B** par Claude / Kudoro avant de commit l'implémentation des voies d'administration.
