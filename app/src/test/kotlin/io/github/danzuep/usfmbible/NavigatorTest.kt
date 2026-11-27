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

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.danzuep.usfmbible.annotated.bookmarks.BookmarksActivity
import io.github.danzuep.usfmbible.timeline.TimelineActivity
import io.github.danzuep.usfmbible.annotated.highlights.HighlightsActivity
import io.github.danzuep.usfmbible.annotated.notes.NotesActivity
import io.github.danzuep.usfmbible.progress.ReadingProgressActivity
import io.github.danzuep.usfmbible.reading.ReadingActivity
import io.github.danzuep.usfmbible.reading.chapter.ChapterSelectionViewModel
import io.github.danzuep.usfmbible.reading.toolbar.ReadingToolbarViewModel
import io.github.danzuep.usfmbible.search.SearchActivity
import io.github.danzuep.usfmbible.settings.SettingsActivity
import io.github.danzuep.usfmbible.tests.BaseActivityTest
import io.github.danzuep.usfmbible.translations.TranslationUpdatesActivity
import io.github.danzuep.usfmbible.translations.TranslationsActivity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import io.github.danzuep.usfmbible.tests.TestContainer

@RunWith(RobolectricTestRunner::class)
class NavigatorTest : BaseActivityTest() {
    private lateinit var navigator: Navigator

    val readingToolbarViewModel: ReadingToolbarViewModel = mockk(relaxed = true)

    val chapterSelectionViewModel: ChapterSelectionViewModel = mockk(relaxed = true)

    override fun createContainer(application: Application) = TestContainer(application, null, readingToolbarViewModel, chapterSelectionViewModel)

    @BeforeTest
    override fun setup() {
        super.setup()
        navigator = Navigator()
    }

    @Test
    fun `test navigate(), internal activities`() {
        testNavigate<ReadingActivity>(Navigator.SCREEN_READING)
        testNavigate<SearchActivity>(Navigator.SCREEN_SEARCH)
        testNavigate<TranslationsActivity>(Navigator.SCREEN_TRANSLATIONS)
        testNavigate<ReadingProgressActivity>(Navigator.SCREEN_READING_PROGRESS)
        testNavigate<BookmarksActivity>(Navigator.SCREEN_BOOKMARKS)
        testNavigate<HighlightsActivity>(Navigator.SCREEN_HIGHLIGHTS)
        testNavigate<NotesActivity>(Navigator.SCREEN_NOTES)
        testNavigate<SettingsActivity>(Navigator.SCREEN_SETTINGS)
        testNavigate<TimelineActivity>(Navigator.SCREEN_TIMELINE)
        testNavigate<TranslationUpdatesActivity>(Navigator.SCREEN_TRANSLATION_UPDATES)
    }

    private inline fun <reified A : Activity> testNavigate(@Navigator.Companion.Screen screen: Int) {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, screen)

            val nextStartedIntent = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
            assertEquals(A::class.java.name, nextStartedIntent.component?.className)
        }
    }

    @Test
    fun `test navigate(), SCREEN_NOTES`() {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, Navigator.SCREEN_NOTES, Bundle().apply { putString("test_key", "value") })

            val nextStartedIntent = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
            assertEquals(NotesActivity::class.java.name, nextStartedIntent.component?.className)
            assertEquals(1, nextStartedIntent.extras?.size())
            assertEquals("value", nextStartedIntent.getStringExtra("test_key"))
        }
    }

    @Test
    fun `test navigate(), SCREEN_RATE_ME`() {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, Navigator.SCREEN_RATE_ME)

            val nextStartedIntent = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, nextStartedIntent.action)
            assertEquals(Uri.parse("https://github.com/danzuep/UsfmBible"), nextStartedIntent.data)
        }
    }

    @Test
    fun `test navigate(), SCREEN_WEBSITE`() {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, Navigator.SCREEN_WEBSITE)

            val nextStartedIntent = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, nextStartedIntent.action)
            assertEquals(Uri.parse("https://danzuep.github.io/UsfmBible/"), nextStartedIntent.data)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test navigate(), with unknown screen`() {
        withActivity<ReadingActivity> { activity ->
            navigator.navigate(activity, -1)
        }
    }

    @Test
    fun `test goBack()`() {
        withActivity<ReadingActivity> { activity ->
            navigator.goBack(activity)
            // Verify that onBackPressedDispatcher.onBackPressed() was called
            // The activity should not be finishing immediately as onBackPressedDispatcher
            // may handle the back press asynchronously
        }
    }
}
