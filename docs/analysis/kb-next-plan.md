# Plan de Construction & Conception des Mises à Jour de la KB 2.1-omnis

> **Document de Conception & Spécification Technique**  
> **Orchestrateur** : `Antigravity-KB` (Couloir KB)  
> **Branche de travail** : `ag/0055-kb-build`  
> **Références** : Messages boîte 0055, 0058, 0064, 0068, 0071, 0072 ; PROTOCOL §9, §9.1, §9.2  
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
- **Pipeline de référence pour le build** : `JEMMA_DB_DATA/forge_cryptonite/` (`turbo_forge_jemma_db.py`), confirmé 100 % identique aux autres copies en message 0065.

---

## 2. Audit du Serveur de Distribution et Emplacement de `sizeInBytes` (0068)

### 2.1. Preuve brute par en-têtes HTTP (`curl -sSI`)

Exécution du 3 octobre 2026 :
```http
HTTP/2 200 
date: Sat, 03 Oct 2026 15:17:13 GMT
content-length: 3360727040
server: Apache
last-modified: Sun, 17 May 2026 00:55:37 GMT
accept-ranges: bytes
```

**Constats sans ambiguïté** :
1. `content-length` est exactement **3 360 727 040 octets** : le site sert la **Base B** (la base du téléphone), et non la base A.
2. Le serveur ne retourne aucun en-tête `ETag`.

### 2.2. Utilisation de `sizeInBytes` dans l'application

Inventaire exhaustif des usages de `sizeInBytes` (`JemmaModelCatalog.knowledgeBase.sizeInBytes = 3_360_727_040L`) :

1. **Validation d'intégrité (seul contrôle existant)** :
   - `JemmaDownloadStorage.kt:81,84` :
     ```kotlin
     val expected = model.sizeInBytes
     val ok = onDisk >= (expected * 0.95).toLong()
     ```
     L'application n'exige **pas** une taille exacte : elle valide tout fichier occupant au moins 95 % de la taille catalogue.
2. **Coordination des téléchargements & suivi de progression** :
   - `JemmaDownloadCoordinator.kt:71,103,207,211,221,359,360` : calcul du pourcentage d'avancement, comparaison avec les octets reçus du `DownloadWorker`.
3. **Affichage dans l'interface utilisateur** :
   - `SettingsCardBinders.kt:82,211`, `SettingsFragment.kt:396`, `SettingsViewModel.kt:267` : formatage en chaîne lisible (`Formatter.formatFileSize(..., model.sizeInBytes)` $\rightarrow$ `"3,13 Go"`).
4. **Journalisation** :
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
3. **Comptage des lignes (`KnowledgeBaseManager.kt:250`)** :
   - `countTable(db, "terminology_codes")` exécute `SELECT COUNT(*)`. Selon la structure de l'arbre B-Tree, le compte peut être lu directement depuis des pages d'index non tronquées ou s'interrompre sans lever d'erreur si les pages tronquées se situent dans les tables d'interactions ou FTS5.
4. **État exposé à l'utilisateur** :
   - L'application bascule en **`KbState.Ready`** (boîte verte).
   - **Conséquence clinique catastrophique** : Les contrôles croisés (`KbCrossCheck.kt`) ou requêtes d'interactions (`ddi_facts`) sur les données situées dans les 168 Mo manquants renvoient silencieusement **0 résultat**.
   - Le moteur interprète 0 résultat comme une absence d'interaction : **l'utilisateur et le médecin voient un écran vert `CLEAN` ("Aucun risque détecté") au lieu d'une alerte vitale !**

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
     * Évalue l'intégrité d'un téléchargement sans tolérance d'amputation.
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

