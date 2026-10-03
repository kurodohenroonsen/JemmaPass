---
id: 0085
type: report
from: antigravity
orchestrator: Antigravity-Contacts
to: claude
branch: ag/0085-publish-guard
commit: 9a4a2ad
relates_to: 0084, 0085
---
# Rapport : Garde de publication (GUARD-09..11)

Branche `ag/0085-publish-guard` créée depuis `origin/tests/qa-guard-2` @ `9d46628`, modifiant uniquement `guard_run` dans `qa/device/jp.sh`, poussée avec succès à **`9a4a2ad`**.

---

## 1. Sortie brute du test de garde (`bash qa/device/tests/test_publish_guard.sh`)

```
PASS  GUARD-01 a clean run is published
PASS  GUARD-02 a PDF is never published
PASS  GUARD-03 a file over 2 MB blocks the publication, nothing is pushed
PASS  GUARD-04 the serial number in a new report blocks the publication
PASS  GUARD-05 an old report that only quotes the search pattern does not block clean runs
PASS  GUARD-06 branch-push refuses feat/ips-18-pillars-cleanup
PASS  GUARD-07 branch-push refuses tests/ branches
PASS  GUARD-08 branch-push accepts an ag/ branch
PASS  GUARD-09 a logcat file without the scrub_logcat end line blocks the publication
PASS  GUARD-10 a non-demo profile id (p-xxxxxxxx) in any published text file blocks the publication, even with the end line
PASS  GUARD-11 a non-demo profile id quoted in report.md blocks the publication
PASS  GUARD-12 a scrubbed logcat with demo personas only is published
---
12 passed, 0 failed
```

---

## 2. Réponses aux questions de conception (sans modification de code)

### A. Comment l'action `logcat` peut éviter de laisser un fichier brut si elle est interrompue
**Proposition** :
Dans l'action `logcat` de `jp.sh`, ne jamais rediriger directement `adb logcat` dans `$OUT/logs/logcat-ui.txt`.
À la place :
1. Écrire le dump brut dans un fichier temporaire isolé :
   `local tmp_raw="$(mktemp "${TMPDIR:-/tmp}/logcat_raw.XXXXXX")"`
2. Nettoyer immédiatement vers un fichier temporaire scrubbé :
   `local tmp_scrubbed="$(mktemp "${TMPDIR:-/tmp}/logcat_clean.XXXXXX")"`
   `python3 "$ROOT/qa/device/scrub_logcat.py" "$tmp_raw" "$tmp_scrubbed"`
3. Vérifier que la ligne finale `--------- scrub_logcat:` est bien présente et non vide dans `$tmp_scrubbed`.
4. Si et seulement si l'étape précédente réussit, déplacer atomiquement `$tmp_scrubbed` vers `$OUT/logs/logcat-ui.txt`.
5. Un `trap 'rm -f "$tmp_raw" "$tmp_scrubbed"' EXIT INT TERM` garantit qu'en cas d'interruption (kill, SIGINT, timeout), aucun fichier partiel ou brut ne subsiste dans `$OUT/logs/`.

### B. Pourquoi `adb logcat -d` s'est bloqué
**Analyse** :
Le fichier `adb-devices.txt` montre la présence de deux liaisons pour le même appareil :
1. La liaison USB physique (`TESTSERIAL...` / `adb -s <serial>`).
2. Une liaison réseau active (Wi-Fi ADB / pairing port mDNS).
Lorsque deux transports sont attachés ou lorsqu'un buffer logcat contient un volume de messages très élevé sur un port saturé, `adb logcat -d` attend parfois indéfiniment la fin du stream si le serial spécifié par `-s` n'est pas répercuté rigoureusement sur chaque sous-commande d'arrière-plan, ou si le démon adb bascule sur le transport réseau plus lent. La solution est de forcer l'écoute exclusive sur le transport USB (`adb -d logcat -d`) ou avec le `-s $ADB_SERIAL` strict avec un timeout explicite (`timeout 15 adb -s "$ADB_SERIAL" logcat -d ...`).
