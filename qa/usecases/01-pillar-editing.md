# 01 — Édition des piliers santé : catalogue de micro cas d'usage

Dérivé de la lecture du code (pas de supposition) : `ui/profile/**`, `ips/*.kt`, `pillars/*Catalog.kt`,
`profiles/ProfilesRepository.kt`, `qr/JemmaFhirBundleBuilder.kt`, `qr/JemmaTextPayloadBuilder.kt`,
les tests JVM de `app/src/test/**` et le protocole `qa/device/README.md` (T1…T22).

## Conventions

- `…/` = `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/`.
- **Couverture** : `Fichier::fonction` = test JVM (`app/src/test/…`) ; `Tn.m` = étape du protocole appareil ;
  `Tn (libellé)` = ligne d'un tableau du protocole ; **partiel** = seul le codec / le domaine est testé, pas le
  chemin écran ; **AUCUNE** = rien ne le couvre aujourd'hui.
- **Résultat attendu** : comportement voulu pour l'utilisateur. Quand le code fait autre chose, c'est noté
  « code actuel : … » avec fichier:ligne.
- `_j` = projection JSON `<sid>.json` (QR / mesh) ; Bundle = `<sid>.fhir.json` (source de vérité des piliers natifs).

## Constats transverses (valables pour tous les tableaux)

1. **Rotation** : `MainActivity` est verrouillée en portrait (`AndroidManifest.xml:182`, `configChanges="uiMode"`).
   Les lignes « Rotation » de T9 et T12 ne recréent donc rien sur téléphone : elles sont **inopérantes**. La
   recréation réelle arrive par : mort du processus en arrière-plan, changement de langue ou de taille de police,
   multi-fenêtre, tablette. Aucun test ne la provoque.
2. **État des formulaires** : aucun `onSaveInstanceState`. Après recréation, les champs texte reviennent (état de
   vue), mais tout ce qui a été *choisi* (code, dates, statut, sévérité, unité) est relu des arguments d'origine.
   Un sélecteur ouvert est restauré **sans callback** : le choix est ignoré (`…/ui/common/IpsCodePickerDialog.kt:370`,
   `KbConditionPicker.kt:172`, `KbDrugPickerDialog.kt:226`).
3. **Dates** : tous les calendriers utilisent `CalendarConstraints.setEnd(aujourd'hui)` sans validateur de jour
   (9 fichiers) et une sélection par défaut en jour **UTC**. Seul le formulaire 🩺/📜/♿ propose « année seulement ».
4. **Anti double-tap** : présent sur 💉 🏥 📟 🧪 🩺 📜 ♿ (bouton désactivé au premier appui) ; **absent** sur
   allergies, médicaments et identité.
5. **Suppression** : bouton « Supprimer » + appui long sur les piliers natifs (par `id`) ; appui long seulement, par
   **position**, sur allergies, médicaments, contacts.
6. **Écriture** : `ProfilesRepository.writeProfileFiles` écrit `_j` puis le Bundle par `writeText` (non atomique,
   sans verrou). Un échec du Bundle est avalé (`:401`) et l'enregistrement répond « ok ».
7. **Listes vides** : aucune section FHIR n'est émise pour un pilier vide (y compris allergies, médicaments,
   problèmes, obligatoires dans l'IPS).
8. **Doublons** : aucune détection, dans aucun pilier.
9. **Statuts dans le QR texte** : suffixe brut anglais (`(not-done)`, `(inactive)`, `(entered-in-error)`) quelle que
   soit la langue (`…/qr/JemmaTextPayloadBuilder.kt:252-300`).

---

