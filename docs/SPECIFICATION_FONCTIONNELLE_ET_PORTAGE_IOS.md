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
   - 2.2. Dualité des formats : FHIR R4 (Source de Vérité) vs `_j 1.2` (Projection Compacte)
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
   - 4.5. Le Défi du Réseau Maillé P2P (BLE CoreBluetooth vs Nearby Connections)
   - 4.6. Expérience Utilisateur & Intégration Écosystème Apple
5. [Orchestration & Feuille de Route d'Exécution](#5-orchestration--feuille-de-route-dexécution)
   - 5.1. Rôles et responsabilités
   - 5.2. Plan par jalons (Milestones M1 à M5)
   - 5.3. Matrice d'assurance qualité & cas d'usage critiques

---

## 1. Vision Stratégique & Contexte Japon

### 1.1. La promesse JemmaPass : *Zero-Cloud at Runtime, On-Device, Cross-Border*

- `[EXISTE SUR ANDROID (downloads/JemmaModelCatalog.kt:33)]` : **Modèle de distribution hybride** : Le runtime fonctionne à 100% hors-ligne lors des interventions d'urgence. En revanche, l'installation ou la mise à jour initiale de la base clinique (`knowledge_full.db`, ~2,0 Go) et des modèles de langage (`gemma-4-E2B-it.litertlm`, ~2,4 Go ou `gemma-4-E4B-it.litertlm`, ~3,4 Go) s'effectue via téléchargement HTTP depuis `https://jemmapass.net/models/`.
- `[EXISTE SUR ANDROID (profiles/ProfilesRepository.kt:23-28)]` : **Standard International HL7 FHIR R4 IPS** : Le document maître persisté est le Bundle FHIR R4 IPS (`<sid>.fhir.json`), projeté en format court `_j 1.2` (`<sid>.json`).
- `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:130-135)]` : **Contrôle Pharmacologique Embarqué Déterministe** : Détection des interactions médicamenteuses majeures et modérées (DDInter 2.0 via `v_ddi_emergency`) et des allergies croisées sans appel réseau.
- `[HYPOTHÈSE À VÉRIFIER]` : **Conformité réglementaire APPI / RGPD / HIPAA** : Bien que le stockage soit strictement local (on-device sans serveur central), la conformité formelle aux lois de protection des données de santé (APPI au Japon, RGPD en Europe, HIPAA aux USA) nécessite un audit juridique certifié, notamment en raison de la sensibilité des données médicales d'urgence.

### 1.2. Le quatuor narratif : Kurodo, Haru, Kamekichi, Gemma

Le système a été conçu autour de 4 personas :
- 🚶‍♂️ **Kurodo** : Pèlerin étranger (Belge). Allergie létale à la pénicilline (`SNOMED 91936005`). Risque vital immédiat si administration d'Augmentin (`ATC J01CR02`).
- 👵 **Haru** : Citoyenne japonaise (80 ans). Sous anticoagulant oral direct Edoxaban (`ATC B01AF03` / Lixiana).
  > `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:131)]` : Toute association d'Edoxaban avec un antiagrégant (ex: Aspirine) ou un AINS est détectée dans la vue `v_ddi_emergency` et produit impérativement le verdict `ALERT` (écran rouge de danger hémorragique). L'affirmation selon laquelle cette association serait "sûre" ou "verte" est médicalement erronée et formellement infirmée par le code.
- 🎒 **Kamekichi** : Secouriste bénévole / DMAT. Scanne les médicaments et pass via la caméra hors-ligne et coordonne le tri de catastrophe.
- ✨ **Gemma / Jemma** : Agent IA local exploitant LiteRT-LM, pilotant 21 outils `@Tool` et vulgarisant les alertes dans la langue de l'intervenant.

### 1.3. Pourquoi le portage iOS est vital au Japon

- `[HYPOTHÈSE À VÉRIFIER]` : **Part de marché iOS au Japon (~65–70%)** : Selon les agrégateurs statistiques du marché mobile (ex: StatCounter Global Stats Japan 2024-2025, estimant iOS entre 65% et 68%), le Japon présente une pénétration d'iOS exceptionnellement élevée, y compris chez les seniors équipés par leurs enfants et les soignants.
- `[PROPOSITION IOS]` : Le portage iOS permet d'éliminer la rupture opérationnelle actuelle où seuls les terminaux Android peuvent participer au réseau de triage ou décoder les pass `_j2`.

### 1.4. Scénarios critiques au Japon

- **Catastrophe Naturelle Majeure (ex: Péninsule de Noto 2024, Séisme du Nankai)** : Rupture totale des réseaux télécoms.
  - `[EXISTE SUR ANDROID (sos/JemmaSosBleScanner.kt)]` : Découverte et partage maillé de balises SOS hors-ligne.
  - `[EXISTE SUR ANDROID (qr/JemmaTextPayloadBuilder.kt:152)]` : QR Code lisible sans application par tout smartphone en mode texte traduit.
- **Pèlerinage de Shikoku (Henro - 88 Temples) & Tourisme International** :
  - `[EXISTE SUR ANDROID (qr/JemmaTextPayloadBuilder.kt:109-125)]` : Canal 2 traduisant instantanément le pass en 25 langues (dont japonais, anglais, français, chinois, coréen).

---

## 2. Cartographie Fonctionnelle Complète de l'Existant

### 2.1. Les 18 Piliers de l'International Patient Summary (HL7 FHIR R4)

L'architecture s'aligne sur le standard HL7 FHIR R4 IPS (ISO 27269) :

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
- `[EXISTE SUR ANDROID (qr/JemmaProfileJ.kt:109-160)]` : Les contacts d'urgence sont stockés dans `p.ct` (liste `JContact`) au sein de l'objet patient, et non à la racine du profil.
- `[EXISTE SUR ANDROID (ips/IpsBloodGroup.kt:1-90)]` : Le groupe sanguin déclaré (`p.bt`) est synchronisé avec l'Observation LOINC 882-1 correspondante (`rs`).

### 2.2. Dualité des formats : FHIR R4 vs `_j 1.2`

- `[EXISTE SUR ANDROID (profiles/ProfilesRepository.kt:23-35)]` :
  - **Source de Vérité** : `<sid>.fhir.json` (HL7 FHIR R4 Bundle).
  - **Projection Compacte** : `<sid>.json` (`JemmaProfileJ`, schéma `_j 1.2`).
- `[EXISTE SUR ANDROID (profiles/ProfilesRepository.kt:146,371)]` :
  - **Contrôle de Concurrence** : Le cycle Lecture-Modification-Écriture est protégé par `private val writeMutex = kotlinx.coroutines.sync.Mutex()`.
  - **Nuance technique critique** : Le `Mutex` de coroutines Kotlin est **strictement non-réentrant**. Tout appel imbriqué à `withLock` dans la même coroutine provoque un interblocage (deadlock) immédiat.

### 2.3. Moteur Clinique & Sécurité Décisionnelle (`KbCrossCheck` + `KbSafety`)

- `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:154)]` : Classe `KbCrossCheck` (injectée via Hilt).
- `[EXISTE SUR ANDROID (kb/KbSafety.kt:30-42)]` : Énumération quadrivalente `KbSafetyVerdict` :
  - `ALERT` : Au moins une collision détectée (`totalHits > 0`, `kb/KbCrossCheck.kt:131`).
  - `CLEAN` : Vérification complète achevée sans aucune collision (`isClean = true`, `kb/KbCrossCheck.kt:145`).
  - `INCOMPLETE` : Contrôle partiel (entrée non résolue ou pilier non exhaustif).
  - `NOT_CHECKED` : Base KB indisponible ou médicament non résolu (`kb/KbCrossCheck.kt:132`).
- `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:418)]` : **Comportement des allergies sans ATC** : Une allergie sans code ATC est évaluée par mot-clé textuel et compte avec le statut `CHECKED` dans le rapport global (`KbSafety.pillarStatus(kbUp, allergies.size)`).
- `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:858,878)]` : **Défauts connus de filtrage lexical (UC-ALM-009 / UC-ALM-010)** : L'implémentation actuelle utilise `contains("ains")` et `contains("statin")`, générant de réels faux positifs (ex: l'allergie alimentaire aux "grains" déclenche l'alerte AINS, et "nystatine" déclenche l'alerte statines). Le filtrage par frontières de mots relève d'une correction requise.

