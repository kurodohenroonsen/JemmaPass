# 🐢 JemmaPass — Analyse Fonctionnelle Globale : Cartographie & Matrice des Échanges
> **Document Fondateur de l'Analyse Fonctionnelle Complète — Version Révisée**  
> **Branche de travail** : `ag/analyse-fonctionnelle` (dérivée de `origin/feat/ips-18-pillars-cleanup` @ `1e6d6f7c882f1d6811e14c08b5078611d7c9a270`)  
> **Rôle** : `orchestrator: Antigravity-Analyse`  
> **Tranche** : 1 / 6 (`00-carte.md` — révision 2 après revue message `0087` de Claude)  
> **État du code décrit** : commit `1e6d6f7c882f1d6811e14c08b5078611d7c9a270` du 4 octobre 2026.  
> **Avertissement méthodologique sur les citations** : Le script de vérification mécanique actuel (`qa/docs/check_citations.py:21-24`) ne lit que `docs/DOCUMENTATION_UML_FONCTIONNELLE.md` et `docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md`. L'extension de ce contrôle à `docs/functional/` est en attente de déploiement côté intégrateur ; toute citation ci-dessous a été vérifiée manuellement ligne à ligne dans le code source Kotlin de l'application Android.  
> **Références normatives externes (à valider par un expert)** : ISO 27269:2021 (International Patient Summary - IPS) `[NON VÉRIFIÉ]`, HL7 FHIR R4 IPS IG v1.1.0 `[NON VÉRIFIÉ]`, RFC 1951 (DEFLATE) `[NON VÉRIFIÉ]`, W3C WebApp / PWA `[NON VÉRIFIÉ]`, NFC Data Exchange Format (NDEF) `[NON VÉRIFIÉ]`.  
> **Personas de référence (stricte conformité avec `qr/JemmaPersonasSeeder.kt`)** :  
> - 🚶‍♂️ `demo_kurodo` (`qr/JemmaPersonasSeeder.kt:25, 238-293`) : Patient belge (`nat = "BE"`), né le 1979-04-04 (`bd = "1979-04-04"`), groupe A+ (`bt = "A+"`), allergie sévère à la pénicilline (`c = "91936005"`, `s = "H"`, choc anaphylactique 2019-03, `JemmaPersonasSeeder.kt:262-266`), allergie au poisson (`c = "417532002"`, `s = "H"`, `JemmaPersonasSeeder.kt:272-276`), pollinose (`c = "419263009"`, `s = "L"`, `JemmaPersonasSeeder.kt:282-286`), contact d'urgence : Kamekichi (`r = "FRND"`, ami, `JemmaPersonasSeeder.kt:252-256`).  
> - 👵 `demo_haru` (`qr/JemmaPersonasSeeder.kt:27, 407-483`) : Patiente japonaise (`nat = "JP"`), 80 ans, née le 1946-02-08 (`bd = "1946-02-08"`), groupe O+ (`bt = "O+"`), sous anticoagulant oral direct Edoxaban (`c = "B01AF03"`, `JemmaPersonasSeeder.kt:473`), porteuse d'un stimulateur cardiaque (`c = "14106009"`, `date = "2021-03-15"`, note « MRI-conditional », pectoral gauche, `JemmaPersonasSeeder.kt:143-148`), contact d'urgence : Sakura Tanaka (`r = "DAUC"`, fille, `JemmaPersonasSeeder.kt:421-425`).  
> - 🚶‍♂️ `demo_kamekichi` (`qr/JemmaPersonasSeeder.kt:26, 216-234, 295-405`) : Patient japonais résidant à Bruxelles (`nat = "JP"`, `adr = "75 Avenue Louise, Bruxelles"`), né le 2000-05-20 (`bd = "2000-05-20"`), groupe B+ (`bt = "B+"`), liste de problèmes actifs (`cn`) : hypertension essentielle (`c = "59621000"`), fibrillation auriculaire sous warfarine (`c = "49436004"`), angine de poitrine (`c = "194828000"`), traitements en cours (`md`) : bisoprolol (`c = "C07AB07"`), warfarine (`c = "B01AA03"`), ibuprofène (`c = "M01AE01"`), sildénafil (`c = "G04BE03"`), dinitrate d'isosorbide (`c = "C01DA08"`), contact d'urgence : Kurodo Henro (`r = "FRND"`, ami, `JemmaPersonasSeeder.kt:309-313`).  

---

## Sommaire de la Tranche 1

