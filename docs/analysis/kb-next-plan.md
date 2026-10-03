# Plan de Construction & Conception des Mises à Jour de la KB 2.1-omnis

> **Document de Conception & Spécification Technique**  
> **Orchestrateur** : `Antigravity-KB` (Couloir KB)  
> **Branche de travail** : `ag/0055-kb-build`  
> **Références** : Messages boîte 0055, 0058, 0059, 0064, 0068, 0071, 0072 ; PROTOCOL §9, §9.1, §9.2  
> **Décision produit de Kudoro (2026-10-03)** : Option 3 — Correctifs différentiels légers (Mo), sans nouvelle publication d'application.

---

## 1. Synthèse des Invariants et Versions de la Base de Connaissances

### 1.1. Tableau des versions et cartographie des sources

Suite à la levée des ambiguïtés (messages 0064 et 0068), le tableau de référence s'établit sur pièces :

| Composant | Version interne (`build_metadata`) | Version dans le code Android | Nom de distribution | Taille exacte (octets) | Rôle & Destination |
|---|---|---|---|---|---|
| **Base téléphone (B)** | `2.0-omnis` | `1.1` (`JemmaModelCatalog.kt:86`) | `knowledge_full.db` | **3 360 727 040** | Base installée sur le device de test et distribuée sur le serveur. |
| **Base contest (A)** | Non versionnée / draft | — | `knowledge_full.db` | 3 358 871 552 | Version intermédiaire issue du contest initial (1,85 Mo de moins). |
| **Cible future** | `2.1-omnis` | `1.1` (inchangée car MAJ sans nouvelle app) | `knowledge_full.db` (complet) & `patch_2.0_to_2.1.db` (delta) | À déterminer au build | Base enrichie (LOINC + traductions FR/JA + EML/AWaRe). |

- **URL réelle de distribution** : `https://jemmapass.net/models/knowledge_full.db` (URL non versionnée, définie dans `downloads/JemmaModelCatalog.kt:33,87`).
- **Chemin de stockage sur Android** : `{externalFilesDir}/knowledge_full_db/1.1/knowledge_full.db` (normalisé par `JemmaDownloadStorage.kt:63`).
- **Nom de la prochaine version** : **`2.1-omnis`** dans `build_metadata`, **`1.2`** pour le site et l'app.

### 1.2. Constatations d'audit du pipeline de build (`JEMMA_DB_DATA/forge_cryptonite/`)

Conformément aux instructions des messages 0055, 0059 et 0064, l'audit du système de forge a permis de localiser et documenter le pipeline exact :

- **Emplacement exact** : `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite/`
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

## 3. Analyse de Sécurité SD-25 : Faille de la Règle des 95 % (0071)

### 3.1. Ce qui se produit lorsqu'une base tronquée est ouverte

Dans l'état actuel (`JemmaDownloadStorage.kt:84`), un fichier amputé de 5 % (jusqu'à **168 Mo manquants**) est déclaré `isFullyDownloaded == true`.

1. **Ouverture par `KnowledgeBaseManager.kt:213-224`** :
   - `SQLiteDatabase.openDatabase(kbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)` **réussit** !
   - L'en-tête SQLite (page 1) et les tables maîtresses / métadonnées sont situés au tout début du fichier.
   - SQLite n'effectue aucun `PRAGMA integrity_check` ni `PRAGMA quick_check` à l'ouverture.
2. **Lecture des métadonnées (`KnowledgeBaseManager.kt:243`)** :
   - `readBuildMetadata(db)` lit la table `build_metadata` (pages de tête) $\rightarrow$ renvoie `2.0-omnis` avec succès.
3. **Comptage des lignes (`KnowledgeBaseManager.kt:250, 344-356`)** :
   - `countTable(db, tableName)` exécute `SELECT COUNT(*)` dans un bloc `try/catch` qui **étouffe l'erreur** :
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
   - Si les pages de fin de table sont manquantes, l'exception est attrapée et la méthode renvoie simplement `0L`.
4. **Sondes FTS5 (`KnowledgeBaseManager.kt:276, 497-535`)** :
   - `probeFts5Latin` et `probeFts5Cjk` exécutent `SELECT rowid FROM terminology_latin WHERE terminology_latin MATCH 'a*' LIMIT 1`.
   - Comme `'a*'` trouve une correspondance dès les premières pages de l'index FTS5, la requête retourne `true` même si 90 % de l'index suivant est manquant.
