# Rapport d'Exécution Cycle 28 — Pilier 8 Contacts d'Urgence & SD-27

- **Date** : 2026-10-04
- **Branche** : `feat/ips-18-pillars-cleanup`
- **Commit testé** : `85214da` (avec SD-27 @ `2ca8e96`, vecteurs sang/dispositifs @ `6a9379a`)
- **Appareil** : Google Pixel 9 Pro XL (Android 16)
- **Hôte** : Darwin arm64
- **Dossier de sortie** : `85214da-20261004-0608`
- **Statut global** : **SUCCÈS TOTAL (PASS 113/113 checks seed, 0 erreur HL7 FHIR IPS, UI contacts SD-27 validée sans MEDPROVR, contact relation seule absent, garde validé)**

---

## 1. Tableaux de Verdicts par Bloc

### BLOC A — Seed 18 piliers sur appareil & Invariants
| Vérification | Attendu | Obtenu | Verdict |
|---|---|---|---|
| JVM unit tests | 512 tests pass | 512 tests pass, 0 failed (`logs/unit-tests.log`) | ✅ PASS |
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

### BLOC C — Vérification JSON de `Patient.contact` brut (3 personas)
| Persona | Contact | Télécom (sans `use: mobile`) | Adresse (`address.text` présent) | Fichier de preuve | Verdict |
|---|---|---|---|---|---|
| `demo_kurodo` | Kamekichi (FRND) | `value: "+32 2 000 00 01"` (pas de use mobile) | `"75 Avenue Louise, Bruxelles"` | `json/demo_kurodo_contacts.json` | ✅ PASS |
| `demo_haru` | Sakura Tanaka (DAUC) | `value: "+81 90 0000 0001"` (pas de use mobile) | `"Aomori, Japan"` | `json/demo_haru_contacts.json` | ✅ PASS |
| `demo_kamekichi` | Kurodo Henro (FRND) | `value: "+32 2 000 00 02"` (pas de use mobile) | `"Rue de la Paix 12, 5660 Couvin"` | `json/demo_kamekichi_contacts.json` | ✅ PASS |

### BLOC D — QR Texte FR & JA de `demo_haru`
| Langue | Section Contact décodée brute | Preuve décodée | Capture écran | Verdict |
|---|---|---|---|---|
| **FR** | `☎️ [ CONTACTS ]\n  ▪️ Sakura Tanaka (Fille) +81 90 0000 0001` | `qr/qr-haru-fr.txt` | `screenshots/haru-text-qr-fr.png` | ✅ PASS |
| **JA** | `☎️ [ 緊急連絡先 ]\n  ▪️ Sakura Tanaka (娘) +81 90 0000 0001` | `qr/qr-haru-ja.txt` | `screenshots/haru-text-qr-ja.png` | ✅ PASS |

### BLOC E — Saisie Dynamique UI (SD-27 : Zéro MEDPROVR, Contact Relation Seule Absent)
| Étape | Action / Contrôle | Preuve observée | Verdict |
|---|---|---|---|
| **E.1** | Tuile Contacts active sur Haru | Tuile `id=profile_detail_tile_contacts` cliquable · `screenshots/haru-detail-contacts-tile.png` | ✅ PASS |
| **E.2** | Navigation ContactsEditFragment | Liste affichant initialement `1 contact` : Sakura Tanaka (Fille) · `screenshots/contacts-list-initial.png` | ✅ PASS |
| **E.3** | FAB Add & formulaire | Formulaire ouvert · `screenshots/contact-form-empty.png` | ✅ PASS |
| **E.4** | Saisie nom "Dr Smith" | Aucune relation `MEDPROVR` inventée (relation reste vide) · `screenshots/contact-form-filled-medprovr.png` | ✅ PASS |
| **E.5** | Saisie adresse sans téléphone | Adresse renseignée : "Aomori Station 1", téléphone laissé vide | ✅ PASS |
| **E.6** | Enregistrement & liste (2 contacts) | Liste affichant Dr Smith sans code technique · `screenshots/contacts-list-2-contacts.png` | ✅ PASS |
| **E.7** | Export FHIR Bundle vérifié | `Patient.contact[1]` contient `name: Dr Smith`, `address: Aomori Station 1`, **zéro** occurrence de `MEDPROVR` · `json/demo_haru_contacts_after_add.json` | ✅ PASS |
| **E.8** | QR texte : Dr Smith sans code technique | Ligne du QR : `▪️ Dr Smith` · `qr/qr-haru-after-medprovr.txt` · `screenshots/haru-text-qr-after-medprovr.png` | ✅ PASS |
| **E.9** | Contact « relation seule » | Soumission d'un contact avec relation seule sans coordonnées ni nom : absent de `Patient.contact` (pat-1) | ✅ PASS |
| **E.10** | Suppression & restauration | Appui long sur Dr Smith → suppression → liste restaurée à 1 contact (`screenshots/contacts-delete-dialog.png`, `screenshots/contacts-list-restored.png`) | ✅ PASS |
| **E.11** | Invariants après restauration | Vérification vérifiée 42 checks PASS (`verify-restored.md`) | ✅ PASS |

### BLOC F — Logs & Stabilité
- **Logcat épuré** : `logs/logcat-ui.txt` avec fin obligatoire `--------- scrub_logcat:`.
- **Zéro identifiant réel** : aucun `p-xxxxxxxx` non-démo.
- **Garde de publication** : tests validés (`test_publish_guard.sh`).
