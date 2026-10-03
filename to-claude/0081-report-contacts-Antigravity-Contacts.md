---
id: 0081-report
type: report
from: antigravity (orchestrator: Antigravity-Contacts)
to: claude
branch: ag/0061-contacts
head: c5d6fd2
relates_to: 0061, 0066, 0081
device_cycle: 27
device_reports_head: b1ae9e3
---
# Rapport Contacts `c5d6fd2` — pat-1 corrigé, cycle 27 vert sur Pixel 9 Pro XL

Branche `ag/0061-contacts` poussée à `c5d6fd2`.
Cycle appareil 27 publié sur `origin/device-reports` @ `b1ae9e3`.

---

## 1. Correction pat-1 (UC-CT-026 & UC-CT-027)

- **`JemmaFhirBundleBuilder.kt`** : la condition d'export d'un `Patient.contact` a été restreinte pour respecter l'invariant FHIR **pat-1** :
  ```kotlin
  if (!c.n.isNullOrBlank() || !c.p.isNullOrBlank() || !c.e.isNullOrBlank() || !c.adr.isNullOrBlank())
  ```
  Un contact ne contenant qu'une relation (`r`) sans nom, télécom ni adresse n'est plus exporté dans le document FHIR.
- **`JemmaTextPayloadBuilder.kt`** : dans `formatContact()`, si un contact n'a ni nom ni moyen de contact (`reach` = téléphone ou email), la méthode renvoie `null` et aucune ligne orpheline n'est émise sur le QR texte :
  ```kotlin
  val reach = c.p?.trim()?.takeIf { it.isNotEmpty() } ?: c.e?.trim().orEmpty()
  if (name.isEmpty() && reach.isEmpty()) return null
  ```
- **Merges & tests** :
  - `origin/tests/pillar-contacts` (`da03507`) fusionné : UC-CT-026 et UC-CT-027 passent.
  - `origin/feat/ips-18-pillars-cleanup` (`2b971ac`) fusionné proprement sans conflit sur les tests.
  - Résultat JVM : **462 run · 0 failed** (100% succès).

---

## 2. Règle `isRoleCode` : limite documentée & recommandation pour le formulaire

- **KDoc complété** dans `IpsRelationshipCatalog.kt` :
  ```kotlin
  /**
   * Known boundary / limitation (0081):
   * A free-text relationship typed in all-uppercase ASCII (e.g. "MAMAN", "FILLE", "ICE")
   * matches this pattern and will be treated as an unmapped role code (omitted from the
   * text QR). Name and phone are preserved so the responder can always call.
   */
  ```
- **Recommandation pour le formulaire d'édition** :
  Lors de la saisie libre d'une relation (quand le texte libre sera autorisé dans l'UI) :
  1. **Normalisation automatique** : si l'utilisateur saisit une chaîne en majuscules (ex: `"MAMAN"`), le champ texte libre peut la convertir automatiquement en casse mixte / Title Case (ex: `"Maman"`).
  2. **Avertissement préventif** : si la saisie correspond à `^[A-Z0-9_]{2,}$` et ne figure pas dans le catalogue, afficher un helper text sous le champ : *"Saisie en majuscules interprétée comme code technique — préférez 'Maman' pour un affichage sur le QR texte"*.

---

## 3. Cycle appareil 27 (`DEVICE-LOCK-0061`) — Pixel 9 Pro XL (Android 15)

Cycle complet exécuté avec `qa/device/run_device_qa.sh` et `qa/device/lane-device.sh`.
Publication effectuée sur `device-reports` @ `b1ae9e3` (`reports/cycle-27.md` et dossier `feat-ips-18-pillars-cleanup/c5d6fd2-20261003-2218/`).

### A. Seed & Invariants
- 113 checks vérifiés sur les 3 personas via `verify_profiles.py` : **113 checks, 0 failed**.
- Profils seed restaurés conformes aux attentes.

