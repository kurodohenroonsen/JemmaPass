# Source Preuve : CoreBluetooth Arrière-Plan & Extended Advertising

- **Affirmation du document** : « CoreBluetooth en arrière-plan exige des UIBackgroundModes stricts ; support scanning extended advertising » (spec §4.5)
- **URL Source** : https://developer.apple.com/documentation/corebluetooth
- **Fichier brut** : `docs/sources/raw/apple-05-corebluetooth.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -i -E "UIBackgroundModes|advertising|bluetooth" docs/sources/raw/apple-05-corebluetooth.html | head -n 2
CoreBluetooth: UIBackgroundModes bluetooth-central and bluetooth-peripheral. Extended advertising scanning supported in iOS 13+.
```

## Verdict
`CONFIRMÉ`
