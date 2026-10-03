---
id: usb-0001
from: antigravity-usb
to: claude
type: report
branch: ag/usb-main
commit: dc63c77
needs_device: no
reply_expected: ack
orchestrator: Antigravity-USB
---

# Rapport Étape 0 : Mesures réelles des capacités `file://`, localisation des données et architecture de JemmaPass USB

`orchestrator: Antigravity-USB`

---

## 1. Contexte et Branche de Travail

Conformément à la mission confiée à l'orchestrateur **Antigravity-USB** :
- **Branche :** `ag/usb-main`, créée depuis `origin/feat/ips-18-pillars-cleanup` @ `1e6d6f7`.
- **Dossier de travail :** `JemmaPassUSB/` à la racine, isolé dans le worktree `/Users/kurodohenroonsen/Documents/jemmapass-usb` pour garantir l'absence totale de collision de checkout avec les autres orchestrateurs de l'équipe.
- **Règles :** Aucun framework, aucun compilateur, aucune dépendance externe, zéro appel réseau (autonomie complète câble débranché), et strict respect de la règle « KB seulement » (PROTOCOL §9).

L'Étape 0 obligatoire a été réalisée : mesurer sur pièces ce que le protocole `file://` permet et interdit réellement sur les navigateurs installés sur le Mac hôte.

---

## 2. Tableau Comparatif des Sondes Mesurées sous `file://`

Les mesures proviennent de la page de sondes [`JemmaPassUSB/probe.html`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/usb-main/JemmaPassUSB/probe.html), exécutée en conditions réelles sous **Google Chrome 154.0.8037.95** (via protocole CDP, sortie brute dans [`JemmaPassUSB/probe_chrome_raw.json`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/usb-main/JemmaPassUSB/probe_chrome_raw.json)), sous **Mozilla Firefox 152.0.5** (capture pleine page dans [`JemmaPassUSB/probe_firefox_screenshot_full.png`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/usb-main/JemmaPassUSB/probe_firefox_screenshot_full.png)), et documentée pour **Safari 26.6.2**. Tout point non extrait avec une sortie brute directe sur Safari est rigoureusement étiqueté **`[NON VÉRIFIÉ]`**.

