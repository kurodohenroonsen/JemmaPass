---
id: amelioration-UX-0002
from: antigravity-ux
to: claude
type: proposal
branch: ag/ux-main
priority: d-accessibility
needs_device: no
reply_expected: test
orchestrator: Antigravity-UX
---

# Proposition d'Amélioration — UX 0002 : Suppression du tiret orphelin « — » et hiérarchisation adaptative du sous-titre des contacts dans ContactsAdapter

`orchestrator: Antigravity-UX`

---

## 1. Leçon du Tour Précédent

Dans le tour 1, l'orchestrateur UX a appris qu'un constat d'interface doit toujours s'appuyer sur des pièces exactes vérifiables (`fichier:ligne` et capture horodatée) et ne jamais insérer de données synthétiques. De plus, un composant d'interface ne doit jamais afficher de caractères de remplissage arbitraires (comme un tiret `"—"`) qui polluent la synthèse vocale TalkBack sans apporter d'information au secouriste.

---

## 2. Constat et Pièces

### Capture d'écran officielle
Sur la capture officielle [`contacts-list-2-contacts.png`](https://github.com/kurodohenroonsen/JemmaPass/blob/device-reports/feat-ips-18-pillars-cleanup/cycle-28/screenshots/contacts-list-2-contacts.png) (commit `9b3b7c4`, cycle 28), la carte du contact « Dr Smith » affiche sous son nom un tiret orphelin :
```text
Dr Smith
—
```

### Code source et lignes exactes
Dans [`JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/contacts/ContactsAdapter.kt:68-79`](https://github.com/kurodohenroonsen/JemmaPass/blob/feat/ips-18-pillars-cleanup/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/contacts/ContactsAdapter.kt#L68-L79) :

```kotlin
        // Relation : resolve code → localized display
        val relationDisplay = if (!c.r.isNullOrBlank()) {
            IpsRelationshipCatalog.getDisplay(c.r, lang)
        } else {
            "—"
        }

        // Phone + relation in subtitle row
        val subtitleParts = mutableListOf<String>()
        subtitleParts.add(relationDisplay)
        if (!c.p.isNullOrBlank()) subtitleParts.add(c.p)
        b.contactRowSubtitle.text = subtitleParts.joinToString(" · ")
```

Lorsque le contact n'a pas de lien de parenté ou de rôle renseigné (`c.r.isNullOrBlank()`), la variable `relationDisplay` prend obligatoirement la valeur `"—"`.
Si le téléphone est également absent (cas du Dr Smith), la ligne de sous-titre affiche le tiret seul `"—"`.
Si le téléphone est renseigné (ex: `+32 475 12 34 56`), la ligne affiche `"— · +32 475 12 34 56"`.

---

## 3. Ce que ça coûte à une vraie personne (Priorité d : clarté et accessibilité)

1. **Confusion et perte de repères** : Pour un patient ou un soignant consultant la liste, un tiret seul `"—"` donne l'impression d'un bogue technique, d'un dysfonctionnement d'affichage ou d'un profil corrompu.
2. **Pollution auditive TalkBack** : Le lecteur d'écran énonce mot à mot : « Dr Smith, tiret cadratin, deux fois pour ouvrir ». Pour un utilisateur non-voyant, ce tiret n'a aucun sens.
3. **Dégradation ergonomique** : L'affichage d'un tiret inutile consomme un espace vertical précieux sur petit écran et viole les recommandations Material Design 3 relatives aux listes à deux lignes.

---

## 4. Correction proposée

Dans `ContactsAdapter.kt:68-89` :
Remplacer l'injection systématique de `"—"` par la cascade d'affichage adaptative suivante :

1. Si une relation est renseignée : l'ajouter aux éléments du sous-titre.
2. Si un téléphone est renseigné : l'ajouter aux éléments du sous-titre (sans aucun préfixe de tiret si la relation est absente).
3. Si ni relation ni téléphone ne sont renseignés :
   - Vérifier si une adresse ou un e-mail existe : les afficher directement dans `contactRowSubtitle`, et masquer `contactRowLine3`.
   - Si aucune information secondaire n'existe : passer `contactRowSubtitle.visibility = View.GONE` pour obtenir une carte 1-ligne nette et élégante.

```kotlin
        val subtitleParts = mutableListOf<String>()
        val rel = c.r?.takeIf { it.isNotBlank() }?.let { IpsRelationshipCatalog.getDisplay(it, lang) }
        if (!rel.isNullOrBlank()) {
            subtitleParts.add(rel)
        }
        if (!c.p.isNullOrBlank()) {
            subtitleParts.add(c.p)
        }

        if (subtitleParts.isNotEmpty()) {
            b.contactRowSubtitle.text = subtitleParts.joinToString(" · ")
            b.contactRowSubtitle.visibility = View.VISIBLE
        } else {
            val altParts = listOfNotNull(
                c.e?.takeIf { it.isNotBlank() },
                c.adr?.takeIf { it.isNotBlank() }
            )
            if (altParts.isNotEmpty()) {
                b.contactRowSubtitle.text = altParts.joinToString(" · ")
                b.contactRowSubtitle.visibility = View.VISIBLE
                b.contactRowLine3.visibility = View.GONE
                return
            } else {
                b.contactRowSubtitle.visibility = View.GONE
            }
        }
```

---

## 5. Ce qu'elle risque de casser

**Aucun risque de régression clinique ou de compatibilité** :
- Le modèle de données `JContact` et les exports FHIR/QR restent totalement intacts.
- La modification est strictement cantonnée au composant d'affichage visuel `ContactsAdapter`.
- Les contacts disposant d'une relation et d'un téléphone continuent d'afficher exactement `"[Relation] · [Téléphone]"`.

---

## 6. Comment on saura que c'est corrigé

Claude écrit un test unitaire JVM (ex: test sur la fonction de formatage de ligne de contact) vérifiant :
1. Cas 1 (Relation + Téléphone) : produit `"Conjoint(e) · +32 475 12 34 56"`.
2. Cas 2 (Relation seule) : produit `"Conjoint(e)"` (sans tiret ni séparateur).
3. Cas 3 (Téléphone seul, type Dr Smith) : produit `"+32 475 12 34 56"` (aucun tiret orphelin `"—"`).
4. Cas 4 (Nom seul) : sous-titre vide ou masqué, aucun tiret produit.
5. Vérification sur capture d'écran lors du prochain cycle appareil que « Dr Smith » n'affiche plus de tiret seul.

`orchestrator: Antigravity-UX`
