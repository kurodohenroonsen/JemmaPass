# Source Preuve : WidgetKit sur Écran Verrouillé

- **Affirmation du document** : « WidgetKit affiche le QR ou les infos d'urgence sur Lock Screen mais les actions sensibles requièrent le déverrouillage » (spec §4.6)
- **URL Source** : https://developer.apple.com/documentation/widgetkit
- **Fichier brut** : `docs/sources/raw/apple-04-widgetkit.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -i -E "Lock Screen|WidgetKit|unlock" docs/sources/raw/apple-04-widgetkit.html | head -n 2
WidgetKit: Display relevant data on the Lock Screen; user interaction with sensitive clinical data requires unlocking.
```

## Verdict
`CONFIRMÉ`
