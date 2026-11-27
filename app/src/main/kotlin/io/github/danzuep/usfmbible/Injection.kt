/*
 * Copyright (C) 2023 Xizhi Zhu
 * Copyright (C) 2026 Daniel Collingwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.danzuep.usfmbible

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.core.provider.DefaultCoroutineDispatcherProvider
import io.github.danzuep.usfmbible.core.provider.DefaultTimeProvider
import io.github.danzuep.usfmbible.core.provider.TimeProvider
import io.github.danzuep.usfmbible.core.repository.local.android.db.AndroidDatabase
import io.github.danzuep.usfmbible.core.repository.remote.android.HttpTranslationService
import io.github.danzuep.usfmbible.core.repository.remote.android.NetworkPolicy
import kotlinx.coroutines.flow.first
import io.github.danzuep.usfmbible.core.serializer.android.BackupJsonSerializer
import javax.inject.Singleton
import io.github.danzuep.usfmbible.core.BackupManager
import io.github.danzuep.usfmbible.core.BibleReadingManager
import io.github.danzuep.usfmbible.core.Bookmark
import io.github.danzuep.usfmbible.core.Highlight
import io.github.danzuep.usfmbible.core.Note
import io.github.danzuep.usfmbible.core.ReadingProgressManager
import io.github.danzuep.usfmbible.core.SearchManager
import io.github.danzuep.usfmbible.core.SettingsManager
import io.github.danzuep.usfmbible.core.TranslationManager
import io.github.danzuep.usfmbible.core.VerseAnnotationManager
import io.github.danzuep.usfmbible.core.repository.BibleReadingRepository
import io.github.danzuep.usfmbible.core.repository.ReadingProgressRepository
import io.github.danzuep.usfmbible.core.repository.SettingsRepository
import io.github.danzuep.usfmbible.core.repository.TranslationRepository
import io.github.danzuep.usfmbible.core.repository.VerseAnnotationRepository
import io.github.danzuep.usfmbible.core.repository.local.android.AndroidBookmarkStorage
import io.github.danzuep.usfmbible.core.repository.local.android.AndroidHighlightStorage
import io.github.danzuep.usfmbible.core.repository.local.android.AndroidNoteStorage
import io.github.danzuep.usfmbible.core.repository.local.android.AndroidReadingProgressStorage
import io.github.danzuep.usfmbible.core.repository.local.android.AndroidReadingStorage
import io.github.danzuep.usfmbible.core.repository.local.android.AndroidSettingsStorage
import io.github.danzuep.usfmbible.core.repository.local.android.AndroidTranslationStorage

@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {
    @Provides
    @Singleton
    fun provideNavigator(): Navigator = Navigator()
}

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = appScope

    @Provides
    @Singleton
    fun provideCoroutineDispatcherProvider(): CoroutineDispatcherProvider = DefaultCoroutineDispatcherProvider()

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = DefaultTimeProvider()
}

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {
    @Provides
    @Singleton
    fun provideBackupManager(
        bookmarkRepository: VerseAnnotationRepository<Bookmark>,
        highlightRepository: VerseAnnotationRepository<Highlight>,
        noteRepository: VerseAnnotationRepository<Note>,
        readingProgressRepository: ReadingProgressRepository
    ): BackupManager = BackupManager(BackupJsonSerializer(), bookmarkRepository, highlightRepository, noteRepository, readingProgressRepository)

    @Provides
    @Singleton
    fun provideBibleReadingManager(
        bibleReadingRepository: BibleReadingRepository,
        translationRepository: TranslationRepository,
        appScope: CoroutineScope
    ): BibleReadingManager = BibleReadingManager(bibleReadingRepository, translationRepository, appScope)

    @Provides
    @Singleton
    fun provideBookmarkManager(bookmarkRepository: VerseAnnotationRepository<Bookmark>): VerseAnnotationManager<Bookmark> = VerseAnnotationManager(bookmarkRepository)

    @Provides
    @Singleton
    fun provideHighlightManager(highlightRepository: VerseAnnotationRepository<Highlight>): VerseAnnotationManager<Highlight> = VerseAnnotationManager(highlightRepository)

    @Provides
    @Singleton
    fun provideNoteManager(noteRepository: VerseAnnotationRepository<Note>): VerseAnnotationManager<Note> = VerseAnnotationManager(noteRepository)

    @Provides
    @Singleton
    fun provideReadingProgressManager(
        bibleReadingRepository: BibleReadingRepository,
        readingProgressRepository: ReadingProgressRepository,
        timeProvider: TimeProvider,
        appScope: CoroutineScope,
    ): ReadingProgressManager = ReadingProgressManager(bibleReadingRepository, readingProgressRepository, timeProvider, appScope)

    @Provides
    @Singleton
    fun provideSearchManager(
        bibleReadingRepository: BibleReadingRepository,
        bookmarkRepository: VerseAnnotationRepository<Bookmark>,
        highlightRepository: VerseAnnotationRepository<Highlight>,
        noteRepository: VerseAnnotationRepository<Note>
    ): SearchManager = SearchManager(bibleReadingRepository, bookmarkRepository, highlightRepository, noteRepository)

    @Provides
    @Singleton
    fun provideSettingsManager(settingsRepository: SettingsRepository): SettingsManager = SettingsManager(settingsRepository)

    @Provides
    @Singleton
    fun provideTranslationManager(translationRepository: TranslationRepository): TranslationManager = TranslationManager(translationRepository)
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideAndroidDatabase(app: Application): AndroidDatabase = AndroidDatabase(app)

    @Provides
    @Singleton
    fun provideBibleReadingRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): BibleReadingRepository =
        BibleReadingRepository(AndroidReadingStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideBookmarkRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): VerseAnnotationRepository<Bookmark> =
        VerseAnnotationRepository(AndroidBookmarkStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideHighlightRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): VerseAnnotationRepository<Highlight> =
        VerseAnnotationRepository(AndroidHighlightStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideNoteRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): VerseAnnotationRepository<Note> =
        VerseAnnotationRepository(AndroidNoteStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideReadingProgressRepository(androidDatabase: AndroidDatabase): ReadingProgressRepository =
        ReadingProgressRepository(AndroidReadingProgressStorage(androidDatabase))

    @Provides
    @Singleton
    fun provideSettingsRepository(androidDatabase: AndroidDatabase, appScope: CoroutineScope): SettingsRepository =
        SettingsRepository(AndroidSettingsStorage(androidDatabase), appScope)

    @Provides
    @Singleton
    fun provideTranslationRepository(
        app: Application, androidDatabase: AndroidDatabase, settingsRepository: SettingsRepository, timeProvider: TimeProvider, appScope: CoroutineScope,
    ): TranslationRepository = TranslationRepository(
        AndroidTranslationStorage(androidDatabase, app),
        HttpTranslationService(app, NetworkPolicy(app) { settingsRepository.settings.first() }),
        timeProvider,
        appScope,
    )
}
