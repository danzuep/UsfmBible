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

package io.github.danzuep.usfmbible.reading.toolbar

import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import io.github.danzuep.usfmbible.Navigator
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.databinding.FragmentReadingToolbarBinding
import io.github.danzuep.usfmbible.reading.ReadingActivity
import io.github.danzuep.usfmbible.reading.chapter.ChapterSelectionViewModel
import org.junit.runner.RunWith
import io.github.danzuep.usfmbible.tests.BaseFragmentTest
import io.github.danzuep.usfmbible.tests.MockContents
import io.github.danzuep.usfmbible.tests.getProperty
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class ReadingToolbarFragmentTest : BaseFragmentTest() {
    private val mockNavigator: Navigator = mockk()

    val readingToolbarViewModel: ReadingToolbarViewModel = mockk(relaxed = true)

    val chapterSelectionViewModel: ChapterSelectionViewModel = mockk(relaxed = true)

    override fun overrides() = module {
        single { mockNavigator }
        viewModel { readingToolbarViewModel }
        viewModel { chapterSelectionViewModel }
    }

    @Test
    fun `test toolbar ViewEvent`() {
        `verify toolbar ViewEvent`(R.id.action_bookmarks, Navigator.SCREEN_BOOKMARKS)
        `verify toolbar ViewEvent`(R.id.action_highlights, Navigator.SCREEN_HIGHLIGHTS)
        `verify toolbar ViewEvent`(R.id.action_notes, Navigator.SCREEN_NOTES)
        `verify toolbar ViewEvent`(R.id.action_reading_progress, Navigator.SCREEN_READING_PROGRESS)
        `verify toolbar ViewEvent`(R.id.action_search, Navigator.SCREEN_SEARCH)
        `verify toolbar ViewEvent`(R.id.action_settings, Navigator.SCREEN_SETTINGS)
        `verify toolbar ViewEvent`(R.id.action_timeline, Navigator.SCREEN_TIMELINE)
    }

    private fun `verify toolbar ViewEvent`(@IdRes menuItemId: Int, @Navigator.Companion.Screen screen: Int) {
        every { mockNavigator.navigate(any(), screen) } returns Unit

        withFragment<ReadingToolbarFragment, ReadingActivity> { fragment ->
            val viewBinding: FragmentReadingToolbarBinding = fragment.getProperty("viewBinding")
            val onMenuItemClickListener: Toolbar.OnMenuItemClickListener = (viewBinding.toolbar as Toolbar).getProperty("mOnMenuItemClickListener")
            assertTrue(onMenuItemClickListener.onMenuItemClick(viewBinding.toolbar.menu.findItem(menuItemId)))
            verify(exactly = 1) { mockNavigator.navigate(any(), screen) }
        }
    }

    @Test
    fun `test onViewStateUpdated(), with null error`() {
        every { readingToolbarViewModel.viewState() } returns flowOf(ReadingToolbarViewModel.ViewState(
            title = "my title",
            translationItems = emptyList(),
            error = null
        ))
        withFragment<ReadingToolbarFragment, ReadingActivity> { fragment ->
            val viewBinding: FragmentReadingToolbarBinding = fragment.getProperty("viewBinding")
            assertEquals("my title", viewBinding.toolbar.title.toString())
        }
    }

    @Test
    fun `test onViewStateUpdated(), with ParallelTranslationRequestingError error`() {
        every { readingToolbarViewModel.viewState() } returns flowOf(ReadingToolbarViewModel.ViewState(
            title = "my title",
            translationItems = emptyList(),
            error = ReadingToolbarViewModel.ViewState.Error.ParallelTranslationRequestingError(MockContents.kjvShortName)
        ))
        withFragment<ReadingToolbarFragment, ReadingActivity> { fragment ->
            val viewBinding: FragmentReadingToolbarBinding = fragment.getProperty("viewBinding")
            assertEquals("my title", viewBinding.toolbar.title.toString())
            verify { readingToolbarViewModel.markErrorAsShown(ReadingToolbarViewModel.ViewState.Error.ParallelTranslationRequestingError("KJV")) }
        }
    }
}
