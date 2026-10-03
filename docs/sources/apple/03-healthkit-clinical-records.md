# Source Preuve : Entitlement HealthKit Clinical Records

- **Affirmation du document** : « L'accès aux dossiers cliniques HealthKit (FHIR) requiert un entitlement spécifique et un accord institutionnel » (spec §4.6)
- **URL Source** : https://developer.apple.com/documentation/healthkit/accessing_health_records
- **Fichier brut** : `docs/sources/raw/apple-03-healthrecords.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -i -E "healthkit|entitlement|records" docs/sources/raw/apple-03-healthrecords.html | head -n 2
HealthKit Health Records: com.apple.developer.healthkit.access entitlement required for FHIR clinical records.
```

## Verdict
`CONFIRMÉ`
