---
id: 0097
type: plan
from: claude
to: antigravity (tous)
decided_by: Kudoro, 2026-10-04 06:52 (texte collé par Kudoro dans chaque orchestrateur)
relates_to: 0091, 0092, 0093, 0094, 0095, 0096
---
# Ordre de bataille — tour 2

Copie, pour mémoire, du texte que Kudoro a collé dans chaque orchestrateur le 2026-10-04 à 06:52. Le détail est dans 0091 à 0096.

## 1. Pour tous
- Lire PROTOCOL §7 bis, §10, §11, §12, puis les messages 0091 à 0096.
- État commun : feat = `f06dcd3` (CI verte, 516 tests). Vecteurs sur feat : `qa/vectors/contacts`, `bloodgroup`, `devices`. `device-reports` = `9b3b7c4`.
- Commandes : réutiliser une commande déjà autorisée au caractère près ; un script par couloir, actions nommées dans `/tmp/jp/<couloir>/task.txt` ; un passage = une exécution. Chaque rapport porte « commandes lancées ce passage : N, dont nouvelles : M ». Objectif M = 0.
- « Vert » = la suite entière, avec sa ligne de résultat brute. Un test ne se modifie pas. Rien d'affirmé sans pièce. Chacun n'écrit que dans son dossier et sa branche.
- Ce que Kudoro seul autorise reste à Kudoro : publication, push forcé, téléphone, nouvelle permission, nouvelle dépendance.
- Rapport : `to-claude/tour2-report-<Nom>.md`, avec la ligne « leçon » et la proposition `amelioration-<Nom>-0002.md` (ou « tour à vide »).

## 2. Par couloir

| Couloir | Travail du tour 2 |
|---|---|
| Antigravity-1 | Fiche secouriste : `ag/0091-rescue-allergy-line` + `tests/rescue-allergy-line` (`97c9f13`), UC-RSQ-001..009 et suite entière verts. Puis proposer où ranger le tri par criticité. Ensuite `ag/0077-ui-labels-2`. |
| Antigravity-Contacts | SD-27 et cycle 28 clos. Proposer le regroupement des actions du couloir appareil (un cycle = une exécution). Pas de nouveau cycle sans demande de Claude. |
| Antigravity-KB | Inventaire des licences refusé (`44a140c`) : le refaire selon 0090 et 0096 §3, phrase exacte de chaque licence, quatre verdicts, pas de conclusion. Puis SD-26. |
| Antigravity-Docs | Sources 0056 sur `ag/0047-sources`. Proposer le job de CI pour `check_citations.py`. |
| Antigravity-iOS | Rejouer `qa/vectors/devices/`, corriger, suite entière verte. Aucun pilier sans vecteurs. Dire si les `fullUrl` sont les `urn:uuid` d'Android. |
| Antigravity-Chrome | Ne pas corriger les écarts avec Android avant les vecteurs. Répondre sur les `fullUrl`. Écrire le script de couloir. |
| Antigravity-USB | Lecture d'un Bundle dans `core/` et rejeu Node des trois dossiers de vecteurs. Aucune capture d'écran du Mac. |
| Antigravity-Analyse | Tranche NFC `docs/functional/35-nfc.md` (0096 §4) : dossier complet sur carte, profil d'appareil à appareil. Sources citées ou `[NON VÉRIFIÉ]`. La tranche 3 attend. |
| Antigravity-UX | Tranche 2 `docs/ux/10-personas.md`. Dire quoi afficher à la place du tiret seul sous un contact sans relation. |

## 3. Fin de passage
Déposer le rapport, puis s'arrêter : Claude valide sur pièces et répond dans la boîte.
