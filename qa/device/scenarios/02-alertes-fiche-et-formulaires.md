# Scénario appareil 02 — Alertes de sécurité : fiche et formulaires

Risque : haut. Une alerte absente ou un « tout va bien » affiché alors que rien n'a été vérifié peut tuer.

Cas d'usage : UC-ALR-001, UC-ALR-002, UC-ALR-004, UC-DDI-001, UC-DDI-016, UC-MED-019, UC-MED-020, UC-ALG-017,
UC-ALM-015 ; UC-ALG-018, UC-DDI-015, UC-ALR-009, UC-ALM-019, UC-DDS-023 (base de connaissances absente) :
**outil manquant**, voir la fin.
Personas : Kamekichi (warfarine + ibuprofène, sildénafil + dinitrate d'isosorbide, allergie pénicilline),
Kurodo (allergie pénicilline HIGH, aucun traitement), Haru (aucune allergie à la pénicilline).
Complète le test JVM `KbSafetyTruthTableTest` (verdicts) ; ici on vérifie ce que la personne voit.

## Règles communes

- Couloir : écrire les lignes ci-dessous dans `/tmp/jp/device/task.txt` (une action par ligne), lancer
  `bash qa/device/lane-device.sh`, lire `/tmp/jp/device/out.txt`. Vocabulaire : `qa/device/README.md` §3 ter.
  Une étape = un bloc ; ne pas enchaîner le bloc suivant si le `rc` d'une ligne `ui exists` / `verify` est non nul.
- Personas autorisés : `demo_kurodo`, `demo_haru`, `demo_kamekichi` uniquement. Le téléphone peut contenir de
  vrais profils : ne jamais les ouvrir, ne jamais capturer la liste des profils, toujours `verify … --only demo_*`.
- Libellés en anglais : la première action est `locale en`. Ce scénario a été écrit **sans appareil** : les
  identifiants (`--id`) viennent des layouts, mais un libellé (`--text`) peut différer. Si un `ui tap --text` échoue,
  faire `ui find --text "<début du libellé>"` ou `ui dump`, corriger la ligne et noter l'écart dans le rapport
  (écart de scénario, pas bug de l'application).
- `ui type` ne sait saisir que de l'ASCII sans espace : les valeurs de test sont choisies en conséquence.
- Verdict par étape : ✅ attendu observé · ❌ attendu non observé (bug : étapes + capture + extrait brut) · ⏭ non
  exécutable (dire pourquoi). Ne jamais reformuler un extrait JSON ou logcat : copie brute.

## Étape 0 — Départ
```
locale en
logcat-clear
ui launch
ui wait 3
```

## Étape 1 — Fiche avec alertes majeures (UC-ALR-001, UC-DDI-001)
```
ui tap --text "Kamekichi"
ui wait 3
ui exists --id profile_detail_alert_banner
ui find --id profile_detail_alert_banner
ui exists --text "major alert"
shot 02-1-kamekichi-banner.png
logcat
grep-log JEMMA-HYDRATOR
```
Attendu : bandeau rouge « ⚠ N major alert(s) detected » visible sans défiler ; N ≥ 2 (warfarine × ibuprofène et
sildénafil × dinitrate d'isosorbide) ; N égal à la somme annoncée par la ligne `JEMMA-HYDRATOR` (copie brute dans le
rapport) ; aucun bandeau « Safety checks not run » ni « incomplete » (sinon : le noter, c'est une information utile,
pas un échec du scénario). Recopier la liste des alertes telle qu'affichée.

## Étape 2 — Allergique à la pénicilline qui ajoute de l'amoxicilline (UC-MED-019)
```
ui back
ui tap --text "Kurodo"
ui tap --id profile_detail_pillars_header
ui tap --id profile_detail_tile_medications
ui tap --id medications_fab_add
ui tap --id medication_form_substance_card
ui tap --id drug_picker_search
ui type amoxicillin
ui wait 2
ui tap --text "Amoxicillin"
ui wait 2
shot 02-2-amoxicillin-dialog.png
ui exists --text "Clinical interaction detected"
ui exists --text "penicillin"
ui tap --text "Review"
ui find --id medication_form_substance_display
shot 02-2-after-review.png
```
Attendu : la fenêtre s'ouvre **dès le choix** de la substance, cite l'allergie à la pénicilline et propose
« Save anyway » et « Review » (ou « Cancel ») ; après « Review », on est toujours dans le formulaire et la substance
est vidée (UC-MED-019) ; rien n'est enregistré.
```
ui tap --id medication_form_cancel_btn
ui exists --id medications_empty_state
```
Attendu : la liste des traitements de Kurodo est toujours vide.

## Étape 3 — « Enregistrer quand même », puis correction (UC-ALR-004)
```
ui tap --id medications_fab_add
ui tap --id medication_form_substance_card
ui tap --id drug_picker_search
ui type amoxicillin
ui wait 2
ui tap --text "Amoxicillin"
ui tap --text "Save anyway"
ui tap --id medication_form_save_btn
ui wait 2
shot 02-3-second-dialog-or-list.png
```
Attendu : la substance reste choisie ; à l'enregistrement la fenêtre revient une seconde fois (choisir encore
« Save anyway ») ; 1 traitement dans la liste.
```
ui back
ui wait 3
ui find --id profile_detail_alert_banner
shot 02-3-kurodo-detail-with-alert.png
```
Attendu : la fiche de Kurodo montre l'alerte allergie × médicament (pénicilline × amoxicilline) et le bandeau compte
une alerte majeure de plus qu'avant l'ajout.
```
ui tap --id profile_detail_tile_medications
ui longpress --text "Amoxicillin"
ui tap --text "Delete"
ui wait 2
ui back
ui wait 3
ui find --id profile_detail_alert_banner
shot 02-3-kurodo-detail-after-delete.png
```
Attendu : l'alerte pénicilline × amoxicilline a disparu au retour sur la fiche, sans relancer l'application ; le
bandeau retrouve son compte initial.

## Étape 4 — Deux médicaments incompatibles saisis au formulaire (UC-DDI-016, UC-MED-020)
```
ui tap --id profile_detail_tile_medications
ui tap --id medications_fab_add
ui tap --id medication_form_substance_card
ui tap --id drug_picker_search
ui type warfarin
ui wait 2
ui tap --text "Warfarin"
ui tap --id medication_form_save_btn
ui wait 2
ui tap --id medications_fab_add
ui tap --id medication_form_substance_card
ui tap --id drug_picker_search
ui type ibuprofen
ui wait 2
ui tap --text "Ibuprofen"
ui wait 2
shot 02-4-ddi-dialog.png
ui exists --text "Drug-Drug Interactions"
ui exists --text "Major"
ui tap --text "Review"
ui tap --id medication_form_cancel_btn
```
Attendu : la warfarine seule s'enregistre sans alerte d'interaction ; au choix de l'ibuprofène, fenêtre avec la
section « ⚕ Drug-Drug Interactions: » et la gravité « Major » citant la warfarine ; après « Review » puis annulation,
la liste ne contient que la warfarine.

## Étape 5 — Nouvelle allergie qui croise un traitement existant (UC-ALG-017, UC-ALM-015)
Préparation sur Haru : ajouter l'amoxicilline (aucune fenêtre attendue : Haru n'est pas allergique — c'est le témoin
négatif, le noter).
```
ui back
ui back
ui tap --text "Haru"
ui tap --id profile_detail_pillars_header
ui tap --id profile_detail_tile_medications
ui tap --id medications_fab_add
ui tap --id medication_form_substance_card
ui tap --id drug_picker_search
ui type amoxicillin
ui wait 2
ui tap --text "Amoxicillin"
ui wait 2
shot 02-5-haru-amoxicillin-no-dialog.png
ui tap --id medication_form_save_btn
ui wait 2
ui back
ui tap --id profile_detail_tile_allergies
ui tap --id allergies_fab_add
ui tap --id allergy_form_substance_card
ui tap --id picker_search
ui type penicillin
ui wait 2
ui tap --text "penicillin"
ui wait 2
shot 02-5-allergy-vs-meds-dialog.png
ui exists --text "Amoxicillin"
ui tap --text "Review"
ui find --id allergy_form_substance_display
ui tap --id allergy_form_cancel_btn
```
Attendu : fenêtre listant l'amoxicilline comme traitement en conflit (section « Existing medications that would
conflict » ou « Conflicts with existing medications ») ; après « Review », la substance du formulaire est vidée ;
après annulation, Haru n'a toujours qu'une allergie (soja).

