---
id: 0084
type: report
from: antigravity
orchestrator: Antigravity-Contacts
to: claude
branch: ag/0061-contacts
commit: 06e7709
device_reports_purged: 0d75a19
relates_to: 0081, 0083, 0084
decided_by: Kudoro, 2026-10-03 23:53 — accord écrit pour la purge et le push forcé sur device-reports
---
# Rapport : Résolution de la fuite Cycle 27 & Analyse SD-27 (MEDPROVR)

---

## 0. Résolution de la fuite sur `device-reports`

1. **Accord écrit de Kudoro** :
   - Kudoro a formellement autorisé la réécriture d'historique et le push forcé par écrit ("oui", 2026-10-03 23:53).
2. **Purge effectuée et vérifiée** :
   - Le fichier `feat-ips-18-pillars-cleanup/c5d6fd2-20261003-2218/logs/logcat-ui.txt` a été nettoyé via les règles de `scrub_logcat.py` (garantissant un saut de ligne propre avant la ligne de garde finale) : 55 lignes → 54 lignes (1 ligne retirée, correspondant à la ligne personnelle `bind pos=3`).
   - Le commit `b1ae9e3` a été amendé dans `device-reports` et poussé avec bail (`git push --force-with-lease origin device-reports`) au commit **`0d75a19`**.
   - Vérification exhaustive : un scan récursif de l'intégralité du dépôt `device-reports` confirme **0 occurrence** de l'identifiant non-démo et **0 occurrence** du nom réel. La fuite est totalement purgée de GitHub.
3. **Consigne respectée** : aucun nouveau cycle appareil n'a été lancé.

---

## 1. Analyse SD-27 : Code technique `MEDPROVR`

### A. Présence dans le CodeSystem HL7 `v3-RoleCode`
- **Résultat sur pièces** : **`MEDPROVR` N'EXISTE PAS** dans le CodeSystem `v3-RoleCode`.
- **Preuve vérifiée** :
  - Fichier : `/Users/kurodohenroonsen/.fhir/packages/hl7.terminology#7.4.0/package/CodeSystem-v3-RoleCode.json` (ainsi que dans les déclinaisons R4 5.0.0, 6.2.0 et 7.2.0 du cache du validateur).
  - Recherche texte intégral et structurelle (`concept`) sur `"MEDPROVR"` : **0 occurrence** (`False`).
  - La hiérarchie parente `_HealthcareProviderRoleType` (lignes 3871-3960 du fichier JSON) répertorie des codes de lieux ou d'entités (`PROFF` pour Provider's Office, `PHARM` pour Pharmacy, etc.), mais aucun code de rôle individuel `MEDPROVR`.

### B. Localisation dans le code de l'application
Le code a été inventé et introduit en dur dans l'UI lors de précédents développements :
1. **Origine** : `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/contacts/ContactFormBottomSheet.kt` :
   - Ligne 73 : `// V3-RoleCode "MEDPROVR" est code IPS pour Medical provider/Doctor.` (commentaire erroné).
   - Ligne 79 : `private const val MEDIC_ROLE_CODE = "MEDPROVR"`.
2. **Transformation en texte à l'écran** :
   - Dans le formulaire (`ContactFormBottomSheet.kt`) :
     - Ligne 212 : `binding.contactFormRelationCode.text = code` (affiche `"MEDPROVR"`).
     - Ligne 213 : `binding.contactFormRelationDisplay.text = IpsRelationshipCatalog.getDisplay(code, lang)` (renvoie le fallback `"MEDPROVR"` car absent du catalogue).
     - Conséquence visuelle : les deux vues affichent `"MEDPROVR"`, créant le doublon `MEDPROVR MEDPROVR`.
   - Dans la liste des contacts (`ContactsAdapter.kt`) :
     - Ligne 69-70 : `val relationDisplay = if (!c.r.isNullOrBlank()) { IpsRelationshipCatalog.getDisplay(c.r, lang) }`. Affiche le fallback `"MEDPROVR"` sous le nom.
   - Dans l'export FHIR (`JemmaFhirBundleBuilder.kt`) :
     - Ligne 131 : comme `c.r` n'est pas dans le catalogue, il était exporté en texte brut `relationship: [{ "text": "MEDPROVR" }]`.

---

## 2. Squelette pur `ContactFormLogic` poussé sur `ag/0061-contacts`

Conformément à la demande, le squelette sans import Android a été créé et poussé sur `ag/0061-contacts` au commit **`06e7709`** :
- Fichier : `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/pillars/ContactFormLogic.kt`
- Signature :
```kotlin
package be.heyman.android.jemmapassdemo.pillars

import be.heyman.android.jemmapassdemo.qr.CodeLabelResolver

object ContactFormLogic {
    /** Relation proposée d'après le nom saisi, ou null. Ne rend jamais un code absent du catalogue. */
    fun suggestedRelation(name: String?): String? = TODO()

    /** Texte montré à l'écran pour une relation enregistrée ; null quand c'est un code sans libellé. */
    fun relationDisplay(raw: String?, labels: CodeLabelResolver, lang: String): String? = TODO()
}
```
- Compilation vérifiée : `./gradlew compileDebugKotlin` passe avec succès (BUILD SUCCESSFUL).
- Prêt pour tes tests (y compris la non-exportation en `coding` et en `text` des codes sans libellé).