## 1. Identité du patient 👤 (`ui/profile/perso`, `ui/profile/contacts`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-PAT-001 | Voyageur qui crée son passeport avant le départ | nominal | Nouveau profil → prénom, nom, sexe, date de naissance (calendrier), groupe O+ → Enregistrer | Toast « créé », retour ; `_j.p{gn,fn,gs,bd,bt}` ; Bundle : Patient + Observation 882-1 dérivée | écran : **AUCUNE** ; partiel IpsBloodGroupTest::bundleHasAnIdentifierNoEmptyNamePartsAndNoHomeMadeExtension | haut |
| UC-PAT-002 | Aidant qui corrige le groupe sanguin d'un parent (A+ → O−) | nominal | Fiche → Patient → groupe → O- → Enregistrer | `p.bt`="O-" ; Observation `rs-blood-group-<sid>` passe à SNOMED 278148006 ; une seule 882-1 | partiel IpsBloodGroupTest::syncKeepsExactlyOneBloodGroupResult (sync pur) ; écran **AUCUNE** | haut |
| UC-PAT-003 | Personne qui retire un groupe sanguin douteux | alternatif | groupe → « — » → Enregistrer | `p.bt` absent ; Observation dérivée retirée ; autres résultats intacts | partiel IpsBloodGroupTest::syncKeepsExactlyOneBloodGroupResult (cas `null`) | haut |
| UC-PAT-004 | Utilisateur pressé qui valide un formulaire vide | erreur | Enregistrer sans prénom ni nom | toast « nom requis », focus prénom, rien écrit | **AUCUNE** | bas |
| UC-PAT-005 | Personne âgée ou réfugiée dont on ne connaît que l'année de naissance ; profil importé avec `bd`="1946" | limite | ouvrir l'identité, changer le téléphone, Enregistrer | attendu : enregistrement possible avec une date partielle ; code actuel : refus tant qu'une date complète n'est pas choisie (`…/ui/profile/perso/PatientEditFragment.kt:591`) → date inventée | **AUCUNE** | moyen |
| UC-PAT-006 | Voyageur aux États-Unis le soir (jour UTC = demain) | limite | ouvrir le calendrier de naissance, OK sans rien choisir | attendu : aucun jour futur ; code actuel : `setEnd` sans validateur (`:526`), défaut = jour UTC | **AUCUNE** | moyen |
| UC-PAT-007 | Personne qui tremble et appuie deux fois sur Enregistrer à la création | erreur | nouveau profil rempli → double appui | attendu : 1 profil ; code actuel : aucune garde (`:549-554`), `sid` nul → deux identifiants générés (`ProfilesRepository.kt:185`) | **AUCUNE** | moyen |
| UC-PAT-008 | Utilisateur qui change d'avis | alternatif | modifier 3 champs → flèche retour | rien écrit, aucun message ; fichiers inchangés | **AUCUNE** | bas |
| UC-PAT-009 | Appel entrant pendant la saisie, Android tue l'app ; ou changement de langue du système | limite | saisir nom + date → app en arrière-plan → `am kill` → revenir | attendu : saisie retrouvée ; code actuel : `populateForm` asynchrone réécrit les champs avec le disque, date et langue choisies perdues | **AUCUNE** | moyen |
| UC-PAT-010 | Patient japonais, arabe, ou nom très long avec emoji | limite | prénom « 太郎 », nom « محمد », 200 caractères + 🙂 | stocké tel quel en UTF-8 ; affichage sans coupure fautive ; QR texte ≤ 2 200 octets (sections de fin tronquées) | partiel JemmaSosChunkCodecTest (identité SOS) ; édition **AUCUNE** | moyen |
| UC-PAT-011 | Profil importé avec 2 adresses, 2 téléphones, 2 identifiants | limite | corriger le prénom → Enregistrer | attendu : tout conservé ; code actuel : seul le premier de chaque liste est réécrit (`:614-635`) | **AUCUNE** | moyen |
| UC-PAT-012 | Utilisateur francophone qui passe l'appareil en japonais | alternatif | changer la langue → rouvrir l'identité | libellés (sexe, pays, langue, usage d'adresse) en japonais ; codes stockés inchangés | **AUCUNE** | bas |
| UC-PAT-013 | Aidant qui corrige l'adresse d'un profil complet (Haru) | nominal | modifier l'adresse → Enregistrer → `verify_profiles.py` | allergies, médicaments, 8 piliers natifs et contacts (`p.ct`) inchangés | **AUCUNE** (T4 ne couvre que l'édition d'allergie) | haut |
| UC-PAT-014 | Profil reçu par QR sans `sid` dans le JSON | limite | importer → éditer l'identité deux fois | un seul fichier ; `sid` = nom du fichier (`ProfilesRepository.kt:436-445`) | **AUCUNE** | moyen |
| UC-PAT-015 | Même personne reçue deux fois (QR puis mesh) | limite | importer deux fois | même `sid` → écrasement ; `sid` différent → deux profils sans alerte de doublon | **AUCUNE** | moyen |
| UC-PAT-016 | Fille qui ajoute le médecin traitant comme contact d'urgence | nominal | Contacts → + → « Dr Martin », téléphone → Enregistrer | relation « médecin » détectée ; téléphone normalisé ; `p.ct[]{n,r,p}` | **AUCUNE** | haut |
| UC-PAT-017 | Faute de frappe dans le numéro du contact d'urgence (« 123 ») | erreur | saisir un numéro trop court → Enregistrer | attendu : refus ; code actuel : toast d'erreur mais enregistrement quand même (« soft », `…/ui/profile/contacts/ContactFormBottomSheet.kt:299-309`) | **AUCUNE** | haut |
| UC-PAT-018 | Recherche de la relation « mère » sans accent, KB absente | alternatif | sélecteur de relation → taper « mere » | « Mère » trouvée (catalogue embarqué, accents repliés) | partiel IpsVaccineCatalogTest::searchAliasesLetAFrenchUserTypeTheInternationalName (fonction `normalizeForPickerSearch`) | bas |
| UC-PAT-019 | Suppression d'un contact périmé | nominal | appui long → confirmer (pas de bouton dans le formulaire) | contact retiré par position ; reste du profil intact | **AUCUNE** | moyen |

## 2. Allergies 🩹 (`ui/profile/allergies`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-ALG-001 | Parent qui déclare l'allergie à la pénicilline de son enfant | nominal | + → Saisir manuellement → substance (sélecteur) → Allergie, criticité élevée, réaction anaphylaxie sévère → Enregistrer | carte ; `al[]{c,cs=SNOMED,s:H,st:A,tp,cat,rxns[1]}` ; AllergyIntolerance dans le Bundle | **AUCUNE** | haut |
| UC-ALG-002 | Allergène absent du catalogue (fruit rare, produit local) | limite | chercher, ne rien trouver | attendu : saisie libre possible ; code actuel : substance codée obligatoire (`…/ui/profile/allergies/AllergyFormBottomSheet.kt:736`) → allergie non enregistrable | **AUCUNE** | haut |
| UC-ALG-003 | Aidant qui complète la note d'une allergie | nominal | ouvrir la carte → note → Enregistrer | même position ; `al[i].d` modifié | T4 | moyen |
| UC-ALG-004 | Allergie importée ou dictée avec 2 réactions (urticaire + anaphylaxie) | limite | ouvrir → changer la criticité → Enregistrer | attendu : les 2 réactions conservées ; code actuel : seule la première survit (`AllergyFormBottomSheet.kt:157`, `AllergiesEditFragment.kt:261-270`) | **AUCUNE** | haut |
| UC-ALG-005 | Allergie importée avec libellé mais sans code | limite | ouvrir la carte | attendu : libellé visible et modifiable ; code actuel : substance affichée vide, Enregistrer refusé | **AUCUNE** | moyen |
| UC-ALG-006 | Allergie saisie par erreur | nominal | appui long → confirmer (pas de bouton Supprimer dans le formulaire) | entrée retirée par position ; `al` et Bundle à jour | **AUCUNE** | moyen |
| UC-ALG-007 | Personne sans allergie connue | limite | profil neuf, ouvrir Allergies | état vide, compteur 0 ; attendu Bundle : section 48765-2 « aucune allergie connue » ; code actuel : section absente (`…/qr/JemmaFhirBundleBuilder.kt:368,429`) → « aucune » indistinguable de « non renseigné » | **AUCUNE** | haut |
| UC-ALG-008 | Utilisateur qui annule | alternatif | remplir → Annuler | aucune carte, fichiers inchangés | **AUCUNE** | bas |
| UC-ALG-009 | Double appui sur Enregistrer | erreur | substance choisie → 2 appuis rapides | attendu : 1 carte ; code actuel : aucune garde, contrôle croisé asynchrone (`AllergyFormBottomSheet.kt:727`) → 2 résultats possibles | **AUCUNE** | moyen |
| UC-ALG-010 | App tuée en arrière-plan, formulaire ou sélecteur ouvert | limite | choisir substance + date → `am kill` → revenir | attendu : choix conservés ; code actuel : choix relus des arguments, sélecteur restauré inerte | **AUCUNE** | moyen |
| UC-ALG-011 | « Depuis l'enfance, vers 1985 » ; ou date de début future | limite | date de début : année seule ; jour après aujourd'hui | attendu : année seule possible, jour futur refusé ; code actuel : calendrier seul, `setEnd` sans validateur (`:607`) ; un `on`="2010" importé est conservé si on n'y touche pas | **AUCUNE** | bas |
| UC-ALG-012 | Note en japonais, arabe, emoji, 2 000 caractères | limite | saisir dans note et mécanisme → Enregistrer | texte intact après relecture ; pas de plantage ; QR texte plafonné | **AUCUNE** | bas |
| UC-ALG-013 | Même allergie ajoutée deux fois (parent puis aidant) | limite | créer deux fois la pénicilline | 2 cartes, aucune alerte de doublon (comportement actuel) ; attendu : avertissement | **AUCUNE** | bas |
| UC-ALG-014 | Premier lancement, KB ou traductions pas encore prêtes | erreur | ouvrir le sélecteur de substance | toast « catalogue indisponible », ajout impossible ; KB absente seule : pas de puces de catégorie, catégorie à choisir à la main | **AUCUNE** | haut |
| UC-ALG-015 | Recherche « pénicilline » ou « penicilline » | alternatif | taper avec puis sans accent | même résultat | partiel (fonction `normalizeForPickerSearch`, IpsVaccineCatalogTest::searchAliasesLetAFrenchUserTypeTheInternationalName) | moyen |
| UC-ALG-016 | Appareil passé du français au japonais | alternatif | changer la langue → liste des allergies | libellés relocalisés ; `c` inchangé | **AUCUNE** | bas |
| UC-ALG-017 | Ajout d'une allergie à la pénicilline alors que l'amoxicilline est dans les médicaments | nominal | choisir la substance | alerte de conflit ; « Annuler » vide la substance ; « Enregistrer quand même » la garde | **AUCUNE** (T17–T19 ne testent que les alertes de la fiche) | haut |
| UC-ALG-018 | Contrôle croisé impossible (KB fermée, exception) | erreur | même scénario, KB indisponible | attendu : message « contrôle non effectué » ; code actuel : silence, enregistrement direct (`…/ui/profile/common/FormCrossCheckHelper.kt:107,181`) | **AUCUNE** | haut |
| UC-ALG-019 | Édition d'une allergie sur un profil riche | nominal | éditer → Enregistrer → `verify_profiles.py` | vaccins, procédures et autres piliers natifs intacts (Bundle autoritaire) | T4, T13 | haut |
| UC-ALG-020 | Allergies importées d'un QR (aucun identifiant, édition par index) | limite | éditer la 2ᵉ de 3 | seule la 2ᵉ change, ordre conservé | **AUCUNE** | moyen |

## 3. Médicaments 💊 (`ui/profile/medications`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-MED-001 | Retraité qui ajoute son anticoagulant | nominal | + → Saisir manuellement → chercher « warfa » → choisir → dose pré-remplie (DDD) → posologie → Enregistrer | carte ; `md[]{c,cs,d_display,r,v,u,t,ms:active}` ; MedicationStatement avec `dosage` | **AUCUNE** | haut |
| UC-MED-002 | Médicament local ou préparation absent de la KB | limite | chercher, ne rien trouver | attendu : saisie libre ; code actuel : substance codée obligatoire (`…/ui/profile/medications/MedicationFormBottomSheet.kt:606`) | **AUCUNE** | haut |
| UC-MED-003 | Changement de dose prescrit (5 → 2,5 mg) | nominal | ouvrir la carte → dose → Enregistrer | même position ; `v` mis à jour | **AUCUNE** | haut |
| UC-MED-004 | Médicament arrêté à retirer | nominal | appui long → confirmer (pas de bouton Supprimer) | entrée retirée par position | **AUCUNE** | haut |
| UC-MED-005 | Personne sans traitement | limite | profil neuf, ouvrir Médicaments | état vide ; attendu Bundle : section 10160-0 « aucun médicament connu » ; code actuel : section absente (`JemmaFhirBundleBuilder.kt:369,429`) | **AUCUNE** | moyen |
| UC-MED-006 | Annulation | alternatif | remplir → Annuler | aucune carte, fichiers inchangés | **AUCUNE** | bas |
| UC-MED-007 | Double appui sur Enregistrer | erreur | substance choisie → 2 appuis rapides | attendu : 1 carte ; code actuel : aucune garde (`:597`), 2 coroutines de contrôle → 2 résultats possibles | **AUCUNE** | moyen |
| UC-MED-008 | App tuée, formulaire ouvert en édition | limite | modifier voie + statut → `am kill` → revenir → Enregistrer | attendu : choix conservés, bonne ligne modifiée ; code actuel : choix perdus, édition par index | **AUCUNE** | moyen |
| UC-MED-009 | « Je le prends depuis 2020 » | limite | date de début | attendu : année seule possible ; code actuel : calendrier seul ; un `eff`="2020" importé est déclaré invalide mais renvoyé tel quel (`:626-630`, remise à zéro sans effet) | **AUCUNE** | bas |
| UC-MED-010 | Date de début future | erreur | calendrier, jour après aujourd'hui | attendu : refus ; code actuel : `setEnd` sans validateur (`:506`) | **AUCUNE** | bas |
| UC-MED-011 | Dose 0 ou négative | erreur | dose « 0 » → Enregistrer | toast « dose invalide », focus, rien écrit | **AUCUNE** | moyen |
| UC-MED-012 | Francophone qui tape « 0,5 » | limite | dose « 0,5 » mg → Enregistrer | attendu : `v`="0.5" et `doseAndRate.doseQuantity.value`=0.5 ; code actuel : `v`="0,5" stocké, quantité FHIR omise (`JemmaFhirBundleBuilder.kt:282`, `toDoubleOrNull`) | **AUCUNE** | haut |
| UC-MED-013 | Dose énorme ou notation exotique (99999, « 1e3 », valeur importée « NaN ») | limite | saisir ou importer | attendu : refus ou confirmation ; code actuel : accepté si `toDoubleOrNull` ≠ null et > 0 ; NaN passe le test `<= 0` | **AUCUNE** | moyen |
| UC-MED-014 | Posologie longue, unité libre, japonais, emoji | limite | « 朝1錠・夕1錠 🙂 » × 500 caractères | texte intact ; QR texte plafonné | **AUCUNE** | bas |
| UC-MED-015 | Même médicament saisi deux fois (générique et marque) | limite | ajouter deux fois le même ATC | attendu : avertissement de doublon ; code actuel : aucune détection dédiée | **AUCUNE** | moyen |
| UC-MED-016 | KB pas prête ou en erreur | erreur | ouvrir le sélecteur, chercher | statut « erreur », liste vide ; aucun ajout possible (pas de texte libre) | **AUCUNE** | haut |
| UC-MED-017 | Recherche « paracétamol » avec accent | alternatif | taper avec puis sans accent | mêmes résultats (accents retirés avant la requête KB) | **AUCUNE** | moyen |
| UC-MED-018 | Appareil passé en japonais | alternatif | changer la langue → liste et formulaire | libellés et statuts localisés ; codes et dose inchangés | **AUCUNE** | bas |
| UC-MED-019 | Ajout d'amoxicilline chez un allergique à la pénicilline | nominal | choisir la substance | alerte allergie × médicament ; « Annuler » vide la substance | **AUCUNE** (formulaire) | haut |
| UC-MED-020 | Ajout d'ibuprofène chez un patient sous warfarine | nominal | Enregistrer | alerte d'interaction ; « Enregistrer quand même » valide, « Annuler » reste sur le formulaire | **AUCUNE** (formulaire) | haut |
| UC-MED-021 | Asthmatique qui ajoute son inhalateur | limite | choisir un médicament dont la voie KB est « inhal.aerosol » | attendu : voie inhalée ; code actuel : voie « I » = Injection (`:423-425`), exportée SNOMED 47625008 intraveineuse (`…/pillars/IpsRouteCatalog.kt:50-52`) | **AUCUNE** | haut |
| UC-MED-022 | Édition d'un médicament sur un profil riche | nominal | éditer → Enregistrer → `verify_profiles.py` | piliers natifs et allergies intacts | partiel T4 (même chemin `MANUAL_EDIT`, testé via l'allergie) | haut |
| UC-MED-023 | Médicament importé sans code, ou index périmé après restauration | limite | ouvrir une carte sans code ; ou Enregistrer avec un index hors liste | attendu : libellé éditable ; erreur visible ; code actuel : substance vide et refus ; index invalide ignoré mais toast « enregistré » (`MedicationsEditFragment.kt:289-300`) | **AUCUNE** | moyen |

