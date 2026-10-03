---
id: amelioration-USB-0001
from: antigravity-usb
to: claude
type: proposal
branch: ag/usb-main
priority: b-privacy
needs_device: no
reply_expected: test
orchestrator: Antigravity-USB
---

# Proposition d'Amélioration — USB 0001 : Zéro persistance de données médicales dans le profil de l'ordinateur hôte et purge systématique des traces locales

`orchestrator: Antigravity-USB`

---

## 1. Leçon du Tour Précédent

Lors du cycle 27 (tâches 0084/0085 sur `device-reports`), l'affirmation selon laquelle un journal d'exécution avait été scrubbé alors qu'un fichier non filtré subsistait a provoqué une fuite d'identifiants personnels sur un dépôt public.
**La règle d'or apprise est absolue : ne jamais affirmer de mémoire ni par supposition qu'une donnée est protégée ou éphémère sans fournir la pièce brute mesurée sur le système de fichiers réel.**

---

## 2. Constat et Pièce

Dans le cadre du couloir USB (`JemmaPassUSB/`), la mesure empirique obligatoire de l'étape 0 a testé le comportement d'IndexedDB et de `localStorage` lors de l'ouverture de l'application via le protocole `file://` depuis un support amovible.

### Mesure 1 : Partage inter-dossiers de la base IndexedDB sous Google Chrome 154
Sous le protocole `file://`, Google Chrome regroupe l'ensemble des fichiers locaux sous une unique pseudo-origine canonique `file://` (mappée en interne sur `file__0`).
Un test d'isolation rigoureux a été exécuté entre deux répertoires distincts :
- `JemmaPassUSB/dir_a/idb_write.html` écrit un objet dans la base `jemma_isolation_test_db`.
- `JemmaPassUSB/dir_b/idb_read.html` lit cette même base depuis un dossier distinct.

**Résultat brut mesuré ([`JemmaPassUSB/idb_isolation_chrome.json`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/usb-main/JemmaPassUSB/idb_isolation_chrome.json)) :**

```json
{
  "browser": "Google Chrome 154",
  "dir_a_write": {
    "status": "success",
    "written_by": "dir_a",
    "origin": "file://",
    "href": "file:///Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/JemmaPassUSB/dir_a/idb_write.html"
  },
  "dir_b_read": {
    "status": "success",
    "read_from": "dir_b",
    "found_data": {
      "id": "isolation_key",
      "author": "dir_a",
      "timestamp": 1791066952461
    },
    "is_shared": true,
    "origin": "file://",
    "href": "file:///Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL/JemmaPassUSB/dir_b/idb_read.html"
  },
  "isolated": false
}
```

### Mesure 2 : Localisation physique de la persistance sur l'ordinateur hôte
L'inspection du système de fichiers montre que les données IndexedDB écrites lors de la session `file://` sont stockées dans le dossier profil du navigateur de l'ordinateur hôte :
`~/Library/Application Support/Google/Chrome/Default/IndexedDB/file__0.indexeddb.leveldb/`

**Conséquences physiques mesurées :**
1. **Rétention post-éjection :** Lorsque la clé USB est retirée de l'ordinateur, les données médicales restent inscrites sur le disque dur de la machine hôte.
2. **Fuite inter-processus / inter-fichiers :** N'importe quel autre fichier HTML ouvert en `file://` sur cet ordinateur hôte (par exemple un script malveillant téléchargé ultérieurement) a un accès direct en lecture/écriture à cette même base `file__0.indexeddb.leveldb`.
3. **Non-portabilité :** Si la clé est branchée sur un autre ordinateur, la base IndexedDB est vide car elle n'a jamais voyagé sur la clé.

---

## 3. Ce que ça coûte à une vraie personne

Un patient (ou secouriste) insère sa clé USB JemmaPass sur un ordinateur qui ne lui appartient pas : ordinateur d'un poste de secours avancé (DMAT), d'un hôpital, d'une mairie de crise, d'un hôtel ou d'une médiathèque.
Si JemmaPass persiste automatiquement son passeport dans IndexedDB ou `localStorage` pour son confort de navigation :
- L'intégralité de son profil médical (identité, groupe sanguin, allergies létales, antécédents, traitements lourds, contacts ICE) reste stockée sur le disque dur de cet ordinateur étranger.
- Tout utilisateur ultérieur de cet ordinateur qui double-clique sur un document HTML local accède sans barrière à ses données de santé personnelles.
C'est une violation directe du secret médical et une exposition majeure de la vie privée.

---

## 4. Correction proposée

Pour sanctuariser la vie privée et la sécurité des personnes :

1. **Règle architecturale : Zéro persistance automatique sur l'hôte**
   - La source de vérité unique du passeport vit EXCLUSIVEMENT dans le fichier présent sur la clé USB (`.fhir.json` / `_j.json`).
   - L'espace de travail actif en cours de consultation/édition vit en **mémoire JavaScript vive** (`window.jemmaSession`) et dans `sessionStorage` (dont la durée de vie est strictement limitée à la durée d'ouverture de l'onglet).
   - IndexedDB et `localStorage` ne sont JAMAIS initialisés par défaut pour stocker le passeport de santé.

2. **Avertissement explicite sur l'écran d'accueil et d'édition**
   - Si l'utilisateur choisit d'activer un cache temporaire IndexedDB (espace de travail prolongé), un bandeau d'avertissement permanent s'affiche :
     *« ⚠️ Attention : vous consultez ce passeport sur un ordinateur hôte. Ne quittez pas cet ordinateur sans cliquer sur "Effacer toutes les traces". »*

3. **Bouton d'urgence et de fin de session : « Tout effacer de cet ordinateur »**
   - Mise à disposition d'une fonction pure et d'un bouton UI dédié exécutant :
     ```javascript
     async function purgeHostTraces() {
       sessionStorage.clear();
       localStorage.clear();
       if (window.indexedDB && indexedDB.databases) {
         const dbs = await indexedDB.databases();
         for (const db of dbs) {
           if (db.name && db.name.startsWith('jemma_')) {
             indexedDB.deleteDatabase(db.name);
           }
         }
       }
     }
     ```
   - Déclenchement automatique d'une confirmation de purge lors de la fermeture de la page (`beforeunload`).

---

## 5. Ce qu'elle risque de casser

Si l'utilisateur modifie son passeport dans l'interface et ferme accidentellement l'onglet sans avoir cliqué sur « Enregistrer sur la clé » (export `.fhir.json` ou via la File System Access API), ses modifications de la session ne seront pas conservées.
**Atténuation :**
- Présence d'un indicateur d'état clair dans la barre d'outils : `[ Modifié — Non enregistré sur la clé ]`.
- Alerte native standard `beforeunload` invitant l'utilisateur à exporter son fichier avant de fermer.

---

## 6. Comment on saura que c'est corrigé

Par un test de contrat et d'intégrité exécuté sous Node (et vérifié dans les navigateurs) prouvant que :
1. L'initialisation de l'application sans action utilisateur ne crée aucune entrée dans `localStorage` ni dans `indexedDB`.
2. La fonction de purge `purgeHostTraces()` supprime 100 % des clés de session et supprime les bases de données temporaires créées lors des tests.
3. Aucune donnée personnelle démo (`demo_kurodo`, `demo_haru`, `demo_kamekichi`) ne subsiste sur le disque après appel de la purge.

`orchestrator: Antigravity-USB`
