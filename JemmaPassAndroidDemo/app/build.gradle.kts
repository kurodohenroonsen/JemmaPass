/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  alias(libs.plugins.android.application)
  // Note: set apply to true to enable google-services (requires google-services.json).
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.hilt.application)
  alias(libs.plugins.oss.licenses)
  alias(libs.plugins.ksp)
}

android {
  // 🆕 JemmaAppDemo — namespace ASBL Aktina, code Google Edge Gallery préservé
  // intact dans le package com.google.ai.edge.gallery.* (cf. /java/com/google/ai/edge/gallery)
  namespace = "be.heyman.android.jemmapassdemo"
  compileSdk = 35

  defaultConfig {
    applicationId = "be.heyman.android.jemmapassdemo"
    minSdk = 31
    targetSdk = 35
    versionCode = 31
    versionName = "1.0.14"

    // Needed for HuggingFace auth workflows.
    // Use the scheme of the "Redirect URLs" in HuggingFace app.
    manifestPlaceholders["appAuthRedirectScheme"] =
        "REPLACE_WITH_YOUR_REDIRECT_SCHEME_IN_HUGGINGFACE_APP"
    manifestPlaceholders["applicationName"] = "com.google.ai.edge.gallery.GalleryApplication"
    manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    // JEMMA : View-system layouts utilisent ViewBinding (FragmentXxxBinding generated).
    viewBinding = true
  }

  // 🆕 L2 v2.5.1 — let pure JUnit (src/test) tests touch classes that
  // call android.util.Log without throwing "not mocked" RuntimeException.
  // The default-values mode returns 0 / null / false for any unmocked
  // android.* method, which is exactly what we want for codec round-trip
  // tests that don't need real Android behavior.
  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    // L44.90 : `-Xcontext-receivers` removed in Kotlin 2.3.0 (replaced by
    // `-Xcontext-parameters`). No `context(...)` declarations exist in this
    // codebase (verified by grep), so the flag is simply dropped — no code
    // migration required. If context parameters are added later, re-enable:
    //   freeCompilerArgs.add("-Xcontext-parameters")
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.compose.navigation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlin.reflect)
  implementation(libs.material.icon.extended)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.datastore)
  implementation(libs.com.google.code.gson)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.webkit)
  implementation(libs.litertlm)
  implementation(libs.commonmark)
  implementation(libs.richtext)
  implementation(libs.tflite)
  implementation(libs.tflite.gpu)
  implementation(libs.tflite.support)
  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.openid.appauth)
  implementation(libs.androidx.splashscreen)
  implementation(libs.protobuf.javalite)
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.play.services.oss.licenses)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.messaging)
  implementation(libs.androidx.exifinterface)
  implementation(libs.moshi.kotlin)
  ksp(libs.hilt.android.compiler)
  testImplementation(libs.junit)
  testImplementation("org.json:json:20240303")
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.hilt.android.testing)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
  ksp(libs.moshi.kotlin.codegen)
  implementation(libs.mlkit.genai.prompt)

  // 🏥 Lot 14.5c22 — SDK Kotlin FHIR (IPS Storage & Hospital Interop)
  // L44.90 : This lib is compiled with Kotlin 2.3.0 — that's WHY we upgraded.
  implementation("dev.ohs.fhir:fhir-model:1.0.0-beta03")
  implementation("com.ionspin.kotlin:bignum:0.3.10")
  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")


  // ─── JEMMA View-system stack ────────────────────────────────────────────────
  // Edge Gallery is full Compose ; JEMMA uses XML layouts via ViewBinding.
  // We add the View-system equivalents alongside (no conflict with Compose).
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.fragment:fragment-ktx:1.8.6")
  implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")
  implementation("androidx.navigation:navigation-ui-ktx:2.8.5")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
  implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")
  // ─── v2.3.0a — QR import (CameraX + ML Kit Barcode) ─────────────────────────
  // CameraX modules (camerax-core/camera2/lifecycle/view) sont déjà dans le
  // bloc principal ci-dessus. On ajoute uniquement le scanner barcode ML Kit.
  // ML Kit Barcode 17.3.0 inclut le détecteur QR_CODE local (~3 MB APK).
  implementation("com.google.mlkit:barcode-scanning:17.3.0")

  // ─── 🆕 Lot 14.1 (PHASE 14) — QR EXPORT (génération de QR codes) ────────────
  //
  // ML Kit Barcode au-dessus ne sait que SCAN/lire un QR. Pour le générer
  // côté export profile (`_j2:<base64>` du Chantier 1), on a besoin de
  // l'algorithme d'encodage QR.
  //
  // ZXing core 3.5.3 = la lib core en Java pur, ~250 KB, 0 dep transitive.
  // Pas d'UI scanner (on a déjà ML Kit pour ça) — uniquement `QRCodeWriter`
  // qui prend une String et retourne une `BitMatrix` qu'on transforme en
  // Bitmap N&B via `JemmaQrBitmapEncoder`.
  //
  // Pas via libs.versions.toml : on suit le pattern des autres ajouts
  // récents (sqlite-android, nearby, location) qui sont en string direct.
  implementation("com.google.zxing:core:3.5.3")

  // ─── v2.2.15 — Bundled SQLite with FTS5 ─────────────────────────────────────
  // Android's system libsqlite.so does NOT ship FTS5 on most OEM ROMs (Pixel 9
  // included). The Clinical Forge KB uses `codes_fts` virtual tables built with
  // FTS5 (cf. jemma_kb_indexer.js). Without this lib, queries against
  // `codes_fts` throw `no such module: fts5`.
  //
  // requery/sqlite-android bundles a `libsqliteX.so` compiled with
  // SQLITE_ENABLE_FTS5, SQLITE_ENABLE_JSON1, SQLITE_ENABLE_ICU. Adds ~3 MB
  // per ABI to the APK.
  //
  // Usage : replace `import android.database.sqlite.SQLiteDatabase` with
  // `import io.requery.android.database.sqlite.SQLiteDatabase` in code that
  // needs FTS5. The Cursor API is identical.
  implementation("com.github.requery:sqlite-android:3.49.0")

  // ─── 🆕 L1 v2.5.0 — JEMMA SOS / Mesh runtime (Plan A port) ──────────────────
  //
  // Google Nearby Connections — the BLE + WiFi-Direct mesh transport that
  // carries SOS chunks (V/VR/S/SR/E/F) between victims and rescuers in the
  // emergency mode. Strategy = P2P_CLUSTER, SERVICE_ID = "be.heyman.android.
  // jemmapassdemo.SOS". Used directly by JemmaNearbySosService and the
  // mesh/session/* gateway/responder.
  //
  // play-services-location — fused location provider for the rescuer-side
  // GPS guard (≤ 100 m accuracy) and victim-side GPS embedded in chunks.
  // Drives JemmaRadarOverlayState.lastRescuerLat/Lon/LocSetAtMs (the
  // critical invariant called out in the handoff).
  implementation("com.google.android.gms:play-services-nearby:19.3.0")
  implementation("com.google.android.gms:play-services-location:21.3.0")
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.26.1" }
  generateProtoTasks { all().forEach { it.plugins { create("java") { option("lite") } } } }
}
