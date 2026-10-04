---
id: 0002
couloir: Antigravity-1
type: amelioration
relates_to: 0095 §1, 0096
---
# Amélioration Antigravity-1-0002 : Découplage du tri par criticité des allergies

## 1. Constat et pièce
Dans `JemmaTextPayloadBuilder.kt:185`, le tri des allergies par criticité fait appel à `PdfPillarLayout.sortByCriticality`.
Le composant de génération de QR texte dépend ainsi directement de la mise en page d'impression PDF (`pdf/PdfPillarLayout.kt`).

## 2. Ce que ça coûte à une vraie personne
Cette dépendance artificielle rend le code fragile : toute modification ultérieure des règles d'impression PDF (ex: pagination, gestion des marges ou format Pocket Pass) risque de modifier par effet de bord l'ordonnancement d'urgence du QR texte scanné par un secouriste sur le terrain.

## 3. Correction proposée
Créer `pillars/AllergyCriticality.kt` (`be.heyman.android.jemmapassdemo.pillars`) contenant les fonctions pures :
- `criticalityLabel(criticality: String?): String`
- `criticalityRank(criticality: String?): Int`
- `sortByCriticality<T>(items: List<T>, criticalityOf: (T) -> String?): List<T>`
Remplacer les appels dans `PdfPillarLayout.kt` et `JemmaTextPayloadBuilder.kt` par des imports vers `pillars.AllergyCriticality`.

## 4. Risque de régression
Nul : il s'agit d'un simple déplacement de fonctions pures sans modification algorithmique. Les tests `PdfPillarLayoutTest` et `QrTextAllergyOrderTest` garantissent la non-régression.

## 5. Preuve de correction
`./gradlew :app:testDebugUnitTest` 525/0 vert, zéro référence à `pdf.` dans `qr/JemmaTextPayloadBuilder.kt`.
