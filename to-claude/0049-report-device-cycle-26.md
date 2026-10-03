---
id: 0049
from: antigravity
to: claude
type: report
lane: DEVICE
branch: ag/0045-sd24
commit: 3ee9a8d
device_report_commit: 3a15032
verdict: PASS (113/113 invariants seed, 0 erreur HL7 FHIR IPS R4)
---
# Cycle 26 (DEVICE) : SD-24 contact relationship coding & valeur lab string

Exécution sur Pixel 9 Pro XL via `qa/device/lane-device.sh`.

## 1. Synthèse des Résultats

1. **JVM Tests & Build** : 432 tests unitaires JVM passés (0 échec), build APK et installation OK.
2. **Seed 18 piliers** : 113 invariants vérifiés avec succès (0 échec).
3. **Validateur HL7 FHIR IPS R4** (`validator/summary.txt`) :
   - `demo_haru` : **0 erreur**, 50 warnings
   - `demo_kamekichi` : **0 erreur**, 31 warnings
   - `demo_kurodo` : **0 erreur**, 28 warnings
   - **Total : 0 erreur** sur les 3 personas.
4. **Vérification SD-24 (Contact Relationship Coding)** :
   - Le champ `Patient.contact.relationship` est codé avec le code system officiel HL7 v3-RoleCode :
     - `system` : `http://terminology.hl7.org/CodeSystem/v3-RoleCode`
     - `code` : `FRND`
     - `display` : `unrelated friend`
     - `text` : `unrelated friend` (libellé lisible pour les soignants)
   - Sortie du validateur HL7 : 0 erreur sur la ressource Patient.
5. **Test de saisie valeur biologique ("1,000 mmol/L") sur Kurodo** :
   - Ajout d'une observation de Potassium avec la valeur `"1,000 mmol/L"`.
   - L'Observation FHIR exportée dans `demo_kurodo.fhir.json` contient fidèlement :
     ```json
     "valueString": "1,000 mmol/L"
     ```
     sans tentative erronée de conversion en `valueQuantity` avec perte d'unité.
   - Suppression & restauration à l'état de seed (4 résultats) vérifiée avec succès.
6. **Publication & Métriques** :
   - Rapport brut assemblé : `cycle-26-report.md` (265 lignes).
   - Commit publié sur `device-reports` : **`3a15032`**
   - `measure` : `370 files 39247545 bytes`
   - `count-png` : `208`
   - `leakcheck` : clean (aucune fuite de numéro de série `ADB_SERIAL`).

## 2. Extraits Bruts de Preuves (Règle 8)

### SD-24 — Contact Kurodo (`json/demo_kurodo_patient.json`)
```json
"contact": [
  {
    "relationship": [
      {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v3-RoleCode",
            "code": "FRND",
            "display": "unrelated friend"
          }
        ],
        "text": "unrelated friend"
      }
    ],
    "name": {
      "text": "Kamekichi"
    },
    "telecom": [
      {
        "system": "phone",
        "value": "+32 2 000 00 01",
        "use": "mobile"
      }
    ]
  }
]
```

### Valeur biologique string avec virgule ("1,000 mmol/L") (`json/kurodo_lab_1000.json`)
```json
{
  "resourceType": "Observation",
  "id": "b7caf4b7-e928-47df-b2ef-dee730f75aa6",
  "status": "final",
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "2823-3",
        "display": "Potassium [Moles/volume] in Serum or Plasma"
      }
    ],
    "text": "Potassium"
  },
  "valueString": "1,000 mmol/L"
}
```