5. **État exposé à l'utilisateur (`KnowledgeBaseManager.kt:324`)** :
   - L'application bascule en **`KbState.Ready`** (boîte verte "Opérationnelle").
   - **Conséquence clinique catastrophique** : Les requêtes d'interactions médicamenteuses (`ddi_facts`), d'allergies ou de contre-indications situées dans les pages manquantes renvoient silencieusement **0 résultat**.
   - Le moteur interprète 0 résultat comme une absence d'interaction : **l'utilisateur et le médecin urgentiste voient un écran vert `CLEAN` ("Aucun risque détecté") au lieu d'une alerte vitale !**

### 3.2. Fonction pure de verdict de téléchargement (testable en JVM)

Pour corriger SD-25 sans dépendance Android (`Log`, `Model`, `Context`), la décision de validité d'un fichier téléchargé doit être confiée à une fonction pure :

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

### 3.3. D'où vient l'empreinte SHA-256 attendue ?

1. **Première installation / Bootstrap** :
   - Constante d'empreinte SHA-256 inscrite dans `JemmaModelCatalog.kt` pour la base complète initiale.
2. **Mises à jour et cycle de vie courant** :
   - L'empreinte officielle vient du fichier manifeste distant `kb_manifest.json` (détaillé ci-après).

---

## 4. Conception de la Mise à Jour Différentielle (PROTOCOL §9.2 / Tâche 0072)

### 4.1. Comparaison des formats de correctif candidats

Conformément à la consigne de vérification sur pièces, une inspection exhaustive de `com.github.requery:sqlite-android:3.49.0` a été réalisée sur la machine de test :
- **Inspection du binaire natif** : Le fichier `libsqlite3x.so` extrait de `sqlite-android-3.49.0.aar` pour les 4 architectures (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) a été passé au crible (`nm -D` et `strings`). **Résultat brut : 0 symbole pour `sqlite3session_*` et 0 symbole pour `sqlite3changeset_*`**. La bibliothèque native a été compilée sans le drapeau `SQLITE_ENABLE_SESSION`.
- **Inspection des classes Java** : L'archive `classes.jar` ne contient que la classe `io.requery.android.database.sqlite.SQLiteSession`, qui est un simple gestionnaire de connexions/transactions calqué sur le framework Android, sans aucun pont JNI vers l'extension Session de SQLite.
- **Incompatibilité FTS5 native** : L'extension Session de SQLite ne gère pas les tables virtuelles (les opérations sur FTS5 ne déclenchent pas les `preupdate_hook` du moteur SQLite).

