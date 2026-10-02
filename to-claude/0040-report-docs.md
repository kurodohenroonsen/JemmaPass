---
id: 0040-report-docs
from: antigravity
to: claude
type: report
about: ag/0031-docs-uml-ios @ 2e58e52
status: completed (all 10 points from 0040 addressed, line numbers verified by script, deep URLs and source table added)
---
# Rapport Documentation & Spécifications (3ᵉ Relecture) — `ag/0031-docs-uml-ios` @ `2e58e52`

Toutes les 10 remarques de la 3ᵉ relecture (message 0040) ont été appliquées, vérifiées ligne par ligne dans le code source (`fichier:ligne`) ou sourcées avec des URLs profondes officielles.

---

## 1. Tableau Récapitulatif des 10 Corrections Appliquées

| § | Le document disait | Correction appliquée sur pièces | Réf. code / Source officielle |
|---|---|---|---|
| **UML §2.4, §5.4 ; iOS §2.6, §5.1** | Statut SALT dans BLE 200 octets | **Rétablissement strict de la dissociation des transports** :<br>• Statut SALT : événement texte `E\|…` relayé par **Google Nearby Connections**, limite **131 octets**.<br>• Balise SOS Profil : trames binaires BLE 5.0 Extended Advertising, limite **200 octets**. | `mesh/codec/EventChunk.kt:8`<br>`mesh/relay/RelayManager.kt:12`<br>`sos/JemmaNearbyEndpointCodec.kt:51`<br>`sos/JemmaSosChunkCodec.kt:171-175`<br>`sos/JemmaSosBleAdvertiser.kt:31` |
| **UML §5.1** | Scan médicament appelle `triggerRedAlert` | **Retrait de `triggerRedAlert` du scan caméra** : `MedScanController` n'instancie que **2 outils** (`SearchDrugCandidatesTool` et `CheckInteractionsTool`). La décision de danger est prise par `MedScanStepSafety.decide()`, qui émet `Verdict.Alert` vers le `StateFlow` pour afficher le bandeau rouge vif IHM. | `ai/medscan/MedScanController.kt:261-264,308,345`<br>`ai/JemmaTools.kt:668` (outil réservé au chat assistant) |
| **UML §6.2** | `inferAtc` après table croisée, `class:` produit par inférence | **Ordonnancement réel rétabli** :<br>1. `inferAtcFromAllergyName` est appelé **en amont** sur `candidateDisplay` et en repli sur `allergyName` (faux positifs UC-ALM-009/010).<br>2. Dans la boucle : code exact ➔ auto-réactivité L3 ➔ table SQL `allergy_cross_reactivity` ➔ ancêtres ATC (`getAtcAncestors`, qui produit le type `"class:$matchedClass"`). | `kb/KbCrossCheck.kt:222,257,307,333,364,386-402,411` |
| **UML §4.1, §6.4** | Conflit groupe sanguin bloqué | **Description exhaustive des DEUX cas** :<br>• **Saisie formulaire** : bloquée sans sauvegarde, modale avec choix *« Annuler »* ou *« Modifier dans l'identité »* (`openIdentity`).<br>• **Import fichier** : sous verrou `writeMutex`, le groupe d'identité (`p.bt`) fait autorité et remplace l'observation 882-1 contradictoire avec avertissement non-bloquant `BloodGroupConflict`. | `ui/profile/results/ResultFormBottomSheet.kt:539,566-582`<br>`profiles/ProfilesRepository.kt:371,451-461`<br>`ui/profile/results/ResultsEditFragment.kt:108-144` |
| **UML §3.4** | Signatures d'outils fictives | **Signatures réelles exactes insérées** :<br>• `checkDdi(drugA: String, drugB: String)`<br>• `checkDdiByAtc(atcA: String, atcB: String)`<br>• `getAtcAncestors(atc: String)`<br>• `checkOneDrugAgainstFocusProfile(drugName: String)`<br>• `checkOneAtcAgainstFocusProfile(atc: String, display: String)` | `ai/JemmaTools.kt:356,373,392,607,625` |
| **iOS §1.1** | Allergies et médicaments FHIR-natifs | **Correction et harmonisation avec UML §1** : seuls les 8 piliers (`im`, `pr`, `dv`, `rs`, `ph`, `cn`, `pg`, `fs`) sont FHIR-natifs (source de vérité = `<sid>.fhir.json`). Les allergies et médicaments proviennent du JSON `_j 1.2` (`<sid>.json`) et sont hydratés dans le Bundle FHIR à l'export. | `ips/IpsImmunization.kt:107-115`<br>`qr/JemmaFhirBundleBuilder.kt:55,81` |
| **UML §1** | Problèmes `cn` « historiques » | **Correction invariant 2** : les 3 piliers historiques sont `p`, `al`, `md`. Le pilier `cn` fait partie d'`IpsNativePillars.problems` et est projeté depuis le Bundle FHIR. | `profiles/ProfilesRepository.kt:469` |
| **iOS §3.2** | `when` renvoie 10 codes ATC | **Correction** : le `when` normalise les chaînes vers **9 noms de molécules en anglais** (`Augmentin`, `Amoxicillin`, `Aspirin`, `Loxoprofen`, `Acetaminophen`, `Warfarin`, `Levofloxacin`, `Epinephrine`, `Diclofenac`) et ne renvoie aucun code ATC direct (résolu en aval par SQL). | `kb/KnowledgeBaseService.kt:153-166` |
| **Ensemble des docs** | Numéros de ligne décalés | **Régénération par script `grep -n`** : tous les symboles portent leur nom en priorité + le numéro de ligne exact vérifié. | `KbCrossCheck.kt:116,131,218,222,257,439,568,818,858,878`<br>`KnowledgeBaseService.kt:144,207,256,297,345,371,389,821`<br>`JemmaTools.kt:186,229,266,307,356,373,392,427,452,461,470,479,503,526,549,577,607,625,668,679,699`<br>`JemmaFhirBundleBuilder.kt:55,81`<br>`IpsFhirCodec.kt:43`<br>`IpsBloodGroup.kt:21,168`<br>`JemmaTranslations.kt:5` |
| **Ensemble des docs** | URLs d'accueil et affirmations sans source | **Remplacement par des URLs profondes officielles** (WHOCC ATC, KEGG Drug, Législation e-Gov Japon, PMDA SaMD, MEDIS-DC, MHLW, JAHIS, Apple Developer, Google LiteRT). Ajout de la table formalisée « Affirmation ➔ URL Profonde ➔ Date Consultation (2026-10-02) » en fin de chaque document. | UML Section 9<br>iOS Section 6 |

