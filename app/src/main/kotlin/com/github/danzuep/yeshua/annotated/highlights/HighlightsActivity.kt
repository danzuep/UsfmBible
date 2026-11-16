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

package com.github.danzuep.yeshua.annotated.highlights

import android.app.Application
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import com.github.danzuep.yeshua.R
import com.github.danzuep.yeshua.annotated.AnnotatedVerseItem
import com.github.danzuep.yeshua.annotated.AnnotatedVerseActivity
import com.github.danzuep.yeshua.annotated.AnnotatedVerseViewModel
import com.github.danzuep.yeshua.core.BibleReadingManager
import com.github.danzuep.yeshua.core.Constants
import com.github.danzuep.yeshua.core.provider.CoroutineDispatcherProvider
import com.github.danzuep.yeshua.core.Highlight
import com.github.danzuep.yeshua.core.Settings
import com.github.danzuep.yeshua.core.SettingsManager
import com.github.danzuep.yeshua.core.VerseAnnotationManager
import com.github.danzuep.yeshua.core.provider.TimeProvider
import javax.inject.Inject

@HiltViewModel
class HighlightsViewModel @Inject constructor(
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

@AndroidEntryPoint
class HighlightsActivity : AnnotatedVerseActivity<Highlight, HighlightsViewModel>(R.string.title_highlights) {
    override val viewModel: HighlightsViewModel by viewModels()
}
