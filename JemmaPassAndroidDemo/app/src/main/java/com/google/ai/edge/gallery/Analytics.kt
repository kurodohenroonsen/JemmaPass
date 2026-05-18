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

/*
 * ──────────────────────────────────────────────────────────────────────────
 * v2.2.12 — JemmaAppDemo : Firebase Analytics neutralisé.
 *
 * ASBL Aktina ne veut PAS envoyer de telemetry à Google. Le code original
 * utilisait `Firebase.analytics` qui throw IllegalStateException quand
 * FirebaseApp n'est pas initialisé (pas de google-services.json) — et
 * polluait les logs avec une stacktrace à chaque update de progress.
 *
 * Solution : le getter `firebaseAnalytics` retourne désormais `null` en
 * dur, sans jamais invoquer le SDK Firebase. Tous les call sites font
 * `firebaseAnalytics?.logEvent(...)` qui devient un no-op silencieux —
 * pas d'exception, pas de log, pas de network call.
 *
 * Les imports Firebase ne sont PAS supprimés du build pour éviter de
 * casser les autres fichiers Google Edge Gallery qui les référencent
 * (Utils.kt, ChatView.kt, etc.) ; ces appels seront aussi neutralisés
 * par le `null` du getter, sans modification.
 *
 * NB : le type de retour du getter doit rester `FirebaseAnalytics?` pour
 *      ne pas casser la signature attendue par les call sites.
 * ──────────────────────────────────────────────────────────────────────────
 */
package com.google.ai.edge.gallery

import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Neutralized in JemmaAppDemo v2.2.12. Always returns null. Every
 * `firebaseAnalytics?.logEvent(...)` becomes a no-op. No SDK access.
 *
 * The original Google implementation invoked `Firebase.analytics` inside
 * a `runCatching { }`, but the failure path still logged a warning and
 * polluted the logcat with a long stacktrace at every progress tick of
 * the DownloadWorker (3 call sites in DownloadRepository.kt). Returning
 * `null` directly skips the SDK invocation entirely.
 */
val firebaseAnalytics: FirebaseAnalytics?
    get() = null

/**
 * Kept identical to the upstream Google source — referenced by
 * BenchmarkScreen, ChatView, GalleryNavGraph, etc. Even though the
 * `firebaseAnalytics` getter is null, callers reference these enum ids
 * to build their `Bundle` payload, so the enum must remain defined.
 */
enum class GalleryEvent(val id: String) {
    CAPABILITY_SELECT(id = "capability_select"),
    MODEL_DOWNLOAD(id = "model_download"),
    GENERATE_ACTION(id = "generate_action"),
    BUTTON_CLICKED(id = "button_clicked"),
    SKILL_MANAGEMENT(id = "skill_management"),
    SKILL_EXECUTION(id = "skill_execution"),
    CHAT_HISTORY(id = "chat_history"),
}