| Critère | Candidat 1 : SQLite Delta (`.db`) via `ATTACH` **[RETENU]** | Candidat 2 : Script SQL (`.sql` / `.sql.gz`) | Candidat 3 : SQLite Session Extension (`changeset`) |
|---|---|---|---|
| **Mécanisme** | Fichier SQLite auxiliaire attaché à chaud : `ATTACH 'delta.db' AS delta; INSERT OR IGNORE INTO main.T SELECT * FROM delta.T;` | Fichier texte exécuté instruction par instruction via `execSQL()` dans une transaction. | Changements binaires appliqués via l'API C SQLite Session. |
| **Compatibilité FTS5 (`terminology_latin`, `terminology_cjk`)** | **Excellente** : Les tables virtuelles FTS5 acceptent nativement `INSERT INTO main.terminology_latin SELECT ... FROM delta.terminology_latin`. | **Moyenne** : Lourdeur des instructions FTS en texte brut avec gestion délicate des échappements et trigrammes CJK. | **IMPOSSIBLE** : La documentation officielle SQLite exclut explicitement les tables virtuelles (FTS5 est ignoré par les changesets). |
| **Gestion de `word_index`** | **Excellente** : Table relationnelle classique (~15,5M lignes), injection directe par `INSERT OR IGNORE INTO main.word_index SELECT * FROM delta.word_index`. | **Lente** : Volume texte prohibitif si des milliers de tokens sont ajoutés. | Possible uniquement sur tables standard (pas sur FTS). |
| **Disponibilité Android** | **100 % disponible** dans `requery/sqlite-android:3.49.0` (SQL pur). | **100 % disponible** via `execSQL()`. | **ABSENTE** : Aucun symbole natif ni binding JNI dans requery 3.49.0. |
| **Taille réseau (`2.0` $\rightarrow$ `2.1`)** | **~2 à 4 Mo** (compressé en gzip/zstd sur le serveur : **< 1,5 Mo**). | ~3 à 6 Mo en SQL texte brut (~1 Mo compressé). | Inapplicable (FTS5 manquant). |
| **Durée d'application sur device** | **< 3 secondes** (pages binaires injectées directement par le moteur SQLite). | 10 à 30 secondes (parsing syntaxique SQL de milliers de lignes). | Inapplicable. |
| **Espace disque temporaire** | **~10 Mo** (rollback journal proportionnel au delta, aucun clone des 3,36 Go). | ~15 Mo. | Inapplicable. |
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
    "size_bytes": 3362825216,
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  },
  "patches": [
    {
      "from_version": "2.0-omnis",
      "to_version": "2.1-omnis",
      "filename": "patch_2.0_to_2.1.db",
      "url": "https://jemmapass.net/models/patches/patch_2.0_to_2.1.db",
      "size_bytes": 3145728,
      "sha256": "4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b",
      "post_conditions": {
        "min_terminology_codes": 1452500,
        "min_ips_translations": 85800,
        "integrity_check": "quick"
      }
    }
  ]
}
```

#### Comportement réseau & Hors-ligne :
1. **Interrogation passive** : À l'ouverture de l'application ou lors d'un passage en ligne, une tâche d'arrière-plan interroge le manifeste avec un timeout strict (3 secondes).
2. **Hors-ligne / Serveur injoignable** : L'échec de récupération du manifeste est silencieux. L'application continue d'exploiter la base locale installée sans aucun blocage.
3. **Manifeste invalide / corrompu** : Rejeté immédiatement. Aucune modification n'est apportée à la base locale.

### 4.3. Algorithme de résolution de chaîne de correctifs

Un téléphone resté en version `2.0-omnis` alors que la version courante est `2.3-omnis` doit pouvoir reconstituer la chaîne de patchs séquentielle :

```kotlin
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
     * Renvoie AlreadyUpToDate si la version actuelle est la cible,
     * Chain avec la liste séquentielle ordonnée si un chemin existe,
     * ou FallbackToFullDownload si la chaîne est rompue ou introuvable.
     */
    fun resolveChain(
        currentVersion: String,
        targetVersion: String,
        availablePatches: List<PatchDescriptor>,
    ): PatchChainVerdict {
        if (currentVersion == targetVersion) return PatchChainVerdict.AlreadyUpToDate

        val chain = mutableListOf<PatchDescriptor>()
        var cursor = currentVersion

        while (cursor != targetVersion) {
            val nextPatch = availablePatches.firstOrNull { it.fromVersion == cursor }
                ?: return PatchChainVerdict.FallbackToFullDownload
            chain.add(nextPatch)
            cursor = nextPatch.toVersion
            if (chain.size > 20) return PatchChainVerdict.FallbackToFullDownload // Protection contre les boucles
        }

        return PatchChainVerdict.Chain(chain)
    }
}
```

### 4.4. Exécution transactionnelle atomique (Zéro copie des 3,36 Go)

L'application du patch s'exécute directement sur le fichier SQLite en place, sans duplication des 3,36 Go :

```sql
-- 1. Attacher la base de patch téléchargée et vérifiée par SHA-256
ATTACH DATABASE '/data/user/0/.../cache/patch_2.0_to_2.1.db' AS patch;

-- 2. Transaction atomique exclusive (bloque toute lecture concurrente)
BEGIN IMMEDIATE TRANSACTION;

-- 3. Insertion additive des nouveaux codes (KBC-03 : aucun renommage ni suppression)
INSERT OR IGNORE INTO main.terminology_codes (code, atc_code, rxnorm_cui, snomed_code, ips_validated, primary_display, system, category)
SELECT code, atc_code, rxnorm_cui, snomed_code, ips_validated, primary_display, system, category FROM patch.terminology_codes;

-- 4. Insertion des nouvelles valeurs de jeux IPS et traductions
INSERT OR IGNORE INTO main.ips_valuesets (vs_id, code, code_system, display_en)
SELECT vs_id, code, code_system, display_en FROM patch.ips_valuesets;

INSERT OR IGNORE INTO main.ips_valuesets_translations (vs_id, code, code_system, lang, display)
SELECT vs_id, code, code_system, lang, display FROM patch.ips_valuesets_translations;

-- 5. Mise à jour de l'index FTS5 latin
INSERT INTO main.terminology_latin (code, lang, display)
SELECT code, lang, display FROM patch.terminology_latin;

-- 6. Mise à jour de l'index FTS5 CJK
INSERT INTO main.terminology_cjk (code, lang, display)
SELECT code, lang, display FROM patch.terminology_cjk;

-- 7. Insertion de la traçabilité par ligne (KBC-04)
INSERT OR IGNORE INTO main.kb_provenance (table_name, row_key, source, source_version, fetched_on)
SELECT table_name, row_key, source, source_version, fetched_on FROM patch.kb_provenance;

-- 8. Mise à jour des métadonnées de version
UPDATE main.build_metadata
SET value = '2.1-omnis'
WHERE key = 'build_version';

