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

package com.github.danzuep.usfmbible.core.repository

import com.github.danzuep.usfmbible.core.Verse
import com.github.danzuep.usfmbible.core.VerseIndex
import com.github.danzuep.usfmbible.core.VerseSearchQuery
import com.github.danzuep.usfmbible.core.repository.local.LocalReadingStorage
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleReadingRepositoryTimelineTest {
    private class InMemoryReading(var verseIndex: VerseIndex) : LocalReadingStorage {
        var timeline = emptyList<VerseIndex>()
        override suspend fun readCurrentVerseIndex() = verseIndex
        override suspend fun saveCurrentVerseIndex(verseIndex: VerseIndex) { this.verseIndex = verseIndex }
        override suspend fun readTimeline() = timeline
        override suspend fun saveTimeline(timeline: List<VerseIndex>) { this.timeline = timeline }
        override suspend fun readTimelineLimit() = 50
        override suspend fun saveTimelineLimit(limit: Int) {}
        override suspend fun readCurrentTranslation() = ""
        override suspend fun saveCurrentTranslation(translationShortName: String) {}
        override suspend fun readParallelTranslations() = emptyList<String>()
        override suspend fun saveParallelTranslations(parallelTranslations: List<String>) {}
        override suspend fun readBookNames(translationShortName: String) = emptyList<String>()
        override suspend fun readBookShortNames(translationShortName: String) = emptyList<String>()
        override suspend fun readBookIndicesWithVerses(translationShortName: String) = emptySet<Int>()
        override suspend fun readVerses(translationShortName: String, bookIndex: Int, chapterIndex: Int) = emptyList<Verse>()
        override suspend fun readVerses(translationShortName: String, parallelTranslations: List<String>, bookIndex: Int, chapterIndex: Int) = emptyList<Verse>()
        override suspend fun readVerses(translationShortName: String, verseIndexes: List<VerseIndex>) = emptyMap<VerseIndex, Verse>()
        override suspend fun search(query: VerseSearchQuery) = emptyList<Verse>()
    }

    private fun TestScope.repository(storage: InMemoryReading) =
        BibleReadingRepository(storage, TestScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun onlyTheStartAndFinalMenuSelectionAreRecordedOnceTheMenuCloses() = runTest {
        val storage = InMemoryReading(VerseIndex(0, 0, 0))
        val repository = repository(storage)

        repository.beginBrowsing()
        repository.saveCurrentVerseIndex(VerseIndex(42, 0, 0))
        repository.saveCurrentVerseIndex(VerseIndex(42, 2, 0))
        repository.saveCurrentVerseIndex(VerseIndex(43, 0, 0))
        repository.saveCurrentVerseIndex(VerseIndex(43, 4, 0))
        repository.saveCurrentVerseIndex(VerseIndex(43, 4, 7))
        assertEquals(emptyList(), repository.readTimeline())

        repository.endBrowsing()
        assertEquals(listOf(VerseIndex(43, 4, 7), VerseIndex(0, 0, 0)), repository.readTimeline())
    }

    @Test
    fun closingTheMenuWithoutMovingRecordsNothing() = runTest {
        val storage = InMemoryReading(VerseIndex(0, 0, 0))
        val repository = repository(storage)

        repository.beginBrowsing()
        repository.saveCurrentVerseIndex(VerseIndex(42, 0, 0))
        repository.saveCurrentVerseIndex(VerseIndex(0, 0, 0))
        repository.endBrowsing()

        assertEquals(emptyList(), repository.readTimeline())
    }

    @Test
    fun readingJumpsAreRecordedAgainAfterTheMenuCloses() = runTest {
        val storage = InMemoryReading(VerseIndex(0, 0, 0))
        val repository = repository(storage)

        repository.beginBrowsing()
        repository.saveCurrentVerseIndex(VerseIndex(42, 0, 0))
        repository.endBrowsing()
        repository.saveCurrentVerseIndex(VerseIndex(42, 1, 0))

        assertEquals(listOf(VerseIndex(42, 0, 0), VerseIndex(0, 0, 0)), repository.readTimeline())
    }
}
