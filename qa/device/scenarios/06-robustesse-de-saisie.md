# Scénario appareil 06 — Robustesse de la saisie : arrêt brutal, langue, gestes rapides

Risque : haut. Une personne âgée ou stressée appuie deux fois, est interrompue par un appel, change de langue ; le
dossier ne doit être ni corrompu, ni dupliqué, ni vidé.

Cas d'usage : UC-ROB-004, UC-HUM-024, UC-ROB-013, UC-PAT-009 (variante arrêt forcé), UC-PAT-007 (variante
traitement), UC-ALR-005. Persona : Kurodo (aucun traitement au départ, donc tout ajout se compte facilement) et
Kamekichi (lecture seule, pour la langue). Complète le test JVM `RandomProfileInvariantsTest`.

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

Limite de l'outillage : chaque ligne `ui tap` prend environ une seconde (lecture de l'écran) : ce n'est pas un vrai
double appui. L'étape 2 utilise deux `swipe` immobiles, le plus rapide possible avec les actions existantes ; un vrai
double appui (< 100 ms) demande une action `double-tap <id>` — à réclamer dans le rapport si l'étape 2 ne reproduit rien.

## Étape 0 — Départ
```
locale en
logcat-clear
ui launch
ui wait 3
ui tap --text "Kurodo"
ui tap --id profile_detail_pillars_header
ui tap --id profile_detail_tile_medications
ui exists --id medications_empty_state
```

## Étape 1 — Trois traitements saisis à la suite, sans pause (UC-ROB-004, UC-HUM-024)
Répéter ce bloc trois fois avec `paracetamol` / `Paracetamol`, `omeprazole` / `Omeprazole`, `metformin` / `Metformin`,
**sans** `ui wait` entre l'enregistrement et l'ajout suivant :
```
ui tap --id medications_fab_add
ui tap --id medication_form_substance_card
ui tap --id drug_picker_search
ui type paracetamol
ui wait 2
ui tap --text "Paracetamol"
ui tap --id medication_form_save_btn
```
Si une fenêtre « Clinical interaction detected » s'ouvre : capture, « Save anyway », le noter. Puis :
```
ui wait 2
ui find --id medications_count
shot 06-1-three-medications.png
pull-profiles pull-06-1
verify pull-06-1 --only demo_kurodo --expect-im demo_kurodo=4 --expect-pr demo_kurodo=2 --expect-rs demo_kurodo=4 --expect-ph demo_kurodo=2 --expect-cn demo_kurodo=1 --title "06-1" --markdown pull-06-1.md
json pull-06-1/demo_kurodo.fhir.json type:MedicationStatement 06-1-md.json
```
Attendu : exactement 3 traitements à l'écran et 3 MedicationStatement distincts dans le fichier (aucun perdu, aucun
doublon) ; les 3 allergies et les piliers natifs intacts.

## Étape 2 — Double appui sur « Enregistrer » (UC-PAT-007, variante traitement)
```
ui tap --id medications_fab_add
ui tap --id medication_form_substance_card
ui tap --id drug_picker_search
ui type cetirizine
ui wait 2
ui tap --text "Cetirizine"
ui find --id medication_form_save_btn
```
Lire les coordonnées du centre du bouton dans la sortie de `ui find` (`bounds=(x1, y1, x2, y2)` → `X=(x1+x2)/2`,
`Y=(y1+y2)/2`), puis deux lignes consécutives :
```
swipe X Y X Y 30
swipe X Y X Y 30
ui wait 2
ui find --id medications_count
shot 06-2-double-tap.png
pull-profiles pull-06-2
json pull-06-2/demo_kurodo.fhir.json type:MedicationStatement 06-2-md.json
```
Attendu : 4 traitements, **un seul** « Cetirizine » ; le second appui ne doit ni créer un doublon ni toucher un
élément de la liste située derrière. Deux entrées Cetirizine = ❌.

## Étape 3 — Arrêt forcé pendant une saisie (UC-PAT-009, variante)
```
ui tap --id medications_fab_add
ui tap --id medication_form_dose_value
ui type 999
shot 06-3-before-stop.png
ui stop
ui launch
ui wait 3
ui tap --text "Kurodo"
ui wait 2
shot 06-3-after-relaunch.png
pull-profiles pull-06-3
verify pull-06-3 --only demo_kurodo --only demo_haru --only demo_kamekichi --expect-im demo_kurodo=4 --expect-rs demo_kurodo=4 --title "06-3" --markdown pull-06-3.md
json pull-06-3/demo_kurodo.fhir.json type:MedicationStatement 06-3-md.json
logcat
grep-log FATAL EXCEPTION
```
Attendu : la fiche de Kurodo s'ouvre ; les deux fichiers du profil se lisent (`verify` P1 vert) ; toujours 4
traitements ; aucune entrée à moitié saisie (`999` nulle part) ; aucun fichier `*.tmp` laissé dans `pull-06-3/`
(lister le dossier) ; aucune exception. Variante non couverte : la mise à mort en arrière-plan par Android
(`am kill`, avec restauration de la saisie) — demander une action `bg-kill`.

## Étape 4 — Changement de langue, formulaire ouvert (UC-ROB-013)
```
ui tap --id profile_detail_pillars_header
ui tap --id profile_detail_tile_allergies
ui tap --id allergies_fab_add
ui tap --id allergy_form_notes
ui type QA-LANG
shot 06-4-before-locale.png
locale ja
ui wait 3
shot 06-4-after-locale-ja.png
ui find --id allergy_form_notes
locale en
ui wait 3
ui find --id allergy_form_notes
logcat
grep-log FATAL EXCEPTION
```
Attendu : pas de plantage ; idéalement le formulaire est toujours là avec `QA-LANG` ; s'il a disparu ou s'est vidé,
le noter comme « perte de saisie » (sévérité moyenne), pas comme réussite. Fermer sans enregistrer :
`ui tap --id allergy_form_cancel_btn` (ou `ui back`). Kurodo doit garder exactement 3 allergies.

## Étape 5 — Changement de langue, fiche avec alertes ouverte (UC-ALR-005)
```
ui back
ui back
ui tap --text "Kamekichi"
ui wait 3
ui find --id profile_detail_alert_banner
shot 06-5-kamekichi-en.png
locale ja
ui wait 4
ui find --id profile_detail_alert_banner
shot 06-5-kamekichi-ja.png
locale fr
ui wait 4
ui find --id profile_detail_alert_banner
shot 06-5-kamekichi-fr.png
locale en
ui wait 3
```
Attendu : le bandeau reste présent dans les trois langues avec **le même nombre** d'alertes majeures ; libellés
traduits ; aucune langue où le bandeau disparaît ou devient « vérifications non effectuées ».

## Restauration de l'état semé (obligatoire, même après un échec)

Supprimer les quatre traitements ajoutés à Kurodo (Paracetamol, Omeprazole, Metformin, Cetirizine) : liste des
traitements → appui long sur chacun → « Delete ». Vérifier `ui exists --id medications_empty_state`. Laisser
l'application en anglais (`locale en`) ou remettre la langue relevée avant le scénario.

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
répondre `nothing matches` (Kurodo n'a aucun traitement dans l'état semé).
