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

package io.github.danzuep.usfmbible.annotated

import androidx.core.view.isVisible
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import io.github.danzuep.usfmbible.Navigator
import io.github.danzuep.usfmbible.Screen
import io.github.danzuep.usfmbible.annotated.bookmarks.BookmarksActivity
import io.github.danzuep.usfmbible.annotated.bookmarks.BookmarksViewModel
import io.github.danzuep.usfmbible.core.Constants
import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.core.VerseIndex
import io.github.danzuep.usfmbible.databinding.ActivityAnnotatedBinding
import io.github.danzuep.usfmbible.preview.Preview
import io.github.danzuep.usfmbible.preview.PreviewItem
import io.github.danzuep.usfmbible.tests.BaseActivityTest
import io.github.danzuep.usfmbible.tests.MockContents
import io.github.danzuep.usfmbible.tests.getProperty
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import io.github.danzuep.usfmbible.tests.TestContainer
import android.app.Application

@RunWith(RobolectricTestRunner::class)
class AnnotatedVerseActivityTest : BaseActivityTest() {
    private val mockNavigator: Navigator = mockk()

    val viewModel: BookmarksViewModel = mockk(relaxed = true)

    override fun createContainer(application: Application) = TestContainer(application, mockNavigator, viewModel)

    @Test
    fun `test adapter viewEvent, OpenVerse`() {
        withActivity<BookmarksActivity> { activity ->
            val adapter: AnnotatedVerseAdapter = (activity as AnnotatedVerseActivity<*, *>).getProperty("adapter")
            val onViewEvent: (AnnotatedVerseAdapter.ViewEvent) -> Unit = adapter.getProperty("onViewEvent")
            onViewEvent(AnnotatedVerseAdapter.ViewEvent.OpenVerse(VerseIndex(1, 2, 3)))

            verify(exactly = 1) { viewModel.openVerse(VerseIndex(1, 2, 3)) }
        }
    }

    @Test
    fun `test adapter viewEvent, ShowPreview`() {
        withActivity<BookmarksActivity> { activity ->
            val adapter: AnnotatedVerseAdapter = (activity as AnnotatedVerseActivity<*, *>).getProperty("adapter")
            val onViewEvent: (AnnotatedVerseAdapter.ViewEvent) -> Unit = adapter.getProperty("onViewEvent")
            onViewEvent(AnnotatedVerseAdapter.ViewEvent.ShowPreview(VerseIndex(1, 2, 3)))

            verify(exactly = 1) { viewModel.loadPreview(VerseIndex(1, 2, 3)) }
        }
    }

    @Test
    fun `test onViewActionEmitted(), OpenReadingScreen`() {
        every { viewModel.viewAction() } returns flowOf(AnnotatedVerseViewModel.ViewAction.OpenReadingScreen)
        every { mockNavigator.navigate(Screen.READING, any()) } returns Unit

        withActivity<BookmarksActivity> { activity ->
            verify(exactly = 1) { mockNavigator.navigate(Screen.READING, any()) }
        }
    }

    @Test
    fun `test onViewStateUpdated(), with null preview, and null error`() {
        every { viewModel.viewState() } returns flowOf(AnnotatedVerseViewModel.ViewState(
            loading = true,
            sortOrder = Constants.SORT_BY_DATE,
            items = emptyList(),
            preview = null,
            error = null,
        ))

        withActivity<BookmarksActivity> { activity ->
            val viewBinding: ActivityAnnotatedBinding = activity.getProperty("viewBinding")
            assertTrue(viewBinding.loadingSpinner.isVisible)
            assertFalse(viewBinding.verseList.isVisible)
            assertEquals(0, viewBinding.verseList.adapter!!.itemCount)
        }
    }

    @Test
    fun `test onViewStateUpdated(), with preview, and null error`() {
        every { viewModel.viewState() } returns flowOf(AnnotatedVerseViewModel.ViewState(
            loading = false,
            sortOrder = Constants.SORT_BY_DATE,
            items = listOf(AnnotatedVerseItem.Header(Settings.DEFAULT, "text", false)),
            preview = Preview(
                title = "preview_title",
                items = listOf(PreviewItem.Verse(
                    settings = Settings.DEFAULT,
                    verseIndex = VerseIndex(0, 0, 0),
                    verseText = MockContents.kjvVerses[0].text.text,
                    followingEmptyVerseCount = 0,
                )),
                currentPosition = 0,
            ),
            error = null,
        ))

        withActivity<BookmarksActivity> { activity ->
            val viewBinding: ActivityAnnotatedBinding = activity.getProperty("viewBinding")
            assertFalse(viewBinding.loadingSpinner.isVisible)
            assertTrue(viewBinding.verseList.isVisible)
            assertEquals(1, viewBinding.verseList.adapter!!.itemCount)

            assertTrue(ShadowDialog.getLatestDialog().isShowing) // TODO test dialog content
        }
    }

