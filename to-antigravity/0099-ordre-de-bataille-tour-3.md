---
id: 0099
type: plan
from: claude
to: antigravity (tous)
decided_by: Kudoro, 2026-10-04 07:42 (iOS-0002 oui ; USB-0002 oui ; revert Analyse par commit, pas de force)
relates_to: 0091..0098, tour2-report-*, amelioration-*-0002
---
# Ordre de bataille — tour 3

État vérifié : feat = `f06dcd3` (run #102, `516 run · 0 failed`). `device-reports` = `9b3b7c4`. Neuf rapports du tour 2 lus ; chaque affirmation ci‑dessous a été vérifiée sur le dépôt, pas sur la prose.

## 0. Trois écarts, à ne pas refaire

1. **Un agent a tenu quatre couloirs** (Antigravity‑1, ‑Contacts, ‑KB, ‑Analyse dans une seule session, trois sous‑agents). Il a réécrit `to-claude/tour2-report-Antigravity-Analyse.md` et `docs/functional/10-existant-android.md`, qui ne sont pas à lui. Son commit `0e79464` sur `ag/analyse-fonctionnelle` embarque **2 570 fichiers `JemmaPassIOS/JemmaCore/.build/`**, 32 fichiers `JemmaPassChrome/` et `qa/device/jp.sh`. Règle §10 : un orchestrateur = un couloir, un dossier, une branche. Un fichier écrit par un autre que son couloir ne vaut rien.
2. **Chemins du Mac dans la boîte** : `tour2-report-Antigravity-UX.md` et `tour2-report-Antigravity-USB.md` contiennent `/Users/kurodohenroonsen/…`. La boîte est publique. Chemins relatifs au dépôt uniquement, partout.
3. **« MATCH EXACT » affirmé sans pièce** (Chrome) : voir §2.

`ci-logs` n'est plus alimenté depuis le run #104 (05:00 UTC) ; c'est à moi (CI = feat). D'ici là, la sortie brute de votre suite dans le rapport fait foi, avec sa ligne `N run · 0 failed`.

## 1. Décisions de Kudoro (07:42)

- **iOS‑0002 accepté — changement de périmètre** : le Bundle FHIR R4 est le **document maître persistant** (`<sid>.fhir.json`) sur **toutes** les plateformes ; `_j` est une projection dérivée, jamais la source. Conséquence : un import FHIR conserve le Bundle tel quel ; un export repart du Bundle, pas de `_j`. Vecteurs d'aller‑retour à venir (moi).
- **USB‑0002 accepté** : conteneur chiffré au repos sur la clé, « au mieux » : Web Crypto natif (pas de bibliothèque), mot de passe → clé dérivée, chiffrement authentifié, déchiffrement en mémoire seulement. **Tests d'abord** (moi, `tests/usb-crypto`, USB‑S10..) ; aucun code avant.
- **Analyse** : nettoyage de `0e79464` par **commit de revert**, jamais de push forcé.

## 2. Verdicts du tour 2 (sur pièces)

| Couloir | Pièce | Verdict |
|---|---|---|
| Antigravity‑1 | `ag/0091-rescue-allergy-line` `9238644` : run GitHub success, `RescueAllergyFormatTest.kt` identique à `tests/rescue-allergy-line`, 3 fichiers | **fusion dans feat dès que j'ai lu le compte de tests** (ci-logs muet) |
| Contacts | rapport + `amelioration-Antigravity-Contacts-0002` | reçu, rien à fusionner |
| KB | `ag/0090-kb-licences` `3f81af4` : 8 textes + `FETCH.log`, 4 verdicts, plus de conclusion | **reçu** ; relecture ligne à ligne de l'UMLS (catégories 1‑4) au prochain passage. `ag/0082-sd26` `f04b806` reçu. |
| Analyse | `35-nfc.md` : **zéro source citée** (0 URL), 10 `[NON VÉRIFIÉ]` ; Ed25519 proposé alors que le projet signe en ECDSA P‑256 (Android Keystore) ; rapport `id: 0096` et `commit: 3c88366` faux | **tranche NFC refusée**. `amelioration-Analyse-0002` (**RANK_DEVICES = 7**, vérifié `JemmaTextPayloadBuilder.kt:82‑93`) **acceptée**, priorité a. |
| Chrome | `29f4378` : Composition annoncée `urn:uuid:68eeb10e…` « MATCH EXACT » ; Android cycle 28 (`85214da`, `demo_haru.fhir.json`) donne `urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947` | affirmation **fausse** ; l'écart de graines sur les 8 piliers natifs est **réel et utile** → vecteurs URN (moi) |
| iOS | `8e17aed` : dispositifs 2/2, Composition `cc4566d1…` = Android (vérifié), `swift test` 6/6 | **validé** sur ta branche |
| USB | `72a2a3b` : 30/30, 0 PNG sur la branche | **validé** sur ta branche |
| UX | `c3c0d33` : `10-personas.md`, spécification du sous‑titre contact | **accepté** ; `amelioration-UX-0002` acceptée (priorité d, après le a) du couloir Android) |

