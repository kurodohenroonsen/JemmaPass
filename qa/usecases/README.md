# Micro cas d'usage — JEMMA Pass

Catalogue dérivé du code (lecture, pas d'exécution) : **577 cas**, dont la majorité sans couverture
automatique aujourd'hui. Chaque cas a un identifiant stable, un type (nominal / alternatif / erreur /
limite), le résultat attendu, la couverture actuelle (test JVM, contrôle `verify_profiles`, étape
device) et le risque santé.

| Fichier | Périmètre | Cas |
|---|---|---|
| [01-pillar-editing.md](01-pillar-editing.md) | édition des 11 piliers | 219 |
| [02-safety-and-emergency.md](02-safety-and-emergency.md) | contrôles croisés, alertes, QR, PDF, SOS, assistant, scan | 178 |
| [03-data-integrity-and-people.md](03-data-integrity-and-people.md) | stockage, imports, multi-profils, conformité, i18n, accessibilité, diversité | 180 |

Chaque fichier se termine par « Trous de couverture prioritaires » et « Défauts probables repérés à
la lecture du code ». Règle de travail : un défaut n'est corrigé qu'après vérification dans le code
par l'intégrateur, avec un test qui porte l'identifiant du cas (`UC-…`).

## Défauts confirmés et corrigés

| Cas | Défaut | Correctif |
|---|---|---|
| UC-STO | Bundle FHIR en échec avalé : l'ancien Bundle regagnait sur la modification suivante | Bundle périmé supprimé, `_j` fait foi jusqu'à la prochaine sauvegarde réussie |
| UC-STO | écritures non atomiques (profil tronqué si l'app est tuée) | `ProfileFiles.writeAtomic` (fichier temporaire + renommage) |
| UC-STO | deux sauvegardes rapprochées s'écrasaient | verrou unique sur lecture-modification-écriture |
| UC-STO | `sid` d'un QR utilisé tel quel comme nom de fichier | `ProfileFiles.safeIdOrNull` |
| UC-AI | médicament introuvable → `is_clean=true` | `is_clean=false`, `checked=false`, avertissement explicite |

## À vérifier puis corriger (ordre de risque)

1. KB absente : les contrôles répondent « rien à signaler » sans dire qu'ils n'ont pas tourné.
2. QR texte : plafond 2200 octets alors qu'une trame en porte 1800 ; QR multi-trames jamais réassemblé.
3. PDF imprimé : allergies au-delà de 3 perdues ; criticité inconnue imprimée « L ».
4. Allergies : une seule réaction conservée à l'édition.
5. Médicaments : statut toujours `active` dans le Bundle ; dose à virgule absente du FHIR ;
   voie « inhalée » convertie en injection.
6. Groupe sanguin : Observation 882-1 désynchronisée après import.
7. Scan de médicament : contrôle marqué OK sans appel d'outil ; médicaments sans code ignorés par
   les contrôles du formulaire.
8. Identité : listes d'adresses / identifiants tronquées ; date de naissance complète obligatoire.
9. Double appui sur Enregistrer (allergie, médicament, identité) → doublon.
