# Les 7 piliers restants — analyse prête à construire

_Branche `feat/ips-18-pillars-cleanup`, état du code au commit `8a675b3` (2 octobre 2026). Lecture du code uniquement, rien n'a été exécuté._

But : permettre de livrer chacun des 7 piliers encore en « stub » (`PillarRegistry.ALL`, `isActive = false`) en un sprint XP court, tests d'abord, sur le même moule que les 11 piliers actifs.

| Clé `PillarRegistry` | Pilier | Clé `_j` déjà réservée | Ce qui existe déjà |
|---|---|---|---|
| `contacts` | Contacts d'urgence | `p.ct` (`JContact`) | écran complet, catalogue des liens, QR texte, `Patient.contact` dans le Bundle |
| `advanceDirectives` | Directives anticipées | `ad` | rien d'autre que la fiche stub et le compteur |
| `consents` | Consentements | `cs` | idem |
| `goals` | Objectifs / plan de soins | `gl` | idem |
| `encounters` | Séjours et consultations | `en` | idem |
| `occupational` | Profession / mode de vie | `oc` | idem |
| `providers` | Soignants / équipe de soins | `pv` | idem (+ `p.gp`, texte libre du médecin traitant, saisi dans l'identité) |

Convention de ce document : quand un code ou une URL n'a pas pu être confirmé ici, il est marqué **« à vérifier avec le validateur HL7 »**. Aucune URL de profil n'est inventée : quand l'IG IPS n'en définit pas, c'est écrit « pas de profil IPS dédié ».

---

## 0. Le moule d'un pilier terminé (rappel, d'après le code)

Modèles étudiés : ♿ état fonctionnel (`ips/IpsFunctional.kt`) et 🤰 grossesse (`ips/IpsPregnancy.kt`).

| Couche | Ce qu'il faut écrire | Où |
|---|---|---|
| Domaine | `data class Ips<X>` avec `id`, `toJEntry()`, `companion.fromJEntry(e, index)`, `newId()`; objet de statuts avec `normalize()` et `display()` | `ips/Ips<X>.kt` |
| Conteneur | un champ de plus dans `IpsNativePillars` + `isEmpty` + `fromJEntries(...)` (paramètres positionnels : 8 aujourd'hui) | `ips/IpsImmunization.kt` |
| FHIR | constantes `LOINC_SECTION_*`, `TITLE_SECTION_*`, `PROFILE_*`; `<x>Urn(sid, id)`; `<x>FromFhir` / `toFhir`; `<x>Of(bundle)`; `<x>Section(urns)`; ajout dans `nativeOf()` | `ips/IpsFhirCodec.kt` |
| Bundle | liste d'URN, boucle `bundleEntries.add`, section dans `listOfNotNull(...)` | `qr/JemmaFhirBundleBuilder.build()` |
| Stockage | `load<X>` / `save<X>` (via `saveNativePillars`), projection dans `writeProfileFiles`, repli `ifEmpty { fromJ }` dans `readNativePillars`, `fromJEntries` dans `resolveNativePillars`, `ensureInitialScan` et `JemmaFhirBundleBuilder.build` | `profiles/ProfilesRepository.kt` |
| `_j` | champs éventuels en plus dans `JEntryGeneric` (tous `null` par défaut) | `qr/JemmaProfileJ.kt` |
| QR texte | une `Part` (icône, clé de titre, rang de conservation) + clé dans `JemmaTranslations` (25 langues) | `qr/JemmaTextPayloadBuilder.kt` |
| Démo | entrées dans `getDemoNativePillars(sid)` | `qr/JemmaPersonasSeeder.kt` |
| UI | fragment liste + adaptateur + feuille de saisie, ou mode `kind` d'un écran existant; chaînes dans values, en, fr, ja, de, nl, zh-rCN | `ui/profile/...` |
| Câblage | `nav_graph.xml` (`dest_*`, `action_detail_to_*`, `action_gallery_to_*`), `MainActivity.FULL_BLEED_DESTINATION_NAMES`, `PillarRegistry.isActive`, `ProfileDetailFragment` (`render<X>`, table de routage), `PillarsGalleryFragment` | — |
| Tests JVM | `Ips<X>CodecTest` (aller-retour, statuts, projection, section, non-mélange), test QR texte | `app/src/test/.../ips`, `.../qr` |
| QA | ligne dans `PILLARS` de `verify_profiles.py`, option `--expect-<clé>`, étape `T<n>` du README | `qa/device/` |

Règles héritées à respecter :
- **Identifiants** : créé à l'écran → `UUID.randomUUID()`; relu depuis `_j` → UUID v3 de `"<clé>|<index>|<code>|<date>|<libellé>"`; démo → identifiant lisible (`fs-haru-cane`). URN d'entrée = `IpsFhirCodec.stableUrn("$sid|<Ressource>|<genre>|$id")`. `verify_profiles.py` P3 exige un UUID v3 sur chaque `fullUrl`.
- **Appartenance** : le plus sûr est le modèle ♿ — « est dans le pilier ce qui est référencé par la section, et rien d'autre » (`sectionRefs`). Il évite qu'une même ressource soit lue par deux piliers (défaut UC-FHIR-025 déjà rencontré).
- **Affichages de codes** : `Coding.display` = libellé officiel anglais; libellé localisé au rendu. Valider avec `-locale en`.
- **Chaînes vides** : jamais de primitive FHIR vide (correctif du cycle 6).

Limites de `verify_profiles.py` à lever pour les nouveaux piliers (constat dans `verify_pillar`) :
1. P6 exige un profil `*-uv-ips` dans `meta.profile` : il faut une option « pas de profil » pour Consent, Goal, CarePlan, Encounter, CareTeam.
2. P6 exige `status` : `Goal` porte `lifecycleStatus`, `Practitioner` n'a pas de statut.
3. `res_date` ne lit que des champs de premier niveau : `Encounter.period.start`, `effectivePeriod.start` et `Consent.provision.period` sont imbriqués.
4. Deux piliers sur le même type de ressource (`Consent`) : il faut un filtre par section, comme `is_functional`.

Modèle FHIR : la disponibilité des classes `Consent`, `Goal`, `CarePlan`, `Encounter`, `Practitioner`, `PractitionerRole`, `Organization`, `CareTeam` dans `dev.ohs.fhir:fhir-model:1.0.0-beta03` n'a pas pu être contrôlée ici (pas de build). Premier geste de chaque sprint : un test qui construit et sérialise la ressource.

Base de connaissances : le dépôt ne contient pas le dump de la KB (la branche `device-reports` locale ne porte qu'un README). Les seuls `vs_id` cités dans le code ou la doc sont : `problems-`, `procedures-`, `medical-devices-`, `vaccines-`, `allergy-intolerance-`, `allergy-reaction-snomed-ct-ips-free-set`, les `results-*`, `personal-relationship-uv-ips`, `pregnancy-status-uv-ips`, `edd-method-uv-ips`, `pregnancies-summary-uv-ips`. **Avant chaque sprint, le couloir KB (`qa/device/lane-kb.sh`) doit lister les `vs_id` de `ips_valuesets`** pour savoir si un jeu de valeurs utile existe; à défaut, catalogue embarqué (comme `IpsResultCatalog`).

---

## 1. Contacts d'urgence (`contacts`)

### 1.1 IPS / FHIR R4
- Pas de section IPS : c'est `Patient.contact[]` dans la ressource Patient (profil `http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips`, déjà émis).
- Éléments : `contact.name` (le code émet `name.text`), `contact.telecom` (phone / email), `contact.relationship` 0..*, `contact.address`.
- Jeux de valeurs : lien de parenté = HL7 v3 RoleCode (`http://terminology.hl7.org/CodeSystem/v3-RoleCode`), les 39 codes de `IpsRelationshipCatalog` (`personal-relationship-uv-ips` d'après le commentaire du catalogue). Rôle du contact = `http://terminology.hl7.org/CodeSystem/v2-0131` (`C` contact d'urgence, `N` proche) — liaison de base FHIR sur `Patient.contact.relationship`, à vérifier avec le validateur HL7 quand les deux codages sont présents.
- Appartenance : tout `Patient.contact`.

Écarts du code actuel (`JemmaFhirBundleBuilder`, bloc `p.ct`) :
- le lien est écrit en `relationship.text` seulement, alors que `JContact.r` est censé être un code v3 RoleCode;
- `name.text = c.n ?: ""` : un contact avec téléphone seul produit une chaîne vide (UC-FHIR-014);
- l'adresse `adr` n'est pas exportée;
- le téléphone est toujours marqué `use = mobile`.

### 1.2 Modèle, `_j`, identifiants
Pas de nouvelle classe : `JContact {n, r, p, e, adr}` reste la forme stockée. Proposition :

```kotlin
data class IpsContact(           // vue domaine, pure Kotlin, pour le codec et les tests
    val name: String?,           // n
    val relationship: String?,   // r — code v3 RoleCode, sinon texte libre hérité
    val phone: String?,          // p — tel quel, composable
    val email: String?,          // e
    val address: String?,        // adr
    val emergency: Boolean = true, // nouveau : `em` (omis quand vrai)
    val rank: Int? = null,         // nouveau : `o` ordre d'appel (omis si ordre de liste)
)
```
- `_j` : inchangé (`p.ct[]`), champs optionnels `em`, `o` seulement si le propriétaire du produit les veut.
- Identité : les contacts n'ont pas d'`id` (sous-élément de Patient); édition et suppression par position, comme aujourd'hui. Pas d'URN.
- Source de vérité : rester « écrit dans `_j` » comme le pilier patient (le Bundle est régénéré). Le passer en natif n'apporte rien tant que Patient ne l'est pas.

### 1.3 Micro cas d'usage
- **Nominal** — Une fille saisit pour sa mère de 80 ans : nom, lien « Fille », numéro. Enregistrer. La fiche montre le contact; le QR texte porte la ligne `Nom (Fille) +81…` dans la langue du lecteur.
- **Nominal** — Personne seule : un voisin, lien « Voisin(e) ».
- **Alternatif** — Deux contacts : l'ordre de la liste est l'ordre d'appel; pouvoir remonter un contact.
- **Alternatif** — Personne qui lit peu : choisir le contact dans le carnet d'adresses du téléphone (sélecteur système, pas de permission permanente) plutôt que taper un numéro.
- **Alternatif** — Numéro étranger (Japonais en Belgique) : préfixe international conservé, pas de reformatage local.
- **Alternatif** — Le contact ne parle pas la langue du pays : aujourd'hui aucune place pour la langue du contact (question ouverte).
- **Erreur** — Numéro trop court (« 123 ») : aujourd'hui un toast puis enregistrement quand même (UC-PAT-017). Attendu : refus avec message sous le champ, lu par TalkBack.
- **Erreur** — Contact avec adresse seule (accepté aujourd'hui) : en urgence il est inutilisable. Attendu : avertir « aucun moyen de joindre cette personne rapidement ».
- **Erreur** — Double appui sur Enregistrer : un seul contact (`SingleShotGuard`, pas encore branché ici).
- **Erreur** — Le nom commence par « Dr » : le formulaire pose le code `MEDPROVR`, absent des 39 codes du catalogue → le QR texte imprime « (MEDPROVR) ». Attendu : proposer de ranger ce contact dans le pilier Soignants.
- **Urgence** — Le secouriste lit le QR : la ligne contact doit rester composable (pas de points insérés dans le numéro; c'est déjà le cas pour `ct`, contrairement au téléphone du patient).
- **Limite** — Profil d'enfant : le contact est le parent, lien « Mère » / « Père ».

### 1.4 Sécurité et vie privée
- Ne jamais montrer faux : un numéro tronqué ou reformaté; un lien affiché comme code brut.
- Le contact est une tierce personne : son nom et son numéro partent sur un QR lisible par tous. Le dire à la saisie (« cette personne sera visible par celui qui scanne »).
- QR texte : oui (déjà en place, rang 4, juste après les problèmes). PDF : aujourd'hui absent (UC-PDF-010) — à ajouter, une ligne. Mesh SOS : non transporté; ne pas l'ajouter sans décision (diffusion radio large).
- Ne pas exporter l'adresse du contact sur le QR texte.

### 1.5 KB
`personal-relationship-uv-ips` (39 lignes, anglais) d'après `IpsRelationshipCatalog`; les traductions FR/JA sont dans le catalogue embarqué, pas dans la KB. Nom, téléphone, adresse : texte libre.

### 1.6 Réutilisation
Tout existe : `ui/profile/contacts/*`, `dest_contacts`, `action_detail_to_contacts`, `action_gallery_to_contacts`, branche `"contacts"` dans `ProfileDetailFragment` et `PillarsGalleryFragment`. Il suffit de repasser `isActive = true` et de retirer le traitement « stub » de la tuile. Pas de nouvel écran.

### 1.7 Tests
- JVM d'abord : `ContactFhirMappingTest` (lien codé v3 RoleCode + texte; pas de `name.text` vide; téléphone seul; texte hérité `friend` conservé en texte), `ContactFormLogicTest` (numéro trop court refusé; adresse seule signalée; `MEDPROVR` jamais stocké), `QrTextBudgetTest` existant à étendre (contact sans téléphone : pas de ligne vide), `PdfPillarLayoutTest` (ligne contact).
- Démo : Kurodo → Kamekichi, code `FRND`, avec un numéro fictif (aujourd'hui `r = "friend"` et aucun numéro); Haru → une fille, code `DAUC`, numéro japonais fictif (aujourd'hui aucun contact pour une personne de 80 ans); Kamekichi → Kurodo, `FRND`, sans numéro pour garder le cas « pas de téléphone ».
- Device : **T23** — tuile active, création, édition, suppression, numéro trop court refusé, QR texte FR/JA/EN, validateur HL7 0 erreur.
- `verify_profiles.py` : nouveau contrôle P9 « `Patient.contact` ⇄ `p.ct` » (même nombre, mêmes numéros, aucun `name.text` vide), option `--expect-ct`.

### 1.8 Taille et dépendances
**S.** Aucune dépendance. À faire en premier : c'est aussi l'occasion de corriger deux défauts connus (UC-PAT-017, UC-FHIR-014).

---

## 2. Directives anticipées (`advanceDirectives`)

### 2.1 IPS / FHIR R4
- Section IPS : LOINC **42348-3**, titre « Advance Directives » (libellé LOINC « Advance healthcare directives » — à vérifier avec le validateur HL7, il refuse un `display` inexact).
- Ressource : `Consent` (ou `DocumentReference` pour un document numérisé). **Pas de profil IPS dédié** pour ces deux ressources.
- Éléments requis par FHIR R4 sur `Consent` : `status`, `scope` (1..1), `category` (1..*), `patient` (invariant quand `scope = adr`), et **`policy` ou `policyRule`** (invariant `ppc-1`). C'est le point délicat : une déclaration du patient n'a pas de texte réglementaire à citer. Proposition : `policyRule.text = "Patient-reported advance directive"` — à vérifier avec le validateur HL7.
- Jeux de valeurs : `scope` = `adr` dans `http://terminology.hl7.org/CodeSystem/consentscope`; `category` dans `http://terminology.hl7.org/CodeSystem/consentcategorycodes` (`acd` directive anticipée, `dnr` ne pas réanimer, `polst`, `hcd` mandat de santé) — codes à vérifier avec le validateur HL7; `provision.type` = `deny` | `permit`; `provision.code` pour l'acte visé (réanimation, nutrition artificielle…), en SNOMED si la KB en propose, sinon texte.
- Appartenance : strictement les `Consent` référencés par la section 42348-3 (modèle ♿). Indispensable, car le pilier Consentements utilise la même ressource.

### 2.2 Modèle, `_j`, identifiants
```kotlin
object IpsDirectiveCategory { ACD, DNR, POLST, HCD /* mandataire */ ; normalize(); display() }
object IpsDirectiveStatus { ACTIVE = "active", INACTIVE = "inactive" /* révoquée */, DRAFT, ENTERED_IN_ERROR }

data class IpsAdvanceDirective(
    val id: String,
    val category: String,                 // acd | dnr | polst | hcd
    val status: String = "active",
    val decision: String? = null,         // "deny" | "permit" | null (document sans consigne codée)
    val actCode: String? = null,          // acte visé, SNOMED si disponible
    val actText: String? = null,          // libellé libre de l'acte
    val date: String? = null,             // Consent.dateTime — date de signature (YYYY, YYYY-MM, YYYY-MM-DD)
    val validUntil: String? = null,       // provision.period.end
    val holder: String? = null,           // où se trouve l'original (personne, médecin, registre)
    val proxyName: String? = null,        // mandataire / personne de confiance
    val proxyPhone: String? = null,
    val verified: Boolean = false,        // jamais vrai par saisie patient (voir 2.4)
    val note: String? = null,
)
```
- `_j.ad[]` (déjà réservé) : `c` = catégorie, `st` = statut si ≠ `active`, `dt` = date, `ab` = fin de validité (champ « date de fin » existant), `vc` = décision (`deny` / `permit`), `d_display` = libellé de l'acte, `d` = note. Nouveaux champs compacts à ajouter à `JEntryGeneric` : `hl` (détenteur), `n` et `p` (mandataire : nom, téléphone — mêmes lettres que `JContact`).
- URN : `stableUrn("$sid|Consent|directive|$id")`. `fromJEntry` : graine `"ad|$index|$c|$dt|$vc|$d_display"`.

### 2.3 Micro cas d'usage
- **Nominal** — Une personne âgée a signé une déclaration chez son médecin. Elle saisit : type « directive anticipée », date, « l'original est chez le Dr X ». Aucune consigne codée. La fiche affiche « Une directive anticipée existe (déclarée par la personne) ».
- **Nominal** — Mandataire de santé : nom + téléphone de la personne de confiance.
- **Alternatif** — La personne veut noter « pas de réanimation ». L'écran explique en phrases simples que l'app ne remplace pas le document signé, puis demande où se trouve ce document. Sans détenteur renseigné, l'entrée est enregistrée comme « souhait exprimé, sans document ».
- **Alternatif** — Faible aisance numérique : trois gros choix illustrés (« J'ai un document », « J'ai désigné une personne », « Je n'ai rien »), pas de vocabulaire juridique. « Je n'ai rien » n'enregistre rien (ne pas créer une fausse information « aucune directive »).
- **Alternatif** — Aidant qui remplit pour un parent : rappel « ce sont les souhaits de la personne, pas les vôtres »; champ note pour dire qui a saisi.
- **Alternatif** — Non-natif : l'écran dans sa langue; le libellé exporté reste le code + le libellé officiel anglais, traduit à la lecture.
- **Alternatif** — Révocation : statut « révoquée ». L'entrée reste dans l'historique mais disparaît de tous les canaux d'urgence.
- **Erreur** — Date dans le futur → refus (`IsoDateRules.isFuture`). Fin de validité avant la date → refus.
- **Erreur** — Deux directives actives qui se contredisent (réanimer / ne pas réanimer) → blocage à l'enregistrement, pas un simple avertissement.
- **Erreur** — Import par QR d'un profil portant une directive : la marquer « reçue, non vérifiée », ne jamais l'afficher comme active sans confirmation.
- **Urgence** — Le secouriste scanne : il doit lire au plus « directive anticipée déclarée — document chez … — mandataire … tél … », jamais un ordre.
- **Limite** — Directive expirée (`validUntil` dépassé) : traitée comme révoquée dans les canaux d'urgence, avec la mention « expirée ».
- **Limite** — Profil d'un mineur : pilier masqué ou réservé (question ouverte).

### 2.4 Sécurité et vie privée
C'est le pilier le plus risqué pour la personne.
- **Ne jamais montrer faux** : « ne pas réanimer » affiché à tort (entrée révoquée, expirée, importée, saisie par erreur, ou sur le mauvais profil) peut coûter une vie. L'inverse (directive réelle absente) est une atteinte à la volonté, mais réversible : en cas de doute, on n'affiche pas de consigne.
- Toute entrée est **déclarative** : `verified = false`, mention « déclaré par la personne, non vérifié » sur chaque canal. Aucune couleur, icône ou phrase vocale ne doit ressembler à un ordre médical.
- Le format radio SOS réserve déjà un bit `DNR` (drapeau `0x01`) et une criticité `0x03` « DOA / DNR » (`JemmaSosChunkCodec`). Aujourd'hui `flags = 0` partout (`SosBroadcastFragment`, `JemmaWidgetEmergencyService`). **Ne pas alimenter ce bit depuis ce pilier** sans décision écrite du propriétaire du produit : c'est une diffusion à tous les appareils voisins.
- QR texte et PDF : proposition par défaut = une ligne d'existence (« directive déclarée, document chez …, mandataire … »), sans le contenu de la consigne. Affichage de la consigne seulement si la personne l'a demandé explicitement pour ce canal (case à cocher par entrée). Décision du propriétaire du produit requise.
- Assistant Gemma et synthèse vocale : pas d'outil `getFocusProfile…` pour ce pilier dans le premier sprint; si un outil est ajouté, il renvoie une `instruction` interdisant toute formulation prescriptive (même mécanisme que `ExplainSafety`).
- Droit : la validité dépend du pays (Belgique, Japon, France…). L'app ne dit jamais « valable ».

### 2.5 KB
Aucun jeu de valeurs cité dans le code pour ce domaine. Catégories, statuts, décision : catalogue embarqué (EN/FR/JA + les 4 autres langues d'écran). Acte visé : `terminology_codes` catégorie Procedure via le sélecteur existant, ou texte libre. Détenteur, mandataire, note : texte libre.

### 2.6 Réutilisation
- Nouvel écran nécessaire pour le parcours guidé (trois choix, explications). Il peut reprendre la structure liste + feuille de `ui/profile/procedures/*` (date, statut, note) mais pas son sélecteur.
- `FormEditGuards` (`SingleShotGuard`, `IsoDateRules`), `FormA11yHelpers`, `PhoneNumberHelper` pour le mandataire.
- Le codec `Consent` écrit ici sert tel quel au pilier Consentements.

### 2.7 Tests
- JVM d'abord : `IpsAdvanceDirectiveCodecTest` (aller-retour complet et minimal; statuts normalisés; `scope = adr`; `policyRule` présent; section 42348-3; projection `ad`; déterminisme), `AdvanceDirectiveSafetyTest` (révoquée / expirée / importée → absente des canaux d'urgence; contradiction → refus; `verified` jamais vrai par saisie), `AdvanceDirectiveTextQrTest` (ligne d'existence sans consigne par défaut; mention « non vérifié » dans 3 langues; rang de conservation), `IpsConsentPillarsNeverMixTest` (écrit dès ce sprint, complété au sprint Consentements).
- Démo : Haru → 1 directive (`acd`, active, 2024, document « chez sa fille », mandataire = la fille du pilier contacts); Kurodo → 1 entrée révoquée (prouve qu'elle ne sort nulle part); Kamekichi → 0.
- Device : **T24** — seed K1 H1 Ka0; parcours guidé; révocation; QR texte FR/JA/EN (Kurodo : aucune ligne; Haru : ligne d'existence); contradiction refusée; validateur HL7 0 erreur (point clé : `ppc-1`, `scope`, `category`).
- `verify_profiles.py` : ligne `ad` (type `Consent`, section 42348-3, sans profil IPS, date `dateTime`), filtre par section, `--expect-ad`, P6g « `_j.ad[].st` ⇄ `Consent.status` ».

### 2.8 Taille et dépendances
**L.** Dépend de décisions du propriétaire du produit (2.4). Bénéficie du pilier Contacts (mandataire). Débloque Consentements.

---

## 3. Consentements (`consents`)

### 3.1 IPS / FHIR R4
- **Pas de section IPS** pour les consentements, et pas de profil IPS dédié. Ressource `Consent`.
- Section hors IPS à ajouter à la Composition : code LOINC à choisir — `59284-0` « Consent Document » est un candidat, à vérifier avec le validateur HL7 (code et droit d'ajouter une section non prévue par le profil Composition IPS).
- Éléments requis : comme en 2.1 (`status`, `scope`, `category`, `patient`, `policy`/`policyRule`). `scope` = `patient-privacy`, `research` ou `treatment`. `provision.type`, `provision.period`, `performer`/`organization` en texte.
- Appartenance : strictement les `Consent` référencés par la section des consentements; jamais ceux de 42348-3.

### 3.2 Modèle, `_j`, identifiants
```kotlin
data class IpsConsent(
    val id: String,
    val scope: String,              // patient-privacy | research | treatment
    val subject: String? = null,    // objet : "don d'organes", "partage du dossier", "étude X"
    val subjectCode: String? = null,
    val decision: String,           // permit | deny
    val status: String = "active",
    val date: String? = null,       // Consent.dateTime
    val validUntil: String? = null, // provision.period.end
    val organization: String? = null, // auprès de qui (registre, hôpital)
    val note: String? = null,
)
```
- `_j.cs[]` : `c` = code de l'objet, `d_display` = libellé, `ct` = scope (champ « catégorie » existant), `vc` = décision, `st`, `dt`, `ab` = fin, `d` = note, `hl` = organisme (même lettre que le détenteur en 2.2).
- Attention : dans `JemmaProfileJ.kt` le commentaire de `cs` dit « Care services », alors que `ProfileDetailFragment` compte `cs` pour les consentements. À trancher avant d'écrire (voir questions ouvertes).
- URN : `stableUrn("$sid|Consent|consent|$id")`.

### 3.3 Micro cas d'usage
- **Nominal** — « Je suis donneur d'organes » : objet « don d'organes », décision oui, organisme « registre national ».
- **Nominal** — Refus de don d'organes : décision non. Les deux doivent s'afficher sans ambiguïté (« Oui » / « Non » en toutes lettres, pas seulement une couleur).
- **Nominal** — Accord de partage du dossier avec un hôpital, avec date de fin.
- **Alternatif** — Faible aisance numérique : liste courte de sujets préparés (don d'organes, don de sang, partage du dossier, recherche) + « autre ».
- **Alternatif** — Aidant : même rappel qu'en 2.3.
- **Alternatif** — Retrait : statut « retiré », l'entrée reste en historique.
- **Erreur** — Fin avant début; date future → refus.
- **Erreur** — Deux consentements actifs opposés sur le même objet → refus.
- **Erreur** — Import par QR : marqué « reçu, non vérifié ».
- **Urgence** — Le don d'organes n'est pas une information de premier secours : ne pas l'afficher dans le résumé d'urgence (risque de malaise et de mauvaise interprétation par un secouriste).
- **Limite** — Consentement expiré : affiché « expiré », jamais « actif ».

### 3.4 Sécurité et vie privée
- Ne jamais montrer faux : la décision inversée (oui ↔ non), un consentement retiré ou expiré présenté comme actif.
- Données sensibles (participation à une recherche = indice sur une maladie). **Par défaut : ni QR texte, ni PDF, ni mesh.** Uniquement dans le Bundle FHIR complet et à l'écran.
- Même règle de non-vérification qu'en 2.4.

### 3.5 KB
Rien d'identifié. Sujets préparés : catalogue embarqué. Organisme, note : texte libre.

### 3.6 Réutilisation
L'écran et la feuille du pilier Directives, en mode `kind = "consent"` (même mécanisme que `past` / `current` / `functional`) : mêmes champs date, statut, décision, note; l'objet remplace la catégorie; pas de mandataire. Codec `Consent` partagé.

### 3.7 Tests
- JVM : `IpsConsentCodecTest` (aller-retour, `scope`, décision, période, section), `IpsConsentPillarsNeverMixTest` complété (une directive et un consentement dans le même Bundle : chacun relu une fois, dans le bon pilier), `ConsentTextQrTest` (aucune ligne sur le QR texte).
- Démo : Kurodo → don d'organes « oui » (2019); Haru → partage du dossier avec son hôpital, actif; Kamekichi → 0.
- Device : **T26** — seed K1 H1 Ka0, création, retrait, absence sur le QR texte, validateur.
- `verify_profiles.py` : ligne `cs` (`Consent`, section choisie), `--expect-cs`.

### 3.8 Taille et dépendances
**S à M** après le pilier Directives (codec et écran partagés); **M à L** s'il est fait avant.

---

## 4. Objectifs / plan de soins (`goals`)

### 4.1 IPS / FHIR R4
- Section IPS : LOINC **18776-5**, « Plan of care note », titre « Plan of Care ».
- Entrées de section : `CarePlan` (l'IG IPS ne prévoit pas `Goal` comme entrée directe — à vérifier avec le validateur HL7). **Pas de profil IPS dédié** pour `CarePlan` ni pour `Goal`.
- Montage proposé : un seul `CarePlan` par profil (`status = active`, `intent = plan`, `subject`), référencé par la section; `CarePlan.goal[]` → les `Goal`.
- Éléments requis : `Goal.lifecycleStatus` (proposed | planned | accepted | active | on-hold | completed | cancelled | entered-in-error | rejected), `Goal.description` (1..1), `Goal.subject`. Optionnels utiles : `priority` (`http://terminology.hl7.org/CodeSystem/goal-priority` : high-priority / medium-priority / low-priority), `startDate`, `target.dueDate`, `target.measure` + `target.detailQuantity`, `achievementStatus`, `note`.
- Appartenance : les `Goal` référencés par le `CarePlan` de la section 18776-5.

### 4.2 Modèle, `_j`, identifiants
```kotlin
data class IpsGoal(
    val id: String,
    val code: String? = null, val system: String? = IpsCodeSystems.SNOMED,
    val display: String? = null, val text: String? = null,  // description
    val status: String = "active",            // lifecycleStatus
    val priority: String? = null,
    val start: String? = null,
    val due: String? = null,                  // target.dueDate
    val measureCode: String? = null,          // LOINC de la mesure cible (ex. LDL)
    val targetValue: String? = null, val targetUnit: String? = null,
    val setBy: String? = null,                // qui a fixé l'objectif (texte)
    val note: String? = null,
)
```
- `_j.gl[]` : `c`, `cs`, `d_display`, `d`, `st` (si ≠ `active`), `dt` = début, `ab` = échéance, `v` + `u` = cible, `vc` = code de la mesure, `ip` laissé vide; priorité → nouveau champ `pr`.
- URN : `stableUrn("$sid|Goal|$id")`, `stableUrn("$sid|CarePlan")` (un seul, id `careplan-<sid>`).

### 4.3 Micro cas d'usage
- **Nominal** — « Faire baisser mon cholestérol » : texte libre, échéance dans 6 mois, cible LDL < 100 mg/dL (mesure choisie dans `IpsResultCatalog`).
- **Nominal** — Objectif sans chiffre : « Marcher 20 minutes par jour ».
- **Alternatif** — Faible aisance numérique : un seul champ obligatoire (la phrase); tout le reste replié.
- **Alternatif** — Aidant : « Objectif fixé par le Dr X » dans `setBy`.
- **Alternatif** — Objectif atteint : statut « atteint »; il passe en bas de liste.
- **Alternatif** — Non-natif : texte libre dans sa langue, exporté tel quel (`description.text`).
- **Erreur** — Échéance avant le début → refus. Cible non numérique (« bas ») → gardée en texte, pas en `detailQuantity`.
- **Erreur** — Virgule décimale (« 1,5 ») → lue comme décimale (`IpsDecimal.normalize`, déjà en place).
- **Erreur** — Description vide → refus.
- **Urgence** — Sans intérêt immédiat pour un secouriste : hors résumé d'urgence.
- **Limite** — Unité absente de `IpsResultCatalog.UNITS` → texte libre, pas de code UCUM.

### 4.4 Sécurité et vie privée
- Ne jamais montrer faux : une cible affichée comme une valeur mesurée (confusion avec le pilier Résultats). Libellé toujours précédé de « objectif ».
- Risque faible pour la personne. Hors QR texte, PDF et mesh par défaut.

### 4.5 KB
Description : `problems-snomed-ct-ips-free-set` peu adapté → texte libre recommandé. Mesure : `IpsResultCatalog` (31 LOINC embarqués; la KB n'a pas de table LOINC). Unités : `IpsResultCatalog.UNITS`.

### 4.6 Réutilisation
Écran liste + feuille sur le modèle de `ui/profile/results/*` (champ valeur + unité + sélecteur de mesure, déjà écrits dans `ResultFormBottomSheet`). Nouveau fragment léger plutôt qu'un mode `kind` : les champs diffèrent trop de ceux d'un résultat (pas d'interprétation, pas d'intervalle).

### 4.7 Tests
- JVM : `IpsGoalCodecTest` (aller-retour; `lifecycleStatus` normalisé; cible exacte en décimal; un seul `CarePlan`; section 18776-5 → `CarePlan` → `Goal`; aucun `CarePlan` ni section quand la liste est vide; projection `gl`).
- Démo : Kurodo → LDL < 100 mg/dL, échéance 2026-07 (cohérent avec son résultat LDL 131 et la note « recheck in 6 months »); Haru → « Peser chaque matin » (insuffisance cardiaque), actif; Kamekichi → 0.
- Device : **T29** — seed K1 H1 Ka0, création texte libre, cible chiffrée, passage à « atteint », suppression du dernier objectif (plus de section ni de `CarePlan`), validateur.
- `verify_profiles.py` : ligne `gl` (type `Goal`, statut lu dans `lifecycleStatus`, date `startDate`, sans profil IPS), contrôle « section 18776-5 → 1 CarePlan → n Goal », `--expect-gl`.

### 4.8 Taille et dépendances
**M.** Aucune dépendance forte; réutilise le catalogue des résultats.

---

## 5. Séjours et consultations (`encounters`)

### 5.1 IPS / FHIR R4
- **Pas de section IPS** pour les séjours, pas de profil IPS dédié. Ressource `Encounter`.
- Section hors IPS : LOINC `46240-8` (section « Encounters » des documents C-CDA) — à vérifier avec le validateur HL7 (code, libellé, section additionnelle admise).
- Éléments requis FHIR R4 : `status` (planned | arrived | triaged | in-progress | onleave | finished | cancelled | entered-in-error | unknown) et `class` (un `Coding` de `http://terminology.hl7.org/CodeSystem/v3-ActCode` : `AMB` ambulatoire, `EMER` urgences, `IMP` hospitalisation, `HH` domicile, `VR` à distance). Utiles : `subject`, `period.start` / `period.end`, `reasonCode` (SNOMED ou texte), `serviceProvider.display`, `type`.
- Appartenance : strictement les `Encounter` référencés par la section.

### 5.2 Modèle, `_j`, identifiants
```kotlin
data class IpsEncounter(
    val id: String,
    val encounterClass: String = "AMB",   // AMB | EMER | IMP | HH | VR
    val status: String = "finished",
    val start: String? = null, val end: String? = null,   // dates partielles admises
    val reasonCode: String? = null, val reasonSystem: String? = IpsCodeSystems.SNOMED,
    val reasonDisplay: String? = null, val reasonText: String? = null,
    val place: String? = null,            // serviceProvider.display
    val note: String? = null,
)
```
- `_j.en[]` : `ct` = classe si ≠ `AMB`, `st` si ≠ `finished`, `dt` = début, `ab` = fin, `c` + `cs` + `d_display` = motif, `d` = note, `hl` = lieu.
- URN : `stableUrn("$sid|Encounter|$id")`.
- `Encounter` n'a pas de champ `note` en R4 : la note va dans une extension ou est abandonnée — décision à prendre au sprint (proposition : ne pas l'exporter, la garder dans `_j.d`, ce qui casse la règle « le Bundle est la source »; à défaut, `reasonCode[1].text`).

### 5.3 Micro cas d'usage
- **Nominal** — Hospitalisation : classe « hospitalisation », du … au …, motif « infarctus » (sélecteur KB), lieu.
- **Nominal** — Passage aux urgences le mois dernier, motif en texte libre.
- **Alternatif** — Dates floues (« en 1975 ») : année seule acceptée, comme pour les antécédents.
- **Alternatif** — Séjour en cours : statut « en cours », pas de date de fin.
- **Alternatif** — Faible aisance numérique : trois gros boutons (hôpital / urgences / consultation), puis une date, puis « pourquoi ? » en texte libre.
- **Alternatif** — Non-natif, soigné à l'étranger : lieu saisi dans l'écriture d'origine (les seeds de Haru portent déjà des noms d'hôpitaux en japonais).
- **Alternatif** — Lien avec une intervention déjà saisie : proposer de reprendre la date et le lieu d'une `Procedure` existante (simple pré-remplissage, pas de référence FHIR dans le premier sprint).
- **Erreur** — Fin avant début → refus. Début dans le futur avec statut « terminé » → refus.
- **Erreur** — Séjour « en cours » avec une date de fin → refus.
- **Urgence** — Utile au secouriste : « hospitalisé il y a 10 jours pour … ». Une seule ligne, la plus récente.
- **Limite** — Dix ans de consultations : liste triée par date décroissante; le QR texte n'en garde qu'une.

### 5.4 Sécurité et vie privée
- Ne jamais montrer faux : un séjour terminé présenté comme en cours (ou l'inverse); un motif tronqué qui change de sens.
- Le lieu et le motif révèlent des soins sensibles (psychiatrie, oncologie). QR texte : au plus la dernière hospitalisation ou le dernier passage aux urgences de moins de 30 jours, rang de conservation bas (retiré en premier). PDF : non. Mesh : non.

### 5.5 KB
Motif : `problems-snomed-ct-ips-free-set` via `KbConditionPicker` (déjà utilisé par 📜/🩺/♿). Classe et statut : catalogue embarqué (5 + 4 valeurs utiles). Lieu : texte libre.

### 5.6 Réutilisation
Très proche de `ui/profile/procedures/*` (date, statut, lieu, note). Deux voies : (a) mode `kind` de l'écran des interventions avec une seconde date et le sélecteur de motif; (b) mode `kind = "encounter"` de l'écran des antécédents (`pastproblems`), qui a déjà début + fin + `KbConditionPicker`. **(b) est le plus court** : il manque seulement le choix de classe et le lieu.

### 5.7 Tests
- JVM : `IpsEncounterCodecTest` (aller-retour; `class` toujours présent; statuts normalisés vers `finished`; période partielle; section; projection `en`; un motif d'`Encounter` n'entre jamais dans `_j.cn` — même garde que `functionalStatusNeverFeedsTheDrugDiseaseProjection`), `EncounterTextQrTest` (une seule ligne, la plus récente; rien au-delà de 30 jours).
- Démo : Haru → hospitalisation 2015-08-27 → 2015-09 (infarctus, cohérent avec ses antécédents et son pontage); Kamekichi → urgences 2018-06 (fibrillation auriculaire); Kurodo → consultation 2024-02-19 (coloscopie).
- Device : **T27** — seed K1 H1 Ka1, création par les trois boutons, année seule, séjour en cours, erreurs de dates, validateur (point clé : `Encounter.class`).
- `verify_profiles.py` : ligne `en` (type `Encounter`, date lue dans `period.start`, sans profil IPS), `--expect-en`.

### 5.8 Taille et dépendances
**M.** Aucune dépendance. Décision préalable : code de section et sort de la note.

---

## 6. Profession et mode de vie (`occupational`)

### 6.1 IPS / FHIR R4
- Section IPS : LOINC **29762-2**, « Social history note », titre « Social History ».
- Ressources : `Observation`. L'IG IPS définit deux profils dans cette section :
  - tabac — `http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-tobaccouse-uv-ips`, code LOINC `72166-2`, valeur = réponse LOINC du jeu `current-smoking-status-uv-ips`;
  - alcool — `http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-alcoholuse-uv-ips`, code LOINC `74013-4`, valeur = quantité par jour.
  (codes des réponses et unité à vérifier avec le validateur HL7 et dans la KB.)
- **Profession : pas de profil IPS dédié.** `Observation` générique, catégorie `social-history` (`http://terminology.hl7.org/CodeSystem/observation-category`), code LOINC `11341-5` « History of Occupation » — à vérifier avec le validateur HL7. La fiche stub cite LOINC `74166-0` : à vérifier aussi, c'est un code de note et non d'observation. Valeur : `valueCodeableConcept` (ISCO-08 si disponible, sinon `text`), employeur en `component` ou en note, `effectivePeriod`.
- Éléments requis : `status`, `code`, `subject`, `effective[x]` (requis par les deux profils IPS), une valeur.
- Appartenance : les `Observation` référencées par la section 29762-2. À inscrire aussi dans `isResultsObservation` (exclusion), comme pour la grossesse : la catégorie `social-history` n'est pas dans `IpsResultCategory.ALL`, mais un profil ou une catégorie venue d'ailleurs ne doit pas faire entrer ces observations dans les résultats.

### 6.2 Modèle, `_j`, identifiants
Même forme que `IpsPregnancyObs` (un type d'observation par code) :
```kotlin
enum class IpsSocialKind { OCCUPATION, TOBACCO, ALCOHOL }

data class IpsSocialObs(
    val id: String,
    val code: String,                 // 11341-5 | 72166-2 | 74013-4
    val valueCode: String? = null,    // réponse tabac (LA…) ou code de métier
    val valueSystem: String? = null,
    val valueText: String? = null,    // intitulé du métier en clair
    val quantity: String? = null, val unit: String? = null,   // alcool
    val employer: String? = null,     // OCCUPATION seulement
    val start: String? = null, val end: String? = null,
    val note: String? = null,
)
```
- `_j.oc[]` : `c` = code LOINC, `vc` = réponse ou code métier, `v` + `u` = quantité, `d_display` = intitulé, `dt` = début, `ab` = fin, `d` = note, `hl` = employeur.
- URN : `stableUrn("$sid|Observation|social|$id")`. Ids fixes pour tabac et alcool (une seule observation de chaque : `oc-tobacco-<sid>`), comme `rs-blood-group-<sid>`.

### 6.3 Micro cas d'usage
- **Nominal** — « Je suis infirmière de nuit » : intitulé en texte libre, « depuis 2010 ».
- **Nominal** — Tabac : un choix parmi 4 à 5 phrases simples (« Je fume tous les jours », « J'ai arrêté », « Je n'ai jamais fumé »…).
- **Nominal** — Retraité : « Retraitée, ancienne ouvrière du textile » — l'exposition passée compte (amiante, poussières).
- **Alternatif** — Faible aisance numérique : un écran unique, trois blocs (travail, tabac, alcool), chacun facultatif, comme l'écran grossesse.
- **Alternatif** — Sans emploi, étudiant, au foyer : choix préparés, pas de champ employeur.
- **Alternatif** — Non-natif : intitulé du métier dans sa langue, exporté en `text`.
- **Alternatif** — La personne ne veut pas répondre : « Non renseigné » n'enregistre rien (pas d'observation « inconnu » créée par défaut).
- **Alternatif** — Alcool : saisie en verres par jour ou par semaine; la conversion est faite par l'app et affichée avant d'enregistrer.
- **Erreur** — Quantité négative ou non numérique → refus. Fin avant début → refus.
- **Erreur** — « Tout effacer » → plus aucune observation, plus de section (même comportement que la grossesse, T21 étape 5).
- **Urgence** — Peu utile en premier secours, sauf métier à risque (plongeur, soudeur) : hors résumé d'urgence par défaut.
- **Limite** — Plusieurs emplois : une observation par emploi; tabac et alcool restent uniques.

### 6.4 Sécurité et vie privée
- Ne jamais montrer faux : « fumeur » pour un ancien fumeur (influence une décision d'anesthésie); une quantité d'alcool mal convertie.
- Très sensible socialement : employeur, consommation d'alcool. **Par défaut hors QR texte, PDF et mesh.** Le nom de l'employeur ne sort jamais sur un canal lisible par tous.
- Formulation sans jugement dans l'écran (important pour que la personne réponde vrai).

### 6.5 KB
À contrôler par le couloir KB : présence de `current-smoking-status-uv-ips` dans `ips_valuesets` (probable, la KB porte déjà les jeux LOINC de la grossesse). Métiers : aucune table ISCO citée dans le code → texte libre. Unités d'alcool : catalogue embarqué.

### 6.6 Réutilisation
`ui/profile/pregnancy/PregnancyEditFragment` est le bon patron : écran unique sans liste, blocs conditionnels, « Tout effacer », ids stables par code (`idFor(code)`). Nouveau fragment calqué dessus (pas de paramétrage possible : les champs sont trop différents). Catalogue sur le modèle de `IpsPregnancyCatalog`.

### 6.7 Tests
- JVM : `IpsSocialCodecTest` (les trois genres; profils IPS tabac et alcool; `effective[x]` toujours présent, avec `data-absent-reason` si inconnu — helper `unknownDateTimeBuilder` existant; section 29762-2; projection `oc`), extension de `IpsBundleConsistencyTest` (une observation sociale n'est jamais relue comme résultat ni comme grossesse), `IpsSocialCatalogTest` (libellés EN/FR/JA de chaque réponse).
- Démo : Kurodo → métier « Software engineer » depuis 2005 + « n'a jamais fumé »; Haru → « Retired — former textile worker » + ancienne fumeuse; Kamekichi → 0.
- Device : **T28** — seed K2 H2 Ka0, saisie des trois blocs, « Tout effacer », absence sur le QR texte, validateur (point clé : premiers profils `Observation-tobaccouse` / `-alcoholuse`).
- `verify_profiles.py` : ligne `oc` (type `Observation`, filtre par section 29762-2, profil IPS exigé seulement pour 72166-2 et 74013-4), `--expect-oc`; le filtre `rs` doit exclure ces observations.

### 6.8 Taille et dépendances
**M.** Dépend d'une vérification KB. Le nom du pilier (« Occupational ») est plus étroit que la section IPS (« Social history ») : à trancher.

---

## 7. Soignants / équipe de soins (`providers`)

### 7.1 IPS / FHIR R4
- **Pas de section IPS** pour l'équipe de soins. L'IG IPS définit en revanche des profils pour les acteurs : `http://hl7.org/fhir/uv/ips/StructureDefinition/Practitioner-uv-ips`, `.../PractitionerRole-uv-ips`, `.../Organization-uv-ips`.
- Médecin traitant : `Patient.generalPractitioner` → `Practitioner` (ou `PractitionerRole`). C'est le seul ancrage prévu par le profil Patient.
- Autres soignants : `CareTeam` (pas de profil IPS dédié) avec `participant.member` → `PractitionerRole`, dans une section hors IPS; LOINC `85847-2` « Patient Care team information » — à vérifier avec le validateur HL7.
- Éléments : `Practitioner.name` (requis par le profil IPS — à vérifier), `telecom`; `PractitionerRole.practitioner`, `.organization`, `.code` (rôle), `.specialty`, `.telecom`; `Organization.name`.
- Jeux de valeurs : rôle et spécialité — l'IG IPS pointe vers des jeux de valeurs dont le nom exact n'a pas pu être confirmé ici (à vérifier avec le validateur HL7); texte libre en repli.
- Appartenance : les `PractitionerRole` membres du `CareTeam` de la section, plus celui référencé par `Patient.generalPractitioner`.

### 7.2 Modèle, `_j`, identifiants
```kotlin
data class IpsProvider(
    val id: String,
    val name: String,                 // nom du soignant, ou du service si pas de personne
    val role: String? = null,         // "gp" | code de rôle | null
    val specialty: String? = null,    // code ou texte
    val organization: String? = null,
    val phone: String? = null, val email: String? = null,
    val isGp: Boolean = false,        // un seul par profil
    val note: String? = null,
)
```
- `_j.pv[]` : `n` = nom, `p` = téléphone, `e` = e-mail (lettres de `JContact`), `r` = rôle, `d_display` = spécialité, `c` + `cs` si codée, `hl` = organisation, `st = "gp"` proscrit — utiliser un champ dédié `gp: 1` (omis sinon), `d` = note.
- Attention : le commentaire de `pv` dans `JemmaProfileJ.kt` dit « Provenance / signature trail », alors que `ProfileDetailFragment` compte `pv` pour les soignants. À trancher.
- `p.gp` (texte libre saisi dans l'identité, **non exporté dans le Bundle aujourd'hui**) : à migrer vers l'entrée `isGp = true` au premier enregistrement, puis à garder comme projection (nom du médecin traitant) pour les lecteurs existants.
- URN : `stableUrn("$sid|Practitioner|$id")`, `stableUrn("$sid|PractitionerRole|$id")`, `stableUrn("$sid|Organization|<nom normalisé>")` (une organisation partagée par plusieurs soignants), `stableUrn("$sid|CareTeam")`.

### 7.3 Micro cas d'usage
- **Nominal** — Médecin traitant : nom, téléphone du cabinet, case « c'est mon médecin traitant ».
- **Nominal** — Cardiologue à l'hôpital : nom, spécialité, hôpital.
- **Alternatif** — La personne ne connaît pas le nom : « Service de cardiologie, hôpital X » — nom du service accepté à la place d'une personne.
- **Alternatif** — Faible aisance numérique : photo de la carte de visite ou de l'ordonnance → pré-remplissage par l'assistant (pipeline `AssistantPipelineFragment` existant), puis relecture.
- **Alternatif** — Reprise des soignants déjà cités ailleurs : les seeds portent « Dr. Lambert, Couvin » comme vaccinateur et opérateur; proposer ces noms en suggestion (simple liste, sans lien FHIR au premier sprint).
- **Alternatif** — Soignant à l'étranger : numéro international, nom en écriture d'origine.
- **Alternatif** — Changement de médecin traitant : cocher la case sur un autre retire l'ancienne (un seul `isGp`).
- **Erreur** — Numéro trop court → refus (même règle que les contacts).
- **Erreur** — Nom vide et organisation vide → refus.
- **Erreur** — Le même médecin saisi deux fois → avertissement de doublon (nom + téléphone identiques).
- **Urgence** — Le secouriste veut une seule ligne : « Médecin traitant : Dr X, tél … ».
- **Limite** — Un contact du pilier Contacts dont le nom commence par « Dr » (auto-détection `MEDPROVR`) : proposer de le déplacer ici.

### 7.4 Sécurité et vie privée
- Ne jamais montrer faux : un ancien médecin traitant affiché comme l'actuel; un numéro tronqué.
- Donnée d'un tiers professionnel (coordonnées de cabinet : peu sensible). La spécialité révèle une maladie (oncologue, psychiatre) : sur le QR texte, **seulement le médecin traitant** (une ligne), jamais la liste des spécialistes. PDF : médecin traitant seulement. Mesh : non.

### 7.5 KB
Aucun jeu de valeurs cité dans le code pour les rôles et spécialités. Catalogue embarqué court (une quinzaine de spécialités courantes, EN/FR/JA) + texte libre. À contrôler par le couloir KB : présence d'un jeu `healthcare-professional-roles-uv-ips` (nom à vérifier avec le validateur HL7).

### 7.6 Réutilisation
`ui/profile/contacts/*` paramétré par `kind = "provider"` : mêmes champs nom / téléphone / e-mail / adresse, `PhoneNumberHelper`, même adaptateur. Le sélecteur de lien devient un sélecteur de spécialité; ajout de la case « médecin traitant » et du champ organisation. La persistance diffère (les contacts écrivent `p.ct` via `saveProfile`, les soignants passeraient par `saveNativePillars`) : isoler ce point derrière une petite interface dans le fragment.

### 7.7 Tests
- JVM : `IpsProviderCodecTest` (aller-retour; profils IPS Practitioner / PractitionerRole / Organization; `Patient.generalPractitioner` pointe vers l'entrée `isGp`; un seul `isGp`; organisation partagée = une seule ressource; `CareTeam` et section absents quand la liste est vide; projection `pv`), `ProviderGpMigrationTest` (`p.gp` texte → entrée `isGp`, sans doublon au second enregistrement), `ProviderTextQrTest` (une ligne, médecin traitant seulement).
- Démo : Kurodo → Dr. Lambert, Couvin, médecin traitant (nom déjà présent dans ses vaccins et sa coloscopie); Haru → un médecin traitant + le service de cardiologie de son hôpital; Kamekichi → 0.
- Device : **T25** — seed K1 H2 Ka0, création, bascule du médecin traitant, doublon signalé, QR texte (une ligne), validateur (point clé : profils d'acteurs IPS, `generalPractitioner`).
- `verify_profiles.py` : ligne `pv` (type `PractitionerRole`, sans statut, sans date), contrôle « au plus un `generalPractitioner`, résolu dans le Bundle », `--expect-pv`.

### 7.8 Taille et dépendances
**M.** Gagne à passer après Contacts (formulaire partagé, règle de numéro). Débloque le lien « détenteur du document » des directives.

---

## 8. Points transverses

### 8.1 `JEntryGeneric` — champs à ajouter (tous optionnels)
| Champ | Sens | Piliers |
|---|---|---|
| `hl` | détenteur / organisme / lieu / employeur (texte) | ad, cs, en, oc, pv |
| `n`, `p`, `e` | nom, téléphone, e-mail (lettres de `JContact`) | ad (mandataire), pv |
| `r` | rôle | pv |
| `pr` | priorité | gl |
| `gp` | 1 = médecin traitant | pv |

Les champs existants `dt`, `ab`, `st`, `ct`, `vc`, `v`, `u`, `c`, `cs`, `d`, `d_display` couvrent le reste. `IpsNativePillars.fromJEntries` passe de 8 à 14 paramètres positionnels : passer à des paramètres nommés ou à `fromProfile(JemmaProfileJ)` (il est appelé à 5 endroits avec la même liste).

### 8.2 QR texte — budget de 1 800 octets
Chaque nouvelle section coûte un titre + des lignes. Proposition de rang de conservation (1 = retiré en dernier) : allergies, médicaments, problèmes, **contacts**, **directive (ligne d'existence, si autorisée)**, compléments d'identité, dispositifs, **médecin traitant (1 ligne)**, antécédents, interventions, résultats, vaccins, grossesse, état fonctionnel, **dernier séjour (1 ligne)**. Consentements, objectifs, mode de vie : jamais sur ce canal. Chaque nouvelle clé de titre doit exister dans les 25 langues de `JemmaTranslations`.

### 8.3 Canaux par pilier (proposition par défaut)
| Pilier | Écran | Bundle FHIR | QR texte | PDF | Mesh SOS | Outil Gemma |
|---|---|---|---|---|---|---|
| contacts | oui | oui | oui (existant) | à ajouter | non | à décider |
| directives | oui | oui | existence seulement, sous condition | idem | **non** | non au 1er sprint |
| soignants | oui | oui | médecin traitant | médecin traitant | non | oui |
| consentements | oui | oui | non | non | non | non |
| séjours | oui | oui | 1 ligne récente | non | non | oui |
| mode de vie | oui | oui | non | non | non | oui |
| objectifs | oui | oui | non | non | non | oui |

### 8.4 Compteurs et libellés
`ProfileDetailFragment` journalise `active=N/N · stub=M/M` et affiche « N piliers actifs, M à venir » (README T21 étape 8) : chaque sprint change ces nombres, à reporter dans l'étape device. Le commentaire de `PillarRegistry.ACTIVE` / `PASSIVE` (« the 4 active », « the 14 passive ») est périmé.

---

## 9. Ordre recommandé des 7 sprints

Critère : d'abord ce qui protège la personne en urgence, ensuite ce qui réutilise le plus.

| # | Pilier | Taille | Pourquoi à cette place |
|---|---|---|---|
| 1 | Contacts d'urgence | S | Déjà imprimé sur le QR texte mais non modifiable dans l'app : une information d'urgence figée est un risque direct. Écran existant, corrige deux défauts connus, aucun nouveau type FHIR. Prépare le mandataire des directives et le formulaire des soignants. |
| 2 | Directives anticipées | L | Pilier le plus dangereux s'il est faux : à traiter tôt, tant que l'équipe a les règles de sécurité en tête, et avant que d'autres canaux ne se multiplient. Demande des décisions du propriétaire du produit; les obtenir pendant le sprint 1. |
| 3 | Soignants | M | « Qui appeler » côté médical; réutilise le formulaire des contacts tout juste repris; résout `p.gp`; donne le détenteur du document des directives. |
| 4 | Consentements | S–M | Réutilise le codec et l'écran des directives pendant qu'ils sont frais; aucun canal d'urgence à toucher. |
| 5 | Séjours | M | Apporte du contexte au secouriste (hospitalisation récente); réutilise l'écran des antécédents. |
| 6 | Mode de vie / profession | M | Premiers profils IPS tabac et alcool; écran calqué sur la grossesse; aucun enjeu d'urgence. |
| 7 | Objectifs | M | Valeur d'urgence nulle, montage `CarePlan` + `Goal` le plus éloigné de l'existant. |

Variante si les décisions sur les directives tardent : intervertir 2 et 3 (les soignants ne dépendent d'aucune décision), ne pas commencer les directives sans réponses.

---

## 10. Questions ouvertes pour le propriétaire du produit

1. **Directives sur les canaux d'urgence.** Une directive déclarée par la personne (non vérifiée) peut-elle apparaître sur le QR texte, le PDF, le widget, la voix ? Sous quelle forme : simple existence, ou consigne ? Le bit `DNR` du format radio SOS doit-il rester à zéro ?
2. **Cadre légal par pays.** L'app s'adresse à la Belgique et au Japon au moins : quel texte d'avertissement, relu par qui, dans quelles langues ?
3. **Sens des clés `_j` `cs` et `pv`.** Les commentaires de `JemmaProfileJ.kt` disent « Care services » et « Provenance », le reste du code les compte comme consentements et soignants. Quelle est la référence (le fichier `jemma_pillar_field_map.js` cité dans `PillarMetadata` n'est pas dans ce dépôt) ? Faut-il passer `_j` à 1.3 pour les nouveaux champs ?
4. **Trois endroits pour un médecin.** `p.gp` (texte), un contact « Dr … » (`MEDPROVR`, code hors catalogue), et le futur pilier Soignants. Lequel fait foi, et que devient l'existant ?
5. **Sections hors IPS.** Consentements, séjours et équipe de soins n'ont pas de section dans l'IG IPS. Accepte-t-on des sections additionnelles dans la Composition (à confirmer par le validateur), ou ces piliers restent-ils hors Composition ?
6. **Pilier « Occupational » ou « Social history » ?** La section IPS couvre tabac et alcool; la fiche stub ne parle que d'employeur et de métier.
7. **Vie privée par défaut.** Confirmer que consentements, mode de vie, objectifs et liste des spécialistes ne sortent sur aucun canal lisible par tous. Faut-il un réglage par pilier « visible en urgence » ?
8. **Tiers cités.** Contacts et soignants sont des personnes qui n'ont pas donné leur accord pour figurer sur un QR. Message d'information à la saisie, ou plus ?
9. **Profils gérés par un aidant, mineurs.** Qui peut saisir une directive ou un consentement pour autrui ? Faut-il masquer ces piliers selon l'âge ?
10. **Import.** Une directive ou un consentement reçu par QR ou mesh doit-il être accepté, mis en quarantaine, ou refusé ?
11. **KB.** Étendre la KB (rôles, spécialités, statut tabagique, métiers) ou embarquer des catalogues dans l'app comme pour les résultats ? Qui lance le couloir KB pour lister les `vs_id` présents ?
12. **Assistant Gemma.** Le modèle peut-il lire les directives et les consentements ? Par défaut proposé : non.
13. **Budget du QR texte.** L'ordre de retrait actuel enlève la grossesse et l'état fonctionnel en premier; l'ajout de nouvelles sections est l'occasion de le revoir (une grossesse en cours est une information d'urgence).
14. **Signes vitaux.** La liste de contrôle de `docs/ips-native-pillars.md` cite la section 8716-3, absente des 18 piliers du registre : hors périmètre, ou 19ᵉ pilier ?
15. **Langues d'écran.** Les textes juridiques (directives, consentements) en de / nl / ja / zh ne peuvent pas être livrés sans relecture par un locuteur natif (point déjà ouvert pour les textes de sécurité).