## 4. Problèmes actifs 🩺 (`ui/profile/pastproblems`, mode `current`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-PRB-001 | Diabétique qui déclare son diabète de type 2 | nominal | + → sélecteur → « diabete » → début « année seulement » 2019, statut actif, sévérité modérée → Enregistrer | carte ; pas de bloc de fin ; Condition `problem-list-item`, `clinicalStatus` active, `onsetDateTime` 2019, section 11450-4 ; `_j.cn[]{c,st,s,dt}` | T16.3 ; IpsProblemCodecTest::jsonCarriesIpsEssentials | haut |
| UC-PRB-002 | Problème absent du jeu SNOMED IPS (« Lombalgie chronique ») | alternatif | texte libre → Enregistrer | `code.text` sans `coding` | partiel IpsProblemCodecTest::fullRoundTrip (codec) ; écran 🩺 **AUCUNE** | moyen |
| UC-PRB-003 | Rechute signalée par le patient | nominal | ouvrir → statut « rechute » → Enregistrer | `clinicalStatus` relapse ; `_j.cn[].st`="relapse" | T16.4 ; IpsProblemCodecTest::statusNormalization | moyen |
| UC-PRB-004 | Problème saisi par erreur | nominal | a) bouton Supprimer du formulaire ; b) appui long | carte retirée par `id` ; Bundle et `_j.cn` à jour | b) T16.4 ; a) **AUCUNE** pour 🩺 (T15.6 teste le même code en mode 📜) | moyen |
| UC-PRB-005 | Personne en bonne santé | limite | profil sans problème | état vide ; attendu Bundle : section 11450-4 « aucun problème connu » (obligatoire IPS) ; code actuel : section absente (`…/ips/IpsFhirCodec.kt:880`) | **AUCUNE** | moyen |
| UC-PRB-006 | Annulation | alternatif | remplir → Annuler | aucune carte, fichiers inchangés | **AUCUNE** | bas |
| UC-PRB-007 | Double appui sur Enregistrer | erreur | 2 appuis rapides | 1 seule carte (garde `isEnabled`, `PastProblemFormBottomSheet.kt:424`) | **AUCUNE** pour ce formulaire | moyen |
| UC-PRB-008 | App tuée, formulaire ouvert | limite | choisir code, année, statut → `am kill` → revenir | attendu : choix conservés ; code actuel : code, dates, statut, sévérité relus des arguments ; texte libre conservé | **AUCUNE** | moyen |
| UC-PRB-009 | Année absurde, ou date exacte future | erreur | « année seulement » → 1850 puis année future ; « date exacte » → jour après aujourd'hui | année : toast « entre 1900 et cette année » (`:374`) ; jour futur : attendu refus ; code actuel : `setEnd` sans validateur (`:343`), aucune vérification à l'enregistrement | partiel T15.4 (1850, en mode 📜) ; jour futur **AUCUNE** | bas |
| UC-PRB-010 | Texte libre long, japonais, RTL, emoji | limite | « 慢性腰痛 🙂 » + 1 000 caractères en note | intact après relecture du Bundle ; carte lisible | partiel IpsProblemCodecTest::fullRoundTrip (ASCII seulement) | bas |
| UC-PRB-011 | Même problème saisi deux fois, ou déjà présent dans 📜 | limite | créer deux fois le même code | 2 Conditions d'`id` distincts ; aucune alerte (actuel) | **AUCUNE** | moyen |
| UC-PRB-012 | KB pas prête | erreur | ouvrir le sélecteur | liste vide ; le texte libre reste possible | **AUCUNE** | moyen |
| UC-PRB-013 | Recherche « diabète » avec accent | alternatif | taper avec puis sans accent | mêmes résultats | partiel T16.3 (ASCII seulement : `ui.py` ne saisit pas les accents) | moyen |
| UC-PRB-014 | Francophone : libellé affiché en français, `Coding.display` en anglais | limite | choisir un code puis Enregistrer **immédiatement** | attendu : `display` anglais ; code actuel : terme anglais récupéré en asynchrone (`:298-304`), libellé localisé stocké si on enregistre avant | T15.2 vérifie l'anglais en mode 📜 ; course **AUCUNE** | bas |
| UC-PRB-015 | Édition d'un problème sur Haru | nominal | éditer → `verify_profiles.py` | 📜 2 et 🩺 2 inchangés ; aucune Condition active dans 11348-0 | T16.5 ; IpsProblemCodecTest::problemsAndPastProblemsLiveInTheirOwnSections ; IpsFunctionalCodecTest::threeConditionPillarsNeverMix | haut |
| UC-PRB-016 | Problèmes importés d'un QR ancien (statut « A », sévérité « H », pas d'`id`) | limite | importer → ouvrir 🩺 → éditer | statut actif, sévérité sévère ; `id` stable entre deux lectures | IpsProblemCodecTest::projectionKeepsTheLegacyShape ; ::legacyProblemListConditionsAreReadWithAStableId | moyen |
| UC-PRB-017 | `cn` importé avec `st`="resolved" | limite | importer → ouvrir 🩺 | attendu : entrée rangée dans 📜 ou signalée ; code actuel : normalisée en « active » | IpsProblemCodecTest::statusNormalization (fige ce comportement) | moyen |
| UC-PRB-018 | Nouveau problème rendant un médicament du profil risqué | alternatif | ajouter « maladie rénale chronique » chez un patient sous AINS | alerte médicament × maladie sur la fiche après enregistrement (aucun contrôle dans ce formulaire) | partiel T18.1, T19 ; DrugDiseaseTermsTest | haut |