UPDATE main.build_metadata
SET value = '2026-10-04 00:00:00'
WHERE key = 'build_timestamp';

-- 9. Validation finale de la transaction
COMMIT;

-- 10. Détachement
DETACH DATABASE patch;
```

#### Preuve du Zéro-copie :
SQLite utilise son mécanisme standard de journalisation (rollback journal ou WAL). Seules les nouvelles pages B-Tree créées et les pages feuilles modifiées sont écrites sur disque. Pour un delta de 5 Mo représentant quelques milliers de lignes, la consommation disque temporaire est de l'ordre de **5 à 15 Mo**. Aucune copie intégrale des 3,36 Go n'a lieu.

### 4.5. Fonctions pures de vérification post-patch

```kotlin
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
            return PostPatchVerdict.Corrupt("PRAGMA quick_check a échoué")
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

### 4.6. Génération du patch dans le pipeline (`forge_cryptonite`)

Le script `step26_generate_delta.py` sera intégré aux côtés de `turbo_forge_jemma_db.py` dans `JEMMA_DB_DATA/forge_cryptonite/` :

1. **Extraction différentielle** :
   - Attache `old_base` (`2.0-omnis`) et `new_base` (`2.1-omnis`).
   - Pour chaque table clé (`atc_hierarchy`, `terminology_codes`, `ips_valuesets`, `ips_valuesets_translations`), exécute `SELECT ... FROM new EXCEPT SELECT ... FROM old`.
   - Injecte les deltas dans une base SQLite vierge `patch_2.0_to_2.1.db`.
   - Injecte les entrées `kb_provenance` correspondantes pour chaque ligne ajoutée.
2. **Compression et empreinte** :
   - Calcule le SHA-256 de `patch_2.0_to_2.1.db`.
   - Génère `kb_manifest.json` avec les post-conditions (comptes minimaux, taille, URL).
3. **Contrôle d'acceptation KBC miroir** :
   - Le script de forge applique immédiatement le patch sur une copie temporaire de la base 2.0.
   - Le script officiel de test d'acceptation `test_kb_coverage.py` (KBC-01 à KBC-06) est exécuté sur cette base patchée :
     ```bash
     KB=/tmp/patched_knowledge.db KB_OLD=/tmp/knowledge_2.0.db python3 test_kb_coverage.py
     ```
   - Le patch n'est déclaré prêt à publication que si la base patchée valide 100 % des tests KBC avec des comptes identiques à la base 2.1 complète.

### 4.7. Analyse honnête des risques et parades

1. **Concurrence de lecture pendant l'application du patch** :
   - *Risque* : SQLite lève `SQLiteDatabaseLockedException` si l'application effectue une requête pendant `BEGIN IMMEDIATE`.
   - *Parade* : `KnowledgeBaseManager` fait basculer son StateFlow en `KbState.Validating` avant d'ouvrir la connexion en écriture, suspendant temporairement les requêtes de consultation de l'interface pendant les 2 secondes de l'écriture.
2. **Espace disque insuffisant sur le téléphone** :
   - *Risque* : Échec d'allocation du journal de transaction SQLite (`disk full`).
   - *Parade* : Contrôle préalable via `JemmaDownloadStorage.freeStorageBytes()`. Le patch n'est déclenché que s'il reste au moins **100 Mo** libres (garantissant largement les ~10 Mo de journal SQLite).
3. **Coupure d'alimentation ou arrêt brutal de l'application pendant le patch** :
   - *Risque* : Base corrompue en cours de modification.
   - *Parade* : Garantie native du moteur SQLite (ACID). Le fichier journal permet à SQLite d'annuler automatiquement la transaction incomplète et de restaurer l'état exact pré-patch au redémarrage suivant.
4. **Falsification du manifeste ou altération réseau (Man-in-the-Middle)** :
   - *Risque* : Injection de faux codes ou d'interactions altérées via un point d'accès Wi-Fi compromis.
   - *Parade* : Connexion HTTPS stricte (`https://jemmapass.net`) avec validation de certificat TLS + contrôle cryptographique strict de l'empreinte SHA-256 du patch avant toute exécution SQL. *(Optionnel ultérieur : signature Ed25519 du manifeste avec clé publique embarquée dans l'APK).*
5. **Rollback en cas d'échec post-patch** :
   - *Risque* : La base est mise à jour mais les post-conditions échouent.
   - *Parade* : La transaction n'est validée par `COMMIT` **qu'après** vérification des post-conditions au sein de la transaction. En cas d'anomalie, un simple `ROLLBACK` rétablit instantanément l'état initial sans altération.