### 2.4. Le Moteur de Vulgarisation Thérapeutique (Gemma 4 + `VulgariseRepository`)

- `[EXISTE SUR ANDROID (ai/assistant/VulgariseHelper.kt:100)]` : Prompt système imposant l'explication simple ("expliquer à un enfant de 10 ans") dans la langue de l'appareil (`deviceLang`).
- `[EXISTE SUR ANDROID (ai/assistant/VulgariseRepository.kt:27-92)]` : Cache local stocké dans `vulgarise_cache.json`, accédé par `get(key, lang)` et `save(key, lang, text)`.
- `[EXISTE SUR ANDROID (ai/assistant/VulgariseHelper.kt:186-195)]` : `ThrottledTextAppender` régulant l'affichage du streaming de tokens avec un intervalle de **150 ms** (`intervalMs = 150L`).

### 2.5. Les 21 Outils Typés `@Tool` de Jemma

`[EXISTE SUR ANDROID (ai/JemmaTools.kt:8-41,172-709)]` : L'interface d'outils exposée à Gemma 4 comprend exactement **21 méthodes `@Tool`** réparties en 6 familles :

| Famille | Méthode `@Tool` | Rôle clinique & Signature réelle |
| :--- | :--- | :--- |
| **🔍 Recherche KB** | 1. `resolveDrug(name: String)` | Nom libre ➔ ATC, RxNorm, display (`:172`) |
| | 2. `resolveAllergy(name: String)` | Nom allergène ➔ SNOMED CT, display, catégorie (`:238`) |
| | 3. `resolveByCode(code: String, system: String)` | Lookup par code et système terminologique (`:277`) |
| | 4. `searchCodes(query: String, category: String)` | Recherche FTS5 floue autocomplétion (`:302`) |
| **💊 Interactions** | 5. `checkDdi(drug1: String, drug2: String)` | DDI sur noms libres via résolution (`:348`) |
| | 6. `checkDdiByAtc(atc1: String, atc2: String)` | DDI direct sur codes ATC (`v_ddi_emergency`, `:376`) |
| | 7. `getAtcAncestors(atcCode: String)` | Parcours arborescence hiérarchique ATC (`:405`) |
| **👤 Profil Focus** | 8. `getFocusProfileSummary()` | Démographie et totaux par pilier (`:431`) |
| | 9. `getFocusProfileAllergies()` | Liste des allergies du patient sous revue (`:451`) |
| | 10. `getFocusProfileMedications()` | Liste des traitements en cours (`:477`) |
| | 11. `getFocusProfileConditions()` | Diagnostics et problèmes actifs (`:501`) |
| | 12. `getFocusProfileImmunizations()` | Historique vaccinal (`:517`) |
| | 13. `getFocusProfileProcedures()` | Actes et interventions chirurgicales (`:531`) |
| | 14. `getFocusProfileDevices()` | Implants et dispositifs médicaux UDI (`:539`) |
| | 15. `getFocusProfileResults()` | Analyses de biologie et groupe sanguin (`:549`) |
| | 16. `getFocusProfilePastProblems()` | Antécédents médicaux passés / résolus (`:577`) |
| **🎯 Contrôle Maître** | 17. `checkOneDrugAgainstFocusProfile(drugName: String)` | Contrôle maître d'une molécule contre le profil (`:607`) |
| | 18. `checkOneAtcAgainstFocusProfile(atcCode: String)` | Contrôle maître rapide sur code ATC (`:634`) |
| **🖥️ Interface UI** | 19. `triggerRedAlert(title: String, body: String)` | Déclenche écran rouge plein écran avec alarme (`:668`) |
| | 20. `triggerToast(message: String, severity: String)` | Émet notification non-bloquante (info/warn/crit, `:679`) |
| **⏰ Utilitaire** | 21. `getCurrentDateTime()` | Horodatage local ISO 8601 fuseau courant (`:699`) |

