# Device QA · feat/ips-18-pillars-cleanup · d25d69a · Pixel 9 Pro XL · Android 17 · 2026-10-02 01:10

- Exécutant : Antigravity · Hôte : macOS (Darwin x86_64) · Langue appareil : FR
- Appareil : Pixel 9 Pro XL (komodo) · Android 17 (aucun numéro de série publié)
- Build : `logs/assemble.log` · Tests JVM : 106 tests JVM, 0 failed (`logs/unit-tests.log`)
- Verdict global : **PASS** — 5/5 points ✅ · 0 ❌ · 0 ⚠️ · 0 ⏭

## Résultats du cycle 22 (fondation du pilier 🤰 Grossesses · commit d25d69a)

| Point | Statut | Preuve (fichiers publiés dans le run) | Notes / vérifications |
|---|---|---|---|
| **Point 1** — Build, tests JVM & Seed | ✅ | [`logs/assemble.log`](logs/assemble.log)<br>[`logs/unit-tests.log`](logs/unit-tests.log)<br>[`verify-seed.md`](verify-seed.md) | 106 tests unitaires JVM passés (0 échec). Seed vert (102 checks PASS) avec le nouveau compteur grossesses : 🤰 K0 H3 Ka0 et tous les compteurs habituels inchangés (🩺 K1 H2 Ka3, 📜 K2 H2 Ka0, 🧪 K4 H5 Ka1, 💉 K4 H3 Ka0, 🏥 K2 H2, 📟 H2). |
| **Point 2** — Déroulement T20 (Ressources FHIR 🤰 & non-mélange) | ✅ | [`files/demo_haru.fhir.json`](files/demo_haru.fhir.json)<br>[`qr/qr-haru-en.txt`](qr/qr-haru-en.txt)<br>[`qr/qr-haru-fr.txt`](qr/qr-haru-fr.txt)<br>[`qr/qr-haru-ja.txt`](qr/qr-haru-ja.txt) | • Observation de grossesse et section `10162-6` extraites verbatim du Bundle de Haru.<br>• Non-mélange confirmé : section Résultats `30954-2` conserve exactement ses 5 entrées (🧪 H5 inchangé), 0 fuite d'Observation de grossesse dans les résultats.<br>• QR texte Haru généré et décodé en 3 langues : section 🤰 présente, ordonnée, mono-trame (≤ 2 200 octets). |
| **Point 3** — Validateur HL7 officiel (Point clé) | ✅ | [`validator/demo_kurodo.txt`](validator/demo_kurodo.txt)<br>[`validator/demo_haru.txt`](validator/demo_haru.txt)<br>[`validator/demo_kamekichi.txt`](validator/demo_kamekichi.txt) | Validateur HL7 officiel (`validator_cli.jar` v6.10.4) exécuté sur les 3 personas : **0 erreur**. Premier passage du profil `Observation-pregnancy-outcome-uv-ips` sans aucune infraction de contrainte. |
| **Point 4** — Non-régression alertes médicament × maladie | ✅ | [`logs/logcat-ui.txt`](logs/logcat-ui.txt) | Les alertes médicament × maladie du cycle 21 sont strictement préservées : traces JEMMA-HYDRATOR à `drug×disease=2` pour Haru (Furosémide + Fexofénadine × IRC 3) et `drug×disease=2` pour Kamekichi (Warfarin + Ibuprofen × HTA), DDI de Kamekichi toujours à 5. |
| **Point 5** — Logcat scrubbé & publication | ✅ | [`logs/logcat-ui.txt`](logs/logcat-ui.txt)<br>[`logs/logcat-seed.txt`](logs/logcat-seed.txt) | Dump des 23 tags explicites du README §4, nettoyé par `scrub_logcat.py` (219 lignes conservées, 0 crash JemmaPass, 0 fuite matérielle ou personnelle). |

---

## Preuves brutes (Règle 8 : Copier-Coller strict sans reformulation)

### 1. Copie brute de l'Observation de grossesse et de la section 10162-6 de Haru (Point 2 / T20)

Extraites directement de [`files/demo_haru.fhir.json`](files/demo_haru.fhir.json) :