## Étape 6 — Preuve logcat
```
logcat
grep-log JEMMA-HYDRATOR
grep-log FATAL EXCEPTION
```
Attendu : lignes d'hydratation présentes pour les trois fiches ouvertes ; aucune exception.

## Outils manquants (à demander, ne pas improviser)

- **Rendre la base de connaissances indisponible sur l'appareil** (`kb-rm` ne supprime que la copie locale) : il
  faudrait deux actions, par exemple `kb-device-hide` (renommer
  `files/knowledge_full_db/1.1/knowledge_full.db` en `.off`) et `kb-device-restore`. Avec elles : ouvrir la fiche de
  Kamekichi → attendu bandeau « ⓘ Safety checks not run — knowledge base unavailable » et **aucun** affichage
  rassurant ; formulaire d'allergie et de traitement → attendu « ⚠ Safety check incomplete » avec le texte « Nothing
  was verified… » (UC-ALG-018, UC-DDI-015, UC-ALR-009, UC-ALM-019, UC-DDS-023). Sans ces actions : ⏭.
- **Fiche sans aucune alerte** (UC-ALR-003) : aucun des trois personas n'en est exempt ; ce cas demande un profil
  de test supplémentaire, hors du périmètre autorisé.

## Restauration de l'état semé (obligatoire, même après un échec)

