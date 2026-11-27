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
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import io.github.danzuep.usfmbible.annotated.bookmarks.BookmarksViewModel
import io.github.danzuep.usfmbible.annotated.highlights.HighlightsViewModel
import io.github.danzuep.usfmbible.annotated.notes.NotesViewModel
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
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.core.provider.DefaultCoroutineDispatcherProvider
import io.github.danzuep.usfmbible.core.provider.DefaultTimeProvider
import io.github.danzuep.usfmbible.core.provider.TimeProvider
import io.github.danzuep.usfmbible.core.provider.TimeSource
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
import io.github.danzuep.usfmbible.core.repository.local.android.db.AndroidDatabase
import io.github.danzuep.usfmbible.core.repository.remote.android.HttpTranslationService
import io.github.danzuep.usfmbible.core.repository.remote.NetworkPolicy
import io.github.danzuep.usfmbible.core.repository.remote.TranslationCatalog
import io.github.danzuep.usfmbible.core.repository.remote.TranslationUpdateChecker
import io.github.danzuep.usfmbible.core.repository.remote.TranslationUpdateScheduler
import io.github.danzuep.usfmbible.core.repository.remote.TranslationVersions
import io.github.danzuep.usfmbible.core.repository.remote.android.AndroidNetworkPolicy
import io.github.danzuep.usfmbible.core.repository.remote.android.AndroidTranslationUpdateScheduler
import io.github.danzuep.usfmbible.core.repository.remote.android.AndroidTranslationVersions
import io.github.danzuep.usfmbible.core.repository.remote.android.AssetTranslationCatalog
import io.github.danzuep.usfmbible.core.repository.remote.android.fetchLastModified
import io.github.danzuep.usfmbible.core.serializer.android.BackupJsonSerializer
import io.github.danzuep.usfmbible.progress.ReadingProgressViewModel
import io.github.danzuep.usfmbible.reading.ReadingViewModel
import io.github.danzuep.usfmbible.reading.chapter.ChapterSelectionViewModel
import io.github.danzuep.usfmbible.reading.toolbar.ReadingToolbarViewModel
import io.github.danzuep.usfmbible.reading.speech.AndroidChapterSpeaker
import io.github.danzuep.usfmbible.reading.speech.ChapterSpeaker
import io.github.danzuep.usfmbible.search.SearchViewModel
import io.github.danzuep.usfmbible.settings.SettingsViewModel
import io.github.danzuep.usfmbible.timeline.TimelineViewModel
import io.github.danzuep.usfmbible.translations.TranslationUpdatesViewModel
import io.github.danzuep.usfmbible.translations.TranslationsViewModel

/**
 * Manual dependency graph, shared by the whole app. Everything is created lazily on first use, so nothing is built at
 * app start unless it is requested. Members are open so tests can substitute them.
 */
open class AppContainer(private val application: Application) {
    open val navigator: Navigator by lazy { AndroidNavigator(application) }
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val timeProvider: TimeProvider by lazy { DefaultTimeProvider() }
    val coroutineDispatcherProvider: CoroutineDispatcherProvider by lazy { DefaultCoroutineDispatcherProvider() }

    /** A new text to speech reader of chapters; each screen that reads aloud owns one and releases it when done. */
    open fun chapterSpeaker(listener: ChapterSpeaker.Listener): ChapterSpeaker = AndroidChapterSpeaker(application, listener)

    private val networkPolicy: NetworkPolicy by lazy { AndroidNetworkPolicy(application) { settingsRepository.settings.first() } }
    private val translationCatalog: TranslationCatalog by lazy { AssetTranslationCatalog(application) }
    private val translationVersions: TranslationVersions by lazy { AndroidTranslationVersions(application) }
    private val translationUpdateScheduler: TranslationUpdateScheduler by lazy { AndroidTranslationUpdateScheduler(application) }

    private val database by lazy { AndroidDatabase(application) }

