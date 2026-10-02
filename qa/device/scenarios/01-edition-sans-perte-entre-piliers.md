# Scénario appareil 01 — Une édition ne fait rien perdre ailleurs

Risque : haut. Un aidant corrige une adresse, une allergie, un traitement ou un contact sur un profil complet ;
aucun des huit piliers natifs (💉 🏥 📟 🧪 📜 🩺 🤰 ♿), ni les allergies, ni les traitements ne doivent bouger.

Cas d'usage : UC-PAT-013, UC-STO-003, UC-STO-004, UC-STO-006, UC-STO-007, UC-MED-022, UC-ALG-019.
Persona : Haru (le seul qui remplit les huit piliers natifs : 💉3 🏥2 📟2 🧪5 📜2 🩺2 🤰3 ♿2 ; 1 allergie, 3 traitements).
Complète le test JVM `PersonaSeedIntegrityTest` (qui prouve la même chose sans l'interface).

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

Ligne de contrôle réutilisée après chaque édition (remplacer `<n>`), appelée **CONTRÔLE(n)** :
```
pull-profiles pull-01-<n>
verify pull-01-<n> --only demo_haru --expect-im demo_haru=3 --expect-pr demo_haru=2 --expect-dv demo_haru=2 --expect-rs demo_haru=5 --expect-ph demo_haru=2 --expect-cn demo_haru=2 --expect-pg demo_haru=3 --expect-fs demo_haru=2 --title "01-<n>" --markdown pull-01-<n>.md
json pull-01-<n>/demo_haru.fhir.json type:AllergyIntolerance 01-<n>-al.json
json pull-01-<n>/demo_haru.fhir.json type:MedicationStatement 01-<n>-md.json
json pull-01-<n>/demo_haru.fhir.json type:Immunization 01-<n>-im.json
json pull-01-<n>/demo_haru.fhir.json type:DeviceUseStatement 01-<n>-dv.json
```
`CONTRÔLE(n)` n'est pas une action : avant d'écrire `task.txt`, le remplacer par ces six lignes avec le bon `<n>`.
Attendu à chaque fois : `verify` rc=0 ; 1 AllergyIntolerance ; 3 MedicationStatement ; les `id` des Immunization et
des DeviceUseStatement identiques à ceux du CONTRÔLE(0) ; lot, fabricant et numéro de dose des vaccins inchangés.

## Étape 0 — État de départ
```
locale en
ui launch
ui wait 3
CONTRÔLE(0)
```
Attendu : rc=0. Garder `01-0-*.json` comme référence de comparaison.

## Étape 1 — Corriger l'adresse (UC-PAT-013)
```
ui tap --text "Haru"
ui tap --id profile_detail_pillars_header
ui tap --id profile_detail_tile_patient
ui tap --id perso_address_line
key 123
ui type QA1
ui tap --id perso_save_btn
ui wait 2
shot 01-1-address-saved.png
CONTRÔLE(1)
json pull-01-1/demo_haru.fhir.json type:Patient 01-1-patient.json
```
Attendu : retour à la fiche sans message d'erreur ; `Patient.address` se termine par `QA1` ; nom, date de naissance,
groupe sanguin `O+` inchangés ; CONTRÔLE(1) conforme.

## Étape 2 — Ajouter un téléphone dans l'identité (UC-STO-006)
```
ui tap --id profile_detail_tile_patient
ui tap --id perso_telecom_phone
key 123
ui type +32470000001
ui tap --id perso_save_btn
ui wait 2
CONTRÔLE(2)
```
Attendu : `Patient.telecom` contient `+32470000001` ; CONTRÔLE(2) conforme (allergie et 3 traitements toujours là).

## Étape 3 — Modifier une allergie (UC-STO-003, UC-STO-004, UC-ALG-019)
```
ui tap --id profile_detail_tile_allergies
ui tap --text "soy"
ui tap --id allergy_form_notes
key 123
ui type QA3
ui tap --id allergy_form_save_btn
ui wait 2
shot 01-3-allergy-saved.png
ui back
CONTRÔLE(3)
```
Attendu : aucune fenêtre d'alerte (Haru ne prend rien qui croise le soja ; si « ⚠ Safety check incomplete » apparaît,
capturer, choisir l'enregistrement et le noter) ; la note de l'allergie se termine par `QA3` ; sévérité toujours
basse ; CONTRÔLE(3) conforme pour les **huit** piliers natifs.

## Étape 4 — Modifier un traitement (UC-MED-022)
```
ui tap --id profile_detail_tile_medications
ui tap --text "Furosemide"
ui tap --id medication_form_timing
key 123
ui type QA4
ui tap --id medication_form_save_btn
ui wait 2
shot 01-4-medication-saved.png
ui back
CONTRÔLE(4)
```
Attendu : si une fenêtre « Clinical interaction detected » s'ouvre, la capturer et choisir « Save anyway » (le
traitement existait déjà) ; code ATC `C03CA01` et dose `1 tab` inchangés ; les deux autres traitements identiques
au CONTRÔLE(0) ; CONTRÔLE(4) conforme.

## Étape 5 — Ajouter puis retirer un contact (UC-STO-007)
```
ui tap --id profile_detail_tile_contacts
ui tap --id contacts_fab_add
ui tap --id contact_form_name
ui type QAContact
ui tap --id contact_form_phone
ui type +32470000002
ui tap --id contact_form_save_btn
ui wait 2
shot 01-5-contact-added.png
ui back
CONTRÔLE(5)
```
Attendu : 1 contact `QAContact` avec son téléphone dans `Patient.contact` ; vaccins avec lot, fabricant et numéro
de dose identiques au CONTRÔLE(0) (c'est le cœur de UC-STO-007) ; CONTRÔLE(5) conforme.

## Restauration de l'état semé (obligatoire, même après un échec)

Défaire dans l'ordre inverse : supprimer le contact `QAContact` (appui long ou ouverture puis suppression, confirmer
« Delete ») ; rouvrir Furosemide, placer le curseur en fin d'horaire (`key 123`) et envoyer trois fois `key 67` ;
même chose pour la note de l'allergie (trois `key 67`) et pour l'adresse (trois `key 67`) ; vider le téléphone
(`key 123` puis douze `key 67`). Enregistrer chaque écran.

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

Contrôle supplémentaire : `json pull-final/demo_haru.fhir.json type:Patient final-haru-patient.json` → adresse
`Aomori, Japan`, pas de `telecom`, pas de `contact`.
