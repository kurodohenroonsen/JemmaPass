---
id: 0060
type: task
from: claude
to: antigravity
lane: IMPL (après analyse), tests sur tests/kb-only @ d254230
decided_by: Kudoro, 2026-10-03 — « Libellés d'interface »
---
# Décision : les libellés courts des codes sont des textes d'interface (PROTOCOL §9.1)

Question posée à Kudoro : les « Orale / 経口 » écrits dans les catalogues Kotlin sont-ils du savoir médical (à sortir vers la KB, donc à perdre faute de traduction officielle) ou des textes d'interface ? Réponse : **textes d'interface**.

Conséquences :
- code + appartenance au jeu de valeurs → KB ;
- libellé court affiché → `strings.xml`, ressource `code_label_<sct|loinc|v3>_<code>` ;
- code sans ressource → libellé anglais de la KB, marqué « non traduit » ;
- la quête KB (0055) n'a donc **pas** à trouver des traductions pour ces listes courtes ; elle reste nécessaire pour les codes manquants, `albuterol`, et les listes longues (vaccins, procédures, examens).

## Tests rouges : `tests/kb-only` @ `d254230`, `kbonly/UiLabelsInResourcesTest.kt`
- UC-KB-011 : les 5 voies ont leur libellé dans `values`, `values-fr`, `values-ja` ;
- UC-KB-012 : un libellé FR/JA n'est pas le texte par défaut recopié ;
- UC-KB-013 : aucun `code_label_*` traduit sans texte par défaut ;
- UC-KB-014 : aucun libellé français ou japonais dans `pillars/*.kt`.
Première famille : les voies. Les autres suivront une par une.

## Ce que j'attends de toi, dans cet ordre
1. **Analyse courte** (`docs/analysis/ui-labels.md`, branche `ag/0060-ui-labels` partie de `tests/kb-only`) : pour chaque fichier de `pillars/` touché par UC-KB-014, le nombre de libellés, les écrans et les sorties (QR texte, PDF, agent) qui les lisent **sans `Context`** — c'est là qu'est la difficulté : `JemmaTextPayloadBuilder` et `JemmaTools` appellent `getDisplay(code, lang)` hors interface. Propose la signature qui remplace `getDisplay` (qui fournit les ressources ? dans quelle langue quand le QR est produit en japonais sur un téléphone en français ?).
2. Tu attends mon retour sur la signature. J'écris alors les tests de comportement.
3. Implémentation des voies seulement. Les libellés existants sont **déplacés tels quels** vers `strings.xml` (même texte, au caractère près) : tu n'en réécris ni n'en traduis aucun. Les 24 autres langues gardent le texte par défaut.

## Rappel des files ouvertes
0059 (rapport étape 0 v2, deux bases) > 0058 (plan KB) > cycle 27 (0054) > 0056 (sources) > 0053 (doc) > ceci.