## 5. Antécédents médicaux 📜 (`ui/profile/pastproblems`, mode `past`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-ANT-001 | Adulte qui note sa rougeole d'enfance | nominal | + → « rougeole » → début et fin « année seulement » 1975 → guérie, légère → Enregistrer | carte « 1975 → 1975 · Guérie » ; Condition `resolved`, `display` anglais, sévérité LA6752-5, section 11348-0 ; `_j.ph[]{c,dt,ab,sv}` | T15.2 ; IpsPastProblemCodecTest::fullRoundTrip, ::projectionContract | moyen |
| UC-ANT-002 | Maladie non codable (« Hépatite virale, enfance ») | alternatif | texte libre, statut inactive, aucune date | `code.text` sans `coding` ; ni `onset` ni `abatement` ni `severity` | T15.3 ; IpsPastProblemCodecTest::freeTextUndatedRemission | moyen |
| UC-ANT-003 | Correction de la sévérité | nominal | ouvrir → sévérité sévère → Enregistrer | `severity` LA6750-9 ; `_j.ph[].sv` identique | T15.5 | bas |
| UC-ANT-004 | Antécédent saisi par erreur | nominal | a) bouton Supprimer ; b) appui long | carte retirée ; compteur à jour | T15.6 | moyen |
| UC-ANT-005 | Aucun antécédent | limite | profil sans antécédent | état vide ; pas de section 11348-0 | IpsPastProblemCodecTest::noSectionWhenThePillarIsEmpty ; écran **AUCUNE** | bas |
| UC-ANT-006 | Annulation | alternatif | remplir → Annuler | rien écrit | **AUCUNE** | bas |
| UC-ANT-007 | Double appui sur Enregistrer | erreur | 2 appuis rapides | 1 carte | **AUCUNE** pour ce formulaire | moyen |
| UC-ANT-008 | App tuée, formulaire ouvert | limite | choisir code + 2 dates → `am kill` → revenir | attendu : choix conservés ; code actuel : relus des arguments | **AUCUNE** | moyen |
| UC-ANT-009 | Fin avant le début | erreur | début 2010-05-01, fin « année » 2005 → Enregistrer | message rouge sous la fin + toast ; rien écrit | T15.4 ; IpsPastProblemCodecTest::abatementBeforeOnsetIsRejected | moyen |
| UC-ANT-010 | Précisions mêlées : début au jour, fin à l'année de la même année | limite | début 2010-05-01, fin 2010 | accepté (comparaison sur le préfixe commun) | partiel IpsPastProblemCodecTest::abatementBeforeOnsetIsRejected (sens inverse seulement) | bas |
| UC-ANT-011 | Année absurde (1850, année future) | erreur | « année seulement » | toast, date inchangée | T15.4 (1850) ; année future **AUCUNE** | bas |
| UC-ANT-012 | Texte long, japonais, RTL, emoji | limite | « 結核 🙂 » + note de 1 000 caractères | intact ; pas de plantage | partiel IpsPastProblemCodecTest::freeTextUndatedRemission (accents français) | bas |
| UC-ANT-013 | Même antécédent saisi deux fois | limite | créer deux fois | 2 entrées, aucune alerte (actuel) | **AUCUNE** | bas |
| UC-ANT-014 | KB pas prête | erreur | ouvrir le sélecteur | liste vide ; texte libre possible | **AUCUNE** | moyen |
| UC-ANT-015 | Recherche avec accent (« hépatite ») | alternatif | avec puis sans accent | mêmes résultats | partiel T15.2 (ASCII seulement) | bas |
| UC-ANT-016 | Appareil en japonais | alternatif | changer la langue → liste et QR texte | libellés KB en japonais ; `display` anglais dans le Bundle | T15.2, T15.7 ; PastProblemsTextQrTest::localisedLabelsWinOverTheEnglishTerm | bas |
| UC-ANT-017 | Antécédents importés d'un QR sans `id` | limite | importer → éditer le 1er | `id` déterministes ; `ab` et `sv` conservés | IpsPastProblemCodecTest::legacyJArrayRebuild, ::projectionIsDeterministic, ::projectionContract | moyen |
| UC-ANT-018 | Problème actif désormais guéri | limite | passer une entrée de 🩺 à 📜 | attendu : action « déplacer » ; code actuel : supprimer d'un côté et recréer de l'autre (oubli ou doublon possible) | **AUCUNE** | moyen |

