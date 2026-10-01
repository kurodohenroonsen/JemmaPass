# Audit de couverture de la Base de Connaissances (KB) pour les piliers IPS restants

- Date : 2026-10-01
- Source analysée : `knowledge_full.db` (version 1.1 · 3,1 Go embarquée sur l'appareil)
- Référence des données brutes : [`kb-coverage-remaining-pillars.txt`](file:///Users/kurodohenroonsen/Documents/jemmapass-device-reports/kb/kb-coverage-remaining-pillars.txt)

---

## Synthèse générale de la KB actuelle

1. **Volume global :**
   - `terminology_codes` : 1 454 447 entrées (dont 135 541 médicaments, 69 523 pathologies/conditions, 51 470 interventions, 14 048 dispositifs).
   - `ips_valuesets` : 37 associations ValueSet-Système réparties sur 32 `vs_id` distincts, totalisant **8 757 codes**.
   - `ips_valuesets_translations` : 20 langues disponibles (ar, cs, de, es, et, fi, fr, hu, is, it, ja, ko, lt, lv, nl, no, pl, pt, ru, sv).
2. **Codes LOINC dans la base :**
   - Exactement **29 codes LOINC** présents dans `ips_valuesets`.
   - **0 code LOINC** dans `terminology_codes` (la recherche FTS5 ne couvre pas LOINC).
   - Les 29 codes LOINC sont exclusivement rédigés en anglais (`display_en`), sans aucune traduction dans `ips_valuesets_translations`.

---

## Tableau de couverture des piliers IPS analysés

| Pilier IPS | Statut | Codes disponibles | Langues | Détail ontologique & constat |
|---|---|---|---|---|
| **📜 Problèmes antérieurs**<br>*(History of Past Illness / Problems)* | **Couvert** *(Riche)* | **5 622** (Free set IPS)<br>+ 69 523 (Conditions) | **20 langues**<br>(FR: 3 596, JA: 3 512, ES: 5 265, EN: 5 622) | `problems-snomed-ct-ips-free-set` couvre massivement les diagnostics avec traductions multilingues. 46 638 conditions avec code SNOMED CT dans `terminology_codes`. Sévérité couverte par `condition-severity-uv-ips` (3 codes LOINC, en). 46 règles `ips_field_rules`. |
| **🤰 Grossesse**<br>*(Pregnancy)* | **Partiel** | **15 codes LOINC**<br>(3 status + 9 summary + 3 EDD) | **Anglais uniquement** (0 traduction) | Statut de grossesse (`pregnancy-status-uv-ips`: 3 codes), antécédents obstétricaux (`pregnancies-summary-uv-ips`: 9 codes), méthode de calcul du terme (`edd-method-uv-ips`: 3 codes). Manque les libellés FR/JA. |
| **🚬 Antécédents sociaux**<br>*(Social History : Tabac & Alcool)* | **Partiel** | **8 codes LOINC** (Tabac)<br>**0 code** (Alcool) | **Anglais uniquement** (0 traduction pour Tabac) | Tabagisme couvert par `current-smoking-status-uv-ips` (8 codes LOINC, en). **Absence totale de ValueSet ou de codes pour l'alcool**. |
| **💓 Signes vitaux**<br>*(Vital Signs)* | **Absent** | **0 code** dans `ips_valuesets` | — | Aucun ValueSet vital signs. Les codes LOINC universels (TA `85354-9`, FC `8867-4`, FR `9279-1`, Temp `8310-5`, SpO2 `2708-6`, IMC `39156-5`) doivent être embarqués dans un catalogue applicatif Kotlin dédié (comme `IpsResultCatalog.kt`). |
| **♿ Statut fonctionnel**<br>*(Functional Status)* | **Absent** | **0 code dédié** | — | Aucun ValueSet (`functional` / `disability` = 0). 0 règle pour `ClinicalImpression` dans `ips_field_rules`. Seules quelques déficiences génériques sont présentes dans le free set des problèmes SNOMED. |
| **✍️ Directives anticipées**<br>*(Advance Directives)* | **Absent** | **0 code dédié** | — | Aucun ValueSet pour le consentement ou les volontés de fin de vie. 0 règle de champ pour la ressource `Consent` dans `ips_field_rules`. |

---

## Recommandations architecturales pour les prochains sprints

1. **Pour le pilier 📜 Problèmes antérieurs (Sprint direct) :**
   - La KB est **prête immédiatement**. Le picker de recherche peut s'appuyer sur `problems-snomed-ct-ips-free-set` (avec libellés multilingues FR/JA/EN) et basculer sur `terminology_codes` (FTS5) pour les recherches avancées.
2. **Pour le pilier 🤰 Grossesse & 🚬 Tabac :**
   - Fournir les libellés d'affichage traduits en français et japonais dans le code applicatif (fichier d'alias / catalogue Kotlin), car `ips_valuesets_translations` ne possède aucune entrée pour ces ValueSets LOINC.
3. **Pour les piliers 💓 Signes vitaux, ♿ Statut fonctionnel & ✍️ Directives anticipées :**
   - Ces domaines ne disposent d'aucun support dans la base SQLite locale.
   - **Stratégie recommandée :** Adopter pour chacun de ces piliers un catalogue embarqué Kotlin compact (`IpsVitalSignsCatalog.kt`, `IpsAdvanceDirectivesCatalog.kt`), normalisé avec les codes LOINC/SNOMED officiels et leurs unités UCUM obligatoires.
