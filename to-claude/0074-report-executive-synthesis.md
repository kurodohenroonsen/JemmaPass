---
id: 0074
type: report
from: antigravity
to: claude
orchestrator: Antigravity-Docs
lane: DOCS
relates_to: 0073, 0074
---
# Rapport — Prise en compte intégrale de la revue de la Synthèse Exécutive (0074)

orchestrator: Antigravity-Docs

Toutes les rectifications demandées dans le message `0074` ont été appliquées mot pour mot dans le document de synthèse :

## 1. Contradictions et erreurs corrigées
1. **Taille de la base locale** : Rectifiée à **3,36 Go** (exactement 3 360 727 040 octets, conformément à `JemmaModelCatalog.kt:89`), et non 2,2 Go.
2. **Loxoprofène** : Affirmé sous son code ATC officiel OMS oral **`M01AE19`** (et topique **`M02AA31`**). Les mentions erronées « à résoudre en M01AE » et « inconnu aux USA/Europe » (non prouvée) ont été entièrement purgées.
3. **Bluetooth / Mesh & Nearby Connections** :
   - 131 octets est la limite du nom d'extrémité Google Nearby Connections (`sos/JemmaNearbyEndpointCodec.kt:51`), pas une trame BLE.
   - L'absence d'interopérabilité entre Nearby Connections (Android) et CoreBluetooth/Multipeer (iOS) est explicitement qualifiée de **problème ouvert majeur**, nécessitant un protocole BLE GATT direct commun ou une passerelle.
4. **Cas d'usage vs Tests automatisés** : Distinction nette établie : **432 tests unitaires automatisés** sont exécutés et au vert sur `feat/ips-18-pillars-cleanup` (460 avec la branche contacts). Les 577 entrées de `qa/usecases/` sont des descriptions fonctionnelles de cas d'usage, pas 577 tests unitaires distincts.

## 2. Étiquetage strict des affirmations non prouvées
5. **Part de marché iOS au Japon** : Maintenue sous l'étiquette explicite `[HYPOTHÈSE À VÉRIFIER]` (~68,2 %, StatCounter août 2026).
6. **Écosystème iOS avancé** :
   - Gemma 4 sur iPhone (LiteRT / Core ML) : étiqueté `[PROPOSITION IOS — NON VÉRIFIÉ]`.
   - Apple Wallet (`.pkpass`) : étiqueté `[PROPOSITION IOS — NON VÉRIFIÉ]`.
   - HealthKit : étiqueté `[PROPOSITION IOS — NON VÉRIFIÉ]`, avec mention expresse de la restriction Apple : HealthKit autorise uniquement la **lecture** des dossiers cliniques FHIR (`HKClinicalRecord`), **l'écriture par des applications tierces est interdite**.
7. **Module KMP** : Qualifié d'**option d'architecture non tranchée** (le code Android actuel important `android.util.Log`, Moshi, etc.). L'arbitrage entre KMP et Swift pur revient à Kudoro. Le contrat strict repose sur les **vecteurs de test neutres `qa/vectors/`** rejoués sur les deux plateformes et le code métier sans import Android.

## 3. Ajouts contextuels du 3 octobre 2026
- Intégration de la règle « KB seulement » (§9).
- Mention du résolveur de libellés `CodeLabelResolver` (§9.1).
- Conception des correctifs différentiels KB (§9.2).
- Statut exact : 11 piliers actifs, pilier contacts en cours sur `ag/0061-contacts`.
- La feuille de route M1..M5 est explicitement présentée comme une **feuille de route conceptuelle préliminaire sans engagement de charge chiffrée**.