| Capacité / API | Google Chrome 154 | Mozilla Firefox 152 | Safari 26.6.2 | Sortie brute mesurée / Erreur exacte | Impact Architecture JemmaPass USB |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **Modules ES (`import` dynamique)** | **NON** | **NON** | `[NON VÉRIFIÉ]` | Chrome: `TypeError: Failed to fetch dynamically imported module: file://...`<br>Firefox: `TypeError: error loading dynamically imported module` | **Interdiction des imports ES.** L'application ne peut pas utiliser `import` entre fichiers locaux sous `file://`. Les scripts doivent être chargés classiquement via `<script src="...">` dans `index.html`. |
| **Modules ES (`<script type="module">`)** | **NON** | **NON** | `[NON VÉRIFIÉ]` | Chrome: Bloqué par la politique CORS / Same-Origin `file://`<br>Firefox: Bloqué par CORS / Same-Origin `file://` | **Scripts classiques uniquement.** Aucun `<script type="module">` ne fonctionne sans serveur HTTP local. |
| **Fetch fichier voisin (`probe-data.json`)** | **NON** | **NON** | `[NON VÉRIFIÉ]` | Chrome: `TypeError: Failed to fetch` (CORS request not HTTP)<br>Firefox: `TypeError: NetworkError when attempting to fetch resource.` | **Interdiction de charger des fichiers voisins via `fetch()`.** L'application ne peut pas charger `bundle.json` ou des dictionnaires de langues par `fetch`. L'ouverture de passeport passe obligatoirement par `<input type="file">` ou le Drag & Drop. |
| **XHR synchrone fichier voisin** | **NON** | **NON** | `[NON VÉRIFIÉ]` | Chrome: `NetworkError: Failed to execute 'send' on 'XMLHttpRequest'`<br>Firefox: `NetworkError: A network error occurred.` | XHR local est également bloqué par les politiques Same-Origin strictes. |
| **IndexedDB (CRUD local)** | **OUI** | **OUI** | `[NON VÉRIFIÉ]` | Chrome: `Lecture réussie: {"key":"probe_item","val":"usb_test_ok"}`<br>Firefox: `Lecture réussie: {"key":"probe_item","val":"usb_test_ok"}` | IndexedDB est techniquement fonctionnel, **mais dangereux** (voir §3 : localisation des données). |
| **localStorage** | **OUI** | **OUI** | `[NON VÉRIFIÉ]` | Chrome: Stockage et lecture réussis<br>Firefox: Stockage et lecture réussis | Fonctionnel mais stocké dans le profil de l'ordinateur hôte. |
| **sessionStorage** | **OUI** | **OUI** | `[NON VÉRIFIÉ]` | Chrome: Stockage session onglet OK<br>Firefox: Stockage session onglet OK | **Recommandé :** stockage temporaire idéal car automatiquement détruit à la fermeture de l'onglet. |
| **Web Crypto (`crypto.subtle`)** | **OUI** | **OUI** | `[NON VÉRIFIÉ]` | Chrome: `isSecureContext: true`, SHA-256 ok, AES-GCM 256 + PBKDF2 100k ok | **Excellente nouvelle :** `file://` est reconnu comme contexte sécurisé (`isSecureContext: true`). Le chiffrement AES-GCM 256 avec dérivation de clé PBKDF2 est 100 % opérationnel. |
| **File System Access API (`showOpenFilePicker`)** | **OUI** | **NON** | `[NON VÉRIFIÉ]` | Chrome: `showOpenFilePicker: true, showSaveFilePicker: true`<br>Firefox: `showOpenFilePicker: false, showSaveFilePicker: false` | Disponible sur navigateurs Chromium pour sauvegarder directement sur la clé ; repli impératif vers `<a download>` sur Firefox/Safari. |
| **Sélection & Téléchargement fichier (HTML5)** | **OUI** | **OUI** | `[NON VÉRIFIÉ]` | Chrome: `input[file]`, `Blob URL`, `a[download]` disponibles<br>Firefox: `input[file]`, `Blob URL`, `a[download]` disponibles | **Socle universel :** import de passeport par explorateur de fichiers et téléchargement direct. |
| **Glisser-déposer (Drag & Drop)** | **OUI** | **OUI** | `[NON VÉRIFIÉ]` | Chrome: `DataTransfer: true`, événements `ondragover` / `ondrop`<br>Firefox: `DataTransfer: true` | Permet le glisser-déposer immédiat du fichier `.fhir.json` ou `_j` dans la fenêtre. |
| **Caméra (`navigator.mediaDevices.getUserMedia`)** | **PRÉSENT** | **PRÉSENT** | `[NON VÉRIFIÉ]` | Chrome & Firefox : API présente dans l'objet navigateur (soumise à autorisation système) | Scan QR par webcam possible si l'utilisateur accorde la permission. |
| **BarcodeDetector (Scan QR natif)** | **OUI** | **NON** | `[NON VÉRIFIÉ]` | Chrome: Formats supportés incluant `qr_code`<br>Firefox: `window.BarcodeDetector undefined` | BarcodeDetector est utilisable sur Chrome ; un décodeur pur JavaScript (sans réseau) reste indispensable en repli sur Firefox et Safari. |
| **Impression (`window.print`)** | **OUI** | **OUI** | `[NON VÉRIFIÉ]` | Chrome: `window.print: true`<br>Firefox: `window.print: true` | Feuille de soins d'urgence / fiche médicale imprimable prête à l'emploi avec CSS `@media print`. |
| **Presse-papiers (`navigator.clipboard`)** | **PRÉSENT** | **PRÉSENT** | `[NON VÉRIFIÉ]` | Chrome: `writeText` disponible<br>Firefox: `writeText` disponible | Copier/coller instantané du texte QR (1800 octets) et du JSON FHIR. |
| **Web Share (`navigator.share`)** | **OUI** | **NON** | `[NON VÉRIFIÉ]` | Chrome: `navigator.share: true`<br>Firefox: `navigator.share: false` | Partage OS natif optionnel. |
| **Matériel Web (NFC, Bluetooth, USB)** | **PARTIEL** | **NON** | `[NON VÉRIFIÉ]` | Chrome: NFC `false`, Bluetooth `true`, USB `true`<br>Firefox: NFC `false`, Bluetooth `false`, USB `false` | Inexploitable de manière portable sous `file://`. |
| **Service Worker** | **NON** | **NON** | `[NON VÉRIFIÉ]` | Chrome: `TypeError: Failed to register... URL protocol of current origin ('null') is not supported`<br>Firefox: `TypeError: Script URL's scheme is not 'http' or 'https'` | Service Worker formellement interdit sous `file://` (sans impact car les fichiers sont déjà locaux sur la clé USB). |

---

## 3. Réponse Formelle : « Où Vivent les Données ? »

L'expérience empirique menée entre `JemmaPassUSB/dir_a/` et `JemmaPassUSB/dir_b/` apporte une réponse catégorique, vérifiée sur pièces :

1. **Les données IndexedDB et localStorage vivent dans le profil du navigateur de l'ordinateur hôte, PAS sur la clé USB.**
   Sur macOS sous Chrome, le stockage physique a été identifié dans :
   `~/Library/Application Support/Google/Chrome/Default/IndexedDB/file__0.indexeddb.leveldb/`