1. [Périmètre, Démarche & Règle du Zéro-Défaut](#1-périmètre-démarche--règle-du-zéro-défaut)
2. [Section A : Taxonomie Exhaustive des Acteurs](#2-section-a--taxonomie-exhaustive-des-acteurs)
3. [Section B : Taxonomie des Appareils et Interfaces](#3-section-b--taxonomie-des-appareils-et-interfaces)
4. [Section C : Matrice Exhaustive des Canaux d'Échange par Paire d'Appareils](#4-section-c--matrice-exhaustive-des-canaux-déchange-par-paire-dappareils)
5. [Section D : Découpage Normalisé des Domaines Fonctionnels](#5-section-d--découpage-normalisé-des-domaines-fonctionnels)
6. [Section E : Registre des Décisions Ouvertes à Faire Prendre par Kudoro](#6-section-e--registre-des-décisions-ouvertes-à-faire-prendre-par-kudoro)

---

## 1. Périmètre, Démarche & Règle du Zéro-Défaut

L'écosystème **JemmaPass** a pour finalité la sauvegarde de vies humaines en situation d'urgence médicale, de mobilité internationale et de catastrophe naturelle majeure (séisme, tsunami, rupture d'infrastructure électrique et télécom).

Dans ce contexte, **la santé de vraies personnes est engagée** :
- Un cas d'usage oublié, une incompatibilité de canal non anticipée ou une hypothèse erronée sur les capacités d'un terminal constitue un **défaut critique** susceptible de retarder ou compromettre une prise en charge médicale d'urgence.
- L'analyse fonctionnelle ne se substitue pas aux décisions du concepteur du produit (*Kudoro*) : toute bifurcation architecturale, choix éthique, compromis ergonomique ou divergence réglementaire est explicitement consigné dans le **Registre des Décisions** avec ses options et leurs conséquences médicales et techniques.
- **Règle d'étiquetage obligatoire** : Tout fait technique vérifié dans le code source Android existant est étiqueté `[EXISTANT (fichier:ligne)]`. Tout composant, écran ou canal non implémenté à ce jour dans le code est rédigé au conditionnel et étiqueté `[PROPOSÉ]`. Toute affirmation matérielle, réglementaire, statistique ou externe non prouvée par une pièce du dépôt est étiquetée `[NON VÉRIFIÉ]`.

---

## 2. Section A : Taxonomie Exhaustive des Acteurs

L'écosystème implique 8 catégories d'acteurs, aux compétences, contraintes et habilitations contrastées.

```mermaid
flowchart TD
    subgraph Acteurs_Civils ["Porteurs & Proches"]
        ACT_PAT["1. Titulaire du Passeport<br/>(Patient autonome ou vulnérable)"]
        ACT_AID["2. Proche ou Aidant<br/>(Famille, tuteur, accompagnateur)"]
        ACT_NOA["8. Personne Sans Appareil<br/>(Inconsciente, mineur, sinistré démuni)"]
    end

    subgraph Acteurs_Secours_Soins ["Secours & Professionnels de Santé"]
        ACT_SEC["3. Secouriste / Équipe d'Intervention<br/>(Pompier, ambulancier, secouriste terrain)"]
        ACT_MED["4. Soignant<br/>(Médecin urgentiste, généraliste, infirmier)"]
        ACT_PHR["5. Pharmacien<br/>(Officine, dispensation d'urgence)"]
    end

    subgraph Acteurs_Support ["Logistique & Médiation"]
        ACT_INT["6. Interprète / Traducteur<br/>(Médiateur linguistique d'urgence)"]
        ACT_ADM["7. Administrateur de Lieu d'Accueil<br/>(Gestionnaire de refuge, gymnase, centre de tri)"]
    end

    ACT_PAT <-->|Partage Pass| ACT_SEC
    ACT_PAT <-->|Délégation| ACT_AID
    ACT_NOA -.->|Prise en charge| ACT_AID
    ACT_NOA -.->|Assistance vitale| ACT_SEC
    ACT_SEC -->|Transmission| ACT_MED
    ACT_MED <-->|Prescription / DDI| ACT_PHR
    ACT_SEC <-->|Médiation| ACT_INT
    ACT_SEC <-->|Enregistrement SALT| ACT_ADM
```

### A.1. Titulaire du Passeport (Patient)
- **Définition** : Personne physique dont les antécédents, traitements, allergies et données vitales sont décrits dans le passeport.
- **Rôle & Actions** :
  - `[EXISTANT (profiles/ProfilesRepository.kt:254-375)]` : Saisie, révision et mise à jour de son profil de santé en situation calme sous verrou d'écriture `writeMutex` (`profiles/ProfilesRepository.kt:146`).
  - `[EXISTANT (ui/export/QrViewerFragment.kt:1-50)]` : Présentation de son QR Code d'urgence (QR texte universel ou QR compact `_j2`).
  - `[PROPOSÉ]` : Port de supports physiques passifs : Pocket Pass papier plié au format carte, clé USB d'urgence sur trousseau, badge ou carte NFC.
- **Contraintes & Stress** : Peut être paniqué, blessé, désorienté, non francophone/non japonophone en voyage (ex: `demo_kurodo` au Japon), ou en état de choc post-séisme.

### A.2. Proche ou Aidant
- **Définition** : Membre de la famille, conjoint, tuteur légal, curateur ou accompagnateur de voyage.
- **Rôle & Actions** :
  - `[EXISTANT (profiles/ProfilesRepository.kt:40-60)]` : Gestion déléguée du profil d'une personne dépendante (enfant, personne âgée telle que `demo_haru`, adulte sous tutelle) via le sélecteur multi-profils local.
  - `[PROPOSÉ]` : Détention d'une copie numérique ou papier du passeport du proche.
  - `[PROPOSÉ]` : Transmission des antécédents et contacts d'urgence aux secours lorsque le titulaire est hors d'état de communiquer.
- **Contraintes** : Stress émotionnel intense, responsabilité légale de substitution, nécessité de basculer rapidement entre plusieurs profils sur un même terminal.

### A.3. Secouriste / Équipe d'Intervention d'Urgence
- **Définition** : Premier intervenant sur le lieu d'un accident ou d'une catastrophe (pompier, ambulancier, bénévole secouriste, équipe d'urgence médicale). *Note : le concept d'équipe d'intervention de catastrophe (DMAT au Japon) est une qualification organisationnelle externe `[NON VÉRIFIÉ]` ; dans le code, cet acteur correspond au rôle `rescuer` (`mesh/codec/EventChunk.kt:8`).*
- **Rôle & Actions** :
  - `[EXISTANT (ui/profiles/import_qr/QrImportScanFragment.kt:1-60)]` : Détection et lecture immédiate du passeport de la victime par scan QR caméra.
  - `[EXISTANT (sos/JemmaSosBleScanner.kt:49-80)]` : Écoute radio des balises d'urgence de proximité.
  - `[EXISTANT (triage/SaltCode.kt:34-51)]` : Attribution d'un statut de triage de catastrophe SALT : WAIT (gris, `SaltCode.kt:36`), EVAL (jaune, `SaltCode.kt:39`), STAB (vert, `SaltCode.kt:42`), HELP (rouge, `SaltCode.kt:45`), EVAC (bleu, `SaltCode.kt:48`), DCD (noir, `SaltCode.kt:51`).
  - `[EXISTANT (mesh/codec/EventChunk.kt:8)]` : Propagation de l'événement de tri SALT sous la forme `E|<source>|<victim>|<status>|<rescuer>|<ts>|<ttl>|<seq>`.
  - `[EXISTANT (ai/medscan/MedScanController.kt:1-40)]` : Scan optique de boîtes de médicaments trouvées sur place pour vérifier l'absence d'allergie ou d'interaction avec le profil de la victime.
- **Contraintes** : Environnement hostile, bruit, coupure réseau totale, luminosité variable (obscurité, plein soleil), temps d'analyse par victime compté en secondes.

### A.4. Soignant (Médecin urgentiste, réanimateur, généraliste, infirmier)
- **Définition** : Professionnel de santé habilité à poser un diagnostic, prescrire ou administrer des thérapeutiques invasives.
- **Rôle & Actions** :
  - `[EXISTANT (profiles/ProfilesRepository.kt:23-28)]` : Prise de connaissance approfondie du dossier IPS complet à travers le Bundle HL7 FHIR R4 standardisé (`.fhir.json`).
  - `[EXISTANT (kb/KbCrossCheck.kt:130-135)]` : Exécution de contrôles croisés médicamenteux rigoureux (`checkOneDrugAgainstProfile`, `kb/KbCrossCheck.kt:646`) avant injection ou geste chirurgical.
  - `[PROPOSÉ]` : Importation du Bundle HL7 FHIR R4 dans le dossier médical hospitalier (DPI/EHR) d'un poste médical avancé ou d'un hôpital de référence.
- **Contraintes** : Exigence absolue de traçabilité, de non-corruption des données médicales et de conformité aux nomenclatures officielles (SNOMED CT, LOINC, ATC, ICD-10).

### A.5. Pharmacien
- **Définition** : Professionnel de santé d'officine ou de pharmacie hospitalière de campagne.
- **Rôle & Actions** :
  - `[PROPOSÉ]` : Lecture du passeport d'un patient se présentant sans ordonnance papier (sinistré ayant fui son domicile sans traitement).
  - `[EXISTANT (kb/KbTranslations.kt:1-50)]` : Identification des médicaments chroniques via leurs codes ATC ou DCI, même sous un nom de marque étranger ou en katakana.
  - `[EXISTANT (kb/KbCrossCheck.kt:130-135)]` : Vérification de l'absence d'interactions médicamenteuses délétères (DDI) et d'allergies croisées lors de la délivrance de dépannage.
- **Contraintes** : Accès restreint ou nul aux serveurs d'assurance maladie en situation de blackout ; responsabilité de délivrance sans ordonnance originale.

### A.6. Interprète / Médiateur Culturel
- **Définition** : Personne assurant la traduction linguistique entre la victime étrangère et les intervenants locaux (ex: interprète anglais/japonais pour `demo_kurodo`).
- **Rôle & Actions** :
  - `[EXISTANT (qr/JemmaTranslations.kt:5-30)]` : Consultation de la version textuelle du passeport traduite dans la langue locale du pays d'accueil (dictionnaires 25 langues).
  - `[PROPOSÉ]` : Explication des symptômes et allergies critiques sans altération sémantique des termes médicaux.
- **Contraintes** : Souvent dépourvu de formation médicale approfondie ; ne doit pas interpréter librement les posologies ou les termes nosologiques.

### A.7. Administrateur d'un Lieu d'Accueil (Refuge / Centre d'Évacuation)
- **Définition** : Responsable municipal ou bénévole en charge de l'enregistrement et de la logistique d'un gymnase ou refuge de sinistrés.
- **Rôle & Actions** :
  - `[PROPOSÉ]` : Recensement des personnes accueillies et identification des profils à haute vulnérabilité (femmes enceintes `pg`, personnes appareillées ou à mobilité réduite `fs`, dialysés, diabétiques insulino-dépendants).
  - `[PROPOSÉ]` : Tenue du registre des personnes présentes sans exposer publiquement le secret médical complet (voir `DEC-09`).
  - `[PROPOSÉ]` : Gestion des régimes alimentaires stricts liés aux allergies vitales recensées.
- **Contraintes** : Matériel hétérogène (ordinateur personnel de fortune, tablettes municipales, fiches papier), absence de qualification soignante.

### A.8. Personne Sans Appareil
- **Définition** : Sinistré, victime inconsciente, enfant égaré, personne âgée non équipée ou personne dont le téléphone est détruit, déchargé ou perdu.
- **Rôle & Actions** :
  - `[PROPOSÉ]` : Acteur passif de la prise en charge : porterait sur elle des supports physiques de substitution (Pocket Pass imprimé, carte NFC au poignet, clé USB autour du cou).
- **Contraintes** : Incapacité matérielle totale à générer un flux radio ou un affichage dynamique ; dépend à 100 % de la lisibilité des supports tangibles par les tiers.

---

## 3. Section B : Taxonomie des Appareils et Interfaces

L'écosystème JemmaPass doit opérer sur un parc hétérogène de 9 terminaux et supports physiques.

```
┌────────────────────────────────────────────────────────────────────────┐
│               TAXONOMIE DES 9 APPAREILS ET SUPPORTS                   │
├───────────────────────────────┬────────────────────────────────────────┤
│ Terminaux Numériques Dédiés   │ 1. Téléphone Android (App native)      │
│                               │ 2. iPhone (App iOS / Lecteur Natif)    │
│                               │ 5. Tablette partagée (Poste secours)   │
├───────────────────────────────┼────────────────────────────────────────┤
│ Environnements Web & Bureaux  │ 3. Extension Chrome (PC connecté/hors) │
│                               │ 4. Ordinateur sans rien d'installé     │
├───────────────────────────────┼────────────────────────────────────────┤
│ Périphériques Ultra-Légers    │ 6. Montre connectée (WearOS / watchOS) │
├───────────────────────────────┼────────────────────────────────────────┤
│ Supports Physiques & Amovibles│ 7. Papier imprimé (Pocket Pass PDF)    │
│                               │ 8. Carte NFC (Badge sans contact)      │
│                               │ 9. Clé USB (Dossier autonome universel)│
└───────────────────────────────┴────────────────────────────────────────┘
```

### B.1. Téléphone Android
- **Configuration** : Smartphone sous Android 10+ (API 29+), application native JemmaPass installée.
- **Capacités vérifiées dans le code** :
  - Caméra / Scan QR : `[EXISTANT (ui/profiles/import_qr/QrImportScanFragment.kt:1-60)]`.
  - Bluetooth BLE Advertising (paquets de 200 octets max) : `[EXISTANT (sos/JemmaSosChunkCodec.kt:171-175, sos/JemmaSosBleAdvertiser.kt:28)]`.
  - Wi-Fi P2P / Découverte de proximité Google Nearby Connections (limite 131 octets UTF-8 pour le nom d'endpoint) : `[EXISTANT (sos/JemmaNearbyEndpointCodec.kt:51, sos/JemmaNearbySosService.kt:1-40)]`.
  - Synthèse vocale multilingue : `[EXISTANT (ai/tts/TtsService.kt:1-30)]`.
  - Stockage local : base SQLite `knowledge_full.db` (taille déclarée `3_360_727_040L` octets, `downloads/JemmaModelCatalog.kt:89`), modèles LiteRT-LM (`downloads/JemmaModelCatalog.kt:5-6`), dossiers profils atomiques (`ProfileFiles.kt`).
- **Contraintes** : Autonomie batterie en zone sinistrée, gestion agressive des processus en arrière-plan par l'OS `[NON VÉRIFIÉ]`.

### B.2. iPhone
- **Configuration** : Smartphone sous iOS 16+ `[NON VÉRIFIÉ]`, modèle grand public (part de marché au Japon estimée à ~68,2 % selon StatCounter août 2026 `[NON VÉRIFIÉ]`).
- **Modes de Fonctionnement envisagés** :
  - *Mode Natif Léger (Sans application JemmaPass)* : `[PROPOSÉ]` Utilisation de l'application native « Appareil photo » d'Apple qui lirait directement le **QR Texte Universel** (≤ 1800 octets UTF-8, `qr/JemmaTextPayloadBuilder.kt:67`) sans connexion réseau ni application tierce requise.
  - *Mode Application Dédiée (Portage iOS)* : `[PROPOSÉ]` Application Swift/SwiftUI exécutant le moteur de règles, la lecture QR `_j2`, et l'accès aux profils.
- **Contraintes identifiées** :
  - Non-interopérabilité native entre Google Nearby Connections (Android) et les frameworks natifs d'Apple (CoreBluetooth / MultipeerConnectivity) sans passerelle ou profil radio commun `[NON VÉRIFIÉ (constaté dans docs/SYNTHESE_PORTAGE_IOS.md:88-90, 128)]`.
  - Contraintes de mémoire vive sous iOS (`EXC_RESOURCE`) pour les modèles LLM lourds `[NON VÉRIFIÉ]`.

### B.3. Extension Chrome / Navigateur Bureau
- **Configuration** : Navigateur Google Chrome / Chromium sur PC Windows, Mac, Linux ou ChromeOS, avec extension JemmaPass `[PROPOSÉ]`.
- **Capacités envisagées** :
  - `[PROPOSÉ]` Affichage grand format pour consultation médicale ou officine de pharmacie.
  - `[PROPOSÉ]` Décodage de flux vidéo via webcam pour scanner les QR codes.
  - `[PROPOSÉ]` Import/Export de fichiers via l'API FileSystemAccess `[NON VÉRIFIÉ]`.
  - `[NON VÉRIFIÉ]` Échanges sans contact via WebNFC (disponible uniquement sous ChromeOS/Android Chrome, non supporté nativement sous Windows/macOS).

### B.4. Ordinateur Sans Rien d'Installé (Kiosque / PC d'Urgence)
- **Configuration** : PC d'accueil, terminal de bibliothèque, poste hospitalier verrouillé sans droits d'administration (aucun runtime Java/Kotlin, pas d'installation permise, pas d'extension, réseau Internet potentiellement coupé) `[PROPOSÉ]`.
- **Interfaces Disponibles** : Navigateur web par défaut (Edge, Safari, Firefox, Chrome), ports USB-A ou USB-C, lecteur de documents PDF standard.
- **Exigence Vitale** : `[PROPOSÉ]` Doit pouvoir ouvrir et afficher le passeport médical depuis une clé USB sans exécutable tiers ni connexion réseau via un fichier HTML autonome zéro-dépendance.

### B.5. Tablette Partagée (Poste Médical Avancé)
- **Configuration** : Tablette Android ou iPad, utilisée en rotation par plusieurs soignants ou secouristes sur un centre de tri `[PROPOSÉ]`.
- **Capacités envisagées** :
  - `[PROPOSÉ]` Écran large propice au triage multi-victimes (Radar SALT).
  - `[PROPOSÉ]` Caméra dorsale pour scan à la chaîne des QR codes de victimes.
- **Contraintes** : `[PROPOSÉ]` Nécessité d'un mode multi-utilisateurs strict avec vidage de cache sécurisé pour éviter toute contamination croisée des profils médicaux.

### B.6. Montre Connectée (Smartwatch Wear OS / Apple Watch)
- **Configuration** : Périphérique porté au poignet, connecté en Bluetooth au smartphone ou autonome `[PROPOSÉ]`.
- **Interfaces Disponibles** : Écran OLED réduit, capteurs biométriques, puce NFC, vibreur haptique `[NON VÉRIFIÉ]`.
- **Rôle d'Urgence envisagé** :
  - `[PROPOSÉ]` Affichage d'un QR code de détresse ultra-compact (≤ 300 octets).
  - `[PROPOSÉ]` Diffusion d'une balise SOS BLE de secours si le smartphone principal est perdu ou déchargé.
- **Contraintes** : Résolution optique limitée pour les QR denses (version QR élevée illisible), autonomie batterie réduite (12 à 36 h) `[NON VÉRIFIÉ]`.

### B.7. Papier Imprimé (Pocket Pass & Fiche de Tri)
- **Configuration** : Feuille A4 standard pliée au format carte de crédit issue du générateur PDF (`[EXISTANT (qr/JemmaPdfExporter.kt:40-100)]`), ou étiquette de tri physique `[PROPOSÉ]`.
- **Contenu Imprimé** :
  - Recto : Données vitales en clair (nom, groupe sanguin, allergies majeures, médicaments critiques, contacts d'urgence) avec pictogrammes normalisés `[EXISTANT (qr/JemmaPdfExporter.kt:530-580)]`.
  - Verso : QR codes haute densité (QR Texte Universel + QR Compact `_j2`) `[EXISTANT (qr/JemmaPdfExporter.kt:500-520)]`.
- **Atouts & Limites** : Zéro dépendance énergétique, insensible à l'eau si plastifié ; statique (non mis à jour après changement d'ordonnance), dégradable si mouillé ou brûlé.

### B.8. Carte NFC (Badge Physique Passif)
- **Configuration** : Carte PVC au format ISO 7810 ID-1 `[NON VÉRIFIÉ]` ou bracelet silicone de sinistré, embarquant une puce sans contact (NTAG213 ou NTAG216) `[PROPOSÉ]`.
- **Capacité Mémoire** : De 144 octets (NTAG213) à 888 octets (NTAG216) selon les spécifications industrielles NFC Forum Type 2 `[NON VÉRIFIÉ]`.
- **Atouts & Limites** : Lisible sans allumer le terminal émetteur ; capacité mémoire insuffisante pour un Bundle FHIR R4 complet sans compression extrême ou pointeur URI.

### B.9. Clé USB (Support Amovible Universel)
- **Configuration** : Clé physique double connecteur USB-A / USB-C, formatée en FAT32 ou exFAT pour interopérabilité universelle `[PROPOSÉ]`.
- **Contenu Dédié** : Dossier racine JemmaPass autonome contenant la visionneuse HTML universelle zéro-dépendance, les fichiers FHIR JSON, le PDF Pocket Pass `[PROPOSÉ]`.
- **Atouts & Limites** : Stockage massif, lisibilité sur tout PC sans réseau ; vulnérable à l'arrachement, à la perte mécanique ou aux politiques de blocage des ports USB d'entreprise.

---

## 4. Section C : Matrice Exhaustive des Canaux d'Échange par Paire d'Appareils

### C.1. Définition des 8 Canaux d'Échange
1. **QR-TXT** : QR Code texte universel brut (plafond strict ≤ 1800 octets UTF-8 `MAX_BYTES`, `[EXISTANT (qr/JemmaTextPayloadBuilder.kt:67)]`), découpé en lignes lisibles directement par tout appareil photo standard sans décodeur spécial.
2. **QR-CMP** : QR Code compact compressé `_j2` (RFC 1951 Deflate-raw + Base64url, `[EXISTANT (qr/JemmaPayloadCodec.kt:30)]`), lisible par tout lecteur compatible JemmaPass.
3. **QR-FHR** : QR Code FHIR multi-trames animé (`JF:i/N`, `[EXISTANT (qr/JemmaQrFrameSplitter.kt:20-34, qr/JemmaQrFrameAssembler.kt:26)]`), transportant le Bundle HL7 FHIR R4 complet par défilement séquentiel.
4. **FILE** : Transfert direct de fichiers numériques (`.json`, `.fhir.json`, `.pdf`, `.html`) via système de fichiers ou support amovible.
5. **NFC** : Échange en champ proche sans contact (norme ISO 14443A / NDEF `[NON VÉRIFIÉ]`).
6. **P2P-RAD** : Réseau radio maillé de proximité (BLE Extended Advertising 200 octets `MAX_CHUNK_BYTES`, `[EXISTANT (sos/JemmaSosChunkCodec.kt:171)]` ; Google Nearby Connections 131 octets `MAX_ENDPOINT_NAME_LEN`, `[EXISTANT (sos/JemmaNearbyEndpointCodec.kt:51)]` ; Apple MultipeerConnectivity `[PROPOSÉ]` ; WebBluetooth `[NON VÉRIFIÉ]`).
7. **USB** : Connexion physique filaire USB (Mass Storage ou liaison câble OTG).
8. **PAPER** : Support papier physique (Pocket Pass imprimé ou étiquette manuscrite de tri).

---

### C.2. Tableau Matriciel Global (Émetteur ➔ Récepteur)

Chaque case de la matrice 9 × 9 indique le statut et son étiquette obligatoire :
- `[EXISTANT (fichier:ligne)]` : Le code source actuel prend en charge ce canal.
- `[PROPOSÉ]` : Canal techniquement faisable mais n'existant pas dans le dépôt.
- `[NON VÉRIFIÉ]` : Faisabilité dépendante d'une contrainte matérielle ou d'un protocole tiers non prouvé.
- `[IMPOSSIBLE]` : Physiquement ou matériellement irréalisable (absence de capteur ou interface).

| Émetteur \ Récepteur | 1. Android | 2. iPhone | 3. Chrome Ext | 4. PC Nu | 5. Tablette | 6. Montre | 7. Papier | 8. Carte NFC | 9. Clé USB |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1. Téléphone Android** | [EXISTANT (qr/ui)] | [PROPOSÉ] (P2P [NON VÉRIFIÉ]) | [PROPOSÉ] | [PROPOSÉ] | [EXISTANT (qr/ui)] | [PROPOSÉ] | [EXISTANT (qr/JemmaPdfExporter.kt:40)] | [PROPOSÉ] | [PROPOSÉ] |
| **2. iPhone** | [PROPOSÉ] (P2P [NON VÉRIFIÉ]) | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] |
| **3. Extension Chrome** | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [IMPOSSIBLE] | [PROPOSÉ] | [NON VÉRIFIÉ] | [PROPOSÉ] |
| **4. Ordinateur Nu** | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [IMPOSSIBLE] | [PROPOSÉ] | [IMPOSSIBLE] | [PROPOSÉ] |
| **5. Tablette Partagée** | [EXISTANT (qr/ui)] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [EXISTANT (qr/ui)] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] |
| **6. Montre Connectée** | [PROPOSÉ] | [PROPOSÉ] | [IMPOSSIBLE] | [IMPOSSIBLE] | [PROPOSÉ] | [PROPOSÉ] | [IMPOSSIBLE] | [IMPOSSIBLE] | [IMPOSSIBLE] |
| **7. Papier Imprimé** | [EXISTANT (ui/scan)] | [PROPOSÉ] | [PROPOSÉ] | [IMPOSSIBLE] | [EXISTANT (ui/scan)] | [IMPOSSIBLE] | [IMPOSSIBLE] | [IMPOSSIBLE] | [IMPOSSIBLE] |
| **8. Carte NFC** | [PROPOSÉ] | [PROPOSÉ] | [NON VÉRIFIÉ] | [IMPOSSIBLE] | [PROPOSÉ] | [NON VÉRIFIÉ] | [IMPOSSIBLE] | [IMPOSSIBLE] | [IMPOSSIBLE] |
| **9. Clé USB** | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [PROPOSÉ] | [IMPOSSIBLE] | [IMPOSSIBLE] | [IMPOSSIBLE] | [IMPOSSIBLE] |

---

### C.3. Analyse Détaillée des 12 Paires d'Échange Critiques

#### Paire 1 : Téléphone Android ➔ Téléphone Android
- **QR-TXT** : `[EXISTANT (qr/JemmaTextPayloadBuilder.kt:152)]` Émission d'un payload texte UTF-8 ≤ 1800 octets, scannable via CameraX / ML Kit (`ui/profiles/import_qr/QrImportScanFragment.kt:1-60`).
- **QR-CMP** : `[EXISTANT (qr/JemmaPayloadCodec.kt:30)]` Compression Deflate-raw `_j2` et décodage inter-appareils.
- **QR-FHR** : `[EXISTANT (qr/JemmaQrFrameSplitter.kt:26, qr/JemmaQrFrameAssembler.kt:26)]` Carrousel multi-trames réassemblé côté récepteur.
- **FILE** : `[EXISTANT (profiles/ProfilesRepository.kt:23-28)]` Échange de fichiers `<sid>.fhir.json` et `<sid>.json`.
- **NFC** : `[PROPOSÉ]` Partage de payload NDEF via Android Host Card Emulation (HCE).
- **P2P-RAD** : `[EXISTANT (sos/JemmaNearbySosService.kt:1-40)]` Relayage d'événements de secours Nearby Connections et `[EXISTANT (sos/JemmaSosBleAdvertiser.kt:28)]` BLE Extended Advertising (200 octets).
- **USB** : `[PROPOSÉ]` Transfert direct de fichier par câble USB-C OTG.
- **PAPER** : `[EXISTANT (qr/JemmaPdfExporter.kt:40)]` Génération du Pocket Pass PDF imprimable.

#### Paire 2 : Téléphone Android ➔ iPhone
- **QR-TXT** : `[PROPOSÉ]` Le QR Texte Universel émis par Android est lisible directement par l'application Caméra native d'iOS sans application tierce installée `[NON VÉRIFIÉ (décrit dans docs/SYNTHESE_PORTAGE_IOS.md:81-84)]`.
- **QR-CMP** : `[PROPOSÉ]` Nécessiterait une application iOS dédiée implémentant le décodeur Deflate-raw `_j2`.
- **QR-FHR** : `[PROPOSÉ]` Nécessiterait une application iOS pour filmer et concaténer le carrousel `JF:i/N`.
- **FILE** : `[PROPOSÉ]` Partage de fichier direct par messagerie locale ou adaptateur physique.
- **NFC** : `[PROPOSÉ]` Lecture par l'iPhone d'un tag NDEF émis par le smartphone Android.
- **P2P-RAD** : `[NON VÉRIFIÉ / PROBLÈME OUVERT]` Google Nearby Connections côté Android n'est pas nativement interopérable avec Apple MultipeerConnectivity ou CoreBluetooth sans couche de compatibilité ad hoc (`docs/SYNTHESE_PORTAGE_IOS.md:88-90, 128`).
- **USB** : `[PROPOSÉ]` Liaison filaire USB-C vers Lightning/USB-C via l'application Fichiers d'iOS `[NON VÉRIFIÉ]`.

#### Paire 3 : iPhone ➔ Téléphone Android
- **QR-TXT** : `[PROPOSÉ]` Émis par l'écran de l'iPhone, scanné par la caméra Android via `[EXISTANT (ui/profiles/import_qr/QrImportScanFragment.kt:1-60)]`.
- **QR-CMP** : `[PROPOSÉ]` Nécessiterait un générateur `_j2` conforme sous iOS.
- **QR-FHR** : `[PROPOSÉ]` Rendu carrousel sous iOS, assemblé par `[EXISTANT (qr/JemmaQrFrameAssembler.kt:26)]`.
- **FILE** : `[PROPOSÉ]` Importation sur Android de fichiers FHIR `.fhir.json` standardisés issus d'iOS.
- **NFC** : `[PROPOSÉ]` Écriture NFC par iPhone restreinte par les APIs Apple CoreNFC `[NON VÉRIFIÉ]`.
- **P2P-RAD** : `[NON VÉRIFIÉ]` Incompatibilité radio directe identique à la Paire 2.

#### Paire 4 : Téléphone (Android / iOS) ➔ Extension Chrome
- **QR-TXT & QR-CMP** : `[PROPOSÉ]` L'extension Chrome utiliserait la webcam de l'ordinateur pour décoder le QR code affiché sur le téléphone.
- **FILE** : `[PROPOSÉ]` Glisser-déposer de fichiers exportés (`.json` ou `.fhir.json`) dans l'interface Chrome.
- **NFC** : `[NON VÉRIFIÉ]` WebNFC non supporté nativement sous Windows/macOS sans middleware.
- **P2P-RAD** : `[NON VÉRIFIÉ]` WebBluetooth permettrait la réception de trames spécifiques mais ne supporte pas l'Advertising ni Nearby Connections.

#### Paire 5 : Téléphone (Android / iOS) ➔ Ordinateur Nu (Sans rien d'installé)
- **QR-TXT** : `[PROPOSÉ]` Affichage du texte si une webcam et un visualiseur local sont disponibles, sinon impossible sans logiciel.
- **FILE / USB** : `[PROPOSÉ]` Le téléphone exporterait vers une clé USB (via adaptateur OTG) ; la clé serait insérée dans l'ordinateur nu.
- **Consultation sur PC Nu** : `[PROPOSÉ]` Un fichier `CONSULTER_URGENCE.html` autonome placé sur la clé USB permettrait d'afficher le passeport dans n'importe quel navigateur (Edge, Safari, Chrome, Firefox) sans accès internet ni droits administrateur.

#### Paire 6 : Téléphone (Android / iOS) ➔ Tablette Partagée
- `[PROPOSÉ]` Mêmes canaux que le transfert téléphone-téléphone, la tablette servant de concentrateur de poste de tri de secours.

#### Paire 7 : Téléphone ➔ Montre Connectée
- **P2P-RAD (BLE)** : `[PROPOSÉ]` Synchronisation locale de secours via Bluetooth standard entre le téléphone et la montre du titulaire.
- **QR d'Urgence** : `[PROPOSÉ]` Envoi vers la montre d'une version ultra-compacte du QR texte ou du QR SOS, stockée pour affichage autonome sur l'écran en cas de batterie épuisée sur le smartphone.

#### Paire 8 : Montre Connectée ➔ Secouriste (Android / iPhone)
- **QR-TXT Réduit** : `[PROPOSÉ]` L'écran de la montre afficherait un QR Code version 10-15 contenant l'identité, le groupe sanguin et les allergies vitales (budget réduit ≤ 300 octets).
- **BLE SOS** : `[PROPOSÉ]` La montre diffuserait en boucle un identifiant SOS capté par le scanner BLE du secouriste (`[EXISTANT (sos/JemmaSosBleScanner.kt:49)]`).

#### Paire 9 : Papier Imprimé (Pocket Pass) ➔ N'importe quel Appareil
- **Lecture Oculaire Humaine** : `[EXISTANT]` Le secouriste lit les données vitales imprimées en clair sur le papier généré par `JemmaPdfExporter` (`qr/JemmaPdfExporter.kt:530-580`).
- **Lecture Optique QR** : `[EXISTANT (ui/profiles/import_qr/QrImportScanFragment.kt:1-60)]` La caméra scanne le QR code haute densité imprimé.
- **Limitation Absolue** : `[IMPOSSIBLE]` Transfert unidirectionnel strict. Le papier ne peut recevoir aucune mise à jour radio.

#### Paire 10 : Carte NFC ➔ Téléphone (Android / iOS)
- **NFC NDEF** : `[PROPOSÉ]` Lecture sans contact en approchant le téléphone de la carte au portefeuille ou au poignet de la victime.
- **Limitation** : `[NON VÉRIFIÉ]` Ne pourrait contenir qu'un extrait court (payload compact `_j2` de moins de 888 octets sur NTAG216).

#### Paire 11 : Clé USB ➔ Ordinateur Nu
- **USB Mass Storage** : `[PROPOSÉ]` Format FAT32/exFAT reconnu universellement.
- **Consultation** : `[PROPOSÉ]` Double-clic sur `CONSULTER_URGENCE.html` ou `POCKET_PASS.pdf`.

#### Paire 12 : Clé USB ➔ Téléphone Android / Tablette
- **USB-C OTG** : `[PROPOSÉ]` L'application JemmaPass importerait le dossier via le Storage Access Framework d'Android.

---

## 5. Section D : Découpage Normalisé des Domaines Fonctionnels

Pour assurer la traçabilité intégrale de l'analyse fonctionnelle sur les livraisons suivantes, l'espace des cas d'usage est partitionné en **29 domaines normalisés** sous la nomenclature `UC-<domaine>-<numéro>`.

```
┌────────────────────────────────────────────────────────────────────────┐
│             NOMENCLATURE DES 29 DOMAINES DE CAS D'USAGE                │
├───────┬────────────────────────────────────────────────────────────────┤
│ SOCLE │ STO : Persistance, stockage local, atomicité, concurrence      │
│       │ TRV : Aspects transversaux (consentement, traçabilité, versions)│
├───────┼────────────────────────────────────────────────────────────────┤
│ LES   │ PAT : Identité, état civil, multi-identifiants (MyNumber, NSS) │
│ 18    │ ALG : Allergies, substances, criticités, réactions multiples   │
│ PILI- │ MED : Médications en cours, posologies, 5 voies d'admin        │
│ ERS   │ PRB : Problèmes actifs, diagnostics en cours (LOINC 11450-4)   │
│ IPS   │ PST : Antécédents résolus, historique chirurgical (11348-0)    │
│       │ IMM : Vaccinations, dates, lots, rappels (LOINC 11369-6)       │
│       │ PRC : Procédures et actes médicaux majeurs (LOINC 47519-4)     │
│       │ DEV : Dispositifs médicaux, implants, pacemaker, UDI GS1       │
│       │ RES : Biologie, examens et Groupe Sanguin LOINC 882-1          │
│       │ PRG : Grossesse, parité, terme présumé (LOINC 10162-6)         │
│       │ FNC : Statut fonctionnel, handicaps, aides (LOINC 47420-5)     │
│       │ CTC : Contacts d'urgence, liens v3 RoleCode, ordre d'appel     │
│       │ STB : Les 6 piliers stubs (Directives, Consentements, Soignants)│
├───────┼────────────────────────────────────────────────────────────────┤
│ MOTEUR│ SEC : Sécurité clinique, DDI (DDInter 2.0), allergies croisées │
│ & IA  │ OCR : Reconnaissance optique et scan de médicaments            │
│       │ TTS : Synthèse vocale multilingue d'urgence                    │
│       │ LLM : Vulgarisation IA locale (Gemma 4) et 21 outils @Tool     │
├───────┼────────────────────────────────────────────────────────────────┤
│ CANAUX│ QRT : Canal QR texte universel (≤ 1800 octets, éviction rangs) │
│ D'É-  │ QRC : Canal QR compact compressé _j2 (RFC 1951 Deflate-raw)    │
│ CHANGE│ QRF : Canal QR FHIR multi-trames animé (JF:i/N)               │
│       │ NFC : Échanges sans contact NDEF                               │
│       │ USB : Dossier clé USB universel autonome                       │
│       │ PDF : Pocket Pass imprimable et fiches de terrain              │
├───────┼────────────────────────────────────────────────────────────────┤
│ CRISE │ SOS : Alertes de détresse, widget lockscreen, balises radio    │
│ & MA- │ TRG : Triage de catastrophe SALT (grille 6 statuts)            │
│ TÉRIEL│ MSH : Réseau maillé de proximité (Nearby / BLE)                │
│       │ CRS : Scénarios de crise extrême (blackout, décès, inconscience)│
└───────┴────────────────────────────────────────────────────────────────┘
```

---

## 6. Section E : Registre des Décisions Ouvertes à Faire Prendre par Kudoro

Conformément à la règle de gouvernance absolue : **l'orchestrateur ne tranche aucun choix de conception à la place de Kudoro**. Chaque arbitrage ouvert est catalogué ci-dessous avec ses options, leurs arguments et leurs conséquences directes sur la sécurité des patients et la faisabilité technique.

---

### DEC-01 : Politique de Chiffrement du Dossier sur Clé USB
- **Problématique** : Faut-il chiffrer les données médicales stockées sur la clé USB d'urgence ?
- **Contexte** : Une clé USB portée sur un trousseau peut être égarée ou volée. Cependant, lors d'un accident ou d'une inconscience, un médecin urgentiste étranger ne disposera pas du mot de passe de la victime.
- **Options en balance** :
  - **Option A (Chiffrement au repos standard, ex: AES-GCM avec mot de passe ou clé dérivée)** :
    - *Avantages* : Confidentialité totale en cas de perte de la clé. Conforme aux recommandations strictes RGPD / APPI sur le stockage de données sensibles `[NON VÉRIFIÉ]`.
    - *Conséquences Médicales / Risques* : **Blocage absolu des soins d'urgence** si la victime est comateuse ou confuse et qu'aucun aidant n'est joignable pour fournir le mot de passe.
  - **Option B (Dossier non chiffré en clair, assimilé au portefeuille d'urgence)** :
    - *Avantages* : Accessibilité vitale immédiate sur n'importe quel ordinateur d'hôpital par simple branchement.
    - *Conséquences / Risques* : Exposition de la vie privée en cas de vol ou de perte matérielle de la clé USB.
  - **Option C (Dossier à deux niveaux / Hybride)** :
    - *Avantages* : Un fichier d'urgence vital non chiffré (`URGENCE_VITALE.html` : groupe sanguin, allergies mortelles, traitements vitaux, contacts) + un conteneur chiffré pour l'historique complet (séjours, bilans biologiques détaillés, notes intimes).
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-02 : Format Maître de Consultation Autonome sur la Clé USB
- **Problématique** : Sous quel format principal doit être présentée la visionneuse de la clé USB pour garantir une lisibilité sur 100 % des ordinateurs mondiaux ?
- **Contexte** : L'ordinateur cible peut être un vieux PC sous Windows 7, un Mac sous macOS Sonoma, ou un terminal Linux de poste frontière, sans accès internet.
- **Options en balance** :
  - **Option A (Fichier unique HTML/CSS autonome avec JavaScript embarqué local)** :
    - *Avantages* : Interactif, multilingue, recherche locale instantanée, affichage conditionnel des alertes, zéro dépendance réseau.
    - *Risques* : Certains postes ultra-sécurisés en milieu hospitalier désactivent l'exécution de scripts JavaScript locaux provenant de volumes amovibles (`file:///`) `[NON VÉRIFIÉ]`.
  - **Option B (Fichier PDF statique multi-pages imprimable)** :
    - *Avantages* : Format universellement lisible par le visualiseur natif de n'importe quel OS. Aucun script requis.
    - *Risques* : Statique, mise en page figée, non filtrable dynamiquement selon la langue de l'urgentiste.
  - **Option C (Ensemble composite : HTML interactif + PDF statique miroir + JSON FHIR brut)** :
    - *Avantages* : Redondance maximale garantissant une solution de repli quel que soit le niveau de verrouillage du poste.
    - *Risques* : Nécessité de synchroniser rigoureusement les 3 formats à chaque mise à jour pour éviter toute divergence d'information.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-03 : Stratégie de Résolution du Pont P2P Radio Android ↔ iOS en Zone Sinistrée
- **Problématique** : Comment faire communiquer les secouristes sous Android et les victimes ou soignants sous iPhone lors d'un blackout total ?
- **Contexte** : Le protocole Google Nearby Connections utilisé par l'application Android (`MAX_ENDPOINT_NAME_LEN = 131`, `[EXISTANT (sos/JemmaNearbyEndpointCodec.kt:51)]`) ne communique pas avec les frameworks natifs d'Apple (CoreBluetooth / MultipeerConnectivity) sans implémentation sur-mesure d'un protocole commun `[NON VÉRIFIÉ (docs/SYNTHESE_PORTAGE_IOS.md:88-90, 128)]`.
- **Options en balance** :
  - **Option A (Reliance exclusive sur les canaux optiques QR et le papier)** :
    - *Avantages* : Zéro défi d'ingénierie radio multi-plateforme. Fiabilité éprouvée du QR Texte 1800 octets déchiffrable par l'appareil photo iOS.
    - *Risques* : Perte des alertes de détresse passives à distance (le secouriste ne détecte pas une victime ensevelie sous les décombres qui diffuse en radio).
  - **Option B (Normalisation d'un profil BLE GATT ouvert universel)** :
    - *Avantages* : Permet une diffusion de balises d'urgence SOS captables de manière croisée entre Android et iOS.
    - *Risques* : Forte complexité d'ingénierie, limitations sévères d'Apple sur l'écoute BLE en tâche de fond sur iOS `[NON VÉRIFIÉ]`.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-04 : Granularité des Données de Santé Visibles Sans Déverrouiller le Smartphone
- **Problématique** : Quelles informations de santé doivent être accessibles depuis l'écran de verrouillage (Lockscreen Widget, Live Activity, raccourci d'urgence) ?
- **Contexte** : Une personne inconsciente ne peut pas déverrouiller son smartphone par empreinte ou code PIN. Cette question s'articule avec le débat déjà ouvert sur l'inclusion ou l'exclusion des directives anticipées (`ad`) et consentements (`cs`) dans les affichages publics/QR sans déverrouillage (`docs/analysis/remaining-pillars.md:10-12`).
- **Options en balance** :
  - **Option A (Fiche vitale d'urgence seule)** :
    - *Contenu* : Nom/Prénom, Âge, Groupe Sanguin, Allergies létales (ex: Pénicilline pour `demo_kurodo`), 2 numéros de contacts d'urgence (`ICE`).
    - *Avantages* : Respect maximal de la vie privée ; exposition minimale en cas de simple perte du téléphone.
  - **Option B (Accès au QR Code Texte complet via le widget)** :
    - *Contenu* : QR Code complet projeté à l'écran de veille, permettant aux secours de scanner l'ensemble des 18 piliers.
    - *Risques* : N'importe quel tiers indiscret peut scanner l'écran du smartphone et découvrir l'ensemble des pathologies et antécédents de l'utilisateur à son insu.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-05 : Règle de Résolution des Conflits de Synchronisation Multi-Terminaux
- **Problématique** : Quand un profil est modifié séparément sur un téléphone et sur une clé USB (ou tablette), quelle règle prévaut lors de la réconciliation ?
- **Contexte** : En situation d'évacuation, un soignant peut ajouter une injection sur une fiche locale alors que l'aidant a modifié les coordonnées sur son smartphone.
- **Options en balance** :
  - **Option A (Dernier Écrivain Gagne - Last-Write-Wins / LWW basé sur horodatage)** :
    - *Risques* : Les horloges des appareils peuvent être désynchronisées en zone de blackout. Risque d'écrasement silencieux d'un acte médical critique saisi récemment.
  - **Option B (Union additive sans suppression / Append-Only)** :
    - *Comportement* : Les nouveaux médicaments ou allergies sont fusionnés sans jamais supprimer les entrées existantes ; les suppressions nécessitent une confirmation explicite de l'utilisateur.
    - *Avantages* : Aucun risque de perte d'une allergie ou d'un antécédent critique par écrasement.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-06 : Statut Clinique face aux Médicaments Non Résolus dans la Base de Connaissances
- **Problématique** : Que doit afficher l'interface lorsqu'un médicament saisi en texte libre ou scanné par OCR n'est pas répertorié dans `knowledge_full.db` ?
- **Contexte** : Règle « KB seulement » (§9 de `PROTOCOL.md`). Un médicament étranger, un complément alimentaire ou une spécialité locale peut manquer dans la base.
- **Options en balance** :
  - **Option A (Règle stricte actuelle : Verdict NOT_CHECKED noir/ambre avec avertissement bloquant)** :
    - *Comportement* : Interdiction formelle d'indiquer « Aucun risque détecté ». L'utilisateur ou le soignant doit explicitement acquitter l'avertissement « Sécurité non vérifiée ».
    - *Avantages* : Sécurité médicale maximale, élimine tout faux sentiment de sécurité.
  - **Option B (Tolérance avec mention informative mineure)** :
    - *Risques* : Risque qu'un secouriste pressé présume que l'absence de bandeau rouge vif équivaut à une autorisation d'administrer le médicament.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-07 : Rôle Opérationnel de la Carte / Badge NFC
- **Problématique** : Quel est le périmètre fonctionnel alloué au support passif NFC ?
- **Contexte** : La mémoire des cartes NTAG courantes (144 à 888 octets `[NON VÉRIFIÉ]`) est trop étroite pour stocker un Bundle FHIR R4 complet.
- **Options en balance** :
  - **Option A (Carte NFC comme simple pointeur d'URL de secours)** :
    - *Contenu* : Enregistrement NDEF URI pointant vers un serveur de secours ou un identifiant local.
    - *Risques* : Inutilisable lors d'un blackout réseau total sans connexion internet.
  - **Option B (Stockage d'un payload ultra-compact `_j2` compressé)** :
    - *Contenu* : Seul le profil minimal compressé (Identité, Groupe sanguin, Allergies majeures, Contacts) est encodé dans la mémoire de 888 octets de la puce NTAG216 `[NON VÉRIFIÉ]`.
    - *Avantages* : Fonctionne 100 % hors-ligne par simple effleurement par le smartphone du secouriste.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-08 : Comportement de la Balise SOS Radio lors du Décès Avéré (Statut SALT DCD 🕊️)
- **Problématique** : Quand un secouriste affecte le statut de triage SALT `DCD` (Noir, Décédé, `[EXISTANT (triage/SaltCode.kt:51)]`) à une victime lors d'une catastrophe, que devient la balise radio du smartphone de la victime ?
- **Contexte** : En situation d'afflux massif de victimes avec saturation des secours. Dans le code, le protocole s'appuie sur `SaltCode` (`triage/SaltCode.kt:34-51`) et `rescuer` (`mesh/codec/EventChunk.kt:8`). L'organisation « DMAT » est une appellation externe d'équipes de secours `[NON VÉRIFIÉ]`.
- **Options en balance** :
  - **Option A (Maintien de l'émission de la balise avec statut DCD)** :
    - *Avantages* : Permet aux équipes de relève de localiser les corps ultérieurement sous les décombres grâce au radar BLE (`[EXISTANT (sos/JemmaSosBleScanner.kt:49)]`).
    - *Risques* : Consomme la bande passante du réseau maillé de proximité et peut fausser la priorité des équipes de réanimation si le filtrage du radar est mal configuré.
  - **Option B (Extinction automatique de la balise SOS)** :
    - *Avantages* : Dégage immédiatement le spectre radio pour concentrer l'attention des secours sur les survivants en détresse vitale (`HELP` rouge, `[EXISTANT (triage/SaltCode.kt:45)]`).
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-09 : Niveaux d'Accès pour les Acteurs Non Soignants (Administrateurs d'Abris, Interprètes)
- **Problématique** : Les gestionnaires de centres d'évacuation doivent-ils voir l'intégralité du dossier médical des personnes accueillies ?
- **Contexte** : Respect du secret médical et de la dignité des personnes réfugiées dans un lieu collectif.
- **Options en balance** :
  - **Option A (Profil Unique Ouvert)** :
    - *Comportement* : Tout lecteur accède à l'intégralité du pass (maladies psychiatriques, antécédents gynécologiques, etc.).
    - *Risques* : Violation caractérisée du secret médical et réticence des citoyens à utiliser le passeport.
  - **Option B (Vues contextuelles sélectionnables à l'export ou au scan)** :
    - *Comportement* : Mode « Accueil / Refuge » (affiche uniquement : identité, régime alimentaire / allergies, personnes à charge, mobilité réduite / statut fonctionnel) vs Mode « Urgence Médicale » (accès aux 18 piliers).
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-10 : Vérification d'Intégrité et Signature Cryptographique des Profils
- **Problématique** : Comment garantir qu'un passeport médical présenté sur clé USB ou QR code n'a pas été altéré ou falsifié hors-ligne ?
- **Contexte** : En intervention hors-ligne, aucune autorité de certification centrale (PKI / serveur gouvernemental) n'est joignable. Cette question s'aligne directement sur le mécanisme déjà retenu pour la base de connaissances médicale (`PROTOCOL.md` §9.2), qui impose des manifestes signés et des sommes de contrôle SHA-256 pour vérifier l'intégrité avant chargement.
- **Options en balance** :
  - **Option A (Empreinte de contrôle SHA-256 locale simple, similaire à `DownloadIntegrity.kt`)** :
    - *Avantages* : Détecte immédiatement les corruptions matérielles accidentelles (clé USB défectueuse, transmission radio tronquée).
    - *Risques* : Ne protège pas contre une falsification délibérée par un tiers malveillant.
  - **Option B (Signature asymétrique locale Ed25519 liée à l'appareil de l'utilisateur)** :
    - *Avantages* : Atteste que le dossier a bien été émis par le terminal de confiance de l'utilisateur sans requérir de connexion internet.
    - *Risques* : Complexité de gestion du trousseau de clés publiques en cas de changement d'appareil.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-11 : Périmètre des Langues Supportées en Synthèse Vocale d'Urgence (TTS)
- **Problématique** : Quelles langues doivent être garanties pour la restitution vocale des alertes vitales en intervention ?
- **Contexte** : `ai/tts/TtsService.kt:1-30` sur Android s'appuie sur le moteur TTS système.
- **Options en balance** :
  - **Option A (Trio prioritaire : Japonais, Anglais, Français)** :
    - *Avantages* : Couvre le périmètre historique de test et de validation clinique du projet.
  - **Option B (Couverture étendue aux 25 langues du QR texte universel)** :
    - *Risques* : Selon les terminaux Android ou iOS, les packs de voix locaux pour certaines langues (ex: Hindi, Bengali, Vietnamien) ne sont pas préinstallés hors-ligne `[NON VÉRIFIÉ]`.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

### DEC-12 : Sauvegarde de Secours et Portabilité du Dossier (Export Zéro-Cloud)
- **Problématique** : Comment l'utilisateur sauvegarde-t-il son passeport pour ne pas le perdre en cas de destruction physique de son smartphone ?
- **Contexte** : Promesse *Zero-Cloud at Runtime*. Aucun serveur central ne stocke les dossiers des utilisateurs.
- **Options en balance** :
  - **Option A (Responsabilité 100 % utilisateur via supports amovibles physiques)** :
    - *Comportement* : L'application invite périodiquement l'utilisateur à exporter son dossier sur une clé USB et à imprimer un Pocket Pass papier.
  - **Option B (Synchronisation de proximité chiffrée de pair à pair avec un proche / aidant)** :
    - *Comportement* : Les smartphones des membres d'une même famille synchronisent leurs passeports réciproques en local lors de rencontres physiques via BLE / Wi-Fi local `[PROPOSÉ]`.
- **Statut** : `[À FAIRE PRENDRE PAR KUDORO]`

---

*Fin du document `docs/functional/00-carte.md` — Tranche 1 (Révision 2).*  
*Livré par l'orchestrateur : `orchestrator: Antigravity-Analyse`*
