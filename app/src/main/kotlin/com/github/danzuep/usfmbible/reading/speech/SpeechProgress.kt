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

import com.github.danzuep.usfmbible.core.Bible
import com.github.danzuep.usfmbible.core.VerseIndex

internal fun adjacentSpeechChapter(current: VerseIndex, direction: Int): VerseIndex? {
    if (!current.isValid() || direction !in listOf(-1, 1)) return null
    val book = current.bookIndex
    val chapter = current.chapterIndex
    return when {
        direction > 0 && chapter + 1 < Bible.getChapterCount(book) -> VerseIndex(book, chapter + 1, 0)
        direction > 0 && book + 1 < Bible.BOOK_COUNT -> VerseIndex(book + 1, 0, 0)
        direction < 0 && chapter > 0 -> VerseIndex(book, chapter - 1, 0)
        direction < 0 && book > 0 -> VerseIndex(book - 1, Bible.getChapterCount(book - 1) - 1, 0)
        else -> null
    }
}

internal fun chapterSpeechFraction(
    verses: List<VerseIndex>, words: Map<VerseIndex, Int>, current: VerseIndex, verseFraction: Float
): Float {
    val position = verses.indexOf(current)
    if (position < 0) return 0f
    val total = verses.sumOf { words[it] ?: 1 }.coerceAtLeast(1)
    val before = verses.take(position).sumOf { words[it] ?: 1 }
    return ((before + (words[current] ?: 1) * verseFraction.coerceIn(0f, 1f)) / total).coerceIn(0f, 1f)
}
