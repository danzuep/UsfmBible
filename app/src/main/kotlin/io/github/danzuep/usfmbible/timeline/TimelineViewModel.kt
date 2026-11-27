/*
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

package io.github.danzuep.usfmbible.timeline

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseItem
import io.github.danzuep.usfmbible.core.BibleReadingManager
import io.github.danzuep.usfmbible.core.Constants
import io.github.danzuep.usfmbible.core.ReadingTimeline
import io.github.danzuep.usfmbible.core.SettingsManager
import io.github.danzuep.usfmbible.core.VerseIndex
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.infra.BaseViewModel
import io.github.danzuep.usfmbible.utils.firstNotEmpty
import co.touchlab.kermit.Logger

class TimelineViewModel(
    private val bibleReadingManager: BibleReadingManager,
    private val settingsManager: SettingsManager,
    private val coroutineDispatcherProvider: CoroutineDispatcherProvider,
    private val application: Application
) : BaseViewModel<TimelineViewModel.ViewAction, TimelineViewModel.ViewState>(
    initialViewState = ViewState(
        loading = false,
        limit = ReadingTimeline.DEFAULT_LIMIT,
        items = emptyList(),
        error = null,
    )
) {
    sealed class ViewAction {
        object OpenReadingScreen : ViewAction()
    }

    data class ViewState(
        val loading: Boolean,
        val limit: Int,
        val items: List<AnnotatedVerseItem>,
        val error: Error?,
        @Constants.SortOrder val sortOrder: Int = Constants.DEFAULT_SORT_ORDER,
    ) {
        sealed class Error {
            object TimelineLoadingError : Error()
            data class LimitSavingError(val limit: Int) : Error()
            data class TimelineDeletingError(val verses: Set<VerseIndex>) : Error()
            data class VerseOpeningError(val verseToOpen: VerseIndex) : Error()
        }
    }

    // The timeline has no dates, so "by date" is the order it was read in, most recent first.
    @Constants.SortOrder
    private var sortOrder = Constants.DEFAULT_SORT_ORDER

    fun setSortOrder(@Constants.SortOrder sortOrder: Int) {
        if (sortOrder == this.sortOrder) return
        this.sortOrder = sortOrder
        loadTimeline()
    }

    fun loadTimeline() {
        viewModelScope.launch(coroutineDispatcherProvider.default) {
            runCatching {
                updateViewState { it.copy(loading = true, items = emptyList()) }

                val settings = settingsManager.settings().first()
                val limit = bibleReadingManager.timelineLimit()
                val timeline = bibleReadingManager.readTimeline()
                val translation = bibleReadingManager.currentTranslation().firstNotEmpty()
                val verses = bibleReadingManager.readVerses(translation, timeline)
                val bookNames = bibleReadingManager.readBookNames(translation)
                val bookShortNames = bibleReadingManager.readBookShortNames(translation)

                val order = sortOrder
                val readVerses = timeline.filter { it in verses }.let { found ->
                    if (order == Constants.SORT_BY_BOOK) {
                        found.sortedWith(compareBy({ it.bookIndex }, { it.chapterIndex }, { it.verseIndex }))
                    } else {
                        found
                    }
                }
                val items = ArrayList<AnnotatedVerseItem>()
                var currentBookIndex = -1
                readVerses.forEach { verseIndex ->
                    if (order == Constants.SORT_BY_BOOK && verseIndex.bookIndex != currentBookIndex) {
                        currentBookIndex = verseIndex.bookIndex
                        items.add(AnnotatedVerseItem.Header(settings = settings, text = bookNames[verseIndex.bookIndex], hideDivider = false))
                    }
                    items.add(
                        AnnotatedVerseItem.Bookmark(
                            settings = settings,
                            verseIndex = verseIndex,
                            bookName = bookNames[verseIndex.bookIndex],
                            bookShortName = bookShortNames[verseIndex.bookIndex],
                            verseText = verses.getValue(verseIndex).text.text,
                            sortOrder = order
                        )
                    )
                }
                val shown = items.ifEmpty {
                    listOf(AnnotatedVerseItem.Header(settings = settings, text = application.getString(R.string.text_no_timeline), hideDivider = true))
                }
                updateViewState { it.copy(loading = false, limit = limit, items = shown, sortOrder = order) }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to load the reading timeline" }
                updateViewState { it.copy(loading = false, error = ViewState.Error.TimelineLoadingError) }
            }
        }
    }

    fun saveLimit(limit: Int) {
        viewModelScope.launch(coroutineDispatcherProvider.default) {
            runCatching {
                bibleReadingManager.saveTimelineLimit(limit)
                loadTimeline()
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to save the reading timeline limit" }
                updateViewState { it.copy(error = ViewState.Error.LimitSavingError(limit)) }
            }
        }
    }

    fun deleteTimelineVerses(verses: Set<VerseIndex>) {
        if (verses.isEmpty()) return
        viewModelScope.launch(coroutineDispatcherProvider.default) {
            runCatching {
                bibleReadingManager.removeTimelineVerses(verses)
                loadTimeline()
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to delete verses from the reading timeline" }
                updateViewState { it.copy(error = ViewState.Error.TimelineDeletingError(verses)) }
            }
        }
    }

    fun openVerse(verseToOpen: VerseIndex) {
        viewModelScope.launch(coroutineDispatcherProvider.default) {
            runCatching {
                bibleReadingManager.saveCurrentVerseIndex(verseToOpen)
                emitViewAction(ViewAction.OpenReadingScreen)
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to save current verse" }
                updateViewState { it.copy(error = ViewState.Error.VerseOpeningError(verseToOpen)) }
            }
        }
    }

    fun markErrorAsShown(error: ViewState.Error) {
        updateViewState { current -> if (current.error == error) current.copy(error = null) else null }
    }
}