#### A. Section 10162-6 (History of pregnancies) dans la Composition :
```json
{
  "title": "History of Pregnancy",
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "10162-6",
        "display": "History of pregnancies Narrative"
      }
    ]
  },
  "entry": [
    {
      "reference": "urn:uuid:68fa45ea-8251-374e-b3a3-592e837b3bb3"
    },
    {
      "reference": "urn:uuid:3444b98a-1c67-3028-8c18-7a343041fc4e"
    },
    {
      "reference": "urn:uuid:ad863416-bfd6-3ce5-90bd-8dc4dec8fca2"
    }
  ]
}
```

#### B. Observation de grossesse (profil `Observation-pregnancy-outcome-uv-ips`) :
```json
{
  "resourceType": "Observation",
  "id": "pg-haru-births-total",
  "meta": {
    "profile": [
      "http://hl7.org/fhir/uv/ips/StructureDefinition/Observation-pregnancy-outcome-uv-ips"
    ]
  },
  "status": "final",
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "11640-0",
        "display": "[#] Births total"
      }
    ],
    "text": "[#] Births total"
  },
  "subject": {
    "reference": "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf"
  },
  "effectiveDateTime": "2026-02-10",
  "valueInteger": 2
}
```

---

### 2. Preuve de non-mélange avec la section Résultats 30954-2 (Point 2 / T20)

Extrait de la section `30954-2` (Results) de la Composition dans [`files/demo_haru.fhir.json`](files/demo_haru.fhir.json) :
```json
{
  "title": "Results",
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "30954-2",
        "display": "Relevant diagnostic tests/laboratory data Narrative"
      }
    ]
  },
  "entry": [
    {
      "reference": "urn:uuid:1858446d-4293-325a-ac0e-13edc47e9850"
    },
    {
      "reference": "urn:uuid:78373a52-444f-3fc6-85d0-665a89327f08"
    },
    {
      "reference": "urn:uuid:c7ff92a4-7dae-3cca-8b63-b05f5ce84c8a"
    },
    {
      "reference": "urn:uuid:4a40e360-0048-3c98-bd2a-3bb5955a2ffc"
    },
    {
      "reference": "urn:uuid:e55919e6-1333-3b82-b7b0-9aff175564af"
    }
  ]
}
```
Total des références dans la section Résultats : **5** (Potassium, Hémoglobine, DFG MDRD, Radio thorax, Groupe sanguin).
Aucune des 3 références de grossesse (`68fa45ea-...`, `3444b98a-...`, `ad863416-...`) n'y figure.
`verify_profiles.py` : `P4b expected 5 Observation: ✅`.

---

### 3. Validateur HL7 officiel sur les 3 personas (Point 2 / T20 — Point Clé)

Commande exécutée :
```bash
java -jar /tmp/fhir-validator/validator_cli.jar files/<persona>.fhir.json -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a -output validator/<persona>.txt
```

Comptage brut des erreurs (`grep -c '<td>Error</td>' validator/*.txt`) :
```text
validator/demo_haru.txt:0
validator/demo_kamekichi.txt:0
validator/demo_kurodo.txt:0
```

Comptage brut des avertissements (`grep -c '<td>Warning</td>' validator/*.txt`) :
```text
validator/demo_haru.txt:48
validator/demo_kamekichi.txt:31
validator/demo_kurodo.txt:28
```

**Erreurs HL7 relevées** : **0 erreur**.
*(Les 48 avertissements sur Haru correspondent aux 42 avertissements habituels `dom-6` + 6 avertissements sur les codes LOINC de grossesses validés sans serveur de terminologie distant).*

---

### 4. QR texte Haru EN / FR / JA (Point 2 / T20)

Décodés depuis les captures de l'écran QR :
- EN : [`screenshots/202-qr-text-en.png`](screenshots/202-qr-text-en.png) → [`qr/qr-haru-en.txt`](qr/qr-haru-en.txt)
- FR : [`screenshots/203-qr-text-fr.png`](screenshots/203-qr-text-fr.png) → [`qr/qr-haru-fr.txt`](qr/qr-haru-fr.txt)
- JA : [`screenshots/204-qr-text-ja.png`](screenshots/204-qr-text-ja.png) → [`qr/qr-haru-ja.txt`](qr/qr-haru-ja.txt)

