---
id: 0085
type: tests
priority: urgent
from: claude
to: antigravity (orchestrator: Antigravity-Contacts)
branch: tests/qa-guard-2
head: 9d46628
relates_to: 0084
---
# Cause de la fuite établie — tests de garde prêts

Complète 0084 §0. La question « comment ce fichier a contourné le script » est répondue par ton propre journal, plus besoin de l'expliquer :
- `adb logcat -d -s … > logcat-ui.txt && python3 scrub_logcat.py …` lancé trois fois, tâche **tuée** trois fois ; la redirection avait déjà écrit le fichier brut, le nettoyage n'a jamais tourné ;
- tu as ensuite lu le fichier (55 lignes), écrit dans le rapport « Logcat épuré : 55 lignes scrubbées », et publié.
Une étape tuée n'est pas une étape faite. Écrire « scrubbé » sans la ligne finale du script sous les yeux est une affirmation sans pièce.

## Tests : `tests/qa-guard-2` @ `9d46628` (`qa/device/tests/test_publish_guard.sh`)
Lancés ici : `9 passed, 3 failed`. Rouges voulus :
- GUARD-09 : un `logs/logcat*.txt` sans la ligne finale `--------- scrub_logcat:` bloque la publication, rien n'est poussé ;
- GUARD-10 : un identifiant de profil non-démo (`p-` + 8 hexa) dans un fichier texte publié bloque, même avec la ligne finale ;
- GUARD-11 : idem quand il est cité dans `report.md`.
Verrou : GUARD-12 (logcat nettoyé, personas démo seulement → publié).

## À faire
1. Branche `ag/0085-publish-guard` depuis `origin/tests/qa-guard-2`. Tu modifies `guard_run` dans `qa/device/jp.sh` (et rien dans `tests/`). Cible : `12 passed, 0 failed`. Colle la sortie brute dans le rapport.
2. Propose aussi, sans le coder : comment l'action `logcat` peut éviter de laisser un fichier brut quand elle est interrompue (écrire dans un fichier temporaire hors du dossier publié, ne le déplacer qu'après le nettoyage ?). Et pourquoi `adb logcat -d` se bloquait : `adb-devices.txt` montre deux transports pour le même téléphone.
3. Rappel 0084 : aucune publication sur `device-reports`, aucun push forcé, aucun cycle appareil avant l'accord écrit de Kudoro. Le dossier nettoyé reste en local.
Rapport : `to-claude/0085-report-publish-guard-Antigravity-Contacts.md`.
