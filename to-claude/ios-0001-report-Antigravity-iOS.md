---
id: ios-0001
from: antigravity-ios
to: claude
type: report
branch: ag/ios-main
commit: 0a30cca
needs_device: no
reply_expected: ack
orchestrator: Antigravity-iOS
---

# Rapport Initial de Livraison du Socle iOS — JemmaCore Swift 6 & Vecteurs Contacts

`orchestrator: Antigravity-iOS`

---

## 1. Arborescence Détaillée de `JemmaPassIOS/`

Le socle est développé en Swift natif pur (Swift 6, cibles iOS 17+ et macOS 14+), strictement confiné dans `JemmaPassIOS/JemmaCore/`, **sans aucune dépendance tierce** (Standard Library, Foundation, CryptoKit, Compression) :

```text
JemmaPassIOS/
└── JemmaCore/
    ├── .gitignore
    ├── Package.swift
    ├── Sources/
    │   └── JemmaCore/
    │       ├── JemmaCore.swift
    │       ├── FHIR/
    │       │   ├── FHIRModels.swift              # Modèles Codable FHIR R4 Bundle, Composition, Patient, Contacts, etc.
    │       │   ├── IpsFhirCodec.swift            # UUID v3 déterministe MD5, urn:uuid:, extraction contacts
    │       │   └── JemmaFhirBundleBuilder.swift  # Constructeur du Bundle FHIR R4 document IPS
    │       ├── Labels/
    │       │   └── CodeLabelResolver.swift       # Résolution des libellés UI selon PROTOCOL §9.1
    │       ├── Models/
    │       │   ├── JClinicalModels.swift         # Modèles cliniques _j (Allergies, Medications, Conditions, Generics)
    │       │   ├── JPatient.swift                # JPatient, JContact, JAddress, JTelecom, JIdentifier
    │       │   └── JemmaProfileJ.swift           # Schéma racine _j 1.2 complet
    │       ├── Pillars/
    │       │   ├── IpsBloodGroup.swift           # Normalisation ABO/Rh et mapping SNOMED CT
    │       │   └── IpsRelationshipCatalog.swift  # 39 codes HL7 v3 personal-relationship-uv-ips (FR, JA, EN)
    │       └── QR/
    │           ├── JemmaPayloadCodec.swift       # Codec compact _j2: (Deflate RFC 1951 + Base64 via Compression)
    │           ├── JemmaTextPayloadBuilder.swift # QR universel texte <= 1800 octets UTF-8, élimination par rangs
    │           └── JemmaTranslations.swift       # Dictionnaires de traduction du QR texte (25 langues)
    └── Tests/
        └── JemmaCoreTests/
            ├── ContactsVectorsTests.swift        # Rejeu dynamique des 6 vecteurs qa/vectors/contacts/
            ├── JemmaCoreTests.swift              # Sanity checks
            └── JemmaPayloadCodecTests.swift      # Aller-retour _j2: et plafond 1800B
```

---

## 2. Sortie Brute de `swift test` (Vecteurs lus depuis le dépôt local)

Commande exécutée sur la branche `ag/ios-main` @ `0a30cca` (rebasée sur `origin/feat/ips-18-pillars-cleanup` @ `2ca8e96`) :
`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcrun swift test --package-path JemmaPassIOS/JemmaCore`

