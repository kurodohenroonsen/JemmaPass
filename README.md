<div align="center">

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #1 — HERO BANNER
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a wide banner showing Jemma (the AI mascot) + the tagline,
  same vibe as the YouTube thumbnail. Recommended size 1280×400 (will scale).
  Suggested filename: docs/images/hero-banner.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![JemmaPass hero banner](docs/images/hero-banner.png) -->

# 🐢 JemmaPass

### Universal IPS Health Passport — Offline-First, On-Device, Cross-Border

**One standard. Every country. Every emergency.**

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Gemma 4](https://img.shields.io/badge/Gemma-4_E4B-4285F4?logo=google&logoColor=white)](https://ai.google.dev/gemma)
[![LiteRT-LM](https://img.shields.io/badge/LiteRT--LM-0.11.x-FF6F00)](https://github.com/google-ai-edge/LiteRT-LM)
[![FHIR R4](https://img.shields.io/badge/FHIR-R4_IPS-E32C2E)](https://www.hl7.org/fhir/R4/index.html)
[![100% Offline](https://img.shields.io/badge/Cloud-Zero_calls-1D9E75)]()
[![25 Languages](https://img.shields.io/badge/UI-25_languages-9B59B6)]()

**🎬 [Watch the demo](https://youtu.be/ADZvQUEWXTk)** · **🌐 [jemmapass.net](https://jemmapass.net)** · **📱 [Download APK](https://jemmapass.net/APK/JemmaPass.apk)** · **🏆 [Hackathon writeup](https://jemmapass.net/hackathon.html)**

</div>

---

## What is JemmaPass?

JemmaPass turns any **Pixel 9** into a **100% offline, multilingual, standards-compliant Patient Summary device**. It uses **Gemma 4 E4B via LiteRT-LM** with **16 typed `@Tool` functions** to populate an **HL7 FHIR R4 International Patient Summary (IPS)** from voice, camera, or paper input — then transmits it via **multilingual QR codes** or **Bluetooth-LE mesh broadcast** (Google Nearby Connections P2P_CLUSTER).

**Zero cloud calls. Zero telemetry. Zero advertising.** Auditable in `git diff`.

> **Built solo in one month**, on personal time, by a Belgian developer with no formal medical training. Submitted to the [Gemma 4 Good Hackathon](https://www.kaggle.com/competitions/gemma-4-good-hackathon) (2026).

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #2 — APP SCREENSHOT TRIPTYCH
  ─────────────────────────────────────────────────────────────────────────
  Suggested: 3 phone screenshots side by side, showing:
    (1) Profile detail screen with the 4 pillars (Kurodo or Haru)
    (2) The red alert screen (Augmentin × penicillin)
    (3) The Triple-Layer QR export screen
  Wrap them in a single horizontal image OR use a markdown table for layout.
  Suggested filename: docs/images/triptych-overview.png
═══════════════════════════════════════════════════════════════════════════
-->

<!--
<p align="center">
  <img src="docs/images/triptych-overview.png" alt="JemmaPass overview" width="900"/>
</p>
-->

---

## 🎭 The cast

JemmaPass is built around a four-character story:

| Character       | Role                     | Why they matter                                                                                                                        |
| --------------- | ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| ✨**Jemma**     | The on-device AI mascot  | The small luminous turtle-spirit incarnation of Gemma 4. She lives in the phone, never in the cloud.                                   |
| 🎒**Kamekichi** | The rescuer turtle       | First-aid volunteer, not a doctor. Backpack, red-cross marking. He scans victims and learns clinical pharmacology one alert at a time. |
| 🚶‍♂️**Kurodo**    | The Belgian pilgrim      | Severe**penicillin** + fish allergies. Walks the Shikoku 88-temple route. Speaks no Japanese.                                          |
| 👵**Haru**      | The Japanese grandmother | 80 years old. Takes**Edoxaban** (anticoagulant). Heart failure. Her grandson visits weekly.                                            |

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #3 — THE 4 CHARACTERS
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a 4-panel grid showing Jemma, Kamekichi, Kurodo, Haru drawings
  (the same characters from the demo video). Each labeled with their role.
  Suggested filename: docs/images/cast.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![The JemmaPass cast](docs/images/cast.png) -->

### The demo scenarios

**🚨 Scenario A — Kurodo gets Augmentin (alerte rouge)**

A rescuer finds Kurodo unconscious after a fall on the pilgrim path. He wants to administer **Augmentin** (amoxicillin + clavulanic acid) for a suspected infection.

1. Kamekichi scans the blister pack with JemmaPass camera
2. Gemma 4 reads the label → resolves to ATC **J01CR02** (penicillin combination)
3. JemmaPass cross-checks against Kurodo's paper Pocket Pass profile
4. **Hit on the SNOMED penicillin-class allergen** → screen turns red in **~200 ms**
5. The plain-language explanation reads: _"Augmentin contains amoxicillin, a beta-lactam in the penicillin family. Kurodo's documented penicillin allergy makes cross-reactivity highly likely."_
6. Kamekichi just learned clinical pharmacology

**✅ Scenario B — Haru gets Aspirin (verdict OK)**

A foreign volunteer wants to give Haru aspirin for a headache. She is on Edoxaban (anticoagulant).

1. Volunteer scans Haru's printed Pocket Pass paper QR
2. JemmaPass decompresses the `_j2` payload offline (1.8 KB)
3. Cross-check against **DDInter 2.0**: Edoxaban × Aspirin at standard dose → **bleed risk minor, not contraindicated**
4. Screen stays green, with a clinical note: _"Monitor for bruising; do not exceed 75–100 mg aspirin daily without clinician supervision."_
5. Same engine, opposite verdict. This is what FHIR-grounded reasoning looks like.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #4 — DEMO SCENARIOS COMIC STRIP
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a 4-panel comic showing:
    (1) Kurodo's blister scan → red alert screen
    (2) Haru's QR scan → green verdict screen
  Or a side-by-side comparison of the two outputs.
  Could also be an animated GIF showing the scan-to-alert flow.
  Suggested filename: docs/images/demo-scenarios.gif (or .png)
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Demo scenarios](docs/images/demo-scenarios.png) -->

---

## ⚡ Why this matters

Every year, millions of travelers, refugees, foreign residents, and disaster victims carry their medical history in a language no nearby rescuer understands. The lock-screen "Medical ID" feature on most phones is buried, locale-locked, and useless to first responders.

JemmaPass is built for three concrete moments:

- **Disaster zones** where networks collapse for days (e.g. **Noto Peninsula, January 2024**, where every second of offline interoperability mattered)
- **Cross-border travel** (the Shikoku pilgrimage; a Belgian patient in Tokyo; a Bengali volunteer in Aomori)
- **Elder care across language barriers** (a Japanese grandmother whose prescription says `ワルファリン 1mg/日`, invisible to a foreign caregiver)

---

## 🏗️ Architecture

A four-layer pipeline, **everything on-device**, no cloud calls anywhere in the runtime.

```
┌────────────────────────────────────────────────────────────────────────┐
│  PERCEPTION  · multimodal intake (CameraX · ML Kit · AudioRecord)      │
│  Voice (PCM 16 kHz → RIFF/WAVE)  ·  Camera/OCR (ML Kit Latin + JA)     │
│  ZXing barcode scan  ·  Manual form (XML + ViewBinding + Hilt)         │
└────────────────────────────┬───────────────────────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│  REASONING  · Gemma 4 E4B via LiteRT-LM 0.11.x  (~3.2 GB Q4_K_M)       │
│  16 typed @Tool functions (KB lookup · DDI · focus profile · alerts)   │
│  Strict structured output → FHIR R4 IPS resources                      │
│  🎓 Vulgarise pipeline → plain-language patient education              │
└────────────────────────────┬───────────────────────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│  KNOWLEDGE BASE  · knowledge_full.db (2.2 GB SQLite, fully on-device)  │
│  • 1.4M concepts (UMLS + SNOMED CT IPS Free Set)                       │
│  • 260,100 drug-drug interactions (DDInter 2.0)                        │
│  • 8,121 drug-disease interactions  ·  857 drug-food interactions      │
│  • 2,289 curated drugs  ·  ATC/DDD 2026 (WHO Oslo)  ·  RxNorm bridges  │
│  • Bundled libsqliteX.so with FTS5 (unicode61 + trigram for CJK)       │
└────────────────────────────┬───────────────────────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│  TRANSFER  · offline-first, multi-channel                              │
│  Triple-Layer QR (compact · text · FHIR slideshow)                     │
│  Nearby Connections (BLE + Wi-Fi Direct, P2P_CLUSTER, 1.5s rotation)   │
│  Pocket Pass (printed paper QR with redundant payload)                 │
│  Lock-screen widget (foreground SOS service, double-tap, no unlock)    │
└────────────────────────────────────────────────────────────────────────┘
```

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #5 — PRA ARCHITECTURE DIAGRAM (SVG)
  ─────────────────────────────────────────────────────────────────────────
  Use the same SVG you already uploaded for the Kaggle writeup, or upload
  a copy as docs/images/pra-architecture.svg.
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![PRA architecture](docs/images/pra-architecture.svg) -->

### The package structure (audit-friendly)

```
be.heyman.android.jemmapassdemo/
├── JemmaApplication.kt              · @HiltAndroidApp · KB & RadarController bootstrap
├── MainActivity.kt                  · single-activity host of nav_graph.xml
│
├── ai/                              · all Gemma 4 + ML Kit pipelines
│   ├── JemmaTools.kt                · the 16 typed @Tool surfaces
│   ├── audio/                       · AudioRecord PCM 16 kHz → RIFF/WAVE wrapper
│   ├── gemma/                       · GemmaSession · LiteRT-LM ToolProvider
│   ├── medscan/                     · MedScan controller (Augmentin scenario)
│   ├── assistant/                   · 🎓 vulgarisation engine (see dedicated section)
│   │   ├── VulgariseHelper.kt       ·   prompt templates + AlertVulgariseContext
│   │   ├── VulgariseRepository.kt   ·   persistent cache (Moshi JSON)
│   │   ├── AssistantMedicationsExtractor.kt  ·  OCR → INN candidates
│   │   ├── ExtractedMedication.kt
│   │   └── L27ExtractPrompt.kt
│   ├── livescan/                    · live camera AI overlay
│   ├── ocr/                         · ML Kit Japanese + Latin OCR
│   ├── stt/                         · Speech-to-Text dictation
│   ├── tts/                         · Text-to-Speech SOS narration
│   └── patient/                     · patient-side capture flow
│
├── kb/                              · clinical knowledge base
│   ├── KnowledgeBaseManager.kt      · single SQLite owner with FTS5
│   ├── KnowledgeBaseService.kt      · 6 service extension files
│   ├── KbCrossCheck.kt              · DDI + allergen + condition engine
│   └── JemmaProfileHydrator.kt      · _j 1.2 short → display-resolved
│
├── qr/                              · offline QR transfer
│   ├── JemmaPayloadCodec.kt         · _j2 deflate-raw RFC 1951 codec
│   ├── JemmaFhirBundleBuilder.kt    · FHIR R4 IPS bundle (dev.ohs.fhir SDK)
│   ├── JemmaTextPayloadBuilder.kt   · 25-language plain-text fallback
│   ├── JemmaQrFrameSplitter.kt      · multi-frame slideshow for big bundles
│   ├── JemmaQrBitmapEncoder.kt      · ZXing core 3.5.3 wrapper
│   ├── JemmaPdfExporter.kt          · printed Pocket Pass PDF
│   ├── JemmaPersonasSeeder.kt       · Kurodo + Haru demo seed
│   └── JemmaTranslations.kt         · QR text channel i18n
│
├── mesh/                            · Plan A/B mesh runtime
│   ├── codec/                       · 7 chunk types (V/VR/S/SR/E/F/Fingerprint)
│   │   ├── CapFlags.kt              · capability flags (rescuer beacon)
│   │   ├── ChunkType.kt             · wire-format discriminator
│   │   ├── EventChunk.kt            · SALT triage events (WAIT/EVAL/STAB/HELP/EVAC/DCD)
│   │   ├── FingerprintChunk.kt      · profile SHA-256 + ts + version
│   │   ├── MeshByteSafety.kt        · 131-BYTE limit (Nearby endpointName)
│   │   ├── RelayedChunkCodec.kt     · VR/SR with TTL
│   │   └── TtlPolicy.kt             · per-chunk-type TTL defaults
│   ├── relay/                       · multi-hop forwarding
│   └── transport/                   · BLE + Wi-Fi Direct abstraction
│
├── sos/                             · lock-screen SOS + Nearby broadcast
│   ├── JemmaSosService.kt           · foreground service, lock-screen icon
│   ├── JemmaNearbySosService.kt     · P2P_CLUSTER + 1.5s chunk rotation
│   ├── JemmaSosBleAdvertiser.kt     · BLE scan record advertiser
│   ├── JemmaSosBleScanner.kt        · BLE scanner (rescuer side)
│   ├── JemmaEmergencyWidget.kt      · lock-screen widget (double-tap SOS)
│   ├── JemmaRescuerBroadcaster.kt   · rescuer beacon emitter
│   ├── JemmaTaskShutdownService.kt  · onTaskRemoved safety net
│   └── JemmaRadarOverlayState.kt    · radar UI state (anti-zombie invariants)
│
├── radar/                           · RadarController (Plan A bridge facade)
├── triage/                          · SALT codes + StatusResolver
├── pillars/                         · IPS data model (allergy/medication/condition)
├── profiles/                        · ProfilesRepository (DataStore)
├── compass/                         · device sensors fusion
├── downloads/                       · model + KB download workers
│
└── ui/                              · ViewBinding + Compose hybrid
    ├── home/  splash/  permissions/  settings/  stubs/
    ├── profiles/   profiles/detail/   profiles/import_qr/
    ├── profile/   profile/edit/   profile/allergies/
    │   profile/medications/   profile/contacts/   profile/perso/
    ├── radar/                       · live SOS radar (rescuer mode)
    ├── assistant/                   · 🎓 Gemma chat + vulgarise UI
    └── export/                      · 3-tab QR exporter UI
```

**356 Kotlin files. 82 XML layouts. 25 UI locales. ~76,000 lines of code.**

---

## 🎓 The vulgarisation engine — patient education built-in

> **JemmaPass is not just a cross-check tool — it is an education platform that fits in your pocket.**

Every drug alert, every allergy match, every drug-disease contraindication can be **explained in plain language** by Gemma 4, in the **user's device language**, automatically. This turns a clinical-grade safety check into a teaching moment for the patient, the family, or the first-aid volunteer who scanned the medication.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #6 — VULGARISATION IN ACTION (GIF)
  ─────────────────────────────────────────────────────────────────────────
  Suggested: an animated GIF showing:
    1. Red alert screen appearing after Augmentin scan
    2. User taps "Explain to me / Vulgariser"
    3. Plain-language explanation streams in word-by-word
    4. Final reminder to consult a doctor appears
  Could also be a static screenshot of the final vulgarized text.
  Suggested filename: docs/images/vulgarise-demo.gif
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Vulgarisation demo](docs/images/vulgarise-demo.gif) -->

### Two flavors of vulgarisation

#### 🚨 Alert vulgarisation — for cross-check results

When a cross-check produces a clinical alert (DDI / allergy / drug-disease), the user can tap **"Explain to me"** and Gemma 4 generates a tailored, multilingual, plain-language explanation. The full prompt template lives in [`VulgariseHelper.kt`](app/src/main/java/be/heyman/android/jemmapassdemo/ai/assistant/VulgariseHelper.kt) and follows a strict structure:

1. **What each medicine does** in plain words
2. **Why combining them is a concern** (the pharmacological mechanism)
3. **What could happen in the body**, described simply
4. **One warm sentence** reminding the patient to talk to their doctor or pharmacist

The `AlertVulgariseContext` captures the minimal data needed:

```kotlin
data class AlertVulgariseContext(
    val kind: String,             // "allergy" | "drug_drug_interaction" | "drug_disease"
    val subjects: List<String>,   // typically 2 drugs in collision
    val severity: String,         // HIGH / MODERATE / MAJOR / MINOR
    val mechanism: String?,       // PK / PD pharmacological mechanism (DDInter 2.0)
    val description: String?,     // clinical description from KB
    val cacheKey: String,         // e.g. "DDI|R06AX29|N06AB06" — for persistence
    val alternative: String?,     // suggested safer class/drug (e.g. "B01A - ANTITHROMBOTICS")
)
```

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #7 — ALERT VULGARISATION SCREENSHOT
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a phone screenshot showing the alert dialog with:
    - The red severity badge at top
    - The two drug names
    - The streaming vulgarized explanation
    - A small "translated by Gemma 4 · on-device" footer
  Suggested filename: docs/images/vulgarise-alert.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Alert vulgarisation screenshot](docs/images/vulgarise-alert.png) -->

#### 💊 Medication vulgarisation — for any drug in the profile

Beyond alerts, **any medication in any profile can be vulgarized**. Tap the 🎓 icon next to a med and Gemma 4 explains:

- What this medicine is used for
- How it generally helps the body (with simple analogies)
- Specific warnings to keep in mind
- A reminder to consult a doctor/pharmacist

The `MedicationVulgariseContext` includes the WHO **AWaRe category** (Access / Watch / Reserve) and **EML status** (Essential Medicines List), so the educational text can include responsible antibiotic stewardship context.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #8 — MEDICATION VULGARISATION SCREENSHOT
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a screenshot showing a medication card with the 🎓 icon and
  the vulgarized explanation panel expanded below it.
  Suggested filename: docs/images/vulgarise-medication.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Medication vulgarisation screenshot](docs/images/vulgarise-medication.png) -->

### Multilingual by construction (25 languages)

The vulgarisation prompts force Gemma 4 to **answer in the user's device language**, regardless of the clinical KB language (which is English). The system prompt is **aggressive** about this:

```
⚠️ CRITICAL LANGUAGE RULE — READ FIRST ⚠️
YOU MUST RESPOND ENTIRELY IN $deviceLang (BCP-47 tag: $langTag).
NOT A SINGLE WORD OF ENGLISH (unless $deviceLang IS ENGLISH).
DO NOT START YOUR ANSWER WITH "Imagine your body…" OR ANY OTHER ENGLISH PHRASE.
EVERY SENTENCE MUST BE IN $deviceLang.
```

This is repeated **three times** in each prompt (header, body, footer). Gemma 4 has a documented tendency to default to English mid-output for technical content, so we hammer the constraint. Translation includes the **analogies and metaphors** themselves — no English "traffic jam" sneaking into a Japanese explanation.

A Belgian volunteer scanning a Bengali patient's profile sees the alert in **Dutch or French**; the same alert on the Bengali patient's phone reads in **বাংলা (Bangla)**. Same data, same engine, native experience for each user.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #9 — VULGARISATION IN 4 LANGUAGES (SIDE BY SIDE)
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a 2x2 grid showing the SAME alert (Augmentin × penicillin)
  rendered in FR / JA / ZH / AR. Proves the multilingual claim visually.
  Suggested filename: docs/images/vulgarise-4-languages.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Vulgarisation in 4 languages](docs/images/vulgarise-4-languages.png) -->

### Persistent cache, zero-network re-display

Every vulgarisation is saved to a local Moshi JSON cache ([`VulgariseRepository.kt`](app/src/main/java/be/heyman/android/jemmapassdemo/ai/assistant/VulgariseRepository.kt)) keyed by `(cacheKey, lang)`:

```kotlin
data class GlobalVulgarisation(
    val key: String,    // e.g. "DDI|R06AX29|N06AB06" or "drug_J01CR02"
    val lang: String,   // BCP-47 tag (fr, ja, zh, ...)
    val text: String,   // the cached explanation
    val ts: Long,       // creation timestamp
)
```

This means:

- First time a Kamekichi sees an Augmentin × penicillin alert → ~5 s Gemma inference → cached
- Every subsequent rescuer in the mesh reading the same alert → **instant display** (no model load, no inference, no network)
- The cache survives app restarts (persisted in `filesDir/vulgarise_cache.json`)

### Streaming UX with ANR protection

Gemma 4 outputs ~20-30 tokens/second during inference. Calling `TextView.append()` for every token triggers a full layout pass of the parent ScrollView, which on Pixel 9 starts dropping frames at ~15 tokens. The [`ThrottledTextAppender`](app/src/main/java/be/heyman/android/jemmapassdemo/ai/assistant/VulgariseHelper.kt) batches appends with a 100 ms minimum interval, preserving fluid scrolling even on Samsung devices with lower frame budgets.

### Why this matters for the hackathon

**The Impact Track is about Digital Equity.** Most clinical decision support tools assume the user is a doctor or a pharmacist. JemmaPass assumes the user might be:

- A **grandmother** who wants to understand why her new pill bottle has a warning sticker
- A **Doctor Without Borders volunteer** in a refugee camp with no medical training but a working phone
- A **family caregiver** who needs to know why grandfather can't take ibuprofen with his Edoxaban
- A **Shikoku pilgrim** who picks up a foreign pharmacy box and wonders if it's safe with his beta-blocker

For all of these users, the cross-check engine alone is not enough. They need the **why**. JemmaPass gives them the why, in their language, in 5 seconds, offline.

---

## 🧬 The 16 typed `@Tool` functions

JemmaPass exposes 16 typed functions to Gemma 4 via LiteRT-LM's `ToolProvider` machinery, defined in [`JemmaTools.kt`](app/src/main/java/be/heyman/android/jemmapassdemo/ai/JemmaTools.kt). They are organized in five families:

### 🔍 KB lookup (4)

| Tool             | Description                                                                                                                                              |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `resolveDrug`    | Drug name (any language, brand, INN, RxNorm, or ATC) → KB entry with ATC + RxNorm + canonical/localized display.**Always call first** before `checkDdi`. |
| `resolveAllergy` | Allergen common name → SNOMED-CT code, category, localized display.                                                                                      |
| `resolveByCode`  | Known terminology code (SNOMED, RxNorm CUI, UMLS) → KB entry.                                                                                            |
| `searchCodes`    | Fuzzy FTS5 search across drugs, allergens, conditions. Returns up to 5 ranked matches.                                                                   |

### 💊 Interactions (3)

| Tool              | Description                                                                                                                                        |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `checkDdi`        | Drug-drug interaction by name (resolves each to ATC, queries `v_ddi_emergency`). Mandatory call before answering any 2+ drug combination question. |
| `checkDdiByAtc`   | Fast-path DDI lookup with both ATC codes already known.                                                                                            |
| `getAtcAncestors` | Walk ATC class hierarchy (debug + class-level allergy matching).                                                                                   |

### 👤 Focus profile (4)

The "focus profile" is the patient currently under review. Set by the UI before each Gemma session via `jemmaTools.bind(profile)`.

| Tool                         | Description                                                            |
| ---------------------------- | ---------------------------------------------------------------------- |
| `getFocusProfileSummary`     | Demographics + counts per clinical pillar.                             |
| `getFocusProfileAllergies`   | Localized allergy list with SNOMED/RxNorm codes ready for cross-check. |
| `getFocusProfileMedications` | Localized medication list with ATC + dose + timing + route.            |
| `getFocusProfileConditions`  | Localized condition list for drug-disease contraindication checks.     |

### 🎯 Cross-check (2) — _the killers_

| Tool                              | Description                                                                                                                                                                                      |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `checkOneDrugAgainstFocusProfile` | **MASTER cross-check**: candidate drug name → collision report against allergies + meds + conditions. The single most useful tool for clinical safety — this is what catches Augmentin × Kurodo. |
| `checkOneAtcAgainstFocusProfile`  | Same as above, fast-path with ATC already resolved.                                                                                                                                              |

### 🖥️ UI bridge (2) — side-effecting

| Tool              | Description                                                                   |
| ----------------- | ----------------------------------------------------------------------------- |
| `triggerRedAlert` | Push a `JemmaToolEvent.RedAlert` — turns the screen red, plays the SOS chime. |
| `triggerToast`    | Push a `JemmaToolEvent.Toast` — non-blocking inline notification.             |

### ⏰ Utility (1)

| Tool                 | Description                           |
| -------------------- | ------------------------------------- |
| `getCurrentDateTime` | ISO 8601 local time (timezone-aware). |

**Architecture note**: LiteRT-LM invokes `@Tool` methods synchronously from its native thread. Our KB primitives are `suspend fun` (Dispatchers.IO). The shim is `runBlocking(Dispatchers.IO) { … }` — blocks the LiteRT-LM worker for ≤ 50 ms per SQL hit (median). All tools return `Map<String, Any>` with the convention `{ ok: Boolean, reason: String?, lang: String, …domain }`.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #10 — TOOL CALL FLOW
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a sequence diagram showing the Augmentin scan flow:
    User scans blister →
      Gemma 4 calls resolveDrug("Augmentin") →
      Gemma 4 calls checkOneDrugAgainstFocusProfile(Kurodo) →
      Gemma 4 calls triggerRedAlert() →
      VulgariseHelper renders plain-language explanation
  Suggested filename: docs/images/tool-call-flow.svg (or .png)
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Tool call flow](docs/images/tool-call-flow.svg) -->

---

## 🧠 Clinical knowledge base (`knowledge_full.db`)

Built by the **Clinical Forge 2.0-omnis** pipeline (2026-04-30). Single SQLite file, bundled in APK assets, unpacked to private storage on first launch. **Total size: 2.2 GB on disk.**

### Schema (audited — 37 tables, 2 views)

#### Unified terminology

- **`terminology_codes`** — ~1.4M rows, the universal concept table
  - PK `code`, with `atc_code`, `rxnorm_cui`, `snomed_code`, `ips_validated` flags
  - `system ∈ {urn:umls (1.4M), http://snomed.info/sct (19,697)}`
  - `category ∈ {Medication, Condition, Procedure, Device, Chemical_Allergen, Protein_Allergen, Mineral, Food, Vitamin_DFI, NULL}`
  - `ips_validated=1` → **12,899** curated IPS rows

#### FTS5 indices (pre-built — no lazy creation)

- **`terminology_latin`** — `fts5(code, lang, display, tokenize='unicode61 remove_diacritics 2')`
- **`terminology_cjk`** — `fts5(code, lang, display, tokenize='trigram case_sensitive 0')`

#### Drug interactions (DDInter 2.0 — Central South University, CC BY-NC-SA 4.0)

- **`ddinter_drugs`** — 2,289 curated drugs with primary ATC, ATC codes, formula, weight, CAS, SMILES, InChI
- **`ddi_facts`** — 260,100 drug-drug interaction facts (Major 52K + Moderate 195K + Minor 12K)
- **`ddi_atc_pairs`** — ~600,000 ATC pair rows pointing to the 260K facts
- **Views**: `v_ddi_emergency` (Major + Moderate pre-filtered), `v_interactions_drug` (full join)

#### Complementary

- **`interactions_food`** — 857 rows (drug-food interactions)
- **`drug_disease_interactions`** — 8,121 rows (contraindications)

### Why a bundled SQLite

Android's system `libsqlite.so` does **not** ship FTS5 on most OEM ROMs (Pixel 9 included). The `codes_fts` virtual tables require `SQLITE_ENABLE_FTS5`. We bundle [`requery/sqlite-android:3.49.0`](https://github.com/requery/sqlite-android) which provides `libsqliteX.so` compiled with `FTS5 + JSON1 + ICU` (~3 MB per ABI). Cursor API is drop-in identical to the system SQLite.

### Lookup latency

- **~50 ms median** for `checkOneDrugAgainstFocusProfile` on a Pixel 9
- **~200 ms P99** for full multilingual fuzzy search
- All measurements logged with the `JEMMA-KB` tag

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #11 — KB STATISTICS INFOGRAPHIC
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a clean infographic showing the KB numbers visually:
    - 1.4M concepts · 260K DDI · 2,289 drugs · 8,121 drug-disease · etc.
  Could be a chart, a "what's inside" diagram, or stat tiles.
  Suggested filename: docs/images/kb-stats.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Knowledge base statistics](docs/images/kb-stats.png) -->

---

## 📡 Triple-Layer QR — the transfer protocol

QR codes have a hard physical limit (~2,953 bytes for QR version 40 alphanumeric). A full FHIR R4 IPS bundle for a complex patient is ~14 KB. **No single QR holds it.** Our solution:

### Channel 1 — `_j2` pruned binary

- **1.5–3 KB** payload, **single QR**
- Custom format `_j2:<base64-of-deflate-raw-of-pruned-json>`
- `Inflater(nowrap=true)` = deflate-raw RFC 1951 (no zlib wrapper)
- 6 chunk types (V / VR / S / SR / E / F)
- CapFlags byte (`|J=08` = default rescuer, adaptive duty 70/30)
- Hash canonicalization for deterministic comparison
- **Covers 95% of real-world IPS profiles**
- Works with any QR scanner on Earth

### Channel 2 — Multilingual text

- **25 languages**, single QR
- Plain-text translation of allergies + critical meds + contacts
- Readable by **any iPhone Camera**, no app needed — the fallback that saves lives
- Defined by the [`Lang` enum](app/src/main/java/be/heyman/android/jemmapassdemo/qr/JemmaTextPayloadBuilder.kt) in `JemmaTextPayloadBuilder.kt`

### Channel 3 — Full FHIR R4 multi-frame slideshow

- **3–8 QRs**, animated playback
- Bundle split via `JemmaQrFrameSplitter` with deterministic ordering
- Receiver reassembles via frame index
- Used for hospital intake / full audit trail
- Generated with the official [Kotlin FHIR SDK](https://github.com/openhealth-stack/fhir-kotlin) (`dev.ohs.fhir:fhir-model:1.0.0-beta03`)

### 🔬 Verify FHIR compliance yourself

Copy the Channel 3 output from JemmaPass ("Copy" button in the export hub) and paste it into the official HL7 IPS Viewer:

➡️ **https://www.ipsviewer.com/classic**

The bundle renders as a structured patient summary, proving end-to-end interoperability with any HL7-conformant EHR.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #12 — TRIPLE-LAYER QR DIAGRAM
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a 3-column layout showing the three QR types side by side:
    | Channel 1: compact (1.8 KB) | Channel 2: 25-lang text | Channel 3: FHIR slideshow |
  Could also be a screenshot of the in-app QR exporter UI with all 3 tabs.
  Suggested filename: docs/images/triple-layer-qr.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Triple-Layer QR](docs/images/triple-layer-qr.png) -->

---

## 🌐 Nearby Mesh — the anti-zombie story

JemmaPass uses **Google Nearby Connections** with `Strategy.P2P_CLUSTER`:

```kotlin
SERVICE_ID = "be.heyman.android.jemmapassdemo.sos.SOS"
STRATEGY   = Strategy.P2P_CLUSTER
CHUNK_ROTATION_MS = 1500L  // full cycle = 6 chunks × 1.5s = 9s
```

Transport: **BLE advertising** (rescuer-side scan record) + **Wi-Fi Direct discovery** (heavy payload sessions).

### Mesh wire format

```
V|<sid4>|<chunkIdx>|<chunkTotal>|<payload>           victim DIRECT
VR|<sid4>|<chunkIdx>|<chunkTotal>|<ttl>|<payload>    victim RELAYED
S|<sid4>|<name>|<lang>|<lat>|<lon>|<capFlags>        rescuer beacon DIRECT
SR|<sid4>|<name>|<lang>|<lat>|<lon>|<ttl>            rescuer beacon RELAYED
E|<src>|<victim>|<status>|<rescuer>|<ts>|<ttl>|<seq> SALT triage event
F|<sid>|<sha256_hex_8>|<lastModifiedTs>|<chunkVer>   profile fingerprint
```

### Anti-zombie thread fixes (L44.16+)

A subtle GMS bug left BLE advertising as a zombie thread after stop, leading to false-positive radar pings on the next start. Fixes shipped:

- Reordered `fullStop()` sequence
- Added 300 ms teardown sleep before service stop
- Added `onTaskRemoved()` safety net in [`JemmaTaskShutdownService`](app/src/main/java/be/heyman/android/jemmapassdemo/sos/JemmaTaskShutdownService.kt)
- 5-guard GPS auto-refresh even when the device is immobile (L44.88)
- Stable across 6 reference builds — **production tag: L44.16.89b**

### UTF-8 byte safety

The 131-char limit advertised by Google Nearby is misleading — it's a **131-BYTE limit** at the BLE advertising payload (UTF-8 encoded). Japanese kana/kanji = 3 bytes/char, emoji = 4 bytes, accented Latin = 2 bytes. The previous codec checked `s.length` (UTF-16 code units in Kotlin), a latent bug for the Shikoku demo where Haru's profile would have overflowed. Now correctly enforced in [`MeshByteSafety.kt`](app/src/main/java/be/heyman/android/jemmapassdemo/mesh/codec/MeshByteSafety.kt).

### SALT triage events

JemmaPass implements the **SALT** mass casualty triage protocol (Sort, Assess, Lifesaving, Treatment):

| Code   | Meaning                                                           |
| ------ | ----------------------------------------------------------------- |
| `WAIT` | Walking wounded, low priority                                     |
| `EVAL` | Needs initial assessment                                          |
| `STAB` | Stable, observation                                               |
| `HELP` | Urgent, needs intervention                                        |
| `EVAC` | Critical, immediate evacuation                                    |
| `DCD`  | Deceased (propagates with TTL=3 to inform late-arriving rescuers) |

Each event chunk includes `source_sid` (creator), `victim_sid`, `rescuer_sid`, `ts_unix`, `ttl`, `seq` for idempotent multi-hop propagation.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #13 — RADAR SCREENSHOT
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a screenshot of the rescuer radar UI showing multiple SOS
  pings with different SALT status colors. Could also be a GIF showing
  the radar refreshing in real time as victims appear in the mesh.
  Suggested filename: docs/images/radar.png (or .gif)
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Rescuer radar](docs/images/radar.png) -->

---

## 🎵 The audio pipeline (a hard-won bug)

Gemma 4's audio input is documented as accepting raw audio, but the LiteRT-LM runtime decodes via **miniaudio** which requires a file-format header. Sending raw PCM produces:

```
Failed to initialize miniaudio decoder, error code: -10 (MA_INVALID_FILE)
```

**Solution**: wrap the PCM in a **44-byte RIFF/WAVE header** before passing to `Content.AudioBytes(...)`. Sample rate 16 kHz, mono, 16-bit, byteRate 32000. Reference: Google's own AI Edge Gallery `ChatMessage.kt:187` (`genByteArrayForWav`) shows this is the canonical approach.

This bug cost two days of debugging. It is now solved in [`JemmaAudioRecorder.kt`](app/src/main/java/be/heyman/android/jemmapassdemo/ai/audio/JemmaAudioRecorder.kt), works reliably on Pixel 9 with **~1.2 s latency** for a 5-second utterance.

---

## 🌍 Internationalization (the unfashionable engineering)

JemmaPass ships with translations spread across **seven surfaces**, all coherent end-to-end:

| Surface                         | Coverage                                                                                                       | Implementation                                                                  |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| **App UI**                      | **25 languages** — 7 fully translated (EN, FR, JA, ES, NL, DE, ZH-CN) + 18 with MVP demo strings + EN fallback | `res/values-*/*.xml` (26 locale folders including `values-night` for dark mode) |
| **QR text channel**             | **25 languages** — plain-text fallback readable by any iPhone Camera                                           | `JemmaTextPayloadBuilder.Lang` enum                                             |
| **Lock-screen SOS TTS**         | **25 languages** — spoken alert phrases for foreign rescuers                                                   | `JemmaTtsService` + Android TextToSpeech                                        |
| **🎓 Vulgarisation prompts**    | **25 languages** — Gemma 4 forced to answer in `Locale.getDefault().displayLanguage`                           | `VulgariseHelper.kt` prompt templates                                           |
| **IPS clinical narrative**      | **3 languages** (EN, FR, JA) — structured FHIR text fields                                                     | `assets/jemma/ips_translations.json`                                            |
| **Drug-name FTS5 index**        | **14 languages** — 13 Latin scripts + Japanese                                                                 | `terminology_latin` (unicode61) + `terminology_cjk` (trigram)                   |
| **YouTube demo subtitles**      | **25 hand-translated SRT tracks**                                                                              | Submission package                                                              |
| **YouTube title + description** | **24 translated entries**                                                                                      | Submission package                                                              |

This is unglamorous work. It is also the difference between a hackathon prototype and a tool that can actually serve a Bengali pilgrim in Shikoku or a Japanese grandmother visited by a Brazilian volunteer.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #14 — LANGUAGE COVERAGE MATRIX
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a 25-flag grid showing which languages are covered per surface.
  Could be a clean infographic or a table screenshot. Same look as the
  Banana prompt #4 in jemma_banana_prompts_v2.md.
  Suggested filename: docs/images/i18n-matrix.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![25-language matrix](docs/images/i18n-matrix.png) -->

---

## 🛠️ Tech stack (full audit)

### Core platform

- **Android 12+ (minSdk 31, targetSdk 35, compileSdk 35)**
- **Kotlin** 2.x with Compose + ViewBinding hybrid
- **Hilt** (Dagger) for dependency injection — `@HiltAndroidApp`, KSP-generated components
- **AndroidX**: lifecycle, activity-compose, navigation-fragment-ktx, work, datastore, security-crypto, webkit, splashscreen, exifinterface

### On-device AI

- **Gemma 4 E4B** (4-bit `Q4_K_M`, ~3.2 GB) via [LiteRT-LM 0.11.x](https://github.com/google-ai-edge/LiteRT-LM)
- **TensorFlow Lite** (`tflite`, `tflite-gpu`, `tflite-support`)
- **ML Kit** — barcode scanning 17.3.0 · Japanese text recognition 16.0.1 · document scanner 16.0.0-beta1 · GenAI Prompt API
- **CameraX** (core, camera2, lifecycle, view) for capture pipelines

### Clinical & FHIR

- **Kotlin FHIR SDK** (`dev.ohs.fhir:fhir-model:1.0.0-beta03`) — R4 model classes
- **kotlinx-datetime 0.7.1** for FhirDateTime / FhirInstant
- **bignum 0.3.10** for FHIR Decimal
- **Moshi** + KSP codegen for `_j 1.2` short-format profiles and vulgarisation cache

### Mesh & transport

- **Google Nearby Connections** (`play-services-nearby:19.3.0`) — P2P_CLUSTER strategy
- **play-services-location:21.3.0** — fused location with 100 m accuracy guard
- Native Android BLE advertising + Wi-Fi Direct discovery

### QR + storage

- **ZXing core 3.5.3** — QR encoding (`QRCodeWriter` → `BitMatrix` → `Bitmap`)
- **requery/sqlite-android 3.49.0** — bundled `libsqliteX.so` with FTS5 + JSON1 + ICU
- **DataStore + Proto** for profile persistence

### UI & UX

- **Material Components 1.12.0** (XML layouts + ViewBinding)
- **Compose Material 3** (for chat panels and gallery surfaces)
- **commonmark + richtext** for Markdown rendering in chat
- **OpenID AppAuth** for HuggingFace authentication (model downloads)

### Build & CI

- **Gradle 8.x** Kotlin DSL
- **AGP 8.x** with **KSP** (no kapt)
- **Protobuf javalite** for the model catalog wire format
- **Firebase** (analytics + messaging) — **disabled in JEMMA build**, no `google-services.json` published

---

## 📦 What's in this repo

```
JemmaPass/
├── README.md                        ← you are here
├── build.gradle.kts                 ← root build (plugin aliases)
├── settings.gradle.kts              ← module includes, JitPack for requery/sqlite-android
├── gradle.properties                ← Kotlin 2.x, JVM args, KSP flags
├── gradlew  gradlew.bat             ← Gradle wrapper
├── local.properties                 ← SDK path (gitignored on your machine)
│
├── gradle/
│   ├── wrapper/                     ← Gradle wrapper jar + props
│   └── libs.versions.toml           ← version catalog
│
└── app/
    ├── build.gradle.kts             ← app-level deps + Android config
    │                                  • namespace = be.heyman.android.jemmapassdemo
    │                                  • applicationId = be.heyman.android.jemmapassdemo
    │                                  • minSdk 31 / targetSdk 35 / compileSdk 35
    │                                  • versionName "1.0.14" · versionCode 31
    │
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml  ← 26 permissions (BLE, Nearby, location, FG service)
        │   ├── java/be/heyman/android/jemmapassdemo/   ← 356 Kotlin files
        │   ├── java/com/google/ai/edge/gallery/         ← Edge Gallery base preserved
        │   ├── res/
        │   │   ├── layout/          ← 82 XML layouts (ViewBinding)
        │   │   ├── navigation/      ← nav_graph.xml (30+ destinations)
        │   │   ├── values/          ← canonical EN strings
        │   │   ├── values-ar/  -bn/  -da/  -de/  -en/  -es/  -fi/  -fr/  -hi/
        │   │   │  -in/  -it/  -ja/  -ko/  -nl/  -no/  -pl/  -pt/  -ro/  -ru/
        │   │   │  -sv/  -th/  -tr/  -uk/  -vi/  -zh-rCN/      ← 25 UI locales
        │   │   ├── values-night/    ← dark theme overrides
        │   │   ├── drawable/  font/  menu/  anim/  color/
        │   │   └── mipmap-*/        ← launcher icons (hdpi → xxxhdpi)
        │   ├── assets/
        │   │   └── jemma/
        │   │       └── ips_translations.json  ← FHIR IPS narrative i18n
        │   └── proto/               ← model catalog protobuf
        │
        └── test/
            └── java/be/heyman/android/jemmapassdemo/
                ├── qr/              ← payload codec round-trip tests
                └── sos/             ← mesh codec unit tests
```

---

## 🚀 Getting started

### Prerequisites

- **Android Studio Iguana** (2024.1) or newer
- **JDK 11+** (the project compiles with `sourceCompatibility = VERSION_11`)
- **Android SDK 35**
- A **Pixel 9** or equivalent device with **≥ 10 GB RAM** for full Gemma 4 E4B inference (Pixel 9 Pro family). Devices with less RAM fall back to 1024-token output (Samsung G781B, S22).

### Build

```bash
git clone https://github.com/kurodohenroonsen/JemmaPass.git
cd JemmaPass

# Build a debug APK
./gradlew assembleDebug

# Build a release APK (uses debug signing config — replace for production)
./gradlew assembleRelease

# Install on a connected device
./gradlew installDebug
```

The first launch will prompt for:

1. **Permissions** (5 mandatory): Camera, Microphone, Foreground Service, Bluetooth, Location
2. **Gemma 4 E4B model download** (~3.2 GB) — via HuggingFace OAuth (AppAuth flow)
3. **Knowledge base download** (~2.2 GB) — single `knowledge_full.db` file

> **TIP**: Download both before going offline. After that, the app **never** contacts the network again. Confirmed by airplane-mode testing over 7 consecutive days.

### Run the demo personas

After install, you can seed the two demo personas (Kurodo + Haru) from **Settings → "Seed demo profiles"**. This populates the database with the exact FHIR IPS bundles featured in the demo video, including all the codes that trigger the Augmentin red alert and the Edoxaban × Aspirin green verdict.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #15 — FIRST-LAUNCH ONBOARDING SCREENSHOTS
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a horizontal strip of 4-5 screenshots showing onboarding:
    permissions → model download → KB download → seeded profiles → home
  Suggested filename: docs/images/onboarding.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Onboarding flow](docs/images/onboarding.png) -->

---

## 🧪 Testing FHIR output

JemmaPass produces standard HL7 FHIR R4 IPS bundles. Verify yourself:

1. Open JemmaPass → Profiles → tap Kurodo or Haru
2. Tap **📤 Export** → **🏥 FHIR** tab → **Copy** button
3. Visit [**https://www.ipsviewer.com/classic**](https://www.ipsviewer.com/classic)
4. Paste the JSON bundle
5. See the patient summary render as structured clinical content

This is the canonical HL7 IPS Viewer. If JemmaPass's output renders correctly there, it will work with any HL7-conformant EHR.

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #16 — IPS VIEWER PROOF
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a screenshot of ipsviewer.com showing Haru's profile rendered
  after pasting the JemmaPass FHIR JSON. This is the killer audit-proof.
  Suggested filename: docs/images/ipsviewer-haru.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![IPS Viewer rendering of JemmaPass output](docs/images/ipsviewer-haru.png) -->

---

## ⚠️ Disclaimers

### Not a medical device

JemmaPass is positioned as an **educational IPS transfer tool**, **not** a medical device. We deliberately avoid Class IIa SaMD classification under EU MDR / AI Act during the hackathon phase. **Regulatory honesty matters more than marketing.**

- The cross-check engine is **not a substitute** for professional medical advice or a licensed pharmacist
- Drug-drug interaction data comes from **DDInter 2.0** (academic dataset, CC BY-NC-SA 4.0) — accurate but not a substitute for clinical decision support certified for emergency use
- Allergy cross-reactivity logic uses **class-level ATC matching** (e.g. penicillin family J01C) which is conservative-correct but not exhaustive
- The vulgarisation outputs are AI-generated educational content. Patients should always consult a qualified clinician before acting on them.

### Built solo, in one month

JemmaPass is a proof of concept built by **one person in personal time over April–May 2026**. There are bugs. Edge cases fail. Not every feature in the codebase is polished to ship quality. We submit a **working demo**, not a perfect product. Every feature shown in the video is reproducible on the supplied APK.

### Hackathon scope

JemmaPass forks from Google's **AI Edge Gallery** (Apache-2.0). The base Gallery code remains intact in `com.google.ai.edge.gallery.*` packages. JemmaPass-specific code lives entirely in `be.heyman.android.jemmapassdemo.*`. The original Firebase/FCM telemetry has been **removed** for privacy.

---

## 📜 Licenses & attributions

### Code

- **JemmaPass** (this repository) — **Apache License 2.0** (matching the AI Edge Gallery base it forks from)
- **AI Edge Gallery** (preserved subset) — Apache-2.0 © Google LLC

### Clinical data

- **DDInter 2.0** — drug-drug interactions, **CC BY-NC-SA 4.0** © Central South University
  Compliance attribution: https://jemmapass.net/hackathon/ddinter-compliance.html
- **SNOMED CT IPS Free Set** — terminology, **CC BY 4.0** © SNOMED International
- **RxNorm** — terminology bridges, public domain (NLM/UMLS)
- **ATC/DDD Index 2026** — drug classification, **CC BY-NC-SA 3.0** © WHO Collaborating Centre Oslo

### Libraries (selection)

- **Gemma 4** — Apache-2.0 © Google DeepMind
- **LiteRT-LM** — Apache-2.0 © Google AI Edge
- **Kotlin FHIR SDK** (`dev.ohs.fhir`) — Apache-2.0 © Open Health Stack
- **ZXing core** — Apache-2.0 © ZXing authors
- **requery/sqlite-android** — Apache-2.0 (bundled SQLite is public domain)
- **AndroidX, Material Components, Hilt, Moshi** — Apache-2.0

Full OSS license list available in-app via **Settings → Open-source licenses** (powered by `play-services-oss-licenses`).

---

## 🙏 Standing on the shoulders of giants

JemmaPass would not exist without:

- **Google DeepMind / Gemma 4 team** — Apache-2.0 multimodal AI that genuinely runs on consumer devices
- **LiteRT-LM team at Google AI Edge** — the native function-calling runtime
- **AI Edge Gallery team** — the base Compose + Hilt + LlmChatModelHelper scaffolding
- **HL7 IPS Working Group** — FHIR R4 International Patient Summary standard
- **Open Health Stack community** — the Kotlin FHIR SDK
- **DDInter team at Central South University** — for openly publishing 260,100 drug-drug interactions
- **SNOMED International** — for the IPS Free Set with multilingual refsets
- **NLM / UMLS / RxNorm** — for the terminology bridges
- **WHO Collaborating Centre Oslo** — for the ATC/DDD Index 2026
- **The 88 Shikoku temples** — for 1,200 years of welcoming the foreign pilgrim 🚶‍♂️⛩️
- **The Noto Peninsula responders, January 2024** — for the lessons in resilience under impossible conditions
- **My wife, Misako** — for the patience and the kitchen table

---

## 🐢 The author

**Claude Heyman** — signed online as **Kurodo Henro Onsen**.

Belgian, ServiceNow developer at the Belgian Ministry of Finance (SPF Finances) by day. Husband to **Misako** (Japanese, Tokyo native). Once walked the **Shikoku 88-temple pilgrimage**. No formal medical training.

JemmaPass was built **solo, in personal time, between April and May 2026**, between a day job and caring for a 90-year-old mother-in-law in Aomori across the language barrier. Every line is auditable on GitHub.

A non-commercial fork for humanitarian field deployment (Doctors Without Borders, Red Cross, JICA) is on the roadmap.

If you have feedback, ideas, want to translate JemmaPass into your language, or wish to deploy it in a humanitarian context — please [open an issue](https://github.com/kurodohenroonsen/JemmaPass/issues).

---

<div align="center">

<!--
═══════════════════════════════════════════════════════════════════════════
  🖼️ IMAGE ZONE #17 — CLOSING SIGNATURE
  ─────────────────────────────────────────────────────────────────────────
  Suggested: a small footer image with Jemma waving + the Toppa breakthrough
  kanji 突破. Optional — could just be the emojis.
  Suggested filename: docs/images/signature.png
═══════════════════════════════════════════════════════════════════════════
-->

<!-- ![Kurodo signature](docs/images/signature.png) -->

**One standard. Every country. Every emergency.**

🐢 突破 · _Toppa_ · Breakthrough

— Kurodo Henro Onsen 🚶‍♂️⛩️♨️
_Couvin, Belgium · May 2026_

</div><div align="center">
