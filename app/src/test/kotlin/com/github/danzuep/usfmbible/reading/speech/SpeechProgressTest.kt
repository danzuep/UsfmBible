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

package com.github.danzuep.usfmbible.reading.speech

import com.github.danzuep.usfmbible.core.VerseIndex
import com.github.danzuep.usfmbible.core.Bible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechProgressTest {
    @Test
    fun `chapter navigation crosses books and stops at bible boundaries`() {
        assertEquals(VerseIndex(0, 1, 0), adjacentSpeechChapter(VerseIndex(0, 0, 8), 1))
        assertEquals(VerseIndex(1, 0, 0), adjacentSpeechChapter(VerseIndex(0, 49, 3), 1))
        assertEquals(VerseIndex(0, 49, 0), adjacentSpeechChapter(VerseIndex(1, 0, 5), -1))
        assertNull(adjacentSpeechChapter(VerseIndex(0, 0, 0), -1))
        val lastBook = Bible.BOOK_COUNT - 1
        assertNull(adjacentSpeechChapter(VerseIndex(lastBook, Bible.getChapterCount(lastBook) - 1, 0), 1))
        assertNull(adjacentSpeechChapter(VerseIndex.INVALID, 1))
    }

    @Test
    fun `progress is chapter relative weighted and bounded`() {
        val verses = (0..2).map { VerseIndex(42, 0, it) }
        val words = mapOf(verses[0] to 2, verses[1] to 6, verses[2] to 2)
        assertEquals(0.5f, chapterSpeechFraction(verses, words, verses[1], 0.5f))
        assertEquals(0.8f, chapterSpeechFraction(verses, words, verses[1], 4f))
        assertEquals(0f, chapterSpeechFraction(verses, words, VerseIndex(42, 1, 0), 0.5f))
        assertEquals(0f, chapterSpeechFraction(emptyList(), emptyMap(), verses[0], 0f))
    }
}
