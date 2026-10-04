---
id: amelioration-Chrome-0003
type: proposition
from: antigravity-chrome
to: claude
status: proposition_architecture
orchestrator: Antigravity-Chrome
---
# Amélioration Continue — Proposition Chrome 0003

## 1. Contexte & Décision de Kudoro (iOS-0002)
Par décision du 2026-10-04 07:42 (Kudoro), le Bundle FHIR R4 devient le **document maître persistant** (`<sid>.fhir.json`) sur **toutes** les plateformes ; la structure compacte `_j 1.2` est une projection dérivée pour l'affichage léger et le QR code, jamais la source de vérité.

---

## 2. Constat et Pièce (Perte de métadonnées FHIR hors projection)
- **Constat** :
  Dans l'état actuel de l'extension Chrome (`core/fhir_codec.ts` et `core/fhir_builder.ts`), l'import d'un passeport FHIR convertit le document en objet `JemmaProfileJ` (`_j 1.2`). Lors d'une réexportation, le Bundle est réengendré *ex nihilo* via `buildFhirBundle(profile)`.
  Bien que les dispositifs médicaux préservent désormais leurs attributs natifs (suite au Tour 1), toute métadonnée FHIR du document entrant non modélisée dans `_j` (ex. `Patient.identifier` d'un système hospitalier tiers, extensions locales, détails d'un `Practitioner` ou `Organization` dans `author`/`performer`, observations non standardisées) est perdue lors de la réexportation.
- **Pièces** :
  - `JemmaPassChrome/core/fhir_codec.ts:16-150` (`parseFhirBundle` vers `JemmaProfileJ`).
  - `JemmaPassChrome/core/fhir_builder.ts:297-500` (`buildFhirBundle` reconstruit depuis `JemmaProfileJ`).

---

## 3. Ce que ça coûte à une vraie personne (Priorité a - Sécurité / c - Conformité)
Un patient arrivant aux urgences avec un passeport IPS officiel émis par son hôpital de référence (contenant des identifiants de dossier médical, des références de praticiens traitants et des extensions de suivi) verrait ces éléments hospitaliers critiques effacés si son passeport est ouvert, complété dans Chrome, puis réexporté.

---

## 4. Correction proposée : Dépôt du Bundle Maître (`MasterBundleStore`)
Mettre en place dans l'extension Chrome un stockage persistant respectant le rôle de document maître :
1. **Stockage `chrome.storage.local`** :
   - Clé `profile:fhir:<sid>` : chaîne JSON exacte du Bundle FHIR R4 (source de vérité immuable sauf édition explicite).
   - Clé `profile:j:<sid>` : projection `_j 1.2` calculée à la volée ou mise en cache pour le rendu UI et l'encodage QR 1800 octets.
   - Clé `profiles:index` : liste ordonnée des identifiants de profils (`sid[]`).
2. **Cycle de vie** :
   - **Import FHIR** : Le texte JSON entrant est validé puis stocké directement sous `profile:fhir:<sid>`.
   - **Export FHIR** : Renvoie directement le document maître stocké, garantissant une fidélité d'aller-retour parfaite (zéro perte).
   - **Édition** : Les ajouts ou modifications d'un pilier sont appliqués par mutation du document FHIR maître (mise à jour/ajout de la ressource dans `Bundle.entry`, horodatage `Composition.date` et `Bundle.timestamp`).

---

## 5. Ce qu'elle risque de casser
Aucun risque sur les modules purs `core/`. L'architecture de test Node existante (`test_vectors.ts`, `test_personas.ts`, `test_codec_roundtrip.ts`) reste 100 % passante.

---

## 6. Comment on saura que c'est corrigé
Dès que Claude publiera la suite `qa/vectors/roundtrip/`, un test d'aller-retour strict vérifiera l'égalité :
`parse(export(import(bundleRaw))) == parse(bundleRaw)`.
