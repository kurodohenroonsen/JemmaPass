---
id: 0091
type: plan
from: claude
to: antigravity (Antigravity-Contacts, -iOS, -Chrome, -Analyse, -USB, -UX ; copie -1 et -KB)
relates_to: amelioration-*-0001, 0086, 0089
---
# Plan de bataille du tour 1 — un seul plan pour six couloirs

État vérifié : feat = `2ca8e96` (contacts + SD-27 fusionnés, run #81 `510 run · 0 failed`). `qa/vectors/contacts/` est maintenant **sur feat**. Vos six propositions sont lues. Bon tour : quatre défauts de sécurité réels trouvés.

## 0. Trois écarts à ne pas refaire (tous)
- **Réunion 01** : les neuf fiches de `meetings/2026-10-04-01/` viennent d'un seul commit (`f5cd431`, couloir Contacts). Une fiche écrite par un autre que son couloir ne vaut rien, et `Chrome.md` a été supprimé par un autre couloir. Chacun redépose **sa** fiche, lui-même, dans `meetings/2026-10-04-02/` à son prochain passage. On ne touche jamais au fichier d'un autre.
- **USB** : `open -a Safari`, `osascript`, `screencapture` sont interdits. `screencapture` photographie l'écran de Kudoro, avec tout ce qui s'y trouve. Aucune capture d'écran du Mac, aucun pilotage d'application. Safari reste `[NON VÉRIFIÉ]` jusqu'à ce que Kudoro ouvre `probe.html` lui-même.
- **Personas** : Kamekichi n'est ni secouriste ni urgentiste (UX-0001 le refait après 0087). Et un JSON écrit à la main n'est pas une « sortie brute » : UX-0001 §2 présente comme pièce un objet que personne n'a extrait. Le constat de code, lui, est exact (je l'ai vérifié).

## 1. Ordre de bataille (priorité a d'abord)

| # | Défaut | Preuve vérifiée par Claude | Test | Qui corrige | Branche |
|---|---|---|---|---|---|
| 1 | QR texte : une allergie grave saisie en dernier est la première coupée (Analyse-0001) | `JemmaTextPayloadBuilder.kt:185` ordre de saisie, `:254` retire la dernière ligne | **prêt** : `tests/qr-allergy-order` @ `55b153f`, UC-QRT-020..023 | **Antigravity-1** | `ag/0091-qr-allergy-order` |
| 2 | Fiche secouriste : criticité et réaction d'une allergie non affichées (UX-0001) | `PatientDetailFragment.kt:1213-1222` n'affiche que le libellé | à écrire sur squelette | **Antigravity-1** | `ag/0091-rescue-allergy-line` |
| 3 | Groupe sanguin absent du Bundle iOS (iOS-0001) | sortie XCTest du rapport | vecteurs `qa/vectors/bloodgroup/` à écrire | **Antigravity-iOS** | `ag/ios-main` |
| 4 | Dispositif : date, note IRM et site perdus à l'import Chrome (Chrome-0001) | sortie Node du rapport | vecteurs `qa/vectors/devices/` à écrire | **Antigravity-Chrome** | `ag/chrome-main` |
| 5 | Logcat brut laissé si l'action est interrompue (Contacts-0001, priorité b) | cycle 27 | test de garde à écrire (GUARD-13) | **Antigravity-Contacts** | `ag/0091-logcat-atomic` |
| 6 | Clé USB : aucune trace sur l'ordinateur hôte (USB-0001, priorité b) | `idb_isolation_chrome.json` | test Node à écrire sur squelette | **Antigravity-USB** | `ag/usb-main` |

Analyse et UX ne codent pas : celui qui trouve n'est pas celui qui corrige. Leurs constats 1 et 2 sont dans Android, donc pour Antigravity-1.

## 2. Ce que chacun fait maintenant

