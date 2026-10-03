---
id: 0090
type: task
from: claude
to: antigravity (orchestrator: Antigravity-KB)
relates_to: 0055, 0082
decided_by: Kudoro, 2026-10-04 00:33 (intention : partager en pair-à-pair les bases sources de la KB)
---
# Partage des bases sources : d'abord l'inventaire des droits, source par source

Kudoro veut que JemmaPass partage, de pair à pair, les bases sources qui ont servi à construire la KB, selon les besoins réels de chaque appareil. Avant de concevoir quoi que ce soit, il faut savoir ce qu'on a le droit de redistribuer. Analyse seulement, aucun code, aucune publication. Les dossiers du pipeline restent en lecture seule.

Livrable : `docs/analysis/kb-sources-licences.md` sur `ag/0090-kb-licences` (depuis feat), avec ses pièces dans `docs/analysis/kb-sources-licences-evidence/`.

Un tableau, une ligne par source (les 32 lignes de `kb_sources`, plus tout ce que `_meta/SOURCES.md` et les 46 scripts de `forge/` lisent et qui n'y figure pas) :
1. nom, éditeur, version et date du fichier réellement utilisé (sortie SQL brute de `kb_sources`, chemin du script qui la lit) ;
2. ce qui en est repris dans la KB : quelles tables, combien de lignes (sortie SQL brute) ;
3. la licence : son nom, l'adresse du texte, et **la phrase exacte** du texte qui autorise ou interdit la redistribution, copiée de la page réellement téléchargée (page brute dans `evidence/`, commande et date dans `FETCH.log`). `_meta/LICENSES.md` est un point de départ, pas une source ;
4. conditions : attribution, mention à afficher, usage non commercial, compte ou accord nominatif, interdiction de redistribuer les données brutes ou dérivées, restriction par pays ;
5. verdict, parmi quatre : `REDISTRIBUABLE` · `SOUS CONDITIONS` (lesquelles) · `NON REDISTRIBUABLE` · `INCONNU` (texte non trouvé ou ambigu). Pas de cinquième catégorie, pas d'interprétation favorable : dans le doute, `INCONNU`.

Deux questions à traiter à part, avec pièces :
- la KB actuelle est déjà distribuée par `jemmapass.net/models` : quelles lignes du tableau sont déjà concernées par une redistribution aujourd'hui ?
- pour chaque source, la différence entre partager le **fichier source brut** et partager les **lignes dérivées** dans la KB.

Tu ne conclus pas sur ce que JemmaPass « peut » faire : tu donnes les textes. La décision est à Kudoro, qui peut vouloir un avis juridique.
Rapport : `to-claude/0090-report-kb-licences-Antigravity-KB.md`. SD-26 (`ag/0082-sd26`) reste à faire ; dis dans quel ordre tu les prends.
