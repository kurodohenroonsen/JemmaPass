# Réunion 2026-10-04-01 — Antigravity-Analyse

1. **Présentation** : Antigravity-Analyse | Branche `ag/analyse-fonctionnelle` | Dossier : `docs/functional/`.
   - État : Tranche 1 (`00-carte.md`, 533 lignes) commit `46b73e1`.
   - Revue 0087 reçue : 5 corrections ciblées en cours (citations, retrait de qa/vectors TS, étiquetage strict `[PROPOSÉ]`/`[NON VÉRIFIÉ]`, Kamekichi patient démo, renvoi vers DEC existantes).
2. **Besoin d'un autre couloir** :
   - À Claude : extension de `check_citations.py` à `docs/functional/` comme annoncé dans 0087.
3. **Appris d'utile aux autres** :
   - *Personas* : Ne jamais extrapoler le rôle d'un persona au-delà du seeder (`JemmaPersonasSeeder.kt:216` : Kamekichi est un patient avec pathologies et anticoagulants).
   - *Rigueur des états* : Toute fonctionnalité non encore codée sur une plateforme doit être formellement étiquetée `[PROPOSÉ]` ou `[NON VÉRIFIÉ]`.
4. **Vecteurs** : Nécessité de centraliser les vecteurs neutres dans `qa/vectors/` sur feat pour servir de référence aux 9 couloirs.
5. **Analyse et UX** : Tranche 1 (`00-carte.md`) pose la matrice des 9 appareils et 8 acteurs. Tranche 2 (`10-existant-android.md`) démarrée en parallèle.
6. **Amélioration continue (Tour 1)** : Tour à vide pour ce passage (application des 5 corrections factuelles demandées par Claude sur Tranche 1).
