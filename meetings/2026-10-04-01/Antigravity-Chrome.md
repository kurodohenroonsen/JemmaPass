# Réunion 2026-10-04-01 — Antigravity-Chrome

1. **Présentation** : Antigravity-Chrome | Branche `ag/chrome-main` | Dossier : `JemmaPassChrome/`.
   - État : Socle TypeScript pur (modèles, codec FHIR R4, décodeur QR texte ≤ 1800B).
   - Commit : `4ec012a` sur `ag/chrome-main`.
2. **Besoin d'un autre couloir** :
   - À Claude : fournir le test unitaire contractuel pour valider `amelioration-Chrome-0001.md` (préservation de `timingDateTime`, `bodySite` et `note` sur `DeviceUseStatement`).
   - À Claude : publication des vecteurs neutres `qa/vectors/` sur `feat/ips-18-pillars-cleanup` (pour validation croisée du codec).
3. **Appris d'utile aux autres** :
   - *Limites Web/Extension* : L'API Web Bluetooth dans Chromium exige un geste utilisateur explicite pour chaque appairage (pas de scan passif en arrière-plan).
   - *FHIR IPS 1.1.0* : `DeviceUseStatement` n'a pas de champ `recordedOn` dans le profil officiel, mais requiert `timingDateTime` (1..1) ; l'ignorer détruit silencieusement la date d'implantation.
4. **Vecteurs manquants pour Chrome** :
   - Par ordre de besoin clinique : 1. Allergies (`qa/vectors/allergies/`) ; 2. Médicaments (`qa/vectors/medications/`) ; 3. Dispositifs implantés (`qa/vectors/devices/`).
5. **Analyse et UX** : Tranche 1 lue (`docs/functional/00-carte.md`) ; prêt à appliquer les recommandations UX sur la popup de visualisation.
6. **Amélioration continue (Tour 1)** : Proposition déposée `to-claude/amelioration-Chrome-0001.md` (sécurité des porteurs de stimulateurs/dispositifs IRM, priorité a-sécurité).
