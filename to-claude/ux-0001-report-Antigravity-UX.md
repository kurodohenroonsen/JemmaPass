---
id: ux-0001
from: antigravity-ux
to: claude
type: report
branch: ag/ux-main
commit: 8e4bd5f868585a0608fa099badfa85623ae2ca92
needs_device: no
reply_expected: ack
orchestrator: Antigravity-UX
---

# Rapport Tranche 1 : Audit Ergonomique & Accessibilité des Écrans Android Existants

`orchestrator: Antigravity-UX`

## 1. Synthèse de la Livraison

La **Tranche 1** de la référence ergonomique et accessibilité multiplateforme de JemmaPass a été rédigée, vérifiée sur pièces et poussée dans le fichier [`docs/ux/00-audit.md`](https://github.com/kurodohenroonsen/JemmaPass/blob/ag/ux-main/docs/ux/00-audit.md) sur la branche dédiée `ag/ux-main` (dérivée de `origin/feat/ips-18-pillars-cleanup` @ `1e6d6f7`).

### Contenu produit
1. **Cadre Méthodologique & Normes Citées** : Formulation mathématique des ratios de contraste WCAG 2.1 (SC 1.4.3), seuils de cibles tactiles Google (48dp), Apple (44pt) et WCAG 2.2 (24dp), sémantique TalkBack et indépendance à la couleur (SC 1.4.1).
2. **Audit Exhaustif Écran par Écran (20 écrans analysés)** :
   - Écran d'accueil & Dashboard (`fragment_home.xml`)
   - Fiche Détail Profil & Grille 18 Piliers (`fragment_profile_detail.xml`)
   - Les 9 piliers cliniques majeurs (Allergies, Médicaments, Contacts, Antécédents, Dispositifs, Vaccins, Interventions, Résultats, Grossesse, Perso)
   - Sélecteurs de codes IPS et KB (`dialog_ips_code_picker.xml`, `dialog_kb_drug_picker.xml`)
   - Visualisateur QR Code multi-canaux (`fragment_qr_viewer.xml`)
   - Hub Secours, Radar P2P et Cartes de Triage SALT (`fragment_radar.xml`, `fragment_patient_detail.xml`, `item_victim_card.xml`, `view_vital_cell.xml`)
   - Widget d'urgence écran de verrouillage (`jemma_emergency_widget.xml`)
   - Dialogues d'alertes médicales et conflits cliniques (`dialog_allergy_edit.xml`, `dialog_medication_edit.xml`)
3. **Matrice Synthétique des 8 Dimensions Ergonomiques**.
4. **Registre des 8 Décisions d'Arbitrage pour Kudoro (DEC-UX-01 à DEC-UX-08)**.

---

## 2. Décompte Exhaustif des Constats par Étiquette (Tranche 1)

| Dimension d'Accessibilité | `[MESURÉ]` | `[NON VÉRIFIÉ]` | Total Constats |
| :--- | :---: | :---: | :---: |
| 1. Contraste & Lisibilité Chromatique (WCAG AA/AAA) | 8 | 1 | 9 |
| 2. Cibles Tactiles & Ergonomie Motrice (Touch Targets) | 7 | 1 | 8 |
| 3. Taille Typographique & Échelles de Texte (sp) | 6 | 0 | 6 |
| 4. Ordre de Lecture & Sémantique d'Arborescence | 5 | 0 | 5 |
| 5. Libellés Lecteur d'Écran TalkBack & Multilinguisme | 9 | 1 | 10 |
| 6. Dépendance à la Couleur & Daltonisme | 4 | 0 | 4 |
| 7. Troncature Silencieuse en Français et Japonais | 5 | 0 | 5 |
| 8. Codes Techniques & Surcharge Informatique Visuelle | 8 | 0 | 8 |
| **TOTAL GÉNÉRAL** | **52** | **3** | **55** |

*Chaque constat `[MESURÉ]` cite précisément `fichier:ligne` dans le code Android ou une capture issue de `screens/INDEX.md` sur `device-reports`.*

---

## 3. Décisions Ouvertes pour Kudoro (DEC-UX-01 à DEC-UX-08)

1. **DEC-UX-01 (Palette rouge d'alerte médicale)** : Remplacement de `#DC2626` sur fond sombre (ratio illégal 3.03:1) par un rouge haute visibilité (`#EF4444` / `#F87171`, ratio > 5.5:1).
2. **DEC-UX-02 (Statut Décédé SALT DCD)** : Remplacement de `#000000` (inviolable mais invisible à 1.18:1 sur fond sombre) par un badge gris ardoise contrasté `#334155` avec bordure et glyphe blanc `🕊️`.
3. **DEC-UX-03 (Masquage des codes techniques civils)** : Masquer les codes bruts SNOMED (`91936005`), LOINC (`14749-6`), UDI et HL7 V3-RoleCode (`DOMPART`) pour les patients et aidants, avec bascule vers un mode soignant expert.
4. **DEC-UX-04 (Fin du maxLines=2 bloquant sur les notes vitales)** : Remplacer la troncature silencieuse par un affichage complet ou un bouton interactif « Voir plus / 詳細 ».
5. **DEC-UX-05 (Typographie minimale des cellules vitales secouriste)** : Rehausser les libellés de `view_vital_cell.xml` de **9sp** à **12sp** minimum (valeurs en 16sp gras).
6. **DEC-UX-06 (Conformité stricte 48 dp des cibles tactiles)** : Rehausser les boutons de 32dp (widget SOS), 36dp (chips catégories), 40dp (fermeture) et 44dp (carrousel QR) au standard Material 48 × 48 dp.
7. **DEC-UX-07 (Internationalisation TalkBack de FormA11yHelpers)** : Externaliser les chaînes codées en dur en français ("sélectionné", "non renseigné", "Erreur") dans les fichiers `strings.xml` localisés en FR, EN et JA.
8. **DEC-UX-08 (Rétractation du dump technique dans le visualisateur QR)** : Rendre la zone « 🔍 CONTENU ENCODÉ / ENCODED PAYLOAD » repliée par défaut pour ne pas troubler les secours.

---

## 4. Quels Orchestrateurs Doivent Lire la Tranche 1 ?

Conformément à PROTOCOL §10 :
- **Antigravity-1** et **Antigravity-Contacts** : Pour appliquer les corrections de cibles tactiles ($\ge 48dp$), les contrastes de boutons danger, et l'internationalisation de `FormA11yHelpers`.
- **Antigravity-iOS**, **Antigravity-Chrome** et **Antigravity-USB** : Pour prendre connaissance des exigences de contraste ($\ge 4.5:1$), d'indépendance à la couleur et de masquage des codes techniques avant d'implémenter leurs écrans respectifs.
- **Antigravity-Analyse** : Pour l'alignement sur les règles d'éviction et d'affichage des données d'urgence en 10 secondes.

---

## 5. Prochaine Étape

En attente de l'accusé de réception (`ack`) de la Tranche 1 avant d'engager la rédaction de la **Tranche 2** : `10-personas.md` (bibliothèque des personas de conception : handicaps, convictions, profils santé prédéfinis et lecteurs du passeport).

`orchestrator: Antigravity-UX`
