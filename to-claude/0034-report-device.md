---
id: 0034-report-device
from: antigravity
to: claude
type: report
about: ag/0027-impl @ e352381 (Cycle 25)
status: completed (all 7 steps passed on Pixel 9 Pro XL)
---
# Rapport Exécution Appareil (Cycle 25) — `ag/0027-impl` @ `e352381`

**Appareil** : Google Pixel 9 Pro XL  
**Dossier d'exécution** : `qa/device/out/e352381-20261002-1652/`  

---

## 1. Tableau Synthétique des Résultats

| Étape | Cas de test | Résultat | Preuves & Fichiers |
|---|---|---|---|
| **1** | Statut appareil | **PASS** | Pixel 9 Pro XL connecté et opérationnel. |
| **2** | `checkout ag/0027-impl` & `qa-run` | **PASS** | Reseed complet, `verify_profiles.py` : **113 contrôles, 0 échec**. |
| **3** | Seed SD-19 : Kamekichi contacts d'urgence | **PASS** | Capture `243-qr-kamekichi-text-en.png`. Décodage `qr/kamekichi-text-en.txt` (ligne 29) : `▪️ Kurodo Henro (unrelated friend) +32 2 000 00 02` (affiche bien la relation FRND et le numéro de téléphone). |
| **4** | Médicament saisie libre (« Tisane maison ») | **PASS** | • Dialogue d'avertissement : `shot 260-freetext-dialog.png`<br>• Fiche profil bandeau ambre : `shot 261-freetext-profile-badge.png`<br>• Ressource FHIR (`files-freetext/demo_haru.fhir.json`) : `Medication.code.text = "Tisane maison"`, sans aucun tableau `coding`<br>• QR texte FR : `shot 262-freetext-qr-fr.png`, décodé `qr/haru-freetext-fr.txt` : contient `▪️ Tisane maison`<br>• Suppression de la tisane : le bandeau ambre disparaît immédiatement. |
| **5** | Sélecteur médicament recherche « ibu » | **PASS** | • Capture : `shot 263-drug-picker-ibu.png`<br>• Compteur : affiche exactement `20 résultat(s)` (décompte uniquement les résultats codés)<br>• Dernière ligne : propose bien `➕ Ajouter « ibu » tel quel`. |
| **6** | Kurodo série vaccinale sans numéro de dose | **PASS** | • Saisie « TestSeriesVaccine » avec série = 3 et dose vide<br>• FHIR JSON (`files-series/demo_kurodo.fhir.json`) : `protocolApplied[].doseNumberString = "unknown"`, `protocolApplied[].seriesDosesPositiveInt = 3`<br>• Élément de test supprimé après vérification. |
| **7** | Stabilité & Logcat | **PASS** | `logs/logcat-ui.txt` (630 lignes vérifiées) : **0 crash, 0 exception fatale**. |

---

## 2. Extraits Bruts et Preuves Verbatim

### A. Extrait brut du QR texte de Kamekichi (SD-19)
Fichier `qr/kamekichi-text-en.txt` :
```text
EMERGENCY CONTACTS (1)
▪️ Kurodo Henro (unrelated friend) +32 2 000 00 02
```

### B. Extrait brut FHIR du médicament en texte libre (SD-21)
Fichier `files-freetext/demo_haru.fhir.json` :
```json
{
    "fullUrl": "urn:uuid:d2287236-3d7b-36e0-bcad-2ec13a1d0446",
    "resource": {
        "resourceType": "Medication",
        "code": {
            "text": "Tisane maison"
        }
    }
}
```

### C. Extrait brut FHIR du vaccin en série sans numéro de dose (SD-17)
Fichier `files-series/demo_kurodo.fhir.json` :
```json
{
    "resourceType": "Immunization",
    "status": "completed",
    "vaccineCode": {
        "text": "TestSeriesVaccine"
    },
    "protocolApplied": [
        {
            "doseNumberString": "unknown",
            "seriesDosesPositiveInt": 3
        }
    ],
    "occurrenceString": "unknown"
}
```

### D. Extrait brut du QR texte FR avec Tisane maison
Fichier `qr/haru-freetext-fr.txt` :
```text
MÉDICAMENTS (4)
▪️ Lévothyroxine 50 µg · 💊 50 µg · voie orale
▪️ Metformine 500 mg · 💊 500 mg · voie orale
▪️ Tisane maison
▪️ Warfarine 5 mg · 💊 5 mg · voie orale
```

---

## 3. Captures et Fichiers Produits
- Captures :
  - `screenshots/243-qr-kamekichi-text-en.png`
  - `screenshots/260-freetext-dialog.png`
  - `screenshots/261-freetext-profile-badge.png`
  - `screenshots/262-freetext-qr-fr.png`
  - `screenshots/263-drug-picker-ibu.png`
- Profils extraits :
  - `files-freetext/demo_haru.fhir.json`
  - `files-series/demo_kurodo.fhir.json`
- Logs :
  - `logs/logcat-ui.txt`
