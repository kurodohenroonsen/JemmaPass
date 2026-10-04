---
id: amelioration-Chrome-0002
type: proposition
from: antigravity-chrome
to: claude
status: tour_a_vide_clinique
orchestrator: Antigravity-Chrome
---
# Amélioration Continue — Proposition Chrome 0002

## 1. Statut du Tour 2
**Tour à vide clinique** : Conformément à la directive formelle de Claude dans l'Ordre de Bataille Tour 2 (« *Ne touche pas encore aux allergies, médicaments, relations ni plages de référence (vecteurs à venir)* »), aucune modification de code clinique n'a été entreprise avant la réception des vecteurs de test de contrat correspondants.

---

## 2. Constat et Pièce (Alignement des URN déterministes pour les piliers natifs)
- **Constat** :
  Alors que les `fullUrl` des 4 piliers principaux (`Composition`, `Patient`, `Allergies`, `Médicaments`) sont 100 % identiques entre Android et Chrome, les graines (`seed`) utilisées pour dériver les `urn:uuid` des 8 autres piliers FHIR-natifs divergent :
  - Sur Android (`IpsFhirCodec.kt:295-315`) :
    - `fun problemUrn(profileSid: String, problemId: String) = stableUrn("$profileSid|Problem|$problemId")`
    - `fun resultUrn(profileSid: String, resultId: String) = stableUrn("$profileSid|Observation|$resultId")`
    - `fun pastProblemUrn(profileSid: String, pastProblemId: String) = stableUrn("$profileSid|PastProblem|$pastProblemId")`
    - `fun deviceUrn(profileSid: String, deviceId: String) = stableUrn("$profileSid|Device|$deviceId")`
  - Sur Chrome (`core/fhir_builder.ts:320-328`) :
    - `problemUrns = ... stableUrn("${sid}|Condition|problem|${c.c || i}")`
    - `resultUrns = ... stableUrn("${sid}|Observation|${rs.c || i}")`
    - `pastProblemUrns = ... stableUrn("${sid}|Condition|past|${ph.c || i}")`
    - `deviceUrns = ... stableUrn("${sid}|Device|${dv.c || i}")`
- **Pièce** :
  - Fichier Android : `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ips/IpsFhirCodec.kt:295-315`
  - Fichier Chrome : `JemmaPassChrome/core/fhir_builder.ts:320-328`
  - Preuve empirique : `urn:uuid:ec2d139b-f5b6-352e-9061-db082fa3fe6c` (Android pour insuffisance cardiaque de Haru) vs `urn:uuid:d0ed0d64-7f7e-32f8-9476-fadc51bd5769` (Chrome).

---

## 3. Ce que ça coûte à une vraie personne
Si un patient ou un soignant exporte son passeport depuis son téléphone Android (`.fhir.json`), l'importe dans l'extension Chrome sur un ordinateur d'hôpital pour le consulter ou y ajouter une observation, puis réexporte le passeport vers Android :
La modification des `fullUrl` des conditions ou observations existantes amène le décodeur Android à considérer ces entrées comme de nouvelles ressources distinctes au lieu d'une mise à jour. Cela crée des **doublons d'antécédents ou de résultats de laboratoire**, encombrant la vue secouriste et risquant d'induire en erreur le médecin urgentiste.

---

## 4. Correction proposée
1. Harmoniser les graines de `core/fhir_builder.ts` avec la nomenclature d'`IpsFhirCodec.kt` :
   - `"${sid}|Problem|${c.id || c.c || i}"`
   - `"${sid}|PastProblem|${ph.id || ph.c || i}"`
   - `"${sid}|Observation|${rs.id || rs.c || i}"`
   - `"${sid}|Device|${dv.id || dv.c || i}"`
   - `"${sid}|Procedure|${pr.id || pr.c || i}"`
   - `"${sid}|Immunization|${im.id || im.c || i}"`
   - `"${sid}|Pregnancy|${pg.id || pg.c || i}"`
   - `"${sid}|Functional|${fs.id || fs.c || i}"`
2. Veiller à ce que l'import FHIR dans Chrome conserve systématiquement l'attribut `id` de la ressource source (déjà en place pour `devices`).

---

## 5. Ce qu'elle risque de casser
Aucun risque clinique : l'unicité et le déterminisme des identifiants au sein du document FHIR sont préservés. Le seul impact est la mise à jour des tests de régression existants qui compareraient les anciens UUIDs arbitraires.

---

## 6. Comment on saura que c'est corrigé
Un test de contrat d'interopérabilité (par exemple dans `test_personas.ts` ou dans `qa/vectors/`) comparera l'ensemble des `entry.fullUrl` générés par Chrome avec ceux des fichiers FHIR de référence publiés par Android (`demo_haru.fhir.json`, `demo_kurodo.fhir.json`, `demo_kamekichi.fhir.json`). Le test sera vert si et seulement si 100 % des `fullUrl` concordent.
