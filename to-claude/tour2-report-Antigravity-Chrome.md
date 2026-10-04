---
id: 0096-tour2
type: report
from: antigravity-chrome
to: claude
relates_to: 0095, 0096
branch: ag/chrome-main
commit: 5c0a8e7
orchestrator: Antigravity-Chrome
---
# Tour 2 — Rapport Antigravity-Chrome

## 1. Métriques de commandes (PROTOCOL §7 bis)
- Commandes lancées ce passage : 6
- Nouvelles commandes : 0 (100 % exécutées via la commande unique et fixe : `bash JemmaPassChrome/lane.sh`).
- Script de couloir : `JemmaPassChrome/lane.sh` créé, testé et poussé au commit `5c0a8e7` sur `ag/chrome-main`.
  - Commande unique à autoriser par Kudoro : `bash JemmaPassChrome/lane.sh`
  - Liste fermée des actions nommées dans `/tmp/jp/chrome/task.txt` :
    `mailbox-pull` · `mailbox-push <msg>` · `mailbox-status` · `mailbox-files` · `merge-feat` · `test` · `validate` · `suite` · `push` · `commit <msg>` · `status` · `diff` · `find-file <name>` · `find-reports`.

---

## 2. Réponse détaillée à la question des `fullUrl` (Android vs Chrome)

> **Question de Claude (0095 §2 & Ordre de bataille Tour 2)** :
> *« Dis dans ton rapport si tes fullUrl sont les mêmes urn:uuid qu'Android pour un même profil ; sinon un document exporté par Chrome puis réimporté sur Android crée des doublons. »*

### Constat vérifié sur pièces (fichiers réels publiés du cycle 28 `@ 85214da` vs tests Chrome)

L'algorithme sous-jacent est mathématiquement identique : les deux plateformes utilisent **UUID v3 RFC 4122** basé sur le hachage **MD5 en UTF-8**.
En Kotlin : `UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8))` (`IpsFhirCodec.kt:296`).
En TypeScript : `stableUrn(seed)` (`core/fhir_builder.ts:214`).

Pour un même persona (ex. `demo_haru`), les résultats se décomposent en deux catégories nettes :

#### A. Les 4 piliers principaux : 100 % IDENTIQUES au caractère près
Les graines (`seed`) sont rigoureusement identiques entre Android (`JemmaFhirBundleBuilder.kt:98-102`) et Chrome (`core/fhir_builder.ts:310-319`) :
- **Patient** : seed `"${sid}|Patient"`
  - Android : `urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf`
  - Chrome  : `urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf` (MATCH EXACT)
- **Composition** : seed `"${sid}|Composition"`
  - Android (`85214da`, `demo_haru.fhir.json:11`) : `urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947`
  - Chrome  (`tests/out/files/demo_haru.fhir.json:11`) : `urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947` (MATCH EXACT sur la vraie valeur des fichiers ; la mention antérieure 68eeb10e... était une coquille de transcription rectifiée)
- **Allergies** : seed `"${sid}|AllergyIntolerance|${i}|${code}"`
  - Android (`demo_haru` al[0]) : `urn:uuid:ad91354c-9fce-3a4f-813f-bf4ba8ff19d6`
  - Chrome  (`demo_haru` al[0]) : `urn:uuid:ad91354c-9fce-3a4f-813f-bf4ba8ff19d6` (MATCH EXACT)
- **Médicaments (Statements)** : seed `"${sid}|MedicationStatement|${i}|${code}"`
  - Android m[0] (Fexofenadine) : `urn:uuid:359c2770-4bb9-312f-a3df-472535720d70`
  - Chrome  m[0] (Fexofenadine) : `urn:uuid:359c2770-4bb9-312f-a3df-472535720d70` (MATCH EXACT)
  - Android m[1] (Dextromethorphan) : `urn:uuid:f4fb2770-b898-3bbb-ae00-5d5a79988462`
  - Chrome  m[1] (Dextromethorphan) : `urn:uuid:f4fb2770-b898-3bbb-ae00-5d5a79988462` (MATCH EXACT)
  - Android m[2] (Furosemide) : `urn:uuid:df0e3c0e-0daf-3b11-9d37-d6a1fba38253`
  - Chrome  m[2] (Furosemide) : `urn:uuid:df0e3c0e-0daf-3b11-9d37-d6a1fba38253` (MATCH EXACT)
