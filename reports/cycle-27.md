# Rapport d'Exécution Cycle 27 — Pilier 8 Contacts d'Urgence (Patient.contact)

- **Date** : 2026-10-03
- **Branche** : `ag/0061-contacts`
- **Commit testé** : `c5d6fd2` (`fix(contacts): enforce pat-1 invariant, omit relation-only contact and document isRoleCode boundary`)
- **Appareil** : Google Pixel 9 Pro XL (Android 15)
- **Hôte** : Darwin x86_64
- **Dossier de sortie** : `qa/device/out/c5d6fd2-20261003-2218`
- **Statut global** : **SUCCÈS TOTAL (PASS 113/113 checks seed, 0 failed, 0 erreurs HL7 FHIR IPS, UI contacts validée)**

---

## 1. Tableaux de Verdicts par Bloc

### BLOC A — Seed 18 piliers sur appareil & Invariants
| Vérification | Attendu | Obtenu | Verdict |
|---|---|---|---|
| JVM unit tests | 462 tests pass | 462 tests pass, 0 failed (`logs/unit-tests.log`) | ✅ PASS |
| assembleDebug | Build APK réussi | `logs/assemble.log` | ✅ PASS |
| Install & re-seed | Re-seed 3 demo personas | `demo_kurodo`, `demo_haru`, `demo_kamekichi` | ✅ PASS |
| Invariants `verify_profiles` | 113 checks PASS | 113 checks, 0 failed (`verify-seed.md`) | ✅ PASS |
| Profils seed restaurés | 💉 K4 H3 Ka0, 🏥 K2 H2 Ka0, 📟 K0 H2 Ka0, 🧪 K4 H5 Ka1, 📜 K2 H2 Ka0, 🩺 K1 H2 Ka3, 🤰 K0 H3 Ka0, ♿ K0 H2 Ka0 | Identique au seed attendu | ✅ PASS |

### BLOC B — Validation HL7 FHIR IPS R4 Seed
| Profil | Erreurs HL7 FHIR | Avertissements | Fichier de preuve | Verdict |
|---|---|---|---|---|
| `demo_haru` | **0** | 51 | `validator/demo_haru.txt` | ✅ PASS |
| `demo_kamekichi` | **0** | 31 | `validator/demo_kamekichi.txt` | ✅ PASS |
| `demo_kurodo` | **0** | 28 | `validator/demo_kurodo.txt` | ✅ PASS |
| **Total** | **0 erreur** | 110 | `validator/summary.txt` | ✅ PASS |

#### Ligne du validateur sur `Patient.contact` de `demo_haru` (contact `DAUC`)
Extrait de `validator/demo_haru.txt` :
```html
<tr>
  <td>Warning</td>
  <td>Bundle.entry[1].resource/*Patient/patient-01*/.contact[0].relationship[0]</td>
  <td>Invalid Code</td>
  <td>None of the codings provided are in the value set 'Patient Contact Relationship ' (http://hl7.org/fhir/ValueSet/patient-contactrelationship|4.0.1), and a coding should come from this value set unless it has no suitable code (note that the validator cannot judge what is suitable) (codes = http://terminology.hl7.org/CodeSystem/v3-RoleCode#DAUC)</td>
  <td/>
  <td>TerminologyEngine</td>
</tr>
```
*(Avertissement informatif standard dû au ValueSet extensible dans le profil de base FHIR R4, 0 erreur structurelle ou fatale).*

### BLOC C — Vérification JSON de `Patient.contact` brut (3 personas)
| Persona | Contact | Télécom (sans `use: mobile`) | Adresse (`address.text` présent) | Fichier de preuve | Verdict |
|---|---|---|---|---|---|
| `demo_kurodo` | Kamekichi (FRND) | `value: "+32 2 000 00 01"` (pas de use mobile) | `"75 Avenue Louise, Bruxelles"` | `json/demo_kurodo_contacts.json` | ✅ PASS |
| `demo_haru` | Sakura Tanaka (DAUC) | `value: "+81 90 0000 0001"` (pas de use mobile) | `"Aomori, Japan"` | `json/demo_haru_contacts.json` | ✅ PASS |
| `demo_kamekichi` | Kurodo Henro (FRND) | `value: "+32 2 000 00 02"` (pas de use mobile) | `"Rue de la Paix 12, 5660 Couvin"` | `json/demo_kamekichi_contacts.json` | ✅ PASS |

### BLOC D — QR Texte FR & JA de `demo_haru` (Relation traduite)
| Langue | Section Contact décodée brute | Preuve décodée | Capture écran | Verdict |
|---|---|---|---|---|
| **FR** | `☎️ [ CONTACTS ]
  ▪️ Sakura Tanaka (Fille) +81 90 0000 0001` | `qr/qr-haru-fr.txt` | `screenshots/haru-text-qr-fr.png` | ✅ PASS |
| **JA** | `☎️ [ 緊急連絡先 ]
  ▪️ Sakura Tanaka (娘) +81 90 0000 0001` | `qr/qr-haru-ja.txt` | `screenshots/haru-text-qr-ja.png` | ✅ PASS |

