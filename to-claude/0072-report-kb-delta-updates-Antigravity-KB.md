---
id: 0072
type: report
from: antigravity
orchestrator: Antigravity-KB
to: claude
lane: KB
branch: ag/0055-kb-build
commit: 7f2cd4557f4b7c4aa87c5dfa0f9839ffbec467d9
relates_to: 0068, 0071, 0072
---
# Conception des correctifs différentiels KB (PROTOCOL §9.2), audit serveur & résolution SD-25

Document complet de conception livré sur `ag/0055-kb-build` @ `7f2cd45` : `docs/analysis/kb-next-plan.md`.  
Correction de l'URL réelle appliquée dans `docs/analysis/kb-only-migration.md:100`.

---

## 1. Audit serveur et clarifications de sources (0068)

### En-têtes HTTP bruts de `https://jemmapass.net/models/knowledge_full.db` (`curl -sSI`) :
```http
HTTP/2 200 
date: Sat, 03 Oct 2026 15:17:13 GMT
content-length: 3360727040
server: Apache
last-modified: Sun, 17 May 2026 00:55:37 GMT
accept-ranges: bytes
```
- Le serveur distribue bien la **Base B (3 360 727 040 octets)**, identique à la base installée sur le téléphone.
- Aucun en-tête `ETag` n'est envoyé par le serveur.

### Emplacements du code pour l'URL, la version et `sizeInBytes` :
- `downloads/JemmaModelCatalog.kt:33` : `BASE_URL = "https://jemmapass.net/models"`
- `downloads/JemmaModelCatalog.kt:87` : `url = "$BASE_URL/knowledge_full.db"` (URL non versionnée)
- `downloads/JemmaModelCatalog.kt:86` : `version = "1.1"`
- `downloads/JemmaDownloadStorage.kt:63` : stockage `{externalFilesDir}/knowledge_full_db/1.1/knowledge_full.db`
- `downloads/JemmaDownloadStorage.kt:84` : `onDisk >= (expected * 0.95).toLong()` (contrôle de validation 95%, faille SD-25)
- `downloads/JemmaDownloadCoordinator.kt:71,103,207,211,221,359` : calculs de progression de téléchargement
- `ui/settings/SettingsCardBinders.kt:82,211`, `SettingsFragment.kt:396` : affichage formaté ("3,13 Go")
- `ai/gemma/GemmaSession.kt:96,147` : logs de démarrage

---

## 2. Analyse de sécurité SD-25 : Faille des 95 % (0071)

### Comportement d'une base tronquée :
1. `SQLiteDatabase.openDatabase(..., OPEN_READONLY)` réussit car les pages d'en-tête SQLite et de schéma se situent en tête de fichier.
2. `readBuildMetadata(db)` renvoie `2.0-omnis` sans erreur.
3. `KnowledgeBaseManager` bascule en `KbState.Ready` (vert).
4. **Impact clinique majeur** : Les requêtes d'interactions ou de contre-indications touchant les pages tronquées (jusqu'à 168 Mo coupés) renvoient 0 résultat. Le moteur de sécurité interprète l'absence de résultat comme une absence de danger $\rightarrow$ **verdict `CLEAN` (vert) sur un profil présentant un risque vital !**

### Fonction pure proposée (testable en JVM) :
```kotlin
fun downloadVerdict(
    onDiskBytes: Long,
    expectedBytes: Long,
    onDiskSha256: String?,
    expectedSha256: String?,
): DownloadVerdict
```
- `COMPLETE` : `onDiskBytes == expectedBytes` ET SHA-256 valide.
- `TRUNCATED` : `onDiskBytes < expectedBytes`.
- `CORRUPT` : taille anormale ou SHA-256 divergent.
- `UNVERIFIED` : taille exacte mais empreinte absente (jamais `COMPLETE` sans contrôle d'empreinte).

L'empreinte attendue provient du manifeste distant `kb_manifest.json` (avec empreinte bootstrap intégrée dans le code pour la première installation).

---

## 3. Conception de la mise à jour différentielle (PROTOCOL §9.2 / Tâche 0072)

### Comparaison des formats candidats :
1. **Candidat 1 : SQLite Delta (`.db`) via `ATTACH` [RETENU]** :
   - Requête : `ATTACH delta.db AS patch; BEGIN IMMEDIATE TRANSACTION; INSERT OR IGNORE INTO main.T SELECT * FROM patch.T; ... COMMIT; DETACH patch;`
   - **FTS5** : 100 % compatible nativement via `INSERT INTO main.terminology_latin SELECT ... FROM patch.terminology_latin`.
   - **Disponibilité Android** : 100 % opérationnel dans `requery/sqlite-android:3.49.0`.
   - **Taille** : ~2 à 4 Mo (< 1,5 Mo compressé sur le réseau).
   - **Durée** : < 3 secondes sur device.
   - **Espace disque** : ~10 Mo de rollback journal, **zéro copie des 3,36 Go**.
   - **Atomicité** : Garantie transactionnelle tout-ou-rien par SQLite.
2. **Candidat 2 : Script SQL (`.sql.gz`)** :
   - Trop lent (parsing de milliers de commandes SQL textuelles) et gestion lourde des chaînes FTS.
3. **Candidat 3 : SQLite Session Extension (`changeset`)** :
   - **Inapplicable** : Ni binding JNI dans requery, et **incompatible par construction avec SQLite FTS5** (la doc officielle SQLite stipule que les tables virtuelles FTS sont formellement ignorées par la session extension).

### Manifeste distant (`https://jemmapass.net/models/kb_manifest.json`) :
- Déclare `latest_version`, l'URL du téléchargement complet avec taille et SHA-256, et la liste chaînée des `patches` (`from_version`, `to_version`, `url`, `size_bytes`, `sha256`, `post_conditions`).
- Consultation en tâche de fond passive (timeout 3s). Si hors-ligne ou erreur, la base existante reste active immédiatement sans blocage.

### Fonctions pures prévues pour tests JVM de Claude :
- `PatchChainResolver.resolveChain(currentVersion, targetVersion, availablePatches): List<PatchDescriptor>?`
- `DownloadIntegrity.downloadVerdict(onDiskBytes, expectedBytes, onDiskSha256, expectedSha256): DownloadVerdict`
- `PatchVerifier.verifyPostConditions(db, minCodes, quickCheck): Boolean`

### Matrice des risques traitée :
- Concurrence de lecture $\rightarrow$ `KbState.Validating` posé pendant les 2 secondes d'application.
- Espace disque faible $\rightarrow$ pré-contrôle `freeStorageBytes() >= 100 Mo`.
- Coupure brutale $\rightarrow$ rollback atomique garanti par le moteur SQLite.

Prêt pour relecture et décision par Kudoro, puis écriture des tests par Claude.
