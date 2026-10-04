# Amélioration continue — Antigravity-Analyse — n° 0002

- **Auteur** : `orchestrator: Antigravity-Analyse`
- **Date** : 2026-10-04
- **Priorité** : a) Sécurité des personnes (donnée vitale effacée lors de la troncature d'urgence)
- **Fichier concerné** : `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt:85-88`

---

## 1. Le Constat et sa Pièce

Dans `JemmaTextPayloadBuilder.kt`, l'algorithme de réduction du QR texte au plafond strict de 1 800 octets (`MAX_BYTES`, lignes 67 et 252-257) sacrifie les lignes des sections selon un ordre de priorité décroissant défini par les constantes de rang (`RANK_*`, lignes 82-94) :

```kotlin
// JemmaTextPayloadBuilder.kt:82-94
private const val RANK_ALLERGIES = 1
private const val RANK_MEDICATIONS = 2
private const val RANK_CONDITIONS = 3
private const val RANK_PREGNANCY = 4
private const val RANK_FUNCTIONAL = 5
private const val RANK_CONTACTS = 6
private const val RANK_DEVICES = 7
private const val RANK_PATIENT_EXTRA = 8   // address, phone, e-mail, national id
private const val RANK_PAST_PROBLEMS = 9
private const val RANK_PROCEDURES = 10
private const val RANK_RESULTS = 11
private const val RANK_IMMUNIZATIONS = 12
```

La boucle de troncature (lignes 252-257) sélectionne la section victime par :
```kotlin
val victim = droppable.filter { it.lines.isNotEmpty() }.maxByOrNull { it.rank } ?: break
victim.lines.removeAt(victim.lines.size - 1)
```

La section avec le `rank` le plus élevé est vidée en premier.
Par conséquent, `RANK_DEVICES` (rang 7) est tronqué et vidé **AVANT** :
- `RANK_CONTACTS = 6` (Téléphone / adresse d'un proche) ;
- `RANK_FUNCTIONAL = 5` (Autonomie : « Marche avec une canne en extérieur », `fs`) ;
- `RANK_PREGNANCY = 4` (Grossesses passées : « Naissances vivantes : 2 en 1975 »).

---

## 2. Ce que ça coûte à une vraie personne (Scénario Clinique Réel)

Prenons le persona de référence 👵 **`demo_haru`** (`qr/JemmaPersonasSeeder.kt:27, 407-483`) :
- Haru est une patiente de 80 ans porteuse d'un **stimulateur cardiaque implanté** (Pacemaker Medtronic, `dv`, implanté en 2021, note « MRI-conditional ») et marchant avec une canne (`fs` « Walks with a cane outdoors »).
- Son profil complet comporte 14 piliers (taille brute FHIR minifiée : 23 211 octets).
- Lorsque son profil est exporté sur le QR texte (1 800 octets) en japonais ou en français, le budget d'octets est saturé.
- La boucle de troncature élimine les vaccins (12), les résultats (11), les procédures (10), les antécédents (9), les coordonnées secondaires (8), **puis élimine immédiatement le stimulateur cardiaque (`RANK_DEVICES = 7`)**.
- En revanche, la mention « Marche avec une canne » (`RANK_FUNCTIONAL = 5`) et le téléphone de sa fille (`RANK_CONTACTS = 6`) sont **conservés** à l'écran.

**Impact Vital** :
Haru fait un malaise dans la rue, inconsciente. Les secouristes scannent son QR texte d'urgence :
1. Ils voient qu'elle marche avec une canne, mais **ignorent totalement qu'elle porte un stimulateur cardiaque**.
2. Lors de la pose d'un défibrillateur automatisé externe (DAE), une électrode placée directement sur le boîtier sous-cutané du pacemaker peut provoquer une brûlure myocardique ou détruire l'appareil.
3. À l'hôpital, une IRM d'urgence peut être engagée sans vérifier la compatibilité du dispositif, entraînant un risque d'arrêt cardiaque par induction magnétique.

Le stimulateur cardiaque est un élément de survie physique immédiat ; la canne ou la date d'un accouchement survenu il y a 50 ans ne le sont pas.

---

## 3. La Correction Proposée

Dans l'échelle de criticité de la prise en charge d'urgence :
1. **Identité & Groupe sanguin** (Vital immédiat, non droppable).
2. **Allergies létales** (Choc anaphylactique immédiat, `RANK_ALLERGIES = 1`).
3. **Dispositifs médicaux implantés** (Pacemaker, pompe à insuline, valve cardiaque, `RANK_DEVICES`). Ils conditionnent directement l'usage des défibrillateurs, seringues automatiques et examens d'imagerie.
4. **Médicaments en cours** (Anticoagulants, insuline, `RANK_MEDICATIONS`).
5. **Problèmes de santé actifs** (Cardiopathie, épilepsie, `RANK_CONDITIONS`).
6. **Grossesse active / terme** (`RANK_PREGNANCY`).
7. **Contacts d'urgence ICE** (`RANK_CONTACTS`).
8. **Statut fonctionnel / autonomie** (`RANK_FUNCTIONAL`).
9. **Coordonnées patient secondaires** (`RANK_PATIENT_EXTRA`).
10. **Antécédents résolus** (`RANK_PAST_PROBLEMS`).
11. **Interventions passées** (`RANK_PROCEDURES`).
12. **Résultats biologiques** (`RANK_RESULTS`).
13. **Vaccinations** (`RANK_IMMUNIZATIONS`).

**Modification minimale dans `JemmaTextPayloadBuilder.kt`** :
Remonter `RANK_DEVICES` au rang 2 ou 3 (juste après les allergies ou entre médicaments et conditions) :
```kotlin
private const val RANK_ALLERGIES = 1
private const val RANK_DEVICES = 2       // 📟 Pacemaker, défibrillateur, pompe : priorité vitale absolue
private const val RANK_MEDICATIONS = 3
private const val RANK_CONDITIONS = 4
private const val RANK_PREGNANCY = 5
private const val RANK_CONTACTS = 6
private const val RANK_FUNCTIONAL = 7   // ♿ Canne, fauteuil : passe après les contacts et dispositifs
private const val RANK_PATIENT_EXTRA = 8
// ...
```

---

## 4. Ce qu'elle risque de casser

- Les tests existants qui vérifient le comportement du QR texte en cas de dépassement de budget :
  - `JemmaTextPayloadBuilderTest` (s'il teste l'ordre exact de disparition des icônes de troncature `✂️ … [ INCOMPLETE RECORD ] 💉 🧪 🏥 📜 👤 📟`).
  - Aucun impact sur le Bundle FHIR R4 ni sur la base de connaissances (ce n'est qu'une constante de rang du visualiseur texte).
  - Aucun impact sur le décodage ou la validation HL7.

---

## 5. Comment on saura que c'est corrigé

- Claude écrit un test unitaire (ex: `UC-QRT-030` dans `tests/qr-device-order`) avec un profil artificiellement chargé contenant un dispositif actif (`dv`) et un statut fonctionnel (`fs`) ou des contacts (`ct`), forçant une troncature :
- Le test vérifie que la ligne `📟` du stimulateur cardiaque reste présente dans le texte produit tant que la section `♿` (statut fonctionnel) n'a pas été entièrement purgée en premier.

---

`orchestrator: Antigravity-Analyse`
