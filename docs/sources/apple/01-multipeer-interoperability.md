# Source Preuve : MultipeerConnectivity & Interopérabilité Android

- **Affirmation du document** : « MultipeerConnectivity équivalent direct de Nearby Connections » (spec §4.5)
- **URL Source** : https://developer.apple.com/documentation/multipeerconnectivity
- **Fichier brut** : `docs/sources/raw/apple-01-multipeer.html`
- **Date de consultation** : 2026-10-02

## Commande grep & sortie brute
```bash
$ grep -i -E "Bluetooth|Wi-Fi|devices" docs/sources/raw/apple-01-multipeer.html | head -n 2
MultipeerConnectivity supports Wi-Fi networks, peer-to-peer Wi-Fi, and Bluetooth between iOS and macOS devices.
```

## Verdict
`CONTREDIT (MultipeerConnectivity est strictement limité aux appareils de l'écosystème Apple et n'interopère pas avec Google Nearby Connections sur Android)`
