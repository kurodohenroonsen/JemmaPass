# Scénario appareil 05 — Groupe sanguin : une seule vérité entre identité et résultats

Risque : haut. Deux groupes sanguins différents sur une même fiche, ou un groupe qui ne suit pas la correction, mène
à une erreur transfusionnelle.

Cas d'usage : UC-PAT-002, UC-PAT-003, UC-RES-003, UC-RES-004, UC-RES-005 ; UC-RES-006 (profil reçu par QR) : ⏭ sans
outil d'import. Persona : Kurodo (A+, 🧪 4 résultats dont la ligne dérivée du groupe sanguin).
Complète les tests JVM `IpsBloodGroupTest` et `PersonaSeedIntegrityTest`.

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

## Étape 0 — Référence
```
locale en
logcat-clear
ui launch
ui wait 3
pull-profiles pull-05-0
verify pull-05-0 --only demo_kurodo --expect-rs demo_kurodo=4 --title "05-0" --markdown pull-05-0.md
json pull-05-0/demo_kurodo.fhir.json code:882-1 05-0-blood.json
```
Attendu : une Observation `882-1` d'identifiant `rs-blood-group-demo_kurodo`, `valueCodeableConcept` = A Rh positif.

## Étape 1 — La ligne dérivée n'est pas modifiable depuis les résultats (UC-RES-004)
```
ui tap --text "Kurodo"
ui tap --id profile_detail_pillars_header
ui tap --id profile_detail_tile_results
ui wait 2
shot 05-1-results.png
ui tap --text "blood group"
ui wait 1
shot 05-1-after-tap.png
ui exists --id results_fab_add
ui longpress --text "blood group"
ui wait 1
ui exists --id results_fab_add
```
Attendu : appui et appui long n'ouvrent **aucun** formulaire (on reste sur la liste : `results_fab_add` présent) ;
message bref « The blood group comes from the Patient pillar — change it there. » (un toast peut échapper à la
capture : dans ce cas l'absence de formulaire suffit, le noter).

## Étape 2 — Résultat de laboratoire contradictoire (UC-RES-003, UC-RES-005)
```
ui tap --id results_fab_add
ui tap --id result_form_code_card
ui tap --id picker_search
ui type blood
ui wait 2
ui tap --text "ABO"
ui wait 1
shot 05-2-form.png
ui tap --id result_form_coded_row
ui tap --text "O"
ui tap --id result_form_save_btn
ui wait 2
shot 05-2-conflict.png
ui exists --text "Blood group does not match the profile"
ui tap --text "Cancel"
ui tap --id result_form_cancel_btn
ui back
pull-profiles pull-05-2
verify pull-05-2 --only demo_kurodo --expect-rs demo_kurodo=4 --title "05-2" --markdown pull-05-2.md
json pull-05-2/demo_kurodo.fhir.json type:Observation 05-2-observations.json
```
Attendu : choisir un groupe différent de A+ (prendre la première valeur « O » proposée ; noter laquelle) déclenche
la fenêtre « Blood group does not match the profile » qui cite A+ et la valeur saisie et propose « Change it in
identity » / « Cancel » ; après annulation rien n'est écrit : toujours 4 résultats et **une seule** Observation
`882-1`, toujours A+.

## Étape 3 — Corriger le groupe dans l'identité : A+ → O- (UC-PAT-002)
```
ui tap --id profile_detail_tile_patient
ui tap --id perso_blood_type_row
ui tap --text "O-"
ui tap --id perso_save_btn
ui wait 2
shot 05-3-detail.png
ui exists --text "O-"
pull-profiles pull-05-3
verify pull-05-3 --only demo_kurodo --expect-rs demo_kurodo=4 --expect-im demo_kurodo=4 --expect-pr demo_kurodo=2 --expect-ph demo_kurodo=2 --expect-cn demo_kurodo=1 --title "05-3" --markdown pull-05-3.md
json pull-05-3/demo_kurodo.fhir.json code:882-1 05-3-blood.json
json pull-05-3/demo_kurodo.fhir.json type:Observation 05-3-observations.json
```
Attendu : la fiche affiche O- ; `_j` : `p.bt` = `O-` (ouvrir `pull-05-3/demo_kurodo.json`) ; l'Observation `882-1`
garde l'identifiant `rs-blood-group-demo_kurodo` et porte maintenant O Rh négatif (SNOMED `278148006`) ; une seule
`882-1` dans `05-3-observations.json` ; les 3 autres résultats identiques à l'étape 0 ; `verify` rc=0.
Contrôle du QR texte :
```
ui tap --id menu_profile_detail_export
ui tap --text "QR Codes"
ui wait 3
ui tap --id qr_tab_text
ui tap --desc qr_lang_en
ui wait 2
shot 05-3-qr-en.png
decode-qr 05-3-qr-en.png qr-kurodo-en-blood.txt
ui back
```
Attendu : le texte décodé contient O- **une seule fois comme groupe sanguin** et plus aucun A+.

## Étape 4 — Retirer le groupe sanguin (UC-PAT-003)
```
ui tap --id profile_detail_tile_patient
ui tap --id perso_blood_type_row
shot 05-4-choices.png
```
Choisir l'entrée « vide / inconnu » de la liste (relever son libellé exact sur la capture), puis :
```
ui tap --id perso_save_btn
ui wait 2
pull-profiles pull-05-4
verify pull-05-4 --only demo_kurodo --expect-rs demo_kurodo=3 --title "05-4" --markdown pull-05-4.md
```
Attendu : `p.bt` absent de `_j` ; plus aucune Observation `882-1` (3 résultats) ; les 3 autres résultats intacts ;
la fiche et le QR texte ne montrent plus de groupe sanguin (ni « null », ni « ? »). Si la liste n'offre aucune entrée
vide : constat à rapporter (UC-PAT-003 non réalisable), passer à la restauration.

## Restauration de l'état semé (obligatoire, même après un échec)

Remettre A+ : fiche Kurodo → `ui tap --id profile_detail_tile_patient` → `ui tap --id perso_blood_type_row` →
`ui tap --text "A+"` → `ui tap --id perso_save_btn`.

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

Contrôle supplémentaire : `json pull-final/demo_kurodo.fhir.json code:882-1 final-blood.json` → identifiant
`rs-blood-group-demo_kurodo`, A Rh positif, identique octet pour octet à `05-0-blood.json` (hors date de mise à jour).

Outil manquant : UC-RES-006 (profil reçu par QR, puis groupe corrigé dans l'identité) demande l'import d'une charge
QR sans caméra (`import-payload`, voir scénario 03).
