---
id: 0053
type: report
from: antigravity
to: claude
orchestrator: Antigravity-Docs
lane: DOCS
branch: ag/0031-docs-uml-ios
head: 0c31acf
---
# Rapport Docs & Citations (`ag/0031-docs-uml-ios` @ `0c31acf`)

orchestrator: Antigravity-Docs

## 1. Suite de tests du garde-fou (`test_check_citations.sh`)
- Exécution de `./qa/docs/tests/test_check_citations.sh` (CIT-01 à CIT-12 issus de `origin/tests/qa-guard` @ `cca0793`) :
  **12 passed, 0 failed**
- Support complet ajouté dans `qa/docs/check_citations.py` :
  - Appels `methode()`
  - Accès membres `Classe.membre`
  - Signatures complètes `methode(args): Type`
  - Validation que le membre existe bien dans la fenêtre de citation `[start_line - 5, end_line + 5]`

## 2. Exécution sur la vraie documentation (`python3 qa/docs/check_citations.py`)
```
=== Rapport de Vérification des Citations de Code ===
Total citations analysées : 298
Citations exactes (±5 lignes) : 298
Citations décalées : 0
Fichiers manquants : 0
Symboles inventés : 0

SUCCÈS : 0 décalage, 0 symbole inventé.
```

## 3. Corrections apportées dans la documentation
- **`docs/DOCUMENTATION_UML_FONCTIONNELLE.md`** :
  - Ligne 226 : alignement exact des citations : `JemmaFhirBundleBuilder` (`qr/JemmaFhirBundleBuilder.kt:56`), sa méthode `build` (`qr/JemmaFhirBundleBuilder.kt:82`), et décodage `IpsFhirCodec` (`ips/IpsFhirCodec.kt:43`).
  - Ligne 764 : alignement exact des citations maillées : `EventChunk` (`mesh/codec/EventChunk.kt:36`), `RelayManager` (`mesh/relay/RelayManager.kt:41`), `MAX_ENDPOINT_NAME_LEN` (`sos/JemmaNearbyEndpointCodec.kt:51`), et `StatusResolver` (`triage/StatusResolver.kt:46`).
- **`docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md`** :
  - Résolution de la contradiction Loxoprofène : suppression des anciennes lignes 207-208 contestant l'existence du code. Loxoprofène affirmé sous code ATC oral officiel OMS **`M01AE19`** et ATC topique **`M02AA31`**. Liens WHOCC mis à jour vers `atcddd.fhi.no`.
  - Ligne 202-203 : alignement exact sur `Loxoprofen` (`kb/KnowledgeBaseService.kt:158`), détection `nsaid` (`kb/AllergyKeywords.kt:65`), et helper `AllergyKeywords` (`kb/AllergyKeywords.kt:15`).
  - Ligne 71 (« 68.2 %, août 2026 ») conservée sous son étiquette explicite `[HYPOTHÈSE À VÉRIFIER]`.
  - Ligne 361 : renvoi explicite vers la branche dédiée `ag/0047-sources` (`docs/sources/INDEX.md`).

## 4. Retrait de `docs/sources/` de cette branche
- `docs/sources/` a été intégralement retiré de la branche `ag/0031-docs-uml-ios` via `git rm -r docs/sources` (sans `push --force`).
- Les sources restent exclusivement hébergées sur `ag/0047-sources`.

La branche `ag/0031-docs-uml-ios` @ `0c31acf` est prête pour fusion.
