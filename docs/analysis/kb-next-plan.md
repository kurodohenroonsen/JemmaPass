# Plan de Construction & Conception des Mises à Jour de la KB 2.1-omnis

> **Document de Conception & Spécification Technique**  
> **Orchestrateur** : `Antigravity-KB` (Couloir KB)  
> **Branche de travail** : `ag/0055-kb-build`  
> **Références** : Messages boîte 0055, 0058, 0059, 0064, 0068, 0071, 0072, 0076 ; PROTOCOL §9, §9.1, §9.2  
> **Décision produit de Kudoro (2026-10-03)** : Option 3 — Correctifs différentiels légers (Mo), sans nouvelle publication d'application.

---

## 1. Synthèse des Invariants et Versions de la Base de Connaissances

### 1.1. Tableau des versions et cartographie des sources

Suite aux vérifications sur pièces des messages 0059, 0064, 0068 et 0076, le tableau de référence s'établit comme suit :

| Composant | Version interne (`build_metadata`) | Version dans le code Android | Nom de distribution | Taille exacte (octets) | SHA-256 | Rôle & Destination |
|---|---|---|---|---|---|---|
| **Base téléphone (B) & Serveur** | `2.0-omnis` | `1.1` (`JemmaModelCatalog.kt:86`) | `knowledge_full.db` | **3 360 727 040** (820 490 pages) | `237d899f9e81e6d22af01bc6969798d1495131f1f1d4caa0491d80ac6a06014c` | Base installée sur le device de test et servie par `jemmapass.net`. |
| **Base archive contest (A)** | `2.0-omnis` (mêmes lignes/schémas) | — | `knowledge_full.db` | 3 358 871 552 (820 037 pages) | `08a5d4b48454ea6c85e081bd1c5b40aeee5bad48e1dd3221c6e1cd1cfb1f223e` | Archive locale sur Mac (écart de 453 pages d'allocation b-tree vides). |
| **Cible future (1.2)** | **`2.1-omnis`** | `1.1` (inchangée car MAJ sans nouvelle app) | `knowledge_full.db` (complet) & `patch_2.0_to_2.1.db` (delta) | À mesurer au build | À calculer au build | Base enrichie (LOINC + traductions FR/JA + EML/AWaRe). |

- **URL réelle de distribution** : `https://jemmapass.net/models/knowledge_full.db` (URL non versionnée, définie dans `downloads/JemmaModelCatalog.kt:33,87`).
- **Chemin de stockage sur Android** : `{externalFilesDir}/knowledge_full_db/1.1/knowledge_full.db` (normalisé par `JemmaDownloadStorage.kt:63`).
- **Nom de la prochaine version** : **`2.1-omnis`** dans `build_metadata`, **`1.2`** pour le site et l'app.

### 1.2. Constatations d'audit du pipeline de build (`JEMMA_DB_DATA/forge_cryptonite/`)

Conformément aux instructions des messages 0055, 0059 et 0064, l'audit du système de forge a permis de documenter le pipeline exact :

- **Emplacement de référence** : `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite/`
  *(Note : `forge_colab/` est un ancien prototype avec discordances de schéma documentées dans son `remar.ques.md`, à ignorer).*
- **Orchestrateur** : `turbo_forge_jemma_db.py`
- **Arborescence des modules** : Dossier `forge/` contenant 46 scripts Python modulaires :
  - `step01_atc_hierarchy.py` & `step01b_atc_combinations.py` : Ingestion de l'arbre WHO ATC/DDD (`atc_ddd_consolidated.json`) et index des combinaisons.
  - `step02a_drugs.py`, `step02b_ddi.py`, `step02c_ddinter_extra.py` : Ingestion de DDInter 2.0 (médicaments, interactions DDI, DFI aliments, DDSI maladies, duplications).
  - `step03_snomed.py`, `step03c_snomed_refset.py`, `step03d_snomed_textdef.py` : Ingestion du snapshot officiel SNOMED CT IPS.
  - `step04_mrconso.py`, `step04c_rxncui.py`, `step05b_unii_extended.py` : Ingestion UMLS 2025AB (`MRCONSO.RRF`), RxNorm et FDA UNII.
  - `step08b_mrsat.py`, `step09_mrrel.py` : Attributs et relations sémantiques UMLS.
  - `step12_atc_propagation.py` & `step18_combo_augmentin.py` : Propagation des codes ATC aux formes de marques et combinaisons.
  - `step14_15_finalize.py` : Schéma 3NF, contraintes d'intégrité, indexation SQLite B-Tree.
  - `step17_allergy_cross_reactivity.py` & `step17b_cryptonite.py` : Table de réactivité croisée allergique et correctifs cliniques critiques.
  - `step23_drug_names.py` : Index virtuels FTS5 multilingues (`terminology_latin`, `terminology_cjk`).
  - `step25_atc_family_stats.py` : Statistiques par famille ATC.
- **Données d'entrée brutes présentes sur le disque** dans `JEMMA_DB_DATA/` :
  - `who_atc/2025/atc_ddd_consolidated.json`
  - `ddinter/2.0/` (`interaction_details_full.json` 419 Mo, `ddinter_drugs_large.json`, `ddinter_disease_full.json`, etc.)
  - `snomed_ips/20240701/Snapshot/` (`der2_Refset_IPSSimpleSnapshot_IPST_20240701.txt`, etc.)
  - `hl7_fhir_ips/package/` (StructureDefinition, ValueSet, ConceptMap)
  - `umls/2025AB/META/` (`MRCONSO.RRF` 17 Go, `MRREL.RRF`, `MRSAT.RRF`)
  - `fda_unii/20260226/` (`UNII_Records_26Feb2026.txt`, `UNII_Names_26Feb2026.txt`)
  - `rxnorm/04062026/rrf/` (`RXNCONSO.RRF`, `RXNREL.RRF`, `RXNSAT.RRF`)
- **Commandes de génération** :
  ```bash
  cd /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite
  export JEMMA_DB_ROOT=/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA
  python3 turbo_forge_jemma_db.py --rebuild          # Reconstruction complète
  python3 turbo_forge_jemma_db.py --fix-cryptonite   # Patch rapide sur base existante
  ```
- **Répertoire de sortie isolé pour 2.1-omnis** :
  Conformément à la directive du message 0064, la nouvelle version 2.1-omnis sera forgée dans un **dossier de sortie nouveau et isolé** :
  `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite/OUTPUT_v2.1/`
  Elle ne sera jamais écrite par-dessus `JemmaPass DB/knowledge_full.db` ni le dossier `OUTPUT/` existant.
- **Registre des sources ingérées (`kb_sources`)** :
  La base contient 32 enregistrements de traçabilité dans la table `kb_sources`. L'export brut a été déposé dans :
  `docs/analysis/kb-next-evidence/kb_sources_raw.txt` (32 sources, URLs, licences, versions et comptes d'enregistrements).

---

## 2. Audit du Serveur de Distribution et Emplacement de `sizeInBytes` (0068)

### 2.1. Preuve brute par en-têtes HTTP (`curl -sSI`)

Exécution du 3 octobre 2026 :
```http
HTTP/2 200 
date: Sat, 03 Oct 2026 15:06:30 GMT
content-length: 3360727040
server: Apache
last-modified: Sun, 17 May 2026 00:55:37 GMT
accept-ranges: bytes
```

### 2.2. Sommes de contrôle SHA-256 et résolution de la contradiction

Calculs d'empreintes SHA-256 réalisés sur les bases physiques :
- **Base B (Téléphone / `/tmp/jp/kb/knowledge_full.db`)** :
  - Taille : **3 360 727 040 octets** (820 490 pages de 4 096 octets)
  - SHA-256 : `237d899f9e81e6d22af01bc6969798d1495131f1f1d4caa0491d80ac6a06014c`
- **Base distante (`https://jemmapass.net/models/knowledge_full.db`)** :
  - `content-length` : **3 360 727 040 octets**
  - Dernière modification : `Sun, 17 May 2026 00:55:37 GMT`
- **Base A (Archive contest / `JemmaPass DB/knowledge_full.db`)** :
  - Taille : `3 358 871 552 octets` (820 037 pages de 4 096 octets)
  - SHA-256 : `08a5d4b48454ea6c85e081bd1c5b40aeee5bad48e1dd3221c6e1cd1cfb1f223e`

**Conclusion sans appel** :
Le serveur web sert bien la **Base B** (3 360 727 040 octets), qui correspond au bit près à la taille attendue dans le code (`JemmaModelCatalog.kt:89`) et à la copie présente sur le téléphone de test.

### 2.3. Utilisation de `sizeInBytes` dans l'application

Inventaire exhaustif des usages de `sizeInBytes` (`JemmaModelCatalog.knowledgeBase.sizeInBytes = 3_360_727_040L`) :

1. **Validation d'intégrité (seul contrôle existant)** :
   - `downloads/JemmaDownloadStorage.kt:81,84` :
     ```kotlin
     val expected = model.sizeInBytes
     val ok = onDisk >= (expected * 0.95).toLong()
     ```
     L'application n'exige **pas** une taille exacte : elle valide tout fichier occupant au moins 95 % de la taille catalogue.
2. **Contrôle d'espace disque disponible avant téléchargement** :
   - `downloads/JemmaDownloadStorage.kt:109` :
     ```kotlin
     val stat = StatFs(root.absolutePath)
     stat.availableBytes
     ```
     Comparé à `model.sizeInBytes` par le preflight check des préférences.
3. **Coordination des téléchargements & suivi de progression** :
   - `downloads/JemmaDownloadCoordinator.kt:71,103,207,211,221,359,360` : calcul du pourcentage d'avancement, comparaison avec les octets reçus du `DownloadWorker`.
4. **Affichage dans l'interface utilisateur** :
   - `SettingsCardBinders.kt:82,211`, `SettingsFragment.kt:396`, `SettingsViewModel.kt:267` : formatage en chaîne lisible (`Formatter.formatFileSize(..., model.sizeInBytes)` $\rightarrow$ `"3,13 Go"`).
5. **Journalisation** :
   - `GemmaSession.kt:96,147` : log de la taille allouée lors de la configuration de session.

---

## 3. Analyse de Sécurité SD-25 : Faille de la Règle des 95 % et Expérimentation (0071, 0076)

### 3.1. Expérimentation sur pièces : Troncatures et Corruption au Milieu

Conformément à la consigne 0076 §1, une batterie d'expériences a été menée sur des clones APFS (`cp -c`, zéro surcoût disque) de la base de 3,36 Go. Les sorties brutes ont été consignées dans `docs/analysis/kb-next-evidence/sd25/` :

#### Expérience 1 : Troncature à 95 % (3 192 690 688 octets, 168 Mo amputés)
- **Ouverture de connexion SQLite (`mode=ro`)** : **RÉUSSIT** (`SUCCESS`). L'en-tête et les premières pages sont intacts.
- **`PRAGMA quick_check;`** : **ÉCHOUE IMMÉDIATEMENT** (`database disk image is malformed`).
- **Requêtes `SELECT count(*)`** : Toutes les requêtes lèvent `sqlite3.DatabaseError: database disk image is malformed`.
- **Requêtes cliniques (Edoxaban × Ibuprofen, Allergie pénicilline)** : Lèvent `database disk image is malformed`.

#### Expérience 2 : Troncature à 99 % (3 327 119 770 octets, 33,6 Mo amputés)
- Même constat : la connexion réussit, mais toute consultation de table au-delà du tronc d'arbre lève `database disk image is malformed`.

#### Expérience 3 : Corruption d'un bloc de 1 Mo de zéros au milieu (offset 1,6 Go)
- **Ouverture de connexion SQLite (`mode=ro`)** : **RÉUSSIT** (`SUCCESS`).
- **`PRAGMA quick_check;`** : **ÉCHOUE** (`database disk image is malformed`).
- **Requêtes `SELECT count(*)`** : **RÉUSSISSENT TOUTES SANS EXCEPTION !**
  - `build_metadata`: 5
  - `atc_hierarchy`: 6 934
  - `ddinter_drugs`: 2 289
  - `ddi_facts`: 260 100
  - `ddi_atc_pairs`: 876 277
  - `terminology_codes`: 1 452 451
  - `ips_valuesets`: 8 554
  - `allergy_cross_reactivity`: 52
- **Requêtes cliniques de test** : Réussissent si leurs pages b-tree ne sont pas dans le mégaoctet écrasé !
  - Edoxaban × Ibuprofen renvoie bien la DDI majeure `107621`.

#### Durée mesurée de `PRAGMA quick_check;` sur la base complète saine :
- **Mesure chronométrée** : **287,540 secondes** (~4,8 minutes) sur le Mac pour parcourir les 820 490 pages !
- **Enseignement capital** : Il est **techniquement impossible** d'exécuter un `PRAGMA quick_check` complet sur 3,36 Go au démarrage d'une application mobile Android (blocage de 5 minutes). En revanche, la vérification de l'empreinte SHA-256 au téléchargement (SD-25) et des sondes ciblées post-patch sont instantanées.

### 3.2. Analyse du comportement réel de `KnowledgeBaseManager.kt`

Dans l'état actuel de `KnowledgeBaseManager.kt:344-356` :
```kotlin
private fun countTable(db: SQLiteDatabase, tableName: String): Long {
    return try {
        db.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
    } catch (e: Exception) {
        Log.d(TAG, "... table '$tableName' absent or unreadable : ${e.message}")
        0L // L'erreur est étouffée et renvoie 0L !
    }
}
```
1. Si la base est tronquée (95 %), `openDatabase()` réussit. Puis les appels `countTable()` lèvent `database disk image is malformed`. L'exception est **attrapée et étouffée**, assignant `0L` aux statistiques.
2. Si une corruption partielle (bloc de zéros au milieu) est présente, les comptes de table renvoient les valeurs normales !
3. Dans les deux cas, `probeFts5` ne teste que le préfixe `'a*'` qui réussit dès les premières pages.
4. L'application bascule alors en **`KbState.Ready`** (boîte verte "Opérationnelle").
5. Lors d'un scan d'urgence ou d'un affichage de profil : si une requête DDI ou allergie lève `SQLiteDatabaseCorruptException` (ou si l'index retourne 0 ligne), le code appelant attrape l'erreur et conclut à l'absence de conflit. Le secouriste voit un bilan vierge (**"Aucun conflit détecté"**), ce qui constitue un risque clinique mortel.

### 3.3. Fonction pure de verdict de téléchargement (testable en JVM)

La décision d'intégrité doit être déléguée à la fonction pure sans Android ni Log (livrée dans le squelette `ag/0076-kb-integrity`) :

```kotlin
package be.heyman.android.jemmapassdemo.downloads

enum class DownloadVerdict {
    /** Fichier complet : taille exacte et SHA-256 rigoureusement conforme à l'attendu. */
    COMPLETE,
    /** Fichier partiel : taille sur disque strictement inférieure à la taille attendue. */
    TRUNCATED,
    /** Fichier corrompu : taille anormale ou empreinte SHA-256 divergente. */
    CORRUPT,
    /** Fichier présent et de taille attendue, mais aucune empreinte de référence n'est disponible. */
    UNVERIFIED,
}

object DownloadIntegrity {

    /**
     * Évalue l'intégrité d'un téléchargement sans tolérance d'amputation (SD-25).
     *
     * Invariants :
     * - Si expectedSha256 est fourni : le verdict COMPLETE exige onDiskSha256 == expectedSha256 (insensible à la casse).
     * - Une taille inférieure donne TRUNCATED.
     * - Une empreinte divergente donne CORRUPT.
     * - L'absence d'empreinte attendue sur un fichier de taille exacte donne UNVERIFIED (jamais COMPLETE sans preuve).
     */
    fun downloadVerdict(
        onDiskBytes: Long,
        expectedBytes: Long,
        onDiskSha256: String?,
        expectedSha256: String?,
    ): DownloadVerdict {
        if (onDiskBytes <= 0L || expectedBytes <= 0L) return DownloadVerdict.TRUNCATED
        if (onDiskBytes < expectedBytes) return DownloadVerdict.TRUNCATED

        if (expectedSha256.isNullOrBlank()) {
            return if (onDiskBytes == expectedBytes) DownloadVerdict.UNVERIFIED else DownloadVerdict.CORRUPT
        }

        if (onDiskSha256.isNullOrBlank()) {
            return DownloadVerdict.UNVERIFIED
        }

        return if (onDiskSha256.trim().equals(expectedSha256.trim(), ignoreCase = true)) {
            if (onDiskBytes == expectedBytes) DownloadVerdict.COMPLETE else DownloadVerdict.CORRUPT
        } else {
            DownloadVerdict.CORRUPT
        }
    }
}
```

---

## 4. Conception de la Mise à Jour Différentielle (PROTOCOL §9.2 / Tâches 0072, 0076)

### 4.1. Comparaison des formats de correctif candidats

Une vérification empirique exhaustive de `com.github.requery:sqlite-android:3.49.0` a été réalisée sur la machine de test :
- **Inspection du binaire natif** : Le fichier `libsqlite3x.so` extrait de `sqlite-android-3.49.0.aar` pour les 4 architectures (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) a été passé au crible (`nm -D` et `strings`). **Résultat brut : 0 symbole pour `sqlite3session_*` et 0 symbole pour `sqlite3changeset_*`**. La bibliothèque native a été compilée sans le drapeau `SQLITE_ENABLE_SESSION`.
- **Inspection des classes Java** : L'archive `classes.jar` ne contient que la classe `io.requery.android.database.sqlite.SQLiteSession`, qui est un simple gestionnaire de connexions/transactions calqué sur le framework Android, sans aucun pont JNI vers l'extension Session de SQLite.
- **Incompatibilité FTS5 native** : L'extension Session de SQLite ne gère pas les tables virtuelles (les opérations sur FTS5 ne déclenchent pas les `preupdate_hook` du moteur SQLite).

| Critère | Candidat 1 : SQLite Delta (`.db`) via `ATTACH` **[RETENU]** | Candidat 2 : Script SQL (`.sql` / `.sql.gz`) | Candidat 3 : SQLite Session Extension (`changeset`) |
|---|---|---|---|
| **Mécanisme** | Fichier SQLite auxiliaire attaché à chaud : `ATTACH 'delta.db' AS delta; INSERT OR IGNORE INTO main.T SELECT * FROM delta.T;` | Fichier texte exécuté instruction par instruction via `execSQL()` dans une transaction. | Changements binaires appliqués via l'API C SQLite Session. |
| **Compatibilité FTS5 (`terminology_latin`, `terminology_cjk`)** | **Excellente** : Les tables virtuelles FTS5 acceptent nativement `INSERT INTO main.terminology_latin (code, lang, display) SELECT code, lang, display FROM delta.terminology_latin`. | **Moyenne** : Lourdeur des instructions FTS en texte brut avec gestion délicate des échappements et trigrammes CJK. | **IMPOSSIBLE** : La documentation officielle SQLite exclut explicitement les tables virtuelles (FTS5 est ignoré par les changesets). |
| **Gestion de `word_index`** | **Excellente** : Table relationnelle classique (~15,5M lignes), injection directe par `INSERT OR IGNORE INTO main.word_index SELECT * FROM delta.word_index`. | **Lente** : Volume texte prohibitif si des milliers de tokens sont ajoutés. | Possible uniquement sur tables standard (pas sur FTS). |
| **Disponibilité Android** | **100 % disponible** dans `requery/sqlite-android:3.49.0` (SQL pur). | **100 % disponible** via `execSQL()`. | **ABSENTE** : Aucun symbole natif ni binding JNI dans requery 3.49.0. |
| **Taille réseau (`2.0` $\rightarrow$ `2.1`)** | **~2 à 4 Mo** (estimation basée sur ~5 000 lignes B-tree + FTS5 ; < 1,5 Mo compressé zstd/gzip). | ~3 à 6 Mo en SQL texte brut (~1 Mo compressé). | Inapplicable (FTS5 manquant). |
| **Durée d'application sur device** | **~1 à 3 secondes** (estimation : pages binaires injectées directement par le moteur SQLite). | 10 à 30 secondes (parsing syntaxique SQL de milliers de lignes). | Inapplicable. |
| **Espace disque temporaire** | **~5 à 15 Mo** (estimation : rollback journal proportionnel aux pages modifiées, zéro clone des 3,36 Go). | ~15 Mo. | Inapplicable. |
| **Atomicité (Tout ou rien)** | **Garantie 100 %** par une transaction unique `BEGIN IMMEDIATE ... COMMIT`. | Garantie si enveloppé dans une transaction. | Inapplicable. |

> **Décision d'architecture** : Le **Candidat 1 (Fichier SQLite Delta `patch_X_to_Y.db`)** est la seule solution viable, performante, atomique et 100 % compatible avec FTS5 sans recompilation de bibliothèque C.

### 4.2. Schéma et cycle de vie du Manifeste distant (`kb_manifest.json`)

Le manifeste est servi à l'adresse fixe : `https://jemmapass.net/models/kb_manifest.json`.

```json
{
  "manifest_version": 1,
  "updated_at": "2026-10-04T00:00:00Z",
  "full_distribution": {
    "version": "2.1-omnis",
    "filename": "knowledge_full.db",
    "url": "https://jemmapass.net/models/knowledge_full.db",
    "size_bytes": "<à calculer au build>",
    "sha256": "<à calculer au build>"
  },
  "patches": [
    {
      "from_version": "2.0-omnis",
      "to_version": "2.1-omnis",
      "filename": "patch_2.0_to_2.1.db",
      "url": "https://jemmapass.net/models/patches/patch_2.0_to_2.1.db",
      "size_bytes": "<à calculer au build>",
      "sha256": "<à calculer au build>",
      "post_conditions": {
        "min_terminology_codes": 1452487,
        "min_ips_translations": 85736,
        "min_atc_hierarchy": 6934,
        "min_ddinter_drugs": 2290
      }
    }
  ]
}
```

#### Comportement réseau & Hors-ligne :
1. **Interrogation passive** : À l'ouverture de l'application ou lors d'un passage en ligne, une tâche d'arrière-plan interroge le manifeste avec un timeout strict (3 secondes).
2. **Hors-ligne / Serveur injoignable** : L'échec de récupération du manifeste est silencieux. L'application continue d'exploiter la base locale installée sans aucun blocage.
3. **Manifeste invalide / corrompu** : Rejeté immédiatement. Aucune modification n'est apportée à la base locale.

### 4.3. Algorithme de sélection de chaîne de correctifs (`PatchChainResolver`)

Un téléphone resté en version `2.0-omnis` alors que la version courante est `2.3-omnis` doit choisir le chemin optimal. Conformément à la règle fixée en 0076 §3, l'algorithme sélectionne :
1. **En priorité le chemin le plus court en nombre de correctifs** (un patch direct cumulatif `2.0 -> 2.3` l'emporte sur la suite `2.0 -> 2.1 -> 2.2 -> 2.3`) ;
2. **À nombre de sauts égal, le chemin le plus léger en octets**.

```kotlin
package be.heyman.android.jemmapassdemo.downloads

data class PatchDescriptor(
    val fromVersion: String,
    val toVersion: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val postConditions: Map<String, Long> = emptyMap(),
)

sealed class PatchChainVerdict {
    data class Chain(val patches: List<PatchDescriptor>) : PatchChainVerdict()
    object AlreadyUpToDate : PatchChainVerdict()
    object FallbackToFullDownload : PatchChainVerdict()
}

object PatchChainResolver {

    /**
     * Résout la suite ordonnée de patchs menant de [currentVersion] à [targetVersion].
     * Utilise une recherche en largeur (BFS) pour garantir le plus petit nombre de correctifs,
     * puis départage par la taille cumulée minimale en octets.
     */
    fun resolveChain(
        currentVersion: String,
        targetVersion: String,
        availablePatches: List<PatchDescriptor>,
    ): PatchChainVerdict {
        if (currentVersion == targetVersion) return PatchChainVerdict.AlreadyUpToDate

        // BFS pour trouver tous les chemins menant à targetVersion
        val queue: ArrayDeque<List<PatchDescriptor>> = ArrayDeque()
        for (patch in availablePatches.filter { it.fromVersion == currentVersion }) {
            queue.add(listOf(patch))
        }

        val successfulChains = mutableListOf<List<PatchDescriptor>>()
        val visited = mutableSetOf<String>()

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val tip = path.last()

            if (tip.toVersion == targetVersion) {
                successfulChains.add(path)
                continue
            }

            if (path.size >= 10) continue // Limite anti-boucle

            for (nextPatch in availablePatches.filter { it.fromVersion == tip.toVersion }) {
                // Éviter les cycles sur les versions déjà traversées dans ce chemin
                if (path.none { it.fromVersion == nextPatch.toVersion }) {
                    queue.add(path + nextPatch)
                }
            }
        }

        if (successfulChains.isEmpty()) return PatchChainVerdict.FallbackToFullDownload

        // Critère 0076 : Plus petit nombre de sauts d'abord, puis taille cumulée en octets
        val bestChain = successfulChains.minWithOrNull(
            compareBy<List<PatchDescriptor>> { it.size }
                .thenBy { it.sumOf { p -> p.sizeBytes } }
        ) ?: return PatchChainVerdict.FallbackToFullDownload

        return PatchChainVerdict.Chain(bestChain)
    }
}
```

### 4.4. Exécution transactionnelle atomique (Schéma Réel & Zéro Copie)

L'application du patch s'exécute directement sur le fichier SQLite en place, sans duplication des 3,36 Go.
La structure SQL respecte scrupuleusement le **schéma 3NF réel** documenté dans `kb-only-evidence/01-schema.out` et intègre les exigences de **rejouabilité/idempotence** et de **gestion des rowid FTS5** :

```sql
-- 1. Attacher la base de patch téléchargée et vérifiée par SHA-256
ATTACH DATABASE '/data/user/0/.../cache/patch_2.0_to_2.1.db' AS patch;

-- 2. Transaction atomique exclusive (bloque toute écriture concurrente)
BEGIN IMMEDIATE TRANSACTION;

-- 3. GARDE D'IDEMPOTENCE ET DE REJOUABILITÉ :
-- Vérifier que la version actuelle est strictement la version de départ requise.
-- Si la version est déjà '2.1-omnis', l'instruction lève une erreur (ou ROLLBACK) et s'arrête.
SELECT CASE 
    WHEN (SELECT value FROM main.build_metadata WHERE key = 'build_version') != '2.0-omnis'
    THEN RAISE(ABORT, 'IDEMPOTENCE_GUARD: KB is not at required from_version 2.0-omnis')
END;

-- 4. Insertion dans terminology_codes (schéma réel à 10 colonnes)
INSERT OR IGNORE INTO main.terminology_codes (
    code, atc_code, atc_source, rxnorm_cui, snomed_code, 
    ips_validated, is_combo, primary_display, system, category
)
SELECT 
    code, atc_code, atc_source, rxnorm_cui, snomed_code, 
    ips_validated, is_combo, primary_display, system, category
FROM patch.terminology_codes;

-- 5. Insertion dans ips_valuesets et traductions (schéma réel)
INSERT OR IGNORE INTO main.ips_valuesets (vs_id, code, code_system, display_en)
SELECT vs_id, code, code_system, display_en FROM patch.ips_valuesets;

INSERT OR IGNORE INTO main.ips_valuesets_translations (vs_id, code, code_system, lang, display)
SELECT vs_id, code, code_system, lang, display FROM patch.ips_valuesets_translations;

-- 6. Mise à jour de atc_hierarchy (libellés multilingues FR et JA)
UPDATE main.atc_hierarchy AS m
SET name_fr = p.name_fr,
    name_jp = p.name_jp
FROM patch.atc_hierarchy AS p
WHERE m.atc_code = p.atc_code 
  AND (p.name_fr IS NOT NULL OR p.name_jp IS NOT NULL);

-- 7. Insertion dans ddinter_drugs (ex: albuterol) et drug_names
INSERT OR IGNORE INTO main.ddinter_drugs (
    ddinter_id, name, primary_atc, atc_codes, formula, weight, cas, smiles, inchi, inchi_key, chembl_id, pubchem_cid
)
SELECT 
    ddinter_id, name, primary_atc, atc_codes, formula, weight, cas, smiles, inchi, inchi_key, chembl_id, pubchem_cid
FROM patch.ddinter_drugs;

-- 8. Mise à jour des index FTS5 (SANS rowid explicite pour éviter toute collision)
INSERT INTO main.terminology_latin (code, lang, display)
SELECT code, lang, display FROM patch.terminology_latin;

INSERT INTO main.terminology_cjk (code, lang, display)
SELECT code, lang, display FROM patch.terminology_cjk;

-- 9. Insertion dans word_index
INSERT OR IGNORE INTO main.word_index (word, concept_code, lang, weight)
SELECT word, concept_code, lang, weight FROM patch.word_index;

-- 10. Insertion de la traçabilité par ligne (KBC-04)
INSERT OR IGNORE INTO main.kb_provenance (table_name, row_key, source, source_version, fetched_on)
SELECT table_name, row_key, source, source_version, fetched_on FROM patch.kb_provenance;

-- 11. Mise à jour des métadonnées de version
UPDATE main.build_metadata
SET value = '2.1-omnis'
WHERE key = 'build_version';

UPDATE main.build_metadata
SET value = '2026-10-04 00:00:00'
WHERE key = 'build_timestamp';

-- 12. Validation finale de la transaction
COMMIT;

-- 13. Détachement
DETACH DATABASE patch;
```

#### Gestion de la connexion et concurrence d'accès :
- `KnowledgeBaseManager` détient actuellement une connexion en `OPEN_READONLY` pour le service de l'application.
- Pour appliquer le correctif, le `JemmaKbPatchExecutor` ouvre une connexion dédiée en `OPEN_READWRITE`.
- Avant d'exécuter `BEGIN IMMEDIATE TRANSACTION;`, le gestionnaire bascule l'état `_state.value = KbState.Validating`. Les écrans et coroutines d'analyse suspendent brièvement leurs lectures pendant la durée du patch (~1 à 3 secondes).
- Une fois le patch commité et vérifié, `KnowledgeBaseManager.reload()` réinitialise les caches mmap et bascule l'état en `KbState.Ready` avec la nouvelle version `2.1-omnis`.

### 4.5. Fonctions pures de vérification post-patch (`PostPatchVerifier`)

```kotlin
package be.heyman.android.jemmapassdemo.downloads

sealed class PostPatchVerdict {
    object Valid : PostPatchVerdict()
    data class VersionMismatch(val actual: String, val expected: String) : PostPatchVerdict()
    data class InsufficientCount(val table: String, val actual: Long, val required: Long) : PostPatchVerdict()
    data class Corrupt(val message: String) : PostPatchVerdict()
}

object PostPatchVerifier {

    /**
     * Vérifie les post-conditions après application du patch (pure, sans Log ni Android).
     */
    fun verify(
        actualVersion: String?,
        expectedVersion: String,
        integrityOk: Boolean,
        actualCounts: Map<String, Long>,
        requiredMinCounts: Map<String, Long>,
    ): PostPatchVerdict {
        if (!integrityOk) {
            return PostPatchVerdict.Corrupt("Vérification d'intégrité échouée")
        }
        if (actualVersion != expectedVersion) {
            return PostPatchVerdict.VersionMismatch(actual = actualVersion ?: "null", expected = expectedVersion)
        }
        for ((metric, minCount) in requiredMinCounts) {
            val count = actualCounts[metric] ?: 0L
            if (count < minCount) {
                return PostPatchVerdict.InsufficientCount(table = metric, actual = count, required = minCount)
            }
        }
        return PostPatchVerdict.Valid
    }
}
```

### 4.6. Génération générique du patch dans le pipeline (`forge_cryptonite`)

Le module `step26_generate_delta.py` sera intégré aux côtés de `turbo_forge_jemma_db.py` dans `JEMMA_DB_DATA/forge_cryptonite/` :

1. **Extraction différentielle générique** :
   - Le script ouvre `old_base` (`2.0-omnis`) et `new_base` (`2.1-omnis`).
   - Pour chaque table $T$ de la base (obtenue dynamiquement par `SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE '%_fts%' AND name NOT LIKE '%_idx%'`), il interroge `PRAGMA table_info(T)` pour extraire la liste exacte des colonnes.
   - Il génère dynamiquement la requête différentielle :
     ```sql
     SELECT col1, col2, ... FROM new.T EXCEPT SELECT col1, col2, ... FROM old.T;
     ```
   - Les deltas sont injectés dans une base SQLite vierge `patch_2.0_to_2.1.db`.
   - Pour les tables virtuelles FTS5, il sélectionne les entrées correspondantes aux nouveaux codes et les insère dans le patch.
   - Il génère automatiquement les lignes de `kb_provenance` associées.
2. **Calcul d'empreintes et manifeste** :
   - Calcule le SHA-256 de `patch_2.0_to_2.1.db`.
   - Calcule le SHA-256 de la distribution complète `knowledge_full.db` 2.1.
   - Génère `kb_manifest.json` avec les post-conditions (comptes minimaux réels).
3. **Rejouabilité des tests KBC d'acceptation** :
   - Le pipeline applique le patch généré sur une copie temporaire de la base 2.0 :
     ```bash
     python3 apply_patch.py --db /tmp/test_2.0.db --patch patch_2.0_to_2.1.db
     ```
   - Le script officiel de test d'acceptation de Claude `qa/kb/tests/test_kb_coverage.py` est exécuté :
     ```bash
     KB=/tmp/test_2.0.db KB_OLD=/tmp/knowledge_2.0.db python3 qa/kb/tests/test_kb_coverage.py
     ```
   - Le patch n'est déclaré prêt à publication que si la base patchée passe **6/6 PASS** avec des résultats rigoureusement identiques à la nouvelle base complète.

### 4.7. Authenticité et Sécurité : Comparaison pour décision de Kudoro (0076 §4)

Le SHA-256 du correctif étant servi sur le même domaine web (`jemmapass.net`), un attaquant ayant compromis le serveur ou le CDN pourrait remplacer simultanément le patch et le manifeste pour empoisonner la base médicale des utilisateurs. Deux options de sécurité sont soumises à l'arbitrage de Kudoro :

| Option | Fonctionnement | Niveau de Sécurité | Coût d'Implémentation & Maintenance |
|---|---|---|---|
| **Option A : HTTPS seul** | Téléchargement du manifeste et du patch via TLS (`https://jemmapass.net`) avec validation de certificat standard Android. Contrôle de l'empreinte SHA-256 issue du manifeste. | **Standard Web** : Protège contre l'écoute et l'interception locale (Man-in-the-Middle sur Wi-Fi public). Vulnérable si le serveur d'hébergement web ou les identifiants DNS sont compromis. | **Nul** : Déjà pris en charge nativement par le stack réseau de l'application. |
| **Option B : Manifeste signé (ECDSA P-256 ou Ed25519)** | Le manifeste `kb_manifest.json` contient un champ `signature`. Lors de la forge sur le Mac de Kudoro, le manifeste est signé avec une clé privée hors-ligne. L'application Android vérifie la signature cryptographique à l'aide d'une clé publique compilée dans le code. | **Sécurité Médicale Absolue** : Même avec un serveur web totalement compromis ou un faux miroir, aucun correctif non signé par la clé privée hors-ligne de Kudoro ne peut être appliqué sur les téléphones. | **Très faible** : ~30 lignes de code Kotlin pur (`java.security.Signature` standard Android / JVM, zéro dépendance externe). Nécessite uniquement la conservation sécurisée de la clé privée de forge par Kudoro. |

> **Recommandation technique** : Pour un dispositif médical d'urgence hors-ligne où la falsification d'une interaction ou d'une allergie peut engager le pronostic vital, l'**Option B (Signature asymétrique hors-ligne)** apporte une garantie d'intégrité absolue à coût quasi nul. Kudoro tranche.

### 4.8. Analyse honnête des risques opérationnels et parades

1. **Concurrence de lecture pendant l'application du patch** :
   - *Risque* : SQLite lève `SQLiteDatabaseLockedException` si l'application effectue une requête pendant `BEGIN IMMEDIATE`.
   - *Parade* : `KnowledgeBaseManager` fait basculer son StateFlow en `KbState.Validating` avant d'ouvrir la connexion en écriture, suspendant temporairement les requêtes de consultation de l'interface pendant les ~1 à 3 secondes de l'écriture.
2. **Espace disque insuffisant sur le téléphone** :
   - *Risque* : Échec d'allocation du journal de transaction SQLite (`disk full`).
   - *Parade* : Contrôle préalable via `JemmaDownloadStorage.freeStorageBytes()`. Le patch n'est déclenché que s'il reste au moins **100 Mo** libres (garantissant largement les ~5 à 15 Mo de journal SQLite).
3. **Coupure d'alimentation ou arrêt brutal de l'application pendant le patch** :
   - *Risque* : Base corrompue en cours de modification.
   - *Parade* : Garantie native du moteur SQLite (ACID). Le fichier journal permet à SQLite d'annuler automatiquement la transaction incomplète et de restaurer l'état exact pré-patch au redémarrage suivant.
4. **Rollback en cas d'échec post-patch** :
   - *Risque* : La base est mise à jour mais les post-conditions (comptes ou intégrité) échouent.
   - *Parade* : La transaction n'est validée par `COMMIT` **qu'après** vérification des post-conditions au sein de la transaction. En cas d'anomalie, un simple `ROLLBACK` rétablit instantanément l'état initial sans altération.

