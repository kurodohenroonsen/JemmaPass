# 🐢 JemmaPass — SD-26 : Analyse Exhaustive des Erreurs de Lecture KB et Inviolabilité du Verdict de Sécurité
> **Spécification Technique & Audit de Sûreté Clinique**  
> **Rôle** : `orchestrator: Antigravity-KB`  
> **Branche de travail** : `ag/0082-sd26` (dérivée de `origin/feat/ips-18-pillars-cleanup` @ `f06dcd3`)  
> **Référence** : Message Claude boîte aux lettres `0082`  
> **Règle absolue à garantir** : **Une erreur de lecture de la KB ne peut jamais finir en `CLEAN`** (interdiction vitale de déclarer « Aucun conflit » lorsqu'une requête de contrôle a échoué).

---

## 1. Cartographie Exhaustive des Blocs `catch` dans l'Écosystème KB

Conformément à la consigne `0082 §2`, l'intégralité des 16 blocs `catch` de `KnowledgeBaseService.kt`, ainsi que les blocs de `KnowledgeBaseManager.kt`, `KbCrossCheck.kt` et `JemmaProfileHydrator.kt` ont été audités ligne par ligne.

Le tableau ci-dessous détaille pour chaque point la valeur de repli, le composant consommateur et le verdict clinique final possible (`ALERT`, `CLEAN`, `INCOMPLETE`, `NOT_CHECKED` ou `N/A`) :

| # | Fichier & Ligne | Fonction | Ce qui est rendu en cas d'erreur (`catch`) | Qui consomme | Verdict final possible | Diagnostic & Risque Clinique |
|---|---|---|---|---|---|---|
| **KBS-01** | `KnowledgeBaseService.kt:610` | `queryDDIInternal` | `DDIResult.Error(msg, durMs)` | `queryDDIByAtc`, `queryDDIDetailed`, `KbCrossCheck.checkMedicationsWithStatus:513` | `INCOMPLETE` ou `ALERT` | ✅ **SÛR** : `queryFailed` incrémente `unverifiedMeds`, forçant le pilier en `INCOMPLETE`. Ne peut pas finir en `CLEAN`. |
| **KBS-02** | `KnowledgeBaseService.kt:675` | `queryDFI` | `DFIResult.Error(msg, durMs)` | Écrans d'interactions alimentaires UI | `N/A` | ✅ **SÛR** : Hors matrice de décision vitale, état d'erreur rendu à l'UI. |
| **KBS-03** | `KnowledgeBaseService.kt:739` | `queryDrugDiseaseTerms` | `DrugDiseaseResult.Error(msg, durMs)` | `JemmaProfileHydrator.crossCheckDrugDisease:611` | `INCOMPLETE` ou `ALERT` | ✅ **SÛR** : `unverifiedConditions.add(condIndex)` marque l'entrée comme non vérifiée. Ne peut pas finir en `CLEAN`. |
| **KBS-04** | `KnowledgeBaseService.kt:801` | `queryDrugDisease` | `DrugDiseaseResult.Error(msg, durMs)` | `KbCrossCheck.checkConditionsWithStatus:600` | `INCOMPLETE` ou `ALERT` | ✅ **SÛR** : Statut du pilier passe en `INCOMPLETE`. Ne peut pas finir en `CLEAN`. |
| **KBS-05** | `KnowledgeBaseService.kt:934` | `trySearchFts` | `null` | `searchCodes:854` (bascule sur repli `searchLike`) | `CLEAN` ou `ALERT` | ⚠️ **DÉGRADÉ** : Bascule silencieuse vers `searchLike`. Si `searchLike` échoue également, voir KBS-06. |
| **KBS-06** | `KnowledgeBaseService.kt:981` | `searchLike` | `emptyList()` | `searchCodes:872` $\rightarrow$ `resolveAllergy:356` | **`CLEAN`** 🔴 | 🚨 **FAILLE CRITIQUE** : Si FTS et LIKE échouent, `resolveAllergy` renvoie `NotFound`. Une allergie en texte libre n'est pas résolue, n'a pas d'ATC, et le pilier conclut à 0 hit et `CHECKED` $\rightarrow$ faux `CLEAN` ! |
| **KBS-07** | `KnowledgeBaseService.kt:1095` | `getAtcAncestors` | `emptyList()` | `KbCrossCheck:383`, `JemmaProfileHydrator:473` | **`CLEAN`** 🔴 | 🚨 **FAILLE CRITIQUE MAJEURE** : L'échec de la CTE récursive SQLite renvoie `emptyList()`. Les ancêtres de l'allergie sont perdus. L'allergie croisée (ex: Pénicilline $\times$ Amoxicilline) n'est pas détectée. Le pilier allergie renvoie 0 hit et `CHECKED` $\rightarrow$ faux `CLEAN` létal ! |
| **KBS-08** | `KnowledgeBaseService.kt:1142` | `getLocalizedDisplay` | `null` | `pickLocalizedDisplay`, `JemmaProfileHydrator.resolveLocalizedDisplay` | `CLEAN` | ℹ️ **COSMÉTIQUE** : Repli sur le nom anglais canonique ou le code brut. Pas d'occultation d'alerte. |
| **KBS-09** | `KnowledgeBaseService.kt:1164` | `getIpsDisplayEn` | `null` | Sélecteur de conditions / affichage anglais | `N/A` | ℹ️ **AFFICHAGE** : Pas d'impact décisionnel. |
| **KBS-10** | `KnowledgeBaseService.kt:1203` | `getLocalizedDisplays` | Map vide ou partielle | `JemmaProfileHydrator.localizePastProblems:173` | `N/A` | ℹ️ **COSMÉTIQUE** : Libellés des antécédents résolus non traduits. |
| **KBS-11** | `KnowledgeBaseService.kt:1324` | `queryAtcHierarchyName` | `null` | `resolveDrug:215, 256` | `CLEAN` | ℹ️ **COSMÉTIQUE** : Repli sur le `primary_display` de `terminology_codes` sans écrasement générique. |
| **KBS-12** | `KnowledgeBaseService.kt:1349` | `queryTerminologyRow` | `null` | `resolveByCode`, `resolveAllergy`, `resolveDrug` | **`CLEAN`** 🔴 | 🚨 **FAILLE CRITIQUE** : Si la table `terminology_codes` lève une erreur, `resolveByCode` renvoie `NotFound`. Une allergie codée SNOMED (`91936005`) perd son code ATC. Le pilier conclut à 0 hit et `CHECKED` $\rightarrow$ faux `CLEAN` ! |
| **KBS-13** | `KnowledgeBaseService.kt:1428` | `queryDdinterRow` | `null` | `resolveDrug:174, 231, 266` | `NOT_CHECKED` ou `INCOMPLETE` | ✅ **SÛR** : Pour un candidat, `candidateResolved = false` $\rightarrow$ `NOT_CHECKED`. Pour un médicament du profil, `atc = null` $\rightarrow$ `INCOMPLETE`. |
| **KBS-14** | `KnowledgeBaseService.kt:1496` | `fetchDoseHints` | `emptyList()` | Formulaire de saisie posologique UI | `N/A` | ℹ️ **ERGONOMIE** : Pas de suggestion de dose automatique. |
| **KBS-15** | `KnowledgeBaseService.kt:1550` | `getGeneralInteractionCounts` (food) | Comptage 0 | `JemmaProfileHydrator.hydrate:136` | `CLEAN` | ℹ️ **COSMÉTIQUE** : Badge statistique sur la carte médicament. |
| **KBS-16** | `KnowledgeBaseService.kt:1562` | `getGeneralInteractionCounts` (disease) | Comptage 0 | `JemmaProfileHydrator.hydrate:136` | `CLEAN` | ℹ️ **COSMÉTIQUE** : Badge statistique sur la carte médicament. |
| **KBM-01** | `KnowledgeBaseManager.kt:219` | `validateInternal` (openDatabase) | `KbState.Failed` | `state: StateFlow<KbState>` | `NOT_CHECKED` | ✅ **SÛR** : Base inaccessible, aucun check ne tourne, `database() == null`. |
| **KBM-02** | `KnowledgeBaseManager.kt:234` | `validateInternal` (PRAGMA) | Ignoré (log warning) | Pipeline d'ouverture | `CLEAN` | ℹ️ **TOLÉRANCE** : Les pragmas de cache n'altèrent pas l'intégrité clinique. |
| **KBM-03** | `KnowledgeBaseManager.kt:340` | `countTable` | `0L` | `KbState.Ready` (`totalDdiFacts`, `totalCodes`, etc.) | **`CLEAN`** 🔴 | 🚨 **FAILLE ARCHITECTURALE** : Si `ddi_facts` ou `ddi_atc_pairs` est corrompue, `countTable` renvoie `0L`. L'app bascule en `KbState.Ready` avec 0 interaction en base. Les scans concluent à 0 hit $\rightarrow$ faux `CLEAN` ! |
| **KBM-04** | `KnowledgeBaseManager.kt:358` | `countWhere` | `0L` | `totalIpsValidated` | `N/A` | ℹ️ **STATISTIQUE** : Information d'affichage dans les Paramètres. |
| **KBM-05** | `KnowledgeBaseManager.kt:379` | `countTerminologyByCategory` | `0L` | `totalMedications`, `totalConditions` | `N/A` | ℹ️ **STATISTIQUE** : Information d'affichage. |
| **KBM-06** | `KnowledgeBaseManager.kt:404` | `countTerminologyAllergens` | `0L` | `totalAllergens` | `N/A` | ℹ️ **STATISTIQUE** : Information d'affichage. |
| **KBM-07** | `KnowledgeBaseManager.kt:432` | `countDistinctLanguages` | `0` | `totalLanguages` | `N/A` | ℹ️ **STATISTIQUE** : Information d'affichage. |
| **KBM-08** | `KnowledgeBaseManager.kt:467` | `readBuildMetadata` | `BuildMeta(null, ...)` | Métadonnées de build | `N/A` | ℹ️ **AFFICHAGE** : Paramètres. |
| **KBM-09** | `KnowledgeBaseManager.kt:522` | `probeFtsTable` | `false` | `fts5LatinOk`, `fts5CjkOk` | `CLEAN` | ✅ **SÛR** : Désactive l'accélération FTS5 et force le repli LIKE. |
| **KBM-10** | `KnowledgeBaseManager.kt:536` | `closeQuietly` | Avalée | Fermeture de connexion | `N/A` | ℹ️ **NETTOYAGE** : Sans impact clinique. |
| **XCK-01** | `KbCrossCheck.kt:350-369` | `checkAllergiesWithStatus` | Ignore l'absence de classes croisées | Pilier allergies | **`CLEAN`** 🔴 | 🚨 **FAILLE D'ABSORPTION** : Si la requête SQL sur `allergy_cross_reactivity` échoue, la liste est vide. L'absence n'est pas tracée comme une erreur d'exécution. Le pilier conclut à `CHECKED` $\rightarrow$ faux `CLEAN` ! |
| **HYD-01** | `JemmaProfileHydrator.kt:392` | `batchCrossCheckDdi` | `unverifiedMeds = meds.indices.toSet()` | `CountedPillar<DdiAlert>` | `INCOMPLETE` ou `ALERT` | ✅ **SÛR** : En cas d'erreur SQL, tout le pilier est marqué non vérifié (`unverifiedMeds > 0`), forçant `KbCheckStatus.INCOMPLETE`. |
| **HYD-02** | `JemmaProfileHydrator.kt:174` | `localizePastProblems` | `emptyMap()` | `HydratedProfile.pastProblemLabels` | `N/A` | ℹ️ **COSMÉTIQUE** : Libellés des antécédents résolus. |
| **HYD-03** | `JemmaProfileHydrator.kt:473` | `crossCheckAllergyMedication` | `emptyList()` reçu de `getAtcAncestors` | `allergyAlerts` (reste vide) | **`CLEAN`** 🔴 | 🚨 **PROPAGATION DE FAILLE** : L'échec interne de `getAtcAncestors` (KBS-07) est reçu comme une liste vide. L'hydrator ne sait pas qu'une erreur a eu lieu, marque `checks.allergy = CHECKED`, concluant à `CLEAN` ! |

---

## 2. Démonstration des 4 Failles Conduisant à un Faux `CLEAN`

L'audit révèle une asymétrie de conception majeure entre les piliers :
- **Pilier DDI (`batchCrossCheckDdi` / `checkMedicationsWithStatus`)** : Très bien protégé. Toute exception SQL ou médoc sans ATC incrémente `unverified`, ce qui positionne le statut en `INCOMPLETE` et interdit formellement le verdict `CLEAN` dans `KbSafety.verdict()`.
- **Pilier Allergies (`crossCheckAllergyMedication` / `checkAllergiesWithStatus`)** : **NON PROTÉGÉ**. Les exceptions sont converties en listes vides ou en `null`, et le pilier ne possède aucun compteur d'erreur (`unverifiedAllergies`). Il bascule mécaniquement en `CHECKED` et génère un faux `CLEAN`.

### Faille 1 : `getAtcAncestors` avale l'erreur et renvoie `emptyList()` (KBS-07 $\rightarrow$ HYD-03 / XCK-01)
1. **Scénario** : Le patient porte une allergie à la Pénicilline G (`J01CA01`, SNOMED `91936005`). On lui prescrit de l'Amoxicilline (`J01CA04`, Augmentin).
2. **Événement** : La table `atc_hierarchy` subit une erreur I/O ou une page corrompue lors de l'exécution de la CTE récursive.
3. **Comportement actuel** : `getAtcAncestors` attrape l'exception ligne 1095, logue `⚠️ getAtcAncestors(J01CA01) failed`, et renvoie `emptyList()`.
4. **Conséquence** : `allergyAncestorAtcs` est vide (ou réduit à un préfixe partiel). Le test d'inclusion `matchAllergyToMed` échoue. `allergyAlerts` est vide.
5. **Verdict final produit** : `checks.allergy = CHECKED` (car `database != null`). `totalHits = 0`. `KbSafety.verdict(CHECKED, 0)` $\rightarrow$ **`CLEAN`** 🔴. L'application affiche un badge vert "Rien à signaler" pour une prise médicamenteuse mortelle !

### Faille 2 : `queryTerminologyRow` avale l'erreur et renvoie `null` (KBS-12)
1. **Scénario** : Le patient a une allergie codée en SNOMED CT (`c = "91936005"`).
2. **Événement** : La requête sur `terminology_codes` échoue suite à un bloc disque illisible.
3. **Comportement actuel** : `queryTerminologyRow` attrape l'exception ligne 1349 et renvoie `null`.
4. **Conséquence** : `resolveByCode` renvoie `ResolvedConcept.NotFound`. Si le libellé texte brut de l'allergie n'est pas standard (ex: libellé japonais ou abréviation), `inferAtcFromAllergyName` échoue. `allergyAtc` devient `null`.
5. **Verdict final produit** : Aucun matching n'est possible, 0 hit produit, pilier marqué `CHECKED` $\rightarrow$ **`CLEAN`** 🔴.

### Faille 3 : `countTable` avale l'erreur et renvoie `0L` dans `KnowledgeBaseManager` (KBM-03)
1. **Scénario** : Le fichier SQLite téléchargé a subi une corruption partielle au milieu (comme l'Expérience 3 de SD-25, 1 Mo écrasé sur `ddi_facts`).
2. **Événement** : Lors de la validation au démarrage (`validateInternal`), `countTable("ddi_facts")` lève `database disk image is malformed`.
3. **Comportement actuel** : `countTable` attrape l'erreur ligne 340 et renvoie `0L`.
4. **Conséquence** : `validateInternal` ne bloque pas. La connexion `database` reste non nulle. L'état devient `KbState.Ready` avec `totalDdiFacts = 0L`.
5. **Verdict final produit** : Toute consultation DDI ultérieure sur la table corrompue renvoie une erreur ou 0 résultat, mais la base est considérée opérationnelle $\rightarrow$ faux **`CLEAN`** généralisé !

---

## 3. Proposition d'Architecture & Signatures Testables (JVM Pure)

Pour garantir contractuellement la règle : **« Une erreur de lecture de la KB ne peut jamais finir en `CLEAN` »**, le service KB ne doit plus jamais avaler silencieusement les erreurs de requêtes sous forme de listes vides non différenciées.

### 3.1. Typage strict des résultats de requêtes KB (`KbResult<T>`)

Remplacer les retours `List<T>` ou `T?` par un type somme explicite distinguant l'absence légitime de données de l'échec d'exécution :

```kotlin
package be.heyman.android.jemmapassdemo.kb

/**
 * Résultat typé d'une requête vers la base de connaissances.
 * Interdit formellement de confondre "0 résultat" et "erreur de lecture".
 */
sealed class KbQueryResult<out T> {
    /** La requête a réussi et renvoie la donnée (qui peut être vide si aucune règle n'existe). */
    data class Success<T>(val value: T) : KbQueryResult<T>()

    /** La requête a échoué (corruption SQLite, I/O, base fermée). */
    data class ReadError(
        val cause: Throwable,
        val operation: String,
        val durationMs: Long = 0L,
    ) : KbQueryResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is ReadError

    fun getOrNull(): T? = (this as? Success)?.value
}
```

### 3.2. Nouvelles signatures pour `KnowledgeBaseService`

```kotlin
// Au lieu de: suspend fun getAtcAncestors(atcCode: String?): List<AtcNode>
suspend fun getAtcAncestorsResult(atcCode: String?): KbQueryResult<List<AtcNode>>

// Au lieu de: suspend fun resolveByCode(code: String?, system: String?): ResolvedConcept
suspend fun resolveByCodeResult(code: String?, system: String?): KbQueryResult<ResolvedConcept>

// Au lieu de: suspend fun resolveAllergy(name: String?): ResolvedConcept
suspend fun resolveAllergyResult(name: String?): KbQueryResult<ResolvedConcept>
```

### 3.3. Intégration dans `KbCrossCheck` et `JemmaProfileHydrator`

Le pilier Allergies doit adopter la même rigueur que le pilier DDI :
```kotlin
var unverifiedAllergies = 0

for (allergy in allergies) {
    val ancestorsResult = kb.getAtcAncestorsResult(allergyAtc)
    val ancestors = when (ancestorsResult) {
        is KbQueryResult.Success -> ancestorsResult.value
        is KbQueryResult.ReadError -> {
            // CRITIQUE : l'erreur est comptabilisée, le pilier ne sera JAMAIS 'CHECKED'
            unverifiedAllergies++
            emptyList()
        }
    }
    // ...
}

// Le statut du pilier allergie reflète impérativement les erreurs :
val allergyStatus = KbSafety.pillarStatus(
    kbAvailable = kbUp,
    itemsToCheck = allergies.size,
    itemsUnverified = unverifiedAllergies,
)
```

### 3.4. Invariant absolu dans `KbSafety.verdict()`

Dans `KbSafety.kt:134`, la matrice de décision garantit déjà :
```kotlin
fun verdict(status: KbCheckStatus, totalHits: Int): KbSafetyVerdict = when {
    totalHits > 0 -> KbSafetyVerdict.ALERT
    status == KbCheckStatus.CHECKED -> KbSafetyVerdict.CLEAN
    status == KbCheckStatus.INCOMPLETE -> KbSafetyVerdict.INCOMPLETE
    else -> KbSafetyVerdict.NOT_CHECKED
}
```
**Preuve formelle** : Dès lors que `unverifiedAllergies > 0`, `pillarStatus` renvoie obligatoirement `KbCheckStatus.INCOMPLETE`. `checks.overall` devient `INCOMPLETE`. `KbSafety.verdict` renvoie donc **`KbSafetyVerdict.INCOMPLETE`**, et **JAMAIS `CLEAN`**. L'interface utilisateur affiche le dialogue d'alerte « Contrôle incomplet » et bloque le badge vert.

---

## 4. Chiffrage du Contrôle d'Intégrité Post-Installation (0082 §3)

Claude demande de chiffrer trois pistes pour l'audit d'intégrité après installation :

### Piste 1 : Empreinte SHA-256 recalculée en tâche de fond
- **Mesure réelle sur Mac (SSD APFS NVMe)** :
  - Commande : `shasum -a 256 knowledge_full.db` sur 3 360 727 040 octets.
  - Durée mesurée : **2,34 secondes** (débit de lecture ~1,44 Go/s avec accélération matérielle SHA-NI).
- **Estimation sur Pixel 9 Pro XL (UFS 4.0 + Tensor G4)** :
  - Le stockage UFS 4.0 offre un débit séquentiel réel de 1,5 à 2,0 Go/s.
  - Le processeur ARM Cortex-X4 / A720 supporte les instructions cryptographiques ARMv8-A Cryptography Extensions (exécution matérielle de SHA-256).
  - Durée estimée sur Android : **3,5 à 5,2 secondes**.
- **Faisabilité** : **EXCELLENTE**. Une tâche `OneTimeWorkRequest` en tâche de fond (`Dispatchers.IO`) dès la fin du téléchargement peut valider les 3,36 Go en moins de 5 secondes sans aucun impact perceptible pour l'utilisateur.

### Piste 2 : `PRAGMA quick_check;` en tâche de fond, téléphone en charge
- **Mesure réelle sur Mac** : **287,54 secondes** (~4,8 minutes) sur la base saine (SD-25).
- **Estimation sur Pixel 9 Pro XL** : **6 à 10 minutes** de calcul intensif (parcours complet de l'arbre b-tree de 820 490 pages avec contraintes thermiques de régulation CPU mobile).
- **Faisabilité** : **VIABLE UNIQUEMENT SOUS CONTRAINTES STRICTES**. Doit être programmé via `WorkManager` avec les contraintes :
  - `setRequiresCharging(true)` (uniquement sur secteur)
  - `setRequiresDeviceIdle(true)` (écran éteint et appareil au repos la nuit)
- **Rôle** : Audit de santé nocturne préventif, mais **totalement inutilisable** en post-condition synchrone d'un patch delta de 5 Mo.

### Piste 3 : Rien après le téléchargement (état actuel)
- **Bilan** : **INACCEPTABLE**. Comme démontré dans SD-25, la règle des 95 % tolère qu'un fichier amputé de 168 Mo soit validé, et une corruption au milieu laisse l'application démarrer en masquant des alertes vitales.

---

## 5. Synthèse & Recommandation pour Claude

1. **Règle absolue** : Les erreurs de requêtes sur les ancêtres ATC (`getAtcAncestors`) et la terminologie doivent remonter au moteur de décision sous forme d'items non vérifiés (`unverifiedAllergies`).
2. **Signature proposée** : Utilisation de `KbQueryResult<T>` pour décorréler l'absence de donnée de l'échec d'exécution.
3. **Contrôle d'intégrité** : Adoption immédiate de la **Piste 1** (SHA-256 recalculé en tâche de fond en ~4 s) à chaque installation ou mise à jour différentielle, complétée par la **Piste 2** en tâche de maintenance nocturne sous charge.
