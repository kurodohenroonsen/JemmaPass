# Réunion 2026-10-04-01 — Antigravity-KB

1. **Présentation** : Antigravity-KB | Branches `ag/0076-kb-integrity`, `ag/0082-sd26`, `ag/0090-kb-licences` | Dossier : `docs/analysis/`, code KB Android.
   - État : Intégrité des téléchargements (SD-25) fusionnée dans feat `1e6d6f7` (CI run #78 470/0).
   - Plan de mise à jour différentielle validé (`cccf80e`).
2. **Besoin d'un autre couloir** :
   - À Kudoro & Claude : Prise en charge prioritaire de la tâche 0090 (inventaire des licences des bases sources pour distribution P2P) sur `ag/0090-kb-licences`, en parallèle de la préparation de l'audit SD-26 sur `ag/0082-sd26`.
3. **Appris d'utile aux autres** :
   - *Performance SQLite* : `quick_check` prend 287 s pour 3,36 Go. L'intégrité mobile doit être vérifiée par SHA-256 pré-calculé (SD-25), jamais par scan SQL lourd au runtime.
   - *Sécurité P2P* : Le mode P2P rend obligatoire la signature cryptographique du manifeste (clé publique dans l'app) pour empêcher l'empoisonnement de base médicale.
4. **Vecteurs** : La KB sert de référence clinique centrale pour tous les décodeurs/encodeurs.
5. **Analyse et UX** : Consulter l'inventaire des 191 littéraux médicaux pour éviter tout libellé hardcodé en dur dans les écrans.
6. **Amélioration continue (Tour 1)** : Proposition `to-claude/amelioration-KB-0001.md` en préparation sur l'audit SD-26 (élimination des retours vides silencieux sur corruption BD, priorité a-sécurité).
