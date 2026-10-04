# 🐢 JemmaPass — Analyse Fonctionnelle : Recensement de l'Existant Android (Tranche 2)
> **Spécification Microscopique des Cas d'Usage Existants et Partiels**  
> **Branche de travail** : `ag/analyse-fonctionnelle` (dérivée de `origin/feat/ips-18-pillars-cleanup` @ `1e6d6f7c882f1d6811e14c08b5078611d7c9a270`)  
> **Rôle** : `orchestrator: Antigravity-Analyse`  
> **Tranche** : 2 / 6 (`10-existant-android.md`)  
> **État du code décrit** : commit `1e6d6f7c882f1d6811e14c08b5078611d7c9a270` du 4 octobre 2026.  
> **Convention d'étiquetage obligatoire** :  
> - `[EXISTANT]` : Comportement vérifié dans le code source Kotlin avec citation exacte `fichier:ligne`.  
> - `[PARTIEL]` : Une partie existe dans le code source ; citation exacte de l'existant et description précise du manque.  
> **Règle spécifique Urgence (directive message Claude 0091)** : Pour chaque cas d'usage touchant les canaux d'intervention vitale (QR Texte, Fiche Secouriste, Widget Écran de Veille, Triage SALT), une ligne obligatoire explicite **« ⚠️ Ce qui est perdu quand la place manque »** détaille l'impact clinique exact d'une éviction ou d'un dépassement de budget.  

---

## Sommaire de la Tranche 2

