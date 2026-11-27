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

package io.github.danzuep.usfmbible.reading.speech

import io.github.danzuep.usfmbible.core.Bible
import io.github.danzuep.usfmbible.core.VerseIndex

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

/** Starts on submission as a fallback for engines that omit onStart, and corrects for startup delay when they do report it. */
internal class SpeechVerseTimer {
    private var verse = VerseIndex.INVALID
    private var startedAt: Long? = null
    private var confirmed = false

    fun reset() {
        verse = VerseIndex.INVALID
        startedAt = null
        confirmed = false
    }

    fun queued(index: VerseIndex, now: Long) {
        if (verse != index) {
            verse = index
            startedAt = now
            confirmed = false
        }
    }

    fun started(index: VerseIndex, now: Long) {
        if (verse != index) queued(index, now)
        if (!confirmed) {
            startedAt = now
            confirmed = true
        }
    }

    fun elapsed(index: VerseIndex, now: Long): Long? =
        startedAt?.takeIf { verse == index }?.let { (now - it).coerceAtLeast(0L) }

    fun fraction(index: VerseIndex, now: Long, words: Int, millisecondsPerWord: Float): Float {
        val elapsed = elapsed(index, now) ?: return 0f
        return (elapsed / (words * millisecondsPerWord)).coerceIn(0f, 1f)
    }
}

internal val speechPlaybackSpeeds = listOf(0.5f, 0.75f, 1f, 0.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f)