### 2.6. Canaux de Transfert Multi-Supports Hors-Ligne

- `[EXISTE SUR ANDROID (qr/JemmaPayloadCodec.kt:57)]` : **Canal 1 (QR Compact `_j2`)** : Format `_j2:<base64(deflate-raw(json))>` (RFC 1951).
- `[EXISTE SUR ANDROID (qr/JemmaTextPayloadBuilder.kt:67,152)]` : **Canal 2 (QR Texte Universel)** : Texte clair 25 langues, plafonné à **1800 octets UTF-8**, avec éviction par rangs (du rang 12 fonctionnel au rang 1 allergies) et marqueur `✂️ …`.
- `[EXISTE SUR ANDROID (qr/JemmaQrFrameSplitter.kt:11, qr/JemmaQrFrameAssembler.kt:25)]` : **Canal 3 (FHIR Slideshow)** : Trames `JF:i/N|<data>` avec index **1-based** (`1..N`), sans hash ni signature de trame, transportant le JSON FHIR brut.
- `[EXISTE SUR ANDROID (sos/JemmaSosChunkCodec.kt)]` : **Canal 4 (Mesh P2P BLE SALT)** : Paquets limités à 131 octets UTF-8.
- `[EXISTE SUR ANDROID (sos/JemmaEmergencyWidget.kt)]` : **Canal 5 (Widget Accueil & Service)** : Sur Android, le déclenchement SOS s'appuie sur un `AppWidgetProvider` et un `ForegroundService` (`JemmaWidgetEmergencyService.kt`), et **non sur un overlay de fenêtre système**.

