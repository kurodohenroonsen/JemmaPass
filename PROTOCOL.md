# Agent mailbox — protocole Claude ⇄ Antigravity (v2)

Branche orpheline `agent-mailbox`. C'est le **seul canal** entre les deux agents.
Kudoro ne copie plus de prompts : il tape `go` dans une UI, ou les heartbeats font le relais.

## 1. Que fait un agent quand il reçoit `go` (ou à chaque heartbeat)

```
git fetch origin agent-mailbox
git checkout agent-mailbox && git pull --rebase origin agent-mailbox
```
1. Lire `PROTOCOL.md` (si son hash a changé) puis **sa boîte** : `to-antigravity/` ou `to-claude/`.
2. Boîte vide → continuer son backlog (`BACKLOG.md`) ou ne rien faire. Jamais d'invention de tâche.
3. Boîte non vide → traiter les messages **dans l'ordre des numéros**, un par un.
4. Pour chaque message traité, **un seul commit** sur `agent-mailbox` qui :
   - ajoute la réponse dans la boîte de l'autre (`to-claude/NNNN-…md` / `to-antigravity/NNNN-…md`) ;
   - **supprime** le message traité de sa propre boîte (`git rm`) — la boîte est une file
     d'attente, l'historique git fait office d'archive ;
   - met à jour **son** fichier d'état (`state/claude.md` ou `state/antigravity.md`).
5. `git pull --rebase origin agent-mailbox && git push origin agent-mailbox`.
   Chaque agent n'écrit que dans la boîte de l'autre et dans son propre `state/…` :
   aucun conflit possible. En cas de rejet du push : `pull --rebase` puis re-push, jamais `--force`.

## 2. Format d'un message

Nom : `NNNN-<type>-<slug>.md` — `NNNN` = numéro de cycle (4 chiffres), types : `task`, `report`,
`question`, `ack`. En-tête obligatoire :

```
---
id: 0023
from: claude            # ou antigravity
to: antigravity
type: task
commit: <sha court de feat/ips-18-pillars-cleanup à tester, ou ->
needs_device: yes|no
reply_expected: report
---
```
Corps : objectif en 1 phrase, étapes numérotées, « attendu », preuves demandées.
Un `report` ne recopie pas le rapport complet : il donne le verdict, le chemin du dossier publié
sur `device-reports`, le commit, et **les seules choses que l'autre doit décider** (bugs, questions).
Le détail vit dans `device-reports` (règle 8 : sorties brutes, fichiers publiés).

## 3. Qui possède quoi (aucun agent n'écrit chez l'autre)

| Branche | Propriétaire | L'autre |
|---|---|---|
| `feat/ips-18-pillars-cleanup` (code, tests, qa/, docs/) | Claude | lecture seule — Antigravity n'y commit JAMAIS |
| `device-reports` (preuves device, galerie d'écrans, guide) | Antigravity | lecture seule |
| `agent-mailbox` | les deux, selon §1 | |
| `ci-logs` | CI | lecture seule |

## 4. L'agent principal est un ORCHESTRATEUR — il ne fait aucun travail lui-même

Son rôle se limite à 4 gestes : **lire** le message, **instancier** un sous-agent par bloc,
**attendre**, **assembler + pousser**. Il n'analyse pas d'images, ne lance pas adb, n'écrit pas de
rapport de mémoire.

1. Chaque `task` contient une table `blocs` : `id · sous-agent · dépend de · commande/consigne ·
   écrit uniquement dans · terminé quand`. L'orchestrateur n'a rien à décider.
2. **Tous les blocs sans dépendance démarrent en même temps**, dans le même tour ; un bloc qui
   dépend d'un autre démarre dès que le fichier attendu existe (pas d'attente « par prudence »).
3. Un sous-agent = **un répertoire de sortie à lui seul** + au plus une ressource exclusive.
   Le téléphone (adb) est la seule ressource exclusive : un seul sous-agent `DEVICE` à la fois ;
   tout le reste est parallèle sans limite.
4. Chaque sous-agent termine par `lanes/<id>.md` : 5 lignes max (verdict, fichiers produits,
   anomalies). Il ne résume pas les sorties : elles sont déjà dans des fichiers (règle 8).
5. **Scripts d'abord** : décodage QR, validateur, nettoyage, galerie ont un outil en une commande
   (`qa/device/README.md` §3 bis). Interdit de réécrire ces boucles à la main ou de classer des
   captures en les « regardant ».
6. L'orchestrateur assemble `report.md` en concaténant les `lanes/*.md` + les sorties brutes
   demandées, pousse **une fois** sur `device-reports`, puis répond dans la mailbox.
7. Budget : un bloc qui dépasse 3× sa durée indicative est arrêté et signalé `⏱` dans le rapport,
   les autres blocs continuent.

Côté Claude, même règle : les blocs de code à fichiers disjoints partent en sous-agents parallèles,
Claude intègre, CI verte, puis `task`.

## 5. Heartbeat

- **Claude** : tant qu'un `task` est dehors, se réveille toutes les 15 min, fait §1, et s'arrête
  après 8 réveils à vide (2 h) — un `go` de Kudoro le relance.
- **Antigravity** : si ta version propose les tâches planifiées, crée
  `/schedule every 10 minutes: applique §1 du PROTOCOL.md de la branche agent-mailbox, jusqu'à 12 fois` ;
  sinon Kudoro tape `go`. Un réveil à boîte vide ne produit **aucun** commit.

## 6. Nettoyage au fil de l'eau (GitHub reste léger)

- **Mailbox** : un message traité est supprimé (§1). Seuls restent `PROTOCOL.md`, `BACKLOG.md`,
  `state/` et les messages en attente.
- **device-reports** : dès que Claude envoie l'`ack` d'un cycle, Antigravity réduit le dossier du
  run à `report.md` + `screenshots/` (suppression de `logs/`, `files/`, `validator/`, `kb/`, `qr/`,
  `verify-*.md`, `steps.md`, `env.txt`, `lanes/`). Commit normal, jamais de réécriture d'historique.
- **On garde toujours les écrans** : ils servent à la documentation, au guide utilisateur et à
  l'équipe design. Le couloir `DOCS` tient une galerie `screens/<pilier>/<NN>-<écran>.png` +
  `screens/INDEX.md` (pilier, écran, langue, cycle et commit d'origine). Une copie dans la galerie
  est un document, pas une preuve : la règle 6 (pas de capture recyclée comme preuve) reste entière.
- Les règles 1–8 de `qa/device/README.md` (branche code) restent en vigueur.
