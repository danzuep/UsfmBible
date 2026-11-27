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
import io.github.danzuep.usfmbible.core.repository.remote.android.NetworkPolicy
import io.github.danzuep.usfmbible.core.serializer.android.BackupJsonSerializer
import io.github.danzuep.usfmbible.progress.ReadingProgressViewModel
import io.github.danzuep.usfmbible.reading.ReadingViewModel
import io.github.danzuep.usfmbible.reading.chapter.ChapterSelectionViewModel
import io.github.danzuep.usfmbible.reading.toolbar.ReadingToolbarViewModel
import io.github.danzuep.usfmbible.search.SearchViewModel
import io.github.danzuep.usfmbible.settings.SettingsViewModel
import io.github.danzuep.usfmbible.timeline.TimelineViewModel
import io.github.danzuep.usfmbible.translations.TranslationUpdatesViewModel
import io.github.danzuep.usfmbible.translations.TranslationsViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/** Koin qualifiers for the verse annotation types, whose generic parameter is erased at runtime. */
object Annotations {
    val bookmarks = named("bookmarks")
    val highlights = named("highlights")
    val notes = named("notes")
}

/** Everything is created lazily on first use, so nothing here runs at app start unless it is requested. */
val providerModule = module {
    single { Navigator() }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<CoroutineDispatcherProvider> { DefaultCoroutineDispatcherProvider() }
    single<TimeProvider> { DefaultTimeProvider() } bind TimeSource::class
}

val repositoryModule = module {
    single { AndroidDatabase(androidApplication()) }
    single { BibleReadingRepository(AndroidReadingStorage(get()), get()) }
    single(Annotations.bookmarks) { VerseAnnotationRepository(AndroidBookmarkStorage(get()), get()) }
    single(Annotations.highlights) { VerseAnnotationRepository(AndroidHighlightStorage(get()), get()) }
    single(Annotations.notes) { VerseAnnotationRepository(AndroidNoteStorage(get()), get()) }
    single { ReadingProgressRepository(AndroidReadingProgressStorage(get())) }
    single { SettingsRepository(AndroidSettingsStorage(get()), get()) }
    single {
        val settingsRepository = get<SettingsRepository>()
        TranslationRepository(
            AndroidTranslationStorage(get(), androidApplication()),
            HttpTranslationService(androidApplication(), NetworkPolicy(androidApplication()) { settingsRepository.settings.first() }),
            get(),
            get(),
        )
    }
}

val managerModule = module {
    single { BackupManager(BackupJsonSerializer(), get(Annotations.bookmarks), get(Annotations.highlights), get(Annotations.notes), get()) }
    single { BibleReadingManager(get(), get(), get()) }
    single(Annotations.bookmarks) { VerseAnnotationManager(get<VerseAnnotationRepository<Bookmark>>(Annotations.bookmarks)) }
    single(Annotations.highlights) { VerseAnnotationManager(get<VerseAnnotationRepository<Highlight>>(Annotations.highlights)) }
    single(Annotations.notes) { VerseAnnotationManager(get<VerseAnnotationRepository<Note>>(Annotations.notes)) }
    single { ReadingProgressManager(get(), get(), get(), get()) }
    single { SearchManager(get(), get(Annotations.bookmarks), get(Annotations.highlights), get(Annotations.notes)) }
    single { SettingsManager(get()) }
    single { TranslationManager(get()) }
}

val viewModelModule = module {
    viewModel { ReadingProgressViewModel(get(), get(), get(), get()) }
    viewModel {
        ReadingViewModel(
            get(), get(), get(), get(Annotations.bookmarks), get(Annotations.highlights), get(Annotations.notes), get(), get(), androidApplication(),
        )
    }
    viewModel { SearchViewModel(get(), get(), get(), get(), androidApplication()) }
    viewModel { SettingsViewModel(get(), get(), androidApplication(), get()) }
    viewModel { TimelineViewModel(get(), get(), get(), androidApplication()) }
    viewModel { TranslationUpdatesViewModel(get(), get(), androidApplication(), get()) }
    viewModel { TranslationsViewModel(get(), get(), get(), androidApplication(), get()) }
    viewModel { ChapterSelectionViewModel(get()) }
    viewModel { ReadingToolbarViewModel(get(), get(), androidApplication()) }
    viewModel { BookmarksViewModel(get(), get(Annotations.bookmarks), get(), get(), get(), androidApplication()) }
    viewModel { HighlightsViewModel(get(), get(Annotations.highlights), get(), get(), get(), androidApplication()) }
    viewModel { NotesViewModel(get(), get(Annotations.notes), get(), get(), get(), androidApplication()) }
}

val appModules = listOf(providerModule, repositoryModule, managerModule, viewModelModule)