---

## 3. Spécificités Médicales, Réglementaires et Linguistiques Japonaises

### 3.1. Écosystème de santé : MHLW, PMDA et statut Dispositif Médical (SaMD)

- `[HYPOTHÈSE À VÉRIFIER]` : **Adoption de FHIR par le MHLW (厚生労働省)** : Le MHLW promeut le profil **JP Core** (développé par NeXEHRS / HL7 Japan) dans le cadre du programme national *Medical DX*. L'obligation légale universelle de ce standard pour les applications d'urgence non hospitalières reste une hypothèse réglementaire en cours d'évaluation.
- `[HYPOTHÈSE À VÉRIFIER]` : **Réglementation PMDA & Statut SaMD (Software as a Medical Device)** :
  Au Japon, selon la loi sur les dispositifs médicaux et les produits pharmaceutiques (PMD Act / 医薬品医療機器等法 - 薬機法), tout logiciel fournissant une aide à la décision clinique (Clinical Decision Support) ou calculant des contre-indications médicamenteuses peut être classé comme **dispositif médical logiciel (SaMD)** soumis à l'homologation de la PMDA (*Pharmaceuticals and Medical Devices Agency* - 医薬品医療機器総合機構).
  - *Conséquence pour le portage iOS* : L'application doit intégrer un avertissement médical formel de non-responsabilité (simple aide d'appoint non contraignante) ou se conformer au processus d'enregistrement SaMD de Classe I/II au Japon.

### 3.2. Nomenclatures et Pharmacopée : Noms en Katakana, Codes HOT/YJ et Classes ATC

- `[EXISTE SUR ANDROID (kb/KnowledgeBaseService.kt:153-166)]` : La normalisation actuelle des noms commerciaux japonais repose sur un bloc `when` codé en dur pour 10 molécules critiques :
  - `オーグメンチン` ➔ Augmentin (`J01CR02`)
  - `アモキシシリン` ➔ Amoxicillin (`J01CA04`)
  - `アスピリン` / `バイアスピリン` / `バファリン` ➔ Aspirin (`B01AC06` / `N02BA01`)
  - `ロキソニン` / `ロキソプロフェン` ➔ Loxoprofen
  - `カロナール` / `アセトアミノフェン` ➔ Acetaminophen (`N02BE01`)
  - `ワーファリン` ➔ Warfarin (`B01AA03`)
  - `クラビット` / `レボフロキサシン` ➔ Levofloxacin (`J01MA12`)
  - `アドレナリン` / `エピネフリン` ➔ Epinephrine (`C01CA24`)
  - `ボルタレン` ➔ Diclofenac (`M01AB05`)
