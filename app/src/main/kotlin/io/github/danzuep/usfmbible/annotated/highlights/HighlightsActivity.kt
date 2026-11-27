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

package io.github.danzuep.usfmbible.annotated.highlights

import android.app.Application
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseItem
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseActivity
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseViewModel
import io.github.danzuep.usfmbible.core.BibleReadingManager
import io.github.danzuep.usfmbible.core.Constants
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.core.Highlight
import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.core.SettingsManager
import io.github.danzuep.usfmbible.core.VerseAnnotationManager
import io.github.danzuep.usfmbible.core.provider.TimeProvider
import org.koin.androidx.viewmodel.ext.android.viewModel

class HighlightsViewModel(
    bibleReadingManager: BibleReadingManager,
    highlightsManager: VerseAnnotationManager<Highlight>,
    settingsManager: SettingsManager,
    coroutineDispatcherProvider: CoroutineDispatcherProvider,
    timeProvider: TimeProvider,
    application: Application
) : AnnotatedVerseViewModel<Highlight>(
    bibleReadingManager = bibleReadingManager,
    verseAnnotationManager = highlightsManager,
    noItemText = R.string.text_no_highlights,
    settingsManager = settingsManager,
    coroutineDispatcherProvider = coroutineDispatcherProvider,
    timeProvider = timeProvider,
    application = application
) {
    override fun buildAnnotatedVerseItem(
        settings: Settings,
        verseAnnotation: Highlight,
        bookName: String,
        bookShortName: String,
        verseText: String,
        @Constants.SortOrder sortOrder: Int
    ): AnnotatedVerseItem = AnnotatedVerseItem.Highlight(
        settings = settings,
        verseIndex = verseAnnotation.verseIndex,
        bookName = bookName,
        bookShortName = bookShortName,
        verseText = verseText,
        highlightColor = verseAnnotation.color,
        sortOrder = sortOrder
    )
}

class HighlightsActivity : AnnotatedVerseActivity<Highlight, HighlightsViewModel>(R.string.title_highlights) {
    override val viewModel: HighlightsViewModel by viewModel()
}
