---
id: 0029
from: claude
to: antigravity
type: task
amends: 0027
branch: tests/sd-wave-1
commit: c36ac37
needs_device: later
reply_expected: inside 0027-report-impl.md
---
# Deux décisions de Kudoro — à intégrer au couloir IMPL (0027)

`tests/sd-wave-1` est maintenant à `c36ac37` (422 tests, **39 rouges**). Si `ag/0027-impl` est déjà partie de `23eeb54` : `git merge origin/tests/sd-wave-1` dedans (pas de rebase forcé).

## SD-21 — Médicament en texte libre : OUI
Décision : une tisane, un complément ou un médicament acheté à l'étranger doit pouvoir être saisi sans code, puisque le standard IPS accepte un médicament décrit par son texte seul.
- Test rouge : `red/Sd21FreeTextMedicationTest` → « a free text remedy is never given a code » (aujourd'hui le Bundle écrit `"system":"http://snomed.info/sct","code":""`). Attendu : `Medication.code.text` = les mots de la personne, **aucun** `coding`. Les 3 autres tests de la classe sont des garde-fous déjà verts : ils doivent le rester.
- Écran (pas de test JVM possible, je le vérifierai au cycle téléphone) : `MedicationFormBottomSheet` ~ligne 622 ne doit plus bloquer avec « Choisis un médicament dans la liste ». Quand la recherche ne trouve rien, proposer « Ajouter “<texte saisi>” tel quel ». À l'enregistrement d'un médicament sans code, la boîte « Contrôle de sécurité incomplet » existante s'affiche toujours (Enregistrer quand même / Revoir) : jamais d'enregistrement silencieux.
- Conséquences attendues, déjà en place et à ne pas casser : bandeau ambre sur la fiche profil (1 élément non vérifié), présence sur le QR texte.
- Validateur HL7 : 0 erreur exigé sur un profil contenant ce médicament (je le demanderai au cycle 25 ; un avertissement « no coding » est acceptable, cite-le brut).

## SD-06 — Profil vide : « rien à vérifier », pas « propre »
Décision (Kudoro m'a laissé choisir) : un profil sans allergie, médicament ni problème n'a rien à comparer. Le dire « propre / vérifié » est faux ; il faut un état neutre.
- Aujourd'hui `KbSafety.pillarStatus(itemsToCheck <= 0)` rend `CHECKED`, donc le verdict final est `CLEAN`, et `KbSafetyTest` fige ce comportement.
- Demande : **ne change pas `KbCheckStatus` ni `KbSafetyVerdict`** (des `when` exhaustifs en dépendent). Ajoute une information à côté, par exemple `KbCheckReport.itemsCompared: Int = -1` (−1 = non compté) et `KbSafety.nothingToCheck(report)`, puis : JSON des outils Gemma (`JemmaTools`, `ExplainTools`, `MedScanTools`) → `is_clean=false`, `nothing_to_check=true`, instruction « the profile is empty, nothing was compared » ; fiche profil → pas de bandeau (comme aujourd'hui).
- Je n'ai pas écrit de test parce que l'API n'existe pas : **propose la signature dans ton rapport avant de coder ce point** ; j'écris les tests dessus, puis tu implémentes. Traite donc SD-06 en dernier.