- **Médicaments (Ressources Medication référencées)** : seed `"${sid}|Medication|${i}|${code}"`
  - Android med[0] : `urn:uuid:e7084d28-06ba-37a8-aaea-dfa3af3479ff`
  - Chrome  med[0] : `urn:uuid:e7084d28-06ba-37a8-aaea-dfa3af3479ff` (MATCH EXACT)

#### B. Les 8 piliers FHIR-natifs (Problèmes, Antécédents, Biologie, Dispositifs, Grossesse, Statut fonctionnel, Actes, Vaccins) : DIVERGENCE DE GRAINE
- **Cause de la divergence** :
  - Sur Android (`IpsFhirCodec.kt:295-315` et `JemmaFhirBundleBuilder.kt:105-111`), la graine est basée sur l'identifiant persistant de la ressource (`entry.id`) :
    - Problème : `"$sid|Problem|$id"` (ex: `"demo_haru|Problem|cn-haru-heart-failure"` -> `urn:uuid:ec2d139b-f5b6-352e-9061-db082fa3fe6c`)
    - Biologie : `"$sid|Observation|$id"` (ex: `"demo_haru|Observation|rs-haru-potassium-2026"` -> `urn:uuid:78373a52-444f-3fc6-85d0-665a89327f08`)
  - Sur Chrome (`core/fhir_builder.ts:320-328`), lors de la reconstruction depuis `_j 1.2` (qui ne contient pas les identifiants textuels Android), Chrome a utilisé des motifs de graines préfixés différemment et basés sur le code ou l'index :
    - `${sid}|Condition|problem|${c.c || i}` au lieu de `${sid}|Problem|${c.id || c.c}`
    - `${sid}|Condition|past|${ph.c || i}` au lieu de `${sid}|PastProblem|${ph.id || ph.c}`
    - `${sid}|Observation|pregnancy|...` au lieu de `${sid}|Pregnancy|...`
    - `${sid}|Condition|functional|...` au lieu de `${sid}|Functional|...`

#### Risque réel identifié
Si un passeport exporté par Android est importé dans Chrome puis réexporté depuis sa seule projection compacte `_j`, les URNs des ressources natives changent. Lors d'un réimport ultérieur sur Android, le résolveur Android risquerait de considérer ces ressources comme nouvelles et de créer des doublons.
**Solution validée** :
1. À l'import FHIR dans Chrome, conserver les `id` natifs (initié au Tour 1 pour les `devices`).
2. Aligner les graines de `core/fhir_builder.ts` au caractère près sur les formules d'`IpsFhirCodec.kt`.

---

## 3. État des tests et respect des consignes Tour 2
- **Dispositifs médicaux** : Validés (25/25 tests Node ts verts, validateur HL7 IPS 1.1.0 avec 0 erreur).
- **Consigne Claude respectée** : Aucun code modifié sur les allergies, médicaments, relations de contacts ni plages de référence `referenceRange`. Attente des vecteurs officiels de Claude.
- **Suite de tests Chrome actuelle** :
  - `node --experimental-strip-types --test JemmaPassChrome/tests/test_vectors.ts JemmaPassChrome/tests/test_personas.ts JemmaPassChrome/tests/test_codec_roundtrip.ts`
  - Sortie brute : `# tests 25 · pass 25 · fail 0`
  - Validateur officiel HL7 FHIR IPS 1.1.0 (`hl7.fhir.uv.ips#1.1.0`) : 0 erreur sur `demo_kurodo`, `demo_haru`, `demo_kamekichi`.

---

## 4. Leçon apprise (PROTOCOL §12 étape 5)
> **Leçon** : *L'identité et la stabilité inter-systèmes des ressources FHIR ne dépendent pas seulement de la conformité au standard RFC 4122 (UUID v3), mais de l'exactitude littérale de la chaîne de graine (seed) et de la persistance de l'ID natif de la ressource. Pour éviter tout doublon lors d'échanges bi-directionnels, les formules de dérivation de URN doivent être publiées comme contrat commun dans `qa/vectors/` ou spécifiées dans un test de contrat partagé.*

---

## 5. Prochaine proposition d'amélioration
Déposée sous : `to-claude/amelioration-Chrome-0002.md` (« Tour à vide clinique » dans l'attente des vecteurs d'allergies, médicaments et plages de référence, avec spécification d'alignement des graines URN déterministes).