    val bibleReadingRepository: BibleReadingRepository by lazy { BibleReadingRepository(AndroidReadingStorage(database), appScope) }
    private val bookmarkRepository by lazy { VerseAnnotationRepository<Bookmark>(AndroidBookmarkStorage(database), appScope) }
    private val highlightRepository by lazy { VerseAnnotationRepository<Highlight>(AndroidHighlightStorage(database), appScope) }
    private val noteRepository by lazy { VerseAnnotationRepository<Note>(AndroidNoteStorage(database), appScope) }
    private val readingProgressRepository by lazy { ReadingProgressRepository(AndroidReadingProgressStorage(database)) }
    private val settingsRepository by lazy { SettingsRepository(AndroidSettingsStorage(database), appScope) }
    private val translationRepository by lazy {
        TranslationRepository(
            AndroidTranslationStorage(database, application),
            HttpTranslationService(application, networkPolicy, translationCatalog, translationVersions),
            timeProvider,
            appScope,
        )
    }

    val backupManager by lazy { BackupManager(BackupJsonSerializer(), bookmarkRepository, highlightRepository, noteRepository, readingProgressRepository) }
    val bibleReadingManager by lazy { BibleReadingManager(bibleReadingRepository, translationRepository, appScope) }
    private val bookmarkManager by lazy { VerseAnnotationManager(bookmarkRepository) }
    private val highlightManager by lazy { VerseAnnotationManager(highlightRepository) }
    private val noteManager by lazy { VerseAnnotationManager(noteRepository) }
    val readingProgressManager by lazy { ReadingProgressManager(bibleReadingRepository, readingProgressRepository, timeProvider, appScope) }
    private val searchManager by lazy { SearchManager(bibleReadingRepository, bookmarkRepository, highlightRepository, noteRepository) }
    val settingsManager by lazy { SettingsManager(settingsRepository) }
    val translationManager by lazy { TranslationManager(translationRepository) }
    val translationUpdateChecker by lazy { TranslationUpdateChecker(translationManager, translationVersions, ::fetchLastModified) }

    open val viewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory {
            initializer { ReadingProgressViewModel(bibleReadingManager, readingProgressManager, settingsManager, coroutineDispatcherProvider) }
            initializer {
                ReadingViewModel(
                    bibleReadingManager, readingProgressManager, translationManager, bookmarkManager, highlightManager, noteManager,
                    settingsManager, timeProvider, application,
                )
            }
            initializer { SearchViewModel(bibleReadingManager, searchManager, settingsManager, coroutineDispatcherProvider, application) }
            initializer { SettingsViewModel(backupManager, settingsManager, application, coroutineDispatcherProvider) }
            initializer { TimelineViewModel(bibleReadingManager, settingsManager, coroutineDispatcherProvider, application) }
            initializer { TranslationUpdatesViewModel(translationManager, translationUpdateChecker, translationUpdateScheduler, networkPolicy, settingsManager, application, coroutineDispatcherProvider) }
            initializer { TranslationsViewModel(bibleReadingManager, translationManager, settingsManager, translationCatalog, application, coroutineDispatcherProvider) }
            initializer { ChapterSelectionViewModel(bibleReadingManager) }
            initializer { ReadingToolbarViewModel(bibleReadingManager, translationManager, application) }
            initializer {
                BookmarksViewModel(bibleReadingManager, bookmarkManager, settingsManager, coroutineDispatcherProvider, timeProvider, application)
            }
            initializer {
                HighlightsViewModel(bibleReadingManager, highlightManager, settingsManager, coroutineDispatcherProvider, timeProvider, application)
            }
            initializer { NotesViewModel(bibleReadingManager, noteManager, settingsManager, coroutineDispatcherProvider, timeProvider, application) }
        }
    }
}

/** Implemented by the [Application] that owns the [AppContainer], so tests can supply their own. */
interface ContainerHolder {
    val container: AppContainer
}

val Context.container: AppContainer get() = (applicationContext as ContainerHolder).container
