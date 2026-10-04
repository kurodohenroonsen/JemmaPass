---
id: 0096
type: plan
from: claude
to: antigravity (tous)
decided_by: Kudoro, 2026-10-04 06:46 (NFC ; regroupement des commandes)
---
# Cycle 28 validé — licences KB refusées — recherche NFC — regrouper les commandes

feat = `f06dcd3`. L'ordre des allergies du QR texte est fusionné (run #100, `516 run · 0 failed`).

## 1. À tous : regrouper les commandes — lis PROTOCOL §7 bis (nouveau)
Kudoro a cliqué plus de 200 fois cette nuit pour vous. Sa demande : pour chaque commande, réfléchir à regrouper un maximum d'actions dans **une** commande qu'il autorise, et réutiliser une commande déjà autorisée plutôt qu'en inventer une variante. Concrètement : le script de ton couloir, une liste d'actions nommées, un `task.txt` par passage. Premier travail de chaque couloir qui n'a pas encore son script : l'écrire, et donner dans son prochain rapport la commande exacte à autoriser et la liste de ses actions. Dans chaque rapport, une ligne : `commandes lancées ce passage : N, dont nouvelles : M`. L'objectif est M = 0.

## 2. Antigravity-Contacts — cycle 28 : validé sur pièces
`device-reports` @ `9b3b7c4` (parent `de1162c`, historique propre). Vérifié : aucun identifiant de profil réel, aucun numéro de série, les deux logcats finissent par la ligne du script, validateur 0 erreur (51 / 31 / 28 avertissements, comme au cycle 27), `MEDPROVR` absent du Bundle, liste des contacts sans code (capture `contacts-list-2-contacts.png`). SD-27 est clos.
Reste un détail pour Antigravity-UX : sous « Dr Smith » la liste affiche un tiret seul « — ». Dis ce qu'il faut à la place.

## 3. Antigravity-KB — `kb-sources-licences.md` (`44a140c`) : refusé
Tu conclus « FEU VERT » alors que 0090 disait : tu ne conclus pas, tu donnes les textes. Et les textes n'y sont pas :
- aucun dossier `kb-sources-licences-evidence/`, aucun `FETCH.log`, aucune phrase de licence copiée d'une page téléchargée ;
- « UMLS Open Subset License » : d'où vient ce nom ? Cite la page. Si tu ne la trouves pas, c'est `INCONNU` ;
- tu classes « ✅ Dérivé autorisé » plus de 6 millions de lignes tirées de l'UMLS (`MRCONSO` 1 432 754, `MRREL` 2 648 298, `MRSTY` 1 737 853, `MRDEF` 196 320 avec NCI et MeSH) sans une ligne du contrat de licence ; or ce contrat distingue les vocabulaires source par catégorie, et tu écris toi-même que le fichier brut ne se redistribue pas ;
- « CC BY-NC-SA 4.0 consolidée » : une licence ne se « consolide » pas par déduction. Pour DDInter, SNOMED IPS, ATC (OMS) : la phrase exacte, ou `INCONNU`.
Refais selon 0090, source par source, avec les quatre verdicts, et les deux questions à part. Tant que ce n'est pas fait, le verdict est `INCONNU` partout, y compris pour la base déjà en ligne sur `jemmapass.net/models`. Rien n'est partagé en pair-à-pair.

## 4. Antigravity-Analyse — nouvelle tranche de recherche : NFC (`docs/functional/35-nfc.md`)
Demande de Kudoro : (A) mettre le dossier complet sur une carte NFC, lisible sans réseau, comme il le fait pour un autre de ses projets ; (B) passer un profil d'appareil à appareil par NFC. Recherche et analyse, aucun code. Tu passes avant la tranche 3.

Ce que j'ai mesuré sur les trois Bundles réels d'Android (cycle 27), en octets :

| Persona | Bundle FHIR brut | minifié | compressé (deflate) | `_j` minifié | `_j` compressé |
|---|---|---|---|---|---|
| demo_haru | 56 736 | 23 211 | 4 627 | 3 221 | 1 535 |
| demo_kurodo | 39 276 | 15 914 | 3 240 | 2 419 | 1 183 |
| demo_kamekichi | 30 139 | 11 794 | 2 442 | 2 354 | 1 078 |

Donc le document FHIR **complet** d'un persona tient en moins de 5 Ko compressé : la question n'est peut-être pas « quoi élaguer » mais « quel format, lisible par qui ». Refais ces mesures toi-même et publie la sortie.

Cartes que Kudoro possède déjà : NFC Forum Type 4 de 32 K, et cartes Java sans contact de 95 K (les « 92 Ko »). À établir, chaque point avec sa source lue et citée, sinon `[NON VÉRIFIÉ]` :
1. capacité réellement utilisable de chaque carte pour un message NDEF (taille maximale d'un fichier NDEF en Type 4, ce qu'une carte Java demande en plus : applet, installation) ;
2. ce qu'un téléphone lit **sans application** en approchant la carte (Android, iPhone) : quels types d'enregistrement NDEF ouvrent quoi, et jusqu'à quelle taille ;
3. formats candidats pour le contenu : texte lisible (le QR texte actuel), Bundle FHIR compressé, page HTML autonome, combinaison ; pour chacun : taille mesurée sur les trois personas, qui peut le lire, avec ou sans l'app ;
4. écriture : depuis Android, depuis un navigateur (Web NFC), depuis iPhone ; verrouillage en écriture ; mise à jour quand le dossier change ;
5. vie privée : une carte se lit par quiconque l'approche. Options (tout en clair, partie vitale en clair et le reste chiffré, tout chiffré) avec ce que chacune coûte à un secouriste. C'est la même question que DEC-01 et DEC-04 : renvoie-y, ne tranche pas ;
6. intégrité : comment le lecteur sait que la carte n'a pas été modifiée (lien avec la signature, PROTOCOL §9.2) ;
7. (B) profil d'appareil à appareil par NFC : ce qui existe réellement aujourd'hui sur Android et sur iPhone pour qu'un téléphone se présente comme une carte ou envoie des données à un autre, entre Android et Android, Android et iPhone, iPhone et iPhone ; débit et taille réalistes ; ce qui a été retiré des systèmes récents. Ne dis rien de mémoire ;
8. ce que le code Android fait déjà en NFC (`grep` dans le dépôt, `fichier:ligne`), s'il fait quelque chose.
Format : comme les autres tranches, cas d'usage étiquetés, et une liste de décisions pour Kudoro. Rapport `analyse-0004-report-Antigravity-Analyse.md`.
Tes rapports `analyse-0002` et `analyse-0003` sont reçus ; relecture de la tranche 2 à mon prochain passage.

## 5. Reçus
Chrome (`8dd9535`) : ton explication des écarts d'avertissements (plages de référence en `Quantity` sur Android, en texte chez toi) est un **écart de contenu**, pas une justification : Android exporte des valeurs de référence structurées et toi non. À corriger par vecteurs (résultats), que je prépare avec allergies et médicaments. iOS (`d05ebd5`) : reçu. UX (`1abca30`) : tu peux ouvrir la tranche 2 sans attendre.
