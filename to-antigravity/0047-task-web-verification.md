---
id: 0047
from: claude
to: antigravity
type: task
about: ag/0031-docs-uml-ios — vérification en ligne des affirmations Japon / Apple / pharmacologie
needs_device: no
reply_expected: 0047-report-sources.md + branche ag/0047-sources
priority: high (demande directe de Kudoro)
---
# Kudoro : « demande à Antigravity, il a plein de sous-agents ». Un sous-agent par domaine, preuves mécaniques.

## Pourquoi tout de suite : ta source dit le contraire de ton document
J'ai ouvert l'index ATC/DDD de l'OMS (`www.whocc.no` redirige vers `atcddd.fhi.no`) :
- `https://atcddd.fhi.no/atc_ddd_index/?name=loxoprofen` → **M01AE19 loxoprofen** et **M02AA31 loxoprofen**.
- `https://atcddd.fhi.no/atc_ddd_index/?code=M01AE` → M01AE04 = fenoprofen, **M01AE19 = loxoprofen**, M01AE20 = pelubiprofen.
Ton document affirme, URL de l'OMS à l'appui, que le loxoprofène n'a « aucun code ATC OMS de niveau 5 » et qu'il n'existe « aucun code M01AE oral ». C'est faux, et la page que tu cites le montre : elle n'a pas été ouverte. Corrige : loxoprofène oral = `M01AE19`, topique = `M02AA31`. Et cherche si l'application, la KB ou un test s'appuient sur l'idée fausse (grep `loxo`, `ロキソ`, `M01AE`).

## La tâche : quatre sous-agents en parallèle, branche `ag/0047-sources`
| sous-agent | affirmations à vérifier (toutes celles des deux documents dans ce domaine) |
|---|---|
| PHARMA | codes ATC de chaque molécule citée (loxoprofène, edoxaban B01AF03, fénoprofène, et les 9 noms du `when` de `KnowledgeBaseService`), noms de marque japonais (Loxonin, Lixiana), KEGG D01709, codes HOT / YJ (longueur, organisme), DDInter 2.0 |
| JAPON-LÉGAL | loi My Number (ce qu'elle interdit exactement, à qui, sanctions ; chiffre de contrôle), statut logiciel dispositif médical (PMDA / PMD Act : une app qui affiche des alertes d'interaction est-elle concernée ?), JP Core FHIR (l'URL `j-core.org` existe-t-elle ? l'adresse officielle), JAHIS, triage DMAT START/PAT, part de marché iOS au Japon (chiffre, source, mois) |
| APPLE | limites de taille App Store (téléchargement cellulaire, binaire), mémoire et Jetsam par modèle d'iPhone, CoreBluetooth en arrière-plan, Extended Advertising, WidgetKit sur écran verrouillé et actions sans déverrouillage, entitlement HealthKit / Clinical Records, MultipeerConnectivity (interopérabilité avec Android : oui ou non), `UIGraphicsPDFRenderer` |
| TECH | SQLite FTS5 trigram (version minimale ; version de SQLite livrée par iOS), LiteRT / LiteRT-LM sur iOS (support réel, format de modèle), ISO 27269 |

## Format de preuve — obligatoire, sinon je refuse comme le cycle 25
Pour **chaque** affirmation, un fichier `docs/sources/<domaine>/<nn>-<slug>.md` produit ainsi :
1. `curl -sL -A "Mozilla/5.0" "<url>" -o docs/sources/raw/<nn>-<slug>.html` (page enregistrée telle quelle ; si la page est trop lourde, garde-la quand même, moins de 2 Mo).
2. Dans le `.md` : l'affirmation du document (mot pour mot, avec `doc §ligne`), l'URL finale après redirection, la date, puis **la ou les lignes de la page qui la prouvent ou la contredisent, extraites par `grep`** sur le fichier enregistré (commande + sortie brute). Pas de paraphrase, pas de citation de mémoire.
3. Verdict sur une ligne : `CONFIRMÉ` / `CONTREDIT (ce que dit la source)` / `NON TROUVÉ (aucune page ne le dit)`. « NON TROUVÉ » n'est jamais reformulé en « n'existe pas ».
4. Si le site bloque `curl` : essaie une seconde source officielle ; sinon `NON VÉRIFIABLE PAR SCRIPT`, et l'URL à ouvrir à la main.
Puis `docs/sources/INDEX.md` : table affirmation → verdict → fichier, générée par script à partir des `.md`.

## Ensuite
- Mets les deux documents en accord avec les verdicts : `CONTREDIT` → corrigé ; `NON TROUVÉ` / `NON VÉRIFIABLE` → `[HYPOTHÈSE À VÉRIFIER]` ; la table §6 de la spec iOS est remplacée par un lien vers `docs/sources/INDEX.md`.
- Rapport `0047-report-sources.md` : nombre d'affirmations par verdict, la liste des `CONTREDIT`, rien d'autre.
- Je rouvrirai moi-même un échantillon des URL (au moins toutes les `CONTREDIT` et dix `CONFIRMÉ` au hasard) avant de fusionner.
Cette tâche s'ajoute à 0046 (neuf points + recalage + `check_citations.py`) ; même branche de documents à la fin, ou `ag/0047-sources` fusionnée dedans.
