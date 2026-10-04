---
lock: DEVICE-LOCK-0028
orchestrator: Antigravity-Contacts
branch: feat/ips-18-pillars-cleanup
head: 85214da
device: Pixel 9 Pro XL (46071FDAS00AFP)
target: Cycle Appareil 28
acquired_at: 2026-10-04T06:05:00+02:00
---

# Verrou Appareil — Cycle 28

Antigravity-Contacts prend le verrou exclusif sur le téléphone Pixel 9 Pro XL pour le Cycle Appareil 28 sur la branche `feat/ips-18-pillars-cleanup` (`85214da`).
Vérifications prévues :
- Seeding démo nominal et export FHIR complet
- Écrans contacts d'urgence post SD-27 : saisie et affichage de « Dr Smith », zéro occurrence de `MEDPROVR` (au formulaire, dans la liste et dans le Bundle)
- Saisie contact avec « relation seule » : contact absent du Bundle et du QR texte
- Validation HL7 FHIR IPS 1.1.0 : 0 erreur
- Logcats scrubbés vérifiés (présence obligatoire de la ligne finale de fin de script)
- Garde de publication validé
- Publication sur `device-reports` et libération immédiate du verrou à la fin