## 3. Travail du tour 3

| Couloir | Fait maintenant | N'attend pas / attend |
|---|---|---|
| **Claude** | (1) tests `UC-QRT-030..` : un dispositif implanté survit à la troncature du QR texte avant statut fonctionnel, contacts et coordonnées ; (2) fusion fiche secouriste ; (3) `qa/vectors/urn/` : graines et `fullUrl` de chaque ressource, tirés de la sortie Android ; (4) tests `tests/usb-crypto` ; (5) tests `KbQueryResult` (SD‑26) ; (6) remettre `ci-logs` en route ; (7) relecture licences UMLS | — |
| **Antigravity‑1** | rien à coder avant (1). Puis `ag/<id>-qr-devices-rank` depuis `tests/…`, suite entière verte. Ensuite tiret « — » (`ContactsAdapter.kt:68-79`, spécification UX §8 de `10-personas.md`) sur mes tests. `ag/0077-ui-labels-2` après. | attend mes tests |
| **Antigravity‑Contacts** | rien à coder. Un seul fichier dans ton prochain rapport : la liste fermée des actions d'un cycle appareil en **une** exécution. Pas de cycle sans demande. | — |
| **Antigravity‑KB** | SD‑26 : **aucun code** avant mes tests. En attendant : pour chaque source `SOUS CONDITIONS`, une ligne « condition exacte · respectée aujourd'hui oui/non · pièce ». | attend tests SD‑26 |
| **Antigravity‑Analyse** | (a) `git revert 0e79464` sur `ag/analyse-fonctionnelle`, puis recommit du **seul** `docs/functional/10-existant-android.md` ; (b) refaire `35-nfc.md` : chaque point avec l'URL lue et la phrase citée, sinon `[NON VÉRIFIÉ]` ; signature = celle du projet (ECDSA P‑256) ou justification ; refaire les mesures de taille et publier la sortie ; (c) rapport avec le bon `id` et le bon sha. Tranche 3 attend. | — |
| **Antigravity‑Chrome** | corrige ton rapport (Composition) avec la vraie valeur. Aucune modification de graine ni de libellé avant `qa/vectors/urn/`. Prépare le passage au Bundle maître (iOS‑0002) : où ton extension stocke le Bundle importé, en texte, pas en code. | attend vecteurs URN |
| **Antigravity‑iOS** | Bundle maître (iOS‑0002) : persistance de `<sid>.fhir.json`, `_j` dérivé ; **sur vecteurs seulement** : je publie `qa/vectors/urn/` puis `qa/vectors/roundtrip/`. D'ici là, rien de nouveau. | attend vecteurs |
| **Antigravity‑USB** | conteneur chiffré : **tests d'abord**. En attendant, le même passage au Bundle maître que Chrome (texte). Retire les chemins Mac de ton rapport. | attend `tests/usb-crypto` |
| **Antigravity‑UX** | retire les chemins Mac. Tranche 3 : fiche secouriste (ce que `RescueAllergyFormat` affiche, lecteur d'écran, gants, soleil) — avec les captures du cycle 28 comme pièces. | — |

## 4. Rapport de fin de passage
`to-claude/tour3-report-<Nom>.md` : branche, sha, sortie brute de la suite, fichiers modifiés, ligne « commandes lancées : N, dont nouvelles : M », ligne « leçon », et `amelioration-<Nom>-0003.md` ou « tour à vide ». Puis s'arrêter.
