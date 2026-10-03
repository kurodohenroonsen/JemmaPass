### `json/demo_haru_patient.json`
```
[
  {
    "fullUrl": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf",
    "resource": {
      "resourceType": "Patient",
      "id": "patient-01",
      "meta": {
        "profile": [
          "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips"
        ]
      },
      "name": [
        {
          "family": "Tanaka",
          "given": [
            "Haru"
          ]
        }
      ],
      "gender": "female",
      "birthDate": "1946-02-08",
      "address": [
        {
          "text": "Aomori, Japan",
          "line": [
            "Aomori, Japan"
          ]
        }
      ],
      "communication": [
        {
          "language": {
            "coding": [
              {
                "system": "urn:ietf:bcp:47",
                "code": "ja-JP"
              }
            ]
          }
        }
      ]
    }
  }
]
```

### `json/demo_kamekichi_patient.json`
```
[
  {
    "fullUrl": "urn:uuid:d5b8d87e-f0ce-36de-b086-bd163cf9aeb2",
    "resource": {
      "resourceType": "Patient",
      "id": "patient-01",
      "meta": {
        "profile": [
          "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips"
        ]
      },
      "name": [
        {
          "given": [
            "Kamekichi"
          ]
        }
      ],
      "gender": "male",
      "birthDate": "2000-05-20",
      "address": [
        {
          "text": "75 Avenue Louise, Bruxelles, Belgique",
          "line": [
            "75 Avenue Louise, Bruxelles, Belgique"
          ]
        }
      ],
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
            "text": "Kurodo Henro"
          },
          "telecom": [
            {
              "system": "phone",
              "value": "+32 2 000 00 02",
              "use": "mobile"
            }
          ]
        }
      ],
      "communication": [
        {
          "language": {
            "coding": [
              {
                "system": "urn:ietf:bcp:47",
                "code": "ja-JP"
              }
            ]
          }
        }
      ]
    }
  }
]
```

### `json/demo_kurodo_patient.json`
```
[
  {
    "fullUrl": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1",
    "resource": {
      "resourceType": "Patient",
      "id": "patient-01",
      "meta": {
        "profile": [
          "http://hl7.org/fhir/uv/ips/StructureDefinition/Patient-uv-ips"
        ]
      },
      "name": [
        {
          "family": "Henro",
          "given": [
            "Kurodo"
          ]
        }
      ],
      "gender": "male",
      "birthDate": "1979-04-04",
      "address": [
        {
          "text": "Rue de la Paix 12, 5660 Couvin, Belgique",
          "line": [
            "Rue de la Paix 12, 5660 Couvin, Belgique"
          ]
        }
      ],
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
      ],
      "communication": [
        {
          "language": {
            "coding": [
              {
                "system": "urn:ietf:bcp:47",
                "code": "fr-FR"
              }
            ]
          }
        }
      ]
    }
  }
]
```

### `json/kurodo_lab_1000.json`
```
{
  "fullUrl": "urn:uuid:89a903aa-96c2-35ce-ad1e-89318db1e2a0",
  "resource": {
    "resourceType": "Observation",
    "id": "b7caf4b7-e928-47df-b2ef-dee730f75aa6",
    "meta": {
      "profile": [
        "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-results-laboratory-uv-ips"
      ]
    },
    "status": "final",
    "category": [
      {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
            "code": "laboratory",
            "display": "Laboratory"
          }
        ],
        "text": "Laboratory"
      }
    ],
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
    "subject": {
      "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1"
    },
    "performer": [
      {
        "reference": "urn:uuid:2efe4dbf-51f2-36a9-8054-5ddd93b3b1f1",
        "display": "Patient-reported"
      }
    ],
    "_effectiveDateTime": {
      "extension": [
        {
          "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
          "valueCode": "unknown"
        }
      ]
    },
    "valueString": "1,000 mmol/L"
  }
}
```

### `validator/summary.txt`
```
errors:
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/3ee9a8d-20261003-0337/validator/demo_haru.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/3ee9a8d-20261003-0337/validator/demo_kamekichi.txt:0
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/3ee9a8d-20261003-0337/validator/demo_kurodo.txt:0
warnings:
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/3ee9a8d-20261003-0337/validator/demo_haru.txt:50
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/3ee9a8d-20261003-0337/validator/demo_kamekichi.txt:31
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/3ee9a8d-20261003-0337/validator/demo_kurodo.txt:28
/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/qa/device/out/3ee9a8d-20261003-0337/validator/summary.txt:0
```

