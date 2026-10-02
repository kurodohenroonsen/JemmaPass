# Scénario appareil 03 — QR d'urgence : texte, compact, FHIR

Risque : haut. Le QR texte est ce qu'un soignant étranger lit sans l'application ; s'il est illisible, coupé sans
prévenir ou réparti sur plusieurs images, l'information vitale n'arrive pas.

Cas d'usage : UC-QRT-001, UC-QRT-008, UC-QRT-010, UC-QRF-001 (moitié émission), UC-QRF-002, UC-IMP-001 (moitié
émission) ; défaut suspecté SD-11 (`qa/usecases/suspected-defects.md`).
Personas : les trois. Complète les tests JVM `TextQrAllLanguagesTest` et `RandomProfileInvariantsTest`.

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

## Étape 1 — QR texte de Kurodo en anglais, français, japonais, arabe (UC-QRT-001)
```
ui tap --text "Kurodo"
ui tap --id menu_profile_detail_export
ui tap --text "QR Codes"
ui wait 3
ui tap --id qr_tab_text
ui tap --desc qr_lang_en
ui wait 2
ui find --id qr_frame_counter
shot 03-1-kurodo-en.png
decode-qr 03-1-kurodo-en.png qr-kurodo-en.txt
ui tap --desc qr_lang_fr
ui wait 2
shot 03-1-kurodo-fr.png
decode-qr 03-1-kurodo-fr.png qr-kurodo-fr.txt
ui tap --desc qr_lang_ja
ui wait 2
shot 03-1-kurodo-ja.png
decode-qr 03-1-kurodo-ja.png qr-kurodo-ja.txt
ui tap --desc qr_lang_ar
ui wait 2
shot 03-1-kurodo-ar.png
decode-qr 03-1-kurodo-ar.png qr-kurodo-ar.txt
```
Attendu pour chaque langue : **un seul** QR (pas de compteur d'images « 1/2 », `qr_frame_counter` absent ou
masqué) ; taille décodée ≤ 1 800 octets ; dans l'ordre : identité (nom, 1979-04-04, groupe A+), les 3 allergies
(pénicilline et poisson marquées graves), puis les autres sections ; jamais le mot `null` ; aucune ligne coupée au
milieu ; pas de marque `✂️` (Kurodo tient entièrement, calcul fait hors appareil). Si la puce `qr_lang_ar` n'est pas
visible, faire défiler la rangée de puces (`swipe`) et le noter.

## Étape 2 — QR texte de Haru : coupe signalée (UC-QRT-008, UC-QRT-010, SD-11)
```
ui back
ui back
ui tap --text "Haru"
ui tap --id menu_profile_detail_export
ui tap --text "QR Codes"
ui wait 3
ui tap --id qr_tab_text
ui tap --desc qr_lang_en
ui wait 2
shot 03-2-haru-en.png
decode-qr 03-2-haru-en.png qr-haru-en.txt
ui tap --desc qr_lang_fr
ui wait 2
shot 03-2-haru-fr.png
decode-qr 03-2-haru-fr.png qr-haru-fr.txt
ui tap --desc qr_lang_ja
ui wait 2
ui find --id qr_frame_counter
ui find --id qr_hint_text
shot 03-2-haru-ja.png
decode-qr 03-2-haru-ja.png qr-haru-ja.txt
```
Attendu : un seul QR, ≤ 1 800 octets, dans chaque langue ; identité, allergie au soja et les **3 traitements
complets** (fexofénadine, dextrométhorphane, furosémide) toujours présents ; la coupe, si elle a lieu, tombe en fin
de ligne et se termine par `✂️ …`.
À relever précisément (défaut suspecté SD-11) : d'après le calcul hors appareil, le texte de Haru dépasse le plafond
dans 23 langues sur 25 et la section ♿ (autonomie : surdité, canne) est la première sacrifiée ; les attentes
T21.6 / T22.5 du README (« section ♿ présente ») ne tiendraient donc plus. Noter pour EN, FR, JA : la section ♿
est-elle présente ? la section 🤰 ? la marque `✂️` ? **L'écran prévient-il la personne que le contenu est coupé**
(texte sous le QR, `qr_hint_text`) ? Une coupe sans avertissement à l'écran est un ❌ (UC-QRT-008).

## Étape 3 — QR texte de Kamekichi : 5 traitements et alertes
```
ui back
ui back
ui tap --text "Kamekichi"
ui tap --id menu_profile_detail_export
ui tap --text "QR Codes"
ui wait 3
ui tap --id qr_tab_text
ui tap --desc qr_lang_ja
ui wait 2
shot 03-3-kamekichi-ja.png
decode-qr 03-3-kamekichi-ja.png qr-kamekichi-ja.txt
ui tap --desc qr_lang_en
ui wait 2
shot 03-3-kamekichi-en.png
decode-qr 03-3-kamekichi-en.png qr-kamekichi-en.txt
```
Attendu : 3 allergies (pénicilline et arachides graves) et les 5 traitements avec dose et rythme (`Warfarin 5mg`…
`5 mg`, `Daily`) ; pas de marque `✂️` ; ≤ 1 800 octets.

## Étape 4 — QR compact (UC-QRF-001, UC-QRF-002, UC-IMP-001)
```
ui tap --id qr_tab_pruned
ui wait 2
ui find --id qr_frame_counter
shot 03-4-kamekichi-compact.png
decode-qr 03-4-kamekichi-compact.png qr-kamekichi-compact.txt
```
Attendu : la charge décodée commence par `_j2:` (ou, s'il y a plusieurs images, par `JF:1/N:`) ; noter N. Si N > 1 :
❌ pour UC-QRF-002 tant qu'aucun lecteur ne réassemble les images (défaut connu du catalogue) ; capturer chaque image
avec `ui tap --id qr_btn_next_frame` + `shot` + `decode-qr`.
Même relevé pour Haru (profil le plus lourd) :
```
ui back
ui back
ui tap --text "Haru"
ui tap --id menu_profile_detail_export
ui tap --text "QR Codes"
ui wait 3
ui tap --id qr_tab_pruned
ui wait 2
ui find --id qr_frame_counter
shot 03-4-haru-compact.png
decode-qr 03-4-haru-compact.png qr-haru-compact.txt
```

## Étape 5 — Onglet FHIR (UC-QRF-002, SD-03)
```
ui tap --id qr_tab_fhir
ui wait 4
ui find --id qr_frame_counter
shot 03-5-haru-fhir.png
logcat
grep-log JEMMA-QR
grep-log FATAL EXCEPTION
validate
```
Attendu : l'onglet s'affiche sans plantage (série d'images animée ou message clair) ; aucune exception ; validateur
HL7 : 0 erreur pour les trois personas (`validator/summary.txt`).

## Outil manquant
- **Lecture par un second téléphone** (UC-QRF-001, UC-IMP-001, UC-IMP-004 : import, ré-import d'un profil modifié,
  avertissement d'écrasement) : aucun moyen de présenter une image à la caméra. Demander une action
  `import-payload <fichier.txt>` (ouvre l'écran d'import avec la charge décodée) ou un second appareil. Sans cela : ⏭,
  et ne pas conclure que l'import fonctionne.

## Restauration de l'état semé (obligatoire, même après un échec)

Ce scénario ne modifie aucun profil ; la vérification finale le prouve.

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
