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

import androidx.lifecycle.ViewModel
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
import io.github.danzuep.usfmbible.progress.ReadingProgressViewModel
import io.github.danzuep.usfmbible.reading.ReadingViewModel
import io.github.danzuep.usfmbible.reading.chapter.ChapterSelectionViewModel
import io.github.danzuep.usfmbible.reading.toolbar.ReadingToolbarViewModel
import io.github.danzuep.usfmbible.search.SearchViewModel
import io.github.danzuep.usfmbible.settings.SettingsViewModel
import io.github.danzuep.usfmbible.tests.BaseKoinTest
import io.github.danzuep.usfmbible.timeline.TimelineViewModel
import io.github.danzuep.usfmbible.translations.TranslationUpdatesViewModel
import io.github.danzuep.usfmbible.translations.TranslationsViewModel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner

/** The graph is checked at runtime, so this catches a missing or mis-ordered binding without launching a screen. */
@RunWith(RobolectricTestRunner::class)
class AppModuleTest : BaseKoinTest() {
    private val koin get() = GlobalContext.get()

    @Test
    fun `test singletons resolve and are shared`() {
        assertSame(koin.get<Navigator>(), koin.get<Navigator>())
        assertSame(koin.get<SettingsManager>(), koin.get<SettingsManager>())
        assertNotNull(koin.get<BackupManager>())
        assertNotNull(koin.get<BibleReadingManager>())
        assertNotNull(koin.get<ReadingProgressManager>())
        assertNotNull(koin.get<SearchManager>())
        assertNotNull(koin.get<TranslationManager>())
    }

    @Test
    fun `test annotation managers are bound per type`() {
        val bookmarks = koin.get<VerseAnnotationManager<Bookmark>>(Annotations.bookmarks)
        val highlights = koin.get<VerseAnnotationManager<Highlight>>(Annotations.highlights)
        val notes = koin.get<VerseAnnotationManager<Note>>(Annotations.notes)
        assertNotSame<Any>(bookmarks, highlights)
        assertNotSame<Any>(highlights, notes)
    }

    @Test
    fun `test view models resolve`() {
        listOf(
            ReadingProgressViewModel::class, ReadingViewModel::class, SearchViewModel::class, SettingsViewModel::class,
            TimelineViewModel::class, TranslationUpdatesViewModel::class, TranslationsViewModel::class,
            ChapterSelectionViewModel::class, ReadingToolbarViewModel::class, BookmarksViewModel::class,
            HighlightsViewModel::class, NotesViewModel::class,
        ).forEach { type ->
            val viewModel: ViewModel = koin.get(type)
            assertNotNull(viewModel)
        }
    }
}
