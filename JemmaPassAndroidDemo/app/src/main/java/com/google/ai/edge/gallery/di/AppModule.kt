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

package com.google.ai.edge.gallery.di

import android.content.Context
import com.google.ai.edge.gallery.AppLifecycleProvider
import com.google.ai.edge.gallery.GalleryLifecycleProvider
import com.google.ai.edge.gallery.data.DefaultDownloadRepository
import com.google.ai.edge.gallery.data.DownloadRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * JEMMA — trimmed AI Edge Gallery module.
 *
 * Only the two bindings JEMMA actually consumes survive the Gallery cleanup:
 * the app-lifecycle probe used by the download notifications and the
 * WorkManager-backed [DownloadRepository] that fetches the Gemma 4 model and
 * the clinical knowledge base. The Gallery DataStores (settings, user data,
 * cutouts, benchmark results, skills) and their protobuf serializers were
 * removed together with the Gallery UI.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

  @Provides
  @Singleton
  fun provideAppLifecycleProvider(): AppLifecycleProvider {
    return GalleryLifecycleProvider()
  }

  @Provides
  @Singleton
  fun provideDownloadRepository(
    @ApplicationContext context: Context,
    lifecycleProvider: AppLifecycleProvider,
  ): DownloadRepository {
    return DefaultDownloadRepository(context, lifecycleProvider)
  }
}