    @Test
    fun `test onViewStateUpdated(), with null preview, and AnnotatedVersesLoadingError error`() {
        every { viewModel.viewState() } returns flowOf(AnnotatedVerseViewModel.ViewState(
            loading = false,
            sortOrder = Constants.SORT_BY_DATE,
            items = emptyList(),
            preview = null,
            error = AnnotatedVerseViewModel.ViewState.Error.AnnotatedVersesLoadingError,
        ))

        withActivity<BookmarksActivity> { activity ->
            val viewBinding: ActivityAnnotatedBinding = activity.getProperty("viewBinding")
            assertFalse(viewBinding.loadingSpinner.isVisible)
            assertTrue(viewBinding.verseList.isVisible)
            assertEquals(0, viewBinding.verseList.adapter!!.itemCount)

            assertTrue(ShadowDialog.getLatestDialog().isShowing) // TODO test dialog content
        }
    }

    @Test
    fun `test onViewStateUpdated(), with null preview, and PreviewLoadingError error`() {
        every { viewModel.viewState() } returns flowOf(AnnotatedVerseViewModel.ViewState(
            loading = false,
            sortOrder = Constants.SORT_BY_DATE,
            items = emptyList(),
            preview = null,
            error = AnnotatedVerseViewModel.ViewState.Error.PreviewLoadingError(VerseIndex(1, 2, 3)),
        ))

        withActivity<BookmarksActivity> { activity ->
            val viewBinding: ActivityAnnotatedBinding = activity.getProperty("viewBinding")
            assertFalse(viewBinding.loadingSpinner.isVisible)
            assertTrue(viewBinding.verseList.isVisible)
            assertEquals(0, viewBinding.verseList.adapter!!.itemCount)

            verify(exactly = 1) {
                viewModel.markErrorAsShown(AnnotatedVerseViewModel.ViewState.Error.PreviewLoadingError(VerseIndex(1, 2, 3)))
                viewModel.openVerse(VerseIndex(1, 2, 3))
            }
        }
    }

    @Test
    fun `test onViewStateUpdated(), with null preview, and SortOrderSavingError error`() {
        every { viewModel.viewState() } returns flowOf(AnnotatedVerseViewModel.ViewState(
            loading = false,
            sortOrder = Constants.SORT_BY_DATE,
            items = emptyList(),
            preview = null,
            error = AnnotatedVerseViewModel.ViewState.Error.SortOrderSavingError(Constants.SORT_BY_BOOK),
        ))

        withActivity<BookmarksActivity> { activity ->
            val viewBinding: ActivityAnnotatedBinding = activity.getProperty("viewBinding")
            assertFalse(viewBinding.loadingSpinner.isVisible)
            assertTrue(viewBinding.verseList.isVisible)
            assertEquals(0, viewBinding.verseList.adapter!!.itemCount)

            assertTrue(ShadowDialog.getLatestDialog().isShowing) // TODO test dialog content
        }
    }

    @Test
    fun `test onViewStateUpdated(), with null preview, and VerseOpeningError error`() {
        every { viewModel.viewState() } returns flowOf(AnnotatedVerseViewModel.ViewState(
            loading = false,
            sortOrder = Constants.SORT_BY_DATE,
            items = emptyList(),
            preview = null,
            error = AnnotatedVerseViewModel.ViewState.Error.VerseOpeningError(VerseIndex(1, 2, 3)),
        ))

        withActivity<BookmarksActivity> { activity ->
            val viewBinding: ActivityAnnotatedBinding = activity.getProperty("viewBinding")
            assertFalse(viewBinding.loadingSpinner.isVisible)
            assertTrue(viewBinding.verseList.isVisible)
            assertEquals(0, viewBinding.verseList.adapter!!.itemCount)

            assertTrue(ShadowDialog.getLatestDialog().isShowing) // TODO test dialog content
        }
    }
}
