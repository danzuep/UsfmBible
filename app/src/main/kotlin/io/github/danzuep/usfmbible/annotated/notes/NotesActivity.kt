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

package io.github.danzuep.usfmbible.annotated.notes

import android.app.Application
import android.os.Bundle
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseItem
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseActivity
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseViewModel
import io.github.danzuep.usfmbible.core.BibleReadingManager
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.core.Note
import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.core.SettingsManager
import io.github.danzuep.usfmbible.core.VerseAnnotationManager
import io.github.danzuep.usfmbible.core.provider.TimeProvider
import io.github.danzuep.usfmbible.reading.ReadingActivity
import androidx.activity.viewModels
import io.github.danzuep.usfmbible.container

class NotesViewModel(
    bibleReadingManager: BibleReadingManager,
    notesManager: VerseAnnotationManager<Note>,
    settingsManager: SettingsManager,
    coroutineDispatcherProvider: CoroutineDispatcherProvider,
    timeProvider: TimeProvider,
    application: Application
) : AnnotatedVerseViewModel<Note>(
    bibleReadingManager = bibleReadingManager,
    verseAnnotationManager = notesManager,
    noItemText = R.string.text_no_notes,
    settingsManager = settingsManager,
    coroutineDispatcherProvider = coroutineDispatcherProvider,
    timeProvider = timeProvider,
    application = application
) {
    override fun buildAnnotatedVerseItem(
        settings: Settings,
        verseAnnotation: Note,
        bookName: String,
        bookShortName: String,
        verseText: String,
        sortOrder: Int
    ): AnnotatedVerseItem = AnnotatedVerseItem.Note(
        settings = settings,
        verseIndex = verseAnnotation.verseIndex,
        bookShortName = bookShortName,
        verseText = verseText,
        note = verseAnnotation.note
    )
}

class NotesActivity : AnnotatedVerseActivity<Note, NotesViewModel>(R.string.title_notes) {
    override val viewModel: NotesViewModel by viewModels { container.viewModelFactory }

    override fun extrasForOpeningVerse(): Bundle = ReadingActivity.bundleForOpenNote()
}