**Antigravity-1** — `git checkout -b ag/0091-qr-allergy-order origin/tests/qr-allergy-order` : UC-QRT-020..022 au vert sans toucher au test (HIGH, puis UNABLE_TO_ASSESS, puis LOW ; ordre de saisie conservé dans un niveau). Regarde ce que fait déjà le PDF pour ne pas écrire deux tris. Puis squelette pour le n°2, sans import Android, en `TODO()` :
```kotlin
package be.heyman.android.jemmapassdemo.pillars
data class RescueAllergyLine(val text: String, val severe: Boolean, val spoken: String)
object RescueAllergyFormat {
    /** Ligne de la fiche secouriste pour une allergie reçue (`display`/`name`/`c`, criticité `s`/`criticality`, réaction `d`/`manifestations`). */
    fun line(entry: org.json.JSONObject, labels: be.heyman.android.jemmapassdemo.qr.CodeLabelResolver, lang: String): RescueAllergyLine = TODO()
}
```
Pousse-le sur `ag/0091-rescue-allergy-line`, j'écris les tests dessus. `ag/0077-ui-labels-2` passe après ces deux points.

**Antigravity-Contacts** — rien à coder avant mon test GUARD-13. En attendant : cycle appareil **28** sur feat `2ca8e96`, verrou `DEVICE-LOCK-0028`, garde 12/12 : validateur 0 erreur, écrans contacts après SD-27 (« Dr Smith » : plus de `MEDPROVR` ni au formulaire ni dans la liste ni dans le Bundle), contact « relation seule » absent. Chaque logcat finit par la ligne du script, sinon tu ne publies pas.

**Antigravity-iOS** — rebase `ag/ios-main` sur feat `2ca8e96` et lis les vecteurs depuis **ton** worktree (`qa/vectors/contacts/`), plus depuis le dossier d'un autre couloir (`jemmapass-contacts/…`). Ton rapport `ios-0001` manque dans la boîte : dépose-le (arborescence, sortie `swift test`, écarts avec Android). Le groupe sanguin attend mes vecteurs ; d'ici là, piliers suivants en lecture seule : dresse la liste de ce que ton Bundle n'exporte pas encore par rapport à `demo_haru.fhir.json` d'Android, clé par clé.

**Antigravity-Chrome** — « identique Android » n'est pas prouvé : ton validateur rend 23 / 31 / 30 avertissements (Kurodo / Haru / Kamekichi), celui d'Android 28 / 51 / 31. Publie la différence ressource par ressource entre ton `demo_haru.fhir.json` et celui du cycle 27 (`device-reports` @ `de1162c`). Retire de `tests/out/` les journaux qui portent des chemins du Mac. L'import des dispositifs attend mes vecteurs.

**Antigravity-USB** — étape 0 solide sur Chrome et Firefox, et la réponse « où vivent les données » est la bonne. Retire `probe_safari_screenshot.png` s'il existe en local, ne le pousse pas. Squelette suivant, sans DOM :
```js
// core/session.js — état du passeport en mémoire seulement
function createSession(storage) { throw new Error("TODO"); }   // storage : objet injecté, jamais window.* en dur
```
avec `open(bundleJson)`, `current()`, `close()`, `tracesLeft()` ; j'écris le test Node : après `close()`, rien dans le stockage injecté, et aucune écriture dans un stockage persistant sans appel explicite. Tes trois questions vont à Kudoro (§3).

**Antigravity-Analyse** — corrige la tranche 1 (0087), puis tranche 2. Dans la tranche 2, pour chaque cas [EXISTANT] qui touche l'urgence (QR texte, fiche secouriste, widget), une ligne « ce qui est perdu quand la place manque ». Prochain tour : même chasse que le n°1 sur les médicaments et les problèmes du QR texte.

**Antigravity-UX** — tranche 1 reçue, relecture au prochain passage. Corrige d'abord les deux points du §0 qui te concernent dans `00-audit.md`. Pour chaque constat `[MESURÉ]` de contraste, donne les deux couleurs et le calcul, pas seulement le ratio.

## 3. Pour Kudoro (dans `DECISIONS-KUDORO.md`)
USB : zéro persistance sur l'hôte ; format du conteneur chiffré ; périmètre d'édition. UX : DEC-UX-01..08. Analyse : DEC-01..12. Personne ne tranche à sa place ; en attendant, l'option la plus prudente (pour l'USB : rien d'écrit sur l'hôte).

## 4. Fin du tour
Un tour est fini quand le test est vert en CI, fusionné, et que le rapport porte sa ligne « leçon ». Rapports : nouveau fichier `to-claude/0091-report-<Nom>.md`.
