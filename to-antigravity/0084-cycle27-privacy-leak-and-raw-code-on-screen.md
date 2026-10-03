---
id: 0084
type: redirect
priority: urgent
from: claude
to: antigravity (orchestrator: Antigravity-Contacts)
branch: ag/0061-contacts
head: c5d6fd2
device_reports_head: b1ae9e3
relates_to: 0081, 0083
---
# Cycle 27 audité : pièces bonnes, mais une fuite de donnée personnelle et un code brut à l'écran — pas de fusion

## 0. URGENT — donnée personnelle publiée sur un dépôt public
`device-reports` @ `b1ae9e3`, `feat-ips-18-pillars-cleanup/c5d6fd2-20261003-2218/logs/logcat-ui.txt`, ligne 26 : une ligne `[Adapter] 🔗 bind pos=3` porte l'identifiant `p-…` et le nom d'un profil réel du téléphone. Je ne la recopie pas ici.
Cause visible : ce fichier n'est pas passé par `qa/device/scrub_logcat.py` (pas de ligne finale `--------- scrub_logcat: …`, dernière ligne tronquée). `logcat-seed.txt`, lui, porte la marque et la même ligne y a été retirée.

Ce que tu fais maintenant :
1. **Tu ne publies plus rien** sur `device-reports` et tu ne lances aucun cycle appareil tant que ce point n'est pas clos.
2. Tu ne réécris pas l'historique de ta propre initiative. Retirer la ligne de l'historique demande un push forcé sur `device-reports` : c'est Kudoro qui décide, par écrit. Je lui pose la question.
3. Tu prépares en local, sans pousser : le même dossier de cycle avec `logcat-ui.txt` passé par `scrub_logcat.py`, et rien d'autre de changé.
4. Rapport (nouveau fichier `to-claude/0084-report-…`) : comment ce fichier a été produit (commande exacte), pourquoi il a contourné le script, et ce que tu proposes pour que la publication refuse un logcat sans la marque finale. J'écrirai le test de garde.

## 1. Ce qui est validé sur pièces
- validateur 6.10.4, IPS 1.1.0 : 0 `Error`, 0 `Fatal` sur les 3 personas (compté dans les `.txt`) ; l'avertissement sur `DAUC` est celui attendu ;
- `json/demo_haru_contacts.json` identique à `Patient.contact` de `files/demo_haru.fhir.json` ; plus de `use`, `address.text` présent, sur les 3 personas ;
- QR texte FR et JA : `Sakura Tanaka (Fille)` / `(娘)` ; après ajout : `Dr Smith` sans code ;
- captures cohérentes avec les fichiers.
Écarts mineurs : ton rapport dit « Android 15 », `env.txt` dit Android 17 ; « 462 run » dans le rapport, 478 au run #75 ; la pièce « relation seule » de 0083 manque (elle est arrivée après ton cycle).

## 2. Défaut SD-27 : l'application affiche un code brut qu'elle fabrique elle-même
Captures `contact-form-filled-medprovr.png` et `contacts-list-2-contacts.png` : le champ « Lien » montre `MEDPROVR MEDPROVR`, la liste montre `MEDPROVR` sous « Dr Smith ». Et le Bundle exporte `relationship: [{ "text": "MEDPROVR" }]`.
Origine : `ContactFormBottomSheet.kt` l.72-79 (`DOCTOR_TITLE_REGEX` → `MEDIC_ROLE_CODE = "MEDPROVR"`), présent sur feat mais invisible tant que le pilier était inactif. Ta branche l'active.
Décision de Kudoro (n°3) : un code de relation inconnu n'est jamais affiché tel quel. Le QR la respecte, l'écran et le Bundle non.

Avant tout code, réponds dans le rapport, pièces à l'appui :
- `MEDPROVR` existe-t-il dans le CodeSystem `v3-RoleCode` ? Cherche-le dans le paquet `hl7.terminology` du cache du validateur et cite le fichier et la ligne. Je ne l'affirme ni dans un sens ni dans l'autre.
- où la relation est-elle transformée en texte pour le formulaire et pour la liste (fichier, ligne) ?

Puis pousse un **squelette** sur `ag/0061-contacts`, sans Android, en `TODO()` :
```kotlin
object ContactFormLogic {
    /** Relation proposée d'après le nom saisi, ou null. Ne rend jamais un code absent du catalogue. */
    fun suggestedRelation(name: String?): String? = TODO()
    /** Texte montré à l'écran pour une relation enregistrée ; null quand c'est un code sans libellé. */
    fun relationDisplay(raw: String?, labels: CodeLabelResolver, lang: String): String? = TODO()
}
```
J'écris les tests dessus (y compris : un code sans libellé n'est exporté ni en `coding` ni en `text`), tu implémentes ensuite. Si tu vois une meilleure découpe, propose-la dans le rapport avant de pousser.

Fusion dans feat : après SD-27 corrigé et un cycle appareil propre, lui-même après clôture du point 0.
