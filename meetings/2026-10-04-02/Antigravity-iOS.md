# Réunion 2026-10-04-02 — Antigravity-iOS

1. **Présentation** : Antigravity-iOS | Branche `ag/ios-main` | Dossier exclusif : `JemmaPassIOS/JemmaCore/`.
   - Rôle : Porteur du développement natif iOS (Swift 6, Swift Package pur sans dépendance tierce, cible iOS 17+ / macOS 14+).
   - État : Socle `JemmaCore` initialisé au commit `df35396` (Package.swift, modèles `_j 1.2`, JPatient, JClinicalModels, FHIR R4 Bundle builder, JemmaTextPayloadBuilder, JemmaPayloadCodec, IpsRelationshipCatalog, IpsBloodGroup).
   - Pièces : `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcrun swift test --package-path JemmaPassIOS/JemmaCore` : 4 tests exécutés, 6/6 vecteurs contacts validés, 0 failure, 0 warning.
2. **Besoin d'un autre couloir** :
   - À Claude : Réception des vecteurs neutres `qa/vectors/bloodgroup/` (Défaut 3 validé au plan 0091) pour implémenter la dérivation déterministe de l'Observation LOINC 882-1 et de la section Results.
3. **Appris d'utile aux autres** :
   - *Intégrité clinique / FHIR* : Le groupe sanguin `p.bt` saisi dans le profil patient ne doit pas disparaître du Bundle FHIR R4. Il doit être projeté sous forme d'une Observation LOINC 882-1 avec son code concept SNOMED CT du jeu libre IPS (`278147001` à `278154007`) et rattaché à une section Results (`30954-2`) pour conformité stricte IPS 1.1.0.
   - *Architecture Swift* : Séparation stricte entre un package `JemmaCore` 100 % pur testable en ligne de commande (`swift test`) et la future couche SwiftUI.
4. **Vecteurs** :
   - Contacts rejoués avec succès (6/6). Rebase en cours pour lire directement depuis `qa/vectors/contacts/` fusionné sur `feat` (`2ca8e96`).
5. **Amélioration continue (Tour 1)** :
   - Proposition déposée : `to-claude/amelioration-iOS-0001.md` (absence du groupe sanguin LOINC 882-1 dans le Bundle, priorité a-safety).
   - Prochaine étape : rebase sur `feat` @ `2ca8e96`, audit comparatif ressource par ressource avec `demo_haru.fhir.json`.
