# Défauts suspectés en écrivant les tests

Relevés le 2026-10-02 par le couloir « jeu de tests », **à la lecture du code** (commit `8a675b3`) : rien n'a
été exécuté. Chaque entrée donne le test qui exposerait le défaut. Ces tests ne sont **pas** dans l'arbre de
tests (ils feraient échouer la CI) ; ils sont prêts à y être copiés avec le correctif.

Règle du dépôt rappelée : un défaut n'est corrigé qu'après vérification dans le code par l'intégrateur.

Degré de certitude utilisé plus bas :

- **lu** — le comportement découle du seul code de l'application, sans hypothèse ;
- **lu + SDK** — le code de l'application est sans garde ; l'effet dépend du SDK FHIR (`dev.ohs.fhir`), dont
  les sources ne sont pas dans le dépôt ; l'hypothèse est celle que le reste du code fait déjà ;
- **calculé** — obtenu avec un portage Python du constructeur, pas avec le code réel ;
- **à exécuter** — rien ne permet de trancher à la lecture.

Chemins : `…/` = `JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/`. Les tests utilisent
`testsupport/ProfileFixtures` (ajouté dans l'arbre de tests) pour fabriquer un `HydratedProfile` sans KB.

## Vue d'ensemble

| N° | Défaut | Cas | Certitude | Déjà au catalogue | Risque |
|---|---|---|---|---|---|
| SD-01 | Triage : deux téléphones qui reçoivent les deux mêmes événements dans un ordre différent ne tombent pas d'accord (décédé / stabilisé) | UC-SOS-020, UC-SOS-021 | lu | non | haut |
| SD-02 | Diffusion à proximité : code de plus de 10 caractères tronqué, code contenant un point coupé en deux | UC-SOS-012 | lu | en partie (`take(10)`) | haut |
| SD-03 | Date de naissance non ISO, usage d'adresse ou système de télécom inconnu : le constructeur du Bundle lève ; l'onglet QR FHIR n'a pas de garde | UC-IMP-013, UC-QRF-013, UC-STO-008 | lu + SDK | en partie | haut |
| SD-04 | Diffusion à proximité : une liste coupée est identique à une liste complète | UC-SOS-011 | lu | oui (⚠ code) | haut |
| SD-05 | Bundle : primitives FHIR vides (`"code": ""`, `"text": ""`) | UC-FHIR-011, UC-FHIR-014, UC-QRF-008 | lu + SDK | oui | moyen |
| SD-06 | Profil vide : le contrôle répond CLEAN (« rien à signaler ») alors que rien n'a pu être comparé | UC-SCAN-010, UC-FHIR-007 | lu | non (figé par un test existant) | haut |
| SD-07 | Identifiant de profil finissant par `.fhir` (écrase le Bundle d'un autre profil) ou valant `meta` (profil invisible) accepté | UC-IMP-009, UC-MPR-014, UC-ROB-017 | lu | `x.fhir` cité, non corrigé | haut |
| SD-08 | Résultat de groupe sanguin concordant mais saisi en texte : gardé par le dépôt, écarté par le constructeur du Bundle | UC-RES-005, UC-STO-025 | lu | non | moyen |
| SD-09 | Médicament × maladie : fausses contre-indications par sous-chaîne, contre-indications manquées par synonyme | UC-DDS-008…017 | lu | oui | haut |
| SD-10 | Résultat « presque numérique » : l'unité est perdue | UC-RES-015, UC-I18N-012 | lu | oui (formulaire) ; ici aussi dans le modèle | haut |
| SD-11 | QR texte de Haru : l'autonomie (♿) puis la grossesse sortent du QR dans 23 langues sur 25, dont le japonais | UC-I18N-008, UC-QRT-009, T22.5 | calculé | non | moyen |
| SD-12 | Ré-import de son propre QR : UDI, numéro de série, lot effacés | UC-DEV-021, UC-IMP-004 | lu | oui | haut |
| SD-13 | Problème « résolu » importé dans `cn` : revient actif | UC-IMP-017, UC-PRB-017 | lu | oui | haut |
| SD-14 | Valeur codée d'un résultat : système de code perdu en passant par `_j` | UC-RES-024 | lu | non | bas |
| SD-15 | Profil qui n'a qu'un nom de famille : « Profile inconnu » | UC-HUM-005 | lu | oui | moyen |
| SD-16 | Date de début d'un traitement (`md[].eff`) jamais exportée dans le Bundle | UC-MED-009, UC-FHIR-005 | lu | non | moyen |
| SD-17 | Série vaccinale sans numéro de dose : la série disparaît | UC-VAC-012 | lu | oui | bas |
| SD-18 | Très grandes ou très petites valeurs numériques : exactitude non garantie | UC-RES-014 | à exécuter | en partie | moyen |
| SD-19 | Données de démonstration : contacts d'urgence sans téléphone, relation hors catalogue | UC-QRT-005, UC-PAT-016 | lu | non | bas |
| SD-20 | Divers, risque bas | — | lu | non | bas |

Les cinq plus graves pour une personne : SD-01, SD-02, SD-07, SD-06, SD-03.

---

## SD-01 — Triage : le résultat dépend de l'ordre d'arrivée

- **Cas** : UC-SOS-020, UC-SOS-021.
- **Code** : `…/triage/StatusResolver.kt:81-90` (`shouldOverwrite`, cas 2 et 3).
- **Entrée** : deux événements pour la même victime — « décédé » créé à t = 1000 par le secouriste r2,
  « stabilisé » créé à t = 1020 par le secouriste r1 (qui n'a pas encore reçu le premier : ce n'est donc pas une
  annulation explicite).
- **Attendu** : tous les téléphones affichent le même statut, quel que soit l'ordre de réception (le
  commentaire de la classe le promet pour les égalités d'horodatage).
- **Constaté à la lecture** : téléphone A (reçoit décédé puis stabilisé) : cas 3, `1020 > 1000 + 30` est faux,
  A reste sur **décédé**. Téléphone B (reçoit stabilisé puis décédé) : cas 2, `1000 > 1020` est faux, B reste
  sur **stabilisé**. Les deux restent en désaccord jusqu'à un événement plus récent de plus de 30 s. Même
  divergence quand les deux événements ont le même horodatage.
- **Risque pour la personne** : une victime vivante reste affichée « décédée » sur une partie des téléphones
  des secouristes ; personne n'y retourne.

```kotlin
package be.heyman.android.jemmapassdemo.triage

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusResolverConvergenceTest {

    private fun statusAfter(vararg events: StatusEvent): SaltCode {
        val phone = StatusResolver()
        for (e in events) phone.apply(e)
        return phone.get("v1")!!.status
    }

    @Test
    fun `UC-SOS-021 two phones that receive the same events in a different order agree`() {
        val deceased = StatusEvent(victimSid = "v1", status = SaltCode.DCD, rescuerSid = "r2", timestampSec = 1_000)
        for (delay in listOf(0L, 1L, 20L, 30L)) {
            val stabilized = StatusEvent(victimSid = "v1", status = SaltCode.STAB, rescuerSid = "r1", timestampSec = 1_000 + delay)
            assertEquals("stabilized ${delay}s after deceased", statusAfter(deceased, stabilized), statusAfter(stabilized, deceased))
        }
    }
}
```

## SD-02 — Diffusion à proximité : codes tronqués ou coupés

- **Cas** : UC-SOS-012.
- **Code** : `…/sos/JemmaNearbyEndpointCodec.kt:136` (`sanitize(code).take(10)`) et `:297`
  (`codesStr.split('.')`).
- **Entrée** : les vaccins de la persona Haru, `1181000221105` et `1801000221105` (SNOMED à 13 chiffres,
  `JemmaPersonasSeeder.kt:116,121`), diffusés par `JemmaSosService.kt:1066` ; un code contenant un point
  (`I48.0`).
- **Attendu** : un code est transmis entier, ou pas du tout.
- **Constaté à la lecture** : le secouriste reçoit `1181000221` et `1801000221`, qui ne sont pas les codes
  envoyés ; `I48.0` arrive comme deux codes, `I48` et `0`. Toute extension nationale SNOMED (jusqu'à 18
  chiffres) est concernée, y compris pour une allergie.
- **Risque pour la personne** : le secouriste lit un code faux ou inconnu là où il y avait une allergie, un
  traitement ou un vaccin.

```kotlin
package be.heyman.android.jemmapassdemo.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyCodeIntegrityTest {

    private fun received(marker: Char, codes: List<String>): List<String> {
        val chunk = JemmaNearbyEndpointCodec.encodeVictimCodes("ab12", JemmaNearbyEndpointCodec.VICTIM_CHUNK_IMMUN, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, marker, codes)
        return (JemmaNearbyEndpointCodec.decode(chunk) as JemmaNearbyEndpointCodec.Decoded.VictimCodes).codes
    }

    @Test
    fun `UC-SOS-012 a code is broadcast whole or not at all`() {
        val haruVaccines = listOf("1181000221105", "1801000221105", "1119349007")
        for (code in received('I', haruVaccines)) assertTrue("'$code' was never sent", code in haruVaccines)
        assertEquals(listOf("I48.0"), received('C', listOf("I48.0")))
    }
}
```

## SD-03 — Valeur d'identité imprévue : pas de Bundle, et l'onglet QR FHIR sans garde

- **Cas** : UC-IMP-013, UC-QRF-013, UC-STO-008, UC-HUM-025.
- **Code** : `…/qr/JemmaFhirBundleBuilder.kt:131` (`FhirDate.fromString(bd)`), `:546`
  (`AddressUse.fromCode`), `:573` et `:575` (`ContactPointSystem.fromCode`, `ContactPointUse.fromCode`), tous
  sans `try`. Appelants : `…/profiles/ProfilesRepository.kt:476-481` (exception attrapée : le profil est
  enregistré **sans Bundle**) et `…/ui/export/QrViewerFragment.kt:549-551` (`buildFhir`, aucune garde, lancé
  dans une coroutine).
- **Entrée** : un profil importé dont `p.bd` vaut `05/02/1956`, ou dont `p.adrs[].u` vaut `vacation`, ou dont
  `p.tels[].s` vaut `whatsapp`.
- **Attendu** : un Bundle valide, la valeur illisible omise ; l'onglet FHIR s'ouvre.
- **Constaté à la lecture** : `IpsFhirCodec` entoure ces mêmes appels du SDK d'un `try / catch` (dates des
  piliers natifs, `fromCode` des statuts) parce qu'ils lèvent sur une valeur inconnue. Ici rien ne les entoure :
  à chaque sauvegarde le Bundle est abandonné (les détails propres au Bundle — lot, UDI, série — ne sont alors
  jamais stockés), et l'ouverture de l'onglet FHIR lève dans la coroutine de `renderActiveChannel`.
- **Certitude** : lu + SDK. Le test `PatientBirthDateBundleTest` (dans l'arbre) vérifie le cas voisin « année
  seule » ; s'il échoue, ce défaut touche aussi les dates partielles, que le formulaire accepte.
- **Risque pour la personne** : profil importé sans document IPS ; plantage de l'écran d'export au moment de
  montrer le QR.

```kotlin
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.ips.IpsFhirCodec
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertNotNull
import org.junit.Test

class UnexpectedIdentityValuesTest {

    @Test
    fun `UC-IMP-013 an unreadable birth date, address use or telecom code still gives a document`() {
        val patients = listOf(
            JPatient(gn = "Haru", bd = "05/02/1956"),
            JPatient(gn = "Haru", bd = "1956-2-5"),
            JPatient(gn = "Haru", adrs = listOf(JAddress(use = "vacation", city = "Aomori"))),
            JPatient(gn = "Haru", tels = listOf(JTelecom(system = "whatsapp", value = "+81 90 0000 0000"))),
            JPatient(gn = "Haru", tels = listOf(JTelecom(system = "phone", value = "+81 90 0000 0000", use = "cell"))),
        )
        for (p in patients) {
            val json = JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(JemmaProfileJ(j = "1.2", sid = "qa_imp", p = p)))
            assertNotNull("$p", IpsFhirCodec.parseBundle(json))
        }
    }
}
```

## SD-04 — Diffusion à proximité : une liste coupée ne se distingue pas d'une liste complète

- **Cas** : UC-SOS-011, UC-SOS-013.
- **Code** : `…/sos/JemmaNearbyEndpointCodec.kt:139-150` (les codes qui ne tiennent pas sont comptés dans un
  journal, rien n'est écrit dans le segment).
- **Entrée** : 50 médicaments.
- **Attendu** : le secouriste voit que la liste est incomplète.
- **Constaté à la lecture** : 14 codes ATC tiennent dans 131 octets ; le segment produit est strictement
  identique à celui d'un patient qui a 14 médicaments.
- **Risque pour la personne** : le secouriste croit connaître tout le traitement.

```kotlin
package be.heyman.android.jemmapassdemo.sos

import org.junit.Assert.assertNotEquals
import org.junit.Test

class NearbyTruncationNoticeTest {

    @Test
    fun `UC-SOS-011 a cut list does not look like a complete one`() {
        val fifty = (1..50).map { String.format(java.util.Locale.ROOT, "B01AA%02d", it) }
        fun segment(codes: List<String>) = JemmaNearbyEndpointCodec.encodeVictimCodes(
            "ab12", JemmaNearbyEndpointCodec.VICTIM_CHUNK_MEDS, JemmaNearbyEndpointCodec.VICTIM_CHUNK_TOTAL, 'M', codes)
        assertNotEquals(segment(fifty.take(14)), segment(fifty))
    }
}
```

## SD-05 — Bundle : primitives FHIR vides

- **Cas** : UC-FHIR-011, UC-FHIR-014, UC-QRF-008.
- **Code** : `…/qr/JemmaFhirBundleBuilder.kt:214` (allergie : `Coding.code = a.raw.c.orEmpty()`), `:250`
  (médicament, même chose), `:152` (contact : `name.text = c.n ?: ""`).
- **Entrée** : une allergie ou un médicament sans code (import, saisie ancienne) ; un contact d'urgence qui n'a
  qu'un téléphone.
- **Attendu** : pas de `coding` quand il n'y a pas de code ; pas de `name` quand il n'y a pas de nom.
- **Constaté à la lecture** : `"code": ""` et `"text": ""` sont émis. Le même fichier explique, ligne 122, que
  le validateur HL7 rejette les chaînes vides (correctif du cycle 6 pour le nom de famille).
- **Risque pour la personne** : document refusé par le système d'un hôpital ; allergie codée « rien ».

```kotlin
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertFalse
import org.junit.Test

class NoEmptyPrimitiveTest {

    @Test
    fun `UC-FHIR-011 no empty FHIR primitive for an uncoded allergy, an uncoded medication or a nameless contact`() {
        val raw = JemmaProfileJ(
            j = "1.2", sid = "qa_empty",
            p = JPatient(gn = "Haru", ct = listOf(JContact(p = "+81 90 0000 0000"))),
            al = listOf(JAllergy(displayLabel = "Savon de grand-mère", s = "H")),
            md = listOf(JMedication(displayLabel = "Tisane du jardin")),
        )
        val json = JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(raw))
        assertFalse(json.contains(": \"\""))
    }
}
```

## SD-06 — Un profil vide reçoit le verdict CLEAN

- **Cas** : UC-SCAN-010, UC-FHIR-007, UC-QRT-006.
- **Code** : `…/kb/KbSafety.kt:115` (`itemsToCheck <= 0 -> CHECKED`) ; `…/kb/KbCrossCheck.kt:703` et
  `…/kb/JemmaProfileHydrator.kt:108` construisent le rapport avec ce statut.
- **Entrée** : une victime dont le profil ne contient ni allergie, ni traitement, ni problème (profil créé avec
  le seul nom, ou reçu incomplet) ; KB disponible ; médicament scanné connu.
- **Attendu** (UC-SCAN-010) : « aucune donnée pour vérifier », jamais « sans danger ».
- **Constaté à la lecture** : les trois piliers sont CHECKED, le verdict est CLEAN, `isClean` est vrai : le scan
  et l'assistant peuvent dire « rien à signaler ». Le test existant
  `KbSafetyTest.ucSafeKb02_nothingToCheck_isCleanEvenWithoutKb` fige ce choix. Tant que l'application ne sait
  pas distinguer « aucune allergie connue » de « non renseigné », un profil vide ne prouve rien.
- **Décision à prendre** par une personne (un nouveau verdict, ou INCOMPLETE). Le nouveau
  `KbSafetyTruthTableTest` ne fige volontairement pas ce point.
- **Risque pour la personne** : un médicament donné à une victime allergique dont le profil est simplement vide.

```kotlin
package be.heyman.android.jemmapassdemo.kb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EmptyProfileVerdictTest {

    @Test
    fun `UC-SCAN-010 an empty profile is not a clean check`() {
        val nothingToCompare = KbSafety.pillarStatus(kbAvailable = true, itemsToCheck = 0)
        val r = CrossCheckResult(
            candidateAtc = "M01AE01", candidateDisplay = "Ibuprofen",
            allergyHits = emptyList(), ddiHits = emptyList(), drugDiseaseHits = emptyList(), totalDurationMs = 1L,
            checks = KbCheckReport(nothingToCompare, nothingToCompare, nothingToCompare),
        )
        assertFalse("nothing was recorded, so nothing was compared", r.isClean)
        assertNotEquals(KbSafetyVerdict.CLEAN, r.verdict)
    }
}
```

## SD-07 — Identifiants de profil qui heurtent la disposition des fichiers

- **Cas** : UC-IMP-009, UC-MPR-014, UC-ROB-017.
- **Code** : `…/profiles/ProfileFiles.kt:14-25` (`SAFE_ID` autorise le point) ;
  `…/profiles/ProfilesRepository.kt:248` (`"$id.json"`), `:447` (`"$id.fhir.json"`), `:614` (le scan ignore
  `*.fhir.json` et `meta.json`).
- **Entrée** : un QR dont le `sid` vaut `demo_haru.fhir` ; un QR dont le `sid` vaut `meta`.
- **Attendu** : identifiant refusé, un nouvel identifiant est généré.
- **Constaté à la lecture** : `demo_haru.fhir` est accepté. Le `_j` importé est écrit dans
  `demo_haru.fhir.json`, c'est-à-dire **à la place du Bundle de Haru** ; à la lecture suivante ce fichier n'est
  plus un Bundle, les piliers de Haru retombent sur sa projection `_j` et ses détails propres au Bundle (UDI,
  série, lot, lieu) sont perdus à la sauvegarde suivante. Le profil importé, lui, n'apparaît pas dans la liste
  (`*.fhir.json` est ignoré) alors que l'import a répondu « enregistré ». Sur un stockage insensible à la casse
  (le stockage externe d'Android l'est), `demo_haru.FHIR` fait la même chose. `meta` est accepté aussi : le
  profil est écrit dans `meta.json`, que le scan ignore.
- **Risque pour la personne** : le dossier d'une autre personne est abîmé par un simple scan ; un proche importé
  n'apparaît jamais.

```kotlin
package be.heyman.android.jemmapassdemo.profiles

import org.junit.Assert.assertNull
import org.junit.Test

class ProfileIdLayoutCollisionTest {

    @Test
    fun `UC-IMP-009 an id cannot be the Bundle file of another profile nor a name the list ignores`() {
        for (bad in listOf("demo_haru.fhir", "x.fhir", "demo_haru.FHIR", "x.Fhir", "meta", "META")) {
            assertNull("should reject '$bad'", ProfileFiles.safeIdOrNull(bad))
        }
    }
}
```

## SD-08 — Groupe sanguin saisi en texte : deux règles de réconciliation qui ne s'accordent pas

- **Cas** : UC-RES-005, UC-STO-025.
- **Code** : `…/ips/IpsBloodGroup.kt:168-183` (`reconcile` : garde un résultat dont le texte dit le groupe du
  profil, via `labelOf`) et `…/qr/JemmaFhirBundleBuilder.kt:510-519` (`reconcileBloodGroup` : ne garde qu'un
  résultat dont `valueCode` est le code SNOMED attendu).
- **Entrée** : `p.bt` = `O+` et un résultat 882-1 importé par `_j.rs` sans `vc`, par exemple
  `{"c":"882-1","v":"O+","dt":"2015-09-01"}` (QR d'une autre version, saisie ancienne).
- **Attendu** : une seule règle ; le résultat daté concordant est gardé partout.
- **Constaté à la lecture** : le dépôt le garde (il est projeté dans `_j.rs`), puis le constructeur du Bundle
  l'écarte et met l'Observation dérivée à sa place. `_j` et Bundle divergent pour cette sauvegarde (le contrôle
  P4 de `verify_profiles.py` le verrait), et à la lecture suivante le Bundle fait foi : la date et le
  laboratoire du résultat sont perdus. Aucun conflit n'est signalé (les deux valeurs concordent).
- **Risque pour la personne** : faible sur la valeur (le groupe reste celui du profil), réel sur la traçabilité
  (la preuve datée du groupe disparaît sans message).

```kotlin
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class BloodGroupTextResultTest {

    @Test
    fun `UC-RES-005 a matching blood group result typed as text is stored the same way in both files`() {
        val byHand = IpsResult(id = "rs-user", code = IpsBloodGroup.LOINC_ABO_RH, display = "ABO/Rh",
            date = "2015-09-01", valueText = "O+", performer = "Laboratoire")
        val stored = IpsBloodGroup.sync(listOf(byHand), "qa_bg", "O+")
        assertEquals("the repository keeps it", listOf(byHand), stored)

        val profile = JemmaProfileJ(j = "1.2", sid = "qa_bg", p = JPatient(gn = "Haru", bt = "O+"))
        val bundle = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile), IpsNativePillars(results = stored)))!!
        assertEquals("the Bundle holds what the repository stored", stored, IpsFhirCodec.resultsOf(bundle))
    }
}
```

## SD-09 — Médicament × maladie : sous-chaînes et synonymes

- **Cas** : UC-DDS-008, 009, 010, 012 (fausses alertes) ; UC-DDS-013, 014, 016, 017 (alertes manquées).
- **Code** : `…/kb/DrugDiseaseTerms.kt:52-57` (`matches` : `d.contains(t)`, sans frontière de mot) et `:20`
  (`VARIANTS` : trois variantes seulement) ; `:39-48` (`singular`).
- **Entrées et constat à la lecture** : `("hypertension", "Intracranial Hypertension")`, `("diabetes",
  "Diabetes Insipidus")`, `("coma", "Glaucoma")`, `("heatstroke", "Stroke")` correspondent ;
  `("hypertensive disorder", "Hypertension")`, « hepatic cirrhosis » × « Liver Diseases », « chronic renal
  failure » × « Kidney Diseases », `("psychoses", "psychosis")` ne correspondent pas.
- **Attendu** : l'inverse dans les huit cas. La liste des synonymes à reconnaître est une décision clinique.
- **Risque pour la personne** : une contre-indication manquée (AINS et hypertension codée « hypertensive
  disorder ») ; ou trop de fausses alertes, que l'on finit par ne plus lire.

```kotlin
package be.heyman.android.jemmapassdemo.kb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugDiseaseWordBoundaryTest {

    private fun anyMatch(storedDisplay: String, disease: String): Boolean =
        DrugDiseaseTerms.candidates(storedDisplay, null, null).any { DrugDiseaseTerms.matches(it, disease) }

    @Test
    fun `UC-DDS-008 a term inside another disease name is not a match`() {
        assertFalse(DrugDiseaseTerms.matches("hypertension", "Intracranial Hypertension"))
        assertFalse(DrugDiseaseTerms.matches("diabetes", "Diabetes Insipidus"))
        assertFalse(DrugDiseaseTerms.matches("coma", "Glaucoma"))
        assertFalse(DrugDiseaseTerms.matches("heatstroke", "Stroke"))
    }

    @Test
    fun `UC-DDS-013 usual synonyms are matched`() {
        assertTrue(anyMatch("Hypertensive disorder, systemic arterial", "Hypertension"))
        assertTrue(anyMatch("Hepatic cirrhosis", "Liver Diseases"))
        assertTrue(anyMatch("Chronic renal failure", "Kidney Diseases"))
        assertTrue(DrugDiseaseTerms.matches("psychoses", "psychosis"))
    }
}
```

## SD-10 — Résultat « presque numérique » : unité perdue

- **Cas** : UC-RES-015, UC-I18N-012.
- **Code** : `…/ips/IpsResult.kt:138` (`unit = if (isNumeric) … else null` dans la projection) et
  `…/ips/IpsFhirCodec.kt:651-660` (`valueString` sans unité). Le catalogue relève la même perte dans le
  formulaire (`ResultFormBottomSheet.kt:431`) ; elle existe donc aussi quand la donnée arrive par import.
- **Entrée** : CRP `<0.5` mg/L ; plaquettes `1 234,5` 10*3/uL.
- **Attendu** : l'unité reste lisible à côté de la valeur.
- **Constaté à la lecture** : `_j.rs` porte `v` = `<0.5` et pas de `u` ; le Bundle porte `valueString` =
  `<0.5` ; à la relecture `unit` est nul.
- **Risque pour la personne** : un soignant lit « 5 » sans savoir si ce sont des mg/L ou des mmol/L.

```kotlin
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JPatient
import be.heyman.android.jemmapassdemo.qr.JemmaFhirBundleBuilder
import be.heyman.android.jemmapassdemo.qr.JemmaProfileJ
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmostNumericResultTest {

    @Test
    fun `UC-RES-015 the unit of a value that is not a plain number stays readable`() {
        for (typed in listOf("<0.5", "1 234,5", ">100", "5.")) {
            val r = IpsResult(id = "rs-1", code = "1988-5", display = "CRP", value = typed, unit = "mg/L")
            val j = r.toJEntry()
            assertTrue("projection of '$typed' : v=${j.value} u=${j.unit}", j.unit == "mg/L" || j.value.orEmpty().contains("mg/L"))

            val profile = JemmaProfileJ(j = "1.2", sid = "qa_unit", p = JPatient(gn = "Haru"))
            val bundle = IpsFhirCodec.parseBundle(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(profile), IpsNativePillars(results = listOf(r))))!!
            val back = IpsFhirCodec.resultsOf(bundle).single()
            assertTrue("Bundle of '$typed' : ${back.valueLabel()}", back.unit == "mg/L" || back.valueLabel().contains("mg/L"))
        }
    }
}
```

## SD-11 — QR texte de Haru : l'autonomie sort du QR dans presque toutes les langues

- **Cas** : UC-I18N-008, UC-QRT-009, UC-HUM-023 ; étapes appareil T22.5 et T21.6.
- **Code** : `…/qr/JemmaTextPayloadBuilder.kt:82-93` (rangs : ♿ = 12, le premier retiré ; 🤰 = 11 ; 💉 = 10).
- **Entrée** : la persona `demo_haru` telle que semée.
- **Constat (calculé)** : avec un portage Python du constructeur, le texte complet de Haru fait 1 797 octets en
  anglais (3 octets sous le plafond) et dépasse 1 800 octets dans 23 langues sur 25. En français : 1 860 octets,
  les 2 lignes ♿ et 1 ligne 🤰 sont retirées. En japonais (sa langue) : 1 880 octets, 2 lignes ♿ et 2 lignes 🤰
  retirées. En bengali, thaï, hindi : les vaccins sortent aussi. Kurodo et Kamekichi tiennent dans les 25
  langues. Le marqueur « ✂️ … » est bien présent ; rien n'est perdu en silence.
- **Deux conséquences** :
  1. `qa/device/README.md` T22.5 attend « QR texte Haru FR : section ♿ » et T21.6 attend la ligne « Naissances
     vivantes » en japonais : ces attentes ne peuvent plus être tenues depuis le plafond à 1 800 octets.
  2. Question de priorité clinique, à trancher par une personne : « malentendante, marche avec une canne » est
     la première information retirée, alors qu'elle change la façon d'aborder la victime. Les vaccins et les
     résultats de laboratoire passent avant.
- **Certitude** : calculé. Les libellés réels de la KB peuvent déplacer le seuil de quelques octets.

```kotlin
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import be.heyman.android.jemmapassdemo.testsupport.TextQrProbe
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruFunctionalStatusInQrTest {

    @Test
    fun `UC-HUM-023 the functional status of Haru is in her text QR in French and in Japanese`() {
        for (lang in listOf(JemmaTextPayloadBuilder.Lang.FR, JemmaTextPayloadBuilder.Lang.JA)) {
            val h = ProfileFixtures.hydrated(ProfileFixtures.persona(JemmaPersonasSeeder.SID_HARU).j, uiLang = lang.isoCode)
            val text = JemmaTextPayloadBuilder.build(h, lang)
            assertTrue("$lang : hearing loss and cane are left out", TextQrProbe.isWhole(text, h, TextQrProbe.FUNCTIONAL))
        }
    }
}
```

## SD-12 — Ré-import de son propre QR : détails du Bundle effacés

- **Cas** : UC-DEV-021, UC-IMP-004, UC-IMP-005.
- **Code** : `…/profiles/ProfilesRepository.kt:429-437` (`resolveNativePillars` : tout ce qui n'est ni
  `MANUAL_EDIT` ni `ASSISTANT_*` reconstruit les piliers depuis le `_j` entrant).
- **Entrée** : Haru, pacemaker avec UDI `(01)00643169007222(21)PJN1234567` ; elle rescanne son propre QR.
- **Attendu** : UDI, série, lot, fabricant conservés quand le code et la date sont les mêmes.
- **Constaté à la lecture** : le `_j` ne porte aucun de ces champs ; ils sont effacés.
- **Risque pour la personne** : avant une IRM ou une défibrillation, l'identifiant du dispositif n'est plus là.

```kotlin
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.ips.IpsNativePillars
import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class ReimportKeepsDeviceDetailsTest {

    @Test
    fun `UC-DEV-021 re-importing the QR of a stored profile keeps what only the Bundle holds`() {
        val haru = ProfileFixtures.persona(JemmaPersonasSeeder.SID_HARU)
        // what ProfilesRepository.resolveNativePillars does for a scanned payload
        val afterImport = IpsNativePillars.fromJEntries(haru.j.im, haru.j.pr, haru.j.dv, haru.j.rs, haru.j.ph, haru.j.cn, haru.j.pg, haru.j.fs)
        assertEquals(haru.native.devices.map { it.udi }, afterImport.devices.map { it.udi })
        assertEquals(haru.native.immunizations.map { it.lotNumber }, afterImport.immunizations.map { it.lotNumber })
    }
}
```

## SD-13 — Problème « résolu » importé : revient actif

- **Cas** : UC-IMP-017, UC-PRB-017.
- **Code** : `…/ips/IpsProblem.kt:25-31` (`IpsProblemStatus.normalize` : tout ce qui n'est pas active /
  recurrence / relapse devient `active`).
- **Entrée** : `cn[]` = `{"c":"22298006","st":"resolved","d_display":"Myocardial infarction"}`.
- **Attendu** : rangé dans les antécédents, ou gardé avec un statut non actif.
- **Constaté à la lecture** : un problème actif. Il entre alors dans le contrôle médicament × maladie.
- **Risque pour la personne** : un infarctus guéri présenté comme en cours ; fausses contre-indications.

```kotlin
package be.heyman.android.jemmapassdemo.ips

import be.heyman.android.jemmapassdemo.qr.JCondition
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedProblemImportTest {

    @Test
    fun `UC-IMP-017 a resolved problem imported in cn does not become an active problem`() {
        val native = IpsNativePillars.fromJEntries(
            im = emptyList(),
            cn = listOf(JCondition(c = "22298006", st = "resolved", displayLabel = "Myocardial infarction")),
        )
        assertTrue(native.problems.none { it.clinicalStatus == IpsProblemStatus.ACTIVE })
    }
}
```

## SD-14 — Valeur codée d'un résultat : système de code perdu par `_j`

- **Cas** : UC-RES-024.
- **Code** : `…/ips/IpsResult.kt:127-141` (la projection porte `vc` sans son système) et `:166-192`
  (`fromJEntry` : `valueCodeSystem` par défaut, SNOMED).
- **Entrée** : un résultat dont la valeur est une réponse LOINC (`LA6576-8`).
- **Attendu** : le système voyage avec le code, ou la valeur revient en texte.
- **Constaté à la lecture** : après un import, le code `LA6576-8` est annoncé comme un code SNOMED.
- **Risque pour la personne** : faible ; document invalide pour un système tiers.

```kotlin
package be.heyman.android.jemmapassdemo.ips

import org.junit.Assert.assertEquals
import org.junit.Test

class CodedValueSystemTest {

    @Test
    fun `UC-RES-024 the code system of a coded value survives the projection`() {
        val r = IpsResult(id = "rs-1", code = "5778-6", display = "Urine colour",
            valueCode = "LA6576-8", valueCodeSystem = IpsCodeSystems.LOINC, valueDisplay = "Yellow")
        assertEquals(IpsCodeSystems.LOINC, IpsResult.fromJEntry(r.toJEntry()).valueCodeSystem)
    }
}
```

## SD-15 — Profil qui n'a qu'un nom de famille

- **Cas** : UC-HUM-005.
- **Code** : `…/qr/JemmaProfileJ.kt` (`displayName()` : `gn fn`, sinon `gn`, sinon « Profile inconnu »).
- **Entrée** : un QR dont le patient n'a que `fn` (mononyme rangé en nom de famille).
- **Attendu** : le nom est montré dans le dialogue d'import.
- **Constaté à la lecture** : « Profile inconnu ».
- **Risque pour la personne** : l'aidant importe (ou refuse) un profil sans savoir de qui il s'agit.

```kotlin
package be.heyman.android.jemmapassdemo.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {

    @Test
    fun `UC-HUM-005 a profile with a family name only shows that name`() {
        assertEquals("Dupont", JemmaProfileJ(j = "1.2", p = JPatient(fn = "Dupont")).displayName())
        assertEquals("Dupont", JemmaProfileJ(j = "1.2", p = JPatient(gn = " ", fn = "Dupont")).displayName())
    }
}
```

## SD-16 — Date de début d'un traitement absente du Bundle

- **Cas** : UC-MED-009, UC-FHIR-005.
- **Code** : `…/qr/JemmaFhirBundleBuilder.kt:270-304` (le `MedicationStatement` ne reçoit ni `effective[x]` ni
  la raison d'absence ; `md[].eff` et `md[].effar` ne sont lus nulle part dans ce fichier). Même constat pour
  les réactions, le début et la catégorie d'une allergie (UC-FHIR-012) et pour `p.ids` / `p.idn` (UC-FHIR-013).
- **Entrée** : warfarine depuis le `2020-03-01`.
- **Attendu** : `effectiveDateTime` (ou `effectivePeriod`), sinon la raison d'absence.
- **Constaté à la lecture** : la date reste dans `_j` seulement.
- **Risque pour la personne** : le médecin étranger ne sait pas depuis quand le traitement est pris.

```kotlin
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.testsupport.ProfileFixtures
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationStartDateTest {

    @Test
    fun `UC-MED-009 the start date of a treatment is in the document`() {
        val raw = JemmaProfileJ(j = "1.2", sid = "qa_eff", p = JPatient(gn = "Haru"),
            md = listOf(JMedication(c = "B01AA03", displayLabel = "Warfarin", codeSystem = "http://www.whocc.no/atc", effective = "2020-03-01")))
        assertTrue(JemmaFhirBundleBuilder.build(ProfileFixtures.hydrated(raw)).contains("2020-03-01"))
    }
}
```

## SD-17 — Série vaccinale sans numéro de dose

- **Cas** : UC-VAC-012.
- **Code** : `…/ips/IpsFhirCodec.kt:364-377` (`seriesDoses` n'est écrit qu'à l'intérieur du bloc `doseNumber`).
- **Entrée** : série de 3, numéro de dose inconnu.
- **Attendu** : série conservée, ou refus explicite à la saisie.
- **Constaté à la lecture** : la série disparaît à la relecture.
- **Risque pour la personne** : faible.

```kotlin
package be.heyman.android.jemmapassdemo.ips

import dev.ohs.fhir.model.r4.Immunization
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesWithoutDoseTest {

    @Test
    fun `UC-VAC-012 a series is kept when the dose number is unknown`() {
        val im = IpsImmunization(id = "im-1", code = "836374004", display = "Hepatitis B vaccine", date = "2001", seriesDoses = 3)
        val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(im, "urn:uuid:00000000-0000-0000-0000-000000000001").build())
        assertEquals(3, IpsFhirCodec.fromFhir(IpsFhirCodec.json.decodeFromString(json) as Immunization).seriesDoses)
    }
}
```

## SD-18 — Très grandes et très petites valeurs numériques (à exécuter)

- **Cas** : UC-RES-014 (« démesurée, 25 chiffres : décimal exact »).
- **Code** : `…/ips/IpsFhirCodec.kt:581` et `:664-672`. Le test existant
  `integralAndCommaDecimalsSurviveTheDoubleJsonEncoding` indique par son nom que le JSON passe par un `double`.
- **Constat** : aucun, à la lecture. Les tests ajoutés couvrent 0, les négatifs, la virgule et les valeurs
  jusqu'à 9 999 999 ; au-delà (notation scientifique d'un `double`, plus de 15 chiffres significatifs) le
  résultat dépend du SDK.
- **Risque pour la personne** : une valeur de laboratoire altérée ; improbable aux ordres de grandeur usuels.

```kotlin
package be.heyman.android.jemmapassdemo.ips

import dev.ohs.fhir.model.r4.Observation
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtremeDecimalTest {

    @Test
    fun `UC-RES-014 very large and very small values are exact`() {
        for (typed in listOf("25000000", "123456789012", "0.0000001", "0.123456789012345678", "1234567890123456789012345")) {
            val r = IpsResult(id = "rs-1", code = "2823-3", display = "Potassium", value = typed, unit = "mmol/L")
            val json = IpsFhirCodec.encode(IpsFhirCodec.toFhir(r, "urn:uuid:00000000-0000-0000-0000-000000000001").build())
            assertEquals(typed, IpsFhirCodec.fromFhir(IpsFhirCodec.json.decodeFromString(json) as Observation).value)
        }
    }
}
```

## SD-19 — Données de démonstration : contacts d'urgence injoignables

- **Cas** : UC-QRT-005, UC-PAT-016, UC-HUM-023.
- **Code** : `…/qr/JemmaPersonasSeeder.kt:251-257` et `:307-313` (contacts de Kurodo et Kamekichi : un nom, une
  relation `friend`, une adresse ; ni téléphone ni courriel) ; Haru n'a aucun contact.
- **Constaté à la lecture** : le QR texte imprime « Kamekichi (friend) », sans moyen de joindre la personne, et
  la relation `friend` n'est pas un code du catalogue (`FRND`) : elle reste en anglais dans les 25 langues.
  Aucune étape appareil ne peut donc vérifier, sur les personas, que le téléphone du contact arrive au
  secouriste.
- **Proposition** : donner un téléphone fictif et la relation `FRND` aux contacts semés (changement de données
  de démonstration, hors du périmètre de ce couloir).

```kotlin
package be.heyman.android.jemmapassdemo.qr

import be.heyman.android.jemmapassdemo.pillars.IpsRelationshipCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

class SeededContactsTest {

    @Test
    fun `UC-QRT-005 every seeded emergency contact can be reached and has a catalog relation`() {
        for (profile in JemmaPersonasSeeder.getDemoProfiles()) {
            for (c in profile.p!!.ct) {
                assertTrue("${profile.sid} ${c.n} has no phone nor e-mail", !c.p.isNullOrBlank() || !c.e.isNullOrBlank())
                assertTrue("${profile.sid} ${c.n} relation '${c.r}'", IpsRelationshipCatalog.isValidCode(c.r))
            }
        }
    }
}
```

## SD-20 — Divers, risque bas (sans test fourni)

- `…/qr/JemmaProfileJ.kt` (`JCondition.rs`, `rc`) : la raison d'un problème importé n'a pas de champ dans
  `IpsProblem` ; elle disparaît à la première sauvegarde (`IpsProblem.toJCondition`).
- `…/ips/IpsFhirCodec.kt:333-336`, `:414-415` : une date illisible sur un pilier natif importé (`05/02/2019`)
  devient « date inconnue » sans message. Voulu pour ne pas planter ; la perte n'est signalée nulle part.
- `…/qr/JemmaQrFrameSplitter.kt:76` : l'en-tête de trame est compté pour 10 octets ; `JF:100/120|` en fait 11.
  À partir de 100 trames (Bundle d'environ 70 ko), une trame peut dépasser le budget de 1 à 3 octets.
- `…/qr/JemmaTextPayloadBuilder.kt:161-163` : pour un mononyme dont `fn` est une chaîne vide (Kamekichi), la
  ligne du nom porte deux espaces (« Kamekichi  (M) »).
- `…/ips/IpsFhirCodec.kt:99-100`, `:616`, `:818` : `fhirId` remplace les caractères interdits ; deux
  identifiants qui ne diffèrent que par un caractère interdit deviendraient identiques. Sans effet tant que les
  identifiants sont des UUID.
