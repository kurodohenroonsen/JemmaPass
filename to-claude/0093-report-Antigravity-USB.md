---
id: 0093
from: antigravity
to: claude
type: report
branch: ag/usb-main
head: b787b5a
relates_to: 0091, 0093, amelioration-USB-0001
orchestrator: Antigravity-USB
---

# Rapport Tour 1 — Couloir USB : Session en mémoire vive & zéro trace hôte (USB-S01..S09)

`orchestrator: Antigravity-USB`

---

## 1. Verdict des Tests
- **Statut** : 100% VERT (9 tests passés, 0 échec).
- **Branche** : `ag/usb-main` @ `737fb11` (poussée sur `origin/ag/usb-main`).
- **Tests** : `JemmaPassUSB/tests/session.test.js` issu de `origin/tests/usb-session` @ `3110675`.
- **Intégrité** : Le fichier de test `JemmaPassUSB/tests/session.test.js` n'a pas été modifié d'un seul caractère.

## 2. Sortie brute d'exécution
Commande : `node --test JemmaPassUSB/tests/session.test.js`
```
✔ USB-S01 an opened passport is the current one, from an object or from JSON text (3.308235ms)
✔ USB-S02 opening and reading a passport writes nothing in the storage (0.421447ms)
✔ USB-S03 after close nothing is current and nothing is left (0.449785ms)
✔ USB-S04 traces of an earlier JemmaPass session are seen, then removed by close (0.334469ms)
✔ USB-S05 keys of other local pages are neither counted nor erased (0.366627ms)
✔ USB-S06 without a storage the session still works, in memory only (0.370216ms)
✔ USB-S07 the session never reaches for the storages of the browser by itself (0.763462ms)
✔ USB-S08 text that is not JSON is refused and leaves the session as it was (0.584347ms)
✔ USB-S09 what current() returns cannot be used to change the passport behind the session's back (0.435732ms)
ℹ tests 9
ℹ suites 0
ℹ pass 9
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 50.840742
```

## 3. Ce qui a été implémenté dans `core/session.js`
- **Passeport en mémoire vive** : variable de clôture locale `activePassport` protégée.
- **Zéro persistance spontanée** : `open()` et `current()` ne touchent jamais au stockage injecté.
- **Indépendance vis-à-vis des API globales** : aucun appel ni référence à `localStorage`, `sessionStorage` ou `indexedDB` du navigateur hôte ; seul le stockage passé en argument est inspecté.
- **Isolation des clés tierces** : `tracesLeft()` et `close()` ne manipulent que les clés débutant par le préfixe `"jemmapass"` (`k.startsWith("jemmapass")`). Les clés d'autres pages locales (ex: `other-page-setting`) sont préservées intactes.
- **Immuabilité externe** : `current()` retourne une copie profonde (`structuredClone` avec repli `JSON.parse(JSON.stringify)`) empêchant toute altération collatérale de l'état interne de la session.
- **Résilience** : les entrées corrompues dans `open()` (ex: texte JSON invalide) sont rejetées par exception sans modifier l'état actif en cours.

## 4. Leçon
- **Ce qui a permis au risque d'exister** : La spécification web pour le protocole `file://` n'isole pas les origines par chemin sous les navigateurs majeurs (tous les fichiers locaux partagent la pseudo-origine `file__0` ou un profil unique). Un code confiant par défaut des données de santé au stockage Web (`localStorage`, `indexedDB`) laisserait une trace permanente et accessible sur l'ordinateur d'un tiers.
- **Règle préventive** : Toute application exécutable sur support amovible (clé USB) doit être conçue « memory-first » avec confinement strict, stockage éphémère injecté optionnel, préfixage exclusif des clés et purge atomique à la fermeture.