- `[EXISTE SUR ANDROID (kb/KbCrossCheck.kt:862)]` vs `[HYPOTHÈSE À VÉRIFIER]` :
  - Dans le code Android (`kb/KbCrossCheck.kt:862`), la règle interne associe le mot-clé Loxoprofène à `M01AE01` (famille de l'ibuprofène).
  - Dans la nomenclature officielle OMS / KEGG Japon, le Loxoprofène sodique est codé `M01AE04`. L'alignement strict vers `M01AE04` sur iOS est une proposition à valider.
- `[PROPOSITION IOS]` : Pour le portage iOS, intégrer les tables officielles des codes **HOT** (9/13 chiffres, MEDIS-DC) et **YJ** (tarification nationale MHLW) pour une couverture exhaustive des prescriptions japonaises.

### 3.3. Carnet de santé (*Okusuri Techou*) et Restrictions Légales du My Number

- `[PROPOSITION IOS]` : Ingestion des codes-barres JAHIS figurant sur les carnets de santé médicamenteux japonais (*Okusuri Techou* - お薬手帳).
- `[HYPOTHÈSE À VÉRIFIER]` : **Restrictions légales strictes sur la carte et le numéro My Number (マイナンバー)** :
  En vertu de la *Loi sur l'utilisation des numéros pour identifier une personne spécifique dans les procédures administratives* (番号法 - *Act on the Use of Numbers to Identify a Specific Individual in the Administrative Procedure*), la collecte, le stockage, l'utilisation ou la transmission du **numéro individuel à 12 chiffres (My Number)** sont expressément interdits en dehors des institutions publiques et des employeurs pour la fiscalité/sécurité sociale, sous peine de **sanctions pénales sévères**.
  - *Règle impérative JemmaPass* : L'application a l'interdiction absolue de collecter le numéro My Number à 12 chiffres brut. Seuls les identifiants d'assurance maladie décorrélés du numéro régalien ou les identifiants hospitaliers privés peuvent être gérés dans `ids` (`JIdentifier`).

### 3.4. Recherche FTS5 CJK (Trigramme) vs Analyseurs Morphologiques

- `[EXISTE SUR ANDROID (kb/KnowledgeBaseService.kt:821-825,849-850)]` : L'application utilise la table virtuelle SQLite `terminology_cjk` configurée avec le tokeniseur `trigram` (`tokenize='trigram case_sensitive 0'`) dès lors qu'un caractère CJK est détecté dans la requête.
- `[PROPOSITION IOS]` : Sous iOS, SQLite supporte également le tokeniseur `trigram` via le framework SQLite 3.34+ sous GRDB.swift. En complément, l'API native `NLTokenizer` (Natural Language framework d'Apple) peut être évaluée pour le découpage morphologique du japonais sans espaces.

### 3.5. Tri de Catastrophe : Protocole SALT adapté et Grille Réelle

`[EXISTE SUR ANDROID (triage/SaltCode.kt:36-51)]` : Les 6 codes et couleurs réels du moteur SALT sont :