## 6. Vaccinations 💉 (`ui/profile/immunizations`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-VAC-001 | Voyageur qui ajoute son vaccin grippe | nominal | + → sélecteur → « influenza » → date → dose 1, lot → Enregistrer | carte en tête ; Immunization `Immunization-uv-ips`, `lotNumber`, `doseNumberPositiveInt` ; `_j.im[]{c,dt,dn}` | T2 ; IpsImmunizationCodecTest::fullEntrySurvivesFhirRoundTrip, ::fhirJsonCarriesTheIpsEssentials | moyen |
| UC-VAC-002 | « Vaccin du village, 1985 » | alternatif | texte libre, date inconnue → Enregistrer | `vaccineCode.text` sans `coding` ; `occurrenceString` "unknown" ; carte « date inconnue » | T8.1 ; IpsImmunizationCodecTest::freeTextVaccineWithoutCodeRoundTrips, ::unknownDateBecomesOccurrenceStringAndComesBackNull | moyen |
| UC-VAC-003 | Ajout du fabricant après coup | nominal | ouvrir → fabricant → Enregistrer | même `id`, pas de doublon | T3 | bas |
| UC-VAC-004 | Vaccin saisi par erreur | nominal | a) bouton Supprimer ; b) appui long | carte retirée ; compteur à jour | T3 a) et b) | moyen |
| UC-VAC-005 | Aucun vaccin connu | limite | profil sans vaccin | état vide ; tuile sans badge ; pas de section 11369-6 | T7 (Kamekichi) ; IpsImmunizationCodecTest::bundleWithoutNativePillarsHasNoImmunizationSection | bas |
| UC-VAC-006 | Annulation, ou retrait du vaccin choisi | alternatif | remplir → Annuler ; choisir un vaccin → ✕ | rien écrit ; le champ texte libre réapparaît | T8.4, T8.3 | bas |
| UC-VAC-007 | Double appui sur Enregistrer | erreur | 2 appuis rapides | 1 carte | T9 (Double-tap Save) | moyen |
| UC-VAC-008 | App tuée ou changement de langue, formulaire ouvert | limite | vaccin + date choisis → `am kill` → revenir | attendu : choix conservés ; code actuel : relus des arguments ; sélecteur restauré inerte | **AUCUNE** (T9 « Rotation » inopérant : portrait verrouillé) | moyen |
| UC-VAC-009 | « Rappel fait en 2019, je ne sais plus quand » | limite | date à l'année | attendu : année seule possible ; code actuel : calendrier seul ; un `dt`="2019" importé est conservé si on n'y touche pas | partiel IpsImmunizationCodecTest::partialDatesAreKeptAsIs (codec) ; écran **AUCUNE** | moyen |
| UC-VAC-010 | Date future (rendez-vous prévu saisi comme fait) | erreur | calendrier, jour après aujourd'hui | attendu : jour désactivé ; code actuel : `setEnd` sans validateur (`ImmunizationFormBottomSheet.kt:324`), défaut jour UTC | T9 (Date future) — attend « désactivés », à confirmer | moyen |
| UC-VAC-011 | Dose 0, dose supérieure à la série, nombre démesuré | erreur | dose 0 ; dose 3 série 2 ; dose à 10 chiffres | erreur en ligne + toast ; rien écrit (10 chiffres : dépassement d'entier → invalide) | T9 (Dose 0, Dose > série) ; 10 chiffres **AUCUNE** | bas |
| UC-VAC-012 | Série connue, numéro de dose oublié | limite | série 3, dose vide → Enregistrer → quitter → revenir | attendu : série conservée ou refus explicite ; code actuel : série non écrite dans le Bundle sans numéro de dose (`IpsFhirCodec.kt:346-358`) → disparaît | **AUCUNE** | bas |
| UC-VAC-013 | Vaccin refusé ou contre-indiqué | alternatif | statut « non administré » | icône 🚫 ; `status` not-done ; `_j.im[].st` ; attendu QR texte : mention localisée ; code actuel : suffixe brut « (not-done) » en toutes langues | T8.2 ; IpsImmunizationCodecTest::statusIsNormalized ; libellé QR **AUCUNE** | haut |
| UC-VAC-014 | Entrée marquée « saisie par erreur » | limite | statut entered-in-error | icône ⚠️ ; attendu : exclue du QR secouriste ; code actuel : toujours exportée | partiel IpsImmunizationCodecTest::statusIsNormalized | moyen |
| UC-VAC-015 | Lot, fabricant ou note en japonais, emoji, très long | limite | lot « ロット🙂 », note 1 000 caractères | intact dans le Bundle (lot, fabricant absents de `_j` par conception) | **AUCUNE** | bas |
| UC-VAC-016 | Deux fois le même vaccin le même jour | limite | créer deux fois | 2 Immunization d'`id` distincts ; aucune alerte (actuel) | **AUCUNE** | bas |
| UC-VAC-017 | KB absente ou traductions indisponibles | limite | ouvrir le sélecteur | catalogue embarqué disponible ; produits SNOMED additionnels absents si l'asset échoue | **AUCUNE** | bas |
| UC-VAC-018 | Recherche « grippe saisonniere » sans accent, ou en kana | alternatif | taper l'alias | entrée trouvée | IpsVaccineCatalogTest::searchAliasesLetAFrenchUserTypeTheInternationalName ; T2 (« influenza ») | bas |
| UC-VAC-019 | Appareil en japonais ; vaccin hors catalogue choisi en français | alternatif | changer la langue ; choisir un produit SNOMED hors catalogue | libellés localisés ; attendu `display` anglais ; code actuel : libellé de la langue d'interface stocké (`:302`) | T5, T7 ; cas hors catalogue **AUCUNE** | bas |
| UC-VAC-020 | Édition d'allergie puis vérification des vaccins | nominal | T4 | 4 vaccins toujours là | T4, T13 | haut |
| UC-VAC-021 | Vaccins importés d'un QR (sans `id`, sans lot) | limite | importer → éditer le 2ᵉ | `id` déterministes ; lot, fabricant, série absents (non portés par le QR) | IpsImmunizationProjectionTest::rebuildIsDeterministic, ::rebuildFromProjectionPreservesWhatTheQrCarries, ::legacyPayloadWithoutTheNewKeysStillParses | moyen |

## 7. Procédures 🏥 (`ui/profile/procedures`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-PRO-001 | Opéré de la vésicule | nominal | + → « Cholécystectomie » → date, site, lieu, note → Enregistrer | carte ; Procedure `Procedure-uv-ips`, code 38102005, `performedDateTime`, section 47519-4 ; `_j.pr[]` | T10 ; IpsProcedureDeviceCodecTest::procedureFullRoundTrip, ::procedureJsonCarriesIpsEssentials | moyen |
| UC-PRO-002 | Intervention trouvée seulement par la recherche KB | alternatif | chercher « appendic » → choisir un résultat KB | pas de doublon ; `system` SNOMED ou `urn:umls` selon la KB | T10 (Recherche KB) | moyen |
| UC-PRO-003 | « Opération du genou, années 80 » | alternatif | texte libre, date inconnue | `code.text` sans `coding` ; `performedString` "unknown" | T12 ; IpsProcedureDeviceCodecTest::procedureUnknownDateAndFreeTextAndStatuses | moyen |
| UC-PRO-004 | Intervention en cours (dialyse), annulée ou interrompue | nominal | ouvrir → statut « en cours », puis not-done, stopped → Enregistrer | icône de statut ; `status` et `_j.pr[].st` ; attendu QR texte : mention localisée ; code actuel : suffixe brut « (not-done) » | T10 (en cours) ; IpsProcedureDeviceCodecTest::procedureUnknownDateAndFreeTextAndStatuses ; libellé QR **AUCUNE** | moyen |
| UC-PRO-005 | Intervention saisie par erreur | nominal | a) bouton Supprimer ; b) appui long | carte retirée | T10 | moyen |
| UC-PRO-006 | Aucune intervention | limite | profil sans intervention | état vide ; pas de section 47519-4 | T13 (Kamekichi) | bas |
| UC-PRO-007 | Annulation, ou retrait du code choisi | alternatif | remplir → Annuler ; choisir → ✕ | rien écrit ; champ texte libre réapparaît | T12 (Annuler, ✕ procédure) | bas |
| UC-PRO-008 | Double appui sur Enregistrer | erreur | 2 appuis rapides | 1 carte | T12 (Double-tap Save) | moyen |
| UC-PRO-009 | App tuée, formulaire ouvert | limite | code + date + statut → `am kill` → revenir | attendu : choix conservés ; code actuel : relus des arguments | **AUCUNE** (T12 « Rotation » inopérant) | moyen |
| UC-PRO-010 | « Césarienne en 1975 » (année seule, seed Haru) | limite | créer ; ou éditer la note de l'entrée existante | attendu : année seule saisissable ; code actuel : calendrier seul à la création ; « 1975 » conservé à l'édition si on n'y touche pas | partiel IpsProcedureDeviceCodecTest::procedureUnknownDateAndFreeTextAndStatuses (« 2010 ») ; T10 (lecture) ; édition **AUCUNE** | moyen |
| UC-PRO-011 | Date future | erreur | calendrier | attendu : jours futurs désactivés ; code actuel : `setEnd` sans validateur (`ProcedureFormBottomSheet.kt:218`) | T12 (Date future) — à confirmer | bas |
| UC-PRO-012 | Site, issue, note en japonais, emoji, très long | limite | « 左膝 🙂 » + 1 000 caractères | intact dans le Bundle | partiel (accents français dans le test codec) | bas |
| UC-PRO-013 | Même intervention saisie deux fois | limite | créer deux fois | 2 entrées, aucune alerte (actuel) | **AUCUNE** | bas |
| UC-PRO-014 | KB pas prête | erreur | ouvrir le sélecteur, puis chercher | suggestions du catalogue visibles ; à la recherche : statut « erreur », y compris pour les entrées du catalogue ; texte libre possible | **AUCUNE** | moyen |
| UC-PRO-015 | Recherche avec accent (« cholécystectomie ») | alternatif | avec puis sans accent | entrée du catalogue en tête | partiel T10 (« appendic », ASCII) | bas |
| UC-PRO-016 | Appareil en japonais | alternatif | changer la langue → liste, QR texte | libellés du catalogue localisés | IpsProcedureDeviceCatalogTest::procedureCodesAreUniqueSnomedIdentifiersWithThreeLabels ; T13 | bas |
| UC-PRO-017 | Édition d'une procédure sur un profil riche | nominal | éditer → `verify_profiles.py` | autres piliers intacts | T10, T13 (`--expect`, `--expect-pr`) | haut |
| UC-PRO-018 | Procédures importées d'un QR (sans `id`) | limite | importer → éditer | `id` déterministes ; site, issue, praticien, lieu absents (non portés par le QR) | IpsProcedureDeviceCodecTest::legacyJArraysRebuildDeterministically, ::procedureProjectionContract | moyen |

## 8. Dispositifs médicaux 📟 (`ui/profile/devices`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-DEV-001 | Porteur d'une pompe à insuline qui recopie sa carte d'implant | nominal | + → « Pompe à insuline » → UDI, fabricant, modèle, série, date, site → Enregistrer | carte ; Device (`udiCarrier`, fabricant, modèle, série) + DeviceUseStatement (`timingDateTime`, `bodySite`) ; section 46264-8 ; `_j.dv[]` | T11 ; IpsProcedureDeviceCodecTest::deviceFullRoundTrip, ::deviceJsonCarriesIpsEssentials | haut |
| UC-DEV-002 | « Plaque tibia gauche » | alternatif | texte libre → Enregistrer | `Device.type.text` + `deviceName[0]` patient-reported, pas de `coding` | T12 ; IpsProcedureDeviceCodecTest::deviceStatusesAndFreeText | moyen |
| UC-DEV-003 | Dispositif retiré | nominal | ouvrir → statut « retiré » → Enregistrer | icône ⏹ ; Device inactive, DeviceUseStatement completed ; `_j.dv[].st` ; trié après les actifs | T11 ; IpsProcedureDeviceCodecTest::deviceStatusesAndFreeText | haut |
| UC-DEV-004 | Dispositif saisi par erreur | nominal | a) bouton Supprimer ; b) appui long | carte, Device et DeviceUseStatement retirés | a) T11 ; b) **AUCUNE** | moyen |
| UC-DEV-005 | Aucun dispositif | limite | profil sans dispositif | état vide ; pas de section 46264-8 | T11 (retour à 0 sur Kurodo) | bas |
| UC-DEV-006 | Annulation | alternatif | remplir → Annuler | rien écrit | **AUCUNE** (T12 « Annuler » ne vise que 🏥) | bas |
| UC-DEV-007 | Double appui sur Enregistrer | erreur | 2 appuis rapides | 1 carte | T12 (Double-tap Save) | moyen |
| UC-DEV-008 | App tuée, formulaire ouvert | limite | type + date + statut → `am kill` → revenir | attendu : choix conservés ; code actuel : relus des arguments ; UDI, série (texte) conservés | **AUCUNE** | moyen |
| UC-DEV-009 | Faute de frappe dans l'UDI (« ABC ») | erreur | UDI « ABC » → Enregistrer | erreur en ligne, focus, effacée à la saisie ; rien écrit | T12 (UDI invalide) ; IpsProcedureDeviceCatalogTest::udiPlausibilityRejectsTypos | moyen |
| UC-DEV-010 | UDI recopié avec des espaces, GTIN nu, formats HIBCC et ICCBBA | alternatif | « (01) 00643169007222 (21) PJN… » ; « 00643169007222 » ; « +H123… » | accepté ; espaces retirés avant stockage | T12 (UDI GTIN nu) ; IpsProcedureDeviceCatalogTest::udiPlausibilityAcceptsTheIssuingAgencyFormats | moyen |
| UC-DEV-011 | UDI plausible mais faux (8 chiffres au hasard, clé GTIN erronée) | limite | « 12345678 » | accepté (contrôle volontairement indulgent, pas de clé) ; attendu : au moins un avertissement | **AUCUNE** | moyen |
| UC-DEV-012 | Date au mois (« juin 2019 », seed Haru) | limite | éditer la note de l'appareil auditif | attendu : mois seul saisissable ; code actuel : calendrier seul ; « 2019-06 » conservé si on n'y touche pas | T11 (lecture) ; édition **AUCUNE** | bas |
| UC-DEV-013 | Date future | erreur | calendrier | attendu : jours futurs désactivés ; code actuel : `setEnd` sans validateur (`DeviceFormBottomSheet.kt:226`) | T12 (Date future) — à confirmer | bas |
| UC-DEV-014 | Modèle, série, site en japonais, emoji, très long | limite | « 左胸 🙂 », série de 200 caractères | intact dans le Bundle | **AUCUNE** | bas |
| UC-DEV-015 | Deux appareils identiques (auditif gauche et droit) | limite | créer deux fois le même type | 2 entrées distinctes ; légitime, aucune alerte | **AUCUNE** | bas |
| UC-DEV-016 | KB pas prête | erreur | ouvrir le sélecteur, puis chercher | suggestions du catalogue ; recherche en erreur ; texte libre possible | **AUCUNE** | moyen |
| UC-DEV-017 | Recherche « prothèse » avec accent | alternatif | avec puis sans accent | entrée du catalogue trouvée | **AUCUNE** | bas |
| UC-DEV-018 | Appareil en japonais | alternatif | changer la langue → liste, QR texte | libellés du catalogue localisés (« 心臓ペースメーカー ») | IpsProcedureDeviceCatalogTest::deviceCodesAreUniqueSnomedIdentifiersWithThreeLabels ; T13 | bas |
| UC-DEV-019 | Création sur Kurodo, Haru doit garder ses 2 dispositifs | nominal | T11 | `--expect-dv demo_haru=2`, vaccins et procédures intacts | T11 | haut |
| UC-DEV-020 | Dispositifs importés d'un QR (sans `id`, sans UDI) | limite | importer → éditer | `id` déterministes ; UDI, fabricant, modèle, série absents | IpsProcedureDeviceCodecTest::deviceProjectionContract, ::legacyJArraysRebuildDeterministically | moyen |
| UC-DEV-021 | Ré-import du QR d'un profil déjà présent (même `sid`) | limite | profil local avec UDI → réimporter son propre QR | attendu : UDI, série, lot conservés ; code actuel : les piliers natifs sont reconstruits depuis `_j` (`ProfilesRepository.kt:368`) → UDI et série perdus | **AUCUNE** | haut |

