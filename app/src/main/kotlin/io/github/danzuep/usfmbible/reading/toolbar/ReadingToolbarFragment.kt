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

import androidx.appcompat.widget.Toolbar
import io.github.danzuep.usfmbible.Screen
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.core.VerseIndex
import io.github.danzuep.usfmbible.databinding.FragmentReadingToolbarBinding
import io.github.danzuep.usfmbible.infra.BaseFragment
import io.github.danzuep.usfmbible.ui.showCopyright
import io.github.danzuep.usfmbible.ui.toast
import io.github.danzuep.usfmbible.infra.viewModelOf
import io.github.danzuep.usfmbible.container

class ReadingToolbarFragment : BaseFragment<FragmentReadingToolbarBinding, ReadingToolbarViewModel.ViewAction, ReadingToolbarViewModel.ViewState, ReadingToolbarViewModel>() {
    val toolbar: Toolbar by lazy(LazyThreadSafetyMode.NONE) { viewBinding.toolbar }

    fun showReadingChapter(firstVerse: VerseIndex, middleVerse: VerseIndex) = viewModel.showReadingChapter(firstVerse, middleVerse)

    var onTitleClicked: (() -> Unit)? = null

    override val viewModel: ReadingToolbarViewModel by viewModelOf()

    override val viewBinding: FragmentReadingToolbarBinding by lazy(LazyThreadSafetyMode.NONE) { FragmentReadingToolbarBinding.inflate(layoutInflater) }

    override fun initializeView() {
        viewBinding.toolbar.initialize { viewEvent ->
            when (viewEvent) {
                is ReadingToolbar.ViewEvent.OpenCopyright -> requireContext().showCopyright(viewEvent.translationShortName)
                is ReadingToolbar.ViewEvent.OpenTimeline -> navigator.navigate(Screen.TIMELINE)
                is ReadingToolbar.ViewEvent.OpenBookmarks -> navigator.navigate(Screen.BOOKMARKS)
                is ReadingToolbar.ViewEvent.OpenHighlights -> navigator.navigate(Screen.HIGHLIGHTS)
                is ReadingToolbar.ViewEvent.OpenNotes -> navigator.navigate(Screen.NOTES)
                is ReadingToolbar.ViewEvent.OpenReadingProgress -> navigator.navigate(Screen.READING_PROGRESS)
                is ReadingToolbar.ViewEvent.OpenSearch -> navigator.navigate(Screen.SEARCH)
                is ReadingToolbar.ViewEvent.OpenSettings -> navigator.navigate(Screen.SETTINGS)
                is ReadingToolbar.ViewEvent.OpenTranslations -> navigator.navigate(Screen.TRANSLATIONS)
                is ReadingToolbar.ViewEvent.RemoveParallelTranslation -> viewModel.removeParallelTranslation(viewEvent.translationToRemove)
                is ReadingToolbar.ViewEvent.RequestParallelTranslation -> viewModel.requestParallelTranslation(viewEvent.translationToRequest)
                is ReadingToolbar.ViewEvent.SelectCurrentTranslation -> viewModel.selectTranslation(viewEvent.translationToSelect)
                is ReadingToolbar.ViewEvent.TitleClicked -> onTitleClicked?.invoke()
            }
        }
    }

    override fun onViewActionEmitted(viewAction: ReadingToolbarViewModel.ViewAction) {}

    override fun onViewStateUpdated(viewState: ReadingToolbarViewModel.ViewState): Unit = with(viewBinding) {
        toolbar.title = viewState.title
        toolbar.setTranslationItems(viewState.translationItems)

        viewState.error?.handle()
    }

    private fun ReadingToolbarViewModel.ViewState.Error.handle() = when (this) {
        is ReadingToolbarViewModel.ViewState.Error.ParallelTranslationRemovalError,
        is ReadingToolbarViewModel.ViewState.Error.ParallelTranslationRequestingError,
        is ReadingToolbarViewModel.ViewState.Error.TranslationSelectionError -> {
            toast(R.string.toast_unknown_error)
            viewModel.markErrorAsShown(this)
        }
    }
}
