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
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import io.github.danzuep.usfmbible.annotated.bookmarks.BookmarksViewModel
import io.github.danzuep.usfmbible.annotated.highlights.HighlightsViewModel
import io.github.danzuep.usfmbible.annotated.notes.NotesViewModel
import io.github.danzuep.usfmbible.progress.ReadingProgressViewModel
import io.github.danzuep.usfmbible.reading.chapter.ChapterSelectionViewModel
import io.github.danzuep.usfmbible.reading.toolbar.ReadingToolbarViewModel
import io.github.danzuep.usfmbible.reading.ReadingViewModel
import io.github.danzuep.usfmbible.search.SearchViewModel
import io.github.danzuep.usfmbible.settings.SettingsViewModel
import io.github.danzuep.usfmbible.timeline.TimelineViewModel
import io.github.danzuep.usfmbible.translations.TranslationUpdatesViewModel
import io.github.danzuep.usfmbible.translations.TranslationsViewModel
import io.github.danzuep.usfmbible.tests.BaseContainerTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Builds the real graph, so a wiring mistake fails here rather than when a screen is first opened. */
@RunWith(RobolectricTestRunner::class)
class AppContainerTest : BaseContainerTest() {
    private val container get() = ApplicationProvider.getApplicationContext<Application>().container

    @Test
    fun `test shared instances are created once`() {
        assertSame(container.navigator, container.navigator)
        assertSame(container.settingsManager, container.settingsManager)
        assertNotNull(container.backupManager)
        assertNotNull(container.bibleReadingManager)
        assertNotNull(container.readingProgressManager)
        assertNotNull(container.translationManager)
    }

    @Test
    fun `test view models can be created`() {
        listOf<KClass<out ViewModel>>(
            ReadingProgressViewModel::class, ReadingViewModel::class, SearchViewModel::class, SettingsViewModel::class, TimelineViewModel::class, TranslationUpdatesViewModel::class, TranslationsViewModel::class, ChapterSelectionViewModel::class, ReadingToolbarViewModel::class, BookmarksViewModel::class, HighlightsViewModel::class, NotesViewModel::class,
        ).forEach { type -> assertNotNull(container.viewModelFactory.create(type.java)) }
    }
}
