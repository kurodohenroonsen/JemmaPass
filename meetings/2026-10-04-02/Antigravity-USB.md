# Réunion 2026-10-04-02 — Antigravity-USB

1. **Présentation** : Antigravity-USB | Branche `ag/usb-main` | Dossier : `JemmaPassUSB/`.
   - État : Étape 0 obligatoire terminée et mesurée sous `file://` (Google Chrome 154 via CDP, Mozilla Firefox 152 headless avec capture intégrale, Safari 26.6.2 documenté).
   - Commit : `dc63c77` sur `ag/usb-main`.
2. **Besoin d'un autre couloir** :
   - À Claude : écriture du test Node pour valider le squelette `core/session.js` (défaut n°6 de l'ordre de bataille : zéro persistance sur l'ordinateur hôte après `close()`, aucune écriture non sollicitée).
   - À Kudoro : arbitrage des 3 décisions ouvertes documentées dans `DECISIONS-KUDORO.md` (zéro persistance hôte par défaut, conteneur chiffré Web Crypto `.jemma.enc`, périmètre d'édition v1).
3. **Appris d'utile aux autres** :
   - *file:// et Same-Origin* : Sous `file://`, Google Chrome regroupe tous les fichiers locaux sous l'origine unique `file__0`. IndexedDB n'est **pas** isolé entre dossiers locaux : deux pages `file://` différentes partagent la même base (`is_shared: true`).
   - *Localisation physique* : Les données IndexedDB et `localStorage` sont enregistrées sur le disque dur de la machine hôte (`~/Library/.../IndexedDB/file__0.indexeddb.leveldb`), **jamais sur la clé USB**, et subsistent après éjection de la clé.
   - *Web Crypto sous file://* : `file://` est reconnu comme contexte sécurisé (`isSecureContext: true`). SHA-256, AES-GCM 256 bits et PBKDF2 (100 000 itérations) fonctionnent parfaitement en local sans aucune dépendance.
   - *ES Modules & Fetch* : Bloqués par CORS sous `file://` sans serveur HTTP. Les scripts doivent être classiques ordonnés (`<script src="...">`).
4. **Vecteurs de test** : Rejeu prévu des vecteurs contacts neutres (`qa/vectors/contacts/`) dès publication sur `feat` (maintenant disponible @ `2ca8e96`).
5. **Amélioration continue (Tour 1)** : Proposition `to-claude/amelioration-USB-0001.md` déposée (priorité b-vie privée : interdiction de la persistance hôte automatique, espace de travail en mémoire vive / `sessionStorage`, bouton d'effacement complet). Squelette `core/session.js` préparé pour les tests de Claude.

`orchestrator: Antigravity-USB`
