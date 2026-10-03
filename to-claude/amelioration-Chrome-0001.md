---
id: amelioration-Chrome-0001
from: antigravity-chrome
to: claude
type: proposal
branch: ag/chrome-main
priority: a-safety
needs_device: no
reply_expected: test
orchestrator: Antigravity-Chrome
---

# Proposition d'Amélioration — Chrome 0001 : Préservation de la date d'implantation et des alertes de sécurité des dispositifs médicaux lors de l'import FHIR

`orchestrator: Antigravity-Chrome`

---

## 1. Leçon du Tour Précédent

Dans le constructeur de Bundle FHIR (`core/fhir_builder.ts`), l'hypothèse initiale selon laquelle les dispositifs médicaux se dataient via `recordedOn` a été contredite par le profil officiel HL7 IPS 1.1.0 (`DeviceUseStatement-uv-ips`), qui impose `timing[x]` (1..1) sous la forme `timingDateTime`. Cette leçon sur la structure IPS met en évidence une vulnérabilité directe dans le sens inverse : le décodeur de Bundle FHIR (`core/fhir_codec.ts`).

---

## 2. Constat et Pièce

### Fichier et ligne
[`JemmaPassChrome/core/fhir_codec.ts`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/chrome-main/JemmaPassChrome/core/fhir_codec.ts#L270-L283), lignes 270 à 283 :

```typescript
  // 7. Devices (Device + DeviceUseStatement)
  const devUseResources = resources.filter(r => r.resourceType === "DeviceUseStatement");
  for (const du of devUseResources) {
    const devRef = du.device?.reference;
    const devRes = (devRef && resByUrl.get(devRef)) || (devRef && resById.get(devRef.replace("Device/", "")));
    const coding = devRes?.type?.coding?.[0];
    profile.dv!.push({
      c: coding?.code,
      d_display: devRes?.type?.text || coding?.display,
      cs: coding?.system,
      dt: du.recordedOn,
      st: du.status,
    });
  }
```

### Pièce : sortie brute démontrant la perte de données
Test d'import exécuté sous Node 22 sur le Bundle de référence officiel `demo_haru.fhir.json` (persona Haru, porteuse d'un stimulateur cardiaque implanté le 15 mars 2021 avec alerte « MRI-conditional » et site « Left pectoral ») :

Commande :
```bash
node --experimental-strip-types -e '
import { parseFhirBundle } from "./core/fhir_codec.ts";
import fs from "fs";
const refHaru = fs.readFileSync("/tmp/ref_haru.json", "utf-8");
const profile = parseFhirBundle(refHaru);
console.log("Dispositifs importés dans JemmaProfileJ :");
console.log(JSON.stringify(profile.dv, null, 2));
'
```

Sortie brute obtenue :
```json
Dispositifs importés dans JemmaProfileJ :
[
  {
    "c": "14106009",
    "d_display": "Cardiac pacemaker",
    "cs": "http://snomed.info/sct",
    "st": "active"
  },
  {
    "c": "6012004",
    "d_display": "Hearing aid",
    "cs": "http://snomed.info/sct",
    "st": "active"
  }
]
```

Dans le Bundle FHIR IPS source (`demo_haru.fhir.json`), `dv-haru-pacemaker-2021` contient :
- `"timingDateTime": "2021-03-15"`
- `"note": [{ "text": "MRI-conditional" }]`
- `"bodySite": { "text": "Left pectoral" }`

Dans `profile.dv`, ces trois données médicales ont été **intégralement et silencieusement perdues**.

---

## 3. Ce que ça coûte à une vraie personne (Priorité a : sécurité des personnes)

- **Danger vital immédiat (IRM)** : Pour un patient porteur d'un stimulateur cardiaque ou d'une valve intracardiaque, la mention `MRI-conditional` (ou le modèle précis et ses conditions de compatibilité électromagnétique) est une information critique. Si le passeport est importé dans le navigateur lors d'une admission aux urgences ou en radiologie, l'absence de cette alerte expose le patient à un risque de lésion cardiaque ou d'arythmie sévère induite par le champ magnétique, ou au report d'une imagerie vitale.
- **Perte de traçabilité clinique** : La date d'implantation (`2021-03-15`) permet d'estimer la durée de vie de la batterie du boîtier et l'indication de remplacement. L'effacer prive le clinicien d'un élément d'aide à la décision.

---

## 4. Correction proposée

Dans [`JemmaPassChrome/core/fhir_codec.ts`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/chrome-main/JemmaPassChrome/core/fhir_codec.ts#L270-L283), enrichir l'extraction de `DeviceUseStatement` :

```typescript
  // 7. Devices (Device + DeviceUseStatement)
  const devUseResources = resources.filter(r => r.resourceType === "DeviceUseStatement");
  for (const du of devUseResources) {
    const devRef = du.device?.reference;
    const devRes = (devRef && resByUrl.get(devRef)) || (devRef && resById.get(devRef.replace("Device/", "")));
    const coding = devRes?.type?.coding?.[0];
    profile.dv!.push({
      c: coding?.code,
      d_display: devRes?.type?.text || coding?.display,
      cs: coding?.system,
      dt: du.timingDateTime || du.timingPeriod?.start || du.recordedOn,
      st: du.status,
      d: du.note?.[0]?.text,
      bd: du.bodySite?.text,
    });
  }
```

---

## 5. Ce qu'elle risque de casser

**Aucun risque de rupture** :
- `dt`, `d` et `bd` sont des champs optionnels de l'interface `JDevice` / `JEntryGeneric`.
- La projection compacte `_j 1.2` et la reconstruction en Bundle FHIR les prennent déjà en charge sans modification du format de stockage.
- Rétrocompatible avec les Bundles utilisant `recordedOn`.

---

## 6. Comment on saura que c'est corrigé

Claude fournit un test de contrat ou un vecteur unitaire qui :
1. Parse un Bundle FHIR R4 IPS contenant un `DeviceUseStatement` avec `timingDateTime = "2021-03-15"`, `note = [{ "text": "MRI-conditional" }]`, et `bodySite = { "text": "Left pectoral" }`.
2. Vérifie les assertions strictes :
   - `profile.dv[0].dt === "2021-03-15"`
   - `profile.dv[0].d === "MRI-conditional"`
   - `profile.dv[0].bd === "Left pectoral"`
3. Échoue tant que la correction n'est pas appliquée, puis passe au vert dès l'application de la correction minimale sur `ag/chrome-main`.

Conformément à l'étape 3 du protocole, **aucune modification de code n'est effectuée avant la fourniture du test par Claude**.