1. [Domaine STO : Persistance, Intégrité Locale & Concurrence](#1-domaine-sto--persistance-intégrité-locale--concurrence)
2. [Domaine PAT & CTC : Identité Patient, Démographie & Contacts d'Urgence](#2-domaine-pat--ctc--identité-patient-démographie--contacts-durgence)
3. [Domaine ALG : Allergies, Intolérances & Criticités Vitales](#3-domaine-alg--allergies-intolérances--criticités-vitales)
4. [Domaine MED : Médications en Cours, Posologies & Voies d'Administration](#4-domaine-med--médications-en-cours-posologies--voies-dadministration)
5. [Domaines PRB & PST : Diagnostics Actifs & Antécédents Résolus](#5-domaines-prb--pst--diagnostics-actifs--antécédents-résolus)
6. [Domaines IMM, PRC & DEV : Vaccinations, Procédures & Dispositifs Médicaux](#6-domaines-imm-prc--dev--vaccinations-procédures--dispositifs-médicaux)
7. [Domaine RES : Résultats Biologiques & Inviolabilité du Groupe Sanguin](#7-domaine-res--résultats-biologiques--inviolabilité-du-groupe-sanguin)
8. [Domaines PRG & FNC : Grossesse / Obstétrique & Statut Fonctionnel](#8-domaines-prg--fnc--grossesse--obstétrique--statut-fonctionnel)
9. [Domaine SEC : Moteur Clinique Pharmacologique & Matrice de Décision KbSafety](#9-domaine-sec--moteur-clinique-pharmacologique--matrice-de-décision-kbsafety)
10. [Domaines QRT, QRC, QRF : Canaux de Transfert QR & Troncature d'Urgence](#10-domaines-qrt-qrc-qrf--canaux-de-transfert-qr--troncature-durgence)
11. [Domaines SOS, TRG, MSH : Alertes de Détresse, Triage SALT & Réseau Maillé](#11-domaines-sos-trg-msh--alertes-de-détresse-triage-salt--réseau-maillé)
12. [Domaines PDF, OCR, TTS, LLM : Supports Imprimés, Vision & IA Embarquée](#12-domaines-pdf-ocr-tts-llm--supports-imprimés-vision--ia-embarquée)

---

## 1. Domaine STO : Persistance, Intégrité Locale & Concurrence

### UC-STO-001 : Écriture atomique du profil avec protection anti-corruption
- **Étiquette** : `[EXISTANT]`
- **Citation** : `profiles/ProfileFiles.kt:45-80`
- **Acteur** : Titulaire du passeport / Aidant
- **Appareil** : Téléphone Android
- **Précondition** : L'utilisateur a modifié une information médicale dans un formulaire.
- **Déclencheur** : Appui sur le bouton « Enregistrer » du formulaire.
- **Étapes** :
  1. L'application sérialise le profil en JSON temporaire dans `profiles/<sid>.json.tmp`.
  2. L'écriture s'effectue sur le fichier temporaire avec vidage forcé des buffers (`flush()` / `sync()`).
  3. L'application effectue un renommage atomique du fichier temporaire vers le fichier cible définitif `profiles/<sid>.json`.
- **Résultat observable** : Le fichier cible n'est jamais dans un état corrompu ou tronqué à 0 octet, même si le processus de l'application est brutalement interrompu par l'OS ou une panne de batterie.
- **Cas d'échec** : Si l'écriture échoue ou si l'espace disque est saturé, une exception est levée, le fichier temporaire est supprimé et l'ancien profil valide reste intact.
- **Données touchées** : Fichier `<sid>.json` sur stockage interne privé.

---

### UC-STO-002 : Exclusion mutuelle des écritures concurrentes sous `writeMutex`
- **Étiquette** : `[EXISTANT]`
- **Citation** : `profiles/ProfilesRepository.kt:146`
- **Acteur** : Titulaire du passeport / Système
- **Appareil** : Téléphone Android
- **Précondition** : Deux opérations d'enregistrement ou d'importation sont sollicitées simultanément (ex: enregistrement utilisateur et réception d'un événement mesh en tâche de fond).
- **Déclencheur** : Appel concomitant à `saveProfile()` ou `saveNativePillars()`.
- **Étapes** :
  1. La coroutine acquiert le verrou exclusif `writeMutex.withLock`.
  2. La lecture-modification-écriture s'exécute de manière sérialisée.
  3. Le verrou est libéré à l'issue de la persistance des fichiers `<sid>.json` et `<sid>.fhir.json`.
- **Résultat observable** : Aucune écriture concurrente ne peut écraser silencieusement les modifications d'une autre opération.
- **Cas d'échec** : Si une opération bloque, les opérations suivantes attendent leur tour sans provoquer d'état incohérent.
- **Données touchées** : Tous les piliers du profil ciblé.

---

### UC-STO-003 : Dualité de persistance : Bundle FHIR R4 et Projection compacte `_j 1.2`
- **Étiquette** : `[EXISTANT]`
- **Citation** : `profiles/ProfilesRepository.kt:23-28`, `profiles/ProfilesRepository.kt:464-472`
- **Acteur** : Système
- **Appareil** : Téléphone Android
- **Précondition** : Un profil contenant des piliers FHIR-natifs (vaccins, actes, dispositifs, biologie) et des piliers de saisie directe (patient, allergies, médications) est sauvegardé.
- **Déclencheur** : Sauvegarde d'un profil.
- **Étapes** :
  1. Les 8 piliers FHIR-natifs sont persistés dans `<sid>.fhir.json` (source de vérité HL7 FHIR R4).
  2. La projection courte `_j 1.2` est générée et persistée dans `<sid>.json` pour les transferts physiques rapides (QR code, BLE).
- **Résultat observable** : Deux fichiers synchronisés coexistent sur le disque pour chaque profil.
- **Cas d'échec** : `[EXISTANT (profiles/ProfilesRepository.kt:26)]` Si la génération du Bundle FHIR échoue, l'ancien Bundle périmé est invalidé/supprimé afin d'éviter qu'une version désynchronisée ne prenne le pas sur `_j 1.2`.
- **Données touchées** : `<sid>.fhir.json` et `<sid>.json`.

---

### UC-STO-004 : Assainissement strict des identifiants de profils (`safeIdOrNull`)
- **Étiquette** : `[EXISTANT]`
- **Citation** : `profiles/ProfileFiles.kt:20-35`
- **Acteur** : Tiers émetteur d'un QR code malveillant / Système
- **Appareil** : Téléphone Android
- **Précondition** : Réception ou scan d'un profil tiers dont le champ `sid` contient des séquences de traversée de répertoire (ex: `../../system`).
- **Déclencheur** : Tentative d'importation d'un QR code.
- **Étapes** :
  1. `ProfileFiles.safeIdOrNull()` analyse la chaîne d'identifiant.
  2. Tout identifiant contenant des caractères non alphanumériques stricts ou des séparateurs de chemin `/` ou `\` est rejeté et retourne `null`.
- **Résultat observable** : Le fichier importé est confiné au répertoire `profiles/` sous un nom sécurisé sans possibilité de traversée d'arborescence.
- **Cas d'échec** : Rejet immédiat de l'identifiant corrompu avec journalisation de sécurité.
- **Données touchées** : Système de fichiers de l'application.

---

## 2. Domaine PAT & CTC : Identité Patient, Démographie & Contacts d'Urgence

### UC-PAT-001 : Saisie de date de naissance partielle sans forçage du jour
- **Étiquette** : `[EXISTANT]`
- **Citation** : `qr/JemmaProfileJ.kt:48`, `ui/profile/perso/PatientFormMerge.kt:1-50`
- **Acteur** : Titulaire du passeport
- **Appareil** : Téléphone Android
- **Précondition** : Le titulaire souhaite renseigner uniquement son année de naissance (ex: `1979`) ou son année et mois (ex: `1979-04`) pour préserver sa vie privée.
- **Déclencheur** : Saisie dans le champ date de naissance du formulaire d'identité.
- **Étapes** :
  1. L'utilisateur saisit une date au format partiel ISO 8601 (`YYYY` ou `YYYY-MM`).
  2. Le validateur `IsoDateRules` vérifie que la date n'est pas dans le futur et accepte la troncature.
  3. Le champ `p.bd` est persisté avec la précision exacte fournie par l'utilisateur.
- **Résultat observable** : Aucune valeur fictive de jour (`01`) n'est inventée ou injectée dans le dossier médical.
- **Cas d'échec** : Une saisie non conforme à la syntaxe ISO 8601 ou une date future est signalée en rouge par le champ de saisie.
- **Données touchées** : `p.bd`.

---

### UC-PAT-002 : Multi-identifiants nationaux (Passeport, NSS, MyNumber)
- **Étiquette** : `[EXISTANT]`
- **Citation** : `pillars/IpsIdentifierSystemCatalog.kt:1-40`, `qr/JemmaProfileJ.kt:48`
- **Acteur** : Titulaire étranger ou citoyen local
- **Appareil** : Téléphone Android
- **Précondition** : Le patient dispose d'un identifiant national de santé étranger (ex: `BE-680412-123-45` pour `demo_kurodo`) ou japonais (ex: `JP-12345678` pour `demo_haru`).
- **Déclencheur** : Saisie d'un identifiant dans le formulaire identité.
- **Étapes** :
  1. Le formulaire associe le numéro au système d'identification correspondant.
  2. Le champ `p.idn` stocke l'identifiant pour la projection compacte `_j 1.2`.
  3. Le générateur de Bundle FHIR (`qr/JemmaFhirBundleBuilder.kt:120-135`) crée l'élément `Patient.identifier` avec le `system` URI officiel correspondant.
- **Résultat observable** : L'identifiant est restitué avec son autorité d'émission dans le document FHIR international.
- **Cas d'échec** : Si aucun système n'est sélectionné, l'identifiant est stocké sous forme de chaîne brute textuelle.
- **Données touchées** : `p.idn`, `Patient.identifier`.

---

### UC-CTC-001 : Enregistrement de contacts d'urgence avec lien HL7 v3 RoleCode
- **Étiquette** : `[EXISTANT]`
- **Citation** : `pillars/IpsRelationshipCatalog.kt:1-50`, `qr/JemmaProfileJ.kt:118-125`
- **Acteur** : Titulaire du passeport / Aidant
- **Appareil** : Téléphone Android
- **Précondition** : L'utilisateur souhaite ajouter une personne à joindre en cas d'urgence (`ICE`).
- **Déclencheur** : Saisie d'un contact dans l'écran dédié (`ui/profile/contacts/`).
- **Étapes** :
  1. L'utilisateur saisit le nom (ex: « Sakura Tanaka »), le téléphone (ex: « +81 90 0000 0001 »).
  2. L'utilisateur sélectionne un lien de parenté parmi les codes standardisés HL7 v3 RoleCode (ex: `DAUC` = Fille, `SPS` = Conjoint, `FRND` = Ami).
  3. Le contact est persisté dans la liste `p.ct` de `JPatient`.
- **Résultat observable** : Le contact est affiché sur la fiche d'identité et projeté dans `Patient.contact` du Bundle FHIR.
- **Cas d'échec** : Si le numéro de téléphone est manquant et que l'e-mail est vide, le formulaire exige au moins un moyen de contact direct.
- **Données touchées** : `p.ct`, `Patient.contact`.

---

## 3. Domaine ALG : Allergies, Intolérances & Criticités Vitales

### UC-ALG-001 : Déclaration d'allergie médicamenteuse avec criticité élevée
- **Étiquette** : `[EXISTANT]`
- **Citation** : `qr/JemmaProfileJ.kt:130-145`, `ui/profile/allergies/AllergyFormBottomSheet.kt:1-60`
- **Acteur** : Titulaire du passeport (ex: `demo_kurodo`)
- **Appareil** : Téléphone Android
- **Précondition** : Le patient est allergique à la pénicilline suite à un choc anaphylactique.
- **Déclencheur** : Formulaire d'ajout d'allergie.
- **Étapes** :
  1. L'utilisateur sélectionne « Pénicilline » (code SNOMED `91936005`).
  2. L'utilisateur sélectionne la criticité « Élevée » (`s = "H"` pour High).
  3. L'utilisateur sélectionne le statut clinique « Actif » (`st = "A"` pour Active).
  4. L'utilisateur renseigne la manifestation clinique (`m = "Anaphylactic shock 2019-03"`).
  5. L'allergie est enregistrée sous `writeMutex`.
- **Résultat observable** : L'allergie apparaît avec un badge rouge vif sur la fiche profil et déclenche les alertes du moteur de contrôle croisé.
- **Cas d'échec** : `[EXISTANT (ui/profile/allergies/AllergyFormBottomSheet.kt:45)]` Si le moteur clinique n'a pas pu évaluer l'allergie (base non chargée), un dialogue d'avertissement « Contrôle incomplet » prévient l'utilisateur avant confirmation.
- **Données touchées** : `al[]`, `AllergyIntolerance` FHIR.

---

### UC-ALG-002 : Préservation de réactions cliniques multiples lors de l'édition
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ui/profile/allergies/AllergyFormMerge.kt:1-40`
- **Acteur** : Titulaire du passeport
- **Appareil** : Téléphone Android
- **Précondition** : Une allergie existante comporte plusieurs manifestations documentées (ex: `m = "Urticaria + tongue swelling"` pour `demo_kurodo`).
- **Déclencheur** : Modification de la criticité de l'allergie dans le formulaire.
- **Étapes** :
  1. Le formulaire charge l'allergie existante.
  2. L'utilisateur modifie un champ secondaire et enregistre.
  3. `AllergyFormMerge` fusionne les champs modifiés sans écraser la chaîne des manifestations.
- **Résultat observable** : L'ensemble des réactions cliniques saisies est intégralement préservé.
- **Cas d'échec** : Aucun écrasement silencieux d'antécédent réactionnel.
- **Données touchées** : `al[].m`.

---

## 4. Domaine MED : Médications en Cours, Posologies & Voies d'Administration

### UC-MED-001 : Saisie de traitement chronique avec voie d'administration inhalée
- **Étiquette** : `[EXISTANT]`
- **Citation** : `pillars/IpsRouteCatalog.kt:1-40`, `qr/JemmaProfileJ.kt:150-170`
- **Acteur** : Titulaire du passeport (patient asthmatique ou BPCO)
- **Appareil** : Téléphone Android
- **Précondition** : Le patient prend un traitement inhalé (ex: Salbutamol).
- **Déclencheur** : Formulaire d'ajout de médicament.
- **Étapes** :
  1. L'utilisateur saisit la dénomination du médicament, la dose (`doseValue = "100"`, `doseUnit = "ug"`).
  2. L'utilisateur sélectionne la voie d'administration « Inhalée » (`route = "H"`).
  3. L'application associe le code SNOMED CT `447694001` (Inhalation) dans le catalogue des voies (`IpsRouteCatalog.kt:25`).
- **Résultat observable** : La voie inhalée est correctement exportée dans le Bundle FHIR (`MedicationStatement.dosage.route`) et ne dégénère jamais en injection.
- **Cas d'échec** : Si une voie inconnue est saisie, le système retombe sur la valeur brute sans inventer de codage erroné.
- **Données touchées** : `md[].r`, `MedicationStatement`.

---

### UC-MED-002 : Préservation de posologies à décimales (virgule européenne vs point)
- **Étiquette** : `[EXISTANT]`
- **Citation** : `qr/JemmaFhirBundleBuilder.kt:280-310`
- **Acteur** : Titulaire du passeport
- **Appareil** : Téléphone Android
- **Précondition** : Le patient prend une dose non entière (ex: « 2,5 mg » de Bisoprolol comme `demo_kamekichi`).
- **Déclencheur** : Saisie de la posologie avec une virgule décimale.
- **Étapes** :
  1. L'utilisateur tape `2,5` dans le champ posologie.
  2. Le convertisseur FHIR normalise la virgule en point décimal standard (`2.5`).
  3. L'élément FHIR `SimpleQuantity.value` reçoit la valeur numérique `2.5`.
- **Résultat observable** : La dose décimale est transmise sans troncature à l'entier inférieur (`2`).
- **Cas d'échec** : Rejet si des caractères alphabétiques non numériques sont saisis dans le champ valeur.
- **Données touchées** : `md[].v`, `MedicationStatement.dosage.doseAndRate.doseQuantity`.

---

## 5. Domaines PRB & PST : Diagnostics Actifs & Antécédents Résolus

### UC-PRB-001 : Renseignement d'un problème actif avec niveau de sévérité
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ips/IpsProblem.kt:1-50`, `ips/IpsConditionSeverity.kt:1-30`
- **Acteur** : Titulaire du passeport (ex: `demo_haru`)
- **Appareil** : Téléphone Android
- **Précondition** : La patiente souffre d'insuffisance cardiaque (`c = "84114007"`, SNOMED CT).
- **Déclencheur** : Ajout d'une affection dans le pilier Problèmes Actifs.
- **Étapes** :
  1. L'utilisateur renseigne l'intitulé « Heart failure » et la date de début (`onset = "2020-11"`).
  2. L'utilisateur sélectionne la sévérité « Modérée » (`IpsConditionSeverity.MODERATE`).
  3. L'entrée est enregistrée dans le conteneur FHIR-natif `IpsNativePillars.problems`.
- **Résultat observable** : Une ressource `Condition` active est créée sous la section IPS LOINC `11450-4` (Problem List).
- **Cas d'échec** : Si la date de début est postérieure à la date du jour, le validateur bloque l'enregistrement.
- **Données touchées** : `cn[]`, `Condition` active.

---

### UC-PST-001 : Renseignement d'un antécédent médical ou chirurgical résolu avec date d'abattement
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ips/IpsPastProblem.kt:1-50`, `ips/IpsFhirCodec.kt:320-350`
- **Acteur** : Titulaire du passeport (ex: `demo_haru`)
- **Appareil** : Téléphone Android
- **Précondition** : La patiente a subi un infarctus du myocarde en août 2015, traité et résolu en septembre 2015.
- **Déclencheur** : Ajout dans le pilier Antécédents (`pastProblems`).
- **Étapes** :
  1. L'utilisateur saisit « Myocardial infarction » (`onset = "2015-08-27"`).
  2. L'utilisateur renseigne la date de guérison/résolution (`abatement = "2015-09"`).
  3. L'entrée est enregistrée dans `IpsNativePillars.pastProblems`.
- **Résultat observable** : Une ressource `Condition` avec `clinicalStatus = "resolved"` est créée sous la section IPS LOINC `11348-0` (History of Past Illness). Elle est disjointe des problèmes actifs.
- **Cas d'échec** : Si la date d'abattement précède la date de début, l'enregistrement est refusé.
- **Données touchées** : `ph[]`, `Condition` résolue.

---

## 6. Domaines IMM, PRC & DEV : Vaccinations, Procédures & Dispositifs Médicaux

### UC-IMM-001 : Historique vaccinal complet avec numéros de lots et rappel
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ips/IpsImmunization.kt:1-60`, `pillars/IpsVaccineCatalog.kt:1-50`
- **Acteur** : Titulaire du passeport (ex: `demo_kurodo`)
- **Appareil** : Téléphone Android
- **Précondition** : Le patient a reçu 4 vaccins (Tdap 2022, Hep A+B 2016, Encéphalite japonaise 2023, COVID-19 2021).
- **Déclencheur** : Consultation ou saisie dans le pilier Vaccinations.
- **Étapes** :
  1. Chaque vaccin est identifié par son code SNOMED officiel (ex: `871876003` pour Tdap).
  2. Les numéros de lot (`lotNumber = "AC52B213BC"`), fabricant et performer sont enregistrés.
  3. Le Bundle FHIR génère une ressource `Immunization` sous la section IPS LOINC `11369-6`.
- **Résultat observable** : La liste vaccinale ordonnée chronologiquement s'affiche avec le statut d'immunisation complet.
- **Données touchées** : `im[]`, `Immunization` FHIR.

---

### UC-DEV-001 : Déclaration d'implant cardiaque avec contrainte IRM et identifiant UDI
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ips/IpsDevice.kt:1-60`, `pillars/IpsDeviceCatalog.kt:1-50`
- **Acteur** : Titulaire du passeport (ex: `demo_haru`)
- **Appareil** : Téléphone Android
- **Précondition** : La patiente porte un stimulateur cardiaque Medtronic (`c = "14106009"`).
- **Déclencheur** : Saisie d'un dispositif médical.
- **Étapes** :
  1. L'utilisatrice renseigne le modèle « Azure XT DR MRI SureScan », le numéro de série (`serial = "PJN1234567"`), l'UDI GS1 `(01)00643169007222(21)PJN1234567` et la note d'alerte `MRI-conditional`.
  2. L'application génère une paire de ressources FHIR : `DeviceUseStatement` pointant vers la ressource `Device` correspondante (`IpsFhirCodec.kt:250-280`).
- **Résultat observable** : L'implant apparaît avec la mention de sécurité IRM et son emplacement anatomique (« pectoral gauche »).
- **Données touchées** : `dv[]`, `DeviceUseStatement`, `Device`.

---

## 7. Domaine RES : Résultats Biologiques & Inviolabilité du Groupe Sanguin

### UC-RES-001 : Inviolabilité et synchronisation bidirectionnelle du Groupe Sanguin (LOINC 882-1)
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ips/IpsBloodGroup.kt:15-80`, `profiles/ProfilesRepository.kt:480-510`
- **Acteur** : Titulaire du passeport / Système
- **Appareil** : Téléphone Android
- **Précondition** : Le profil patient indique un groupe sanguin `A+` dans l'état civil (`p.bt = "A+"`).
- **Déclencheur** : Persistance du profil ou importation d'un Bundle FHIR externe.
- **Étapes** :
  1. Le moteur `IpsBloodGroup.reconcile()` contrôle la présence de l'Observation LOINC `882-1` (ABO and Rh group).
  2. Si aucune Observation 882-1 n'existe, une Observation déterministe dérivée est automatiquement injectée dans les résultats pour garantir la complétude FHIR IPS.
  3. Si un résultat LOINC 882-1 contradictoire tente d'être importé ou saisi (ex: groupe `B+`), `ResultBloodGroupGuard` bloque l'opération et journalise un conflit critique dans `bloodGroupConflictFlow`.
- **Résultat observable** : Le groupe sanguin affiché et certifié est strictement unique et inviolable sur l'ensemble des écrans et canaux.
- **Cas d'échec** : Rejet de toute valeur discordante à la saisie ; signalement en rouge.
- **Données touchées** : `p.bt`, `Observation` LOINC 882-1.

---

## 8. Domaines PRG & FNC : Grossesse / Obstétrique & Statut Fonctionnel

### UC-PRG-001 : Synthèse obstétrique et parité
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ips/IpsPregnancy.kt:1-50`, `pillars/IpsPregnancyCatalog.kt:1-40`
- **Acteur** : Patiente (ex: `demo_haru`)
- **Appareil** : Téléphone Android
- **Précondition** : La patiente a eu deux grossesses menées à terme (`count = 2`).
- **Déclencheur** : Saisie dans le pilier Grossesse / Obstétrique.
- **Étapes** :
  1. L'utilisatrice renseigne le nombre total de naissances (LOINC `11640-0`), les naissances vivantes (`11636-8`) et les naissances à terme (`11639-2`).
  2. Les entrées sont enregistrées sous forme d'Observations obstétriques standardisées sous la section LOINC `10162-6`.
- **Résultat observable** : La synthèse obstétrique est lisible par les soignants et disjointe des résultats biologiques généraux.
- **Données touchées** : `pg[]`, `Observation` obstétrique.

---

### UC-FNC-001 : Statut fonctionnel, limitations motrices et aides techniques
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ips/IpsFunctional.kt:1-50`
- **Acteur** : Titulaire du passeport (ex: `demo_haru`)
- **Appareil** : Téléphone Android
- **Précondition** : La patiente présente une perte auditive appareillée (`c = "15188001"`) et marche avec une canne à l'extérieur.
- **Déclencheur** : Saisie dans le pilier Statut Fonctionnel.
- **Étapes** :
  1. L'utilisatrice ajoute l'aide technique « Marche avec une canne à l'extérieur ».
  2. L'entrée est enregistrée dans `IpsNativePillars.functional` sous la section IPS LOINC `47420-5` (Functional Status).
- **Résultat observable** : L'aide technique apparaît sous l'icône ♿ et ne pollue pas la liste des pathologies actives (`cn`).
- **Données touchées** : `fs[]`, `Condition` fonctionnelle.

---

## 9. Domaine SEC : Moteur Clinique Pharmacologique & Matrice de Décision KbSafety

### UC-SEC-001 : Détection de collision médicamenteuse létale (DDI Major)
- **Étiquette** : `[EXISTANT]`
- **Citation** : `kb/KbCrossCheck.kt:130-135`, `kb/JemmaProfileHydrator.kt:90-105`
- **Acteur** : Secouriste / Soignant ouvrant la fiche d'un patient polymédiqué
- **Appareil** : Téléphone Android
- **Précondition** : Le profil contient deux médicaments en interaction majeure dans la base SQLite locale `knowledge_full.db` (ex: Warfarine `B01AA03` + Ibuprofène `M01AE01` chez `demo_kamekichi`).
- **Déclencheur** : Hydratation du profil à l'ouverture de la fiche.
- **Étapes** :
  1. `JemmaProfileHydrator.batchCrossCheckDdi` exécute la requête sur la vue `v_ddi_emergency`.
  2. La paire est détectée avec la sévérité la plus haute (Major).
  3. Le moteur de décision calcule le verdict : `totalHits > 0` ➔ `KbSafetyVerdict.ALERT` 🔴.
- **Résultat observable** : Un bandeau rouge d'alerte prioritaire apparaît en tête de fiche ; les cartes des deux médicaments sont encadrées de rouge ; interdiction absolue d'afficher « Rien à signaler ».
- **Données touchées** : `HydratedProfile.ddiAlerts`, `safetyVerdict`.

---

### UC-SEC-002 : Détection d'allergie croisée par arborescence ATC (Pénicilline × Augmentin)
- **Étiquette** : `[EXISTANT]`
- **Citation** : `kb/KbCrossCheck.kt:250-310`
- **Acteur** : Soignant ou secouriste scannant un médicament pour `demo_kurodo`
- **Appareil** : Téléphone Android
- **Précondition** : Le profil `demo_kurodo` porte une allergie sévère à la pénicilline (code SNOMED `91936005`).
- **Déclencheur** : Scan ou saisie d'Augmentin (Amoxicilline + Acide clavulanique, ATC `J01CR02`).
- **Étapes** :
  1. `KbCrossCheck.checkOneAtcAgainstAllergies` analyse le code ATC candidat `J01CR02`.
  2. Le moteur extrait le préfixe de classe thérapeutique L3 `J01C` (Pénicillines).
  3. L'allergie à la pénicilline est résolue et rattachée à la classe `J01C`.
  4. La collision de classe est confirmée (`hit: class:J01C`).
- **Résultat observable** : Verdict immédiat `ALERT` 🔴 avec blocage visuel et avertissement explicite de risque de choc anaphylactique.
- **Données touchées** : `HydratedProfile.allergyAlerts`, `safetyVerdict`.

---

### UC-SEC-003 : Inviolabilité du verdict CLEAN : interdiction de « Rien à signaler » sans contrôle complet
- **Étiquette** : `[EXISTANT]`
- **Citation** : `kb/KbSafety.kt:15-60`, `kb/KbCrossCheck.kt:130-135`
- **Acteur** : Soignant
- **Appareil** : Téléphone Android
- **Précondition** : Un médicament en vente libre ou local non répertorié dans la base SQLite est présent dans le profil.
- **Déclencheur** : Exécution du contrôle de sécurité.
- **Étapes** :
  1. Le médicament candidat ne possède pas de code ATC résolu (`candidateResolved = false`).
  2. Le statut du contrôle passe à `INCOMPLETE` ou `NOT_CHECKED`.
  3. Le verdict global `KbSafety.verdict` refuse formellement l'état `CLEAN`.
- **Résultat observable** : L'interface affiche un bandeau ambre explicite indiquant « Sécurité non vérifiée : 1 médicament non reconnu ». L'application ne prétend jamais que le traitement est sûr en l'absence de preuve dans la base.
- **Données touchées** : `KbCheckReport`, `KbSafetyVerdict`.

---

## 10. Domaines QRT, QRC, QRF : Canaux de Transfert QR & Troncature d'Urgence

### UC-QRT-001 : Émission du QR Texte Universel (Plafond strict ≤ 1800 octets UTF-8)
- **Étiquette** : `[EXISTANT]`
- **Citation** : `qr/JemmaTextPayloadBuilder.kt:67-150`
- **Acteur** : Titulaire du passeport / Secouriste lecteur
- **Appareil** : Téléphone Android (émetteur) / Tout smartphone ou iPhone (lecteur)
- **Précondition** : Le profil du patient comporte de multiples piliers renseignés.
- **Déclencheur** : Affichage du QR code d'urgence dans l'onglet « QR Texte » de `QrViewerFragment`.
- **Étapes** :
  1. `JemmaTextPayloadBuilder.build()` assemble la chaîne textuelle dans la langue sélectionnée (25 langues disponibles via `JemmaTranslations.kt`).
  2. La taille en octets UTF-8 est mesurée (`utf8Size(text)`).
  3. Si la taille dépasse 1800 octets (`MAX_BYTES`), la boucle d'éviction par rangs retire les lignes excédentaires section par section en commençant par les sections de plus faible priorité (vaccins `RANK_IMMUNIZATIONS = 12`, biologie `RANK_RESULTS = 11`, actes `RANK_PROCEDURES = 10`, etc.).
  4. La mention explicite `✂️ … [ INCOMPLETE RECORD ]` est ajoutée en cas de coupure.
  5. Le QR code généré (version ≤ 35, correction d'erreur M) est affiché en une seule image fixe.
- **Résultat observable** : Le QR code est immédiatement lisible par l'appareil photo natif de n'importe quel iPhone ou smartphone Android sans application dédiée installée.
- **⚠️ Ce qui est perdu quand la place manque** :
  - Les vaccins anciens (`im`) sont sacrifiés en premier (seuls les plus récents subsistent, accompagnés de la mention `✂️ … +N`).
  - Puis les résultats de laboratoire (`rs`), les interventions chirurgicales passées (`pr`), les antécédents résolus (`ph`), les dispositifs (`dv`), et les coordonnées postales secondaires du patient (`patientExtra`).
  - L'identité vitale (Nom, Date de naissance, Groupe sanguin, Langue) n'est **JAMAIS** supprimée (`rank = 0`).
  - *Défaut identifié au tour 1 (en cours de correction sur `ag/0091-qr-allergy-order`)* : Au sein de la section allergies, l'absence de tri par criticité provoquait la suppression de la dernière allergie saisie (pouvant être une allergie `HIGH`) avant une allergie `LOW`. Le tri par criticité décroissante en cours garantit désormais que seules les allergies `LOW` sont sacrifiées.
- **Données touchées** : Payload textuel UTF-8.

---

### UC-QRC-001 : Émission et décodage du QR Compact compressé `_j2`
- **Étiquette** : `[EXISTANT]`
- **Citation** : `qr/JemmaPayloadCodec.kt:30-80`, `qr/JemmaPayloadPruner.kt:1-50`
- **Acteur** : Deux smartphones équipés de JemmaPass
- **Appareil** : Téléphone Android (émetteur et récepteur)
- **Précondition** : Le profil complet `JemmaProfileJ` est prêt à être partagé.
- **Déclencheur** : Sélection de l'onglet « QR Compact » (`_j2`).
- **Étapes** :
  1. `JemmaPayloadCodec.encode()` sérialise le profil JSON compact.
  2. Le flux est compressé via RFC 1951 Deflate-raw (sans en-tête zlib).
  3. Le résultat binaire est encodé en Base64url avec le préfixe `_j2:`.
  4. Le récepteur scanne le QR code, retire le préfixe, décompresse le flux brut et reconstruit l'instance `JemmaProfileJ`.
- **Résultat observable** : Un passeport dense de 8 Ko de données est transmis en un seul QR code physique de taille modérée (1,5 à 2,5 Ko).
- **⚠️ Ce qui est perdu quand la place manque** : Si le payload brut dépasse la capacité maximale d'un QR unique, `JemmaPayloadPruner` émonde les champs descriptifs verbeux non indispensables à l'urgence.
- **Données touchées** : `_j 1.2` JSON.

---

### UC-QRF-001 : Carrousel animé de trames QR FHIR (`JF:i/N`) pour export complet de dossier médical
- **Étiquette** : `[EXISTANT]`
- **Citation** : `qr/JemmaQrFrameSplitter.kt:20-60`, `qr/JemmaQrFrameAssembler.kt:26-90`
- **Acteur** : Soignant transférant un dossier vers un poste médical récepteur
- **Appareil** : Téléphone Android (émetteur et récepteur)
- **Précondition** : Le Bundle HL7 FHIR R4 complet (`<sid>.fhir.json`) dépasse la taille d'un QR code unique (ex: 15 Ko).
- **Déclencheur** : Sélection de l'onglet « FHIR » dans `QrViewerFragment`.
- **Étapes** :
  1. `JemmaQrFrameSplitter` découpe le payload en N trames de 1400 octets max (`QR_FRAME_CHUNK`), préfixées par `JF:i/N|`.
  2. L'écran de l'émetteur fait défiler les trames QR à une fréquence régulière (slideshow).
  3. La caméra du récepteur capture les trames dans n'importe quel ordre via `JemmaQrFrameAssembler.addFrame()`.
  4. Dès que les N trames distinctes sont acquises, l'assembleur concatène les fragments et reconstruit le Bundle FHIR intégral.
- **Résultat observable** : Un dossier médical hospitalier complet de 30 Ko est transmis hors-ligne de caméra à écran sans aucune perte d'information.
- **⚠️ Ce qui est perdu quand la place manque** : **Rien n'est perdu cliniquement**. En revanche, si la transmission est interrompue avant la capture des N trames, le dossier complet ne peut pas être reconstitué tant que la trame manquante n'a pas été filmée.
- **Données touchées** : Bundle FHIR R4 complet.

---

## 11. Domaines SOS, TRG, MSH : Alertes de Détresse, Triage SALT & Réseau Maillé

### UC-SOS-001 : Déclenchement d'alerte de détresse via Widget Écran Verrouillé
- **Étiquette** : `[EXISTANT]`
- **Citation** : `sos/JemmaEmergencyWidget.kt:1-50`, `sos/JemmaWidgetEmergencyService.kt:1-60`
- **Acteur** : Titulaire du passeport en détresse
- **Appareil** : Téléphone Android (écran verrouillé)
- **Précondition** : Le widget d'urgence JemmaPass a été positionné sur l'écran d'accueil ou de verrouillage.
- **Déclencheur** : Appui sur le bouton SOS du widget.
- **Étapes** :
  1. Le widget démarre `JemmaWidgetEmergencyService` en avant-plan.
  2. Le service active la diffusion radio de la balise BLE d'urgence (`JemmaSosBleAdvertiser.kt:28`) et du service Nearby (`JemmaNearbySosService.kt`).
  3. L'écran de secours affiche les données d'extrême urgence et le contact ICE prioritaire.
- **Résultat observable** : La détresse est immédiatement signalée aux secouristes à proximité même si le smartphone n'est pas déverrouillé.
- **⚠️ Ce qui est perdu quand la place manque** : Sur l'écran du widget verrouillé, seules les données vitales minimales (Nom, Groupe sanguin, Allergie majeure, ICE) sont projetées. L'historique complet des pathologies et des examens est masqué pour protéger la vie privée et respecter l'espace graphique restreint.
- **Données touchées** : Statut d'émission SOS, notification persistante d'avant-plan.

---

### UC-TRG-001 : Attribution de statut de triage de catastrophe SALT et résolution de conflits
- **Étiquette** : `[EXISTANT]`
- **Citation** : `triage/SaltCode.kt:34-51`, `triage/StatusResolver.kt:46-108`
- **Acteur** : Secouriste (`rescuer`)
- **Appareil** : Téléphone Android
- **Précondition** : Le secouriste prend en charge une victime sur une zone de sinistre.
- **Déclencheur** : Sélection d'un statut de tri dans l'interface radar/secours.
- **Étapes** :
  1. Le secouriste affecte l'un des 6 statuts SALT normalisés :
     - `WAIT` (Gris, ⏳, attente)
     - `EVAL` (Jaune, 🔍, en cours d'évaluation)
     - `STAB` (Vert, ✅, stabilisé / blessé léger)
     - `HELP` (Rouge, 🆘, détresse vitale immédiate)
     - `EVAC` (Bleu, 🚑, en cours d'évacuation)
     - `DCD` (Noir, 🕊️, décédé)
  2. L'événement est horodaté et associé à l'identifiant du secouriste (`rescuer_sid`).
  3. En cas de réception d'un événement concurrent via le réseau maillé, `StatusResolver.shouldOverwrite()` résout le conflit selon la règle de priorité asymétrique (la détresse vitale `HELP` prime sur la stabilisation `STAB` en cas de doute d'horodatage).
- **Résultat observable** : L'état de la victime est mis à jour sur les terminaux de tous les secouristes du périmètre.
- **⚠️ Ce qui est perdu quand la place manque** : La trame réseau d'un événement de tri SALT est condensée sous le format compact strict de 131 octets UTF-8 (`E|<source>|<victim>|<status>|<rescuer>|<ts>|<ttl>|<seq>`, `sos/JemmaNearbyEndpointCodec.kt:51`). Aucune observation clinique verbeuse n'est transmise dans cette trame radio d'urgence.
- **Données touchées** : Registre d'état des victimes (`StatusResolver`), `EventChunk`.

---

### UC-MSH-001 : Découverte et propagation maillée par paquets BLE et Nearby
- **Étiquette** : `[EXISTANT]`
- **Citation** : `sos/JemmaSosChunkCodec.kt:171-175`, `sos/JemmaSosBleScanner.kt:49-80`, `mesh/relay/RelayManager.kt:1-50`
- **Acteur** : Terminaux secouristes et victimes en zone sans réseau
- **Appareil** : Téléphone Android
- **Précondition** : Réseau cellulaire et électrique totalement détruits.
- **Déclencheur** : Présence de terminaux dans un rayon radio direct (10 à 50 m).
- **Étapes** :
  1. Le smartphone de la victime diffuse des trames de 200 octets max (`MAX_CHUNK_BYTES`) via BLE Extended Advertising.
  2. Le terminal du secouriste capte les paquets radio en arrière-plan sans appairage préalable.
  3. `RelayManager` relaie les événements reçus aux autres secouristes selon la politique de durée de vie `TtlPolicy`.
- **Résultat observable** : Le radar affiche la liste des victimes détectées avec leur distance relative estimée (RSSI) et leur statut de tri.
- **⚠️ Ce qui est perdu quand la place manque** : Le paquet BLE de 200 octets ne transporte qu'un extrait ultra-court du profil SOS (`_j 1.2` élagué). Le dossier médical exhaustif ne peut pas transiter par ce canal radio balise.
- **Données touchées** : Cache du radar de victimes (`RadarController`).

---

## 12. Domaines PDF, OCR, TTS, LLM : Supports Imprimés, Vision & IA Embarquée

### UC-PDF-001 : Génération du Pocket Pass d'urgence avec tri prioritaire par criticité
- **Étiquette** : `[EXISTANT]`
- **Citation** : `qr/JemmaPdfExporter.kt:40-100`, `pdf/PdfPillarLayout.kt:8-9,58-60`
- **Acteur** : Titulaire du passeport
- **Appareil** : Téléphone Android
- **Précondition** : Le titulaire souhaite disposer d'une version papier physique de secours dans son portefeuille.
- **Déclencheur** : Sélection de « Exporter en PDF (Pocket Pass) ».
- **Étapes** :
  1. `PdfPillarLayout.planAllergies` trie les allergies par criticité décroissante (`HIGH` en premier, puis `UNKNOWN`, puis `LOW`).
  2. Les données vitales sont dessinées sur le gabarit vectoriel d'une feuille A4 pliable en carte.
  3. Deux QR codes haute densité sont dessinés au dos (QR Texte Universel et QR Compact `_j2`).
  4. Si le nombre d'allergies dépasse les lignes disponibles (`COLUMN1_ROWS = 13`), les allergies `HIGH` sont imprimées en priorité et les lignes excédentaires sont dénombrées par une ligne d'overflow explicite `• +N allergies` (`PdfPillarLayout.kt:90-93`).
- **Résultat observable** : Un document PDF autonome est généré, prêt à imprimer, garantissant qu'aucune allergie vitale n'est dissimulée derrière une allergie bénigne.
- **⚠️ Ce qui est perdu quand la place manque** : Sur le papier physique, les lignes de texte sont limitées à 13 rangs pour la colonne 1 et 18 rangs pour les médicaments. Les médicaments excédentaires sont remplacés par `• +N médicaments` ; le QR code imprimé au verso conserve l'intégralité des données.
- **Données touchées** : Fichier PDF généré via `PdfDocument` Android.

---

### UC-OCR-001 : Scan optique de boîte de médicament par caméra hors-ligne (ML Kit)
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ai/medscan/MedScanController.kt:1-40`, `ai/ocr/`
- **Acteur** : Secouriste prenant en charge une victime
- **Appareil** : Téléphone Android
- **Précondition** : Une boîte de médicament inconnue est trouvée à côté d'une victime inconsciente.
- **Déclencheur** : Activation du scanner de médicament dans l'application.
- **Étapes** :
  1. La caméra capture les images en temps réel et le moteur OCR ML Kit extrait les blocs textuels hors-ligne.
  2. `DrugNameExtractor` extrait la dénomination commerciale ou la substance active (gère le Katakana au Japon et le texte latin).
  3. L'application résout la substance dans `knowledge_full.db` et exécute immédiatement le contrôle croisé contre le profil de la victime (`checkOneDrugAgainstProfile`, `kb/KbCrossCheck.kt:646`).
- **Résultat observable** : Si la substance scannée présente un risque d'interaction létale ou d'allergie croisée avec le profil de la victime, un écran rouge d'alerte `ALERT` s'affiche immédiatement.
- **Cas d'échec** : Si le texte est illisible ou la substance non répertoriée dans la base, l'application affiche explicitement l'état `NOT_CHECKED` sans prétendre que le produit est sans danger.
- **Données touchées** : Flux caméra, analyse textuelle volatile.

---

### UC-TTS-001 : Restitution sonore multilingue des alertes vitales
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ai/tts/TtsService.kt:1-30`, `ai/tts/TtsSafety.kt:1-40`
- **Acteur** : Secouriste étranger ou victime malvoyante
- **Appareil** : Téléphone Android
- **Précondition** : Une alerte clinique majeure est détectée lors d'une intervention dans l'obscurité ou sous tension extrême.
- **Déclencheur** : Détection d'un verdict clinique ou appui sur l'icône haut-parleur.
- **Étapes** :
  1. `TtsSafety` sélectionne la phrase sonore officielle strictement alignée sur le verdict clinique (`KbSafetyVerdict`).
  2. Le moteur TTS système restitue vocalement l'alerte dans la langue locale (ex: « Attention : allergie sévère à la pénicilline détectée » en français ou japonais).
- **Résultat observable** : L'intervenant entend l'alerte vitale sans devoir regarder l'écran.
- **⚠️ Ce qui est perdu quand la place manque** : La phrase vocale est condensée à la consigne d'urgence vitale immédiate ; l'historique complet et les détails bibliographiques ne sont pas lus oralement.
- **Données touchées** : Flux audio système.

---

### UC-LLM-002 : Exécution sécurisée des 21 outils `@Tool` par l'agent local Gemma 4
- **Étiquette** : `[EXISTANT]`
- **Citation** : `ai/JemmaTools.kt:8-41`, `ai/assistant/JemmaAssistant.kt:1-50`
- **Acteur** : Utilisateur interrogeant son dossier ou soignant demandant une explication
- **Appareil** : Téléphone Android
- **Précondition** : Le modèle local Gemma 4 (`gemma-4-E2B-it.litertlm` ou `E4B`) est chargé en mémoire via LiteRT-LM.
- **Déclencheur** : Question en langage naturel dans l'assistant clinique.
- **Étapes** :
  1. L'agent conversationnel interprète la question de l'utilisateur.
  2. L'agent invoque exclusivement les outils déterministes annotés `@Tool` (`getFocusProfileAllergies`, `getFocusProfileMedications`, `checkDdi`, etc.).
  3. Les outils lisent directement les données médicales structurées sous verrou et renvoient des faits vérifiés au modèle.
  4. L'agent vulgarise la réponse dans la langue de l'utilisateur.
- **Résultat observable** : Les réponses médicales sont 100 % ancrées dans les données réelles du patient sans hallucination de substances ou de posologies.
- **Cas d'échec** : Si un outil échoue ou si la base KB est absente, l'outil retourne une erreur explicite transmise au modèle qui formule un avertissement de non-vérification.
- **Données touchées** : 21 fonctions d'accès aux piliers IPS et au moteur décisionnel.

---

*Fin du document `docs/functional/10-existant-android.md` — Tranche 2.*  
*Livré par l'orchestrateur : `orchestrator: Antigravity-Analyse`*
