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

package com.google.ai.edge.gallery

import android.app.Application

/*
 * ═══════════════════════════════════════════════════════════════════════
 *  JEMMA ADAPTATION — DO NOT REVERT
 * ═══════════════════════════════════════════════════════════════════════
 *  Hilt allows only ONE @HiltAndroidApp Application class per compilation
 *  unit. JemmaApplication is the launcher (declared in AndroidManifest as
 *  android:name=".JemmaApplication"). GalleryApplication is therefore
 *  NEVER INSTANTIATED at runtime.
 *
 *  Both original responsibilities of GalleryApplication.onCreate() :
 *    1. Theme loading     → moved to JemmaApplication (Hilt EntryPoint)
 *    2. Firebase init     → REMOVED (privacy-first stance)
 * ═══════════════════════════════════════════════════════════════════════
 */
class GalleryApplication : Application()