## 9. Résultats 🧪 (`ui/profile/results`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-RES-001 | Insuffisant rénal qui recopie sa kaliémie | nominal | + → « kaliemie » → unité mmol/L auto → « 5,9 » → Élevé → réf. 3,5 / 5,1 → date → Enregistrer | carte « 5.9 mmol/L » 🔺 ; `valueQuantity` 5.9 UCUM, `referenceRange`, profil laboratoire ; `_j.rs[]{c,v,u,ip,rr}` | T14.3 ; IpsResultCodecTest::integralAndCommaDecimalsSurviveTheDoubleJsonEncoding, ::numericJsonCarriesIpsEssentials | haut |
| UC-RES-002 | Compte rendu d'échographie | alternatif | texte libre, catégorie imagerie, valeur « Normale » | `code.text`, `valueString`, catégorie imaging ; profil radiologie si date au jour, sinon générique | T14.4 ; IpsResultCodecTest::imagingWithoutDayPreciseDateUsesTheGenericResultsProfile | moyen |
| UC-RES-003 | Groupe sanguin saisi comme résultat de labo | alternatif | + → « Groupe sanguin ABO / Rhésus » → sélecteur 🩸 → Enregistrer | valeur et unité remplacées par le sélecteur ; `valueCodeableConcept` SNOMED ; une seule 882-1 dans le Bundle | T14.6 ; IpsBloodGroupTest::syncKeepsExactlyOneBloodGroupResult | haut |
| UC-RES-004 | Tentative de modifier le groupe sanguin dérivé | erreur | appui ou appui long sur la ligne dérivée | toast « vient du pilier Patient », aucun formulaire, rien écrit | T14.2 | haut |
| UC-RES-005 | Groupe saisi en 🧪 différent de `p.bt` (A+ côté patient, O− côté résultat) | limite | créer une 882-1 O− alors que `p.bt`="A+" | attendu : alerte de contradiction ou alignement ; code actuel : la 882-1 de l'utilisateur masque la dérivée, `p.bt` reste A+ (deux groupes différents dans le passeport) | **AUCUNE** | haut |
| UC-RES-006 | Profil reçu par QR, puis groupe sanguin corrigé dans l'identité | limite | importer → changer `p.bt` → ouvrir 🧪 | attendu : 882-1 suit `p.bt` ; code actuel : la 882-1 reconstruite depuis `_j` perd l'`id` `rs-blood-group-…`, devient « utilisateur » et ne suit plus (`…/ips/IpsBloodGroup.kt:81-86`) | **AUCUNE** | haut |
| UC-RES-007 | Correction d'une valeur, suppression d'un résultat | nominal | ouvrir → valeur → Enregistrer ; bouton Supprimer ; appui long | même `id` ; carte retirée | T14.7 | moyen |
| UC-RES-008 | Aucun résultat et groupe inconnu | limite | profil sans `bt` ni résultat | état vide ; pas de section 30954-2 | **AUCUNE** | bas |
| UC-RES-009 | Annulation | alternatif | remplir → Annuler | rien écrit | **AUCUNE** | bas |
| UC-RES-010 | Double appui sur Enregistrer | erreur | 2 appuis rapides | 1 carte | **AUCUNE** pour ce formulaire | moyen |
| UC-RES-011 | App tuée, formulaire ouvert | limite | test, unité, interprétation, date choisis → `am kill` → revenir | attendu : choix conservés ; code actuel : relus des arguments ; valeur et bornes (texte) conservées | **AUCUNE** | moyen |
| UC-RES-012 | Test choisi sans valeur ; test codé sans choix | erreur | Enregistrer sans valeur | erreur en ligne « saisis la valeur » ; toast « choisis le groupe sanguin » | T14.5, T14.6 | moyen |
| UC-RES-013 | Bornes inversées ou non numériques | erreur | réf. 9 / 3 ; réf. « abc » | erreur en ligne sur la borne ; rien écrit | T14.5 | moyen |
| UC-RES-014 | Valeur 0, négative (excès de base −0,5), démesurée (25 chiffres) | limite | « 0 » ; « -0,5 » ; coller un très grand nombre | accepté en numérique (décimal exact), pas de plantage ; `valueQuantity` 0 / −0.5 ; carte lisible | partiel IpsResultCodecTest::decimalHelpers ; écran **AUCUNE** | moyen |
| UC-RES-015 | Écritures « presque numériques » : « 1 234,5 », « .5 », « 5. », « 1e5 », « <0.5 » | limite | saisir avec l'unité mg/L → Enregistrer | attendu : avertissement ou conservation de l'unité ; code actuel : bascule silencieuse en texte et **unité supprimée** (`ResultFormBottomSheet.kt:431`) | partiel IpsResultCodecTest::nonNumericTypedValueFallsBackToValueString (codec, sans unité) | haut |
| UC-RES-016 | Interprétation contradictoire (valeur dans la plage, « critique haut » choisi) | limite | 4,1 avec réf. 3,5–5,1 et HH | attendu : avertissement ; code actuel : aucun contrôle de cohérence | **AUCUNE** | moyen |
| UC-RES-017 | Le choix du test écrase l'unité et la catégorie déjà réglées | limite | régler unité g/L → choisir un test du catalogue | attendu : confirmation ; code actuel : unité et catégorie du catalogue imposées (`:246-247`) | **AUCUNE** | moyen |
| UC-RES-018 | Date inconnue, ou connue à l'année seulement | limite | effacer la date ; entrée importée « 2025-12 » | `_effectiveDateTime` avec data-absent-reason ; date partielle conservée ; pas de saisie « année seule » dans ce formulaire | IpsResultCodecTest::undatedAndUnattributedResultsStillMeetTheIpsCardinalities, ::imagingWithoutDayPreciseDateUsesTheGenericResultsProfile ; écran **AUCUNE** | bas |
| UC-RES-019 | Date future | erreur | calendrier | attendu : refus ; code actuel : `setEnd` sans validateur (`:358`) | **AUCUNE** | bas |
| UC-RES-020 | Note, laboratoire en japonais, emoji, très long | limite | « 松山赤十字病院 🙂 » + 1 000 caractères | intact dans le Bundle | **AUCUNE** | bas |
| UC-RES-021 | Sélecteur de test sans KB ; recherche « kaliémie » avec accent | alternatif | KB absente → ouvrir le sélecteur → taper avec et sans accent | catalogue LOINC embarqué, alias trouvés | IpsResultCatalogTest::aliasesLetAFrenchUserTypeTheUsualShorthand ; T14.3 (ASCII) | bas |
| UC-RES-022 | Appareil en japonais | alternatif | changer la langue → liste, QR texte | libellés du catalogue localisés ; valeur et unité inchangées | T14.8 ; IpsResultCatalogTest::aliasesLetAFrenchUserTypeTheUsualShorthand | bas |
| UC-RES-023 | Édition d'un résultat ; les observations de grossesse ne doivent pas apparaître ici | nominal | éditer → `verify_profiles.py` | autres piliers intacts ; 🧪 ne liste que les Observations de résultats | T14.7 ; IpsPregnancyCodecTest::bundleSectionAndNoLeakIntoResults ; IpsResultCodecTest::otherObservationKindsAreLeftToTheirOwnPillars | haut |
| UC-RES-024 | Résultats importés d'un QR (sans `id`) ; valeur codée hors groupe sanguin | limite | importer → éditer | `id` déterministes, 3 formes de valeur reconstruites ; laboratoire absent ; valeur codée hors 882-1 : le formulaire exige une nouvelle saisie et perd le code | IpsResultCodecTest::projectionRebuildsTheThreeValueKinds ; cas codé hors 882-1 **AUCUNE** | moyen |

