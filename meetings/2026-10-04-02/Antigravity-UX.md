# Réunion 2026-10-04-02 — Antigravity-UX

1. **Présentation** : Antigravity-UX | Branche `ag/ux-main` | Dossier exclusif : `docs/ux/` (worktree dédié `/Users/kurodohenroonsen/Documents/jemmapass-ux`).
   - Rôle : Référent ergonomie, accessibilité universelle (WCAG 2.1/2.2 AA/AAA) et design multiplateforme (Android, iOS, Chrome, USB, papier).
   - État : Tranche 1 (`docs/ux/00-audit.md`) finalisée et poussée (`1abca30`), 20 écrans audités, 52 constats `[MESURÉ]` avec formules de contraste explicites ($L_1, L_2$, fraction), 3 `[NON VÉRIFIÉ]`, 8 décisions pour Kudoro (DEC-UX-01 à 08). Persona Kamekichi rectifié (patient B+ polymédiqué, lecteurs du passeport anonymes).
2. **Besoin d'un autre couloir** :
   - À Claude : relecture de la Tranche 1 (`00-audit.md`), validation des tests pour le défaut n°2 (fiche secouriste allergies, squelette Kotlin `RescueAllergyLine`), et feu vert pour la Tranche 2 (`10-personas.md`).
   - À Kudoro : arbitrage des 8 décisions soumises dans `DECISIONS-KUDORO.md` (DEC-UX-01 à DEC-UX-08 : palette rouge d'alerte, visibilité badge DCD, masquage des codes bruts pour les civils, etc.).
   - À Antigravity-1 : implémentation du formateur `RescueAllergyFormat` sur `ag/0091-rescue-allergy-line` dès publication des tests de Claude.
3. **Appris d'utile aux autres** :
   - *Règle des 10 secondes en secours* : Les cellules vitales à 9sp (`view_vital_cell.xml`) et la troncature des allergies à 2 lignes (`item_allergy_row.xml`) constituent un risque vital direct en intervention d'urgence.
   - *Calculs de contraste WCAG exacts* : Le rouge danger `#DC2626` ($L=0.1674$) sur fond `#1E293B` ($L=0.0218$) donne un ratio de $3.03:1$, en infraction de WCAG AA (< 4.5:1). Remplacer par `#F87171` ($L=0.3296$, ratio $5.29:1$) pour le texte et les bordures d'alerte. Le badge DCD noir `#000000` est invisible sur fond sombre ($1.18:1$ à $1.44:1$).
   - *Intégrité cognitive* : Ne jamais exposer de codes bruts SNOMED, LOINC ou HL7 RoleCode aux civils sans libellé naturel traduit.
4. **Vecteurs & Tests** : Relecture des fiches et propositions d'amélioration des autres plateformes (iOS groupe sanguin, Chrome dispositifs, USB zéro trace).
5. **Amélioration continue (Tour 1)** : Proposition `to-claude/amelioration-UX-0001.md` actualisée (défaut n°2 de l'ordre de bataille 0091, priorité a-sécurité).

`orchestrator: Antigravity-UX`
