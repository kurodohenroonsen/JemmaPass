# Captures manquantes pour le guide utilisateur

Tâche pour l'agent appareil (lane `qa/device`). Le guide `docs/guide/guide-utilisateur.md` a besoin des écrans ci-dessous. Aucune capture fiable n'existe pour eux sur `origin/device-reports`.

## Pourquoi elles manquent

- Les captures les plus récentes datent du run `d25d69a-20261002-0012` (commit `d25d69a`, 1er octobre, 20 h 06).
- Les écrans de sécurité (bandeau ambre, fenêtres « contrôle incomplet », voie inhalée, conflit de groupe sanguin, marqueur ✂️, scan en plusieurs images) sont arrivés après : commits `513b637`, `ee830d5`, `178b379`, `8a675b3`.
- Les rubriques 🤰 (écran d'édition, `3d6a5bf`) et ♿ (`e41c181`) sont aussi postérieures.
- Aucun run n'a capturé l'accueil, la liste des profils, l'identité, l'écran de partage, la carte PDF, l'import par QR ni les réglages.

## Règles

- Personas autorisés uniquement : `demo_kurodo`, `demo_haru`, `demo_kamekichi`. Aucune donnée personnelle.
- Deux écrans (B1, B2) n'existent que sans aucun profil. Ils se capturent sur un appareil vierge, avant la création des personas.
- Toute entrée de test ajoutée à un persona est supprimée en fin de run (`verify_profiles.py` vert).
- Langue par défaut : `fr`. Les langues supplémentaires sont indiquées ligne par ligne. Bascule : `cmd locale set-app-locales be.heyman.android.jemmapassdemo --locales <tag>`.
- Nom de fichier : `<n°>-<écran>-<état>[-<langue>].png`, dans `feat-ips-18-pillars-cleanup/<run>/screenshots/`. La numérotation reprend à 210.
- Le rapport du run dit, pour chaque capture, ce qu'elle montre (texte exact des bandeaux et des boutons).

## A. Priorité haute — sécurité

| # | Écran | État à capturer | Persona | Langue(s) | Fichier suggéré |
|---|---|---|---|---|---|
| A1 | Profil (`ProfileDetailFragment`) | Bandeau rouge « ⚠ N alerte(s) MAJEURE(S) détectée(s) » visible en haut, sans défilement | demo_kamekichi | fr, en, ja | `210-detail-red-banner-kamekichi-fr.png` (+ `-en`, `-ja`) |
| A2 | Profil | Bandeau rouge « ⚠ Autres alertes détectées » (aucune alerte majeure) | demo_haru | fr | `211-detail-red-banner-other-haru.png` |
| A3 | Profil | Bandeau ambre « ⓘ Contrôles de sécurité non effectués — base de connaissances indisponible » (base non téléchargée ou supprimée) | demo_haru | fr, en, de, nl, ja, zh | `212-detail-amber-not-checked-haru-fr.png` (+ 5 langues) |
| A4 | Profil | Bandeau ambre avec nombre : « … — 1 élément n'a pas pu être vérifié » (ajouter un médicament en texte libre) | demo_kurodo | fr | `213-detail-amber-incomplete-1-kurodo.png` |
| A5 | Profil | Bandeau ambre avec nombre au pluriel (deux médicaments en texte libre) | demo_kurodo | fr, de, nl | `214-detail-amber-incomplete-2-kurodo-fr.png` (+ `-de`, `-nl`) |
| A6 | Profil | Bandeau rouge ET bandeau ambre ensemble (alertes existantes + un médicament en texte libre) | demo_kamekichi | fr | `215-detail-red-and-amber-kamekichi.png` |
| A7 | Profil | Aucun bandeau (contrôles faits, rien trouvé) — pour montrer l'absence de vert | demo_kurodo | fr | `216-detail-no-banner-kurodo.png` |
| A8 | Formulaire médicament | Fenêtre « ⚠ Contrôle de sécurité incomplet », message « … n'a pas pu s'exécuter. Rien n'a été vérifié… », boutons « Enregistrer quand même » / « Revoir » | demo_haru | fr, de, nl, ja, zh | `217-med-gap-not-run-haru-fr.png` (+ 4 langues) |
| A9 | Formulaire médicament | Même fenêtre, message « « … » n'est pas reconnu par la base de connaissances… » | demo_haru | fr | `218-med-gap-unresolved-haru.png` |
| A10 | Formulaire médicament | Même fenêtre, message « Ces médicaments n'ont pas de code et n'ont PAS été inclus… » (liste longue : 3 médicaments en texte libre) | demo_kurodo | fr | `219-med-gap-uncoded-kurodo.png` |
| A11 | Formulaire allergie | Fenêtre « ⚠ Contrôle de sécurité incomplet » (les trois messages `allergy_form_xchk_gap_*`) | demo_kurodo | fr | `220-allergy-gap-not-run-kurodo.png`, `221-allergy-gap-unchecked-meds-kurodo.png`, `222-allergy-gap-incomplete-kurodo.png` |
| A12 | Formulaire médicament | Fenêtre « Interaction clinique détectée » (titre avec rond 🔴), boutons « Enregistrer quand même » / « Revoir » | demo_kamekichi | fr, en | `223-med-xchk-major-kamekichi-fr.png` (+ `-en`) |
| A13 | Formulaire médicament | Les cinq boutons de voie, « 🫁 Inhalée » sélectionné | demo_haru | fr, de, nl, ja, zh | `224-med-form-route-inhaled-haru-fr.png` (+ 4 langues) |
| A14 | Formulaire médicament | Même état sur l'écran le plus étroit disponible (Galaxy S20 FE) et avec la taille de police système au maximum | demo_haru | fr, de, zh | `225-med-form-routes-narrow-haru-fr.png` (+ `-de`, `-zh`) |
| A15 | Formulaire résultat | Fenêtre « Le groupe sanguin ne correspond pas au profil », boutons « Le modifier dans l'identité » / « Annuler » | demo_kurodo | fr, de, nl, ja, zh | `226-result-blood-conflict-kurodo-fr.png` (+ 4 langues) |
| A16 | Écran « Résultats » | Même fenêtre, affichée sur la liste (conflit signalé par le dépôt, voir `ResultsEditFragment.showBloodGroupConflict`) | demo_kurodo | fr | `227-results-blood-conflict-list-kurodo.png` |
| A17 | QR, onglet « 🌐 Texte » | QR avec marqueur ✂️ : puce de format et zone « CONTENU ENCODÉ » montrant « ✂️ … +N » et « ✂️ … [ FICHE INCOMPLÈTE ] » (charger le profil avec des entrées de test jusqu'à dépasser 1 800 octets) | demo_haru | fr, en, ja | `228-qr-text-truncated-haru-fr.png` (+ `-en`, `-ja`) ; joindre le texte décodé |
| A18 | Import par QR (`QrImportScanFragment`) | Progression multi-images « 🧩 2 / 5 · … 3 4 5 » | demo_kamekichi (QR affiché sur un second appareil) | fr | `229-import-multiframe-progress-kamekichi.png` |
| A19 | Import par QR | Fin de lecture « 🧩 N / N ✓ » puis fenêtre « Profil détecté 🐢 » | demo_kamekichi | fr | `230-import-multiframe-complete-kamekichi.png`, `231-import-confirm-kamekichi.png` |
| A20 | Fiche patient secouriste (`PatientDetailFragment`), panneau de scan | Étiquette ambre « ⚠ Non vérifié — le contrôle de sécurité n'a pas été exécuté » | demo_haru (victime) | fr, ja | `232-livescan-not-verified-haru-fr.png` (+ `-ja`) |
| A21 | Fiche patient secouriste | Étiquette ambre « ⚠ Contrôle incomplet — vérification partielle » | demo_haru | fr | `233-livescan-incomplete-haru.png` |
| A22 | Fiche patient secouriste | Étiquette verte « ✓ Aucune interaction détectée » | demo_haru | fr | `234-livescan-safe-haru.png` |
| A23 | Fiche patient secouriste | Bandeau rouge d'un résultat majeur (pour comparer rouge / orange / ambre) | demo_kamekichi | fr | `235-livescan-major-kamekichi.png` |

## B. Priorité moyenne — parcours du guide

| # | Écran | État à capturer | Persona | Langue(s) | Fichier suggéré |
|---|---|---|---|---|---|
| B1 | Autorisations (`PermissionsFragment`) | Premier lancement, avant « Tout accorder » | aucun profil (appareil vierge) | fr | `240-permissions-first-launch.png` |
| B2 | Profils, état vide | « Bienvenue à bord » et ses trois cartes | aucun profil (avant création des personas) | fr | `241-profiles-empty-state.png` |
| B3 | Profils, liste | Les trois personas, étoile sur le profil courant, bouton « Nouveau profil » | demo_kurodo (courant), demo_haru, demo_kamekichi | fr | `242-profiles-list.png` |
| B4 | Profils | Menu d'appui long (« ⭐ Définir comme profil courant », « 🗑 Supprimer le profil ») | demo_haru | fr | `243-profiles-longpress-haru.png` |
| B5 | Profils | Fenêtre « Supprimer le profil ? » (capturer puis « Annuler ») | demo_haru | fr | `244-profiles-delete-confirm-haru.png` |
| B6 | Identité (`PatientEditFragment`) | Haut du formulaire rempli | demo_kurodo | fr | `245-identity-top-kurodo.png` |
| B7 | Identité | Bas du formulaire (adresse, coordonnées, identifiant, médecin traitant, langue) | demo_kurodo | fr | `246-identity-bottom-kurodo.png` |
| B8 | Identité | Choix « 📅 Date précise » / « 🗓 Année seulement » | demo_kurodo | fr | `247-identity-birthdate-mode-kurodo.png` |
| B9 | Profil | Grille dépliée à jour (sous-titre « 11 piliers actifs, 7 à venir ») — remplace la capture `187-kurodo-detail-9-active.png` liée dans le guide | demo_kurodo | fr | `248-detail-pillars-11-active-kurodo.png` |
| B10 | Allergies | Fenêtre « Ajouter une allergie » (choix voix / manuel, choix grisés) | demo_kurodo | fr | `249-allergy-add-sheet-kurodo.png` |
| B11 | Allergies | Formulaire « 🩹 Ajouter une allergie » (haut et bas) | demo_kurodo | fr | `250-allergy-form-top-kurodo.png`, `251-allergy-form-bottom-kurodo.png` |
| B12 | Médicaments | Fenêtre « Ajouter un médicament » (choix « Scanner un médicament » avec badge « démo », choix grisés, « Saisir manuellement ») | demo_haru | fr | `252-med-add-sheet-haru.png` |
| B13 | Médicaments | Formulaire « 💊 Ajouter un médicament » complet (haut et bas) | demo_haru | fr | `253-med-form-top-haru.png`, `254-med-form-bottom-haru.png` |
| B14 | Médicaments | Liste à jour (remplace `80-haru-meds.png`, run du 30 septembre) | demo_haru | fr | `255-med-list-haru.png` |
| B15 | Autonomie et handicaps | Liste, puis formulaire « ♿ Ajouter une limitation » | demo_haru (entrée de test, supprimée ensuite) | fr | `256-functional-list-haru.png`, `257-functional-form-haru.png` |
| B16 | Grossesses | Écran complet (statut, terme, bilan), haut et bas | demo_haru | fr | `258-pregnancy-top-haru.png`, `259-pregnancy-bottom-haru.png` |
| B17 | Grossesses | Fenêtre « Effacer les grossesses ? » (capturer puis « Annuler ») | demo_haru | fr | `260-pregnancy-clear-confirm-haru.png` |
| B18 | Profil | Fenêtre « Partager le Pass Jemma » et ses deux choix | demo_haru | fr | `261-share-choice-haru.png` |
| B19 | QR, onglet « 📦 Compact » | Une seule image | demo_kurodo | fr | `262-qr-compact-single-kurodo.png` |
| B20 | QR, onglet « 📦 Compact » | Plusieurs images : compteur « 1 / N » et flèches (charger le profil si nécessaire) | demo_kamekichi | fr | `263-qr-compact-multiframe-kamekichi.png` |
| B21 | QR, troisième onglet (🏥) | Défilement en cours, bouton pause visible (remplace `60-qr-fhir.png`, run du 30 septembre) | demo_kurodo | fr | `264-qr-tab3-slideshow-kurodo.png` |
| B22 | QR, onglet « 🌐 Texte » | Rangée de drapeaux entière (25 langues), après le plafond de 1 800 octets (remplace 202–204) | demo_haru | fr | `265-qr-text-lang-chips-haru.png` |
| B23 | Carte PDF | Page 1 (feuille à plier) et page 2 (grands QR), rendues en image | demo_haru | fr, ja | `266-pdf-page1-haru-fr.png`, `267-pdf-page2-haru-fr.png` (+ `-ja`) |
| B24 | Carte PDF | Page 1 avec lignes « +N » (listes trop longues : plus de 13 lignes allergies + maladies, plus de 18 médicaments) | demo_kamekichi (entrées de test, supprimées ensuite) | fr, en | `268-pdf-page1-overflow-kamekichi-fr.png` (+ `-en`) |
| B25 | Import par QR | Écran de visée avec la consigne initiale | demo_kurodo (profil courant ; aucun QR dans le champ) | fr | `269-import-hint.png` |
| B26 | Import par QR | Fenêtre « QR non lisible » | demo_kurodo (profil courant ; QR quelconque non JemmaPass) | fr | `270-import-error.png` |
| B27 | Réglages | Carte « Préparer la démo » avant téléchargement | demo_kurodo (profil courant) | fr | `271-settings-prepare.png` |
| B28 | Réglages | Parties « Langue de l'interface » et « Confidentialité » | demo_kurodo (profil courant) | fr | `272-settings-lang-privacy.png` |
| B29 | SOS/Secours | Écran d'accueil et fenêtre « Choisis ton rôle » | demo_haru (courant) | fr | `273-sos-hub.png`, `274-sos-mode-select.png` |
| B30 | Radar secouriste | Bouton « Scanner QR » visible | demo_kamekichi (courant) | fr | `275-radar-scan-qr-kamekichi.png` |

## C. Revue visuelle multilingue (pour l'équipe design)

| # | Écran | État à capturer | Persona | Langue(s) | Fichier suggéré |
|---|---|---|---|---|---|
| C1 | Profil | Haut de l'écran avec la grille dépliée | demo_kamekichi | de, nl, ja, zh | `280-detail-pillars-kamekichi-de.png` (+ `-nl`, `-ja`, `-zh`) |
| C2 | Formulaire résultat | Formulaire complet | demo_kurodo | de, nl, zh | `281-result-form-kurodo-de.png` (+ `-nl`, `-zh`) |
| C3 | QR | Barre des trois onglets et boutons du bas | demo_haru | de, nl, ja, zh | `282-qr-viewer-haru-de.png` (+ `-nl`, `-ja`, `-zh`) |
| C4 | Profil | Bandeaux avec police système au maximum (200 %) | demo_kamekichi | fr, de | `283-detail-banners-fontscale-kamekichi-fr.png` (+ `-de`) |
| C5 | Tout écran de formulaire | Lecture TalkBack : transcription du texte annoncé pour le bandeau ambre, les boutons étoile / corbeille de la liste des profils et les drapeaux du QR | demo_haru | fr | `284-talkback-notes.md` (texte, pas d'image) |

## Captures liées dans le guide mais à rafraîchir

Elles montrent le bon écran. Leur contenu a vieilli.

| Capture actuelle | Problème | Remplacée par |
|---|---|---|
| `e53075e-…/187-kurodo-detail-9-active.png` | Sous-titre « 9 piliers actifs, 9 à venir » ; il y en a 11 | B9 |
| `1c1b7e5-…/80-haru-meds.png` | Run du 30 septembre | B14 |
| `f7c500e-…/60-qr-fhir.png` | Run du 30 septembre | B21 |
| `d25d69a-…/202-204-qr-text-*.png` | Antérieures au plafond de 1 800 octets et au marqueur ✂️ | B22, A17 |
| `e093854-…/200-dd-kamekichi.png` | Le rapport décrit la liste des alertes ; il ne dit pas si le bandeau rouge est dans le cadre | A1 |