## 10. Grossesses 🤰 (`ui/profile/pregnancy`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-GRO-001 | Femme enceinte qui voyage | nominal | statut « Enceinte », date du statut aujourd'hui, terme futur, méthode « dernières règles » → Enregistrer | bloc terme visible ; Observation 82810-3 (LA15173-0) + Observation 11779-6 `valueDateTime` ; `_j.pg[]{c,vc,v,dt}` | T21.2 ; IpsPregnancyCodecTest::roundTrips, ::jsonCarriesTheIpsProfilesAndValueTypes | haut |
| UC-GRO-002 | Bilan obstétrical seul (2 naissances, 2 vivantes) | alternatif | remplir 2 compteurs + date du bilan → Enregistrer | Observations `valueInteger` ; section 10162-6 ; `id` conservés par code LOINC | T21.1, T21.2 ; IpsPregnancyCodecTest::projection | moyen |
| UC-GRO-003 | Après l'accouchement : « Non enceinte » | nominal | statut « Non enceinte » → Enregistrer | bloc terme masqué, terme effacé ; plus d'Observation de terme | T21.4 | haut |
| UC-GRO-004 | Retrait du statut | alternatif | statut « — Non renseigné » → Enregistrer | Observation de statut retirée ; compteurs conservés | T21.4 | moyen |
| UC-GRO-005 | Terme antérieur à la date du statut | erreur | statut daté d'aujourd'hui, terme hier | message rouge + toast ; rien écrit | T21.3 | moyen |
| UC-GRO-006 | Naissances vivantes supérieures au total | erreur | vivantes 3, total 2 | message « ne peuvent pas dépasser le total » ; rien écrit | T21.3 | moyen |
| UC-GRO-007 | Autres incohérences du bilan | limite | à terme 3 + prématurées 2 avec total 2 ; toujours en vie > vivantes ; spontanées + provoquées > interruptions | attendu : refus ; code actuel : une seule règle vérifiée (vivantes ≤ total) | **AUCUNE** | moyen |
| UC-GRO-008 | Tout effacer | nominal | « Tout effacer » → confirmer | champs vidés ; `pg` vide ; pas de section 10162-6 | T21.5 | moyen |
| UC-GRO-009 | Grossesse terminée mais statut jamais mis à jour ; terme absurde (dans 3 ans) | limite | statut « Enceinte », terme passé ou absent, sans date de statut ; terme 2029 | attendu : signaler un statut périmé, refuser un terme au-delà d'environ 10 mois ; code actuel : aucun contrôle si la date du statut est vide, aucune borne haute ; « Enceinte » reste affiché aux secouristes | **AUCUNE** | haut |
| UC-GRO-010 | Compteur 0 distinct de « non renseigné » ; maximum 99 | limite | saisir 0 ; saisir 99 ; laisser vide | 0 → Observation `valueInteger` 0 ; vide → pas d'Observation ; 2 chiffres maximum, pas de signe | partiel IpsPregnancyCodecTest::roundTrips (`count = 0`) ; écran **AUCUNE** | bas |
| UC-GRO-011 | Sortie de l'écran sans Enregistrer | alternatif | modifier le statut → retour | attendu : confirmation ; code actuel : modifications perdues sans message | **AUCUNE** | moyen |
| UC-GRO-012 | Double appui sur Enregistrer | erreur | 2 appuis rapides | une seule écriture (bouton désactivé pendant l'enregistrement) | **AUCUNE** | bas |
| UC-GRO-013 | App tuée, écran ouvert et non enregistré | limite | statut + terme + compteurs saisis → `am kill` → revenir | attendu : saisie retrouvée ; code actuel : rechargement du disque, tout est perdu | **AUCUNE** | moyen |
| UC-GRO-014 | Patient de sexe masculin, ou âgé de 80 ans, marqué « Enceinte » par erreur | limite | profil `gs`="M" → statut « Enceinte » | attendu : avertissement ; code actuel : aucun contrôle croisé avec le sexe ou l'âge | **AUCUNE** | moyen |
| UC-GRO-015 | Date du statut ou du bilan dans le futur | erreur | calendrier | attendu : refus ; code actuel : `setEnd` sans validateur (`PregnancyEditFragment.kt:213`) ; terme : futur autorisé volontairement | **AUCUNE** | bas |
| UC-GRO-016 | Observations importées avec note, ou deux statuts historisés | limite | importer → ouvrir → Enregistrer sans rien changer | attendu : notes et historique conservés ; code actuel : Observations reconstruites sans note (`:255-261`), un seul statut gardé | **AUCUNE** | bas |
| UC-GRO-017 | Appareil en japonais ; aucune KB nécessaire | alternatif | changer la langue → écran, QR texte | statuts, méthodes, compteurs localisés (catalogue embarqué) | T21.6 ; IpsPregnancyCatalogTest::formatLocalisesStatusOutcomeAndEdd ; PregnancyTextQrTest::frenchQrPrintsTheLocalisedOutcomeLabel | bas |
| UC-GRO-018 | Enregistrement sur Haru, autres piliers intacts | nominal | T21.2 → `verify_profiles.py` | 🧪 5 inchangé, `--expect-pg` exact | T21.2, T21.4 ; IpsPregnancyCodecTest::bundleSectionAndNoLeakIntoResults | haut |
| UC-GRO-019 | `pg` importé d'un QR (sans `id`) ; code non obstétrical glissé dedans | limite | importer → ouvrir | `id` déterministes ; code inconnu ignoré | IpsPregnancyCodecTest::projection | bas |

## 11. Autonomie et handicaps ♿ (`ui/profile/pastproblems`, mode `functional`)

| ID | Situation réelle (qui, pourquoi) | Type (nominal / alternatif / erreur / limite) | Étapes | Résultat attendu (écran + donnée FHIR/_j) | Couverture actuelle | Risque santé si ça casse (haut/moyen/bas) |
|---|---|---|---|---|---|---|
| UC-FON-001 | Malentendant appareillé | nominal | + → sélecteur → « perte auditive » → année 2019 → Enregistrer | carte ♿ ; Condition `Condition-uv-ips` active, section 47420-5 ; `_j.fs[]{c,dt}` | partiel IpsFunctionalCodecTest::roundTripsAndStatuses, ::projection (codec) ; création codée à l'écran **AUCUNE** (seed seulement) | haut |
| UC-FON-002 | « Fauteuil roulant à l'extérieur » | alternatif | texte libre, statut présente, année 2020 | `code.text` sans `coding` ; section 47420-5 et **pas** 11450-4 | T22.3 | haut |
| UC-FON-003 | Limitation devenue inactive | nominal | ouvrir → statut inactive → Enregistrer | `_j.fs[].st`="inactive" | T22.4 | moyen |
| UC-FON-004 | Entrée saisie par erreur | nominal | a) bouton Supprimer ; b) appui long | carte retirée ; plus de section 47420-5 si liste vide | T22.4 (chemin non précisé) | moyen |
| UC-FON-005 | Aucune limitation | limite | profil sans entrée | état vide ; pas de section | T22.4 (retour à 0) | bas |
| UC-FON-006 | Annulation | alternatif | remplir → Annuler | rien écrit | **AUCUNE** | bas |
| UC-FON-007 | Double appui sur Enregistrer | erreur | 2 appuis rapides | 1 carte | **AUCUNE** | moyen |
| UC-FON-008 | App tuée, formulaire ouvert | limite | code + année + statut → `am kill` → revenir | attendu : choix conservés ; code actuel : relus des arguments | **AUCUNE** | moyen |
| UC-FON-009 | Le formulaire ne doit montrer ni bloc de fin ni sévérité | limite | ouvrir le formulaire ♿ | blocs masqués ; `severity` jamais écrite ; statuts présente / inactive / résolue | T22.2 (sans bloc de fin) ; sévérité **AUCUNE** | bas |
| UC-FON-010 | Date à l'année, année hors bornes, date exacte future | limite | année 2020 ; 1850 ; jour futur | 2020 accepté ; 1850 refusé ; attendu jour futur refusé (code actuel : `setEnd` sans validateur) | T22.3 (2020) ; reste **AUCUNE** | bas |
| UC-FON-011 | Limitation « résolue » qui ne doit pas glisser dans 📜 ni 🩺 | limite | statut résolue → Enregistrer → ouvrir 📜 et 🩺 | entrée visible seulement dans ♿ | IpsFunctionalCodecTest::threeConditionPillarsNeverMix | moyen |
| UC-FON-012 | Une limitation ne doit pas déclencher d'alerte médicament × maladie | limite | ajouter une entrée ♿ chez un patient traité | `_j.cn` inchangé ; aucune alerte nouvelle | IpsFunctionalCodecTest::functionalStatusNeverFeedsTheDrugDiseaseProjection ; T22.3 (`--expect-cn`) | moyen |
| UC-FON-013 | Sélecteur (jeu « problèmes » IPS) sans terme adapté, ou KB pas prête | erreur | chercher « fauteuil » ; KB absente | aucun résultat ou liste vide ; texte libre possible | **AUCUNE** | moyen |
| UC-FON-014 | Recherche avec accent (« cécité ») | alternatif | avec puis sans accent | mêmes résultats | **AUCUNE** | bas |
| UC-FON-015 | Texte en japonais, RTL, emoji, très long | limite | « 車椅子 🙂 » + note de 1 000 caractères | intact ; carte lisible | **AUCUNE** | bas |
| UC-FON-016 | Même limitation saisie deux fois | limite | créer deux fois | 2 entrées, aucune alerte (actuel) | **AUCUNE** | bas |
| UC-FON-017 | Appareil en japonais ; statut dans le QR texte | alternatif | changer la langue → QR texte | libellé localisé ; attendu statut localisé ; code actuel : suffixe brut « (inactive) » | partiel T22.5 (français, entrées actives) | moyen |
| UC-FON-018 | Entrées importées d'un QR (sans `id`) | limite | importer → éditer | `id` déterministes ; texte libre revenu en `text` | IpsFunctionalCodecTest::projection | bas |

