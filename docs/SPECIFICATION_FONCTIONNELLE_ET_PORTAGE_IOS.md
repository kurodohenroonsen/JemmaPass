# 🐢 JemmaPass — Dossier d'Architecture Fonctionnelle & Cahier des Charges de Portage iOS (Japon)

> **Document Technique d'Architecture & Spécification de Portage**  
> **Auteur du projet d'origine** : Claude Heyman (*Kurodo Henro Onsen*)  
> **Version** : 1.0.14-SPEC (aligné sur l'application Android v1.0.14, `app/build.gradle.kts:38`)  
> **Statut** : Document de Travail & Spécification de Portage iOS (En cours de révision technique — non approuvé comme norme unique)  
> **Périmètre** : Spécification Fonctionnelle (18 Piliers IPS, Moteur Clinique, AI Edge, Transfert Hors-Ligne) & Blueprint d'Ingénierie pour le Portage iOS ciblant le Japon.  
> **Conventions de classification** :  
> Chaque exigence ou composant technique est rigoureusement classé sous l'une des 3 catégories suivantes :  
> - `[EXISTE SUR ANDROID (fichier:ligne)]` : Fonctionnalité ou classe existante, vérifiée sur pièces dans le code source.  
> - `[PROPOSITION IOS]` : Architecture ou composant cible proposé pour le portage iOS.  
> - `[HYPOTHÈSE À VÉRIFIER]` : Hypothèse médicale, réglementaire, de marché ou faisabilité OS nécessitant une validation empirique ou juridique.

---

## Table des Matières

1. [Vision Stratégique & Contexte Japon](#1-vision-stratégique--contexte-japon)
   - 1.1. La promesse JemmaPass : *Zero-Cloud at Runtime, On-Device, Cross-Border*
   - 1.2. Le quatuor narratif : Kurodo, Haru, Kamekichi, Gemma
   - 1.3. Pourquoi le portage iOS est vital au Japon (PDM et usages)
   - 1.4. Scénarios critiques au Japon : Catastrophe naturelle et Pèlerinage
2. [Cartographie Fonctionnelle Complète de l'Existant](#2-cartographie-fonctionnelle-complète-de-lexistant)
   - 2.1. Les 18 Piliers de l'International Patient Summary (HL7 FHIR R4)
   - 2.2. Dualité des formats : FHIR R4 (Source de Vérité Piliers FHIR-Natifs) vs `_j 1.2` (Projection Compacte)
   - 2.3. Moteur Clinique & Sécurité Décisionnelle (`KbCrossCheck` + `KbSafety`)
   - 2.4. Le Moteur de Vulgarisation Thérapeutique (Gemma 4 + `VulgariseRepository`)
   - 2.5. Les 21 Outils Typés `@Tool` de Jemma
   - 2.6. Canaux de Transfert Multi-Supports Hors-Ligne
3. [Spécificités Médicales, Réglementaires et Linguistiques Japonaises](#3-spécificités-médicales-réglementaires-et-linguistiques-japonaises)
   - 3.1. Écosystème de santé : MHLW, PMDA et statut Dispositif Médical (SaMD)
   - 3.2. Nomenclatures et Pharmacopée : Noms en Katakana, Codes HOT/YJ et Classes ATC
   - 3.3. Carnet de santé (*Okusuri Techou*) et Restrictions Légales du My Number
   - 3.4. Recherche FTS5 CJK (Trigramme) vs Analyseurs Morphologiques
   - 3.5. Tri de Catastrophe : Protocole SALT adapté et Grille Réelle
4. [Cahier des Charges Architectural du Portage iOS](#4-cahier-des-charges-architectural-du-portage-ios)
   - 4.1. Stratégie d'implémentation : KMP partagé vs 100% Swift Natif
   - 4.2. Matrice d'Équivalence Technologique Complète (Android ➔ iOS)
   - 4.3. Base de Données Clinique (`knowledge_full.db`) & Moteur FTS5 sous iOS
   - 4.4. Intelligence Artificielle Embarquée : Inférence LLM & Vision OCR sur Apple Silicon
   - 4.5. Le Défi du Réseau P2P : Google Nearby Connections (Android) ➔ MultipeerConnectivity & CoreBluetooth (iOS)
   - 4.6. Expérience Utilisateur & Intégration Écosystème Apple
5. [Orchestration & Feuille de Route d'Exécution](#5-orchestration--feuille-de-route-dexécution)
   - 5.1. Rôles et responsabilités
   - 5.2. Plan par jalons (Milestones M1 à M5)
   - 5.3. Matrice d'assurance qualité & cas d'usage critiques
6. [Sources citées — à vérifier par un humain avant usage](#6-sources-citées--à-vérifier-par-un-humain-avant-usage)

---

## 1. Vision Stratégique & Contexte Japon

### 1.1. La promesse JemmaPass : *Zero-Cloud at Runtime, On-Device, Cross-Border*

- `[EXISTE SUR ANDROID (downloads/JemmaModelCatalog.kt:3,80-90)]` : **Modèle de distribution hybride** : Le runtime fonctionne à 100% hors-ligne lors des interventions d'urgence. En revanche, l'installation ou la mise à jour initiale de la base clinique (`knowledge_full.db`, ~2,0 Go dans l'en-tête `:7`, taille déclarée `3_360_727_040L` soit ~3,13 Go à `:89`) et des modèles de langage (`gemma-4-E2B-it.litertlm`, ~2,4 Go ou `gemma-4-E4B-it.litertlm`, ~3,4 Go à `:5-6`) s'effectue via téléchargement HTTP depuis `https://jemmapass.net/models/`.
- `[EXISTE SUR ANDROID (profiles/ProfilesRepository.kt:23-28)]` : **Standard International HL7 FHIR R4 IPS** : Le document maître persisté pour les 8 piliers cliniques FHIR-natifs (`IpsNativePillars`, `ips/IpsImmunization.kt:107-115`) (`im` vaccins, `pr` actes, `dv` dispositifs, `rs` biologie, `ph` antécédents, `cn` problèmes actifs, `pg` grossesse, `fs` statut fonctionnel) est le Bundle FHIR R4 IPS (`<sid>.fhir.json`), projeté en format court `_j 1.2` (`<sid>.json`, `ProfilesRepository.kt:464-472`). En revanche, les allergies (`al`) et les médicaments (`md`) **ne sont pas FHIR-natifs** : ils sont saisis et gérés dans `_j 1.2` (`JemmaProfileJ`) puis hydratés dans le Bundle FHIR via `build` (`qr/JemmaFhirBundleBuilder.kt:82`).
- `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:130-135)]` : **Contrôle Pharmacologique Embarqué Déterministe** : Détection des interactions médicamenteuses majeures et modérées (DDInter 2.0 via `v_ddi_emergency`) et des allergies croisées sans appel réseau avec calcul du `verdict`.
- `[HYPOTHÈSE À VÉRIFIER]` : **Conformité réglementaire APPI / RGPD / HIPAA** : Bien que le stockage soit strictement local (on-device sans serveur central), la conformité formelle aux lois de protection des données de santé (APPI au Japon, RGPD en Europe, HIPAA aux USA) nécessite un audit juridique certifié, notamment en raison de la sensibilité des données médicales d'urgence.

### 1.2. Le quatuor narratif : Kurodo, Haru, Kamekichi, Gemma

Le système a été conçu autour de 4 personas :
- 🚶‍♂️ **Kurodo** : Pèlerin étranger (Belge). Allergie létale à la pénicilline (`SNOMED 91936005`). Risque vital immédiat si administration d'Augmentin (`ATC J01CR02`).
- 👵 **Haru** : Citoyenne japonaise (80 ans). Sous anticoagulant oral direct Edoxaban (`ATC B01AF03` [WHOCC](https://www.whocc.no/atc_ddd_index/?code=B01AF03) / Lixiana).
  > `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:131)]` : Si l'association d'Edoxaban (`B01AF03`) avec un antiagrégant (ex: Aspirine `B01AC06`) ou un AINS est présente dans la vue locale `v_ddi_emergency` / `ddi_facts` (à vérifier par `kb-sql` sur `knowledge_full.db`), elle produit impérativement le verdict `ALERT` (totalHits > 0). En cas de présence dans la base, l'anticoagulant oral direct combiné à l'aspirine ne peut jamais afficher un écran vert `CLEAN`.
- 🎒 **Kamekichi** : Secouriste bénévole / DMAT. Scanne les médicaments et pass via la caméra hors-ligne et coordonne le tri de catastrophe.
- ✨ **Gemma / Jemma** : Agent IA local exploitant LiteRT-LM, pilotant 21 outils `@Tool` et vulgarisant les alertes dans la langue de l'intervenant.

### 1.3. Pourquoi le portage iOS est vital au Japon

- `[HYPOTHÈSE À VÉRIFIER]` : **Part de marché iOS au Japon (68.2%, Août 2026)** : Selon les données de [StatCounter Mobile OS Market Share Japan (Août 2026)](https://gs.statcounter.com/os-market-share/mobile/japan), iOS détient **68.2%** des parts de marché des systèmes d'exploitation mobiles au Japon (contre 31.6% pour Android), confirmant la nécessité critique du portage pour toucher la majorité des secouristes, soignants et citoyens.
- `[PROPOSITION IOS]` : Le portage iOS permet d'éliminer la rupture opérationnelle actuelle où seuls les terminaux Android peuvent participer au réseau de triage ou décoder les pass `_j2`.

### 1.4. Scénarios critiques au Japon

- **Catastrophe Naturelle Majeure (ex: Séisme de la péninsule de Noto 2024, Séisme redouté du Nankai)** : Rupture totale des réseaux télécoms et électriques.
  - `[EXISTE SUR ANDROID (sos/JemmaSosBleScanner.kt:49)]` : Découverte et partage maillé de balises SOS hors-ligne.
  - `[EXISTE SUR ANDROID (qr/JemmaTextPayloadBuilder.kt:152)]` : QR Code lisible sans application par tout smartphone en mode texte traduit.
- **Pèlerinage de Shikoku (Henro - 88 Temples) & Tourisme International** :
  - `[EXISTE SUR ANDROID (qr/JemmaTextPayloadBuilder.kt:109-135)]` : Canal 2 traduisant instantanément via `Lang` en 25 langues (dont japonais, anglais, français, chinois, coréen).

---

## 2. Cartographie Fonctionnelle Complète de l'Existant

### 2.1. Les 18 Piliers de l'International Patient Summary (HL7 FHIR R4)

L'architecture s'aligne sur le standard HL7 FHIR R4 IPS ([ISO 27269:2021](https://www.iso.org/standard/79491.html)) :

```
┌────────────────────────────────────────────────────────────────────────┐
│                      JEMMAPASS — 18 PILIERS IPS                        │
├───────────────────────────────────┬────────────────────────────────────┤
│ 11 PILIERS ACTIFS                 │ 7 PILIERS STUBS                    │
├───────────────────────────────────┼────────────────────────────────────┤
│ 👤 Patient (Demographics/Contact)  │ ⚖️ Advance Directives (ad)         │
│ ⚠️ Allergies & Intolérances (al)  │ 🤝 Consents & Authorizations (cs)  │
│ 💊 Médications en Cours (md)      │ 🎯 Care Goals (gl)                 │
│ 🩺 Problèmes Actifs (cn)          │ 🏥 Encounters (en)                 │
│ 📜 Antécédents Passés (ph)        │ 💼 Occupational Data (oc)          │
│ 💉 Vaccinations (im)              │ 👨‍⚕️ Healthcare Providers (gp)      │
│ 🏥 Actes & Chirurgies (pr)        │ 📞 Provenance / History (pv)       │
│ 📟 Dispositifs Médicaux (dv)      │                                    │
│ 🧪 Biologie & Labo (rs)           │                                    │
│ 🤰 Grossesse & Maternité (pg)     │                                    │
│ ♿ Statut Fonctionnel (fs)        │                                    │
└───────────────────────────────────┴────────────────────────────────────┘
```

- `[EXISTE SUR ANDROID (qr/JemmaProfileJ.kt:43-96)]` : Les 18 piliers sont modélisés dans `JemmaProfileJ` sous leurs clés courtes JSON (`al, md, cn, ph, im, pr, dv, fs, pg, rs, ad, cs, gl, en, oc, pv`).
- `[PROPOSITION IOS / HYPOTHÈSE À VÉRIFIER]` : Les codes LOINC 11453-8 (Advance Directives) et 11383-7 (Care Goals) mentionnés dans les ébauches antérieures sont **totalement absents du code source Android** et constituent des cibles d'implémentation future.
- `[EXISTE SUR ANDROID (qr/JemmaProfileJ.kt:109-160)]` : Les contacts d'urgence sont stockés dans `p.ct` (liste `JContact`) au sein de l'objet patient `p`, et non à la racine du profil.
- `[EXISTE SUR ANDROID (ips/IpsBloodGroup.kt:21-188)]` : Le groupe sanguin déclaré (`p.bt`) est synchronisé avec l'Observation LOINC 882-1 correspondante (`rs`).

### 2.2. Dualité des formats : FHIR R4 vs `_j 1.2`

- `[EXISTE SUR ANDROID (profiles/ProfilesRepository.kt:23-35, ips/IpsImmunization.kt:107-115)]` :
  - **Source de Vérité pour les 8 Piliers FHIR-Natifs** : `<sid>.fhir.json` (HL7 FHIR R4 Bundle).
  - **Projection Compacte & Piliers Historiques** : `<sid>.json` (`JemmaProfileJ`, schéma `_j 1.2`), hébergeant notamment la saisie originale du patient `p`, des allergies `al` et des médicaments `md`.
- `[EXISTE SUR ANDROID (profiles/ProfilesRepository.kt:146,371)]` (`writeMutex` / `withLock`) :
  - **Contrôle de Concurrence** : Le cycle Lecture-Modification-Écriture est protégé par `private val writeMutex = kotlinx.coroutines.sync.Mutex()`.
  - **Nuance technique critique** : Le `Mutex` de coroutines Kotlin est **strictement non-réentrant**. Tout appel imbriqué à `withLock` dans la même coroutine provoque un interblocage (deadlock) immédiat.

### 2.3. Moteur Clinique & Sécurité Décisionnelle (`KbCrossCheck` + `KbSafety`)

- `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:154)]` : Classe `KbCrossCheck` (injectée via Hilt).
- `[EXISTE SUR ANDROID (kb/KbSafety.kt:30-42)]` : Énumération quadrivalente `KbSafetyVerdict` :
  - `ALERT` : Au moins une collision détectée (`totalHits > 0`, `kb/KbCrossCheck.kt:131`).
  - `CLEAN` : Vérification complète achevée sans aucune collision (`isClean`, `kb/KbCrossCheck.kt:145`).
  - `INCOMPLETE` : Contrôle partiel (entrée non résolue ou pilier non exhaustif).
  - `NOT_CHECKED` : Base KB indisponible ou médicament non résolu (`kb/KbCrossCheck.kt:132`).
- `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:418)]` : **Comportement des allergies sans ATC** : Une allergie sans code ATC est évaluée par mot-clé textuel via `inferAtcFromAllergyName` (`kb/KbCrossCheck.kt:818`) et compte avec le statut `CHECKED` dans le rapport global (`KbSafety.pillarStatus(kbUp, allergies.size)`).
- `[EXISTE SUR ANDROID (kb/AllergyKeywords.kt:65,86)]` : **Résolution des défauts de filtrage lexical (UC-ALM-009 / UC-ALM-010)** : L'implémentation utilise désormais `hasExactWord("ains")` et `hasExactWord("statin")` (`AllergyKeywords.kt:65,86`) après extraction par SD-22, excluant les faux positifs ("grains", "nystatine").

### 2.4. Le Moteur de Vulgarisation Thérapeutique (Gemma 4 + `VulgariseRepository`)

- `[EXISTE SUR ANDROID (ai/assistant/VulgariseHelper.kt:100)]` : Prompt système imposant l'explication simple ("expliquer à un enfant de 10 ans") dans la langue de l'appareil (`deviceLang`).
- `[EXISTE SUR ANDROID (ai/assistant/VulgariseRepository.kt:27-92)]` : Cache local stocké dans `vulgarise_cache.json`, accédé par `get(key, lang)` et `save(key, lang, text)` (sauvegarde par écriture directe `cacheFile.writeText(json)` à `:86`).
- `[EXISTE SUR ANDROID (ai/assistant/VulgariseHelper.kt:186-195)]` : `ThrottledTextAppender` régulant l'affichage du streaming de tokens avec un intervalle de **150 ms** (`intervalMs = 150L`).

### 2.5. Les 21 Outils Typés `@Tool` de Jemma

`[EXISTE SUR ANDROID (ai/JemmaTools.kt:4-41,185-709)]` : L'interface d'outils exposée à Gemma 4 comprend exactement **21 méthodes `@Tool`** réparties en 6 familles :

| Famille | Méthode `@Tool` | Rôle clinique & Signature réelle |
| :--- | :--- | :--- |
| **🔍 Recherche KB** | 1. `resolveDrug(name: String)` | Nom libre ➔ ATC, RxNorm, display (`:186`) |
| | 2. `resolveAllergy(name: String)` | Nom allergène ➔ SNOMED CT, display, catégorie (`:229`) |
| | 3. `resolveByCode(code: String, system: String)` | Lookup par code et système terminologique (`:266`) |
| | 4. `searchCodes(query: String, category: String)` | Recherche FTS5 floue autocomplétion (`:307`) |
| **💊 Interactions** | 5. `checkDdi(drugA: String, drugB: String)` | DDI sur noms libres via résolution (`:356`) |
| | 6. `checkDdiByAtc(atcA: String, atcB: String)` | DDI direct sur codes ATC (`v_ddi_emergency`, `:373`) |
| | 7. `getAtcAncestors(atc: String)` | Parcours arborescence hiérarchique ATC (`:392`) |
| **👤 Profil Focus** | 8. `getFocusProfileSummary()` | Démographie et totaux par pilier (`:427`) |
| | 9. `getFocusProfileAllergies()` | Liste des allergies du patient sous revue (`:452`) |
| | 10. `getFocusProfileMedications()` | Liste des traitements en cours (`:461`) |
| | 11. `getFocusProfileConditions()` | Diagnostics et problèmes actifs (`:470`) |
| | 12. `getFocusProfileImmunizations()` | Historique vaccinal (`:479`) |
| | 13. `getFocusProfileProcedures()` | Actes et interventions chirurgicales (`:503`) |
| | 14. `getFocusProfileDevices()` | Implants et dispositifs médicaux UDI (`:526`) |
| | 15. `getFocusProfileResults()` | Analyses de biologie et groupe sanguin (`:549`) |
| | 16. `getFocusProfilePastProblems()` | Antécédents médicaux passés / résolus (`:577`) |
| **🎯 Contrôle Maître** | 17. `checkOneDrugAgainstFocusProfile(drugName: String)` | Contrôle maître d'une molécule contre le profil (`:607`) |
| | 18. `checkOneAtcAgainstFocusProfile(atc: String, display: String)` | Contrôle maître rapide sur code ATC (`:625`) |
| **🖥️ Interface UI** | 19. `triggerRedAlert(title: String, body: String)` | Déclenche écran rouge plein écran avec alarme (`:668`) |
| | 20. `triggerToast(message: String, severity: String)` | Émet notification non-bloquante (info/warning/critical, `:679`) |
| **⏰ Utilitaire** | 21. `getCurrentDateTime()` | Horodatage local ISO 8601 fuseau courant (`:699`) |

### 2.6. Canaux de Transfert Multi-Supports Hors-Ligne

- `[EXISTE SUR ANDROID (qr/JemmaPayloadCodec.kt:57,189)]` : **Canal 1 (QR Compact `_j2`)** : Format `_j2:<base64(deflate-raw(json))>` (RFC 1951), encodage via `EncodeResult` et décodage via `DecodeResult` (`:122`).
- `[EXISTE SUR ANDROID (qr/JemmaTextPayloadBuilder.kt:67,152)]` (`MAX_BYTES`, `build`) : **Canal 2 (QR Texte Universel)** : Texte clair 25 langues, plafonné à **1800 octets UTF-8** (`MAX_BYTES = 1800`), avec éviction par rangs (du rang 12 vaccinations au rang 1 allergies) et marqueur `✂️ …`.
- `[EXISTE SUR ANDROID (qr/JemmaQrFrameSplitter.kt:94, qr/JemmaQrFrameAssembler.kt:26,65)]` : **Canal 3 (FHIR Slideshow)** : Trames `JF:i/N|<data>` avec index **1-based** (`1..N`), découpées via `split(payload, maxSingle, frameChunk)` et reconstituées via `offer(raw: String?): Result`.
- `[EXISTE SUR ANDROID (sos/JemmaSosChunkCodec.kt:171-175, mesh/codec/EventChunk.kt:8, sos/JemmaNearbyEndpointCodec.kt:51)]` (`MAX_CHUNK_BYTES`, `victim_sid`, `MAX_ENDPOINT_NAME_LEN`) : **Canaux 4a & 4b (Transferts Sans-Fil SOS & Triage)** :
  - **Canal 4a (Balise SOS BLE)** : Profil d'urgence `_j 1.2` découpé en trames binaires de **200 octets max** via BLE 5.0 Extended Advertising (`MAX_CHUNK_BYTES = 200`, `sos/JemmaSosChunkCodec.kt:171-175`, diffusé par `sos/JemmaSosBleAdvertiser.kt:60`).
  - **Canal 4b (Réseau Maillé de Triage Nearby)** : Statut et événements SALT relayés sous forme d'événements texte `E|<source>|<victim>|<status>|<rescuer>|<ts>|<ttl>|<seq>` par **Google Nearby Connections** (`mesh/codec/EventChunk.kt:8`, `mesh/relay/RelayManager.kt:12`), soumis à la limite stricte de **131 octets UTF-8** (`MAX_ENDPOINT_NAME_LEN = 131`, `sos/JemmaNearbyEndpointCodec.kt:51`).
- `[EXISTE SUR ANDROID (sos/JemmaEmergencyWidget.kt:24, sos/JemmaWidgetEmergencyService.kt)]` : **Canal 5 (Widget Accueil & Service)** : Sur Android, le déclenchement SOS s'appuie sur un `AppWidgetProvider` et un `ForegroundService` (`JemmaWidgetEmergencyService.kt`), et **non sur un overlay de fenêtre système**.

---

## 3. Spécificités Médicales, Réglementaires et Linguistiques Japonaises

### 3.1. Écosystème de santé : MHLW, PMDA et statut Dispositif Médical (SaMD)

- `[HYPOTHÈSE À VÉRIFIER]` : **Adoption de FHIR par le MHLW (厚生労働省)** : Le MHLW promeut le profil **JP Core** (développé par NeXEHRS / HL7 Japan : [NeXEHRS / JP Core v1.1.2](https://jpfhir.jp/)) dans le cadre du programme national *Medical DX*. L'obligation légale universelle de ce standard pour les applications d'urgence non hospitalières reste une hypothèse réglementaire en cours d'évaluation.
- `[HYPOTHÈSE À VÉRIFIER]` : **Réglementation PMDA & Statut SaMD (Software as a Medical Device)** :
  Au Japon, selon la loi sur les dispositifs médicaux et les produits pharmaceutiques (PMD Act / 医薬品医療機器等法 - 薬機法 : [PMDA SaMD Regulatory Info](https://www.pmda.go.jp/english/review-services/regulatory-info/0002.html)), tout logiciel fournissant une aide à la décision clinique (Clinical Decision Support) ou calculant des contre-indications médicamenteuses peut être classé comme **dispositif médical logiciel (SaMD)** soumis à l'homologation de la PMDA (*Pharmaceuticals and Medical Devices Agency* - 医薬品医療機器総合機構).
  - *Conséquence pour le portage iOS* : L'application doit intégrer un avertissement médical formel de non-responsabilité (simple aide d'appoint non contraignante) ou se conformer au processus d'enregistrement SaMD de Classe I/II au Japon.

### 3.2. Nomenclatures et Pharmacopée : Noms en Katakana, Codes HOT/YJ et Classes ATC

- `[EXISTE SUR ANDROID (kb/KnowledgeBaseService.kt:153-164)]` : La normalisation actuelle des noms commerciaux japonais repose sur un bloc `when` codé en dur (10 branches, 9 noms distincts de molécules en anglais) normalisant textuellement vers **9 noms de molécules en anglais** (les codes ATC ne sont pas renvoyés par ce bloc mais résolus en aval par requête SQLite dans la table `ddinter_drugs` / `terminology`) :
  1. `オーグメンチン` / `オーメンチン` / `オグメンチン` ➔ `"Augmentin"`
  2. `アモキシシリン` ➔ `"Amoxicillin"`
  3. `アスピリン` / `バイアスピリン` / `バファリン` ➔ `"Aspirin"`
  4. `ロキソニン` / `ロキソプロフェン` ➔ `"Loxoprofen"` (`:158`)
  5. `カロナール` / `アセトアミノフェン` ➔ `"Acetaminophen"`
  6. `ワーファリン` ➔ `"Warfarin"`
  7. `クラビット` / `レボフロキサシン` ➔ `"Levofloxacin"`
  8. `アドレナリン` / `エピネフリン` ➔ `"Epinephrine"`
  9. `ボルタレン` ➔ `"Diclofenac"`
- `[EXISTE SUR ANDROID (kb/KnowledgeBaseService.kt:158, kb/AllergyKeywords.kt:63-68)]` (`Loxoprofen`, `NSAID`) vs `[HYPOTHÈSE À VÉRIFIER]` :
  - Dans le code Android, `KnowledgeBaseService.kt:158` normalise `ロキソニン` / `ロキソプロフェン` en `"Loxoprofen"`. Dans `kb/AllergyKeywords.kt:63-68`, le bloc de reconnaissance des AINS associe les mots-clés (`ains`, `ibuprofène`, `naproxène`, `diclofénac`, `kétoprofène`) au code ATC `M01AE01` (Ibuprofène).
  - **Correction nomenclature OMS & Statut Loxoprofène** :
    - Dans l'index officiel de l'OMS ([WHOCC ATC M01AE04](https://www.whocc.no/atc_ddd_index/?code=M01AE04)), le code `M01AE04` correspond à **Fenoprofen**, et **non à Loxoprofen**.
    - L'OMS répertorie le Loxoprofène sous le code ATC topique **`M02AA31`** pour les formes locales (gel, patch, cataplasmes : [WHOCC ATC M02AA31](https://www.whocc.no/atc_ddd_index/?code=M02AA31)).
    - En revanche, il n'existe **aucun code ATC de niveau 5 officiel de l'OMS pour la forme systémique orale dans la classe `M01AE`** (molécule orale largement prescrite au Japon et en Asie de l'Est, enregistrée sous [KEGG Drug Entry D01709](https://www.kegg.jp/entry/D01709) / JAPIC).
  - `[HYPOTHÈSE À VÉRIFIER]` : Pour le portage iOS ciblant le Japon, le Loxoprofène oral doit être rattaché soit à la classe ATC parent `M01AE` (dérivés de l'acide propionique), soit directement aux tables nationales japonaises de codes **HOT** ([MEDIS-DC Master Standard](https://www.medis.or.jp/2_kaihatu/kizyun/kizyun.html)) et **YJ** ([MHLW Drug Tariff List](https://www.mhlw.go.jp/topics/2024/04/tp20240401-01.html)).
- `[PROPOSITION IOS]` : Pour le portage iOS, intégrer les tables officielles des codes **HOT** (9/13 chiffres, MEDIS-DC) et **YJ** (tarification nationale MHLW) pour une couverture exhaustive des prescriptions japonaises.

### 3.3. Carnet de santé (*Okusuri Techou*) et Restrictions Légales du My Number

- `[HYPOTHÈSE À VÉRIFIER]` : Ingestion des codes-barres JAHIS figurant sur les carnets de santé médicamenteux japonais (*Okusuri Techou* - お薬手帳, [JAHIS Standards & Specifications](https://www.jahis.jp/standard/)).
- `[HYPOTHÈSE À VÉRIFIER]` : **Restrictions légales strictes du My Number (番号法) et gestion d'identifiants** :
  - **Cadre légal régalien** : En vertu de la *Loi sur l'utilisation des numéros pour identifier une personne spécifique dans les procédures administratives* (番号法 - [*Act on the Use of Numbers to Identify a Specific Individual in the Administrative Procedure*, Loi n° 27 du 31 mai 2013, e-Gov](https://elaws.e-gov.go.jp/document?lawid=425AC0000000027)), la collecte, le stockage, l'utilisation ou la transmission du **numéro individuel à 12 chiffres (My Number)** sont expressément interdits en dehors des institutions publiques et des employeurs pour la fiscalité/sécurité sociale, sous peine de **sanctions pénales sévères**.
  - **Règle impérative JemmaPass** : L'application a l'interdiction absolue de collecter le numéro My Number à 12 chiffres brut. Seuls les identifiants d'assurance maladie décorrélés du numéro régalien ou les identifiants hospitaliers privés peuvent être gérés dans `ids` (`JIdentifier`).
  - **Mécanisme technique de validation regex sous iOS** : Détection et rejet automatique à la saisie de tout identifiant à 12 chiffres respectant l'algorithme modulus 11 spécifique au My Number.

### 3.4. Recherche FTS5 CJK (Trigramme) vs Analyseurs Morphologiques

- `[EXISTE SUR ANDROID (kb/KnowledgeBaseService.kt:821-825,849-850)]` : L'application utilise la table virtuelle SQLite `terminology_cjk` configurée avec le tokeniseur `trigram` (`tokenize='trigram case_sensitive 0'`) dès lors qu'un caractère CJK est détecté dans la requête.
- `[PROPOSITION IOS]` : Sous iOS, SQLite supporte également le tokeniseur `trigram` via le framework SQLite 3.34+ sous GRDB.swift ([SQLite FTS5 Trigram Tokenizer](https://www.sqlite.org/fts5.html#the_trigram_tokenizer)). En complément, l'API native `NLTokenizer` (Natural Language framework d'Apple) peut être évaluée pour le découpage morphologique du japonais sans espaces.

### 3.5. Tri de Catastrophe : Protocole SALT adapté et Grille Réelle

`[EXISTE SUR ANDROID (triage/SaltCode.kt:36-51, triage/StatusResolver.kt:57,70,124)]` (`WAIT`, `DCD_GRACE_SEC`, `shouldOverwrite`, `apply`) : Les 6 codes et couleurs réels du moteur SALT sont :

| Code SALT | Définition dans JemmaPass | Emoji | Couleur Réelle Hex | Rôle clinique & Règle de résolution |
| :--- | :--- | :---: | :--- | :--- |
| `WAIT` | Awaiting assessment | ⏳ | **Gris (`#9E9E9E`)** | Blessé ambulatoire / attente |
| `EVAL` | Under active evaluation | 🔍 | **Jaune (`#FFC107`)** | Évaluation des constantes en cours |
| `STAB` | Stabilized | ✅ | **Vert (`#4CAF50`)** | Stabilisé, pas de besoin vital immédiat |
| `HELP` | Needs help right now | 🆘 | **Rouge (`#F44336`)** | Détresse vitale, intervention urgente |
| `EVAC` | Ready for evacuation | 🚑 | **Bleu (`#2196F3`)** | Prêt pour évacuation prioritaire |
| `DCD` | Deceased | 🕊️ | **Noir (`#000000`)** | Décédé (fenêtre de grâce `DCD_GRACE_SEC = 30L`, rétrogradation permise si `incoming.timestampSec > existing.timestampSec + 30` ou `isExplicitOverride == true`) |

- `[HYPOTHÈSE À VÉRIFIER]` : **Alignement sur le Triage Tag japonais** : Le protocole officiel japonais de catastrophe (DMAT) s'appuie sur la méthode START / PAT à 4 couleurs (Rouge I, Jaune II, Vert III, Noir 0). La grille SALT à 6 statuts ci-dessus (incluant WAIT gris, STAB vert et EVAC bleu) constitue une adaptation logicielle propre à JemmaPass et non une copie stricte de la fiche papier japonaise.

---

## 4. Cahier des Charges Architectural du Portage iOS

### 4.1. Stratégie d'implémentation : KMP partagé vs 100% Swift Natif

```
┌────────────────────────────────────────────────────────────────────────┐
│                   COMPARAISON DES STRATÉGIES PORTAGE                   │
├──────────────────────────┬─────────────────────────────────────────────┤
│ CRITÈRE                  │ OPTION A : MODULE KMP PARTAGÉ (COEUR)       │
├──────────────────────────┼─────────────────────────────────────────────┤
│ Réutilisation du Code    │ Estimation ~70% du code métier partagé      │
│                          │ (Codecs, Modèle) [HYPOTHÈSE À VÉRIFIER]     │
│ Maintenance à long terme │ Évolution synchronisée Android / iOS        │
│ Accès Matériel iOS       │ Ponts Kotlin/Native requis                  │
├──────────────────────────┼─────────────────────────────────────────────┤
│ CRITÈRE                  │ OPTION B : COUCHE APPLICATIVE SWIFTUI       │
├──────────────────────────┼─────────────────────────────────────────────┤
│ Interface Utilisateur    │ 100% SwiftUI native (Human Interface Apple) │
│ Inférence LLM & Vision   │ Intégration directe Apple Silicon / Vision  │
│ Intégration Système      │ Widgets, ActivityKit, Live Activities       │
└──────────────────────────┴─────────────────────────────────────────────┘
```

- `[PROPOSITION IOS]` : Architecture recommandée :
  - **Module KMP partagé (`JemmaCore.xcframework`)** : Modèles `JemmaProfileJ`, parsing FHIR, codecs `_j2` (deflate-raw RFC 1951), algorithmes d'éviction du QR texte 1800 octets (`JemmaTextPayloadBuilder`), résolveur SALT (`StatusResolver`).
  - **Application Swift 6 / SwiftUI** : Interface utilisateur, accès SQLite via **GRDB.swift**, Vision Framework, capture audio et widgets.

### 4.2. Matrice d'Équivalence Technologique Complète (Android ➔ iOS)

| Composant | Implémentation Android (Vérifiée) | Équivalent Recommandé iOS | Catégorie |
| :--- | :--- | :--- | :--- |
| **Langage & UI** | Kotlin 2.x + ViewBinding / Compose | Swift 6 + SwiftUI | `[PROPOSITION IOS]` |
| **Injection** | Hilt (Dagger) + KSP | Factory / Swift Dependencies | `[PROPOSITION IOS]` |
| **Base KB & FTS5** | requery/sqlite-android (libsqliteX.so) | **GRDB.swift** ([SQLite FTS5 Trigram Tokenizer](https://www.sqlite.org/fts5.html#the_trigram_tokenizer)) | `[PROPOSITION IOS]` |
| **Inférence LLM** | LiteRT-LM (`.litertlm`, C++ runtime) | [Google LiteRT](https://ai.google.dev/edge/litert) OU llama.cpp Metal | `[PROPOSITION IOS]` |
| **Format Modèle** | `.litertlm` (`downloads/JemmaModelCatalog.kt:5`) | Fichiers `.litertlm` ou quantifiés GGUF/CoreML | `[PROPOSITION IOS]` |
| **OCR Caméra** | ML Kit Text Japanese seul (`:16.0.1`) | Apple **Vision Framework** (`VNRecognizeTextRequest`) | `[PROPOSITION IOS]` |
| **Synthèse Vocale** | Android TextToSpeech (25 langues) | **AVSpeechSynthesizer** (Voix japonaises natives) | `[PROPOSITION IOS]` |
| **Génération QR** | ZXing Core 3.5.3 (BitMatrix) | **CoreImage** (`CIQRCodeGenerator`) + CoreGraphics | `[PROPOSITION IOS]` |
| **Lecture QR** | CameraX + ZXing / ML Kit Barcode | **AVFoundation** (`AVCaptureMetadataOutput` natif) | `[PROPOSITION IOS]` |
| **Génération PDF** | Android `PdfDocument` | **UIKit / PDFKit** ([`UIGraphicsPDFRenderer`](https://developer.apple.com/documentation/uikit/uigraphicspdfrenderer)) | `[PROPOSITION IOS]` |
| **Mesh P2P (Triage & SOS)** | Google Nearby Connections (`mesh/relay/RelayManager.kt:12`) + BLE Extended Advertising (`sos/JemmaSosBleAdvertiser.kt:60`) | **MultipeerConnectivity** (Triage) + **CoreBluetooth** (GATT SOS) | `[PROPOSITION IOS]` |
| **Déclenchement SOS** | Home AppWidget + Foreground Service | **WidgetKit** (LockScreen) + **App Intents** | `[PROPOSITION IOS]` |
| **Live Alertes** | Notification persistante Foreground | **ActivityKit** (Live Activities / Dynamic Island) | `[PROPOSITION IOS]` |
| **Pass Numérique** | Widget écran d'accueil | **Apple Wallet** (`.pkpass` Pass d'urgence) | `[PROPOSITION IOS]` |
| **Données Santé** | Fichiers privés `<sid>.fhir.json` | Stockage privé + **HealthKit** (Optionnel) | `[PROPOSITION IOS]` |

### 4.3. Base de Données Clinique (`knowledge_full.db`) & Moteur FTS5 sous iOS

- `[EXISTE SUR ANDROID (downloads/JemmaModelCatalog.kt:7,89)]` : La base pèse ~2,0 Go (selon l'en-tête du fichier `:7`) et est déclarée à `3_360_727_040L` octets (~3,13 Go à `:89`).
- `[PROPOSITION IOS]` : Sur iOS, la base ne peut pas être incluse dans le bundle initial de l'application (contrainte de taille et téléchargement App Store). Elle sera téléchargée au premier lancement via `URLSessionDownloadTask` avec support de reprise en arrière-plan depuis `https://jemmapass.net/models/knowledge_full.db`.
- `[HYPOTHÈSE À VÉRIFIER]` : **Limites App Store pour le bundle applicatif** : L'App Store impose une limite de téléchargement cellulaire (OTA) sans Wi-Fi (actuellement 200 Mo par défaut sans avertissement de confirmation) et une limite de taille maximale de binaire IPA décompressé de 4 Go. La distribution de la base de 3,36 Go par téléchargement post-installation in-app est donc techniquement impérative.
- `[PROPOSITION IOS]` : Utilisation de **GRDB.swift** configuré avec SQLite compilé avec les extensions FTS5 pour reproduire fidèlement les index `terminology_cjk` et les requêtes DDI.

### 4.4. Intelligence Artificielle Embarquée sur Apple Silicon

- `[HYPOTHÈSE À VÉRIFIER]` : **Limites de mémoire RAM et contraintes Jetsam sous iOS** :
  - Sur un iPhone standard équipé de 6 Go de RAM (ex: iPhone 15), la limite de mémoire maximale allouable à un processus unique avant terminaison brutale par le démon système **Jetsam** se situe généralement entre 2,8 Go et 3,5 Go.
  - Le modèle `gemma-4-E4B-it.litertlm` pesant ~3,4 Go, son chargement en mémoire active présente un risque très élevé de crash `EXC_RESOURCE (MEMORY)` sur les terminaux à 6 Go.
  - *Stratégie iOS* :
    - Terminaux à 8 Go de RAM unifiée (iPhone 15 Pro, iPhone 16 / 16 Pro) : Support de Gemma 4 E4B.
    - Terminaux à 6 Go de RAM : Déploiement exclusif de Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, ~2,4 Go) avec fenêtre de contexte contrainte.

### 4.5. Le Défi du Réseau P2P : Google Nearby Connections (Android) ➔ MultipeerConnectivity & CoreBluetooth (iOS)

- `[EXISTE SUR ANDROID (mesh/codec/EventChunk.kt:8, mesh/relay/RelayManager.kt:12, sos/JemmaNearbyEndpointCodec.kt:51)]` (`victim_sid`, `JemmaNearbySosService`, `MAX_ENDPOINT_NAME_LEN`) :
  - Sur Android, la propagation maillée des statuts et événements de triage SALT (`E|<source>|<victim>|<status>|<rescuer>|<ts>|<ttl>|<seq>`, <= 131 octets) repose sur l'API **Google Nearby Connections**, tandis que la balise SOS d'urgence (`_j 1.2`) diffuse des trames binaires de 200 octets max via BLE 5.0 Extended Advertising (`sos/JemmaSosBleAdvertiser.kt:60`, `MAX_CHUNK_BYTES = 200`).
- `[PROPOSITION IOS]` : **MultipeerConnectivity pour le relais de triage SALT & Limite d'interopérabilité trans-OS** :
  - Apple ne disposant pas de l'API Google Nearby Connections, le portage iOS utilise le framework natif **`MultipeerConnectivity`** (`MCSession`, `MCNearbyServiceAdvertiser`, `MCNearbyServiceBrowser`) comme équivalent direct hors-ligne pour la découverte et l'échange ad-hoc P2P (Wi-Fi direct et Bluetooth) des trames SALT sans infrastructure réseau entre terminaux iOS.
  - **Limite technique critique d'interopérabilité trans-OS** : Le framework `MultipeerConnectivity` d'Apple n'est **absolument pas interopérable** avec Google Nearby Connections sur Android (les piles réseau et protocoles de découverte diffèrent totalement : un iPhone et un terminal Android ne se voient pas directement et ne peuvent pas échanger de trames SALT en P2P sans passerelle ou pont applicatif dédié).
- `[PROPOSITION IOS]` vs `[HYPOTHÈSE À VÉRIFIER]` : **CoreBluetooth GATT pour l'interopérabilité SOS trans-OS** :
  - Pour garantir la réception des balises SOS émises par Android (`sos/JemmaSosBleAdvertiser.kt:60`) et l'émission depuis iOS vers les scanners Android (`sos/JemmaSosBleScanner.kt:49`), iOS implémente un service GATT périphérique `CoreBluetooth`.
  - `[HYPOTHÈSE À VÉRIFIER]` : Sous iOS, l'API `CBPeripheralManager` impose des restrictions strictes sur l'émission publicitaire en arrière-plan : les identifiants de service sont masqués dans une zone réservée propriétaire et les données publicitaires personnalisées (`CBAdvertisementData`) ne peuvent pas transporter de gros volumes arbitraires lorsque l'application est suspendue. L'émission continue en arrière-plan nécessite une validation empirique de conformité avec les politiques d'Apple.

### 4.6. Expérience Utilisateur & Intégration Écosystème Apple

- `[PROPOSITION IOS]` : **Dynamic Island & Live Activities (ActivityKit)** : Affichage de l'état de l'alerte SOS, du statut SALT et du nombre de secouristes à proximité pendant une situation d'urgence active.
- `[HYPOTHÈSE À VÉRIFIER]` : **SOS depuis un Widget écran de verrouillage verrouillé** : Bien qu'iOS 17+ supporte les boutons interactifs via App Intents dans WidgetKit, l'exécution d'actions lourdes (recherche réseau, alarme sonore continue, émission radio) sans déverrouillage de l'appareil nécessite une validation des politiques de sécurité d'Apple.
- `[HYPOTHÈSE À VÉRIFIER]` : **Pass Apple Wallet (`.pkpass`) et interopérabilité hospitalière** : La génération d'un pass Apple Wallet permet une présentation rapide via double-clic sur le bouton latéral. En revanche, l'affirmation selon laquelle les bornes NFC hospitalières japonaises liraient nativement ce pass comme dossier médical officiel n'est pas vérifiée (les hôpitaux japonais utilisent principalement le système de carte d'assurance My Number ou des lecteurs de QR codes dédiés).
- `[HYPOTHÈSE À VÉRIFIER]` : **Intégration Apple HealthKit "bidirectionnelle"** : L'accès en lecture et écriture aux dossiers cliniques HL7 FHIR sous HealthKit (*Health Records API*) est soumis à un agrément développeur spécifique accordé par Apple (*Clinical Health Records entitlement*) et n'est pas ouvert de façon générale à toutes les applications tierces.

---

## 5. Orchestration & Feuille de Route d'Exécution

### 5.1. Rôles et responsabilités

L'exécution du portage iOS est confiée à des sous-agents spécialisés :
1. **Agent Clinique & Données Japon** : Intégration des nomenclatures de pharmacopée japonaise (HOT, YJ, MHLW), règles d'interaction Katakana, conformité SaMD PMDA et restrictions My Number.
2. **Agent Moteur Partagé & Codecs** : Module KMP partagé (proposé : JemmaCore), codec `_j2`, assembleur de trames QR `JF:i/N` (1..N) et budget 1800 octets.
3. **Agent IA Embarquée & Vision** : Pipeline LiteRT iOS / Core ML, reconnaissance de texte japonais avec Apple Vision, prompt engineering vulgarisation.
4. **Agent Réseau Maillé & SOS** : Protocole CoreBluetooth GATT compatible avec les chunks BLE Android de 200 octets (`MAX_CHUNK_BYTES = 200`, `sos/JemmaSosChunkCodec.kt:171-175`), passerelle pour les événements de triage SALT `E|<source_sid>` de 131 octets (`mesh/codec/EventChunk.kt:8`, `MAX_ENDPOINT_NAME_LEN`, `sos/JemmaNearbyEndpointCodec.kt:51`), machine à états SALT LWW : `shouldOverwrite` (`StatusResolver.kt:70`) et `apply` (`StatusResolver.kt:124`).
5. **Agent SwiftUI & Intégration Système** : Interface SwiftUI, Live Activities, Lock Screen Widgets.

### 5.2. Plan par jalons (Milestones M1 à M5)

| Jalon | Titre | Livrables Principaux | Échéance |
| :--- | :--- | :--- | :--- |
| **M1** | **Module KMP Core & Codecs** | `JemmaCore.xcframework` (Modèles `JemmaProfileJ`, compression `_j2`, parseur trames `JF:i/N`, tests croisés JVM/iOS). | S+2 |
| **M2** | **Base Clinique & Moteur Décisionnel** | GRDB.swift avec FTS5, import de `knowledge_full.db`, portage du moteur `KbCrossCheck` et des règles quadrivalentes. | S+5 |
| **M3** | **OCR Vision & Inférence IA** | Intégration de Vision Framework (Katakana/Kanji), exécution de Gemma 4 ([Google LiteRT](https://ai.google.dev/edge/litert) / Metal), portage des 21 `@Tool`. | S+8 |
| **M4** | **Canaux de Transfert & Réseau P2P** | Scanner QR AVFoundation, export QR texte 1800B, MultipeerConnectivity pour le relais de triage SALT `[PROPOSITION IOS]` et service CoreBluetooth GATT interopérable avec Android. | S+10 |
| **M5** | **UI SwiftUI & Intégration Système** | Application SwiftUI complète, Live Activities, Lock Screen Widgets, validation des contraintes Jetsam sur iPhone. | S+13 |

### 5.3. Matrice d'assurance qualité & cas d'usage critiques

Le portage iOS devra valider l'ensemble des scénarios critiques documentés dans `qa/usecases/` :
- **UC-SAFE-KB** : Interdiction absolue d'émettre le verdict `CLEAN` si la base est absente ou si le médicament candidat n'est pas résolu (`verdict`, `kb/KbCrossCheck.kt:129-135`).
- **UC-DDI** : Résolution des médicaments japonais (Loxonine, Lixiana) et détection systématique de l'alerte rouge `ALERT` en cas d'interaction majeure (Edoxaban × Aspirine / AINS, sous réserve de présence dans la base locale).
- **UC-ALM** : Détection des allergies croisées par classe ATC (Kurodo : Pénicilline J01C × Augmentin J01CR02).
- **UC-ALM-FIX** : Implémentation d'un filtrage lexical rigoureux par frontières de mots sur iOS pour éliminer les faux positifs historiques (grains/AINS, nystatine/statines).
- **UC-QR-TEXT** : Plafond strict de 1800 octets UTF-8 pour le QR texte universel (`QR_MAX_SINGLE = 1800`), avec éviction prioritaire propre et marqueur `✂️ …`.
- **UC-BLOOD** : Synchronisation inviolable entre le groupe sanguin (`p.bt`) et l'Observation LOINC 882-1 (`rs`, `ips/IpsBloodGroup.kt:21-188`).
- **UC-LEGAL-JP** : Contrôle bloquant interdisant la saisie ou la mémorisation d'un numéro My Number à 12 chiffres conformément à la loi japonaise (番号法, [Loi n° 27 du 31 mai 2013, e-Gov](https://elaws.e-gov.go.jp/document?lawid=425AC0000000027)).

---

## 6. Sources citées — Vérifications Mécaniques

Conformément à la directive du message 0047, l'intégralité des affirmations a fait l'objet de vérifications en ligne et de preuves enregistrées par script.

👉 **Consulter la table complète et les verdicts mécaniques dans : [docs/sources/INDEX.md](sources/INDEX.md)**.

### Synthèse des Rectifications Apportées
1. **Loxoprofène ATC oral (`M01AE19`)** : L'index ATC/DDD de l'OMS classe bien le loxoprofène oral sous `M01AE19` (et le loxoprofène topique sous `M02AA31`). La mention d'absence de code ATC oral a été corrigée.
2. **JP Core FHIR (`https://jpfhir.jp/`)** : L'URL officielle est celle de NeXEHRS / HL7 Japan (`https://jpfhir.jp/`), remplaçant l'URL erronée `j-core.org`.
3. **MultipeerConnectivity Apple** : Non interopérable avec Google Nearby Connections sur Android (nécessite le canal BLE GATT ouvert ou une passerelle).
