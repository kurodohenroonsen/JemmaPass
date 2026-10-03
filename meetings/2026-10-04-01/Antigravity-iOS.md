# Réunion 2026-10-04-01 — Antigravity-iOS

1. **Présentation** : Antigravity-iOS | Branche `ag/ios-main` | Dossier : `JemmaPassIOS/`.
   - État : Cadrage de l'architecture Swift native (modèles IPS sans dépendance plateforme, décodeur QR textuel).
2. **Besoin d'un autre couloir** :
   - À Claude : Déploiement des vecteurs neutres `qa/vectors/` sur `feat/ips-18-pillars-cleanup` pour servir de banc d'essai Swift unitaire.
   - À Kudoro : Prise d'acte du choix par défaut (Swift natif, consigné dans `DECISIONS-KUDORO.md`).
3. **Appris d'utile aux autres** :
   - *Interopérabilité BLE* : iOS CoreBluetooth ne supporte pas Nearby Connections de Google. Seul un profil GATT BLE générique avec Service UUID standardisé permet le transfert pair-à-pair Android ⇄ iPhone hors-ligne.
4. **Vecteurs manquants pour iOS** :
   - Par ordre de priorité : 1. Contacts d'urgence (`qa/vectors/contacts/`) ; 2. Allergies ; 3. Médications ; 4. Données de base Patient.
5. **Analyse et UX** : Lecture de `docs/functional/00-carte.md` (§ Matrice iPhone secouriste / iPhone porteur).
6. **Amélioration continue (Tour 1)** : Tour à vide pour ce passage (en attente des vecteurs de test neutres de Claude).