---

## Trous de couverture prioritaires

Les 20 cas non couverts les plus dangereux, risque haut d'abord. « JVM » = test Kotlin pur, sans Android.

1. **Bundle non régénéré mais « enregistré »** (constat 6 ; touche tous les piliers natifs) — JVM après extraction de la politique de lecture en fonction pure : « Bundle plus ancien que `_j` → la lecture ne préfère pas le Bundle » ; sinon test appareil en forçant l'échec du constructeur.
2. **Écriture non atomique du profil** (constat 6) — JVM sur un écrivain extrait, dossier temporaire : exception simulée entre les deux écritures → `<sid>.json` reste lisible.
3. **UC-RES-006 — groupe sanguin désynchronisé après import** — JVM immédiat : `IpsBloodGroup.sync(fromJEntries(rs = [dérivée.toJEntry()]).results, sid, "A-")` doit donner une seule 882-1 valant A−.
4. **UC-RES-005 — deux groupes sanguins contradictoires** — JVM : `sync` avec une 882-1 utilisateur ≠ `p.bt` doit signaler le conflit (valeur de retour à enrichir).
5. **UC-ALG-004 — réactions 2..n perdues à l'édition** — JVM après extraction de `applyAllergyForm(existante, formulaire)` : les réactions suivantes sont conservées.
6. **UC-ALG-018 — contrôle croisé muet si la KB échoue** — JVM : `FormCrossCheckHelper` avec un `KbCrossCheck` factice qui lève → résultat « indisponible », pas `null`.
7. **UC-ALG-002 / UC-MED-002 / UC-MED-016 — pas de saisie libre, ajout impossible sans KB** — test appareil (KB renommée) ; JVM sur la fonction de validation une fois le texte libre admis.
8. **UC-MED-012 — dose à virgule absente du FHIR** — JVM : `JemmaFhirBundleBuilder.build` avec `md[v="0,5"]` → `doseQuantity.value` = 0.5 (gabarit `hydratedStub` des tests existants).
9. **UC-MED-021 — voie inhalée exportée « intraveineuse »** — JVM après extraction de la table voie KB → code court : « inhal.aerosol » ne donne pas « I ».
10. **UC-ALG-007 / UC-MED-005 / UC-PRB-005 — sections IPS obligatoires absentes quand la liste est vide** — JVM : `build(hydratedStub vide)` contient 48765-2, 10160-0 et 11450-4 avec une entrée « aucune information / aucun connu ».
11. **UC-GRO-009 — statut « Enceinte » périmé** — JVM sur une fonction pure `isPregnancyStale(statut, terme, aujourdHui)`.
12. **UC-DEV-021 — ré-import qui efface UDI, série, lot** — JVM sur une fonction de fusion « natif existant + `_j` entrant » : champs riches conservés à code et date égaux.
13. **UC-PAT-017 — téléphone d'urgence invalide enregistré** — JVM : `PhoneNumberHelper.validate` (pur) + règle bloquante sur `TOO_SHORT` / `INVALID_CHARS`.
14. **UC-RES-015 — valeur « presque numérique », unité perdue** — JVM : cas `IpsDecimal` (« .5 », « 5. », « 1 234,5 », « 1e5 ») et fonction pure `parseResultValue(brut, unité)` qui garde l'unité.
15. **UC-VAC-013 / UC-PRO-004 / UC-FON-017 — statuts bruts anglais dans le QR texte** — JVM calqué sur `PregnancyTextQrTest` : `JemmaTextPayloadBuilder` en `ja` et `fr` avec `st="not-done"` → libellé localisé.
16. **UC-PAT-013 / UC-MED-022 — non-régression des autres piliers après édition identité ou médicament** — test appareil : T4 étendu aux écrans identité et médicament, `verify_profiles.py` avec tous les `--expect-*`.
17. **UC-ALG-009 / UC-MED-007 / UC-PAT-007 — double appui (doublon, deux profils)** — test appareil : lignes « Double-tap Save » pour ces trois écrans ; JVM sur un garde `SingleShot` pur.
18. **Dates futures et jour UTC** (UC-VAC-010, UC-PAT-006, tous calendriers) — JVM : fonction pure `isFutureIso(iso, aujourdHuiLocal)` appelée à l'enregistrement ; appareil : T9 strict, fuseau UTC−8 le soir.
19. **UC-PAT-011 / UC-PAT-005 — adresses, téléphones, identifiants tronqués ; date de naissance partielle bloquante** — JVM après extraction de `buildPatient(existant, formulaire)`.
20. **Recréation du formulaire** (ligne « App tuée » de chaque tableau : choix perdus, sélecteur inerte) — test appareil seulement : `adb shell am kill` app en arrière-plan, puis changement de langue, formulaire et sélecteur ouverts.

## Défauts probables repérés à la lecture du code

Vérifiés dans le code ; non exécutés. `…/` comme défini plus haut.

1. `…/profiles/ProfilesRepository.kt:396-403` — l'échec de génération du Bundle est avalé et `saveNativePillars` renvoie `true` ; or la lecture préfère le Bundle (`:341-350`) : une modification d'un pilier natif peut être annoncée « enregistrée » puis disparaître au rechargement.
2. `…/profiles/ProfilesRepository.kt:394,399` — `writeText` direct, sans fichier temporaire ni verrou autour de lire → modifier → écrire : un arrêt en cours d'écriture laisse un JSON tronqué, et `loadProfile` (`:430-435`) renvoie alors `null` (profil entier invisible).
3. `…/ui/profile/allergies/AllergiesEditFragment.kt:261-270` avec `AllergyFormBottomSheet.kt:157` — l'allergie est reconstruite avec une seule réaction : les suivantes sont perdues à chaque édition.
4. `…/ips/IpsBloodGroup.kt:81-86` avec `ProfilesRepository.kt:368` — reconstruite depuis `_j`, l'Observation 882-1 dérivée reçoit un autre `id`, est prise pour une saisie utilisateur et bloque la synchronisation avec `p.bt`.
5. `…/qr/JemmaFhirBundleBuilder.kt:282` — `doseValue.toDoubleOrNull()` : une dose « 0,5 », acceptée par le formulaire (`MedicationFormBottomSheet.kt:615`), n'a pas de `doseQuantity` dans le Bundle.
6. `…/ui/profile/medications/MedicationFormBottomSheet.kt:423-425` avec `…/pillars/IpsRouteCatalog.kt:50-52` — les voies « inhal.* » sont converties en « I », code défini comme Injection et exporté en voie intraveineuse.
7. `…/ui/profile/medications/MedicationFormBottomSheet.kt:597`, `…/allergies/AllergyFormBottomSheet.kt:727`, `…/perso/PatientEditFragment.kt:549-554` — aucun garde contre le double appui (les autres formulaires désactivent le bouton) : doublon possible, et deux profils à la création (`ProfilesRepository.kt:185`).
8. Neuf calendriers, par exemple `…/ui/profile/immunizations/ImmunizationFormBottomSheet.kt:324` — `CalendarConstraints.setEnd(aujourd'hui)` sans validateur de jour et sélection par défaut en jour UTC : une date future reste sélectionnable ; aucun contrôle à l'enregistrement. À confirmer sur appareil (T9 et T12 attendent des jours désactivés).
9. `…/ui/profile/perso/PatientEditFragment.kt:614-635` et `:591` — l'enregistrement ne réécrit que la première adresse, le premier téléphone, le premier email et le premier identifiant ; et il exige une date de naissance complète, ce qui bloque toute édition d'un profil à date partielle.
10. `…/ui/profile/results/ResultFormBottomSheet.kt:431` — dès que la valeur n'est pas un décimal strict (« <0.5 », « 1 234,5 », « .5 »), elle passe en texte et l'unité choisie est supprimée sans message.