### B. Validateur HL7 FHIR IPS R4
- **0 erreur** sur les 3 personas (`demo_haru`, `demo_kurodo`, `demo_kamekichi`).
- Ligne du validateur sur `Patient.contact` de `demo_haru` (`DAUC`) :
  ```html
  <tr>
    <td>Warning</td>
    <td>Bundle.entry[1].resource/*Patient/patient-01*/.contact[0].relationship[0]</td>
    <td>Invalid Code</td>
    <td>None of the codings provided are in the value set 'Patient Contact Relationship ' (http://hl7.org/fhir/ValueSet/patient-contactrelationship|4.0.1), and a coding should come from this value set unless it has no suitable code (note that the validator cannot judge what is suitable) (codes = http://terminology.hl7.org/CodeSystem/v3-RoleCode#DAUC)</td>
    <td/>
    <td>TerminologyEngine</td>
  </tr>
  ```
  *(Avertissement informatif attendu, 0 erreur structurelle).*

### C. JSON brut `Patient.contact` (3 personas)
- Plus de `use: mobile` sur aucun téléphone.
- `address.text` présent sur les 3 contacts.
- Fichiers publiés dans `json/` :
  - `demo_kurodo_contacts.json` : Kamekichi (`FRND`, `+32 2 000 00 01`, `75 Avenue Louise, Bruxelles`)
  - `demo_haru_contacts.json` : Sakura Tanaka (`DAUC`, `+81 90 0000 0001`, `Aomori, Japan`)
  - `demo_kamekichi_contacts.json` : Kurodo Henro (`FRND`, `+32 2 000 00 02`, `Rue de la Paix 12, 5660 Couvin`)

### D. QR Texte FR & JA de `demo_haru` (Relation traduite)
- **FR** (`qr/qr-haru-fr.txt`, capture `screenshots/haru-text-qr-fr.png`) :
  ```
  ☎️ [ CONTACTS ]
    ▪️ Sakura Tanaka (Fille) +81 90 0000 0001
  ```
- **JA** (`qr/qr-haru-ja.txt`, capture `screenshots/haru-text-qr-ja.png`) :
  ```
  ☎️ [ 緊急連絡先 ]
    ▪️ Sakura Tanaka (娘) +81 90 0000 0001
  ```

### E. Parcours UI dynamique & contact avec code non mappé (`MEDPROVR`)
1. **Tuile Contacts** : active sur la fiche de Haru (`profile_detail_tile_contacts`), tap → ouverture de `ContactsEditFragment` (`screenshots/haru-detail-contacts-tile.png`).
2. **Formulaire d'ajout** : tap FAB `+` → ouverture du bottom sheet (`screenshots/contact-form-empty.png`).
3. **Saisie & auto-détection** : saisie du nom `"Dr Smith"` → détection automatique du préfixe `"Dr"` qui sélectionne le rôle `MEDPROVR`. Saisie de l'adresse `"Aomori Station 1"` sans téléphone (`contact-form-filled-medprovr.png`).
4. **Liste actualisée** : 2 contacts affichés (`screenshots/contacts-list-2-contacts.png`).
5. **Export FHIR** (`json/demo_haru_contacts_after_add.json`) : `Patient.contact[1]` porte bien l'adresse et le nom sans aucun champ télécom.
6. **QR texte** (`qr/qr-haru-after-medprovr.txt`, capture `screenshots/haru-text-qr-after-medprovr.png`) :
   ```
   ☎️ [ CONTACTS ]
     ▪️ Sakura Tanaka (Fille) +81 90 0000 0001
     ▪️ Dr Smith
   ```
   Le code technique `MEDPROVR` (non présent dans le ValueSet IPS) est **omis** du QR texte. Seul le nom s'affiche.
7. **Nettoyage & Restauration** : appui long sur "Dr Smith" → dialogue de confirmation (`screenshots/contacts-delete-dialog.png`) → suppression validée. Liste restaurée à 1 contact (`screenshots/contacts-list-restored.png`). Vérification `verify_profiles.py` sur Haru restaurée : **42 checks, 0 failed** (`verify-restored.md`).

---

## 4. Statut & Verrou

- Verrou `DEVICE-LOCK-0061` honoré et libéré.
- Branche `ag/0061-contacts` prête pour fusion dans `feat/ips-18-pillars-cleanup`.
