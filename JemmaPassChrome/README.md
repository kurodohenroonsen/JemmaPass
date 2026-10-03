# 🐢 JemmaPass Chrome — Extension MV3 Hors-Ligne (IPS HL7 FHIR R4)

Extension Chrome Manifest V3 dédiée à la lecture, l'affichage et l'édition hors-ligne d'un passeport de santé conforme au standard international HL7 FHIR R4 IPS (*International Patient Summary*, ISO 27269:2021) et à sa projection compacte `_j 1.2`.

## 1. Principes Directeurs & Sécurité

- **Zero-Cloud at Runtime** : Aucun appel réseau, aucune télémétrie, aucune ressource chargée à distance. Fonctionne 100% hors-ligne.
- **Permissions Minimales** : `storage` uniquement (`chrome.storage.local`). Aucune permission d'hôte (`host_permissions`), aucun script injecté dans les pages web.
- **Politique de Sécurité de Contenu (CSP) Stricte** : `extension_pages: "script-src 'self'; object-src 'none';"`.
- **Règle « KB seulement » (PROTOCOL §9)** : Aucun code médical ni table de savoir en dur dans le TypeScript.
- **Libellés d'interface (PROTOCOL §9.1)** : Les libellés d'interface pour les codes (rôles, voies, groupe sanguin) sont stockés dans `_locales/<lang>/messages.json` sous la nomenclature `code_label_<prefix>_<cleaned_code>`.

## 2. Architecture Modulaire

```
JemmaPassChrome/
├── manifest.json                 # Manifest V3 (permissions minimales: storage)
├── _locales/                     # Libellés d'interface et codes courts (FR, EN, JA)
│   ├── en/messages.json
│   ├── fr/messages.json
│   └── ja/messages.json
├── core/                         # Module TypeScript pur (zéro DOM, zéro chrome.*)
│   ├── types.ts                  # Schémas JemmaProfileJ _j 1.2 et FHIR R4 IPS
│   ├── code_label_resolver.ts    # Résolution des libellés selon PROTOCOL §9.1
│   ├── blood_group.ts            # Normalisation et réconciliation LOINC 882-1
│   ├── payload_codec.ts          # Codec QR compact _j2 (RFC 1951 deflate-raw) & JSON
│   ├── fhir_builder.ts           # Constructeur déterministe de Bundle FHIR R4 IPS
│   ├── fhir_codec.ts             # Parseur de Bundle FHIR R4 vers JemmaProfileJ
│   ├── translations.ts           # Libellés du QR texte universel 25 langues
│   ├── text_payload_builder.ts   # QR Texte 1800B max avec éviction par rangs
│   └── index.ts                  # Point d'entrée unique du module core
├── tests/                        # Tests automatisés en ligne de commande
│   ├── test_vectors.ts           # Rejeu des vecteurs qa/vectors/contacts/ (ct-001..006)
│   ├── test_personas.ts          # Validation des 3 personas démo (Kurodo, Haru, Kamekichi)
│   ├── test_codec_roundtrip.ts   # Tests de compression _j2, import/export et sang
│   └── run_all_tests.sh          # Script d'exécution global + validateur HL7
└── icons/                        # Icônes de l'extension (16, 48, 128)
```

## 3. Piliers IPS Supportés (18 Piliers)

1. **Patient (p)** : Démographie, contacts d'urgence (`ct`), adresses, télécoms, identifiants.
2. **Allergies et Intolérances (al)** : Allergies médicamenteuses, alimentaires, environnementales (`AllergyIntolerance-uv-ips`).
3. **Médications en Cours (md)** : Traitements actifs, posologies, voies d'administration (`MedicationStatement-uv-ips` + `Medication-uv-ips`).
4. **Problèmes Actifs (cn)** : Diagnostics et pathologies en cours (`Condition-uv-ips`, section 11450-4).
5. **Antécédents Passés (ph)** : Antécédents médicaux et résolus (`Condition-uv-ips`, section 11348-0).
6. **Vaccinations (im)** : Historique vaccinal, numéros de dose (`Immunization-uv-ips`, section 11369-6).
7. **Actes et Chirurgies (pr)** : Interventions chirurgicales et actes (`Procedure-uv-ips`, section 47519-4).
8. **Dispositifs Médicaux (dv)** : Implants, pacemakers, prothèses (`Device-uv-ips` + `DeviceUseStatement-uv-ips`, section 46264-8).
9. **Résultats et Biologie (rs)** : Examens de laboratoire, imagerie, et groupe sanguin ABO/Rh LOINC 882-1 réconcilié.
10. **Grossesse et Maternité (pg)** : Statut de grossesse, date prévue d'accouchement, parité (`Observation-pregnancy-*-uv-ips`).
11. **Statut Fonctionnel (fs)** : Autonomie, limitations et aides techniques (`Condition-uv-ips`, section 47420-5).
12-18. **Piliers Stubs (ad, cs, gl, en, oc, gp, pv)** : Préservés dans la projection compacte `_j 1.2`.

## 4. Exécution des Tests

Les tests s'exécutent directement en ligne de commande avec Node.js (v22+) sans framework lourd :

```bash
bash JemmaPassChrome/tests/run_all_tests.sh
```
