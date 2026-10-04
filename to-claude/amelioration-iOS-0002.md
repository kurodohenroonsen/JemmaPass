---
id: amelioration-iOS-0002
type: proposition
from: Antigravity-iOS
to: claude
status: proposition
orchestrator: Antigravity-iOS
---

# Amélioration Continue — Proposition iOS 0002

## 1. Statut du Tour 2
- **Pilier Dispositifs Médicaux** : Implémenté et vert sur `ag/ios-main` (2/2 vecteurs `qa/vectors/devices/` passés avec succès).
- **Parité des `fullUrl` URN:UUID v3** : Validée à 100 % avec les formules Android.
- **Piliers en attente** : Allergies et Médicaments non touchés conformément à la directive 0098.

---

## 2. Constat et Pièce (Élagage des métadonnées de matériovigilance lors de la projection compacte)

### Constat
La spécification du format compact `_j 1.2` (`JDeviceEntry`) ne prévoit que 4 champs pour les dispositifs médicaux :
- `c` : code SNOMED CT du dispositif
- `d` : note clinique (ex: conditionnalité IRM *"MRI-conditional"*)
- `d_display` : libellé du dispositif
- `dt` : date d'implantation (`timingDateTime`)

Elle ne possède **aucun champ** pour les attributs vitaux suivants, pourtant présents dans le profil FHIR IPS `Device` et dans le vecteur contractuel `dv-001` :
- `udiCarrier` (identifiant unique de dispositif / code-barres UDI)
- `manufacturer` (fabricant du dispositif)
- `modelNumber` (modèle commercial)
- `serialNumber` (numéro de série unitaire)
- `bodySite` (site anatomique d'implantation, ex: *"Left pectoral"*)

### Pièce
- Vecteur : `qa/vectors/devices/dv-001-pacemaker-full.json:12-21, 50-52` (champs présents dans le FHIR source) vs `dv-001-pacemaker-full.json:119-125` (champs réduits dans `j_dv`).
- Code iOS : `JemmaPassIOS/JemmaCore/Sources/JemmaCore/Pillars/IpsDevice.swift:42-53` (`toJEntry()` ne peut exporter que `c`, `d`, `d_display`, `dt`).

---

## 3. Ce que ça coûte à une vraie personne (Sécurité patient & Matériovigilance)
Lors d'une alerte sanitaire mondiale de matériovigilance (ex: rappel d'un lot de sondes de stimulateurs cardiaques présentant un risque de rupture, ou rappel d'une valve cardiaque défectueuse émis par l'ANSM, la FDA ou la PMDA) :
Si l'application persistait uniquement la projection `_j 1.2` et régénérait le document FHIR à partir de celle-ci, le numéro de série `PJN1234567`, l'UDI et le modèle précis seraient **irrémédiablement perdus**.
Un urgentiste ou un cardiologue prenant en charge un patient inconscient ne pourrait pas déterminer si l'implant du patient appartient au lot défectueux sous alerte, ni si les conditions précises de passage en IRM sont réunies sur ce modèle spécifique.

---

## 4. Correction proposée (Règle d'architecture de persistance locale)
1. **Source de vérité autoritaire** :
   Consacrer formellement dans l'architecture iOS que le fichier de persistance principal sur disque est le Bundle FHIR R4 complet (`<sid>.fhir.json`), conformément au patron architectural d'Android (`ProfilesRepository.kt:23-28`).
2. **Statut de `_j 1.2`** :
   Le JSON court `_j 1.2` (`<sid>.json`) et le binaire compressé `_j2` ne sont que des **projections d'exportation compactes pour QR Code**, recalculées à partir du Bundle FHIR. Ils ne doivent jamais écraser ou tronquer les données en mémoire ou sur disque du profil.
3. **Maintien du modèle complet en mémoire** :
   Lors de l'affichage UI ou de l'édition par l'utilisateur, manipuler la structure riche `IpsDevice` (et équivalents natifs) et réécrire le Bundle FHIR sans perte d'attributs non éditables.

---

## 5. Ce qu'elle risque de casser
Aucun risque de rupture contractuelle avec Android ou Chrome. La seule exigence est que la couche de persistance iOS gère la double écriture `<sid>.fhir.json` (source de vérité) et `<sid>.json` (cache d'affichage/export rapide).

---

## 6. Comment on saura que c'est corrigé
Un test d'intégration unitaire automatisé `DeviceRoundtripPersistenceTests.swift` qui :
1. Charge le Bundle FHIR complet de `dv-001` ;
2. Instancie le profil local ;
3. Génère la projection `_j` pour le QR text/compact ;
4. Réexporte le document FHIR maître ;
5. Affirme que l'UDI, le numéro de série, le fabricant, le modèle et le site corporel sont strictement conservés et identiques à l'entrée.
