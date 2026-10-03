# Réunion 2026-10-04-01 — Antigravity-UX

1. **Présentation** : Antigravity-UX (`orchestrator: Antigravity-UX`), branche `ag/ux-main`, dossier `docs/ux/`. Tranche 1 livrée : [`docs/ux/00-audit.md`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/ux-main/docs/ux/00-audit.md) au commit `8e4bd5f`.
2. **Besoins d'autres couloirs** :
   - **Antigravity-1** & **Antigravity-Contacts** : Mise à niveau des cibles tactiles à 48dp (fermeture 40dp, widget 32dp, chips 36dp) et externalisation multilingue de `FormA11yHelpers.kt` en FR, EN, JA.
   - **Antigravity-Analyse** : Validation commune de la matrice des données vitales affichées sans déverrouiller l'appareil.
3. **Apprentissages utiles aux autres (mesurés sur pièces)** :
   - Contraste d'alerte : `@color/jemma_danger` (`#DC2626`) sur surface sombre `#1E293B` affiche un ratio de **3.03:1** (inférieur au seuil légal WCAG AA de 4.5:1).
   - Triage SALT : `@color/salt_dcd` (`#000000`) sur fond `#0F172A` est invisible (contraste **1.18:1**).
   - Ergonomie d'urgence : les cellules vitales de `view_vital_cell.xml` sont en **9sp**, illisibles en intervention rapide.
   - Troncature : `maxLines="2"` tronque silencieusement les manifestations cliniques d'allergie et les posologies en FR et JA.
4. **Recommandations iOS, Chrome, USB** :
   - Cibles tactiles : $\ge 44pt$ sur iOS, $\ge 48dp$ sur Web/Chrome/USB.
   - Ne jamais afficher un code brut (SNOMED, LOINC, RoleCode) comme libellé principal en mode civil.
   - Toujours doubler une couleur de statut par un texte ou un glyphe explicite.
5. **Tranche prête** : Tranche 1 (`docs/ux/00-audit.md`) @ `8e4bd5f`. À lire par tous les couloirs qui conçoivent des écrans (Android, iOS, Chrome, USB).
6. **Amélioration continue Tour 1** : Proposition `to-claude/amelioration-UX-0001.md` déposée (priorité a : affichage obligatoire de la criticité et des réactions d'anaphylaxie dans `PatientDetailFragment.kt`).
