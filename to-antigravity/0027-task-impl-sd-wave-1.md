---
id: 0027
from: claude
to: antigravity
type: task
commit: 23eeb54
branch: tests/sd-wave-1
needs_device: no
reply_expected: report (0027-report-impl.md)
---
# Couloir IMPL — vague 1 : faire passer 38 tests rouges

`tests/sd-wave-1` @ `23eeb54` = `c6f66f8` + 17 classes de tests dans `app/src/test/…/red/`.
CI de cette branche (résumé sur `ci-logs:tests-sd-wave-1/latest.md`) : **418 tests, 38 en échec, 0 erreur de compilation** — les 38 sont les rouges, voulus.
Mode d'emploi et fichiers probables à modifier, SD par SD : `qa/usecases/red-tests-wave-1.md` ; origine de chaque défaut : `qa/usecases/suspected-defects.md`.

## Règles (PROTOCOL §8)
- Branche `ag/0027-impl` créée **à partir de** `tests/sd-wave-1`. Jamais de push sur `feat/ips-18-pillars-cleanup` ni sur `tests/sd-wave-1`.
- **Aucun fichier sous `app/src/test/` n'est modifié.** Un test que tu juges faux : tu le laisses rouge et tu l'expliques dans le rapport.
- Les 380 tests existants restent verts. Les noms de champs `_j` ne changent pas.
- Un commit par SD (`fix(SD-07): …`), pour que je puisse valider ou refuser SD par SD.
- Sous-agents en parallèle par groupes de fichiers disjoints, par exemple : radio/Nearby (SD-01, 02, 04) · Bundle FHIR (SD-03, 05, 16, 17, 18) · résultats et groupe sanguin (SD-08, 10, 14) · stockage et import (SD-07, 13) · affichage et QR (SD-11, 15) · seeder (SD-19) · médicament × maladie (SD-09).

## Décisions qui ne sont pas à toi
- **SD-09** (deux des quatre tests : synonymes, pluriels irréguliers) et **SD-11** (quoi retirer du QR de Haru pour y garder le statut fonctionnel) touchent à un choix clinique ou d'usage. Implémente la version la plus prudente, et écris dans le rapport ce que tu as choisi et l'alternative : Kudoro tranchera.
- **SD-06** (profil vide = verdict CLEAN) et **SD-12** (ré-import de son propre QR) n'ont pas de test : ne les traite pas dans cette vague.
- **SD-01** : la correction va dans `StatusResolver.apply` ; `shouldOverwrite` est figé par `StatusResolverTest`.

## Rapport attendu
Branche, sha, les lignes « Unit tests » et la liste FAILED brutes de `ci-logs:ag-0027-impl/latest.md`, tableau SD → commit → fichiers modifiés → test(s) passés, ce qui reste rouge et pourquoi.
Je valide sur la CI de ta branche et sur le diff ; puis cycle téléphone + validateur HL7 avant fusion, parce que le Bundle change (SD-03, 05, 16, 17).
