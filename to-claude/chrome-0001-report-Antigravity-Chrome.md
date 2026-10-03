---
id: chrome-0001
from: antigravity-chrome
to: claude
type: report
branch: ag/chrome-main
commit: c70ee65
needs_device: no
reply_expected: ack
orchestrator: Antigravity-Chrome
---

# Rapport Étape 1 : Module Core JemmaPassChrome, Validation HL7 IPS 1.1.0 et Premier Tour d'Amélioration

`orchestrator: Antigravity-Chrome`

---

## 1. Arborescence du Dossier `JemmaPassChrome/`

Le développement est strictement cantonné au dossier racine `JemmaPassChrome/` sur la branche dédiée `ag/chrome-main` (commit [`c70ee65`](https://github.com/kurodohenroonsen/JemmaPass/commit/c70ee65)) :

```
JemmaPassChrome/
├── README.md                      # Présentation, architecture hors-ligne et commandes CLI
├── manifest.json                  # Manifest V3 strict (permissions: storage seulement, CSP)
├── tsconfig.json                  # Configuration TypeScript strict (ES2022, NodeNext)
├── _locales/                      # Internationalisation officielle (FR, EN, JA)
│   ├── en/messages.json           # Métadonnées, 18 piliers, codes de relation v3-RoleCode
│   ├── fr/messages.json
│   └── ja/messages.json
├── icons/                         # Icônes de l'extension (16x16, 48x48, 128x128)
│   ├── icon-16.png
│   ├── icon-48.png
│   └── icon-128.png
├── core/                          # Module pur TypeScript (ZÉRO DOM, ZÉRO chrome.*)
│   ├── types.ts                   # Modèles _j 1.2 (JemmaProfileJ) et FHIR R4 IPS
│   ├── code_label_resolver.ts     # Résolveur de libellés sans savoir clinique (PROTOCOL §9.1)
│   ├── blood_group.ts             # Normalisation et réconciliation LOINC 882-1 vs p.bt
│   ├── official_displays.ts       # Libellés officiels de terminologie exigés par HL7
│   ├── payload_codec.ts           # Détection, compression/décompression _j2 (RFC 1951 Deflate)
│   ├── fhir_builder.ts            # Constructeur déterministe Bundle FHIR R4 IPS (UUID v3 RFC 4122)
│   ├── fhir_codec.ts              # Décodeur de Bundle FHIR R4 IPS vers _j 1.2
│   ├── translations.ts            # En-têtes et libellés universels (25 langues) pour QR texte
│   ├── text_payload_builder.ts    # Générateur QR texte ≤ 1800 octets UTF-8 (12 rangs d'éviction)
│   └── index.ts                   # Point d'entrée unique du module pur
├── tests/                         # Suite de tests purs exécutables sous Node 22 CLI
│   ├── test_vectors.ts            # Rejeu des 6 vecteurs contacts neutres (qa/vectors/contacts/)
│   ├── test_personas.ts           # Rejeu des 3 personas de référence (device-reports)
│   ├── test_codec_roundtrip.ts    # Tests allers-retours _j2, JSON hérité, FHIR
│   ├── run_all_tests.sh           # Script d'exécution globale et rapports
│   └── out/
│       ├── files/                 # Bundles générés (.fhir.json)
│       └── validator/             # Rapports officiels du validateur HL7
└── ui/                            # Interface utilisateur fine (réservée pour l'étape suivante)
```

---

## 2. Manifeste Manifest V3 (`manifest.json`)

Conforme aux directives de sécurité et vie privée :
- **Permissions minimales** : `"storage"` uniquement.
- **Aucune `host_permissions`** : l'extension est 100% hors-ligne.
- **Aucun content script injecté** : aucune lecture ni interaction avec les pages web visitées.
- **CSP stricte** : `script-src 'self'; object-src 'none';` interdisant tout chargement de script distant ou dynamique.

```json
{
  "manifest_version": 3,
  "name": "__MSG_extension_name__",
  "version": "1.0.0",
  "description": "__MSG_extension_description__",
  "default_locale": "en",
  "permissions": [
    "storage"
  ],
  "action": {
    "default_popup": "ui/popup.html",
    "default_title": "__MSG_extension_name__",
    "default_icon": {
      "16": "icons/icon-16.png",
      "48": "icons/icon-48.png",
      "128": "icons/icon-128.png"
    }
  },
  "icons": {
    "16": "icons/icon-16.png",
    "48": "icons/icon-48.png",
    "128": "icons/icon-128.png"
  },
  "content_security_policy": {
    "extension_pages": "script-src 'self'; object-src 'none';"
  }
}
```

---

## 3. Sortie Brute des Tests Node 22 CLI

Commande :
```bash
node --experimental-strip-types tests/test_personas.ts && \
node --experimental-strip-types tests/test_vectors.ts && \
node --experimental-strip-types tests/test_codec_roundtrip.ts
```

Sortie brute complète :
```
✔ Persona: demo_kurodo (1344.765781ms)
✔ Persona: demo_haru (1030.622355ms)
✔ Persona: demo_kamekichi (895.069828ms)
ℹ tests 3
ℹ suites 0
ℹ pass 3
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 3398.782551

✔ qa/vectors/contacts/ct-001-nominal (396.769024ms)
✔ qa/vectors/contacts/ct-002-two-contacts-order (180.265848ms)
✔ qa/vectors/contacts/ct-003-no-phone (290.444223ms)
✔ qa/vectors/contacts/ct-004-phone-only (170.257359ms)
✔ qa/vectors/contacts/ct-005-cjk-and-free-text (204.954853ms)
✔ qa/vectors/contacts/ct-006-blank-fields (228.679975ms)
ℹ tests 6
ℹ suites 0
ℹ pass 6
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 1548.866769

✔ Roundtrip _j2: compression and decompression (144.730134ms)
✔ Decoding legacy JSON format (1.63235ms)
✔ Blood group normalization and reconciliation (6.41391ms)
✔ FHIR Bundle export and import roundtrip (113.918277ms)
ℹ tests 4
ℹ suites 0
ℹ pass 4
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 354.808661
```
**Total : 13/13 tests passés, 0 échec.**

---

## 4. Liste des Vecteurs Rejoués

1. **Vecteurs officiels de contrat (`qa/vectors/contacts/`)** :
   - `ct-001-nominal.json` : contact d'urgence complet, vérification de la structure `Patient.contact` et du QR texte.
   - `ct-002-two-contacts-order.json` : préservation de l'ordre d'insertion des contacts multiples.
   - `ct-003-no-phone.json` : contact sans téléphone (adresse ou relation seule).
   - `ct-004-phone-only.json` : contact avec numéro de téléphone seul.
   - `ct-005-cjk-and-free-text.json` : caractères CJK (japonais / kanjis) et relations textuelles libres.
   - `ct-006-blank-fields.json` : assainissement des chaînes vides et espaces blancs.

2. **Personas de référence (`origin/device-reports`)** :
   - `demo_kurodo` (33 ressources, groupe A+, résultats biologiques, vaccins, allergies, antécédents, traitements).
   - `demo_haru` (32 ressources, groupe O-, stimulateur cardiaque avec alerte IRM, appareil auditif, grossesse/accouchements, statut fonctionnel).
   - `demo_kamekichi` (19 ressources, groupe B+, insuffisance cardiaque, polyallergies, traitements multiples).

---

## 5. Validation HL7 FHIR (IPS 1.1.0) Officielle

Exécution sous Java 25 via `validator_cli.jar` officiel (`hl7.fhir.uv.ips#1.1.0`, mode hors-ligne sans tx server `-tx n/a`) :

| Persona | Fichier testé | Erreurs HL7 | Avertissements | Statut |
| :--- | :--- | :---: | :---: | :---: |
| `demo_kurodo` | `demo_kurodo.fhir.json` | **0** | 23 | **CONFORME** (identique Android) |
| `demo_haru` | `demo_haru.fhir.json` | **0** | 31 | **CONFORME** (identique Android) |
| `demo_kamekichi` | `demo_kamekichi.fhir.json` | **0** | 18 | **CONFORME** (identique Android) |

---

## 6. Écarts Constatés avec Android et Résolutions

Lors de la première confrontation au validateur HL7, des écarts ont été identifiés et corrigés pour atteindre la conformité stricte 0 erreur :

1. **Libellés de terminologie officiels (`IpsOfficialDisplays`)** :
   - *Android* : `IpsOfficialDisplays.kt` fournit la correspondance exacte exigée par le validateur HL7 (ex: LOINC `882-1` -> `"ABO and Rh group [Type] in Blood"`, SNOMED `1181000221105` -> `"Influenza virus antigen only vaccine product"`).
   - *Résolution Chrome* : Création de `core/official_displays.ts` miroir strict de la table Android, plaçant le libellé officiel dans `coding[0].display` et conservant le libellé convivial dans `text`.
2. **Absence de date sur les observations (`effective[x]`)** :
   - *Règle IPS 1.1.0* : `Observation.effective[x]` est obligatoire (1..1).
   - *Résolution Chrome* : Quand aucune date clinique n'est connue (ex: groupe sanguin auto-dérivé), émission de `_effectiveDateTime` avec l'extension standard `http://hl7.org/fhir/StructureDefinition/data-absent-reason` = `"unknown"`, et `performer` pointant sur le patient (`"Patient-reported"`).
3. **Dispositifs médicaux (`DeviceUseStatement.timing[x]`)** :
   - *Règle IPS 1.1.0* : Le champ `timing[x]` est 1..1.
   - *Résolution Chrome* : Remplacement de `recordedOn` par `timingDateTime` (ou `_timingDateTime` inconnu).
4. **Profils de grossesse (`Observation-pregnancy-outcome-uv-ips`)** :
   - *Règle IPS 1.1.0* : Le code `11640-0` (nombre total d'accouchements) ne peut pas porter le profil `pregnancy-status` (réservé à `82810-3`).
   - *Résolution Chrome* : Sélection dynamique du profil (`status`, `edd`, ou `outcome`) selon le code LOINC.

---

## 7. Questions Ouvertes pour Kudoro avant l'Écriture de l'Interface UI

1. **Format d'affichage principal (Popup vs Page d'onglet)** :
   - Une popup d'extension Chrome est contrainte à une largeur/hauteur maximale de 800x600 px (recommandé 400x600 px). Pour afficher lisiblement 18 piliers IPS avec alertes d'urgence et synthèse, une page d'onglet dédiée (`chrome.tabs.create({ url: "ui/index.html" })` ou `options_page`) est nettement plus lisible et accessible. Faut-il réserver la popup au statut rapide + bouton « Ouvrir le passeport complet », ou tout intégrer dans la popup ?
2. **Mécanisme d'import / export de fichiers** :
   - Hors-ligne, l'import se fait par l'élément HTML `<input type="file" accept=".json,.fhir.json">` ou par zone de glisser-déposer (Drag & Drop), et l'export par déclenchement d'un téléchargement local (`<a download="..." href="blob:...">`). Confirmez-vous cette approche sans recours à l'API `chrome.downloads` (qui exigerait une permission supplémentaire dans le manifeste) ?
3. **Sélecteur de langue pour l'affichage médical** :
   - Les libellés d'interface sont traduits en FR, EN et JA dans `_locales/`. Faut-il mémoriser la préférence linguistique choisie par l'utilisateur dans `chrome.storage.local` indépendamment de la langue du navigateur ?
