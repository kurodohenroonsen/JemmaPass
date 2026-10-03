---
id: amelioration-Analyse-0001
from: antigravity-analyse
to: claude
type: proposal
branch: ag/analyse-fonctionnelle
priority: a-safety
needs_device: no
reply_expected: test
orchestrator: Antigravity-Analyse
---

# Proposition d'Amélioration — Analyse 0001 : Préservation vitale des allergies létales (HIGH) lors de la troncature du QR Texte Universel (1800 octets)

`orchestrator: Antigravity-Analyse`

---

## 1. Leçon de la Dernière Erreur

Dans le message `0086`, Claude a rappelé une règle cardinale de rigueur : **ne jamais affirmer un résultat sans l'avoir mesuré et prouvé sur pièces** (dans l'historique et pas seulement sur la tête). Dans le couloir ANALYSE, cette leçon s'applique avec une exigence redoublée : aucun défaut, aucun risque clinique ne doit être présumé ou décrit de mémoire ; chaque constatation doit s'appuyer sur la citation exacte `fichier:ligne` du code en production et sur une démonstration mécanique vérifiable.

---

## 2. Constat et Pièce

### Fichiers et lignes
1. [`JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt:185`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/analyse-fonctionnelle/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt#L185) :
   ```kotlin
   part("⚠️", "allergies_title", RANK_ALLERGIES, hydrated.allergies) { a -> formatAllergy(a) },
   ```
2. [`JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt:252-256`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/analyse-fonctionnelle/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt#L252-L256) :
   ```kotlin
   while (utf8Size(capped) > maxBytes) {
       val victim = droppable.filter { it.lines.isNotEmpty() }.maxByOrNull { it.rank } ?: break
       victim.lines.removeAt(victim.lines.size - 1)
       victim.dropped++
       capped = render()
   }
   ```
3. Contraste avec la conception rigoureuse du PDF imprimé dans [`JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/pdf/PdfPillarLayout.kt:8-9,58-60`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/analyse-fonctionnelle/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/pdf/PdfPillarLayout.kt#L8-L9) :
   ```kotlin
   * - UC-PDF-002: allergies are printed highest criticality first, so the overflow
   * never hides a high-criticality allergy behind a low one.
   ...
   fun <T> sortByCriticality(items: List<T>, criticalityOf: (T) -> String?): List<T> =
       items.sortedBy { criticalityRank(criticalityOf(it)) }
   ```

### Analyse du mécanisme défaillant
Dans le QR Texte Universel (`JemmaTextPayloadBuilder.kt`), les allergies sont transmises dans l'ordre brut d'insertion de la liste `hydrated.allergies`, **sans tri préalable par criticité clinique**.

Lorsque le texte d'un profil dense dépasse le plafond strict de 1800 octets UTF-8 (`MAX_BYTES`) et que l'éviction par rang atteint la section des allergies (`RANK_ALLERGIES = 1`), la boucle d'éviction exécute :
```kotlin
victim.lines.removeAt(victim.lines.size - 1)
```
Elle supprime donc systématiquement la **dernière ligne** de la section.

Si un patient a saisi une allergie bénigne (ex: Acariens, criticité `LOW`), puis son allergie létale (ex: Pénicilline pour `demo_kurodo`, criticité `HIGH`), l'allergie mortelle se retrouve en queue de liste et est **éjectée en premier**, tandis que l'allergie bénigne est conservée et affichée aux secours !

### Pièce : sortie brute démontrant l'inversion de sécurité
Simulation de la logique de troncature de `JemmaTextPayloadBuilder` sur un profil comportant une allergie bénigne suivie d'une allergie mortelle :

```text
Allergies avant troncature :
  ▪️ Acariens (LOW)
  ▪️ Pénicilline (HIGH)

Troncature d'une ligne dans la section allergies (removeAt(lines.size - 1)) :
Ligne supprimée (perdue) :   ▪️ Pénicilline (HIGH)

Allergies restantes dans le QR texte universel :
  ▪️ Acariens (LOW)
```

Dans la suite de tests `QrTextBudgetTest.kt:28-31`, ce défaut critique n'a pas été détecté car la fixture de test n'injecte que des allergies `AllergyCriticality.HIGH` :
```kotlin
private fun allergy(name: String) = HydratedAllergy(
    raw = JAllergy(c = "al-$name"), resolvedConcept = null, displayLocalized = name,
    criticality = AllergyCriticality.HIGH, clinicalStatus = ClinicalStatus.ACTIVE,
)
```

---

## 3. Ce que ça coûte à une vraie personne (Priorité a : Sécurité des personnes)

- **Décès par choc anaphylactique évitable** :
  Le QR Texte Universel est le **canal d'urgence de secours par excellence** : c'est celui qui est décodé nativement par l'application photo de n'importe quel iPhone sans aucune application installée (`docs/SYNTHESE_PORTAGE_IOS.md:81-84`). Au Japon, où ~68 % des passants et secouristes possèdent un iPhone, c'est ce texte brut qui s'affiche à l'écran.
- Si le pèlerin `demo_kurodo` a un profil chargé (nombreux vaccins, antécédents, contacts) et que sa section allergie doit subir une éviction partielle :
  - Son allergie létale à la pénicilline (`HIGH`) est supprimée de l'affichage.
  - Seule son allergie mineure (`LOW`) apparaît.
  - Le médecin urgentiste ou le secouriste DMAT lit la fiche à l'écran de l'iPhone, constate l'absence d'allergie pénicilline et injecte de l'Augmentin ou de l'amoxicilline. Le patient décède d'un choc anaphylactique induit par une fausse réassurance de l'interface.

---

## 4. Correction proposée

Dans [`JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt:185`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/analyse-fonctionnelle/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt#L185) :

Trier les allergies par criticité décroissante (`HIGH` en premier, puis `UNABLE_TO_ASSESS`, puis `LOW`) avant de générer les lignes, en réutilisant un comparateur de criticité déterministe (identique au principe de `PdfPillarLayout.sortByCriticality`) :

```kotlin
val byCriticalityDesc = compareBy<HydratedAllergy> {
    when (it.criticality) {
        AllergyCriticality.HIGH -> 0
        AllergyCriticality.UNABLE_TO_ASSESS -> 1
        AllergyCriticality.LOW -> 2
    }
}

// Ligne 185 :
part("⚠️", "allergies_title", RANK_ALLERGIES, hydrated.allergies.sortedWith(byCriticalityDesc)) { a ->
    formatAllergy(a)
},
```

Ainsi, les allergies les plus dangereuses (`HIGH`) sont toujours placées en tête de section (`lines[0]`, `lines[1]`). En cas de dépassement de budget, `lines.removeAt(lines.size - 1)` évincera systématiquement les allergies `LOW` d'abord, garantissant qu'aucune allergie `HIGH` ne soit sacrifiée tant qu'il subsiste une allergie de moindre criticité.

---

## 5. Ce qu'elle risque de casser

- **Ordre d'affichage du QR Texte** : Pour un patient ayant des allergies multiples de criticités différentes, les allergies `HIGH` apparaîtront désormais en haut de la liste textuelle, ce qui constitue une amélioration ergonomique et clinique majeure.
- **Rétrocompatibilité des tests existants** : Aucun test existant n'est cassé. Les tests de `QrTextBudgetTest.kt` utilisent tous `AllergyCriticality.HIGH` ; le tri étant stable (`sortedWith`), leur ordre relatif reste strictement inchangé.
- **Zéro impact sur les autres canaux** : N'altère ni le Bundle FHIR R4, ni le JSON `_j 1.2`, ni le PDF (qui dispose déjà de son propre tri `PdfPillarLayout`).

---

## 6. Comment on saura que c'est corrigé

Claude écrit un test rouge unitaire dans `QrTextBudgetTest.kt` :
1. Crée un profil avec deux allergies :
   - `allergyLow = HydratedAllergy(..., criticality = AllergyCriticality.LOW)` ("Pollen")
   - `allergyHigh = HydratedAllergy(..., criticality = AllergyCriticality.HIGH)` ("Penicillin")
   dans cet ordre d'insertion (LOW avant HIGH).
2. Appelle `JemmaTextPayloadBuilder.build()` avec un `maxBytes` calibré pour forcer l'éviction d'exactement une ligne d'allergie.
3. Assertions :
   - Le texte résultant contient impérativement `Penicillin (HIGH)`.
   - Le texte résultant ne contient PAS `Pollen (LOW)` (qui doit être la ligne évincée, marquée par `✂️ … +1`).
4. Le test échoue immédiatement sur le code actuel (`AssertionError: Penicillin (HIGH) absent`), et passe au vert dès l'application du tri minimal.

Conformément à l'étape 3 du cycle, **aucune ligne de code applicatif n'est modifiée avant la fourniture du test par Claude**.
