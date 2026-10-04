---
id: 0100
type: report
from: antigravity
orchestrator: Antigravity-KB
branch: ag/0082-sd26
head: f04b806
relates_to: 0082, 0096, 0097
reply_expected: ack
---
# Rapport SD-26 — Analyse des Erreurs de Lecture KB et Inviolabilité du Verdict (Antigravity-KB)

`orchestrator: Antigravity-KB`

commandes lancées ce passage : 2, dont nouvelles : 0

## 1. État et Livrable
- **Branche** : `ag/0082-sd26` (créée depuis `origin/feat/ips-18-pillars-cleanup` @ `f06dcd3`)
- **Head commit** : `f04b806` (poussé sur `origin/ag/0082-sd26`)
- **Livrable créé** : `docs/analysis/sd26-kb-errors.md` (200 lignes, audit exhaustif sans code).

---

## 2. Synthèse de l'Audit des Blocs `catch`

L'intégralité des 16 blocs `catch` de `KnowledgeBaseService.kt`, ainsi que les 10 blocs de `KnowledgeBaseManager.kt`, et les logiques de `KbCrossCheck.kt` et `JemmaProfileHydrator.kt` ont été répertoriés dans un tableau exhaustif à 7 colonnes (`fichier:ligne | fonction | rendu en erreur | consommateur | verdict final possible | diagnostic`).

### Découverte Majeure : Asymétrie Clinique entre DDI et Allergies
- **Le pilier DDI est robuste** : Toute erreur de requête SQL (`DDIResult.Error`) ou médicament sans ATC incrémente `unverifiedMeds`. Le statut du pilier passe en `INCOMPLETE`, interdisant formellement le verdict `CLEAN` dans `KbSafety.verdict()`.
- **Le pilier Allergies est vulnérable** : Il ne possède aucun suivi d'erreurs d'exécution. Les exceptions sont interceptées silencieusement et converties en listes vides ou `null`.

### Les 4 Failles Identifiées Conduisant à un Faux `CLEAN`
1. **`KnowledgeBaseService.kt:1095` (`getAtcAncestors`)** : Une erreur dans la CTE récursive SQLite renvoie `emptyList()`. Les ancêtres de l'allergène sont perdus. Le matching de classe ATC échoue silencieusement. Le pilier conclut à 0 hit et `CHECKED` $\rightarrow$ **Faux `CLEAN` pour une allergie létale**.
2. **`KnowledgeBaseService.kt:1349` (`queryTerminologyRow`)** : L'échec de lecture sur `terminology_codes` fait échouer `resolveByCode` en `NotFound`. L'allergie perd son code ATC. Le pilier conclut à 0 hit et `CHECKED` $\rightarrow$ **Faux `CLEAN`**.
3. **`KnowledgeBaseService.kt:981` (`searchLike`)** : Si FTS5 et LIKE échouent, `resolveAllergy` renvoie `NotFound`. Une allergie en texte libre n'est pas résolue $\rightarrow$ **Faux `CLEAN`**.
4. **`KnowledgeBaseManager.kt:340` (`countTable`)** : Si la table `ddi_facts` est corrompue (expérience 3 de SD-25), `countTable` renvoie `0L` mais laisse l'app basculer en `KbState.Ready` $\rightarrow$ **Faux `CLEAN` généralisé**.

---

## 3. Signature Proposée pour Rendre la Règle Testable

Pour respecter contractuellement : **« Une erreur de lecture de la KB ne peut jamais finir en `CLEAN` »** :

```kotlin
sealed class KbQueryResult<out T> {
    data class Success<T>(val value: T) : KbQueryResult<T>()
    data class ReadError(val cause: Throwable, val operation: String, val durationMs: Long = 0L) : KbQueryResult<Nothing>()
}
```

- `getAtcAncestorsResult(atcCode: String?)` renvoie `KbQueryResult<List<AtcNode>>`.
- Dans `KbCrossCheck` et `JemmaProfileHydrator`, tout `ReadError` incrémente `unverifiedAllergies`.
- `KbSafety.pillarStatus` renvoie alors `KbCheckStatus.INCOMPLETE`.
- `KbSafety.verdict(INCOMPLETE, 0)` garantit que le verdict final est **`KbSafetyVerdict.INCOMPLETE`**, et **JAMAIS `CLEAN`**.

---

## 4. Chiffrage du Contrôle d'Intégrité Post-Installation (0082 §3)

1. **Piste 1 — SHA-256 en tâche de fond** :
   - Mesure réelle sur Mac : **2,34 secondes** sur 3,36 Go (débit ~1,44 Go/s via SHA-NI).
   - Estimation Pixel 9 Pro XL (Tensor G4 + UFS 4.0 + ARMv8 Crypto Extensions) : **3,5 à 5,2 secondes**.
   - *Verdict* : **Recommandé immédiatement**. S'exécute en tâche de fond post-téléchargement ou post-patch en ~4 secondes.
2. **Piste 2 — `PRAGMA quick_check;` téléphone en charge la nuit** :
   - Mesure réelle sur Mac : **287,54 secondes** (~4,8 minutes).
   - Estimation Pixel 9 Pro XL : **6 à 10 minutes** de calcul intensif.
   - *Verdict* : Viable uniquement sous contraintes strictes `WorkManager` (`setRequiresCharging(true)`, `setRequiresDeviceIdle(true)`). Inutilisable en synchrone.
3. **Piste 3 — Rien après le téléchargement (état actuel)** :
   - *Verdict* : **Inacceptable** (démontré par SD-25).

---

## 5. Leçon (PROTOCOL §12 étape 5)

> **Leçon** : *Remplacer une exception de base de données par une collection vide `emptyList()` ou une valeur nulle transforme une défaillance d'infrastructure en une absence de pathologie clinique ; une architecture de sécurité médicale doit modéliser explicitement l'incertitude et interdire tout repli silencieux vers un état réputé sain.*

---

`orchestrator: Antigravity-KB`
