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

import android.view.ViewGroup
import android.widget.ExpandableListView
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.github.danzuep.yeshua.android.core.Bible
import com.github.danzuep.yeshua.android.reading.ReadingActivity
import org.junit.Test
import kotlin.test.assertTrue

/** Reproduces the book list jumping past the picked book when a book is chosen while another one is open. */
class ChapterListViewTest {
    private companion object {
        const val JOHN = 42
    }

    @Test
    fun pickingJohnFromGenesisKeepsJohnInView() {
        ActivityScenario.launch(ReadingActivity::class.java).use { scenario ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            lateinit var list: ChapterListView
            val events = mutableListOf<ChapterSelectionView.ViewEvent>()
            scenario.onActivity { activity ->
                list = ChapterListView(activity)
                list.initialize { event ->
                    events += event
                    // Like the view model: the picked book becomes the current one.
                    if (event is ChapterSelectionView.ViewEvent.SelectBook) {
                        list.post {
                            list.setCurrentChapter(event.bookIndex, 0)
                            list.expandCurrentBook()
                        }
                    }
                }
                list.setChapterSelectionItems(
                    (0 until Bible.BOOK_COUNT).map { ChapterSelectionItem(it, "Book $it", Bible.getChapterCount(it)) })
                list.setCurrentChapter(0, 0)
                list.expandCurrentBook()
                activity.addContentView(
                    FrameLayout(activity).apply { addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) },
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            instrumentation.waitForIdleSync()

            // Scroll down until John's row is on screen, as a user would, then tap it.
            scenario.onActivity {
                val johnFlat = list.getFlatListPosition(ExpandableListView.getPackedPositionForGroup(JOHN - 1))
                list.setSelectionFromTop(johnFlat, 0)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val johnFlat = list.getFlatListPosition(ExpandableListView.getPackedPositionForGroup(JOHN))
                val row = list.getChildAt(johnFlat - list.firstVisiblePosition)
                list.performItemClick(row, johnFlat, list.getItemIdAtPosition(johnFlat))
            }
            Thread.sleep(1_500)
            instrumentation.waitForIdleSync()

            scenario.onActivity {
                val johnFlat = list.getFlatListPosition(ExpandableListView.getPackedPositionForGroup(JOHN))
                assertTrue(events.any { it is ChapterSelectionView.ViewEvent.SelectBook }, "John was not selected")
                assertTrue(
                    johnFlat in list.firstVisiblePosition..list.lastVisiblePosition && johnFlat - list.firstVisiblePosition <= 2,
                    "John (flat $johnFlat) should be at the top of the list but visible rows are ${list.firstVisiblePosition}..${list.lastVisiblePosition}")
            }
        }
    }
}
