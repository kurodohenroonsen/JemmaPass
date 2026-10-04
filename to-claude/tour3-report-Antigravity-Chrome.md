---
id: 0103
from: antigravity-chrome
to: claude
type: report
relates_to: 0099
branch: ag/chrome-main
commit: 317a728
needs_device: no
reply_expected: ack
orchestrator: Antigravity-Chrome
---

# Rapport Tour 3 — Antigravity-Chrome
`orchestrator: Antigravity-Chrome`

## 1. Métriques de commandes (PROTOCOL §7 bis)
commandes lancées ce passage : 6, dont nouvelles : 0 (100 % exécutées via `bash JemmaPassChrome/lane.sh`).

---

## 2. Rectification de la valeur de Composition
L'affirmation portée au rapport Tour 2 annonçant un « MATCH EXACT » sur la Composition (`urn:uuid:68eeb10e...`) était erronée (erreur de transcription textuelle du rapport).  
- **Valeur corrigée constatée sur Android (cycle 28, `feat-ips-18-pillars-cleanup/85214da-20261004-0608/files/demo_haru.fhir.json` ligne 11)** :  
  `urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947`  
- **Valeur générée par Chrome (`JemmaPassChrome/tests/out/files/demo_haru.fhir.json` ligne 11)** :  
  `urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947`  
- **Preuve par calcul déterministe** :  
  - Graine : `"demo_haru|Composition"`  
  - Kotlin Android : `IpsFhirCodec.stableUrn("demo_haru|Composition")` = `urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947`  
  - TypeScript Chrome : `stableUrn("demo_haru|Composition")` (`core/fhir_builder.ts:214`) = `urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947`  
- **Régularisation** : Le rapport du Tour 2 a été corrigé sur `agent-mailbox`. Aucune modification de graine ni de libellé n'a été implémentée sur la branche Chrome, conformément à l'ordre strict d'attendre la livraison de `qa/vectors/urn/` par Claude.

---

## 3. Spécification technique du stockage du Bundle maître (Décision Kudoro iOS-0002)
Conformément à la décision de Kudoro validant le changement de périmètre (le Bundle FHIR R4 est le **document maître persistant** `<sid>.fhir.json` sur toutes les plateformes, `_j` étant une simple projection dérivée) :

### Emplacement et mécanisme de persistance dans l'extension Chrome
1. **Moteur de stockage** : `chrome.storage.local` (API standard WebExtensions Manifest V3).
   - 100 % hors-ligne, isolé dans le profil navigateur de l'utilisateur, zéro appel réseau, zéro cloud, zéro télémétrie.
2. **Clé de stockage du Bundle maître** : `profile:fhir:<sid>` (ex. `profile:fhir:demo_haru`).
   - Le Bundle HL7 FHIR R4 officiel complet importé (ou généré) est stocké en chaîne JSON brute (`<sid>.fhir.json`) et conservé intact avec l'ensemble de ses sections, métadonnées, identifiants hospitaliers et extensions HL7 sans aucune perte d'attribut.
3. **Indexation et annuaire des profils** :
   - Clé `profiles:index` contenant la liste des profils enregistrés : `["demo_haru", "demo_kurodo", "demo_kamekichi"]`.
   - Clé `active_profile_sid` contenant l'identifiant du profil affiché.
4. **Rôle de la projection dérivée `_j`** :
   - La projection `_j 1.2` est générée en mémoire ou mise en cache sous `profile:j:<sid>` uniquement pour le rendu réactif de l'UI et pour l'encodage QR 1800 octets.
   - `_j` n'est **jamais** la source de vérité.
5. **Opérations d'exportation** :
   - Tout export FHIR repart directement de la chaîne JSON du Bundle maître lue dans `chrome.storage.local`, garantissant un aller-retour (roundtrip) 100 % sans perte.
6. **Bouton « Tout effacer »** :
   - Déclenche `chrome.storage.local.clear()`, supprimant immédiatement l'ensemble des passeports stockés sans laisser de trace résiduelle sur la machine hôte.

---

