# Scénario appareil 04 — Profil courant, édition du bon profil, suppression

Risque : haut. Le profil « courant » est celui que montrent le widget d'urgence et l'écran SOS : afficher la fiche
d'une autre personne, ou écrire dans le mauvais dossier, est une erreur d'identité.

Cas d'usage : UC-MPR-002, UC-MPR-004, UC-MPR-005, UC-MPR-006, UC-MPR-007, UC-STO-027. UC-MPR-003 et UC-MPR-008
demandent un vrai profil ou un lien direct : ⏭ (voir la fin).
Personas : les trois ; deux sont supprimés puis re-semés par `qa-run`.

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

**Précaution propre à ce scénario** : ne supprimer que des profils `demo_*`. S'il existe de vrais profils sur le
téléphone, ne pas exécuter les étapes 4 et 5 (⏭ « vrais profils présents ») : la promotion automatique pourrait
désigner l'un d'eux, ce qui ne doit pas être provoqué en test.

## Étape 0 — Relever le profil courant de départ
```
locale en
logcat-clear
ui launch
ui wait 3
ui find --id profile_active_name
pull-profiles pull-04-0
verify pull-04-0 --only demo_kurodo --only demo_haru --only demo_kamekichi --title "04-0" --markdown pull-04-0.md
```
Attendu : noter dans le rapport le nom affiché par `profile_active_name` (appelé plus bas **COURANT-INITIAL**) ; s'il
n'y en a pas, noter « aucun ». C'est lui qu'il faudra rétablir.

## Étape 1 — Choisir Haru comme profil courant (UC-MPR-002)
```
ui longpress --text "Haru"
ui tap --id profile_lpm_activate
ui wait 2
ui find --id profile_active_name
ui stop
ui launch
ui wait 3
ui find --id profile_active_name
logcat
grep-log JEMMA-PROFILES
```
Attendu : `profile_active_name` = Haru, avant **et après** l'arrêt-relance (le choix est durable). Si l'écran d'accueil
porte un widget d'urgence, `key 3` puis une capture `shot 04-1-widget.png` : il montre Haru (sinon ⏭ « pas de widget
posé »).

## Étape 2 — Éditer Kurodo pendant que Haru est courant (UC-MPR-007)
```
ui tap --text "Kurodo"
ui tap --id profile_detail_pillars_header
ui tap --id profile_detail_tile_contacts
ui tap --id contacts_fab_add
ui tap --id contact_form_name
ui type QAOwner
ui tap --id contact_form_save_btn
ui wait 2
ui back
ui back
pull-profiles pull-04-2
verify pull-04-2 --only demo_kurodo --only demo_haru --expect-im demo_kurodo=4 --expect-im demo_haru=3 --expect-rs demo_haru=5 --title "04-2" --markdown pull-04-2.md
json pull-04-2/demo_kurodo.fhir.json type:Patient 04-2-kurodo-patient.json
json pull-04-2/demo_haru.fhir.json type:Patient 04-2-haru-patient.json
```
Attendu : `QAOwner` figure dans `Patient.contact` de **Kurodo** et nulle part dans le fichier de Haru ; Haru est
toujours le profil courant ; `verify` rc=0.

## Étape 3 — Retirer le profil courant (UC-MPR-004)
```
ui longpress --text "Haru"
shot 04-3-menu.png
```
Attendu : relever les entrées du menu. S'il existe un moyen de retirer le statut courant (étoile de la carte
`profile_card_star`, ou entrée du menu), l'utiliser puis `ui find --id profile_active_name` : attendu aucun profil
courant, et un widget vide plutôt que la fiche d'un autre. S'il n'existe aucun moyen : le consigner comme constat
(UC-MPR-004 non réalisable par l'interface) et fermer le menu (`ui back`).

## Étape 4 — Supprimer un profil non courant (UC-STO-027)
```
ui longpress --text "Kamekichi"
ui tap --id profile_lpm_delete
ui exists --text "Delete this profile?"
shot 04-4-confirm.png
ui tap --text "Delete"
ui wait 2
ui find --id profile_active_name
pull-profiles pull-04-4
verify pull-04-4 --only demo_kurodo --only demo_haru --title "04-4" --markdown pull-04-4.md
```
Attendu : demande de confirmation explicite ; après confirmation, Kamekichi a disparu de la liste ; dans
`pull-04-4/` il n'y a plus **ni** `demo_kamekichi.json` **ni** `demo_kamekichi.fhir.json` (lister le dossier dans le
rapport) ; Haru est toujours courant ; Kurodo et Haru intacts.

## Étape 5 — Supprimer le profil courant (UC-MPR-005, UC-MPR-006)
```
ui longpress --text "Haru"
ui tap --id profile_lpm_delete
ui tap --text "Delete"
ui wait 2
ui find --id profile_active_name
shot 04-5-after-delete-current.png
logcat
grep-log JEMMA-PROFILES
```
Attendu (catalogue) : **aucun** profil courant ; l'application ne désigne pas silencieusement Kurodo comme porteur
du téléphone. Le catalogue annonce que le code actuel promeut le dernier profil modifié : si `profile_active_name`
affiche Kurodo sans avoir rien demandé, c'est un ❌ à rapporter avec la capture et la ligne `JEMMA-PROFILES` brute.

## Non exécutables ici
- UC-MPR-003 (premier vrai profil créé alors que les personas existent) : créer un profil sort du périmètre
  « personas de démonstration uniquement ».
- UC-MPR-008 (écran d'édition ouvert sans identifiant de profil) : demande une action `deeplink <uri>`.

## Restauration de l'état semé (obligatoire, même après un échec)
```
qa-run
ui launch
ui wait 3
ui find --id profile_active_name
pull-profiles pull-final
verify pull-final --only demo_kurodo --only demo_haru --only demo_kamekichi --expect-im demo_kurodo=4 --expect-im demo_haru=3 --expect-im demo_kamekichi=0 --expect-pr demo_kurodo=2 --expect-pr demo_haru=2 --expect-pr demo_kamekichi=0 --expect-dv demo_kurodo=0 --expect-dv demo_haru=2 --expect-dv demo_kamekichi=0 --expect-rs demo_kurodo=4 --expect-rs demo_haru=5 --expect-rs demo_kamekichi=1 --expect-ph demo_kurodo=2 --expect-ph demo_haru=2 --expect-ph demo_kamekichi=0 --expect-cn demo_kurodo=1 --expect-cn demo_haru=2 --expect-cn demo_kamekichi=3 --expect-pg demo_kurodo=0 --expect-pg demo_haru=3 --expect-pg demo_kamekichi=0 --expect-fs demo_kurodo=0 --expect-fs demo_haru=2 --expect-fs demo_kamekichi=0 --title "Seed restored" --markdown pull-final.md
json pull-final/demo_kurodo.fhir.json type:Patient final-kurodo-patient.json
logcat
grep-log FATAL EXCEPTION
```
Attendu : `qa-run` vert (les trois personas re-semés, donc `QAOwner` disparu du contact de Kurodo : le vérifier dans
`final-kurodo-patient.json`, qui ne doit contenir que le contact `Kamekichi`) ; `verify` rc=0. Puis rétablir
**COURANT-INITIAL** : `ui longpress --text "<nom relevé à l'étape 0>"` + `ui tap --id profile_lpm_activate`, et
contrôler avec `ui find --id profile_active_name`. Si COURANT-INITIAL était « aucun » et que l'interface ne permet pas
d'y revenir, l'écrire en tête du rapport : le téléphone ne doit pas rester avec un persona de démonstration présenté
comme porteur sans que ce soit dit.
