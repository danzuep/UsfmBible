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

package io.github.danzuep.usfmbible.reading.chapter

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import io.github.danzuep.usfmbible.core.Bible
import io.github.danzuep.usfmbible.core.BibleReadingManager
import io.github.danzuep.usfmbible.core.VerseIndex
import io.github.danzuep.usfmbible.infra.BaseViewModel
import co.touchlab.kermit.Logger

class ChapterSelectionViewModel(
    private val bibleReadingManager: BibleReadingManager,
) : BaseViewModel<ChapterSelectionViewModel.ViewAction, ChapterSelectionViewModel.ViewState>(
    initialViewState = ViewState(
        currentBookIndex = -1,
        currentChapterIndex = -1,
        chapterSelectionItems = emptyList(),
        error = null,
    )
) {
    sealed class ViewAction

    data class ViewState(
        val currentBookIndex: Int,
        val currentChapterIndex: Int,
        val chapterSelectionItems: List<ChapterSelectionItem>,
        val error: Error?,
    ) {
        sealed class Error {
            data class ChapterSelectionError(val bookToSelect: Int, val chapterToSelect: Int) : Error()
        }
    }

    private var currentChapter = -1 to -1

    init {
        bibleReadingManager.currentTranslation().onEach { currentTranslation ->
            val booksWithVerses = bibleReadingManager.readBookIndicesWithVerses(currentTranslation)
            val chapterSelectionItems = bibleReadingManager.readBookNames(currentTranslation).mapIndexedNotNull { index, bookName ->
                if (index in booksWithVerses) ChapterSelectionItem(bookIndex = index, bookName = bookName, chapterCount = Bible.getChapterCount(index)) else null
            }
            updateViewState { it.copy(chapterSelectionItems = chapterSelectionItems) }
        }.launchIn(viewModelScope)

        bibleReadingManager.currentVerseIndex().onEach { currentVerseIndex ->
            currentChapter = currentVerseIndex.bookIndex to currentVerseIndex.chapterIndex
            updateViewState { it.copy(currentBookIndex = currentVerseIndex.bookIndex, currentChapterIndex = currentVerseIndex.chapterIndex) }
        }.launchIn(viewModelScope)
    }

    /** The number of verses in the chapter of the current translation, or 0 if it has none. */
    suspend fun verseCount(bookIndex: Int, chapterIndex: Int): Int = runCatching {
        val translation = bibleReadingManager.currentTranslation().first()
        bibleReadingManager.readVerses(translation, bookIndex, chapterIndex).maxOfOrNull { it.verseIndex.verseIndex + 1 } ?: 0
    }.getOrDefault(0)

    /** Moves the reader to the chapter's start, unless it is already being read. */
    fun openChapter(bookIndex: Int, chapterIndex: Int) {
        if (currentChapter != bookIndex to chapterIndex) selectChapter(bookIndex, chapterIndex)
    }

    fun selectChapter(bookToSelect: Int, chapterToSelect: Int, verseToSelect: Int = 0) {
        viewModelScope.launch {
            runCatching {
                bibleReadingManager.saveCurrentVerseIndex(VerseIndex(bookIndex = bookToSelect, chapterIndex = chapterToSelect, verseIndex = verseToSelect))
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to select chapter [bookToSelect=$bookToSelect, chapterToSelect=$chapterToSelect]" }
                updateViewState { it.copy(error = ViewState.Error.ChapterSelectionError(bookToSelect = bookToSelect, chapterToSelect = chapterToSelect)) }
            }
        }
    }

    fun markErrorAsShown(error: ViewState.Error) {
        updateViewState { current -> if (current.error == error) current.copy(error = null) else null }
    }
}

data class ChapterSelectionItem(val bookIndex: Int, val bookName: String, val chapterCount: Int)
