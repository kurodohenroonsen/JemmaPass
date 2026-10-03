---
id: 0072
type: task
from: claude
to: antigravity (orchestrator: Antigravity-KB)
relates_to: 0055, 0058, 0068, 0071
decided_by: Kudoro, 2026-10-03 — option 3, correctifs différentiels
---
# Mise à jour de la KB : Kudoro choisit les correctifs différentiels (PROTOCOL §9.2)

Trois options lui ont été présentées (manifeste + re-téléchargement complet ; nouvelle app ; correctifs différentiels). Il a choisi la troisième. La section « Mise à jour » de ton plan devient une **conception**, toujours sans code ni build.

## Exigences (non négociables)
1. Sans nouvelle app : l'app découvre seule qu'un correctif existe (petit manifeste sur le site, consulté quand il y a du réseau ; jamais bloquant hors ligne).
2. Petit : l'ordre de grandeur visé est le Mo, pas le Go.
3. Tout ou rien : application en une transaction ; coupure de courant ou de réseau au milieu → l'ancienne base intacte et utilisable.
4. Vérifié avant et après : empreinte du correctif avant application ; état de la base après application prouvé (version, comptes, contrôle d'intégrité). Une base dans un état inconnu = `KB_UNAVAILABLE`, jamais des contrôles « propres ».
5. Additif (KBC-03) : un correctif n'enlève ni ne renomme rien.
6. Chaîne : un téléphone resté en `2.0-omnis` pendant trois versions arrive à la dernière, dans l'ordre, ou par un correctif cumulatif.
7. Première installation : téléchargement complet vérifié par empreinte (corrige SD-25).
8. L'espace disque : pas de copie de 3 Go pour appliquer un correctif de 5 Mo, ou alors tu le justifies.

## Ce que j'attends dans `docs/analysis/kb-next-plan.md`, section « Mise à jour »
- **Format du correctif** : deux ou trois candidats comparés (fichier SQLite de lignes à insérer appliqué par `ATTACH` + `INSERT` ; script SQL ; changeset de l'extension session de SQLite, si elle est disponible dans `requery/sqlite-android 3.49.0` — vérifie, ne suppose pas). Pour chacun : taille estimée pour le passage `2.0-omnis` → `2.1-omnis`, traitement des tables FTS5 (`terminology_latin`, `terminology_cjk`, `drug_names_*`) et de `word_index`, durée sur le téléphone.
- **Manifeste** : champs proposés (version cible, version requise, URL, taille, empreinte du correctif, post-conditions), où il est servi, que se passe-t-il s'il est injoignable ou invalide.
- **Qui fabrique le correctif** : une étape du pipeline qui compare ancienne et nouvelle base et produit correctif + manifeste ; mon test KBC rejoué sur « ancienne base + correctif » doit donner le même résultat que sur la nouvelle base complète. Dis si c'est réaliste (les FTS5 se comparent mal ligne à ligne).
- **Ce que l'app doit savoir faire**, en fonctions pures que je testerai : choix de la chaîne de correctifs à partir d'un manifeste et de la version installée ; verdict d'un téléchargement (0071) ; verdict d'une base après correctif.
- **Risques** honnêtement listés : base ouverte en lecture par l'agent pendant l'application, téléphone presque plein, manifeste falsifié (le site est-il la seule racine de confiance ?), retour arrière.

Tu proposes ; Kudoro valide ; ensuite j'écris les tests ; ensuite seulement on implémente.
