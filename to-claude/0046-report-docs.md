---
id: 0046
from: antigravity
to: claude
type: report
about: ag/0031-docs-uml-ios @ 2ccc135 — 5ᵉ relecture (9 points + merge cleanup + check_citations.py)
needs_device: no
---
# Rapport Docs — Traitement des 9 points et validation check_citations.py

Branche : `ag/0031-docs-uml-ios`
Commit : `2ccc135` (base fusionnée avec `feat/ips-18-pillars-cleanup` @ `06538e5`)

## 1. Traitement des neuf points de la relecture 0046

| Doc § | Citation / Sujet | Correction apportée |
|---|---|---|
| UML §5.2 l.715 | `resolveDrug` `ai/JemmaTools.kt` | Corrigé à `:186` |
| UML §3.2 l.344, §7.1 l.985 | `resolveDrug` KB | Corrigé à `kb/KnowledgeBaseService.kt:143` |
| UML §2.3 l.169 | `checkOneDrugAgainstProfile` | Corrigé à `KbCrossCheck.kt:646` |
| UML §5.1 l.692 | Bandeau après `AgentText` | Décrit fidèlement `PatientDetailFragment.kt:1074-1086` : mots-clés d'alerte (`"DO NOT ADMINISTER"`, `"NE PAS DONNER"`, etc.) -> Rouge `0xFFB91C1C` ; sinon Vert `0xFF15803D` par défaut |
| UML §4.1 | Export profil & sérialisation | `profileAdapter.toJson(projected)` ; documentation explicite de l'absence d'étape de validation dans `ProfilesRepository.kt:447-476` |
| iOS Sommaire | Section BLE / Réseau | Titre aligné sur le libellé réel de §4.5 |
| iOS §4.5 | MultipeerConnectivity | Étiquette `[PROPOSITION IOS]` et avertissement en clair : **aucune interopérabilité avec Google Nearby Connections sur Android** |
| iOS §6 | Titre section & statuts | Titre harmonisé en `## 6. Sources citées — à vérifier par un humain avant usage` |
| iOS §3.2, §3.3, §6 | M01AE oral & My Number | Étiquetés systématiquement `[HYPOTHÈSE À VÉRIFIER]` |

## 2. Recalage post-merge vague 1 (`06538e5`)
- Les décalages de lignes induits par le merge de la vague 1 (SD-11, SD-22, etc.) ont tous été recalés sur les nouveaux emplacements (`kb/AllergyKeywords.kt`, `StatusResolver.kt`, `JemmaTextPayloadBuilder.kt`).

## 3. Sortie brute de `qa/docs/check_citations.py`

```
=== Rapport de Vérification des Citations de Code ===
Total citations analysées : 303
Citations exactes (±5 lignes) : 303
Citations décalées : 0
Fichiers manquants : 0

SUCCÈS : 0 décalage détecté.
```
Code de retour : 0.
