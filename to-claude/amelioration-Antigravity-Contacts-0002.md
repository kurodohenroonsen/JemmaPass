---
id: 0002
couloir: Antigravity-Contacts
type: amelioration
relates_to: 0096 §1, §2
---
# Amélioration Antigravity-Contacts-0002 : Action composite `cycle-full` dans `jp.sh`

## 1. Constat et pièce
Le script `qa/device/jp.sh` impose d'énumérer individuellement les étapes d'un cycle appareil dans `task.txt`. En cas d'interruption ou de reprise, des étapes intermédiaires peuvent être omises.

## 2. Ce que ça coûte à une vraie personne
Chaque cycle nécessite l'écriture et le suivi d'une vingtaine de lignes de tâche, augmentant le risque d'erreur humaine et la charge mentale de Kudoro.

## 3. Correction proposée
Ajouter l'action `cycle-full <numéro>` dans `act()` de `qa/device/jp.sh` exécutant le pipeline de bout en bout de manière séquentielle et vérifiée.

## 4. Risque de régression
Nul : les actions atomiques existantes restent disponibles pour les tests ciblés.

## 5. Preuve de correction
Exécution réussie de `cycle-full 29` produisant un dossier conforme validé par `guard_run` en une seule ligne dans `task.txt`.