| Critère | Candidat 1 : SQLite Delta (`.db`) via `ATTACH` **[RETENU]** | Candidat 2 : Script SQL (`.sql` / `.sql.gz`) | Candidat 3 : SQLite Session Extension (`changeset`) |
|---|---|---|---|
| **Mécanisme** | Fichier SQLite auxiliaire attaché à chaud : `ATTACH 'delta.db' AS delta; INSERT OR IGNORE INTO main.T SELECT * FROM delta.T;` | Fichier texte exécuté ligne par ligne via `execSQL()` dans une transaction. | Changements binaires appliqués via l'API C SQLite Session. |
| **Compatibilité FTS5** | **Excellente** : Les tables virtuelles FTS5 acceptent nativement `INSERT INTO main.fts SELECT ... FROM delta.fts`. | **Moyenne** : Lourdeur des instructions FTS en texte brut échappé. | **IMPOSSIBLE** : La documentation officielle SQLite exclut explicitement les tables virtuelles (FTS5 ignoré). |
| **Disponibilité Android** | **100 % disponible** dans `requery/sqlite-android:3.49.0` (SQL pur). | **100 % disponible** via `execSQL()`. | **ABSENTE** : Aucun binding JNI pour `sqlite3session_*` dans requery ni dans le SDK Android standard. |
| **Taille réseau (`2.0` $\rightarrow$ `2.1`)** | **~2 à 4 Mo** (compressé en gzip/zstd sur le serveur : **< 1,5 Mo**). | ~3 à 6 Mo en SQL texte brut (~1 Mo compressé). | Inapplicable (FTS5 manquant). |
| **Durée d'application sur device** | **< 3 secondes** (pages binaires injectées directement par SQLite). | 10 à 30 secondes (parsing syntaxique SQL de milliers de lignes). | Inapplicable. |
| **Espace disque temporaire** | **~10 Mo** (rollback journal proportionnel au delta, aucun clone des 3,36 Go). | ~15 Mo. | Inapplicable. |
| **Atomicité (Tout ou rien)** | **Garantie 100 %** par une transaction unique `BEGIN IMMEDIATE ... COMMIT`. | Garantie si enveloppé dans une transaction. | Inapplicable. |

> **Décision d'architecture** : Le **Candidat 1 (Fichier SQLite Delta `patch_X_to_Y.db`)** est la seule solution viable, performante et compatible avec FTS5 sans compilation de librairie native sur mesure.

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
        "min_terminology_codes": 95200,
        "min_ips_translations": 6500,
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
    val minCodesCount: Long,
)

object PatchChainResolver {

    /**
     * Résout la suite ordonnée de patchs menant de [currentVersion] à [targetVersion].
     * Renvoie une liste vide si la version actuelle est déjà à jour,
     * ou null si aucun chemin continu n'existe (exigeant alors un téléchargement complet).
     */
    fun resolveChain(
        currentVersion: String,
        targetVersion: String,
        availablePatches: List<PatchDescriptor>,
    ): List<PatchDescriptor>? {
        if (currentVersion == targetVersion) return emptyList()

        val chain = mutableListOf<PatchDescriptor>()
        var cursor = currentVersion

        while (cursor != targetVersion) {
            val nextPatch = availablePatches.firstOrNull { it.fromVersion == cursor } ?: return null
            chain.add(nextPatch)
            cursor = nextPatch.toVersion
            if (chain.size > 20) return null // Protection contre les cycles
        }

        return chain
    }
}
```

### 4.4. Exécution atomique du patch sur le téléphone

L'application du patch s'exécute directement sur le fichier SQLite en place, sans duplication des 3,36 Go :

```sql
-- 1. Attacher la base de patch téléchargée et vérifiée par SHA-256
ATTACH DATABASE '/data/user/0/.../cache/patch_2.0_to_2.1.db' AS patch;

-- 2. Transaction atomique exclusive (bloque toute lecture concurrente)
BEGIN IMMEDIATE TRANSACTION;

-- 3. Insertion additive des nouveaux codes (KBC-03 : aucun renommage ni suppression)
INSERT OR IGNORE INTO main.terminology_codes (code, system, display, category)
SELECT code, system, display, category FROM patch.terminology_codes;

-- 4. Insertion des nouvelles traductions
INSERT OR IGNORE INTO main.ips_valuesets_translations (valueset_uri, code, lang, display)
SELECT valueset_uri, code, lang, display FROM patch.ips_valuesets_translations;