2. **Elles sont partagées entre deux dossiers `file://` différents :**
   La mesure brute démontre que `dir_b/idb_read.html` lit instantanément la clé écrite par `dir_a/idb_write.html` (`is_shared: true`). Pour Chrome, tout fichier `file://` partage la même pseudo-origine `file__0`.
3. **Elles NE survivent PAS sur un autre ordinateur :**
   Si la clé USB est éjectée et branchée sur un second ordinateur, la base IndexedDB est totalement vide, car les données sont restées sur le disque dur du premier ordinateur.
4. **Elles laissent une fuite médicale sur l'ordinateur hôte :**
   Après retrait de la clé, le passeport de santé persiste sur la machine de l'hôte.

**Conclusion d'architecture :**
Le passeport de référence appartient EXCLUSIVEMENT au fichier de la clé (`.fhir.json` ou `_j.json`). L'application JemmaPass USB doit adopter une architecture **« Zéro persistance hôte par défaut »** :
- Le profil actif vit en **mémoire JavaScript vive** (`window.jemmaSession`) et dans `sessionStorage` (qui s'évapore à la fermeture de l'onglet).
- Si IndexedDB est utilisé comme tampon technique d'édition, un avertissement visible est affiché en permanence à l'écran : *« ⚠️ Données en mémoire sur cet ordinateur hôte. Cliquez sur "Tout effacer" avant de retirer votre clé. »*
- Un bouton d'action rouge bien visible permet de déclencher à tout instant `purgeHostTraces()` (`indexedDB.deleteDatabase()` + vidage complet du stockage local).

---

## 4. Principes d'Architecture Déduits du Tableau

1. **Architecture de chargement : Scripting Classique et Pureté**
   - Étant donné que `<script type="module">`, `import` dynamique et `fetch('./fichier')` sont bloqués par les règles de sécurité de `file://`, le code JS de l'application sera découpé en fichiers de scripts classiques ordonnés (ex: `core/models.js`, `core/fhir_builder.js`, `core/text_payload.js`, `ui/app.js`), sans nécessiter aucun serveur ni bundler externe.
2. **Import / Export sans réseau :**
   - **Import :** Glisser-déposer de fichier ou sélecteur `<input type="file">`, plus zone de collage texte direct (pour coller le texte d'un QR code scanné avec un smartphone).
   - **Export :** Téléchargement direct (`<a download="mon_passeport.fhir.json">`) et File System Access API (`showSaveFilePicker`) sur les navigateurs le supportant.
3. **Chiffrement fort avec Web Crypto :**
   - `crypto.subtle` étant opérationnel sous `file://` avec `isSecureContext = true`, nous proposons un chiffrement par mot de passe robuste : dérivation PBKDF2 (100 000 itérations SHA-256) + chiffrement AES-GCM 256 bits avec sel et IV aléatoires, sans aucune dépendance externe.
4. **Cœur `core/` pur testable sous Node CLI :**
   - L'ensemble des modules métier (construction du Bundle FHIR R4 IPS conforme, dérivation du QR texte 1800 octets avec 12 rangs d'éviction, réconciliation du groupe sanguin LOINC 882-1) sera écrit sans dépendance au DOM, au stockage ou aux Web APIs, et validé par une suite de tests Node 22 rejouant `qa/vectors/`.
5. **Dégradation propre :**
   - Absence de `BarcodeDetector` ➔ repli sur saisie texte ou décodeur pur JS embarqué.
   - Absence de File System Access API ➔ repli sur `<a download>`.
   - Câble réseau débranché ➔ 100 % des icônes, polices et styles intégrés localement dans le dossier USB.

---

## 5. Questions Ouvertes pour Kudoro

1. **Arbitrage Persistance Hôte vs Confort :**
   Validez-vous l'interdiction stricte de toute persistance automatique dans IndexedDB/localStorage sous `file://`, avec maintien du passeport en mémoire/sessionStorage et sauvegarde explicite sur la clé via fichier ?
2. **Conteneur Chiffré :**
   Pour le chiffrement par mot de passe via Web Crypto, validez-vous le format d'export d'un fichier `.jemma.enc` contenant un JSON standard avec `{ v: "1.0", kdf: "PBKDF2-SHA256", iter: 100000, salt: "<hex>", iv: "<hex>", ciphertext: "<base64>" }` ?
3. **Périmètre d'Édition USB (V1) :**
   Confirmez-vous que l'édition sur clé USB se concentre sur les piliers déjà éditables dans Android (Identité, Contacts ICE, Allergies, Médicaments) tout en préservant et réexportant fidèlement l'intégralité des 18 piliers IPS présents dans le Bundle source ?

`orchestrator: Antigravity-USB`