| Code SALT | Définition dans JemmaPass | Emoji | Couleur Réelle Hex | Rôle clinique |
| :--- | :--- | :---: | :--- | :--- |
| `WAIT` | Awaiting assessment | ⏳ | **Gris (`#9E9E9E`)** | Blessé ambulatoire / attente |
| `EVAL` | Under active evaluation | 🔍 | **Jaune (`#FFC107`)** | Évaluation des constantes en cours |
| `STAB` | Stabilized | ✅ | **Vert (`#4CAF50`)** | Stabilisé, pas de besoin vital immédiat |
| `HELP` | Needs help right now | 🆘 | **Rouge (`#F44336`)** | Détresse vitale, intervention urgente |
| `EVAC` | Ready for evacuation | 🚑 | **Bleu (`#2196F3`)** | Prêt pour évacuation prioritaire |
| `DCD` | Deceased | 🕊️ | **Noir (`#000000`)** | Décédé (fenêtre de grâce 30s) |

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
│ Réutilisation du Code    │ ~70% du code métier partagé (Codecs, Modèle)│
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
| **Base KB & FTS5** | requery/sqlite-android (libsqliteX.so) | **GRDB.swift** (SQLite FTS5 trigram + unicode61) | `[PROPOSITION IOS]` |
| **Inférence LLM** | LiteRT-LM (`.litertlm`, C++ runtime) | LiteRT for iOS (C API) OU llama.cpp Metal | `[PROPOSITION IOS]` |
| **Format Modèle** | `.litertlm` (`downloads/JemmaModelCatalog.kt:5`) | Fichiers `.litertlm` ou quantifiés GGUF/CoreML | `[PROPOSITION IOS]` |
| **OCR Caméra** | ML Kit Text Japanese seul (`:16.0.1`) | Apple **Vision Framework** (`VNRecognizeTextRequest`) | `[PROPOSITION IOS]` |
| **Synthèse Vocale** | Android TextToSpeech (25 langues) | **AVSpeechSynthesizer** (Voix japonaises natives) | `[PROPOSITION IOS]` |
| **Génération QR** | ZXing Core 3.5.3 (BitMatrix) | **CoreImage** (`CIQRCodeGenerator`) + CoreGraphics | `[PROPOSITION IOS]` |
| **Lecture QR** | CameraX + ZXing / ML Kit Barcode | **AVFoundation** (`AVCaptureMetadataOutput` natif) | `[PROPOSITION IOS]` |
| **Génération PDF** | Android `PdfDocument` | **PDFKit** (`UIGraphicsPDFRenderer`) | `[PROPOSITION IOS]` |
| **Mesh P2P** | BLE Advertising + Nearby Connections | **CoreBluetooth** (GATT Custom Service) | `[PROPOSITION IOS]` |
| **Déclenchement SOS** | Home AppWidget + Foreground Service | **WidgetKit** (LockScreen) + **App Intents** | `[PROPOSITION IOS]` |
| **Live Alertes** | Notification persistante Foreground | **ActivityKit** (Live Activities / Dynamic Island) | `[PROPOSITION IOS]` |
| **Pass Numérique** | Widget écran d'accueil | **Apple Wallet** (`.pkpass` Pass d'urgence) | `[PROPOSITION IOS]` |
| **Données Santé** | Fichiers privés `<sid>.fhir.json` | Stockage privé + **HealthKit** (Optionnel) | `[PROPOSITION IOS]` |

### 4.3. Base de Données Clinique (`knowledge_full.db`) & Moteur FTS5 sous iOS

- `[EXISTE SUR ANDROID (downloads/JemmaModelCatalog.kt:7)]` : La base pèse ~2,0 Go.
- `[PROPOSITION IOS]` : Sur iOS, la base ne peut pas être incluse dans le bundle initial de l'application (contrainte de taille et téléchargement App Store). Elle sera téléchargée au premier lancement via `URLSessionDownloadTask` avec support de reprise en arrière-plan depuis `https://jemmapass.net/models/knowledge_full.db`.
- `[PROPOSITION IOS]` : Utilisation de **GRDB.swift** configuré avec SQLite compilé avec les extensions FTS5 pour reproduire fidèlement les index `terminology_cjk` et les requêtes DDI.

### 4.4. Intelligence Artificielle Embarquée sur Apple Silicon

- `[HYPOTHÈSE À VÉRIFIER]` : **Limites de mémoire RAM et contraintes Jetsam sous iOS** :
  - Sur un iPhone standard équipé de 6 Go de RAM (ex: iPhone 15), la limite de mémoire maximale allouable à un processus unique avant terminaison brutale par le démon système **Jetsam** se situe généralement entre 2,8 Go et 3,5 Go.
  - Le modèle `gemma-4-E4B-it.litertlm` pesant ~3,4 Go, son chargement en mémoire active présente un risque très élevé de crash `EXC_RESOURCE (MEMORY)` sur les terminaux à 6 Go.
  - *Stratégie iOS* :
    - Terminaux à 8 Go de RAM unifiée (iPhone 15 Pro, iPhone 16 / 16 Pro) : Support de Gemma 4 E4B.
    - Terminaux à 6 Go de RAM : Déploiement exclusif de Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, ~2,4 Go) avec fenêtre de contexte contrainte.

### 4.5. Le Défi du Réseau Maillé P2P (BLE CoreBluetooth vs Nearby)