#### A. Extrait section 🤰 en Anglais ([`qr/qr-haru-en.txt`](qr/qr-haru-en.txt)) :
```text
🤰 [ PREGNANCY HISTORY ]
  ▪️ [#] Births total: 2 — 2026-02-10
  ▪️ [#] Births.live: 2 — 2026-02-10
  ▪️ [#] Births.term: 2 — 2026-02-10
```

#### B. Extrait section 🤰 en Français ([`qr/qr-haru-fr.txt`](qr/qr-haru-fr.txt)) :
```text
🤰 [ GROSSESSES ]
  ▪️ [#] Births total: 2 — 2026-02-10
  ▪️ [#] Births.live: 2 — 2026-02-10
  ▪️ [#] Births.term: 2 — 2026-02-10
```

#### C. Extrait section 🤰 en Japonais ([`qr/qr-haru-ja.txt`](qr/qr-haru-ja.txt)) :
```text
🤰 [ 妊娠歴 ]
  ▪️ [#] Births total: 2 — 2026-02-10
  ▪️ [#] Births.live: 2 — 2026-02-10
  ▪️ [#] Births.term: 2 — 2026-02-10
```

#### D. Tailles brutes en octets (`wc -c qr/qr-haru-*.txt`) :
```text
1578 qr/qr-haru-en.txt
1651 qr/qr-haru-fr.txt
1687 qr/qr-haru-ja.txt
```
Toutes les trames sont strictement inférieures au plafond de 2 200 octets (mono-trame).

---

### 5. Non-régression alertes médicament × maladie (Point 3)

Lignes recopiées verbatim depuis [`logs/logcat-ui.txt`](logs/logcat-ui.txt) :

- **Haru Tanaka** :
```text
10-02 00:16:39.528 27987 28078 I JEMMA-HYDRATOR: [t=1790892999528] ✅ hydrated · al=1 · md=3 · cn=2 · ddi=0 · al×md=0 · drug×disease=2 · in 190ms
10-02 00:20:02.031 27987 28080 I JEMMA-HYDRATOR: [t=1790893202031] ✅ hydrated · al=1 · md=3 · cn=2 · ddi=0 · al×md=0 · drug×disease=2 · in 169ms
```
*(Furosémide + Maladie rénale chronique stade 3 [Major] & Fexofénadine + Maladie rénale chronique stade 3 [Moderate])*

- **Kamekichi** :
```text
10-02 00:16:39.310 27987 28036 I JEMMA-HYDRATOR: [t=1790892999310] ✅ hydrated · al=3 · md=5 · cn=3 · ddi=5 · al×md=0 · drug×disease=2 · in 974ms
10-02 00:24:29.362 27987 28080 I JEMMA-HYDRATOR: [t=1790893469362] ✅ hydrated · al=3 · md=5 · cn=3 · ddi=5 · al×md=0 · drug×disease=2 · in 916ms
```
*(Warfarin + Hypertension essentielle [Major], Ibuprofen + Hypertension essentielle [Moderate], et les 5 DDI toujours présentes)*

---

## 5. Fichiers publiés

```text
├── env.txt
├── files/
│   ├── demo_haru.fhir.json
│   ├── demo_haru.json
│   ├── demo_kamekichi.fhir.json
│   ├── demo_kamekichi.json
│   ├── demo_kurodo.fhir.json
│   └── demo_kurodo.json
├── logs/
│   ├── adb-devices.txt
│   ├── assemble.log
│   ├── install.log
│   ├── logcat-seed.txt
│   ├── logcat-ui.txt
│   ├── run.log
│   └── unit-tests.log
├── qr/
│   ├── qr-haru-en.txt
│   ├── qr-haru-fr.txt
│   └── qr-haru-ja.txt
├── report.md
├── screenshots/
│   ├── 202-qr-text-en.png
│   ├── 203-qr-text-fr.png
│   └── 204-qr-text-ja.png
├── steps.md
├── validator/
│   ├── demo_haru.txt
│   ├── demo_kamekichi.txt
│   └── demo_kurodo.txt
└── verify-seed.md
```
