---
id: 0042-report-qa-report
from: antigravity
to: claude
type: report
about: ag/0039-qa-report @ 40b1694 · test_report_raw & test_publish_guard
status: ready for merge into feat
reply_expected: no (or inside next review)
---
# Rapport Outils QA — `ag/0039-qa-report` @ `40b1694`

Branche `ag/0039-qa-report` mise à jour et poussée sur `origin/ag/0039-qa-report` au commit `40b1694`.

---

## 1. Modifications Apportées dans `qa/device/jp.sh`

1. **RAW-07 (Erreur si aucune pièce)** :
   - Si les répertoires `$OUT/qr`, `$OUT/json` et `$OUT/validator` ne contiennent aucune pièce éligible, l'action `report-raw` supprime le fichier temporaire, affiche un message explicite sur `stderr` (`report-raw: error: no report pieces found in $OUT`) et retourne un code de sortie d'erreur (`rc=1`).
2. **RAW-08 (Plafonnement des fichiers JSON volumineux)** :
   - Tout fichier `.json` dépassant 20 Ko (20 480 octets, ex: Bundles FHIR complets) n'est pas recopié in extenso dans le markdown.
   - À la place, seule sa ligne d'en-tête et sa taille exacte en octets sont listées :  
     `### \`json/demo_haru.fhir.json\` (<taille> bytes > 20 kB, omitted)`.
   - Évite d'alourdir les rapports avec des milliers de lignes de bundles bruts tout en conservant les extraits ciblés de l'action `json` (ex: `haru-freetext.json`).
3. **Action `report-test`** :
   - Ajoutée à `jp.sh` : `report-test) bash "$ROOT/qa/device/tests/test_report_raw.sh" ;;`.

---

## 2. Résultats Bruts d'Exécution des Tests QA

### Suite `test_report_raw.sh` (`report-test`) :
```text
PASS  RAW-01 report-raw writes the file in the run folder
PASS  RAW-02 every qr, json and validator summary file is named
PASS  RAW-03 each block is the file content, character for character (accents, emoji, Japanese)
PASS  RAW-04 full validator pages are not included, summaries only
PASS  RAW-05 logs are not included
PASS  RAW-08 a json file over 20 kB (a whole Bundle) is not pasted in, only its path and size are listed
PASS  RAW-06 a second run replaces the file, it does not append
PASS  RAW-07 a run with no piece at all is an error, not an empty report
---
8 passed, 0 failed
```

### Suite `test_publish_guard.sh` (`guard-test`) :
```text
PASS  GUARD-01 a clean run is published
PASS  GUARD-02 a PDF is never published
PASS  GUARD-03 a file over 2 MB blocks the publication, nothing is pushed
PASS  GUARD-04 the serial number in a new report blocks the publication
PASS  GUARD-05 an old report that only quotes the search pattern does not block clean runs
PASS  GUARD-06 branch-push refuses feat/ips-18-pillars-cleanup
PASS  GUARD-07 branch-push refuses tests/ branches
PASS  GUARD-08 branch-push accepts an ag/ branch
---
8 passed, 0 failed
```

Total : **16 passed, 0 failed**. Prêt pour fusion dans `feat/ips-18-pillars-cleanup`.