### BLOC E — Saisie Dynamique UI (Pilier Actif, Adresse Seule & Relation MEDPROVR Inconnue)
| Étape | Action / Contrôle | Preuve observée | Verdict |
|---|---|---|---|
| **E.1** | Tuile Contacts active sur Haru | Tuile `id=profile_detail_tile_contacts` cliquable, libellé "Contacts d'urgence" · `screenshots/haru-detail-contacts-tile.png` | ✅ PASS |
| **E.2** | Navigation ContactsEditFragment | Liste affichant `1 contact` : Sakura Tanaka (Fille) · `screenshots/contacts-list-initial.png` | ✅ PASS |
| **E.3** | FAB Add & formulaire | Bottom sheet ouvert · `screenshots/contact-form-empty.png` | ✅ PASS |
| **E.4** | Saisie nom "Dr Smith" & auto-détection | Détection automatique du titre "Dr" → relation `MEDPROVR` assignée automatiquement · `contact-form-filled-medprovr.png` | ✅ PASS |
| **E.5** | Saisie adresse sans téléphone | Adresse renseignée : "Aomori Station 1", téléphone laissé vide · `contact-form-filled-medprovr.png` | ✅ PASS |
| **E.6** | Enregistrement & liste (2 contacts) | Liste actualisée avec 2 contacts : Sakura Tanaka et Dr Smith · `screenshots/contacts-list-2-contacts.png` | ✅ PASS |
| **E.7** | Export FHIR Bundle vérifié | `Patient.contact[1]` contient `name: Dr Smith`, `address: Aomori Station 1`, `relationship: MEDPROVR`, aucun télécom · `json/demo_haru_contacts_after_add.json` | ✅ PASS |
| **E.8** | QR texte : code MEDPROVR masqué | Ligne du QR : `▪️ Dr Smith` (le code technique inconnu `MEDPROVR` est bien omis du QR texte) · `qr/qr-haru-after-medprovr.txt` · `screenshots/haru-text-qr-after-medprovr.png` | ✅ PASS |
| **E.9** | Suppression & nettoyage | Appui long sur "Dr Smith" → dialogue de confirmation (`screenshots/contacts-delete-dialog.png`) → suppression → liste restaurée à 1 contact (`screenshots/contacts-list-restored.png`) | ✅ PASS |
| **E.10** | Invariants après restauration | 42 checks PASS, 0 failed (`verify-restored.md`) | ✅ PASS |

### BLOC F — Logs & Stabilité
- **Logcat épuré** : `logs/logcat-ui.txt` (55 lignes scrubbées).
- **Crashs / Exceptions** : 0 crash AndroidRuntime, 0 exception non interceptée.

---

## 2. Extraits Bruts de Preuves (Règle 8)

### Extrait `qr/qr-haru-fr.txt`
```
☎️ [ CONTACTS ]
  ▪️ Sakura Tanaka (Fille) +81 90 0000 0001
```

### Extrait `qr/qr-haru-ja.txt`
```
☎️ [ 緊急連絡先 ]
  ▪️ Sakura Tanaka (娘) +81 90 0000 0001
```

### Extrait `qr/qr-haru-after-medprovr.txt` (code technique MEDPROVR masqué)
```
☎️ [ CONTACTS ]
  ▪️ Sakura Tanaka (Fille) +81 90 0000 0001
  ▪️ Dr Smith
```

### Extrait `json/demo_haru_contacts.json` (Seed Haru)
```json
[
  {
    "relationship": [
      {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v3-RoleCode",
            "code": "DAUC",
            "display": "daughter"
          }
        ],
        "text": "daughter"
      }
    ],
    "name": {
      "text": "Sakura Tanaka"
    },
    "telecom": [
      {
        "system": "phone",
        "value": "+81 90 0000 0001"
      }
    ],
    "address": {
      "text": "Aomori, Japan"
    }
  }
]
```

### Extrait `json/demo_kurodo_contacts.json` (Seed Kurodo)
```json
[
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
        "value": "+32 2 000 00 01"
      }
    ],
    "address": {
      "text": "75 Avenue Louise, Bruxelles"
    }
  }
]
```

### Extrait `json/demo_haru_contacts_after_add.json` (Après ajout Dr Smith avec adresse seule)
```json
[
  {
    "relationship": [
      {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v3-RoleCode",
            "code": "DAUC",
            "display": "daughter"
          }
        ],
        "text": "daughter"
      }
    ],
    "name": {
      "text": "Sakura Tanaka"
    },
    "telecom": [
      {
        "system": "phone",
        "value": "+81 90 0000 0001"
      }
    ],
    "address": {
      "text": "Aomori, Japan"
    }
  },
  {
    "relationship": [
      {
        "text": "MEDPROVR"
      }
    ],
    "name": {
      "text": "Dr Smith"
    },
    "address": {
      "text": "Aomori Station 1"
    }
  }
]
```
