---
id: analyse-0001
from: antigravity-analyse
to: claude
type: report
branch: ag/analyse-fonctionnelle
commit: 46b73e17981e97360601e08e0cf91a3efcfa6792
needs_device: no
reply_expected: ack
orchestrator: Antigravity-Analyse
---

# Rapport Tranche 1 : Cartographie des Acteurs, Appareils, Matrice des Canaux et Registre des Décisions

`orchestrator: Antigravity-Analyse`

## 1. Synthèse de la Livraison

La **Tranche 1** de l'analyse fonctionnelle complète de l'écosystème JemmaPass a été rédigée et validée dans le fichier [`docs/functional/00-carte.md`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/analyse-fonctionnelle/docs/functional/00-carte.md) sur la branche dédiée `ag/analyse-fonctionnelle` (dérivée de `origin/feat/ips-18-pillars-cleanup` @ `1e6d6f7`).

### Contenu produit
1. **Taxonomie des 8 Acteurs** : Titulaire du passeport, Proche/Aidant, Secouriste/DMAT, Soignant, Pharmacien, Interprète, Administrateur de lieu d'accueil, Personne sans appareil.
2. **Taxonomie des 9 Appareils & Interfaces** : Téléphone Android, iPhone, Extension Chrome, Ordinateur nu sans rien d'installé, Tablette partagée, Montre connectée, Papier imprimé (Pocket Pass), Carte NFC, Clé USB.
3. **Matrice Complète des Canaux d'Échange** : Analyse des 8 canaux (QR Texte ≤ 1800B, QR Compact `_j2`, QR FHIR `JF:i/N`, Fichier, NFC, P2P Radio BLE/Nearby, USB, Papier) pour chaque paire d'appareils (81 combinaisons évaluées en Possible, Impossible, Partiel, ou Inconnu/Non vérifié).
4. **Découpage en 29 Domaines Fonctionnels** : Normalisation des préfixes `UC-<domaine>-<numéro>` pour les tranches suivantes (Socle, 18 piliers IPS, Moteur clinique & IA, Canaux physiques, Crise & Triage).
5. **Registre des Décisions Ouvertes (DEC-01 à DEC-12)** : 12 arbitrages structurés avec options et impacts médicaux/techniques, réservés à Kudoro.

Conformément aux instructions de cadrage de la Tranche 1, aucun micro-cas d'usage détaillé n'est encore introduit dans `00-carte.md` (ils démarrent en Tranche 2 avec `10-existant-android.md`).

---

## 2. Décompte des Cas d'Usage par Étiquette (Tranche 1)

| Étiquette | Décompte Tranche 1 | Commentaire |
| :--- | :---: | :--- |
| `[EXISTANT]` | 0 | Débutent à la Tranche 2 (`10-existant-android.md`) |
| `[PARTIEL]` | 0 | Débutent à la Tranche 2 (`10-existant-android.md`) |
| `[PROPOSÉ]` | 0 | Débutent aux Tranches 3, 4 et 5 |
| `[NON VÉRIFIÉ]` | 0 | Débutent aux Tranches 3, 4 et 5 |
| **Total Cas d'Usage** | **0** | *Conforme au cadrage : carte et matrice préalables* |

---

## 3. Sortie Brute de `python3 qa/docs/check_citations.py`

```text
=== Rapport de Vérification des Citations de Code ===
Total citations analysées : 298
Citations exactes (±5 lignes) : 298
Citations décalées : 0
Fichiers manquants : 0
Symboles inventés : 0

SUCCÈS : 0 décalage, 0 symbole inventé.
```

---

## 4. Questions et Décisions Ouvertes pour Kudoro

Les 12 décisions documentées dans `docs/functional/00-carte.md` (§6) sont soumises à son arbitrage :

1. **DEC-01 (Chiffrement clé USB)** : Faut-il chiffrer le dossier sur la clé USB (Option A : AES chiffré, Option B : clair d'urgence immédiat, Option C : hybride vital clair + historique chiffré) ?
2. **DEC-02 (Format maître clé USB)** : HTML interactif universel local, PDF statique multi-pages ou ensemble composite (HTML + PDF + FHIR JSON) ?
3. **DEC-03 (Pont P2P Android ↔ iOS)** : Reliance exclusive sur QR codes optiques et papier, ou spécification d'un profil BLE GATT ouvert commun ?
4. **DEC-04 (Exposition Lockscreen)** : Données vitales d'urgence seules (groupe sanguin, allergies létales, ICE) ou accès au QR code complet des 18 piliers sans déverrouiller ?
5. **DEC-05 (Conflits multi-appareils)** : Last-Write-Wins (LWW) horodaté ou union additive sans suppression (Append-Only) ?
6. **DEC-06 (Médicaments non résolus KB)** : Blocage ferme avec verdict NOT_CHECKED / alerte explicite, ou mention informative tolérée ?
7. **DEC-07 (Carte NFC)** : Pointeur URL de secours ou stockage du profil compact `_j2` dans la puce NTAG216 (888 octets) ?
8. **DEC-08 (Statut SALT DCD 🕊️)** : Maintien de l'émission de la balise SOS pour identification des dépouilles ou extinction automatique de la radio ?
9. **DEC-09 (Habilitations non-soignants)** : Vues contextuelles filtrées (Mode « Refuge ») ou profil unique ouvert ?
10. **DEC-10 (Intégrité & Signature)** : Empreinte SHA-256 locale simple ou signature asymétrique Ed25519 on-device ?
11. **DEC-11 (Langues TTS d'urgence)** : Trio prioritaire (Japonais, Anglais, Français) ou engagement sur les 25 langues du QR texte ?
12. **DEC-12 (Sauvegarde Zéro-Cloud)** : Responsabilité 100 % utilisateur via supports amovibles physiques ou synchronisation locale P2P familiale ?

---

## 5. Prochaine Étape

En attente de la relecture de la Tranche 1 avant d'engager la rédaction de la **Tranche 2** : `10-existant-android.md` (recensement exhaustif de tous les cas `[EXISTANT]` et `[PARTIEL]` du code source).

`orchestrator: Antigravity-Analyse`
