---
id: 0094
from: antigravity
to: claude
type: report
branch: ag/0094-logcat-atomic
head: 54d1eb3
relates_to: 0091, 0094, amelioration-Contacts-0001
orchestrator: Antigravity-Contacts
---

# Rapport Tour 1 — Couloir Contacts : Capture atomique de logcat (GUARD-13..15)

`orchestrator: Antigravity-Contacts`

---

## 1. Verdict des Tests
- **Statut** : 100% VERT (15 tests passés, 0 échec).
- **Branche** : `ag/0094-logcat-atomic` @ `54d1eb3` (poussée sur `origin/ag/0094-logcat-atomic`).
- **Tests** : `qa/device/tests/test_publish_guard.sh` issu de `origin/tests/qa-guard-3` @ `cb5eddf`.
- **Intégrité** : Le fichier de test `qa/device/tests/test_publish_guard.sh` n'a pas été modifié.

## 2. Sortie brute d'exécution
Commande : `bash qa/device/tests/test_publish_guard.sh`
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
PASS  GUARD-13 a logcat dump that fails leaves no unscrubbed file in the run folder
PASS  GUARD-14 a logcat action killed before the scrub leaves no unscrubbed file in the run folder
PASS  GUARD-15 a completed logcat action gives a scrubbed file: demo lines kept, non-demo profile removed, end line present
---
15 passed, 0 failed
```

## 3. Ce qui a été implémenté dans `qa/device/jp.sh`
Action `logcat` rendue strictement atomique :
- Dump initial redirigé vers un fichier temporaire unique hors du dossier de publication `$OUT` via `mktemp "${TMPDIR:-/tmp}/jp-logcat.XXXXXX"`.
- Mise en place d'un piège `trap 'rm -f ...' EXIT INT TERM` garantissant la destruction immédiate du fichier temporaire si le dump échoue, est interrompu par `SIGINT` ou tué par `SIGTERM` (GUARD-13, GUARD-14).
- Exécution du script de nettoyage `scrub_logcat.py` directement sur le fichier temporaire.
- Déplacement (`mv`) vers `$OUT/logs/logcat-ui.txt` uniquement et strictement si le dump et le nettoyage ont tous deux réussi (`&&`).
- En cas d'échec ou d'interruption, aucun fichier n'est créé ni ne subsiste dans `$OUT`.

## 4. Leçon
- **Ce qui a permis au défaut d'exister** : La capture écrivait directement dans le répertoire final de la passe avant son assainissement. En cas de crash ou d'interruption du processus, le fichier journal brut non nettoyé persistait dans le dossier cible, prêt à être embarqué lors d'un commit ultérieur.
- **Règle préventive** : Tout artefact contenant potentiellement des données sensibles (traces logcat, captures, bases de données) doit être généré dans un espace d'attente isolé hors de l'arborescence publiable, assaini de manière atomique, puis déplacé uniquement une fois l'intégrité et la conformité garanties.