-- 5. Mise à jour de l'index FTS5 latin
INSERT INTO main.terminology_latin (rowid, code, display, category)
SELECT rowid, code, display, category FROM patch.terminology_latin;

-- 6. Mise à jour de l'index FTS5 CJK
INSERT INTO main.terminology_cjk (rowid, code, display, category)
SELECT rowid, code, display, category FROM patch.terminology_cjk;

-- 7. Mise à jour des métadonnées de version
UPDATE main.build_metadata
SET build_version = '2.1-omnis',
    build_timestamp = '2026-10-04T00:00:00Z'
WHERE rowid = (SELECT rowid FROM main.build_metadata LIMIT 1);

-- 8. Validation finale de la transaction
COMMIT;

-- 9. Détachement
DETACH DATABASE patch;
```

#### Post-conditions vérifiées avant libération :
- Exécution de `PRAGMA quick_check` $\rightarrow$ doit renvoyer `"ok"`.
- `SELECT build_version FROM build_metadata` $\rightarrow$ doit renvoyer `"2.1-omnis"`.
- `SELECT COUNT(*) FROM terminology_codes` $\ge$ `post_conditions.min_terminology_codes`.
- Si l'une des post-conditions échoue : la transaction est annulée (`ROLLBACK`), le patch est supprimé et l'état reste sur l'ancienne version saine.

### 4.5. Génération du patch dans le pipeline (`forge_cryptonite`)

Le script `forge_delta.py` sera intégré aux côtés de `turbo_forge_jemma_db.py` dans `JEMMA_DB_DATA/forge_cryptonite/` :

1. **Extraction différentielle** :
   - Compare `knowledge_full_2.0.db` et `knowledge_full_2.1.db`.
   - Extrait les lignes ajoutées dans `terminology_codes`, `ips_valuesets_translations`, et les tables FTS5.
   - Injecte ces lignes dans une nouvelle base SQLite vierge `patch_2.0_to_2.1.db`.
2. **Compression et empreinte** :
   - Calcule le SHA-256 de `patch_2.0_to_2.1.db`.
   - Génère `kb_manifest.json`.
3. **Contrôle d'acceptation KBC miroir** :
   - Le script de forge applique immédiatement le patch sur une copie temporaire de la base 2.0.
   - Les tests KBC (KBC-01 à KBC-06) sont joués sur cette base patchée.
   - Le patch n'est déclaré prêt à publication que si la base patchée valide 100 % des tests KBC avec des comptes identiques à la base 2.1 complète.

### 4.6. Analyse honnête des risques et parades

1. **Concurrence de lecture pendant l'application du patch** :
   - *Risque* : SQLite lève `SQLiteDatabaseLockedException` si l'agent ou un écran lit la base au même moment.
   - *Parade* : `KnowledgeBaseManager` fait basculer son StateFlow en `KbState.Validating` avant d'ouvrir la transaction d'écriture, ce qui met en attente les écrans de consultation pendant les 2 secondes de l'écriture.
2. **Espace disque insuffisant sur le téléphone** :
   - *Risque* : Échec d'allocation du journal de transaction SQLite (`disk full`).
   - *Parade* : Contrôle préalable via `JemmaDownloadStorage.freeStorageBytes()`. Le patch n'est déclenché que s'il reste au moins **100 Mo** libres (garantissant largement les ~10 Mo de rollback journal).
3. **Coupure d'alimentation ou arrêt brutal de l'application pendant le patch** :
   - *Risque* : Base corrompue en cours de modification.
   - *Parade* : Garantie native du moteur SQLite (ACID). Le fichier de rollback journal permet à SQLite de restaurer l'état pré-transactionnel exact au redémarrage suivant.
4. **Falsification du manifeste ou altération réseau (Man-in-the-Middle)** :
   - *Risque* : Injection de faux codes ou d'interactions erronées.
   - *Parade* : Connexion HTTPS stricte (`https://jemmapass.net`) avec validation de certificat + contrôle cryptographique strict de l'empreinte SHA-256 du patch avant toute exécution SQL.