- `[HYPOTHÈSE À VÉRIFIER]` : **Faisabilité du BLE Extended Advertising en tâche de fond sous iOS** :
  - Sous iOS, l'API `CBPeripheralManager` impose des restrictions strictes sur l'émission publicitaire en arrière-plan : les identifiants de service sont masqués dans une zone réservée propriétaire et les données publicitaires personnalisées (`CBAdvertisementData`) ne peuvent pas transporter de gros volumes arbitraires lorsque l'application est suspendue.
  - *Solution d'ingénierie proposée* : Mettre en œuvre un service GATT standardisé JemmaPass où le terminal agit comme serveur périphérique BLE en premier plan ou diffuse des trames de 131 octets via des caractéristiques accessibles en lecture/écriture par les pairs Android et iOS.

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
2. **Agent Moteur Partagé & Codecs** : Module KMP partagé (`JemmaCore`), codec `_j2`, assembleur de trames QR `JF:i/N` (1..N) et budget 1800 octets.
3. **Agent IA Embarquée & Vision** : Pipeline LiteRT iOS / Core ML, reconnaissance de texte japonais avec Apple Vision, prompt engineering vulgarisation.
4. **Agent Réseau Maillé & SOS** : Protocole CoreBluetooth GATT compatible avec les chunks Android de 131 octets, machine à états SALT LWW (StatusResolver).
5. **Agent SwiftUI & Intégration Système** : Interface SwiftUI, Live Activities, Lock Screen Widgets.

### 5.2. Plan par jalons (Milestones M1 à M5)

| Jalon | Titre | Livrables Principaux | Échéance |
| :--- | :--- | :--- | :--- |
| **M1** | **Module KMP Core & Codecs** | `JemmaCore.xcframework` (Modèles `JemmaProfileJ`, compression `_j2`, parseur trames `JF:i/N`, tests croisés JVM/iOS). | S+2 |
| **M2** | **Base Clinique & Moteur Décisionnel** | GRDB.swift avec FTS5, import de `knowledge_full.db`, portage du moteur `KbCrossCheck` et des règles quadrivalentes. | S+5 |
| **M3** | **OCR Vision & Inférence IA** | Intégration de Vision Framework (Katakana/Kanji), exécution de Gemma 4 (LiteRT iOS / Metal), portage des 21 `@Tool`. | S+8 |
| **M4** | **Canaux de Transfert & Mesh BLE** | Scanner QR AVFoundation, export QR texte 1800B, service CoreBluetooth GATT interopérable avec Android. | S+10 |
| **M5** | **UI SwiftUI & Intégration Système** | Application SwiftUI complète, Live Activities, Lock Screen Widgets, validation des contraintes Jetsam sur iPhone. | S+13 |

### 5.3. Matrice d'assurance qualité & cas d'usage critiques

Le portage iOS devra valider l'ensemble des scénarios critiques documentés dans `qa/usecases/` :
- **UC-SAFE-KB** : Interdiction absolue d'émettre le verdict `CLEAN` si la base est absente ou si le médicament candidat n'est pas résolu (`kb/KbCrossCheck.kt:130-135`).
- **UC-DDI** : Résolution des médicaments japonais (Loxonine, Lixiana) et détection systématique de l'alerte rouge `ALERT` en cas d'interaction majeure (Edoxaban × Aspirine / AINS).
- **UC-ALM** : Détection des allergies croisées par classe ATC (Kurodo : Pénicilline J01C × Augmentin J01CR02).
- **UC-ALM-FIX** : Implémentation d'un filtrage lexical rigoureux par frontières de mots sur iOS pour éliminer les faux positifs historiques (grains/AINS, nystatine/statines).
- **UC-QR-TEXT** : Plafond strict de 1800 octets UTF-8 pour le QR texte universel, avec éviction prioritaire propre et marqueur `✂️ …`.
- **UC-BLOOD** : Synchronisation inviolable entre le groupe sanguin (`p.bt`) et l'Observation LOINC 882-1.
- **UC-LEGAL-JP** : Contrôle bloquant interdisant la saisie ou la mémorisation d'un numéro My Number à 12 chiffres conformément à la loi japonaise (番号法).
