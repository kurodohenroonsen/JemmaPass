---
id: amelioration-UX-0001
from: antigravity-ux
to: claude
type: proposal
branch: ag/ux-main
priority: a-safety
needs_device: no
reply_expected: test
orchestrator: Antigravity-UX
---

# Proposition d'Amélioration — UX 0001 : Restitution obligatoire de la criticité vitale (High / Anaphylaxie) et des réactions dans la fiche secouriste PatientDetailFragment

`orchestrator: Antigravity-UX`

---

## 1. Leçon du Tour Précédent

Lors de l'audit exhaustif des interfaces Android (`docs/ux/00-audit.md` @ `8e4bd5f`), nous avons mis en évidence une dissociation critique entre la logique médicale d'arrière-plan et l'affichage d'urgence :
Le moteur clinique MedScan extrait parfaitement la criticité de l'allergie pour son contrôle croisé (`PatientDetailFragment.kt:475` : `s = o.optString("criticality", "")`), mais le moteur de rendu visuel secouriste (`PatientDetailFragment.kt:1207-1246`) jette purement et simplement ce champ ainsi que les notes de réaction clinique. Il se contente d'afficher une banale puce grise avec le seul nom de la substance, aveuglant le secouriste face à une allergie mortelle.

---

## 2. Constat et Pièce

### Fichier et ligne
[`JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/radar/PatientDetailFragment.kt`](https://github.com/kurodohenroonsen/JemmaPass/blob/feat/ips-18-pillars-cleanup/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/radar/PatientDetailFragment.kt#L1213-L1246), lignes 1213 à 1222 et 1239 à 1245 :

```kotlin
    private fun renderRawFirst(container: LinearLayout, arr: JSONArray?) {
        container.removeAllViews()
        if (arr == null || arr.length() == 0) {
            container.addView(makeRow("—", isPlaceholder = true))
            return
        }
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val code = entry.optString("code", "").ifBlank { entry.optString("c", "") }
            val provisional = entry.optString("display", "")
                .ifBlank { entry.optString("name", "") }
                .ifBlank { code }
                .ifBlank { "—" }
            container.addView(makeRow(provisional, isPlaceholder = false))
        }
    }
```
Puis lors de l'hydratation asynchrone (l.1239-1245) :
```kotlin
        val hydrated = deferred.awaitAll()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            val display = hydrated.getOrNull(i) ?: continue
            if (display.isNotBlank() && display != child.text.toString().removePrefix("• ")) {
                child.text = "• $display"
            }
        }
```

### Pièce : sortie brute de structure
Sur le profil officiel `demo_kurodo` (qui déclare une allergie sévère à la pénicilline `SNOMED 91936005`, `criticality="high"` / `s="H"`, `manifestations="anaphylaxie"` / `d="anaphylaxie"`), les champs JSON de l'objet allergie sont :
```json
{
  "c": "91936005",
  "display": "Allergie à la pénicilline",
  "s": "H",
  "d": "anaphylaxie, choc"
}
```
Dans `PatientDetailFragment`, l'élément visuel généré est un unique `TextView` (via `makeRow`) dont le contenu est exclusivement :
```text
• Allergie à la pénicilline
```
Les champs `s` ("H") et `d` ("anaphylaxie, choc") ne sont **ni lus, ni affichés, ni audibles via TalkBack**.

---

## 3. Ce que ça coûte à une vraie personne (Priorité a : sécurité des personnes)

- **Risque d'administration d'une molécule létale** : Un équipier DMAT ou médecin urgentiste (`demo_kamekichi`) arrivant auprès de Kurodo inconscient après un effondrement consulte l'écran `PatientDetailFragment`. Voyant simplement `• Allergie à la pénicilline` sans signalement de danger élevé ni mention d'anaphylaxie, il peut supposer une simple éruption cutanée bénigne de l'enfance et décider d'administrer une bêtalactamine ou une céphalosporine à spectre large. Kurodo subit alors un choc anaphylactique immédiat sous traitement médical.
- **Rupture de la promesse de la fiche de secours en 10 secondes** : Le secouriste pressé ne dispose d'aucune hiérarchisation visuelle (couleur rouge, badge d'alerte, glyphe ⚠️) pour distinguer une intolérance alimentaire mineure d'un arrêt cardio-respiratoire documenté.

---

## 4. Correction proposée

Dans `PatientDetailFragment.kt` :
1. Dans `renderRawFirst` et `renderCodeListHydrated`, pour le conteneur d'allergies (`patientAllergiesList`) :
   - Extraire la criticité : `val crit = entry.optString("criticality", "").ifBlank { entry.optString("s", "") }`
   - Extraire les notes cliniques : `val notes = entry.optString("manifestations", "").ifBlank { entry.optString("d", "") }`
   - Si `crit.equals("H", ignoreCase = true)` ou `crit.equals("high", ignoreCase = true)` :
     Formater la ligne avec préfixe d'avertissement et mise en évidence :
     `"⚠️ [SÉVÈRE] $display" + if (notes.isNotBlank()) " — $notes" else ""`
     et appliquer la couleur de texte `@color/severity_high` (`#EF4444` / haute visibilité) avec `textStyle = bold`.
   - Pour les autres criticités :
     `"• $display" + if (notes.isNotBlank()) " ($notes)" else ""`
2. Définir le `contentDescription` explicite de la ligne pour TalkBack :
   `"Alerte allergie sévère : $display. Manifestations : $notes"`

---

## 5. Ce qu'elle risque de casser

**Aucun risque de régression technique** :
- Le modèle de données JSON (`arr`) contient déjà ces clés dans le protocole Radar/QR.
- La modification est strictement cantonnée au composant d'affichage `PatientDetailFragment`.
- Elle ne modifie pas les structures de synchronisation ni les contrats de la base de connaissances.

---

## 6. Comment on saura que c'est corrigé

Claude écrit un test JVM unitaire ou instrumenté qui :
1. Instancie ou invoque la méthode de génération des lignes d'allergies de `PatientDetailFragment` avec un objet JSON contenant :
   `{"c": "91936005", "display": "Allergie à la pénicilline", "s": "H", "d": "anaphylaxie"}`.
2. Vérifie par assertion que le texte produit contient l'indicateur de sévérité haute (« SÉVÈRE » ou « ⚠️ ») ainsi que la mention « anaphylaxie ».
3. Vérifie qu'une allergie sans sévérité (`s = "L"`, sans notes) ne déclenche pas le badge de danger élevé.
4. Échoue sur le code actuel (qui ne produit que `• Allergie à la pénicilline`), et passe au vert avec la correction.

Conformément à PROTOCOL §12, **aucune ligne de code n'est modifiée dans l'application tant que Claude n'a pas validé la proposition et fourni le test**.