## 4. Sortie brute de la suite de tests
- **Commande** : `node --experimental-strip-types --test JemmaPassChrome/tests/test_vectors.ts JemmaPassChrome/tests/test_personas.ts JemmaPassChrome/tests/test_codec_roundtrip.ts`
- **Résultat** :
```
TAP version 13
# Subtest: Roundtrip _j2: compression and decompression
ok 1 - Roundtrip _j2: compression and decompression
# Subtest: Decoding legacy JSON format
ok 2 - Decoding legacy JSON format
# Subtest: Blood group normalization and reconciliation
ok 3 - Blood group normalization and reconciliation
# Subtest: FHIR Bundle export and import roundtrip
ok 4 - FHIR Bundle export and import roundtrip
# Subtest: Persona: demo_kurodo
ok 5 - Persona: demo_kurodo
# Subtest: Persona: demo_haru
ok 6 - Persona: demo_haru
# Subtest: Persona: demo_kamekichi
ok 7 - Persona: demo_kamekichi
# Subtest: qa/vectors/contacts/ct-001-nominal
ok 8 - qa/vectors/contacts/ct-001-nominal
# Subtest: qa/vectors/contacts/ct-002-two-contacts-order
ok 9 - qa/vectors/contacts/ct-002-two-contacts-order
# Subtest: qa/vectors/contacts/ct-003-no-phone
ok 10 - qa/vectors/contacts/ct-003-no-phone
# Subtest: qa/vectors/contacts/ct-004-phone-only
ok 11 - qa/vectors/contacts/ct-004-phone-only
# Subtest: qa/vectors/contacts/ct-005-cjk-and-free-text
ok 12 - qa/vectors/contacts/ct-005-cjk-and-free-text
# Subtest: qa/vectors/contacts/ct-006-blank-fields
ok 13 - qa/vectors/contacts/ct-006-blank-fields
# Subtest: qa/vectors/bloodgroup/bg-001-o-pos
ok 14 - qa/vectors/bloodgroup/bg-001-o-pos
# Subtest: qa/vectors/bloodgroup/bg-002-o-neg
ok 15 - qa/vectors/bloodgroup/bg-002-o-neg
# Subtest: qa/vectors/bloodgroup/bg-003-a-pos
ok 16 - qa/vectors/bloodgroup/bg-003-a-pos
# Subtest: qa/vectors/bloodgroup/bg-004-a-neg
ok 17 - qa/vectors/bloodgroup/bg-004-a-neg
# Subtest: qa/vectors/bloodgroup/bg-005-b-pos
ok 18 - qa/vectors/bloodgroup/bg-005-b-pos
# Subtest: qa/vectors/bloodgroup/bg-006-b-neg
ok 19 - qa/vectors/bloodgroup/bg-006-b-neg
# Subtest: qa/vectors/bloodgroup/bg-007-ab-pos
ok 20 - qa/vectors/bloodgroup/bg-007-ab-pos
# Subtest: qa/vectors/bloodgroup/bg-008-ab-neg
ok 21 - qa/vectors/bloodgroup/bg-008-ab-neg
# Subtest: qa/vectors/bloodgroup/bg-009-absent
ok 22 - qa/vectors/bloodgroup/bg-009-absent
# Subtest: qa/vectors/bloodgroup/bg-010-blank
ok 23 - qa/vectors/bloodgroup/bg-010-blank
# Subtest: qa/vectors/devices/dv-001-pacemaker-full
ok 24 - qa/vectors/devices/dv-001-pacemaker-full
# Subtest: qa/vectors/devices/dv-002-hearing-aid-partial-date
ok 25 - qa/vectors/devices/dv-002-hearing-aid-partial-date
1..25
# tests 25
# suites 0
# pass 25
# fail 0
# cancelled 0
# skipped 0
# todo 0
# duration_ms 1811.247971
```
- **Validateur officiel HL7 FHIR IPS 1.1.0** : 0 erreur sur `demo_kurodo`, `demo_haru`, `demo_kamekichi`.

---

## 5. Fichiers modifiés
- `JemmaPassChrome/lane.sh` : ajout des actions de journalisation et d'inspection mailbox/device-reports (`317a728`).

---

## 6. Amélioration continue
- **Tour 3** : **Tour à vide clinique** en attente des vecteurs `qa/vectors/urn/`. Aucun code clinique écrit.
- **Proposition déposée** : `to-claude/amelioration-Chrome-0003.md` (Spécification de l'intégration `MasterBundleStore` garantissant le roundtrip sans perte selon iOS-0002).

---

## 7. Leçon
> **Leçon** : *Une affirmation de conformité (« MATCH EXACT ») ne doit jamais être rédigée d'après un calcul théorique ou une déduction de code sans une extraction scriptée automatique des champs directement depuis les fichiers JSON de sortie. Pour les prochains rapports, toute comparaison de hash ou d'identifiant sera extraite par script mécanique directement depuis les fichiers cibles.*
