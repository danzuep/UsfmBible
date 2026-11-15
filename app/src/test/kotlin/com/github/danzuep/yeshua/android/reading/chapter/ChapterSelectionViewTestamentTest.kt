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

package com.github.danzuep.yeshua.android.reading.chapter

import com.github.danzuep.yeshua.android.core.Bible
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterSelectionViewTestamentTest {
    @Test
    fun testTestamentTabVisibility_allBooks() {
        assertEquals(
            ChapterSelectionView.TestamentTabVisibility(showOldTestament = true, showNewTestament = true),
            ChapterSelectionView.testamentTabVisibility((0 until Bible.CANON_BOOK_COUNT).toList())
        )
    }

    @Test
    fun testTestamentTabVisibility_onlyOldTestament() {
        assertEquals(
            ChapterSelectionView.TestamentTabVisibility(showOldTestament = true, showNewTestament = false),
            ChapterSelectionView.testamentTabVisibility((0 until 39).toList())
        )
    }

    @Test
    fun testTestamentTabVisibility_onlyNewTestament() {
        assertEquals(
            ChapterSelectionView.TestamentTabVisibility(showOldTestament = false, showNewTestament = true),
            ChapterSelectionView.testamentTabVisibility((39 until Bible.CANON_BOOK_COUNT).toList())
        )
    }

    @Test
    fun testTestamentTabVisibility_noBooks() {
        assertEquals(
            ChapterSelectionView.TestamentTabVisibility(showOldTestament = false, showNewTestament = false),
            ChapterSelectionView.testamentTabVisibility(emptyList())
        )
    }

    @Test
    fun testTestamentTabVisibility_invalidIndicesIgnored() {
        assertEquals(
            ChapterSelectionView.TestamentTabVisibility(showOldTestament = false, showNewTestament = false),
            ChapterSelectionView.testamentTabVisibility(listOf(-1, Bible.BOOK_COUNT, 100))
        )
        assertEquals(
            ChapterSelectionView.TestamentTabVisibility(showOldTestament = false, showNewTestament = true),
            ChapterSelectionView.testamentTabVisibility(listOf(-1, 39, 100))
        )
    }

    @Test
    fun testTestamentTabVisibility_deuterocanon() {
        val visibility = ChapterSelectionView.testamentTabVisibility((0 until Bible.BOOK_COUNT).toList())
        assertEquals(ChapterSelectionView.TestamentTabVisibility(true, true, true), visibility)
        assertEquals(true, visibility.showTabs)
        assertEquals(false, ChapterSelectionView.testamentTabVisibility((Bible.CANON_BOOK_COUNT until Bible.BOOK_COUNT).toList()).showTabs)
    }
}
