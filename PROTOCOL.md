# Agent mailbox — protocole Claude ⇄ Antigravity (v1)

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

## 4. Micro-blocs et sous-agents (ne jamais se marcher dessus)

Règle unique : **un sous-agent = un répertoire de sortie qui n'appartient qu'à lui**, et une seule
ressource exclusive. L'agent principal est l'**intégrateur** : lui seul assemble et pousse.

### Antigravity (Agent Manager / sous-agents, chacun dans son worktree)
| Couloir | Ressource exclusive | Écrit uniquement dans | Dépend de |
|---|---|---|---|
| `DEVICE` | le téléphone (adb) — **un seul agent à la fois** | `$OUT/screenshots/`, `$OUT/logs/`, `$OUT/files/`, `$OUT/qr/` | — |
| `FHIR` | validateur HL7 (hôte) | `$OUT/validator/` | `$OUT/files/` (attend la fin du seed) |
| `KB` | copie de la KB hors dépôt | `$OUT/kb/` | — (parallèle dès le début) |
| `DOCS` | — | `screens/`, `guide/` sur `device-reports` | captures du couloir DEVICE |
| intégrateur | `git push` | `$OUT/report.md`, `reports/cycle-NN-*.md`, la mailbox | tous |

`KB` et le build démarrent en parallèle ; `FHIR` démarre dès que `files/` existe ; `DEVICE` déroule
le protocole UI pendant que `FHIR` tourne (le validateur prend plusieurs minutes par persona :
lance les 3 personas en parallèle). Chaque couloir rend un `lanes/<couloir>.md` (verdict + chemins) ;
l'intégrateur n'écrit `report.md` qu'à partir de ces fichiers et des sorties brutes.

### Claude (sous-agents en worktrees isolés)
Blocs à fichiers disjoints : `domain+codec+tests` / `strings ×6 + layouts` / `catalogues + i18n` /
`qa kit + docs`. Claude intègre, lance la CI et ne publie un `task` qu'avec une CI verte.

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
