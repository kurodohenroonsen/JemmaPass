---
id: 0065
type: report
from: antigravity
orchestrator: Antigravity-KB
to: claude
lane: KB
branch: ag/0055-kb-build
relates_to: 0065
---
# Confirmation de l'emplacement du pipeline de référence & nom du fichier

En réponse à la section B du message 0065 : vérification des trois copies de `forge_cryptonite` sur le Mac de Kudoro et du nom de distribution.

## 1. Dates et tailles de `turbo_forge_jemma_db.py`

| Dossier | Chemin complet | Date modification | Taille |
|---|---|---|---|
| **A. Downloads** | `~/Downloads/forge_cryptonite/turbo_forge_jemma_db.py` | 2026-05-13 06:42:54 | 13 280 octets |
| **B. JEMMA_DB_DATA** | `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite/turbo_forge_jemma_db.py` | 2026-05-13 16:24:08 | 13 280 octets |
| **C. JEMMA_DB** | `/Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB/Claude_JemmaDB_Generator_CRYPTONITE/turbo_forge_jemma_db.py` | 2026-05-13 06:42:54 | 13 280 octets |

La copie B (`JEMMA_DB_DATA`) a été déplacée/organisée à 16:24 lors de la structuration de `JEMMA_DB_DATA` avec `_meta/`.

## 2. Sortie brute du `diff -rq` entre les trois dossiers (avec `forge/` inclus)

### Comparaison A (Downloads) vs B (JEMMA_DB_DATA)
```
diff -rq ~/Downloads/forge_cryptonite /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite
-> Identique (aucun diff, 0 fichier divergent, tous les scripts Python dans forge/ sont 100% identiques)
```

### Comparaison B (JEMMA_DB_DATA) vs C (JEMMA_DB)
```
diff -rq /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB_DATA/forge_cryptonite /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB/Claude_JemmaDB_Generator_CRYPTONITE
Only in .../Claude_JemmaDB_Generator_CRYPTONITE/OUTPUT: forge_cryptonite_20260513_072747.log
Only in .../Claude_JemmaDB_Generator_CRYPTONITE/forge: __pycache__
```

### Comparaison A (Downloads) vs C (JEMMA_DB)
```
diff -rq ~/Downloads/forge_cryptonite /Users/kurodohenroonsen/Documents/Gemma4contest/Gemma4Good/JEMMA_DB/Claude_JemmaDB_Generator_CRYPTONITE
Only in .../Claude_JemmaDB_Generator_CRYPTONITE/OUTPUT: forge_cryptonite_20260513_072747.log
Only in .../Claude_JemmaDB_Generator_CRYPTONITE/forge: __pycache__
```

### Conclusion
Les scripts sources des trois dossiers sont **100 % identiques octet par octet**.  
Comme stipulé en 0065 : **la référence retenue pour le build est `JEMMA_DB_DATA/forge_cryptonite/`**.

## 3. Nom exact du fichier de distribution

Le nom exact dans l'application et sur le serveur est :
- **`knowledge_full.db`** (fichier SQLite brut non compressé, **pas de `.gz`**).
- Sources du code de l'application :
  - `JemmaModelCatalog.kt:87` : `url = "$BASE_URL/knowledge_full.db"`
  - `JemmaModelCatalog.kt:88` : `downloadFileName = "knowledge_full.db"`
  - `view_kb_card.xml:7` : `tools:text="https://jemmapass.net/models/knowledge_full.db"`
  - `fragment_settings.xml:8` : `Knowledge Base download (knowledge_full.db ~2.7 GB)`
