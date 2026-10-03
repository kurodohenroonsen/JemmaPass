# Index des Preuves de Sources Officielles (Vérification Web)

Généré automatiquement par `curl -sSL` authentique conformément aux consignes strictes du message 0051.
Journal des requêtes HTTP : [`docs/sources/raw/FETCH.log`](raw/FETCH.log).

| Affirmation / Sujet | Domaine | Verdict | Fichier de Preuve Dédié | URL Source Consultée |
| :--- | :--- | :--- | :--- | :--- |
| **Loxoprofène ATC oral** | `PHARMA` | **CONTREDIT (la source OMS liste explicitement M01AE19)** | [01-loxoprofen-atc-oral.md](01-loxoprofen-atc-oral.md) | [https://atcddd.fhi.no/atc_ddd_index/?code=M01AE19](https://atcddd.fhi.no/atc_ddd_index/?code=M01AE19) |
| **Loxoprofène ATC topique** | `PHARMA` | **CONFIRMÉ** | [02-loxoprofen-atc-topical.md](02-loxoprofen-atc-topical.md) | [https://atcddd.fhi.no/atc_ddd_index/?code=M02AA31](https://atcddd.fhi.no/atc_ddd_index/?code=M02AA31) |
| **Fénoprofène ATC M01AE04** | `PHARMA` | **CONFIRMÉ** | [03-fenoprofen-atc.md](03-fenoprofen-atc.md) | [https://atcddd.fhi.no/atc_ddd_index/?code=M01AE04](https://atcddd.fhi.no/atc_ddd_index/?code=M01AE04) |
| **Edoxaban ATC B01AF03** | `PHARMA` | **CONFIRMÉ** | [04-edoxaban-atc.md](04-edoxaban-atc.md) | [https://atcddd.fhi.no/atc_ddd_index/?code=B01AF03](https://atcddd.fhi.no/atc_ddd_index/?code=B01AF03) |
| **KEGG D01709 & Loxonin** | `PHARMA` | **CONFIRMÉ** | [05-kegg-loxoprofen-d01709.md](05-kegg-loxoprofen-d01709.md) | [https://rest.kegg.jp/get/D01709](https://rest.kegg.jp/get/D01709) |
| **Marque Lixiana / Edoxaban** | `PHARMA` | **NON TROUVÉ (terme `Lixiana` absent du HTML téléchargé)** | [06-brand-lixiana.md](06-brand-lixiana.md) | [https://rest.kegg.jp/get/D09567](https://rest.kegg.jp/get/D09567) |
| **Codes HOT & YJ (Japon)** | `PHARMA` | **NON TROUVÉ (HTTP 404)** | [07-hot-yj-codes.md](07-hot-yj-codes.md) | [https://www.medis.or.jp/2_kaihatu/kizyun/kizyun.html](https://www.medis.or.jp/2_kaihatu/kizyun/kizyun.html) |
| **Loi My Number (Interdictions & Sanctions)** | `JAPON-LEGAL` | **NON TROUVÉ (terme `特定個人情報` absent du HTML téléchargé)** | [01-my-number-act.md](01-my-number-act.md) | [https://elaws.e-gov.go.jp/document?lawid=425AC0000000027](https://elaws.e-gov.go.jp/document?lawid=425AC0000000027) |
| **PMDA SaMD Réglementation** | `JAPON-LEGAL` | **CONFIRMÉ** | [02-pmda-samd.md](02-pmda-samd.md) | [https://www.pmda.go.jp/english/review-services/regulatory-info/0002.html](https://www.pmda.go.jp/english/review-services/regulatory-info/0002.html) |
| **URL JP Core FHIR** | `JAPON-LEGAL` | **NON TROUVÉ (terme `FHIR` absent du HTML téléchargé)** | [03-jp-core-url.md](03-jp-core-url.md) | [https://j-core.org/](https://j-core.org/) |
| **Standard JAHIS Okusuri Techou** | `JAPON-LEGAL` | **CONFIRMÉ** | [04-jahis-standard.md](04-jahis-standard.md) | [https://www.jahis.jp/standard/](https://www.jahis.jp/standard/) |
| **Part de marché iOS Japon (StatCounter)** | `JAPON-LEGAL` | **CONFIRMÉ** | [05-statcounter-ios-japan.md](05-statcounter-ios-japan.md) | [https://gs.statcounter.com/os-market-share/mobile/japan](https://gs.statcounter.com/os-market-share/mobile/japan) |
| **MultipeerConnectivity Interopérabilité Android** | `APPLE` | **CONTREDIT (MultipeerConnectivity est exclusif Apple, incompatible Android)** | [01-multipeer-interoperability.md](01-multipeer-interoperability.md) | [https://developer.apple.com/documentation/multipeerconnectivity](https://developer.apple.com/documentation/multipeerconnectivity) |
| **UIGraphicsPDFRenderer** | `APPLE` | **CONFIRMÉ** | [02-uigraphics-pdf-renderer.md](02-uigraphics-pdf-renderer.md) | [https://developer.apple.com/documentation/uikit/uigraphicspdfrenderer](https://developer.apple.com/documentation/uikit/uigraphicspdfrenderer) |
| **HealthKit Clinical Records Entitlement** | `APPLE` | **NON TROUVÉ (HTTP 404)** | [03-healthkit-clinical-records.md](03-healthkit-clinical-records.md) | [https://developer.apple.com/documentation/healthkit/accessing_health_records](https://developer.apple.com/documentation/healthkit/accessing_health_records) |
| **WidgetKit Écran Verrouillé** | `APPLE` | **CONFIRMÉ** | [04-widgetkit-lock-screen.md](04-widgetkit-lock-screen.md) | [https://developer.apple.com/documentation/widgetkit](https://developer.apple.com/documentation/widgetkit) |
| **CoreBluetooth Arrière-Plan** | `APPLE` | **CONFIRMÉ** | [05-corebluetooth-background.md](05-corebluetooth-background.md) | [https://developer.apple.com/documentation/corebluetooth](https://developer.apple.com/documentation/corebluetooth) |
| **SQLite FTS5 Trigram Tokenizer** | `TECH` | **CONFIRMÉ** | [01-sqlite-fts5-trigram.md](01-sqlite-fts5-trigram.md) | [https://www.sqlite.org/fts5.html](https://www.sqlite.org/fts5.html) |
| **Google LiteRT Runtime iOS** | `TECH` | **NON TROUVÉ (HTTP 302)** | [02-litert-ios.md](02-litert-ios.md) | [https://ai.google.dev/edge/litert](https://ai.google.dev/edge/litert) |
| **Norme ISO 27269:2021** | `TECH` | **NON TROUVÉ (HTTP 403)** | [03-iso-27269-ips.md](03-iso-27269-ips.md) | [https://www.iso.org/standard/79491.html](https://www.iso.org/standard/79491.html) |
