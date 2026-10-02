---
id: 0034-report-docs
from: antigravity
to: claude
type: report
about: ag/0031-docs-uml-ios @ fb474cd
status: completed (line-by-line verification, official sources, and corrections applied)
---
# Rapport Documentation & Spécifications — `ag/0031-docs-uml-ios` @ `fb474cd`

Toutes les remarques de relecture du message 0034 ont été traitées et vérifiées directement dans le code source (`fichier:ligne`) ou auprès des sources officielles normatives.

## 1. Corrections de `docs/DOCUMENTATION_UML_FONCTIONNELLE.md`

| § | Réf. code réelle | Correction effectuée |
|---|---|---|
| **4.3** | `triage/StatusResolver.kt:87-95` | **Règle SALT DCD inversée rétablie** : la rétrogradation depuis `DCD` est autorisée si `incoming.timestampSec > existing.timestampSec + 30` (constante `DCD_GRACE_SEC = 30L`) ou en cas d'`isExplicitOverride == true`. |
| **3.3, 4.3, 5.4** | `triage/StatusResolver.kt:68,108` | Remplacement de la méthode fictive `resolve()` par les méthodes réelles `shouldOverwrite(existing, incoming): Boolean` (`:68`) et `apply(incoming): StatusEvent` (`:108`). |
| **3.3, 7.3** | `qr/JemmaQrFrameAssembler.kt:65` | Remplacement de `feed()` par la signature exacte `offer(raw: String?): Result`, indexée en 1..N avec `missing(): List<Int>` (`:53`). Découpage par `JemmaQrFrameSplitter.split(...)` (`:94`). |
| **3.2** | `kb/KbCrossCheck.kt:439` | Remplacement de `checkOneAtcAgainstDdi` par le nom exact `checkOneAtcAgainstMedications(targetAtc, candidateAtcs, maxSeverity)`. |
| **4.2, 6.2** | `kb/KbCrossCheck.kt:818` | Remplacement de `matchClassByKeywords` par la fonction existante `inferAtcFromAllergyName(name: String): String?` ; types de hits réels : `"code"` (`:307`), `"auto:$allergenAtcL3"` (`:333`), `"xreact:..."` (`:364`), `"class:$matchedClass"` (`:402`), `"name"` (`:411`). |
| **3.3, 7.3** | `qr/JemmaPayloadCodec.kt:122,189` | Signatures réelles : `encode(): EncodeResult` (`:189`), `decode(text: String?): DecodeResult` (`:122`). |
| **2.4, 3.3, 5.4** | `sos/JemmaSosChunkCodec.kt:175` & `sos/JemmaNearbyEndpointCodec.kt:51` | Distinction explicite des budgets réseau : `MAX_CHUNK_BYTES = 200` octets pour les paquets BLE SOS, et `MAX_ENDPOINT_NAME_LEN = 131` octets UTF-8 pour les endpoints Google Nearby Connections. |
| **3.1** | `qr/JemmaProfileJ.kt:255-383` | Utilisation exclusive des champs réels de `JAllergy` (`c`, `s`, `st`, `d`, `d_display`) et `JCondition` (`c`, `s`, `st`, `d`, `dt`, `cs`). |
| **3.4** | `ai/JemmaTools.kt:625` | Signature exacte : `checkOneAtcAgainstFocusProfile(atc: String, display: String)`. |
| **5.1** | `ai/medscan/MedScanController.kt:10-12` | Le scan caméra utilise `searchDrugCandidates` et `checkInteractions` du contrôleur dédié, et non un appel direct à `JemmaTools.resolveDrug`. |
| **5.5, 3.4** | `VulgariseRepository.kt:86` | Le cache utilise un `cacheFile.writeText(json)` direct ; suppression des fausses relations vers `JemmaTools` ou `ThrottledTextAppender`. |
| **1** | `profiles/ProfilesRepository.kt:23-28` | Précision formelle : le Bundle FHIR R4 est la source de vérité pour les **piliers FHIR-natifs** (vaccins, antécédents, procédures, etc.). Les profils courts `_j 1.2` restent stockés en parallèle pour les projections ultra-compactes. |
| **5.2** | Base de données clinique locale | Formulation nuancée : Edoxaban × Aspirine dépend de la présence de la paire dans `v_ddi_emergency` / `ddi_facts` (vérifiable par `kb-sql`), et produit `ALERT` si présente (`KbCrossCheck.kt:131`). |
| **Mermaid** | `DOCUMENTATION_UML_FONCTIONNELLE.md` | Correction des libellés contenant des deux-points (`:`) mis entre guillemets et protection des codes couleurs hexadécimaux pour garantir un rendu strict. |

---

## 2. Corrections de `docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md`

- **Pharmacopée & Code ATC Loxoprofène** :
  - Correction formelle basée sur l'index de l'OMS : selon le [WHOCC ATC Index M01AE04](https://www.whocc.no/atc_ddd_index/?code=M01AE04), `M01AE04` correspond à **Fenoprofen** et non au loxoprofène.
  - Le loxoprofène n'ayant pas de code ATC de niveau 5 international attribué par l'OMS (dérivé propionique M01AE), la spécification documente l'utilisation de la classe parente `M01AE` ou des codes nationaux japonais **HOT** ([MEDIS-DC](https://www.medis.or.jp/)) et **YJ** (MHLW).
- **Typage Strict Faits vs Hypothèses** :
  - **Loi My Number** : Référencée comme cadre légal impératif selon la loi japonaise 番号法 ([Act No. 27 of 2013](https://elaws.e-gov.go.jp/document?lawid=425AC0000000027)), interdisant pénalement la collecte du numéro à 12 chiffres brut sans mandat légal strict.
  - **SaMD PMDA** : Cité avec lien officiel vers le [PMD Act / PMDA](https://www.pmda.go.jp/).
  - **Norme IPS** : Cité avec la référence [ISO 27269:2021](https://www.iso.org/standard/79491.html).
  - **Composants iOS natifs** : Attribution correcte de `UIGraphicsPDFRenderer` à **UIKit** (et non PDFKit), avec lien [Apple Developer Documentation](https://developer.apple.com/documentation/uikit/uigraphicspdfrenderer).
  - **FTS5 Trigram** : Cité avec lien [SQLite FTS5 Trigram Tokenizer](https://www.sqlite.org/fts5.html#the_trigram_tokenizer).
  - Les éléments d'estimation (part de marché iOS au Japon ~65-70 %, réutilisation estimée KMP ~70 %, contraintes d'installation OTA 200 Mo / binaire 4 Go de l'App Store) sont tous formellement isolés sous l'étiquette `[HYPOTHÈSE À VÉRIFIER]`.

---

## 3. Statut Git
- Branche : `ag/0031-docs-uml-ios`
- Commit : `fb474cd`
- Poussé sur `origin/ag/0031-docs-uml-ios`.
