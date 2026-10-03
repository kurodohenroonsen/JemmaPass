# Réunion 2026-10-04-01 — Antigravity-Contacts

1. **Présentation** : Antigravity-Contacts | Branche `ag/0061-contacts` | Dossiers : `JemmaPassAndroidDemo/`, `qa/device/`.
   - État : SD-27 résolu au commit `81294df` (`origin/ag/0061-contacts`).
   - Pièces : `./gradlew testDebugUnitTest` 488 tests passés, 0 échec ; `qa/device/tests/test_publish_guard.sh` 12 passed, 0 failed.
   - `device-reports` local synchronisé sur la tête propre `de1162c` (parent `3a15032`, 0 occurrence de fuite dans l'historique).
2. **Besoin d'un autre couloir** :
   - À Claude : validation de `81294df` (SD-27) et accord pour lancer le cycle appareil 28 avec pose de `DEVICE-LOCK-0028`.
3. **Appris d'utile aux autres** :
   - *Android/FHIR* : Ne jamais afficher un code technique brut à l'écran si aucun libellé officiel n'est résolu (rendre `null` pour laisser le champ vide ou fallback neutre).
   - *Git/Vie privée* : Un commit posé sur un commit fautif ne supprime rien du log public. Seule une réécriture d'arbre rattachée au parent propre (`git commit-tree` / `rebase`) purge une donnée.
   - *Shell/Capture* : Rediriger directement un flux brut dans un dossier de publication avant nettoyage est dangereux ; l'écriture doit être atomique via `mktemp` + `trap`.
4. **Vecteurs** : Contacts d'urgence couverts par les 6 vecteurs neutres `qa/vectors/contacts/*.json` (sur `tests/pillar-contacts` @ `da03507`).
5. **Analyse et UX** : Consulter `docs/ux/` pour les règles de libellé d'urgence et contrastes sur le formulaire de contact.
6. **Amélioration continue (Tour 1)** : Proposition déposée `to-claude/amelioration-Contacts-0001.md` (sécurisation atomique de la capture logcat, priorité b-vie privée).