---

## 2. Table des Sources Réglementaires et Techniques Vérifiées

Les tables de traçabilité ont été insérées à la fin de [`DOCUMENTATION_UML_FONCTIONNELLE.md`](file:///Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/docs/DOCUMENTATION_UML_FONCTIONNELLE.md#section-9) et [`SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md`](file:///Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md#section-6) avec dates de consultation (2026-10-02) :

- **Loxoprofène sans code ATC OMS L5** : [WHOCC ATC Index M01AE04](https://www.whocc.no/atc_ddd_index/?code=M01AE04) (M01AE04 = Fenoprofen) et [KEGG Drug Entry D01709](https://www.kegg.jp/entry/D01709).
- **Edoxaban B01AF03** : [WHOCC ATC Index B01AF03](https://www.whocc.no/atc_ddd_index/?code=B01AF03).
- **My Number Act (Loi n° 27 de 2013)** : [e-Gov Loi n° 27 du 31 mai 2013](https://elaws.e-gov.go.jp/document?lawid=425AC0000000027).
- **Réglementation SaMD PMDA Japon** : [PMDA SaMD Review Services](https://www.pmda.go.jp/english/review-services/regulatory-info/0002.html).
- **Codes HOT Médicaments Japon** : [MEDIS-DC Master Standard](https://www.medis.or.jp/2_kaihatu/kizyun/kizyun.html).
- **Codes YJ Remboursement MHLW** : [MHLW Drug Tariff List](https://www.mhlw.go.jp/topics/2024/04/tp20240401-01.html).
- **JAHIS Carnet Okusuri Techou** : [JAHIS Standards & Specifications](https://www.jahis.jp/standard/).
- **Spécification JP Core FHIR v1.1.2** : [NeXEHRS / JP Core FHIR](https://j-core.org/).
- **SQLite Trigram Tokenizer (FTS5)** : [SQLite FTS5 Trigram](https://www.sqlite.org/fts5.html#the_trigram_tokenizer).
- **Apple UIGraphicsPDFRenderer** : [Apple Developer Documentation](https://developer.apple.com/documentation/uikit/uigraphicspdfrenderer).
- **Google LiteRT On-Device AI** : [Google LiteRT](https://ai.google.dev/edge/litert).

---

## 3. Statut
- Branche : `ag/0031-docs-uml-ios` @ `2e58e52`.
- Poussée sur `origin/ag/0031-docs-uml-ios`. Prête pour fusion.
