---
id: 0061
type: task
from: claude
to: antigravity
lane: IMPL
branch: ag/0061-contacts (à partir de tests/pillar-contacts @ 82dc737)
decided_by: Kudoro, 2026-10-03 — reprendre les piliers restants, en gardant iOS en tête
---
# Pilier 12/18 : contacts d'urgence — tests rouges prêts

Kudoro : « continuer l'implémentation des piliers restants sur Android tout en gardant derrière la tête la future implémentation iOS ».

## Ce qui change dans la méthode : des vecteurs rejouables sur iOS
Chaque pilier arrive désormais avec des **vecteurs neutres** dans `qa/vectors/<pilier>/` : entrée `_j`, `Patient.contact` attendu, lignes attendues et interdites du QR texte. Le test Android les rejoue ; l'implémentation iOS rejouera les mêmes fichiers. Ils décrivent le contrat, pas le Kotlin.
Règle d'implémentation qui en découle : la logique d'un pilier (codec, règles de formulaire, rang QR) vit dans du Kotlin **sans import Android** ; les fragments n'en sont que l'habillage.

## Tests : `tests/pillar-contacts` @ `82dc737` (CI en attente — si elle ne compile pas, c'est moi qui corrige, tu attends)
`pillar8/ContactsPillarTest.kt` (24 tests) et `pillar8/ContactsVectorsTest.kt` (4 tests), 6 vecteurs. Rouges attendus aujourd'hui :
- UC-CT-001 `contacts` actif dans `PillarRegistry` ;
- UC-CT-005 le téléphone d'un contact ne porte pas `use` (aujourd'hui toujours `mobile`, que rien ne prouve) ;
- UC-CT-013 `JContact.adr` exporté en `Patient.contact.address.text` ;
- UC-CT-015 / 016 aller-retour `_j` → Bundle → `_j` : il manque `fun contactsOf(bundle: Bundle): List<JContact>` dans `IpsFhirCodec` (un `JContact` par `Patient.contact`, dans l'ordre ; `r` = code RoleCode si codé, sinon le texte ; champ absent = `null`) ;
- UC-CT-021 un code de rôle inconnu (`MEDPROVR`) n'est jamais imprimé brut sur le QR texte ;
- UC-CT-022 chaque persona de démo a un contact avec téléphone et relation du catalogue (Haru n'en a pas) ;
- UC-CT-V02 les vecteurs ct-001, ct-002, ct-004.
Les autres sont des verrous verts : ils doivent le rester.

## Ordre
1. Rapport d'analyse court (pas de code) : lis les tests, dis-moi ce qui te paraît faux ou infaisable. Trois points où j'ai tranché sans texte, à contester si tu as un argument : pas de `use` sur le téléphone ; adresse du contact = `text` seul ; code inconnu ≠ texte libre (UC-CT-012 exige que « friend », « ami », « 友人 » restent mot pour mot — propose la règle qui distingue les deux).
2. Implémentation sur `ag/0061-contacts`, sans modifier tests ni vecteurs. Règle KB seulement : aucun nouveau code médical en Kotlin ; les libellés suivent §9.1.
3. Rapport avec CI brute. Puis cycle appareil : écran contacts, QR FR/JA, validateur 0 erreur, PDF.

## Hors périmètre de ce message
Règles de formulaire (numéro trop court, contact sans moyen de le joindre) : j'écrirai les tests quand tu auras proposé une logique pure, par exemple `ContactFormLogic.check(name, phone, email, address): Verdict`.

## Files
0059 > 0058 (KB) > cycle 27 (0054) > **0061** > 0060 (libellés) > 0056 (sources) > 0053 (doc).
