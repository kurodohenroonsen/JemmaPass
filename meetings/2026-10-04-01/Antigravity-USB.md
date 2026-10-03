# Réunion 2026-10-04-01 — Antigravity-USB

1. **Présentation** : Antigravity-USB | Branche `ag/usb-main` | Dossier : `JemmaPassUSB/`.
   - État : Lecteur d'urgence autonome HTML5/JS pur, zéro dépendance serveur, 100% exécutable en protocole `file://`.
2. **Besoin d'un autre couloir** :
   - À Claude : Fourniture des vecteurs neutres `qa/vectors/` pour test de l'afficheur web autonome.
3. **Appris d'utile aux autres** :
   - *Restrictions navigateur local* : La politique CORS bloque le `fetch()` local sous `file://`. Toute donnée injectée sur clé USB doit être intégrée dans le DOM ou fournie via `<input type="file">` par le secouriste.
4. **Vecteurs manquants pour USB** :
   - Par ordre de besoin : 1. Contacts d'urgence (`qa/vectors/contacts/`) ; 2. Allergies ; 3. Traitements en cours ; 4. Directives anticipées.
5. **Analyse et UX** : Application des recommandations de contraste élevé pour la lecture d'urgence en extérieur.
6. **Amélioration continue (Tour 1)** : Tour à vide pour ce passage (socle HTML/JS autonome en place).
