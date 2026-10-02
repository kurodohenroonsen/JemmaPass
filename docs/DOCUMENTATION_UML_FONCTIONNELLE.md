# 🐢 JemmaPass — Documentation & Spécification UML Fonctionnelle (Conforme au Code Réel)

> **Document Technique d'Architecture & Modèle Métier**  
> **Auteur** : Équipe d'Ingénierie JemmaPass & Sub-Agent Documentation Architecture  
> **Version** : 1.0.14-UML (aligné sur l'application Android v1.0.14, `app/build.gradle.kts:38`)  
> **Statut** : Document de Travail & Spécification d'Architecture Révisée (En cours de révision technique — non approuvé comme norme unique)  
> **Périmètre** : Spécification Fonctionnelle, Modèles de Domaine, Diagrammes d'États et de Séquence vérifiés sur pièces contre le code source de JemmaPass. Toute classe, méthode ou propriété figurant dans les sections 1 à 7 est sourcée avec son chemin et sa ligne exacte (`fichier:ligne`). Les évolutions futures et propositions non encore implémentées sont regroupées dans la [Section 8 (Proposé — n'existe pas encore)](#8-proposé--nexiste-pas-encore-cibles-dévolution--portages).

---

## Sommaire

1. [Principes Directeurs & Invariants Système](#1-principes-directeurs--invariants-système)
2. [Diagrammes des Cas d'Utilisation Métier (Use Cases)](#2-diagrammes-des-cas-dutilisation-métier-use-cases)
   - 2.1. Cartographie Globale des Acteurs et Cas d'Usage
   - 2.2. Package 1 : Gestion du Passeport IPS (Patient & Aidant)
   - 2.3. Package 2 : Prise en Charge d'Urgence & Scan (Secouriste / DMAT)
   - 2.4. Package 3 : Triage en Zone de Catastrophe (SALT Mesh)
   - 2.5. Package 4 : Éducation Thérapeutique & Vulgarisation IA
3. [Modèle du Domaine Fonctionnel (Class Diagrams)](#3-modèle-du-domaine-fonctionnel-class-diagrams)
   - 3.1. Structure des 18 Piliers IPS & Dualité FHIR R4 vs Projection `_j 1.2`
   - 3.2. Moteur Clinique, Pharmacologique & Sécurité Décisionnelle
   - 3.3. Canaux de Transfert Multi-Supports Hors-Ligne
   - 3.4. Pipeline d'Intelligence Artificielle & Outils Embarqués (`@Tool`)
4. [Machines à États-Transitions (State Diagrams)](#4-machines-à-états-transitions-state-diagrams)
   - 4.1. Cycle de Vie & Persistance du Dossier Patient
   - 4.2. Matrice Inviolable du Verdict de Sécurité Clinique (`KbSafetyVerdict`)
   - 4.3. Protocole de Tri de Catastrophe SALT (Asymmetric LWW & Grace Window)
   - 4.4. Cycle d'Assemblage des Trames QR Multi-Frames (`JF:i/N`)
5. [Diagrammes de Séquence des Flux Fonctionnels Clés (Sequence Diagrams)](#5-diagrammes-de-séquence-des-flux-fonctionnels-clés-sequence-diagrams)
   - 5.1. Détection de Collision Létale : Scénario Kurodo (Pénicilline × Augmentin)
   - 5.2. Contrôle Sans Interaction : Scénario Paracétamol chez un Profil Sain (Verdict CLEAN)
   - 5.3. Génération du QR Texte 25 Langues avec Budget d'Éviction Strict (1800 octets UTF-8)
   - 5.4. Découverte, Alerte et Propagation Maillée P2P SALT (Zone Sinistrée)
   - 5.5. Pipeline de Vulgarisation Pédagogique Multilingue et Cache Local
6. [Diagrammes d'Activités & Algorithmes Métier (Activity Diagrams)](#6-diagrammes-dactivités--algorithmes-métier-activity-diagrams)
   - 6.1. Algorithme de Résolution Sémantique d'un Médicament (Normalisation & FTS5)
   - 6.2. Algorithme de Détection d'Allergie Croisée par Arborescence de Classes
   - 6.3. Algorithme d'Éviction Prioritaire du QR Texte Universel
   - 6.4. Algorithme de Réconciliation et d'Inviolabilité du Groupe Sanguin
7. [Spécifications Formelles des Contrats d'Interface Vérifiés](#7-spécifications-formelles-des-contrats-dinterface-vérifiés)
   - 7.1. Contrat Réel du Service de Connaissance Médicale (`KnowledgeBaseService`)
   - 7.2. Contrat Réel du Moteur de Contrôle Croisé (`KbCrossCheck`)
   - 7.3. Contrat Réel des Codecs QR, Trames et Triage
8. [Proposé — N'existe pas encore (Cibles d'Évolution & Portages)](#8-proposé--nexiste-pas-encore-cibles-dévolution--portages)

---

## 1. Principes Directeurs & Invariants Système

Le système JemmaPass repose sur 5 invariants fonctionnels vérifiés dans le code Android existant :

```
┌────────────────────────────────────────────────────────────────────────┐
│                   LES 5 INVARIANTS SYSTÈME JEMMAPASS                   │
├────────────────────────────────────────────────────────────────────────┤
│ 1. ZERO-NETWORK AT RUNTIME                                             │
│    Aucune opération vitale en intervention (scan, contrôle clinique,   │
│    triage SALT, transfert QR/BLE) ne dépend du réseau. Le runtime     │
│    opère 100% hors-ligne. Les modèles (.litertlm) et la base KB       │
│    (knowledge_full.db) sont pré-téléchargés au besoin depuis           │
│    https://jemmapass.net/models/ (downloads/JemmaModelCatalog.kt:33). │
│                                                                        │
│ 2. FHIR R4 AS SOURCE OF TRUTH FOR FHIR-NATIVE PILLARS                  │
│    Le document médical d'autorité est le Bundle HL7 FHIR R4 IPS        │
│    (<sid>.fhir.json) pour les seuls piliers FHIR-natifs (vaccins,      │
│    actes, dispositifs, résultats... - profiles/ProfilesRepository.kt:  │
│    23-27). Les 4 piliers historiques (patient, allergies, médocs,     │
│    conditions) restent issus de la saisie _j 1.2 (<sid>.json).         │
│                                                                        │
│ 3. STRICT SAFETY DECISION MATRIX                                       │
│    Tout conflit détecté donne ALERT (kb/KbCrossCheck.kt:131).          │
│    Le verdict CLEAN ("Rien à signaler") n'est possible que si la KB    │
│    est active, le candidat résolu et qu'aucune collision n'existe.     │
│    Nuance du code : une allergie sans code ATC est évaluée par mots-   │
│    clés et compte comme CHECKED (kb/KbCrossCheck.kt:418).              │
│                                                                        │
│ 4. DETERMINISTIC OFFLINE TRANSFER                                      │
│    Le QR texte universel respecte un plafond strict de 1800 octets     │
│    UTF-8 (qr/JemmaTextPayloadBuilder.kt:67). En cas de dépassement,    │
│    les sections sont évincées par rangs et marquées ✂️ (jamais de coupure│
│    silencieuse). Les trames QR multi-frames sont indexées 1..N         │
│    (qr/JemmaQrFrameAssembler.kt:26).                                   │
│                                                                        │
│ 5. MULTILINGUAL NATIVE EXPERIENCE                                      │
│    Restitution dans la langue locale (25 langues) via les dictionnaires│
│    embarqués (qr/JemmaTranslations.kt:12) et l'agent Gemma 4.          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Diagrammes des Cas d'Utilisation Métier (Use Cases)

### 2.1. Cartographie Globale des Acteurs et Cas d'Usage

Le système interagit avec 4 acteurs :
- **Patient / Porteur du Pass** : Édite son passeport IPS et affiche son QR d'urgence.
- **Aidant / Famille** : Configure le profil d'un proche dépendant.
- **Secouriste / DMAT / Soignant** : Scanne les pass et médicaments, réalise le triage SALT.
- **Agent IA Embarqué (Gemma 4)** : Exécute les 21 outils `@Tool` de `ai/JemmaTools.kt:8-41` et vulgarise les alertes.

```mermaid
flowchart LR
    subgraph Acteurs
        P["👤 Patient / Porteur"]
        A["👨‍👩‍👦 Aidant / Famille"]
        S["🎒 Secouriste / DMAT"]
        J["✨ Agent IA Gemma 4 (LiteRT-LM)"]
    end

    subgraph Cas_d_Usage_Globaux ["Système JemmaPass"]
        UC1["UC-1: Gérer son Passeport IPS (18 Piliers)"]
        UC2["UC-2: Exporter son Pass (QR Triple-Layer / PDF)"]
        UC3["UC-3: Déclencher Alerte SOS / Widget"]
        UC4["UC-4: Scanner & Contrôler un Médicament (DDI/Allergie)"]
        UC5["UC-5: Scanner & Importer un Passeport Tiers"]
        UC6["UC-6: Effectuer le Tri Médical de Catastrophe (SALT Mesh)"]
        UC7["UC-7: Vulgariser & Expliquer une Alerte Clinique"]
    end

    P --> UC1
    P --> UC2
    P --> UC3
    A --> UC1
    A --> UC2
    S --> UC4
    S --> UC5
    S --> UC6
    S --> UC7
    J -.-> UC4
    J -.-> UC7
```

### 2.2. Package 1 : Gestion du Passeport IPS (Patient & Aidant)

Implémenté dans `ui/profile/` et persisté via `profiles/ProfilesRepository.kt:254-375`.

```mermaid
flowchart TD
    subgraph Gestion_Profil ["UC-1: Gestion du Passeport IPS"]
        UC1_1["Saisir l'Identité & Contacts d'Urgence (p.ct)"]
        UC1_2["Déclarer une Allergie / Intolérance (al)"]
        UC1_3["Ajouter un Médicament en Cours (md)"]
        UC1_4["Renseigner Problèmes Actifs (cn) & Antécédents (ph)"]
        UC1_5["Enregistrer Vaccins (im), Actes (pr) & Dispositifs (dv)"]
        UC1_6["Saisir Résultats Biologiques (rs) & Groupe Sanguin"]
        UC1_7["Renseigner Grossesse (pg) & Statut Fonctionnel (fs)"]
        UC1_8["Sauvegarde sous writeMutex (ProfilesRepository.kt:146)"]
    end

    UC1_2 -.->|include| UC1_8
    UC1_3 -.->|include| UC1_8
    UC1_6 -.->|synchronise LOINC 882-1| UC1_8
```

### 2.3. Package 2 : Prise en Charge d'Urgence & Scan (Secouriste / DMAT)

Implémenté dans `ai/medscan/MedScanController.kt:1-40` et `kb/KbCrossCheck.kt:130-135`.

```mermaid
flowchart TD
    subgraph Prise_En_Charge ["Prise en Charge d'Urgence"]
        SCAN["Scanner Médicament (Caméra OCR ML Kit)"]
        IMPORT["Scanner Pass Victime (QR / Mesh)"]
        EVAL["Exécuter Contrôle Croisé Sécurité (KbCrossCheck.kt:652)"]
        VERDICT{"Évaluation Verdict (KbCrossCheck.kt:130)"}
        RED["🔴 ALERT: Au moins une collision (totalHits > 0)"]
        GREEN["🟢 CLEAN: Aucun hit ET vérification complète"]
        AMBER["🟠 INCOMPLETE: Candidat résolu mais profil incomplet"]
        GRAY["⚫ NOT_CHECKED: Base absente ou médicament non reconnu"]
        VULG["Demander Vulgarisation Thérapeutique (Gemma 4)"]
    end

    SCAN --> EVAL
    IMPORT --> EVAL
    EVAL --> VERDICT
    VERDICT -->|totalHits > 0| RED
    VERDICT -->|checked && totalHits == 0| GREEN
    VERDICT -->|checks != CHECKED| AMBER
    VERDICT -->|!candidateResolved| GRAY
    RED --> VULG
    AMBER --> VULG
```

### 2.4. Package 3 : Triage en Zone de Catastrophe (SALT Mesh)

Implémenté dans `triage/SaltCode.kt:34-51` et `triage/StatusResolver.kt:46-99`. Les couleurs réelles du code sont :
- **WAIT** : Gris (`#9E9E9E`, "⏳", `SaltCode.kt:36`)
- **EVAL** : Jaune (`#FFC107`, "🔍", `SaltCode.kt:39`)
- **STAB** : Vert (`#4CAF50`, "✅", `SaltCode.kt:42`)
- **HELP** : Rouge (`#F44336`, "🆘", `SaltCode.kt:45`)
- **EVAC** : Bleu (`#2196F3`, "🚑", `SaltCode.kt:48`)
- **DCD** : Noir (`#000000`, "🕊️", `SaltCode.kt:51`)

```mermaid
flowchart TD
    subgraph Triage_SALT ["Tri Médical en Zone de Sinistre"]
        DISC["Découvrir Victimes via BLE (JemmaSosBleScanner.kt)"]
        RADAR["Afficher le Radar des Victimes (RadarController.kt)"]
        ASSIGN["Affecter Statut SALT (WAIT gris / STAB vert / HELP rouge / EVAC bleu / DCD noir)"]
        RESOLVE["Résoudre Conflits: shouldOverwrite() & apply() (StatusResolver.kt:68, 108)"]
        BROADCAST["Diffuser Paquet BLE (MAX_CHUNK_BYTES = 200, JemmaSosChunkCodec.kt:175)"]
    end

    DISC --> RADAR
    RADAR --> ASSIGN
    ASSIGN --> RESOLVE
    RESOLVE --> BROADCAST
```

---

## 3. Modèle du Domaine Fonctionnel (Class Diagrams)

### 3.1. Structure des 18 Piliers IPS & Dualité FHIR R4 vs Projection `_j 1.2`

Sur Android, la persistance locale maintient deux fichiers par profil (`profiles/ProfilesRepository.kt:23-28`) :
- `<sid>.fhir.json` : Le Bundle FHIR R4 (source de vérité pour les piliers FHIR-natifs, généré via `qr/JemmaFhirBundleBuilder.kt:30` et `ips/IpsFhirCodec.kt:18`).
- `<sid>.json` : La projection compacte `JemmaProfileJ` (`qr/JemmaProfileJ.kt:43-103`).

```mermaid
classDiagram
    class JemmaProfileJ {
        +String j = "1.2"
        +JPatient p
        +List~JAllergy~ al
        +List~JMedication~ md
        +List~JCondition~ cn
        +List~JEntryGeneric~ ph
        +List~JEntryGeneric~ im
        +List~JEntryGeneric~ pr
        +List~JEntryGeneric~ dv
        +List~JEntryGeneric~ fs
        +List~JEntryGeneric~ pg
        +List~JEntryGeneric~ rs
        +List~JEntryGeneric~ ad
        +List~JEntryGeneric~ cs
        +List~JEntryGeneric~ gl
        +List~JEntryGeneric~ en
        +List~JEntryGeneric~ oc
        +List~JEntryGeneric~ pv
        +String sid
    }

    class JPatient {
        +String gn
        +String fn
        +String gs
        +String bd
        +String nat
        +String bt
        +String adr
        +String tel
        +String eml
        +String idn
        +List~JIdentifier~ ids
        +List~JAddress~ adrs
        +List~JTelecom~ tels
        +String gp
        +String lang
        +List~JContact~ ct
    }

    class JContact {
        +String n
        +String r
        +String p
        +String e
        +String adr
    }

    class JAllergy {
        +String c
        +String s
        +String st
        +String d
        +String m
        +String displayLabel
        +String codeSystem
        +String type
        +String category
        +String onset
        +List~JReaction~ reactions
    }

    class JMedication {
        +String c
        +String t
        +String r
        +String v
        +String u
        +String rs
        +String rc
        +String displayLabel
        +String codeSystem
        +String status
        +String effective
        +String effectiveAbsenceReason
    }

    class JCondition {
        +String c
        +String s
        +String st
        +String d
        +String rs
        +String rc
        +String displayLabel
        +String date
        +String codeSystem
    }

    class JEntryGeneric {
        +String c
        +String d
        +String displayLabel
        +String date
        +String codeSystem
    }

    JemmaProfileJ *-- JPatient : p
    JPatient *-- JContact : ct
    JemmaProfileJ *-- JAllergy : al
    JemmaProfileJ *-- JMedication : md
    JemmaProfileJ *-- JCondition : cn
    JemmaProfileJ *-- JEntryGeneric : ph, im, pr, dv, fs, pg, rs, ad, cs, gl, en, oc, pv
```

### 3.2. Moteur Clinique, Pharmacologique & Sécurité Décisionnelle

Modèle strictement aligné sur `kb/KbCrossCheck.kt` et `kb/KbSafety.kt` :

```mermaid
classDiagram
    class KnowledgeBaseService {
        +resolveDrug(name: String?) ResolvedConcept [kb/KnowledgeBaseService.kt:143]
        +queryDDIByAtc(atcA: String?, atcB: String?) DDIResult [kb/KnowledgeBaseService.kt:440]
        +queryDDI(drugA: String?, drugB: String?) DDIResult [kb/KnowledgeBaseService.kt:478]
        +queryDrugDisease(atc: String?, diseaseName: String?) DrugDiseaseResult [kb/KnowledgeBaseService.kt:758]
        +searchCodes(query: String?, lang: String, categoryFilter: String?, maxResults: Int) KbSearchResult [kb/KnowledgeBaseService.kt:833]
    }

    class KbCrossCheck {
        +checkOneDrugAgainstProfile(candidateName, allergies, meds, conditions, lang) CrossCheckResult [kb/KbCrossCheck.kt:652]
        +checkOneAtcAgainstAllergies(allergies, candidateAtc, candidateAllAtcs, candidateDisplay, lang) List~AllergyHit~ [kb/KbCrossCheck.kt:184]
        +checkAllergiesWithStatus(...) PillarCheck~AllergyHit~ [kb/KbCrossCheck.kt:233]
        +checkOneAtcAgainstMedications(meds, candidateAtc, candidateAllAtcs, candidateDisplay, includeMinor, lang) List~DdiHit~ [kb/KbCrossCheck.kt:439]
        +checkMedicationsWithStatus(...) PillarCheck~DdiHit~ [kb/KbCrossCheck.kt:481]
        +checkOneAtcAgainstConditions(conditions, candidateAtc, candidateDisplay, lang) List~DrugDiseaseHit~ [kb/KbCrossCheck.kt:556]
        +checkConditionsWithStatus(...) PillarCheck~DrugDiseaseHit~ [kb/KbCrossCheck.kt:568]
        -inferAtcFromAllergyName(name: String) String? [kb/KbCrossCheck.kt:818]
    }

    class CrossCheckResult {
        +String candidateAtc [kb/KbCrossCheck.kt:108]
        +String candidateDisplay [kb/KbCrossCheck.kt:109]
        +List~AllergyHit~ allergyHits [kb/KbCrossCheck.kt:110]
        +List~DdiHit~ ddiHits [kb/KbCrossCheck.kt:111]
        +List~DrugDiseaseHit~ drugDiseaseHits [kb/KbCrossCheck.kt:112]
        +Long totalDurationMs [kb/KbCrossCheck.kt:113]
        +KbCheckReport checks [kb/KbCrossCheck.kt:115]
        +Boolean candidateResolved [kb/KbCrossCheck.kt:118]
        +Boolean checked [kb/KbCrossCheck.kt:126]
        +KbSafetyVerdict verdict [kb/KbCrossCheck.kt:129]
        +Boolean hasMajor [kb/KbCrossCheck.kt:136]
        +Int totalHits [kb/KbCrossCheck.kt:141]
        +Boolean isClean [kb/KbCrossCheck.kt:145]
    }

    class KbCheckReport {
        +KbCheckStatus allergy [kb/KbSafety.kt:57]
        +KbCheckStatus ddi [kb/KbSafety.kt:58]
        +KbCheckStatus drugDisease [kb/KbSafety.kt:59]
        +Int unverifiedItems [kb/KbSafety.kt:66]
        +KbCheckStatus overall [kb/KbSafety.kt:69]
        +Boolean fullyChecked [kb/KbSafety.kt:72]
        +Boolean kbAvailable [kb/KbSafety.kt:76]
    }

    class KbSafetyVerdict {
        <<enumeration>>
        ALERT [kb/KbSafety.kt:32]
        CLEAN [kb/KbSafety.kt:35]
        INCOMPLETE [kb/KbSafety.kt:38]
        NOT_CHECKED [kb/KbSafety.kt:41]
    }

    class KbCheckStatus {
        <<enumeration>>
        CHECKED [kb/KbSafety.kt:20]
        INCOMPLETE [kb/KbSafety.kt:23]
        KB_UNAVAILABLE [kb/KbSafety.kt:26]
    }

    KbCrossCheck --> KnowledgeBaseService : interroge
    KbCrossCheck --> CrossCheckResult : construit
    CrossCheckResult --> KbSafetyVerdict : calcule
    CrossCheckResult --> KbCheckReport : contient
    KbCheckReport --> KbCheckStatus : agrège
```

### 3.3. Canaux de Transfert Multi-Supports Hors-Ligne

Modèle réel des composants d'exportation et de transmission hors-ligne :

```mermaid
classDiagram
    class JemmaPayloadCodec {
        +String MAGIC_PREFIX = "_j2:" [qr/JemmaPayloadCodec.kt:57]
        +encode(profile: JemmaProfileJ) EncodeResult [qr/JemmaPayloadCodec.kt:189]
        +decode(text: String?) DecodeResult [qr/JemmaPayloadCodec.kt:122]
    }

    class JemmaTextPayloadBuilder {
        +Int MAX_BYTES = 1800 [qr/JemmaTextPayloadBuilder.kt:67]
        +String TRUNCATION_MARK = "✂️ …" [qr/JemmaTextPayloadBuilder.kt:73]
        +build(hydrated: HydratedProfile, lang: Lang, maxBytes: Int) String [qr/JemmaTextPayloadBuilder.kt:152]
    }

    class JemmaQrFrameSplitter {
        +Int QR_MAX_SINGLE = 1800 [qr/JemmaQrFrameSplitter.kt:53]
        +split(payload: String, maxSingle: Int, frameChunk: Int) List~String~ [qr/JemmaQrFrameSplitter.kt:94]
    }

    class JemmaQrFrameAssembler {
        +offer(raw: String?) Result [qr/JemmaQrFrameAssembler.kt:65]
        +missing() List~Int~ [qr/JemmaQrFrameAssembler.kt:53]
        +reset() [qr/JemmaQrFrameAssembler.kt:57]
    }

    class JemmaSosChunkCodec {
        +Int MAX_CHUNK_BYTES = 200 [sos/JemmaSosChunkCodec.kt:175]
    }

    class JemmaNearbyEndpointCodec {
        +Int MAX_ENDPOINT_NAME_LEN = 131 [sos/JemmaNearbyEndpointCodec.kt:51]
    }

    class SaltCode {
        <<enumeration>>
        WAIT = "WAIT" / "#9E9E9E" [triage/SaltCode.kt:36]
        EVAL = "EVAL" / "#FFC107" [triage/SaltCode.kt:39]
        STAB = "STAB" / "#4CAF50" [triage/SaltCode.kt:42]
        HELP = "HELP" / "#F44336" [triage/SaltCode.kt:45]
        EVAC = "EVAC" / "#2196F3" [triage/SaltCode.kt:48]
        DCD  = "DCD"  / "#000000" [triage/SaltCode.kt:51]
        +parse(code: String) SaltCode? [triage/SaltCode.kt:59]
    }

    class StatusResolver {
        +Long DCD_GRACE_SEC = 30L [triage/StatusResolver.kt:56]
        +shouldOverwrite(existing: StatusEvent, incoming: StatusEvent) Boolean [triage/StatusResolver.kt:68]
        +apply(incoming: StatusEvent) StatusEvent [triage/StatusResolver.kt:108]
    }
```

### 3.4. Pipeline d'Intelligence Artificielle & Outils Embarqués (`@Tool`)

L'agent Jemma (Gemma 4 via LiteRT-LM) dispose de **21 outils typés** exposés dans `ai/JemmaTools.kt:128` :

```mermaid
classDiagram
    class JemmaTools {
        +resolveDrug(name) Map [ai/JemmaTools.kt:172]
        +resolveAllergy(name) Map [ai/JemmaTools.kt:238]
        +resolveByCode(code, system) Map [ai/JemmaTools.kt:277]
        +searchCodes(query, category) Map [ai/JemmaTools.kt:302]
        +checkDdi(drug1, drug2) Map [ai/JemmaTools.kt:348]
        +checkDdiByAtc(atc1, atc2) Map [ai/JemmaTools.kt:376]
        +getAtcAncestors(atcCode) Map [ai/JemmaTools.kt:405]
        +getFocusProfileSummary() Map [ai/JemmaTools.kt:431]
        +getFocusProfileAllergies() Map [ai/JemmaTools.kt:452]
        +getFocusProfileMedications() Map [ai/JemmaTools.kt:461]
        +getFocusProfileConditions() Map [ai/JemmaTools.kt:470]
        +getFocusProfileImmunizations() Map [ai/JemmaTools.kt:479]
        +getFocusProfileProcedures() Map [ai/JemmaTools.kt:503]
        +getFocusProfileDevices() Map [ai/JemmaTools.kt:526]
        +getFocusProfileResults() Map [ai/JemmaTools.kt:549]
        +getFocusProfilePastProblems() Map [ai/JemmaTools.kt:577]
        +checkOneDrugAgainstFocusProfile(drugName) Map [ai/JemmaTools.kt:607]
        +checkOneAtcAgainstFocusProfile(atc, display) Map [ai/JemmaTools.kt:625]
        +triggerRedAlert(title, body) Map [ai/JemmaTools.kt:668]
        +triggerToast(message, severity) Map [ai/JemmaTools.kt:679]
        +getCurrentDateTime() Map [ai/JemmaTools.kt:699]
    }

    class VulgariseRepository {
        +get(key: String, lang: String) String? [ai/assistant/VulgariseRepository.kt:66]
        +save(key: String, lang: String, text: String) [ai/assistant/VulgariseRepository.kt:74]
    }

    class ThrottledTextAppender {
        +intervalMs : Long = 150L [ai/assistant/VulgariseHelper.kt:189]
        +append(partial: String) [ai/assistant/VulgariseHelper.kt:216]
        +cancelUI() [ai/assistant/VulgariseHelper.kt:228]
        +end() [ai/assistant/VulgariseHelper.kt:235]
    }
```

---

## 4. Machines à États-Transitions (State Diagrams)

### 4.1. Cycle de Vie & Persistance du Dossier Patient

La persistance dans `profiles/ProfilesRepository.kt:370-375` protège le cycle par un `writeMutex` (`Mutex()`, non-réentrant, ligne 146).

```mermaid
stateDiagram-v2
    [*] --> Idle : Profil Chargé

    Idle --> Modifying : Édition d'un Pilier (IHM)
    Modifying --> AcquiringLock : Clic Enregistrer
    AcquiringLock --> Validating : writeMutex.withLock (ProfilesRepository.kt:371)
    
    state Validating {
        [*] --> BloodGroupSync : Synchronisation Observation LOINC 882-1
        BloodGroupSync --> ValidatePillars : Validation Cohérence FHIR
        ValidatePillars --> [*]
    }

    Validating --> WritingTemp : Validation OK
    Validating --> ReleaseError : Validation Échouée (Conflit Sanguin Saisie)

    state WritingTemp {
        [*] --> ProjectToJ : moshi.toJson(profile) vers fichier .json.tmp
        ProjectToJ --> BuildFhirBundle : JemmaFhirBundleBuilder.build() vers .fhir.json.tmp
        BuildFhirBundle --> AtomicRename : Renommage Atomique (.tmp vers .json)
        AtomicRename --> [*]
    }

    WritingTemp --> ReleaseSuccess : Fichiers Écrits
    WritingTemp --> ReleaseError : Erreur I/O Disque

    ReleaseSuccess --> UpdatingStateFlow : Release writeMutex
    ReleaseError --> Idle : Release writeMutex + Notification Erreur
    UpdatingStateFlow --> Idle : Profil Actif Notifié
```

### 4.2. Matrice Inviolable du Verdict de Sécurité Clinique (`KbSafetyVerdict`)

Implémentée dans `kb/KbCrossCheck.kt:129-135` et `kb/KbSafety.kt:30-42` :

```mermaid
stateDiagram-v2
    [*] --> Initial : Analyse Demandée

    Initial --> Resolving : kb.isKbAvailable() && resolveDrug(candidate)
    Resolving --> NOT_CHECKED : !candidateResolved || !kbAvailable (KbCrossCheck.kt:132)
    Resolving --> CrossChecking : candidateResolved (ATC Connu)

    state CrossChecking {
        [*] --> CheckAllergies : checkAllergiesWithStatus
        CheckAllergies --> CheckMeds : checkMedicationsWithStatus (DDI)
        CheckMeds --> CheckConditions : checkConditionsWithStatus
        CheckConditions --> AggregateReport : checks = KbCheckReport(al, ddi, cond)
        AggregateReport --> [*]
    }

    CrossChecking --> ALERT : totalHits > 0 (KbCrossCheck.kt:131)
    
    state EvaluateClean <<choice>>
    CrossChecking --> EvaluateClean : totalHits == 0

    EvaluateClean --> CLEAN : checks.overall == CHECKED (isClean = true, :145)
    EvaluateClean --> INCOMPLETE : checks.overall == INCOMPLETE (checks.overall, :133)
    EvaluateClean --> NOT_CHECKED : checks.overall == KB_UNAVAILABLE

    ALERT --> [*] : "🔴 Écran Rouge Vif + triggerRedAlert"
    CLEAN --> [*] : "🟢 Écran Vert (Rien à signaler)"
    INCOMPLETE --> [*] : "🟠 Bandeau Ambre (Vérification partielle)"
    NOT_CHECKED --> [*] : "⚫ Bandeau Avertissement (Non vérifié)"
```

> **Règle vérifiée dans le code (`kb/KbCrossCheck.kt:418`)** : Une allergie sans code ATC est testée via `inferAtcFromAllergyName` (`kb/KbCrossCheck.kt:818`). Si aucun mot-clé ne correspond, elle ne bloque pas le pilier et retourne le statut `KbCheckStatus.CHECKED` (`KbSafety.pillarStatus(kbUp, allergies.size)` sans `unverifiedItems`).

### 4.3. Protocole de Tri de Catastrophe SALT (Asymmetric LWW & Grace Window)

Le code dans `triage/StatusResolver.kt:68-98` et `triage/SaltCode.kt:36-51` applique un **Last-Write-Wins asymétrique** avec fenêtre de grâce de 30 secondes pour rétrograder un statut décédé (DCD) :

```mermaid
stateDiagram-v2
    [*] --> ReceivedEvent : Événement SALT Reçu
    
    state Decision <<choice>>
    ReceivedEvent --> Decision : shouldOverwrite(existing, incoming)

    Decision --> Overwrite : "incoming.timestampSec > existing.timestampSec && !(existing == DCD && incoming != DCD)"
    Decision --> DemoteDcdGrace : "existing == DCD && incoming.timestampSec > existing.timestampSec + 30 (StatusResolver.kt:89)"
    Decision --> DemoteDcdOverride : "existing == DCD && incoming.isExplicitOverride == true (StatusResolver.kt:88)"
    Decision --> DiscardStale : incoming.timestampSec < existing.timestampSec
    Decision --> TieBreak : incoming.timestampSec == existing.timestampSec

    DemoteDcdGrace --> Overwrite : Rétrogradation Autorisée
    DemoteDcdOverride --> Overwrite : Rétrogradation Forcée Sauveteur
    TieBreak --> Overwrite : incoming.rescuerSid > existing.rescuerSid (Ordre Lexicographique)
    TieBreak --> DiscardStale : incoming.rescuerSid <= existing.rescuerSid

    Overwrite --> Updated : "apply(incoming) applique Statut"
    DiscardStale --> Ignore : Conserver Statut Existant
    Updated --> [*]
    Ignore --> [*]
```

### 4.4. Cycle d'Assemblage des Trames QR Multi-Frames (`JF:i/N`)

Conforme à `qr/JemmaQrFrameAssembler.kt:25-85` :
- Format des trames : `JF:<index>/<total>|<data>` où `index` est **1-based** (`1..N`).
- **Absence d'identifiant de lot et de checksum au niveau de la trame** (`qr/JemmaQrFrameAssembler.kt:16-19`) : Si une trame annonce un total différent ou un contenu divergent pour un même index, la collecte redémarre à zéro (`restarted = true`).
- Pour le canal FHIR Slideshow, les données sont le JSON FHIR brut découpé, sans compression deflate.

```mermaid
stateDiagram-v2
    [*] --> Idle : Scanner QR Actif

    Idle --> Collecting : "offer(raw) reçoit trame JF:i/N|data (index 1..N)"

    state Collecting {
        [*] --> CheckTotal : total annoncé == expectedTotal ?
        CheckTotal --> ResetBuffer : Non (Nouveau total ou conflit) -> reset()
        CheckTotal --> StorePart : Oui -> parts[i] = data
        ResetBuffer --> StorePart : Nouveau lot initialisé avec frame i
        StorePart --> ComputeMissing : "missing = (1..total).filter { it !in parts }"
        ComputeMissing --> [*]
    }

    state CompletionCheck <<choice>>
    Collecting --> CompletionCheck : "parts.size == total ?"

    CompletionCheck --> Progress : "parts.size < total (Result.Progress)"
    CompletionCheck --> Completed : "parts.size == total (Result.Complete)"

    Progress --> Collecting : Trame suivante
    Completed --> Joining : Concaténation ordinale des parts de 1 à total
    Joining --> PayloadValidation : Décodage via JemmaPayloadCodec ou Parse JSON FHIR
    PayloadValidation --> Success : Payload Validé
    PayloadValidation --> PayloadCorrupted : Erreur Syntaxe / Décompression
    PayloadCorrupted --> Idle : Alerte Trame Invalide
    Success --> [*]
```

---

## 5. Diagrammes de Séquence des Flux Fonctionnels Clés (Sequence Diagrams)

### 5.1. Détection de Collision Létale : Scénario Kurodo (Pénicilline × Augmentin)

Flux démontrant l'intervention de l'agent Gemma 4 via les outils `@Tool` de `ai/medscan/MedScanController.kt:10-12` et `ai/JemmaTools.kt:668` :

```mermaid
sequenceDiagram
    autonumber
    actor Secouriste as 🎒 Kamekichi (Secouriste)
    participant UI as 📱 Interface Caméra
    participant OCR as 👁️ ML Kit Japanese (app/build.gradle.kts:141)
    participant Ctrl as ⚙️ MedScanController (ai/medscan/MedScanController.kt)
    participant Gemma as ✨ Gemma 4 (LiteRT-LM)
    participant Cross as 🛡️ KbCrossCheck (kb/KbCrossCheck.kt)
    participant Tools as 🛠️ JemmaTools (ai/JemmaTools.kt)

    Secouriste->>UI: Filme boîte "Augmentin 1g"
    UI->>OCR: Analyse de l'image caméra
    OCR-->>Ctrl: Texte extrait : "Augmentin 1g amoxicilline clavulanate"
    Ctrl->>Gemma: Démarre agent de scan (system prompt + image)
    Gemma->>Ctrl: searchDrugCandidates(["Augmentin", "amoxicilline"]) [MedScanController.kt:10]
    Ctrl-->>Gemma: ["Augmentin -> J01CR02", "Amoxicillin -> J01CA04"]
    Gemma->>Ctrl: checkInteractions("J01CR02") [MedScanController.kt:12]
    Ctrl->>Cross: checkOneAtcAgainstAllergies(p.al, "J01CR02", ...)
    Cross->>Cross: Auto-réactivité ATC L3 "J01C" (KbCrossCheck.kt:327)
    Cross-->>Ctrl: [AllergyHit: "auto:J01C", severity=HIGH]
    Ctrl-->>Gemma: Collision mortelle trouvée (Allergie Pénicillines)
    Gemma->>Tools: triggerRedAlert("Allergie Mortelle", "Kurodo est allergique aux pénicillines.") [ai/JemmaTools.kt:668]
    Tools-->>UI: Émission JemmaToolEvent.RedAlert
    UI->>Secouriste: 🔴 ÉCRAN ROUGE + Signal Sonore d'Urgence
```

### 5.2. Contrôle Sans Interaction : Scénario Paracétamol chez un Profil Sain (Verdict CLEAN)

Illustration d'un contrôle de sécurité complet aboutissant au verdict **CLEAN** (`kb/KbCrossCheck.kt:145`) :

```mermaid
sequenceDiagram
    autonumber
    actor Secouriste as 🎒 Secouriste
    participant UI as 📱 Interface Scanner
    participant Gemma as ✨ Gemma 4 (LiteRT-LM)
    participant Tools as 🛠️ JemmaTools (ai/JemmaTools.kt)
    participant Cross as 🛡️ KbCrossCheck (kb/KbCrossCheck.kt)

    Secouriste->>UI: Soumet "Paracétamol 500mg" (Profil sain)
    UI->>Gemma: Contrôle candidat
    Gemma->>Tools: resolveDrug("Paracétamol") [ai/JemmaTools.kt:172]
    Tools-->>Gemma: {atc: "N02BE01", display: "Paracetamol"}
    Gemma->>Tools: checkOneAtcAgainstFocusProfile("N02BE01", "Paracetamol") [ai/JemmaTools.kt:625]
    Tools->>Cross: checkAllergiesWithStatus + checkMedicationsWithStatus + checkConditionsWithStatus
    Note over Cross: Allergies : 0 hit (CHECKED)<br/>DDI : 0 hit (CHECKED)<br/>Pathologies : 0 hit (CHECKED)
    Cross-->>Tools: CrossCheckResult(totalHits=0, checks=all(CHECKED), isClean=true, verdict=CLEAN)
    Tools-->>Gemma: {verdict: "CLEAN", isClean: true, totalHits: 0}
    Gemma-->>UI: Verdict Clinique "CLEAN" validé
    UI->>Secouriste: 🟢 ÉCRAN VERT ("Rien à signaler — Médicament vérifié compatible")
```

> **Note sur Edoxaban × Aspirine** : Le résultat de l'interaction Edoxaban × Aspirine dépend de la présence de la paire dans la base locale (`v_ddi_emergency` / `ddi_facts`), à vérifier par `kb-sql`. Si la paire est présente, elle donne `ALERT` (`kb/KbCrossCheck.kt:131`).

### 5.3. Génération du QR Texte 25 Langues avec Budget d'Éviction Strict (1800 octets UTF-8)

Implémenté dans `qr/JemmaTextPayloadBuilder.kt:152-290` :

```mermaid
sequenceDiagram
    autonumber
    actor Patient as 👤 Patient / Soignant
    participant UI as 📱 Écran Export QR
    participant Builder as 📝 JemmaTextPayloadBuilder (qr/JemmaTextPayloadBuilder.kt)
    participant Hydrator as 🧠 JemmaProfileHydrator (kb/JemmaProfileHydrator.kt)

    Patient->>UI: Sélectionne "QR Texte d'Urgence" en Japonais (JA)
    UI->>Hydrator: hydrate(profile, lang="ja")
    Hydrator-->>UI: HydratedProfile (Libellés traduits)
    UI->>Builder: build(hydrated, Lang.JA, maxBytes=1800) [qr/JemmaTextPayloadBuilder.kt:152]
    Builder->>Builder: Assemble en-tête, identité, groupe sanguin, contacts, piliers
    Builder->>Builder: Calcule utf8Size(payload) [qr/JemmaTextPayloadBuilder.kt:106]
    alt Taille <= 1800 octets UTF-8
        Builder-->>UI: Texte complet non tronqué
    else Taille > 1800 octets UTF-8
        loop Tant que taille > 1800 octets
            Builder->>Builder: Supprime la dernière ligne de la section au rang le plus élevé
            Note over Builder: Éviction : RANK_FUNCTIONAL(12) -> RANK_PREGNANCY(11) -> ... -> RANK_ALLERGIES(1)
        end
        Builder->>Builder: Ajoute le marqueur TRUNCATION_MARK "✂️ …" [qr/JemmaTextPayloadBuilder.kt:73]
        Builder-->>UI: Texte tronqué conforme (<= 1800 octets UTF-8)
    end
    UI->>UI: Rendu QR Code Bitmap (ZXing Core, EC=M)
```

### 5.4. Découverte, Alerte et Propagation Maillée P2P SALT (Zone Sinistrée)

Implémenté dans `sos/JemmaSosBleScanner.kt`, `sos/JemmaSosChunkCodec.kt:175` (`MAX_CHUNK_BYTES = 200`), `sos/JemmaNearbyEndpointCodec.kt:51` (`MAX_ENDPOINT_NAME_LEN = 131`) et `triage/StatusResolver.kt:68, 108` :

```mermaid
sequenceDiagram
    autonumber
    actor SecouristeA as 🎒 Secouriste A (DMAT)
    participant PhoneA as 📱 Terminal A
    participant BLE as 📡 BLE Broadcast P2P (MAX_CHUNK_BYTES = 200)
    participant PhoneB as 📱 Terminal B (Poste Médical)
    participant ResolverB as ⚖️ StatusResolver (triage/StatusResolver.kt)

    SecouristeA->>PhoneA: Assigne statut SALT "HELP" (Rouge "#F44336") pour Victime Haru
    PhoneA->>PhoneA: Encode trame chunk type 'E' (<= 200 octets UTF-8)
    PhoneA->>BLE: Diffusion BLE Advertising
    BLE->>PhoneB: Trame reçue par Terminal B
    PhoneB->>ResolverB: shouldOverwrite(existing, incoming) [triage/StatusResolver.kt:68]
    ResolverB-->>PhoneB: true (Timestamp plus récent)
    PhoneB->>ResolverB: apply(incoming) [triage/StatusResolver.kt:108]
    PhoneB->>PhoneB: Met à jour le Radar (Point rouge clignotant)
    PhoneB->>PhoneB: Notifie le secouriste B
```

### 5.5. Pipeline de Vulgarisation Pédagogique Multilingue et Cache Local

Implémenté dans `ai/assistant/VulgariseRepository.kt:27-92` et `ai/assistant/VulgariseHelper.kt:186-245` :

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Utilisateur
    participant UI as 📱 Écran Vulgarisation
    participant Repo as 💾 VulgariseRepository (ai/assistant/VulgariseRepository.kt)
    participant Gemma as ✨ Gemma 4 (LiteRT-LM)
    participant Buffer as ⏱️ ThrottledTextAppender (ai/assistant/VulgariseHelper.kt)

    User->>UI: Clic sur "Expliquer simplement"
    UI->>Repo: get(cacheKey, lang) [ai/assistant/VulgariseRepository.kt:66]
    alt Entrée en cache présente (Hit)
        Repo-->>UI: Texte vulgarisé sauvegardé
        UI->>User: Affichage immédiat (0 ms, 0 NPU)
    else Entrée absente (Miss)
        UI->>Gemma: Inférence streaming (Prompt vulgarisation bilingue)
        loop Tokens reçus
            Gemma-->>Buffer: append(token) [intervalMs = 150L, :189]
            Buffer-->>UI: Mise à jour fluide du TextView
        end
        Gemma-->>UI: Inférence terminée
        UI->>Repo: save(cacheKey, lang, fullText) [ai/assistant/VulgariseRepository.kt:74]
        Repo->>Repo: Écriture simple cacheFile.writeText(json) [:86]
    end
```

---

## 6. Diagrammes d'Activités & Algorithmes Métier (Activity Diagrams)

### 6.1. Algorithme de Résolution Sémantique d'un Médicament

Implémentation exacte observée dans `kb/KnowledgeBaseService.kt:143-250,833-855` :

```mermaid
flowchart TD
    Start(["Chaîne Médicament Candidate"]) --> NormalizeHardcoded{"Correspondance Table des 10 Molécules ? (KnowledgeBaseService.kt:153)"}
    
    NormalizeHardcoded -- "Oui (Augmentin, Amoxicilline, Aspirine, Loxonine, Calonal, Warfarine, Cravit, Adrénaline, Voltaren...)" --> MapEnglish["Substitution par DCI Anglaise Standard"]
    NormalizeHardcoded -- Non --> ExactLookup["Recherche Exacte ddinter_drugs (COLLATE NOCASE, :174)"]
    
    MapEnglish --> ExactLookup
    ExactLookup --> FoundExact{"Trouvé dans ddinter_drugs ?"}
    
    FoundExact -- Oui --> ReturnExact(["Retourne ResolvedConcept.Exact (Code ATC + Display)"])
    FoundExact -- Non --> RegexAtc{"La chaîne a la forme d'un code ATC ? (Regex A##XX##, :197)"}
    
    RegexAtc -- Oui --> LookupTermCodes["Recherche directe terminology_codes par atc_code (:201)"]
    LookupTermCodes --> FoundTerm{"Trouvé ?"}
    FoundTerm -- Oui --> ReturnTerm(["Retourne Concept avec Clean Generic Display (:220)"])
    FoundTerm -- Non --> SearchScript{"Détection du Script Unicode (containsCjk, :850)"}
    
    RegexAtc -- Non --> SearchScript
    
    SearchScript -- "Contient des caractères CJK" --> QueryFTSCJK["Recherche FTS5 table terminology_cjk (trigram, :822)"]
    SearchScript -- "Caractères Latins Purs" --> QueryFTSLatin["Recherche FTS5 table terminology_latin (unicode61, :820)"]
    
    QueryFTSCJK --> FtsSuccess{"Résultat FTS5 ?"}
    QueryFTSLatin --> FtsSuccess
    
    FtsSuccess -- Succès --> ReturnFts(["Retourne Liste des Concepts Associés"])
    FtsSuccess -- Échec / Erreur FTS --> FallbackLike["Fallback LIKE sur terminology_codes.primary_display (:824)"]
    FallbackLike --> ReturnFts
```

### 6.2. Algorithme de Détection d'Allergie Croisée par Arborescence de Classes

Implémentation exacte observée dans `kb/KbCrossCheck.kt:304-418,818-885` :

```mermaid
flowchart TD
    Start(["Médicament Candidat (ATC Set) + Liste Allergies"]) --> LoopAllergies["Pour chaque allergie du profil"]
    
    LoopAllergies --> ExactCode{"Code Substance Exact Identique ? (KbCrossCheck.kt:306)"}
    ExactCode -- Oui --> DirectHit["AllergyHit: Type 'code' (KbCrossCheck.kt:307)"]
    
    ExactCode -- Non --> AutoReactivity{"Même Famille ATC L3 ? (ex: J01C, KbCrossCheck.kt:327)"}
    AutoReactivity -- Oui --> AutoHit["AllergyHit: Type 'auto:L3' (KbCrossCheck.kt:333)"]
    
    AutoReactivity -- Non --> CrossTable{"Paire dans allergy_cross_reactivity ? (KbCrossCheck.kt:348)"}
    CrossTable -- Oui --> CrossHit["AllergyHit: Type 'xreact:...' (KbCrossCheck.kt:364)"]
    
    CrossTable -- Non --> InferAtc{"inferAtcFromAllergyName(name) ? (KbCrossCheck.kt:818)"}
    InferAtc -- "contient 'ains' -> M01AE01 (:858)" --> BugAins["Attention: Match aussi 'grains' (Défaut UC-ALM-009)"]
    InferAtc -- "contient 'statin' -> C10AA01 (:878)" --> BugStatin["Attention: Match aussi 'nystatine' (Défaut UC-ALM-010)"]
    InferAtc -- "Autre mot-clé reconnu" --> ClassHit["AllergyHit: Type 'class:$matchedClass' (:402)"]
    
    BugAins --> ClassHit
    BugStatin --> ClassHit
    
    InferAtc -- Non --> SubstringCheck{"Allergie >= 4 chars contenue dans Display ? (:410)"}
    SubstringCheck -- Oui --> SubstringHit["AllergyHit: Type 'name' (:411)"]
    SubstringCheck -- Non --> NoHit["Aucun hit pour cette allergie"]
    
    DirectHit --> Collect["Ajout à la liste des hits"]
    AutoHit --> Collect
    CrossHit --> Collect
    ClassHit --> Collect
    SubstringHit --> Collect
    NoHit --> NextAllergy["Allergie suivante"]
    Collect --> NextAllergy
    
    NextAllergy --> Remaining{"Reste des allergies ?"}
    Remaining -- Oui --> LoopAllergies
    Remaining -- Non --> Finish(["PillarCheck(hits, KbSafety.pillarStatus(kbUp, allergies.size)) (:418)"])
```

### 6.3. Algorithme d'Éviction Prioritaire du QR Texte Universel

Implémenté dans `qr/JemmaTextPayloadBuilder.kt:82-93,237-290` :

```mermaid
flowchart TD
    Start(["HydratedProfile"]) --> BuildSections["Construire En-tête, Identité, Contacts et 12 Sections Piliers"]
    BuildSections --> CheckBytes{"utf8Size(payload) <= 1800 octets ? (MAX_BYTES, :67)"}
    
    CheckBytes -- Oui --> ReturnPayload(["Retourner Payload Conforme"])
    CheckBytes -- Non --> FindHighest["Identifier la section active au RANK le plus élevé (:82-93)"]
    
    subgraph Echelle_Rangs_Eviction ["Échelle d'Éviction (Du premier supprimé au dernier conservé)"]
        R12["12. RANK_FUNCTIONAL (Statut Fonctionnel, :93)"]
        R11["11. RANK_PREGNANCY (Grossesse, :92)"]
        R10["10. RANK_IMMUNIZATIONS (Vaccinations, :91)"]
        R9["9. RANK_RESULTS (Biologie, :90)"]
        R8["8. RANK_PROCEDURES (Actes chirurgicaux, :89)"]
        R7["7. RANK_PAST_PROBLEMS (Antécédents passés, :88)"]
        R6["6. RANK_DEVICES (Dispositifs médicaux, :87)"]
        R5["5. RANK_PATIENT_EXTRA (Adresses, télécoms, ID national, :86)"]
        R4["4. RANK_CONTACTS (Contacts d'urgence, :85)"]
        R3["3. RANK_CONDITIONS (Maladies actives, :84)"]
        R2["2. RANK_MEDICATIONS (Médicaments en cours, :83)"]
        R1["1. RANK_ALLERGIES (Allergies - Inviolable, :82)"]
    end
    
    FindHighest --> DropLine["Supprimer la dernière ligne de la section sélectionnée (:247)"]
    DropLine --> AddMarker["Insérer le marqueur TRUNCATION_MARK '✂️ …' (:73)"]
    AddMarker --> CheckBytes
```

### 6.4. Algorithme de Réconciliation et d'Inviolabilité du Groupe Sanguin

Implémenté dans `ips/IpsBloodGroup.kt:1-90` et `profiles/ProfilesRepository.kt:82-114` :

```mermaid
flowchart TD
    Start(["Profil Patient avec p.bt et liste des résultats rs"]) --> NormBT["Normaliser p.bt (IpsBloodGroup.normalize, A+, O-, B+, AB...)"]
    NormBT --> ScanObs["Rechercher Observation LOINC 882-1 dans rs"]
    
    ScanObs --> ObsPresent{"Observation 882-1 présente ?"}
    ObsPresent -- Non --> GenerateObs["Générer Observation LOINC 882-1 avec Code SNOMED Conforme"]
    
    ObsPresent -- Oui --> Compare{"Valeur Observation == p.bt ?"}
    Compare -- Oui --> Consistent["Conserver Observation Valide"]
    Compare -- Non --> ConflictDetected["Conflit Détecté : p.bt fait foi (ProfilesRepository.kt:80)"]
    
    ConflictDetected --> IsManualEdit{"Action en cours ?"}
    IsManualEdit -- "Saisie Formulaire Manuelle" --> RejectEdit["Bloquer la modification contradictoire"]
    IsManualEdit -- "Import / Réconciliation Fichiers" --> DropContradictory["Évincer l'observation contradictoire et notifier BloodGroupConflict"]
    
    GenerateObs --> SaveProfile["Persistance sous writeMutex (ProfilesRepository.kt:146)"]
    Consistent --> SaveProfile
    DropContradictory --> SaveProfile
    SaveProfile --> Finish(["Profil Persisté avec Intégrité Sanguine Assurée"])
```

---

## 7. Spécifications Formelles des Contrats d'Interface Vérifiés

Signatures réelles en Kotlin relevées ligne par ligne dans le code source :

### 7.1. Contrat Réel du Service de Connaissance Médicale (`KnowledgeBaseService`)

```kotlin
// Source : JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/kb/KnowledgeBaseService.kt
package be.heyman.android.jemmapassdemo.kb

@Singleton
class KnowledgeBaseService @Inject constructor(
    private val kbManager: KnowledgeBaseManager,
) {
    // Résolution sémantique d'un médicament (nom de marque, DCI, katakana ou code ATC)
    // Ligne 143
    suspend fun resolveDrug(name: String?): ResolvedConcept

    // Résolution sémantique d'une allergie (nom de substance ou aliment)
    // Ligne 263
    suspend fun resolveAllergy(name: String?): ResolvedConcept

    // Interrogation DDI rapide sur la vue v_ddi_emergency (Major + Moderate)
    // Ligne 440
    suspend fun queryDDIByAtc(atcA: String?, atcB: String?): DDIResult

    // Interrogation DDI avec résolution automatique de noms libres
    // Ligne 478
    suspend fun queryDDI(drugA: String?, drugB: String?): DDIResult

    // Interrogation contre-indication médicament x pathologie
    // Ligne 758
    suspend fun queryDrugDisease(atc: String?, diseaseName: String?): DrugDiseaseResult

    // Recherche FTS5 plein-texte (terminology_latin ou terminology_cjk selon script)
    // Ligne 833
    suspend fun searchCodes(
        query: String?,
        lang: String = "en",
        categoryFilter: String? = null,
        maxResults: Int = SEARCH_MAX_RESULTS,
    ): KbSearchResult
}
```

### 7.2. Contrat Réel du Moteur de Contrôle Croisé (`KbCrossCheck`)

```kotlin
// Source : JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/kb/KbCrossCheck.kt
package be.heyman.android.jemmapassdemo.kb

@Singleton
class KbCrossCheck @Inject constructor(
    private val kb: KnowledgeBaseService,
    private val kbManager: KnowledgeBaseManager,
) {
    // Contrôle maître d'un médicament contre les 3 piliers (allergies, médocs, pathologies)
    // Ligne 652
    suspend fun checkOneDrugAgainstProfile(
        candidateName: String,
        allergies: List<JAllergy>,
        meds: List<JMedication>,
        conditions: List<JCondition>,
        lang: String = "en",
    ): CrossCheckResult

    // Contrôle spécifique contre les allergies avec statut de vérification
    // Ligne 233
    suspend fun checkAllergiesWithStatus(
        allergies: List<JAllergy>,
        candidateAtc: String,
        candidateAllAtcs: List<String>,
        candidateDisplay: String,
        lang: String = "en",
        kbAvailable: Boolean? = null,
    ): PillarCheck<AllergyHit>

    // Contrôle DDI contre les médicaments existants avec statut de vérification
    // Ligne 481
    suspend fun checkMedicationsWithStatus(
        meds: List<JMedication>,
        candidateAtc: String,
        candidateAllAtcs: List<String>,
        candidateDisplay: String,
        includeMinor: Boolean = false,
        lang: String = "en",
        kbAvailable: Boolean? = null,
    ): PillarCheck<DdiHit>

    // Accès simplifié DDI
    // Ligne 439
    suspend fun checkOneAtcAgainstMedications(
        meds: List<JMedication>,
        candidateAtc: String,
        candidateAllAtcs: List<String>,
        candidateDisplay: String,
        includeMinor: Boolean = false,
        lang: String = "en",
    ): List<DdiHit>

    // Contrôle contre les pathologies actives avec statut de vérification
    // Ligne 568
    suspend fun checkConditionsWithStatus(
        conditions: List<JCondition>,
        candidateAtc: String,
        candidateDisplay: String,
        lang: String = "en",
        kbAvailable: Boolean? = null,
    ): PillarCheck<DrugDiseaseHit>
}
```

### 7.3. Contrat Réel des Codecs QR, Trames et Triage

```kotlin
// Source : qr/JemmaPayloadCodec.kt, qr/JemmaTextPayloadBuilder.kt, qr/JemmaQrFrameAssembler.kt, triage/StatusResolver.kt

object JemmaPayloadCodec {
    const val MAGIC_PREFIX = "_j2:" // Ligne 57
    fun encode(profile: JemmaProfileJ): EncodeResult // Ligne 189
    fun decode(text: String?): DecodeResult // Ligne 122
}

object JemmaQrFrameSplitter {
    const val QR_MAX_SINGLE = 1800 // Ligne 53
    fun split(payload: String, maxSingle: Int, frameChunk: Int): List<String> // Ligne 94
}

object JemmaTextPayloadBuilder {
    const val MAX_BYTES = 1800 // Ligne 67
    const val TRUNCATION_MARK = "✂️ …" // Ligne 73
    fun build(hydrated: HydratedProfile, lang: Lang, maxBytes: Int = MAX_BYTES): String // Ligne 152
}

class JemmaQrFrameAssembler {
    data class Frame(val index: Int, val total: Int, val data: String) // Ligne 26 (index 1-based)
    fun offer(raw: String?): Result // Ligne 65
    fun missing(): List<Int> // Ligne 53
    fun reset() // Ligne 57
}

class StatusResolver {
    companion object {
        const val DCD_GRACE_SEC = 30L // Ligne 56
        fun shouldOverwrite(existing: StatusEvent, incoming: StatusEvent): Boolean // Ligne 68
    }
    fun apply(incoming: StatusEvent): StatusEvent // Ligne 108
}
```

---

## 8. Proposé — N'existe pas encore (Cibles d'Évolution & Portages)

Cette section rassemble expressément les concepts, classes et propositions d'architecture qui **ne figurent pas dans le code Android actuel** mais représentent des cibles d'évolution technique pour les refactorings futurs et le portage iOS :

1. **Façade d'Abstractions Multiplateforme (`ITransferHub`, `ICrossCheckEngine`)** :
   - *Statut actuel* : Absentes du code. Les composants actuels sont des Singletons Hilt ou des `object` Kotlin directs (`KbCrossCheck`, `JemmaPayloadCodec`, `JemmaTextPayloadBuilder`).
   - *Proposition* : Créer des interfaces pures pour faciliter l'injection de dépendances multiplateforme (KMP / Swift).

2. **Résolution Lexicale Robuste par Frontières de Mots (Correction UC-ALM-009 / UC-ALM-010)** :
   - *Statut actuel* : Le code utilise `n.contains("ains")` et `n.contains("statin")` (`kb/KbCrossCheck.kt:858,878`), ce qui produit des faux positifs sur "grains" et "nystatine".
   - *Proposition* : Remplacer par une expression régulière avec frontières de mots `\bains\b` ou une tokenisation lexicale stricte pour exclure les sous-chaînes accidentelles.

3. **Normalisation Universelle Zenkaku / Hankaku & Table Katakana Dédiée** :
   - *Statut actuel* : Normalisation gérée par un bloc `when` codé en dur pour 10 molécules (`kb/KnowledgeBaseService.kt:153-166`).
   - *Proposition* : Intégrer un analyseur morphologique ou une table de correspondance formelle Katakana -> HOT / YJ / ATC pour l'ensemble de la pharmacopée japonaise.

4. **Somme de Contrôle et Identifiant de Lot dans les Trames QR Multi-Frames** :
   - *Statut actuel* : Le format `JF:i/N|data` ne comporte ni hash, ni identifiant de session, ni CRC (`qr/JemmaQrFrameAssembler.kt:16-19`).
   - *Proposition* : Étendre le format de trame à `JF:i/N:SESSION_HASH|data` pour prévenir les entrelacements de scans entre deux diaporamas différents.

5. **Signature Cryptographique de Lot et Chiffrement Bout-en-Bout** :
   - *Statut actuel* : Les trames transitent en clair (Base64 deflate-raw ou JSON brut).
   - *Proposition* : Intégrer une couche de signature numérique (Ed25519) pour authentifier l'émetteur du pass en milieu d'urgence.
