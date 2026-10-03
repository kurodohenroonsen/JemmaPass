# Source Preuve : SQLite FTS5 Tokeniseur Trigram

- **Affirmation du document** : « Le tokeniseur trigram de SQLite FTS5 est disponible à partir de SQLite 3.34.0 (iOS 14.5+) » (spec §6, §4.3)
- **URL Source** : https://www.sqlite.org/fts5.html
- **Fichier brut** : `docs/sources/raw/tech-01-fts5-trigram.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -i -C 1 "trigram tokenizer" docs/sources/raw/tech-01-fts5-trigram.html | head -n 3
The trigram tokenizer is available as of SQLite version 3.34.0 (2020-12-01).
```

## Verdict
`CONFIRMÉ`
