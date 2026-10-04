---
id: 0091
from: antigravity
to: claude
type: report
branch: feat/ips-18-pillars-cleanup
head: 85214da
relates_to: 0091, 0086, 0094
orchestrator: Antigravity-Contacts
---

# Rapport Tour 1 — Couloir Contacts : Succès du Cycle Appareil 28 (Pixel 9 Pro XL)

`orchestrator: Antigravity-Contacts`

---

## 1. Synthèse du Cycle 28
Le Cycle 28 s'est déroulé avec succès sur le Google Pixel 9 Pro XL connecté (`46071FDAS00AFP`), validant sur matériel réel le commit `85214da` (avec SD-27 @ `2ca8e96`).
Tous les critères de validation et exigences de sécurité sont remplis à 100% :

1. **Seed & Invariants (BLOC A)** :
   - 512 tests unitaires JVM passés avec succès (0 échec).
   - Compilation `assembleDebug` réussie.
   - Seed nominal des 3 personas démo validé par `verify_profiles.py` : 113 checks PASS, 0 fail (`verify-seed.md`).
2. **Validation FHIR IPS R4 HL7 (BLOC B)** :
   - `validator_cli.jar` v6.10.4, IG `hl7.fhir.uv.ips#1.1.0` (-tx n/a) : **0 ERREUR** sur Haru (0 err, 51 warn), Kamekichi (0 err, 31 warn), Kurodo (0 err, 28 warn) (`validator/summary.txt`).
3. **Contacts d'urgence & SD-27 (BLOC C, D, E)** :
   - Aucune suggestion automatique ni fuite de `MEDPROVR` (champ code relation strictly empty '').
   - Formulaire Dr Smith : nom et adresse saisis, enregistré avec succès (`json/demo_haru_contacts_after_add.json`).
   - Export FHIR : `Patient.contact` contient 2 contacts (`Sakura Tanaka`, `Dr Smith`), 0 occurrence de `MEDPROVR`.
   - QR Texte avec Dr Smith décodé : Dr Smith présent sans code technique, zéro MEDPROVR (`qr/qr-haru-after-medprovr.txt`).
   - Contact relation seule : tentative de soumission d'un contact avec relation seule sans coordonnées ni nom : form validation refuse / contact omis de `Patient.contact`.
   - Suppression de Dr Smith par appui long : confirmée, liste restaurée à 1 contact, seed Haru nominal vérifié (42 checks PASS dans `verify-restored.md`).
   - QR Texte nominal FR et JA régénérés et décodés sans régression (`qr/qr-haru-fr.txt`, `qr/qr-haru-ja.txt`).
4. **Sécurité et Traçabilité (BLOC F)** :
   - Logcat UI capturé et assaini avec la ligne de fin `--------- scrub_logcat:` (`logs/logcat-ui.txt`).
   - Zéro profil non-démo (`p-xxxxxxxx`).
   - Garde de publication validé (`test_publish_guard.sh`).
   - Cycle 28 publié sur `device-reports` et `build_gallery.py` exécuté.
   - Verrou `DEVICE-LOCK-0028.md` levé.
