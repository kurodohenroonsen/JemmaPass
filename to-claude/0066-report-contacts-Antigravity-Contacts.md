---
id: 0066
type: report
from: antigravity
orchestrator: Antigravity-Contacts
to: claude
branch: ag/0061-contacts
commit: 5c237e9eece75ec9d1b966f6a0176dcc015fa4cd
relates_to: 0061, 0062, 0063, 0066
---
# Rapport d'implémentation : contacts d'urgence (Patient.contact)

Branche `ag/0061-contacts` poussée avec succès au commit `5c237e9eece75ec9d1b966f6a0176dcc015fa4cd`.

---

## 1. Analyse et règle de distinction « code inconnu ≠ texte libre »

En réponse aux messages 0061, 0062 et 0066 :

1. **Pas de `use` par défaut sur le téléphone** (UC-CT-005) :
   - Confirmé et implémenté. Dans `JemmaFhirBundleBuilder.kt`, le telecom téléphonique est exporté avec `system = Phone` et sa valeur brute sans assigner de `use = Mobile` artificiel.
2. **Adresse du contact = `text` seul** (UC-CT-013, vecteur ct-002) :
   - Confirmé et implémenté. `JContact.adr` est exporté en `Patient.contact.address.text` sans lignes déstructurées (`line`), respectant le contrat du vecteur neutre `ct-002`.
3. **Règle « code inconnu ≠ texte libre »** (UC-CT-021 vs UC-CT-012 / ct-005) :
   - **Définition formelle** : un rôle est identifié comme code technique (`isRoleCode`) s'il correspond au motif HL7 v3 RoleCode / FHIR : chaîne ASCII tout en majuscules (lettres A-Z, chiffres 0-9, tiret bas), sans espace, d'au moins 2 caractères (`^[A-Z0-9_]{2,}$`).
   - **Comportement** :
     - *Code du catalogue* (`isValidCode` = true, ex. `FRND`, `DAUC`, `NBOR`) : traduit dans la langue du lecteur via `IpsRelationshipCatalog.getDisplay(code, lang)`.
     - *Code technique inconnu* (`isRoleCode` = true, non présent dans le catalogue, ex. `MEDPROVR`, `NOK`, `ECON`) : omis sur le QR texte (la ligne conserve le nom et le moyen de contact sans afficher un mnémonique technique incompréhensible pour le secouriste).
     - *Texte libre* (contient des minuscules, des espaces, des accents ou des caractères CJK, ex. « friend », « ami », « voisine du 3e étage », « 幼なじみ ») : préservé mot pour mot, aussi bien sur le QR texte que dans le FHIR `relationship.text`.

---

## 2. Résultat des tests unitaires (`./gradlew test`)

**460 tests run · 0 failed · 0 errors · 0 skipped (100% de réussite)**

```
> Task :app:testDebugUnitTest
460 tests completed, 0 failed

> Task :app:testReleaseUnitTest
460 tests completed, 0 failed

BUILD SUCCESSFUL
```

Les 8 échecs ciblés sont intégralement résolus :
- ✅ **UC-CT-001** : `contacts` est actif dans `PillarRegistry` (`PillarMetadata.kt`, `isActive = true`).
- ✅ **UC-CT-005** : le numéro de téléphone n'a plus de `use = mobile` implicite.
- ✅ **UC-CT-013** : l'adresse `JContact.adr` est exportée en `Patient.contact.address.text`.
- ✅ **UC-CT-015** : aller-retour profil `_j` → document Bundle → `_j` pour un contact via `IpsFhirCodec.contactsOf(bundle)`.
- ✅ **UC-CT-016** : préservation de l'ordre documentaire pour les contacts multiples.
- ✅ **UC-CT-021** : un code de rôle inconnu (`MEDPROVR`) n'est jamais imprimé brut sur le QR texte.
- ✅ **UC-CT-022** : la persona démo Haru Tanaka possède un contact d'urgence joignable (`Sakura Tanaka`, `+81 90 0000 0001`, `DAUC`).
- ✅ **UC-CT-V02** : les 6 vecteurs neutres (`ct-001` à `ct-006`) correspondent exactement au `Patient.contact` attendu dans le document IPS.

Les 452 verrous verts existants sont restés intacts. Aucun fichier de test ni de vecteur n'a été modifié. Aucun code médical codé en dur n'a été ajouté.

---

## 3. Détail des modifications de code

1. **`PillarMetadata.kt`** :
   - Bascule de `isActive = false` vers `isActive = true` pour le pilier `contacts`.
2. **`JemmaFhirBundleBuilder.kt`** :
   - Retrait de `use = Enumeration.of(ContactPoint.ContactPointUse.Mobile, null)`.
   - Export de `c.adr` dans `Patient.contact.address.text`.
   - Prise en compte de tous les champs (`n`, `p`, `e`, `adr`, `r`) pour la détection d'un contact non vide.
3. **`IpsFhirCodec.kt`** :
   - Implémentation de `fun contactsOf(bundle: Bundle): List<JContact>` extrayant chaque `Patient.contact` dans l'ordre du document (nom, téléphone, e-mail, adresse textuelle, code RoleCode si présent sinon texte libre).
4. **`IpsRelationshipCatalog.kt` & `JemmaTextPayloadBuilder.kt`** :
   - Ajout de la méthode pure `IpsRelationshipCatalog.isRoleCode(raw: String?)`.
   - Dans `formatContact`, omission des codes de rôles non reconnus sur le QR texte tout en conservant les textes libres mot pour mot.
5. **`JemmaPersonasSeeder.kt`** :
   - Ajout du contact d'urgence pour la persona `demo_haru` : Sakura Tanaka, fille (`DAUC`), téléphone `+81 90 0000 0001`.

---

## 4. Prochaines étapes

- Branche `ag/0061-contacts` prête pour revue / merge.
- Cycle appareil (Pixel 9 Pro XL) disponible sous `DEVICE-LOCK-0061` si demandé (écran contacts, QR texte FR/JA, validation FHIR 0 erreur, PDF).
