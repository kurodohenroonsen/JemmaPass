# 🐢 JemmaPass — Dossier d'Architecture Fonctionnelle & Cahier des Charges de Portage iOS (Japon)

> **Document de Référence Orchestrateur Multi-Agents**  
> **Auteur du projet d'origine** : Claude Heyman (*Kurodo Henro Onsen*)  
> **Version** : 3.0.0-PROD · Octobre 2026  
> **Statut** : Document Maître d'Ingénierie & Stratégie Produit  
> **Périmètre** : Spécification Fonctionnelle Complète (18 Piliers IPS, Moteur Clinique, AI Edge, Transfert Hors-Ligne) & Blueprint d'Ingénierie pour le Portage iOS Ciblant le Marché Japonais.

---

## Table des Matières

1. [Vision Stratégique & Contexte Japon](#1-vision-stratégique--contexte-japon)
   - 1.1. La promesse JemmaPass : *Zero-Cloud, 100% On-Device, Cross-Border*
   - 1.2. Le quatuor narratif : Kurodo, Haru, Kamekichi, Jemma
   - 1.3. Pourquoi le portage iOS est vital au Japon (~70% PDM)
   - 1.4. Scénarios critiques au Japon : Catastrophe naturelle (Noto/Nankai) et Pèlerinage (Shikoku)
2. [Cartographie Fonctionnelle Complète de l'Existant](#2-cartographie-fonctionnelle-complète-de-lexistant)
   - 2.1. Les 18 Piliers de l'International Patient Summary (HL7 FHIR R4)
   - 2.2. Dualité des formats : FHIR R4 (Source de Vérité) vs `_j 1.2` (Projection Compacte)
   - 2.3. Moteur Clinique & Sécurité Décisionnelle (`KbCrossCheck` + `KbSafety`)
   - 2.4. Le Moteur de Vulgarisation Thérapeutique (Gemma 4 + `VulgariseHelper`)
   - 2.5. Les 16 Outils Typés `@Tool` de Jemma
   - 2.6. Canaux de Transfert Multi-Supports Hors-Ligne (Triple-Layer QR, Mesh P2P, Pocket Pass PDF, Lock-screen SOS)
3. [Spécificités Médicales, Réglementaires et Linguistiques Japonaises](#3-spécificités-médicales-réglementaires-et-linguistiques-japonaises)
   - 3.1. L'écosystème de santé japonais : MHLW (厚生労働省), PMDA et DMAT
   - 3.2. Pharmacopée & Nomenclatures : Codes HOT/YJ, DCI vs Noms Commerciaux en Katakana (ex: Loxonin, Rixiana)
   - 3.3. Carnet de santé numérique (*Okusuri Techou* - お薬手帳) et Carte My Number
   - 3.4. Tokenisation FTS5 CJK (Trigramme) vs Analyseurs Morphologiques (MeCab)
   - 3.5. Tri de Catastrophe : Protocole SALT adapté aux fiches japonaises (*Triage Tag*)
4. [Cahier des Charges Architectural du Portage iOS](#4-cahier-des-charges-architectural-du-portage-ios)
   - 4.1. Stratégie d'implémentation : KMP (Kotlin Multiplatform) vs 100% Swift Natif
   - 4.2. Matrice d'Équivalence Technologique Complète (Android ➔ iOS)
   - 4.3. Base de Données Clinique (`knowledge_full.db`) & Moteur FTS5 sous iOS
   - 4.4. Intelligence Artificielle Embarquée : Inférence LLM & Vision OCR sur Apple Silicon
   - 4.5. Le Défi du Réseau Maillé P2P (Nearby Connections vs CoreBluetooth / Multipeer)
   - 4.6. Expérience Utilisateur & Intégration Écosystème Apple (Dynamic Island, Live Activities, Wallet, HealthKit)
5. [Orchestration Multi-Agents & Feuille de Route d'Exécution](#5-orchestration-multi-agents--feuille-de-route-dexécution)
   - 5.1. Rôles et responsabilités des Sub-Agents spécialisés
   - 5.2. Protocole de communication inter-agents
   - 5.3. Plan par jalons (Milestones M1 à M5)
   - 5.4. Matrice d'assurance qualité & cas d'usage critiques (577 micro-cas)

---

## 1. Vision Stratégique & Contexte Japon

### 1.1. La promesse JemmaPass : *Zero-Cloud, 100% On-Device, Cross-Border*

JemmaPass résout une faille majeure de la santé numérique mondiale : **la dépendance aux infrastructures cloud en situation d'urgence ou de rupture de connectivité transfrontalière**.

Lorsqu'un voyageur s'effondre à l'étranger, ou qu'un séisme majeur détruit les réseaux de télécommunications, les dossiers médicaux électroniques (DME) hospitaliers deviennent inaccessibles. De plus, la fiche d'urgence native des smartphones (*Medical ID*) est le plus souvent verrouillée dans la langue locale du propriétaire et dépourvue de sémantique clinique exploitable.

JemmaPass transforme un smartphone grand public en un **terminal autonome de passeport médical d'urgence**, garantissant :
- **100% Hors-Ligne (Zero-Cloud)** : Aucune requête réseau, aucune télémétrie, aucune fuite de données privées (HIPAA / RGPD / APPI par conception).
- **Standard International HL7 FHIR R4 IPS** : Conformité stricte avec le profil *International Patient Summary* (ISO 27269).
- **Vérification Pharmacologique Embarquée** : Moteur de détection des interactions médicamenteuses (DDI) et des allergies croisées s'appuyant sur une base SQLite de 2,2 Go et 1,4 million de concepts.
- **Assistance IA Explicable** : Modèle de langage multimodal (Gemma 4) traduisant et vulgarisant instantanément les alertes en 25 langues.

### 1.2. Le quatuor narratif : Kurodo, Haru, Kamekichi, Jemma

Le système a été conçu autour de 4 personas emblématiques incarnant la réalité du terrain :

| Personnage | Statut | Rôle clinique & Scénario |
| :--- | :--- | :--- |
| 🚶‍♂️ **Kurodo** | Pèlerin étranger (Belge) | Marche sur le sentier des 88 temples de Shikoku. Ne parle pas japonais. Allergie létale à la **pénicilline** et aux poissons. En cas de malaise, un soignant local pourrait lui administrer de l'Augmentin (amoxicilline). |
| 👵 **Haru** | Citoyenne japonaise (80 ans) | Vit seule à Shikoku ou Aomori. Insuffisance cardiaque, sous anticoagulant direct (**Edoxaban** / リクシアナ). Ne lit pas l'anglais. Risque d'interaction hémorragique avec l'aspirine ou les AINS. |
| 🎒 **Kamekichi** | Secouriste bénévole / DMAT | Volontaire de premier secours, non-médecin. Équipé de son sac à dos d'urgence. Doit scanner des plaquettes de médicaments, obtenir un verdict de sécurité instantané et trier les victimes sans réseau. |
| ✨ **Jemma** | IA embarquée (Gemma 4) | L'esprit-tortue gardienne vivant exclusivement dans la puce du téléphone. Elle interroge la base locale via 16 outils typés et vulgarise les alertes dans la langue native du soignant et du soigné. |

### 1.3. Pourquoi le portage iOS est vital au Japon (~70% PDM)

Au Japon, la structure du marché mobile est unique parmi les pays développés :
- **Part de marché iOS** : Entre **65% et 70%** de façon constante depuis 10 ans. Les cohortes jeunes, actives, ainsi que le personnel soignant et les secouristes utilisent massivement l'iPhone.
- **Adoption chez les seniors** : Bien que certains utilisent la gamme Android "Raku-Raku Phone", une immense proportion de personnes âgées (ou leurs aidants familiaux) sont équipées d'iPhones configurés par leurs enfants.
- **Tourisme et Pèlerinage** : Les voyageurs internationaux entrants (Occidentaux, Taïwanais, Sud-Coréens) disposent majoritairement d'iPhones.
- **Constat d'échec d'une solution Android exclusive** : Un outil d'urgence déployé uniquement sur Android au Japon laisse de côté **7 secouristes sur 10** et **7 victimes sur 10**. Le portage iOS n'est pas une simple déclinaison commerciale : c'est la condition sine qua non de la pertinence opérationnelle de JemmaPass en Asie de l'Est.

### 1.4. Scénarios critiques au Japon

#### 1.4.1. Catastrophe Naturelle Majeure (Le précédent de Noto, Janvier 2024 & Séisme du Nankai)
Lors du tremblement de terre de la péninsule de Noto (1er janvier 2024), les routes ont été coupées et les antennes 4G/5G ont cessé d'émettre pendant plusieurs jours. Les équipes DMAT (Disaster Medical Assistance Team) et la Croix-Rouge japonaise ont dû prodiguer des soins sans accès aux serveurs d'assurance maladie en ligne (*On-line Shikaku Kakunin*).
- **Rôle de JemmaPass** : Communication maillée P2P (BLE/Mesh) entre tentes de secours, tri SALT hors-ligne, transmission de l'historique médical par QR code papier (*Pocket Pass*) ou écran à écran, sans aucun relais cellulaire.

#### 1.4.2. Le Pèlerinage de Shikoku (Henro - 88 Temples) & Voyageurs Étrangers
Un marcheur étranger déshydraté ou blessé s'évanouit près d'un temple rural dans les montagnes de Tokushima. L'équipe d'ambulanciers locale ne parle pas anglais ni français :
- **Rôle de JemmaPass** : L'ambulancier scanne avec son iPhone le QR code de Kurodo. Même sans l'application installée, la caméra native d'iOS décode le **Canal 2 (Texte brut traduit en japonais)** affichant en kanji/katakana clairs : `⚠️ アレルギー: ペニシリン (重篤)`.
- Si l'ambulancier a JemmaPass iOS, il bénéficie de l'alerte rouge en 200 ms et de la vulgarisation vocale en japonais naturel.

---

## 2. Cartographie Fonctionnelle Complète de l'Existant

### 2.1. Les 18 Piliers de l'International Patient Summary (HL7 FHIR R4)

L'architecture s'aligne rigoureusement sur le guide d'implémentation **HL7 FHIR R4 IPS** (ISO 27269). Le système déploie **11 piliers actifs** et prépare l'intégration des 7 piliers complémentaires.

```
┌────────────────────────────────────────────────────────────────────────┐
│                      JEMMAPASS — 18 PILIERS IPS                        │
├───────────────────────────────────┬────────────────────────────────────┤
│ 11 PILIERS ACTIFS                 │ 7 PILIERS STUBS (PHASE 2)          │
├───────────────────────────────────┼────────────────────────────────────┤
│ 👤 Patient (Demographics/Contact)  │ ⚖️ Advance Directives (11453-8)    │
│ ⚠️ Allergies & Intolérances       │ 🤝 Consents & Authorizations       │
│ 💊 Médications / Traitements      │ 🎯 Care Goals (11383-7)            │
│ 🩺 Liste des Problèmes Actifs     │ 🏥 Encounters / Hospitalisations   │
│ 📜 Antécédents Médicaux (Passés)  │ 💼 Occupational Data               │
│ 💉 Vaccinations / Immunisations   │ 👨‍⚕️ Healthcare Providers           │
│ 🏥 Actes & Procédures Chirurgicales│ 📞 Extended Care Contacts          │
│ 📟 Dispositifs Médicaux & Implants │                                    │
│ 🧪 Résultats Biologiques & Labo   │                                    │
│ 🤰 Grossesse & Obstétrique        │                                    │
│ ♿ Statut Fonctionnel & Handicap  │                                    │
└───────────────────────────────────┴────────────────────────────────────┘
```

#### Détail des 11 Piliers Actifs :

1. **Patient 👤 (`JPatient`)** : Identité, sexe administratif, date de naissance (support des dates partielles : année seule ou année-mois pour réfugiés/seniors), nationalité, groupe sanguin, adresses structurées, téléphones, identifiants multiples (passeport, MyNumber, NSS, NRN).
2. **Allergies & Intolérances ⚠️ (`JAllergy` / `AllergyIntolerance`)** : Substance (code SNOMED/RxNorm), criticité (`H` high, `L` low, `U` unknown), statut clinique (actif, inactif, résolu), type (allergie vs intolérance), catégorie (médicament, aliment, environnement, biologique), liste de réactions documentées avec sévérité.
3. **Médications 💊 (`JMedication` / `MedicationStatement`)** : Code DCI/ATC/RxNorm, nom de marque dénormalisé, posologie, fréquence, voie d'administration (O oral, I injection, T topique, S sous-cutané, H inhalé - SNOMED 447694001), statut du traitement, dates de prise.
4. **Problèmes Actifs 🩺 (`IpsProblem` / `Condition` section 11450-4)** : Diagnostics en cours, sévérité LOINC (mild, moderate, severe), statut actif/récurrent/rechute.
5. **Antécédents Passés 📜 (`IpsPastProblem` / `Condition` section 11348-0)** : Maladies résolues ou en rémission, date de début et d'abattement (*abatement date*).
6. **Vaccinations 💉 (`IpsImmunization` / `Immunization` section 11369-6)** : Code vaccin SNOMED/CVX, date de vaccination, rang de dose (`dn`), statut (*completed*, *not-done*).
7. **Procédures & Chirurgie 🏥 (`IpsProcedure` / `Procedure` section 47519-4)** : Interventions passées (ex: pontage aortocoronarien, appendicectomie), date, site anatomique, statut.
8. **Dispositifs Médicaux & Implants 📟 (`IpsDevice` / `Device` + `DeviceUseStatement` section 46264-8)** : Pacemaker, prothèses, implants auditifs, pompe à insuline. Numéro d'identifiant unique (UDI GS1), fabricant, modèle, date d'implantation.
9. **Résultats de Biologie & Imagerie 🧪 (`IpsResult` / `Observation` section 30954-2)** : Analyses sanguines (HbA1c, DFG/créatinine, potassium, cholestérol), imagerie (radio pulmonaire). Support des valeurs numériques avec unités UCUM (`system="http://unitsofmeasure.org"`), interprétation v3 (H, L, N) et plages de référence. Cas particulier inviolable : **Groupe sanguin (LOINC 882-1)** synchronisé sur le profil.
10. **Grossesse & Maternité 🤰 (`IpsPregnancy` / `Observation` section 10162-6)** : Statut de grossesse (LOINC 82810-3), date présumée d'accouchement (DPA / EDD), antécédents obstétricaux (gestité, parité, fausses couches).
11. **Statut Fonctionnel & Aides Techniques ♿ (`IpsFunctional` / `Condition` section 47420-5)** : Limitations d'autonomie (cécité, surdité, marche avec canne, dépendance ventilatoire). Totalement disjoint de la liste des maladies actives pour éviter tout faux positif d'interaction médicamenteuse.

### 2.2. Dualité des formats : FHIR R4 (Source de Vérité) vs `_j 1.2` (Projection Compacte)

Une innovation architecturale fondamentale de JemmaPass (adoptée lors du refactoring septembre 2026) est la **séparation nette entre la source de vérité et le format de transmission** :

```
             ┌────────────────────────────────────────────────────────┐
             │       STOCKAGE LOCAL SUR L'APPAREIL (PERSISTANCE)       │
             └───────────────────────────┬────────────────────────────┘
                                         │
             ┌───────────────────────────┴────────────────────────────┐
             ▼                                                        ▼
┌─────────────────────────┐                              ┌─────────────────────────┐
│     <sid>.fhir.json     │                              │        <sid>.json       │
│  FHIR R4 Bundle (JSON)  │                              │      JemmaProfileJ      │
│   SOURCE DE VÉRITÉ      │                              │   PROJECTION COMPACTE   │
│                         │                              │                         │
│ Contient l'exhaustivité │      Projeté par             │ Modèle ultra-allégé     │
│ sémantique HL7 :        │   ProfilesRepository         │ codes courts (c, d, md) │
│ - Dates précises/heures │ ───────────────────────────> │ Utilisé pour :          │
│ - Statuts FHIR complets │                              │ - QR Code _j2 (1.8 KB)  │
│ - Identifiants UDI GS1  │                              │ - Chunks BLE Mesh       │
│ - Unités UCUM           │                              │ - Inférence IA légère   │
└─────────────────────────┘                              └─────────────────────────┘
```

- **Garantie d'intégrité** : Écritures atomiques via fichier temporaire et renommage (`writeAtomic`). Verrou réentrant sur le cycle Lecture-Modification-Écriture pour éliminer tout risque de concurrence.

### 2.3. Moteur Clinique & Sécurité Décisionnelle (`KbCrossCheck` + `KbSafety`)

Le moteur de règles fonctionne à 100% sur la base SQLite locale `knowledge_full.db` (2,2 Go) sans aucun service distant.

#### 2.3.1. Les 4 Axes de Contrôle Croisé
1. **DDI (Drug-Drug Interactions)** :
   - Table `ddi_facts` (260 100 interactions issues de DDInter 2.0).
   - Filtrage pré-calculé via `v_ddi_emergency` (niveaux *Major* et *Moderate*).
   - Gestion fine des multi-ATC : Si un médicament possède plusieurs codes ATC (ex: ibuprofène : G02CC01 et M01AE01), le moteur interroge l'union des codes pour ne rater aucune interaction.
   - Sélection déterministe de la sévérité maximale au sein d'une même paire.
2. **Allergie × Médicament** :
   - Détection hiérarchique par classe ATC (L3/L4) : Une allergie déclarée à la pénicilline active automatiquement la classe `J01C`. L'Augmentin (`J01CR02`) est immédiatement capté.
   - Table de réactivité croisée (`allergy_cross_reactivity`) : Couvre les risques croisés entre pénicillines et céphalosporines de première génération (`J01D`).
   - Filtrage sémantique rigoureux : Exclusion des faux positifs lexicaux (ex: interdire qu'une allergie alimentaire aux *grains* déclenche une alerte *AINS*, ou que la *nystatine* déclenche une alerte sur les *statines*).
3. **Médicament × Pathologie (Drug-Disease Contraindications)** :
   - Table `drug_disease_interactions` (8 121 règles).
   - Exemple : Anticoagulants (Edoxaban) en présence d'ulcère gastrique ou d'insuffisance rénale terminale.
4. **Intégrité du Groupe Sanguin** :
   - Le groupe sanguin déclaré (`p.bt`) génère obligatoirement une Observation LOINC 882-1 correspondante.
   - Tout import ou saisie contradictoire est intercepté et rejeté.

#### 2.3.2. Le Système de Verdict Quadrivalent Inviolable (`KbSafetyVerdict`)
Pour éradiquer le risque létal où une erreur technique (base de données absente, nom mal orthographié) afficherait par défaut un faux "Rien à signaler", le système impose une machine à états stricte :

```
                  ┌──────────────────────────────────────────────┐
                  │          RÉSULTAT DU CONTRÔLE CLINIQ.        │
                  └──────────────────────┬───────────────────────┘
                                         │
         ┌───────────────────────────────┴───────────────────────────────┐
         ▼                                                               ▼
   [ COLLISION ? ]                                                [ AUCUN HIT ]
         │                                                               │
   OUI ──┴──> 🔴 ALERT                                    ┌──────────────┴──────────────┐
              (Danger vital détecté)                      ▼                             ▼
              Écran rouge instantané              [ CHECK COMPLET ? ]         [ PROBLÈME RÉSOLUTION ]
              Bouton "Vulgariser"                         │                             │
                                                    OUI ──┴──> 🟢 CLEAN           NON ──┴──> 🟠 INCOMPLETE
                                                               (Vérifié 100%)                ou ⚫ NOT_CHECKED
                                                               Seul cas autorisé             Avertissement
                                                               pour "Rien à signaler"        Bandeau ambre
```

- **`ALERT`** : Au moins une interaction ou allergie majeure trouvée.
- **`CLEAN`** : Vérifié intégralement contre la base, aucun conflit trouvé. **Seul et unique statut autorisant la mention "Rien à signaler"**.
- **`INCOMPLETE`** : Le contrôle a tourné mais une partie du profil n'a pas pu être vérifiée (médicament sans code ATC, format non reconnu).
- **`NOT_CHECKED`** : Base de données non prête, absente, ou substance candidate totalement inconnue. L'application interdit formellement de prétendre que le patient est en sécurité.

### 2.4. Le Moteur de Vulgarisation Thérapeutique (Gemma 4 + `VulgariseHelper`)

JemmaPass ne se contente pas d'émettre des codes d'erreur cliniques : il éduque le patient et guide le sauveteur grâce à l'IA générative on-device.

1. **Vulgarisation d'Alerte (DDI / Allergie)** :
   - Structure pédagogique quadripartite imposée par prompt :
     1. Rôle individuel de chaque molécule avec des mots simples.
     2. Mécanisme pharmacologique de l'interaction (pourquoi elles s'affrontent).
     3. Manifestations concrètes ressenties dans le corps (ex: risque de saignement, étourdissement).
     4. Phrase bienveillante rappelant de consulter un professionnel de santé.
2. **Règle Absolue de Langue Native** :
   - Gemma 4 reçoit l'instruction stricte (répétée en début, milieu et fin de prompt) de générer **100% de sa réponse dans la langue configurée sur l'appareil**.
   - Si un sauveteur japonais scanne un touriste français, l'explication s'affiche en japonais naturel sans aucun anglicisme.
3. **Persistance en Cache Local (`VulgariseRepository`)** :
   - Les explications générées sont enregistrées dans un cache JSON local (`vulgarise_cache.json`) indexé par `(cacheKey, langue)`.
   - **Bénéfice** : Dès qu'une interaction a été vulgarisée une fois, tout secouriste ultérieur la consulte en **0 milliseconde**, sans solliciter le NPU et sans consommer de batterie.
4. **Protection ANR & Lissage de Rendu (`ThrottledTextAppender`)** :
   - L'inférence générant 20 à 30 tokens/seconde, l'injection token par token dans l'UI provoque des saccades graphiques. Un tampon de 100 ms regroupe les émissions pour préserver un défilement à 60/120 fps.

### 2.5. Les 16 Outils Typés `@Tool` de Jemma

Gemma 4 interagit avec le système via 16 fonctions natives fournies par LiteRT-LM :

```
┌────────────────────────────────────────────────────────────────────────┐
│                   LES 16 OUTILS TYPÉS GEMMA 4                          │
├────────────────────┬───────────────────────────────────────────────────┤
│ FAMILLE            │ FONCTIONS (@Tool)                                 │
├────────────────────┼───────────────────────────────────────────────────┤
│ 🔍 Recherche KB    │ 1. resolveDrug(name) ➔ ATC, RxNorm, display        │
│                    │ 2. resolveAllergy(name) ➔ SNOMED, catégorie       │
│                    │ 3. resolveByCode(code, system) ➔ concept          │
│                    │ 4. searchCodes(query, category) ➔ FTS5 matches    │
├────────────────────┼───────────────────────────────────────────────────┤
│ 💊 Interactions    │ 5. checkDdi(drug1, drug2)                         │
│                    │ 6. checkDdiByAtc(atc1, atc2)                      │
│                    │ 7. getAtcAncestors(atcCode)                       │
├────────────────────┼───────────────────────────────────────────────────┤
│ 👤 Profil Focus    │ 8. getFocusProfileSummary()                       │
│    (Victime active)│ 9. getFocusProfileAllergies()                     │
│                    │ 10. getFocusProfileMedications()                  │
│                    │ 11. getFocusProfileConditions()                   │
├────────────────────┼───────────────────────────────────────────────────┤
│ 🎯 Contrôle Maître │ 12. checkOneDrugAgainstFocusProfile(drugName)     │
│                    │ 13. checkOneAtcAgainstFocusProfile(atcCode)       │
├────────────────────┼───────────────────────────────────────────────────┤
│ 🖥️ Interface UI    │ 14. triggerRedAlert(reason, severity)             │
│                    │ 15. triggerToast(message)                         │
├────────────────────┼───────────────────────────────────────────────────┤
│ ⏰ Utilitaire      │ 16. getCurrentDateTime()                          │
└────────────────────┴───────────────────────────────────────────────────┘
```

### 2.6. Canaux de Transfert Multi-Supports Hors-Ligne

JemmaPass intègre 4 canaux complémentaires pour transférer les données vitales en l'absence totale d'Internet :

```
┌────────────────────────────────────────────────────────────────────────┐
│               CANAUX DE TRANSFERT HORS-LIGNE JEMMAPASS                 │
├────────────────────────────────────────────────────────────────────────┤
│ 1. CANAL QR COMPACT (_j2)                                              │
│    Format : _j2:<base64(deflate-raw(json))>  ·  Taille : 1.5 à 3 Ko    │
│    Portée : Écran à écran vers un autre terminal JemmaPass             │
├────────────────────────────────────────────────────────────────────────┤
│ 2. CANAL QR TEXTE UNIVERSEL (25 Langues)                               │
│    Format : Texte formaté clair  ·  Budget strict : ≤ 1800 octets UTF-8│
│    Portée : Lisible par N'IMPORTE QUEL iPhone/Android (App Caméra pure)│
├────────────────────────────────────────────────────────────────────────┤
│ 3. CANAL QR FHIR SLIDESHOW (Multi-Trames JF:i/N)                       │
│    Format : Bundle FHIR R4 complet découpé en 3 à 8 QR animés          │
│    Portée : Transmission intégrale vers les dossiers hospitaliers/EHR  │
├────────────────────────────────────────────────────────────────────────┤
│ 4. RÉSEAU MAILLÉ P2P BLE / NEARBY (SALT Triage)                        │
│    Format : Trames 131 octets UTF-8 (V, VR, S, SR, E, F)               │
│    Portée : Balise de détresse portée 50-100m, retransmission multi-hop│
├────────────────────────────────────────────────────────────────────────┤
│ 5. POCKET PASS (PDF Haute Résolution) & WIDGET ÉCRAN DE VERROUILLAGE   │
│    Passeport papier pliable format portefeuille + SOS en double appui  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Spécificités Médicales, Réglementaires et Linguistiques Japonaises

### 3.1. L'écosystème de santé japonais : MHLW, PMDA et DMAT

Pour réussir son intégration et son adoption au Japon, JemmaPass doit adopter les standards de l'écosystème de santé nippon :
- **MHLW (Ministère de la Santé, du Travail et des Affaires Sociales - 厚生労働省, *Kōsei-rōdō-shō*)** : Pilote la numérisation de la santé (*Medical DX*) et impose l'adoption progressive de FHIR sous le profil national **JP Core (NeXEHRS / HL7 Japan)**.
- **PMDA (Agence des Dispositifs Médicaux et Pharmaceutiques - 医薬品医療機器総合機構)** : Organisme de régulation pharmacologique équivalent de la FDA/EMA. Fournit les notices officielles des médicaments (*Tenpum bunsho* - 添付文書).
- **DMAT (Disaster Medical Assistance Team - 日本DMAT)** : Corps médical de secours d'urgence déployé lors des séismes, inondations et éruptions volcaniques. Ce sont les premiers utilisateurs cibles du mode Secouriste/Radar de JemmaPass.

### 3.2. Pharmacopée & Nomenclatures : Noms Commerciaux en Katakana

Au Japon, les prescriptions médicales et les carnets de santé utilisent quasi-exclusivement des noms commerciaux ou génériques transcrits en **Katakana**, et non les dénominations DCI latines occidentales :

| Molécule (DCI / Western) | Nom Commercial au Japon | Katakana | Spécificité / Risque au Japon |
| :--- | :--- | :--- | :--- |
| **Loxoprofen sodium** | Loxonin | ロキソニン | **AINS n°1 absolu au Japon**, omniprésent en vente libre et sur ordonnance. Inconnu dans la pharmacopée standard US/EU. Doit impérativement être résolu vers la classe ATC `M01AE` ! |
| **Edoxaban** | Lixiana | リクシアナ | Anticoagulant oral direct (AOD) extrêmement fréquent chez les personnes âgées au Japon (cf. Haru). |
| **Amoxicillin / Clavulanate** | Augmentin / Clavamox | オーグメンチン / クラバモックス | Pénicilline combinée (cf. scénario Kurodo). |
| **Warfarin** | Warfarin | ワーファリン | Anticoagulant historique à marge thérapeutique étroite. |
| **Acetaminophen** | Calonal | カロナール | Antipyrétique/antalgique pédiatrique et gériatrique de référence. |

- **Codes d'identification japonais** :
  - **Code HOT (9 ou 13 chiffres)** : *Health care Order Terminology*, géré par le MEDIS-DC (Centre de développement des systèmes d'information médicale).
  - **Code YJ (12 caractères alphanumériques)** : Code individuel de médicament figurant sur le tarif officiel des médicaments remboursables.
- **Exigence JemmaPass** : La base SQLite `knowledge_full.db` et le module OCR doivent impérativement mapper les termes Katakana et les codes HOT vers les identifiants internationaux ATC / RxNorm / SNOMED CT.

### 3.3. Carnet de santé numérique (*Okusuri Techou* - お薬手帳) et Carte My Number

1. **L'Okusuri Techou (お薬手帳)** : 
   Chaque citoyen japonais possède un carnet de santé médicamenteux (papier ou app mobile) dans lequel les pharmaciens collent les vignettes codes-barres/QR codes de chaque délivrance. JemmaPass doit être capable de scanner ces vignettes (code JAHIS) pour ingérer instantanément le traitement d'un patient japonais.
2. **Carte My Number (マイナンバーカード)** :
   La carte d'identité numérique japonaise fait désormais office de carte d'assurance maladie (*Myna Insurance Card*). Le format de données patient de JemmaPass intègre déjà le système d'identifiant `JP-MyNumber` dans sa classe `JIdentifier`.

### 3.4. Tokenisation FTS5 CJK (Trigramme) vs Analyseurs Morphologiques

La langue japonaise ne comporte pas d'espaces entre les mots. Une recherche plein texte classique (index `unicode61`) est incapable d'indexer du texte japonais :
- **Solution Android actuelle** : Utilisation de la table virtuelle `terminology_cjk` configurée avec le tokeniseur `trigram` (`tokenize='trigram case_sensitive 0'`). Le trigramme décompose les chaînes en blocs de 3 caractères (ex: `オーグメンチン` ➔ `オーグ`, `ーグメ`, `グメン`...), permettant une recherche floue performante sans embarquer de dictionnaire morphologique lourd.
- **Portage iOS** : iOS SQLite supporte nativement le tokeniseur `trigram` sous SQLite 3.34+. Pour une précision clinique supérieure dans l'analyse de texte libre sur iOS, l'API native Apple `NLTokenizer` (Natural Language framework) peut être utilisée en amont pour segmenter le japonais en lemmes médicaux.

### 3.5. Tri de Catastrophe : Protocole SALT adapté aux fiches japonaises

Lors d'un séisme au Japon, les secouristes DMAT apposent sur chaque victime une étiquette physique standardisée appelée **Triage Tag (トリアージタッグ)**, comportant 4 coupons détachables correspondant aux 4 couleurs de tri.

JemmaPass implémente le protocole international **SALT** (Sort, Assess, Lifesaving, Treatment), directement aligné sur le système japonais :

| Code SALT | Catégorie Japonaise | Couleur | Définition clinique |
| :--- | :--- | :--- | :--- |
| `WAIT` | 緑 (Green) · 軽症 (Catégorie III) | 🟢 Vert | Blessés légers ambulatoires, surveillance différée. |
| `EVAL` | — | 🟡 Jaune | En cours d'évaluation par le secouriste. |
| `STAB` | 黄 (Yellow) · 中等症 (Catégorie II) | 🟡 Jaune | Blessés stables mais non ambulatoires, transport différé possible. |
| `HELP` | 赤 (Red) · 重症 (Catégorie I) | 🔴 Rouge | Détresse vitale immédiate, soins d'urgence requis. |
| `EVAC` | 赤 (Red) · 最優先搬送 | 🔴 Rouge | Évacuation prioritaire immédiate vers hôpital de référence. |
| `DCD` | 黒 (Black) · 不処置 (Catégorie 0) | ⚫ Noir | Décédé ou blessures non viables (propageable avec TTL=3 pour avertir les renforts). |

---

## 4. Cahier des Charges Architectural du Portage iOS

### 4.1. Stratégie d'implémentation : KMP vs 100% Swift Natif

L'analyse de faisabilité met en concurrence deux options stratégiques pour le portage iOS :

```
┌────────────────────────────────────────────────────────────────────────┐
│                   COMPARAISON DES STRATÉGIES PORTAGE                   │
├──────────────────────────┬─────────────────────────────────────────────┤
│ CRITÈRE                  │ OPTION A : KMP (KOTLIN MULTIPLATFORM)       │
├──────────────────────────┼─────────────────────────────────────────────┤
│ Réutilisation du Code    │ ⭐⭐⭐⭐⭐ ~75% du code métier partagé       │
│ Maintenance à long terme │ ⭐⭐⭐⭐⭐ Évolution synchrone Android/iOS   │
│ Accès Matériel iOS       │ ⭐⭐⭐ Nécessite des bridges Kotlin/Native │
│ Adoption Écosystème      │ ⭐⭐⭐ Framework binaire lourd dans Xcode   │
├──────────────────────────┼─────────────────────────────────────────────┤
│ CRITÈRE                  │ OPTION B : 100% SWIFT 6 / SWIFTUI NATIF     │
├──────────────────────────┼─────────────────────────────────────────────┤
│ Réutilisation du Code    │ ⭐ Réécriture complète des codecs/règles    │
│ Performance & Mémoire    │ ⭐⭐⭐⭐⭐ Optimisation Metal/ANE maximale    │
│ Intégration OS (Widgets) │ ⭐⭐⭐⭐⭐ SwiftUI, ActivityKit, App Intents │
│ Recrutement iOS Japon    │ ⭐⭐⭐⭐⭐ Pool de développeurs Swift vaste │
└──────────────────────────┴─────────────────────────────────────────────┘
```

#### Recommandation de l'Orchestrateur : Architecture Hybride Modulaire
1. **Module Partagé KMP (`JemmaCore.xcframework`)** :
   - Modèles de données purs (`JemmaProfileJ`, types IPS).
   - Codec QR `_j2` (compression RFC 1951 deflate-raw).
   - Règles d'encodage Mesh (131 octets UTF-8, chunks V/VR/S/SR/E/F).
   - Algorithmes de tri et de priorité d'éviction du QR texte.
2. **Couche Native Swift 6 / SwiftUI (Application iOS)** :
   - Interface utilisateur 100% SwiftUI (Human Interface Guidelines d'Apple).
   - Moteur SQLite via **GRDB.swift** (accès rapide et typé à `knowledge_full.db`).
   - Inférence IA via **Apple Core ML / llama.cpp Metal** ou **Google LiteRT iOS**.
   - OCR via **Apple Vision Framework** (optimisé pour les kanji/katakana).
   - Intégration système : **WidgetKit**, **ActivityKit (Live Activities / Dynamic Island)**, **Apple HealthKit**, **Apple Wallet**.

### 4.2. Matrice d'Équivalence Technologique Complète (Android ➔ iOS)

| Composant Fonctionnel | Implémentation Android (Actuelle) | Équivalent Recommandé iOS (Cible) |
| :--- | :--- | :--- |
| **Langage & UI** | Kotlin 2.x + ViewBinding / Compose | **Swift 6 + SwiftUI** |
| **Injection de Dépendances** | Hilt (Dagger) + KSP | **Factory / Swift Dependencies (Point-Free)** |
| **Base de Données KB** | requery/sqlite-android (libsqliteX.so FTS5) | **GRDB.swift** avec SQLite FTS5 (unicode61 + trigram) |
| **Inférence LLM (Gemma 4)** | LiteRT-LM (C++ runtime + ToolProvider) | **LiteRT for iOS (C API)** OU **llama.cpp (Metal)** OU **Core ML** |
| **Reconnaissance OCR** | Google ML Kit (Text Japanese + Latin) | **Apple Vision Framework** (`VNRecognizeTextRequest`) |
| **Audio Capture / PCM** | AudioRecord 16 kHz PCM + Header RIFF/WAVE | **AVAudioEngine** + AVAudioConverter 16 kHz Mono |
| **Synthèse Vocale (TTS)** | Android TextToSpeech (25 locales) | **AVSpeechSynthesizer** (Voix japonaises *Kyoko* / *Otoya*) |
| **Génération QR Code** | ZXing Core 3.5.3 (BitMatrix ➔ Bitmap) | **CoreImage** (`CIQRCodeGenerator`) + CoreGraphics |
| **Lecture Caméra / QR** | CameraX + ZXing / ML Kit Barcode | **AVFoundation** (`AVCaptureMetadataOutput` natif 60 fps) |
| **Génération PDF** | Android PdfDocument | **PDFKit** (`UIGraphicsPDFRenderer`) |
| **Réseau P2P / Mesh** | Google Nearby Connections (`P2P_CLUSTER`) | **CoreBluetooth** (GATT Custom) + **MultipeerConnectivity** |
| **Mode Écran Verrouillé** | Foreground Service + LockScreen Overlay | **WidgetKit** (LockScreen) + **ActivityKit** (Live Activities) |
| **Passeport Portefeuille** | Widget écran d'accueil | **Apple Wallet** (`.pkpass` Pass d'urgence avec QR) |
| **Interopérabilité Santé** | Stockage JSON local privé | **Apple HealthKit** (Clinical Records FHIR import/export) |

### 4.3. Base de Données Clinique (`knowledge_full.db`) & Moteur FTS5 sous iOS

- **Taille & Déploiement** : La base de données pèse 2,2 Go. Sur iOS, intégrer un asset de 2,2 Go dans le bundle principal de l'App Store est déconseillé (limite de 4 Go pour le binaire décompressé, temps de téléchargement pénalisant).
  - **Solution** : **On-Demand Resources (ODR)** d'Apple ou téléchargement au premier lancement via un `URLSessionDownloadTask` avec reprise en tâche de fond (*Background Transfer Service*).
- **Moteur SQLite** :
  - La bibliothèque **GRDB.swift** offre la vitesse maximale sur iOS grâce à ses liaisons C directes.
  - La compilation de SQLite avec les flags `-DSQLITE_ENABLE_FTS5` et `-DSQLITE_ENABLE_JSON1` permet de reproduire fidèlement les requêtes `checkDdi`, `resolveDrug` et les vues `v_ddi_emergency`.

### 4.4. Intelligence Artificielle Embarquée sur Apple Silicon

Les puces Apple Silicon de la série A (A16, A17 Pro, A18) disposent d'un **Neural Engine (ANE)** à 16 cœurs ultra-performant et d'une mémoire unifiée :
1. **Contrainte Mémoire RAM sous iOS** :
   - Un modèle Gemma 4 E4B quantifié en 4-bit (`Q4_K_M`) nécessite environ **3,2 Go d'espace mémoire actif**.
   - Sur un iPhone 15/16 standard (6 Go de RAM totale), la limite d'allocation mémoire par application imposée par iOS (*Jetsam memory limit*) se situe autour de 3,5 à 4 Go. Un dépassement provoque un crash immédiat `EXC_RESOURCE RESOURCE_TYPE_MEMORY`.
   - **Stratégie d'ingénierie iOS** :
     - Pour les modèles Pro (iPhone 15 Pro, iPhone 16 / 16 Pro avec 8 Go de RAM unifiée) : Exécution intégrale de Gemma 4 E4B.
     - Pour les modèles standard (6 Go) : Déploiement d'un modèle adapté (Gemma 2 2B quantifié ou Gemma 4 E2B) ou limitation de la fenêtre de contexte à 1024 tokens.
2. **Framework d'Inférence** :
   - **Option 1 (Google Native)** : SDK **LiteRT for iOS** (Google AI Edge). Permet de réutiliser exactement les modèles `.tflite` / LiteRT-LM d'Android.
   - **Option 2 (Performance Apple)** : Conversion Core ML via `coremltools` pour une exécution directe sur le Neural Engine (ANE) avec zéro charge CPU/GPU.

### 4.5. Le Défi du Réseau Maillé P2P (Nearby vs Apple Ecosystème)

C'est le défi d'ingénierie le plus complexe du portage : **Google Nearby Connections n'offre pas d'API d'arrière-plan complète sur iOS et ne permet pas le mode P2P_CLUSTER BLE public de manière transparente avec Android**.

#### Architecture Réseau Maillé Hybride iOS/Android :
Pour que secouristes et victimes puissent communiquer sans réseau quel que soit leur OS :
1. **Couche BLE Universelle (CoreBluetooth)** :
   - Utilisation d'un **Service GATT BLE dédié JemmaPass** (UUID propriétaire 128-bit).
   - Les trames de 131 octets UTF-8 (`MeshByteSafety`) sont injectées soit dans les paquets publicitaires étendus (*Extended Advertising*), soit dans une caractéristique GATT en lecture/diffusion périodique.
   - **Interopérabilité** : Un terminal Android en écoute BLE scanne et décode les chunks émis par l'iPhone, et inversement.
2. **Couche MultipeerConnectivity (Intra-iOS)** :
   - Pour les transferts volumineux (échange complet de dossiers FHIR) entre deux iPhones, le framework natif Apple `MultipeerConnectivity` prend automatiquement le relais via Wi-Fi direct ad-hoc.

### 4.6. Expérience Utilisateur & Intégration Écosystème Apple

L'application iOS offrira une expérience spécifiquement conçue pour l'ergonomie Apple :
1. **Dynamic Island & Live Activities (ActivityKit)** :
   - Lors d'une situation d'urgence ou d'un déclenchement SOS, une *Live Activity* s'affiche sur l'écran de verrouillage et dans la *Dynamic Island*.
   - Affiche en temps réel : Statut d'émission SOS, groupe sanguin, statut SALT, nombre de secouristes à portée.
2. **Lock Screen Widgets (WidgetKit)** :
   - Widget circulaire d'urgence : déclenchement du mode SOS en 1 appui sans déverrouiller l'iPhone.
   - Widget rectangulaire : affichage permanent du QR code d'identité médicale d'urgence.
3. **Apple Wallet Pass (`.pkpass`)** :
   - Génération d'une carte d'urgence officielle dans l'application Apple Wallet.
   - **Bénéfice majeur au Japon** : Accessible par **double-clic sur le bouton latéral de l'iPhone**, même téléphone verrouillé, compatible avec les bornes NFC et les scanners optiques des hôpitaux.
4. **Intégration Apple Health (HealthKit)** :
   - Synchronisation bidirectionnelle avec l'application *Santé* d'Apple : lecture des allergies et traitements enregistrés par l'utilisateur, et export au format international FHIR Clinical Records.

---

## 5. Orchestration Multi-Agents & Feuille de Route d'Exécution

En tant qu'**Orchestrateur Principal de Sub-Agents**, la conduite du projet est segmentée en 5 agents autonomes hautement spécialisés :

### 5.1. Rôles et responsabilités des Sub-Agents spécialisés

```
┌────────────────────────────────────────────────────────────────────────┐
│             STRUCTURE DE L'ÉQUIPE DE SUB-AGENTS JEMMAPASS              │
├────────────────────────────────────────────────────────────────────────┤
│                       ORCHESTRATEUR PRINCIPAL                          │
│               (Coordination, Arbitrage, Intégration)                   │
└──────┬───────────────┬────────────────┬───────────────┬────────────────┘
       │               │                │               │
       ▼               ▼                ▼               ▼
┌─────────────┐ ┌─────────────┐  ┌─────────────┐ ┌─────────────┐
│ SUB-AGENT 1 │ │ SUB-AGENT 2 │  │ SUB-AGENT 3 │ │ SUB-AGENT 4 │
│ CLINIQUE &  │ │ CODECS,     │  │ AI EDGE &   │ │ P2P MESH &  │
│ SANTÉ JAPON │ │ FORMATS QR  │  │ MULTIMODAL  │ │ RESCUE SOS  │
└─────────────┘ └─────────────┘  └─────────────┘ └─────────────┘
       │
       ▼
┌─────────────┐
│ SUB-AGENT 5 │
│ SWIFTUI &   │
│ APPLE ECO.  │
└─────────────┘
```

1. **Sub-Agent 1 : Clinique, Termonilogie & Santé Japon (`@agent-clinical-jp`)**
   - **Mission** : Validation sémantique HL7 FHIR R4 IPS, intégration des tables de pharmacopée japonaise (MHLW/PMDA/HOT/YJ), règles d'interaction Katakana, extension des 7 piliers stubs restants.
2. **Sub-Agent 2 : Moteur Multiplateforme & Codecs (`@agent-core-codecs`)**
   - **Mission** : Développement du module KMP partagé (`JemmaCore`), portage du codec `_j2` (deflate-raw RFC 1951), réassembleur multi-trames `JF:i/N`, algorithmes de découpage QR texte 1800 octets.
3. **Sub-Agent 3 : IA Embarquée & Vision (`@agent-edge-ai`)**
   - **Mission** : Pipeline d'inférence Gemma 4 sur iOS (Core ML / LiteRT iOS), intégration de Vision Framework pour l'OCR japonais, prompt engineering vulgarisation multi-locales, cache local.
4. **Sub-Agent 4 : Réseau Maillé & Protocoles d'Urgence (`@agent-mesh-sos`)**
   - **Mission** : Protocole CoreBluetooth interopérable avec Android Nearby, gestion des 131 octets UTF-8, synchronisation des statuts de tri SALT, algorithmes anti-zombie.
5. **Sub-Agent 5 : Interface SwiftUI & Écosystème Apple (`@agent-apple-ui`)**
   - **Mission** : Conception de l'UI native SwiftUI, intégration Lock Screen Widgets, Dynamic Island (ActivityKit), Apple Wallet Pass (.pkpass) et Apple HealthKit.

### 5.2. Protocole de communication inter-agents

- **Format des échanges** : Spécifications fonctionnelles validées par l'Orchestrateur sous forme de contrats d'interface (Interface Definition Language / Swift Protocols).
- **Règle de non-régression** : Chaque développement d'un sous-agent doit s'adosser à l'un des 577 micro-cas d'usage du catalogue d'assurance qualité (`qa/usecases/`).

### 5.3. Plan par jalons (Milestones M1 à M5)

| Jalon | Intitulé | Livrables Principaux | Échéance |
| :--- | :--- | :--- | :--- |
| **M1** | **Spécification & Module KMP Core** | `JemmaCore.xcframework` opérationnel (Codecs QR `_j2`, modèles IPS, projections, tests unitaires croisés). | S+2 |
| **M2** | **Base Clinique & Moteur Décisionnel iOS** | SQLite FTS5 (GRDB.swift) fonctionnel sous iOS, import de `knowledge_full.db`, requêtes DDI et allergies validées. | S+4 |
| **M3** | **Vision OCR & Pipeline IA On-Device** | Reconnaissance de texte japonais (Vision), inférence Gemma 4 locale, outils `@Tool` portés en Swift. | S+7 |
| **M4** | **Canaux de Transfert & Mesh BLE** | Scanner/Générateur QR triple-layer sous iOS, couche BLE CoreBluetooth interopérable avec Android. | S+9 |
| **M5** | **UI SwiftUI & Intégration Système Apple** | Écrans SwiftUI, Widgets écran de verrouillage, Dynamic Island, Apple Wallet Pass, tests terrain au Japon. | S+12 |

### 5.4. Matrice d'assurance qualité & Cas Critiques (577 micro-cas)

Le portage iOS devra satisfaire 100% des cas d'usage à haut risque documentés dans `qa/usecases/` :
- **UC-SAFE-KB** : Interdiction absolue du statut "Rien à signaler" en cas de base inaccessible ou de médicament non reconnu.
- **UC-DDI** : Résolution exacte des marques japonaises en Katakana (ex: *Loxonin* ➔ M01AE) et détection des interactions majeures avec les anticoagulants (*Edoxaban*).
- **UC-ALM** : Détection des allergies croisées par classe ATC et blocage de l'Augmentin pour les allergiques à la pénicilline.
- **UC-QR-TEXT** : Respect strict du plafond de 1800 octets UTF-8 pour le QR universel, avec éviction prioritaire propre des sections non vitales.
- **UC-BLOOD** : Inviolabilité du groupe sanguin et synchronisation stricte avec l'Observation LOINC 882-1.

---

> **Validation de l'Orchestrateur** :  
> Ce document constitue la feuille de route officielle et exhaustive pour la pérennisation et l'extension internationale de JemmaPass, positionnant la solution comme le standard incontournable du passeport médical d'urgence décentralisé au Japon et dans le monde.
