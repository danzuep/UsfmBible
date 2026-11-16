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

package com.github.danzuep.yeshua.core

object Bible {
    /** The Protestant canon: [OLD_TESTAMENT_COUNT] + [NEW_TESTAMENT_COUNT] books. The deuterocanon comes after it, so these indexes never move. */
    const val CANON_BOOK_COUNT = 66
    const val BOOK_COUNT = CANON_BOOK_COUNT + 18
    const val OLD_TESTAMENT_COUNT = 39
    const val NEW_TESTAMENT_COUNT = 27

    private val CHAPTER_COUNT = intArrayOf(50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36,
            10, 13, 10, 42, 150, 31, 12, 8, 66, 52, 5, 48, 12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2, 14, 4,
            28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3, 1, 13, 5, 5, 3, 5, 1, 1, 1, 22,
            // Tobit, Judith, Esther (Greek), Wisdom, Sirach, Baruch, Letter of Jeremiah, Prayer of Azariah, Susanna, Bel and the Dragon,
            // 1-4 Maccabees, 1-2 Esdras, Prayer of Manasseh, Psalm 151 (the order of BOOK_CODES).
            14, 16, 10, 19, 51, 5, 1, 1, 1, 1, 16, 15, 7, 18, 9, 16, 1, 1)

    val TOTAL_CHAPTER_COUNT: Int = CHAPTER_COUNT.sum()

    fun getChapterCount(bookIndex: Int) = CHAPTER_COUNT[bookIndex]
}