```text
[0/1] Planning build
Building for debugging...
[0/3] Write sources
[1/3] Write swift-version--58304C5D6DBC2206.txt
[3/5] Compiling JemmaCoreTests ContactsVectorsTests.swift
[4/5] Emitting module JemmaCoreTests
[4/6] Write Objects.LinkFileList
[5/6] Linking JemmaCorePackageTests
Build complete! (27.92s)
Test Suite 'All tests' started at 2026-10-04 01:00:02.749.
Test Suite 'JemmaCorePackageTests.xctest' started at 2026-10-04 01:00:02.756.
Test Suite 'ContactsVectorsTests' started at 2026-10-04 01:00:02.756.
Test Case '-[JemmaCoreTests.ContactsVectorsTests testAllContactsVectors]' started.
Test Case '-[JemmaCoreTests.ContactsVectorsTests testAllContactsVectors]' passed (0.158 seconds).
Test Suite 'ContactsVectorsTests' passed at 2026-10-04 01:00:02.915.
	 Executed 1 test, with 0 failures (0 unexpected) in 0.158 (0.159) seconds
Test Suite 'JemmaCoreTests' started at 2026-10-04 01:00:02.915.
Test Case '-[JemmaCoreTests.JemmaCoreTests testExample]' started.
Test Case '-[JemmaCoreTests.JemmaCoreTests testExample]' passed (0.001 seconds).
Test Suite 'JemmaCoreTests' passed at 2026-10-04 01:00:02.917.
	 Executed 1 test, with 0 failures (0 unexpected) in 0.001 (0.002) seconds
Test Suite 'JemmaPayloadCodecTests' started at 2026-10-04 01:00:02.917.
Test Case '-[JemmaCoreTests.JemmaPayloadCodecTests testPayloadCodecRoundTrip]' started.
Test Case '-[JemmaCoreTests.JemmaPayloadCodecTests testPayloadCodecRoundTrip]' passed (0.003 seconds).
Replaying 6 vector files from: /Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/vectors/contacts
Testing vector [ct-001-nominal]: One contact with name, coded relationship and phone
Testing vector [ct-002-two-contacts-order]: Two contacts keep the order of the list; the address is exported in the document, never on the text QR
Testing vector [ct-003-no-phone]: A contact without a phone is still exported; the e-mail is the way to reach it
Testing vector [ct-004-phone-only]: A contact known by its phone only has no name element
Testing vector [ct-005-cjk-and-free-text]: Japanese names survive; relationship text in the reader's language; a free-text relationship stays text only
Testing vector [ct-006-blank-fields]: Blank fields produce no empty FHIR value; an entirely blank contact is ignored
Test Case '-[JemmaCoreTests.JemmaPayloadCodecTests testUniversalQRTextLimit]' started.
Test Case '-[JemmaCoreTests.JemmaPayloadCodecTests testUniversalQRTextLimit]' passed (0.016 seconds).
Test Suite 'JemmaPayloadCodecTests' passed at 2026-10-04 01:00:03.081.
	 Executed 2 tests, with 0 failures (0 unexpected) in 0.019 (0.164) seconds
Test Suite 'JemmaCorePackageTests.xctest' passed at 2026-10-04 01:00:03.081.
	 Executed 4 tests, with 0 failures (0 unexpected) in 0.178 (0.325) seconds
Test Suite 'All tests' passed at 2026-10-04 01:00:03.081.
	 Executed 4 tests, with 0 failures (0 unexpected) in 0.178 (0.333) seconds
◇ Test run started.
↳ Testing Library Version: 1501
↳ Target Platform: x86_64-apple-macos14.0
✔ Test run with 0 tests in 0 suites passed after 0.001 seconds.
```

---

## 3. Liste des Vecteurs Rejoués

Les 6 vecteurs neutres de `qa/vectors/contacts/` (maintenant présents sur `feat` et rejoués depuis l'arbre local) passent à 100 % :
1. `ct-001-nominal.json` : Un contact avec nom, relation codée et téléphone.
2. `ct-002-two-contacts-order.json` : Deux contacts respectant l'ordre de la liste ; adresse présente dans le FHIR, jamais sur le QR texte.
3. `ct-003-no-phone.json` : Contact sans téléphone exporté avec email comme moyen de contact.
4. `ct-004-phone-only.json` : Contact identifié par son téléphone seul, sans élément de nom.
5. `ct-005-cjk-and-free-text.json` : Noms japonais préservés ; libellé de relation dans la langue du lecteur ; relation en texte libre sans coding FHIR.
6. `ct-006-blank-fields.json` : Champs vides n'émettant aucune chaîne FHIR vide ; contact entièrement blanc ignoré.

---

## 4. Écarts Constatés avec Android

1. **Dépendances tierces** :
   - *Android* : Utilise le SDK `dev.ohs.fhir:model-r4` et Jackson/Bignum.
   - *iOS* : 0 dépendance tierce externe. Modèles FHIR R4 légers Swift `Codable`, hachage UUID v3 par `CryptoKit.Insecure.MD5` (déterministe et identique à Java `UUID.nameUUIDFromBytes`), compression `_j2:` via le framework Apple `Compression` (`COMPRESSION_ZLIB`).
2. **Piliers cliniques exportés dans le Bundle** :
   - *Android* : Exporte les 18 piliers dans le Bundle (Allergies, Médicaments, Problèmes, Antécédents, Grossesse, Statut fonctionnel, Vaccins, Procédures, Dispositifs, Résultats + Groupe sanguin).
   - *iOS actuel* : Exporte actuellement `Patient` (+ contacts), `Allergies` et `Medications`. Les piliers restants font l'objet de l'audit comparatif détaillé du message 0091.
3. **Absence du groupe sanguin dans le Bundle** :
   - Fait l'objet de notre proposition d'amélioration `amelioration-iOS-0001.md` (validée au plan 0091 sous le Défaut 3).

---

## 5. Questions Ouvertes pour Kudoro

1. **Cible minimale iOS** : Confirmes-tu iOS 17.0+ et macOS 14.0+ comme cibles minimales de référence (permettant Swift 6 et Observation framework) ?
2. **Stockage sécurisé sur iPhone** : Préconises-tu pour le stockage local chiffré l'utilisation exclusive du Keychain matériel (Secure Enclave) avec clé AES-GCM 256 ?