Supprimer la warfarine de Kurodo (liste des traitements → appui long → « Delete ») et l'amoxicilline de Haru (même
geste). Kurodo doit revenir à 0 traitement, Haru à 3.

```
ui back
ui back
pull-profiles pull-final
verify pull-final --only demo_kurodo --only demo_haru --only demo_kamekichi --expect-im demo_kurodo=4 --expect-im demo_haru=3 --expect-im demo_kamekichi=0 --expect-pr demo_kurodo=2 --expect-pr demo_haru=2 --expect-pr demo_kamekichi=0 --expect-dv demo_kurodo=0 --expect-dv demo_haru=2 --expect-dv demo_kamekichi=0 --expect-rs demo_kurodo=4 --expect-rs demo_haru=5 --expect-rs demo_kamekichi=1 --expect-ph demo_kurodo=2 --expect-ph demo_haru=2 --expect-ph demo_kamekichi=0 --expect-cn demo_kurodo=1 --expect-cn demo_haru=2 --expect-cn demo_kamekichi=3 --expect-pg demo_kurodo=0 --expect-pg demo_haru=3 --expect-pg demo_kamekichi=0 --expect-fs demo_kurodo=0 --expect-fs demo_haru=2 --expect-fs demo_kamekichi=0 --title "Seed restored" --markdown pull-final.md
json pull-final/demo_kurodo.fhir.json type:AllergyIntolerance final-kurodo-al.json
json pull-final/demo_haru.fhir.json type:MedicationStatement final-haru-md.json
json pull-final/demo_kamekichi.fhir.json type:MedicationStatement final-kamekichi-md.json
logcat
grep-log FATAL EXCEPTION
```
Attendu : `verify` rc=0 ; 3 allergies pour Kurodo, 3 traitements pour Haru, 5 pour Kamekichi ; `grep-log` ne trouve
rien (rc=1 est le bon résultat ici). Au moindre écart : `qa-run` (re-sème les trois personas de démonstration et
revérifie), puis le signaler dans le rapport.

Contrôle supplémentaire : `json pull-final/demo_kurodo.fhir.json type:MedicationStatement final-kurodo-md.json` doit
répondre `nothing matches` (rc≠0 : c'est le bon résultat, Kurodo n'a aucun traitement).
