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

package com.github.danzuep.usfmbible.reading.chapter

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.view.ViewGroup
import android.widget.TextView
import com.github.danzuep.usfmbible.R
import com.github.danzuep.usfmbible.utils.localized
import android.widget.ExpandableListView
import androidx.annotation.IntDef
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.danzuep.usfmbible.core.Bible
import com.github.danzuep.usfmbible.databinding.InnerChapterSelectionViewBinding

class ChapterSelectionView : ConstraintLayout {
    companion object {
        private const val SELECTION_OLD_TESTAMENT = 1
        private const val SELECTION_NEW_TESTAMENT = 2

        @IntDef(SELECTION_OLD_TESTAMENT, SELECTION_NEW_TESTAMENT)
        @Retention(AnnotationRetention.SOURCE)
        private annotation class Selection

        /** The testament tabs are only useful when both testaments have at least one valid book. */
        internal fun testamentTabVisibility(bookIndices: Collection<Int>): TestamentTabVisibility {
            val validBookIndices = bookIndices.filter { it in 0 until Bible.BOOK_COUNT }
            return TestamentTabVisibility(
                showOldTestament = validBookIndices.any { it < Bible.OLD_TESTAMENT_COUNT },
                showNewTestament = validBookIndices.any { it >= Bible.OLD_TESTAMENT_COUNT })
        }
    }

    internal data class TestamentTabVisibility(val showOldTestament: Boolean, val showNewTestament: Boolean)

    sealed class ViewEvent {
        data class SelectBook(val bookIndex: Int) : ViewEvent()
        data class SelectChapter(val bookIndex: Int, val chapterIndex: Int) : ViewEvent()
        data class SelectVerse(val bookIndex: Int, val chapterIndex: Int, val verseIndex: Int) : ViewEvent()
        /** A chapter was tapped, so its verses can be offered; answer with [showVerses]. */
        data class OpenVerses(val bookIndex: Int, val chapterIndex: Int) : ViewEvent()
    }

    private var onViewEvent: ((ViewEvent) -> Unit)? = null
    private var versesBookIndex = -1
    private var bookNames = emptyMap<Int, String>()
    private var showTestamentTabs = true

    private val viewBinding = InnerChapterSelectionViewBinding.inflate(LayoutInflater.from(context), this)

    @Selection
    private var selection = SELECTION_OLD_TESTAMENT

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        setBackgroundColor(Color.BLACK)

        viewBinding.chapterSelectionOldTestament.setOnClickListener { updateSelection(SELECTION_OLD_TESTAMENT, true) }
        viewBinding.chapterSelectionNewTestament.setOnClickListener { updateSelection(SELECTION_NEW_TESTAMENT, true) }
        viewBinding.chapterListView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}

            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                val firstVisibleGroup = ExpandableListView.getPackedPositionGroup(
                    viewBinding.chapterListView.getExpandableListPosition(firstVisibleItem))
                updateSelection(if (firstVisibleGroup < Bible.OLD_TESTAMENT_COUNT) SELECTION_OLD_TESTAMENT else SELECTION_NEW_TESTAMENT, false)
            }
        })
        viewBinding.verseSelectionBook.setOnClickListener { hideVerses() }
        updateSelection(SELECTION_OLD_TESTAMENT, false)
    }

    /** Narrows the chapters to a column and fills the rest with the verses of [chapterIndex], as "chapter:verse". */
    fun showVerses(bookIndex: Int, chapterIndex: Int, verseCount: Int) {
        if (verseCount <= 0) return
        if (versesBookIndex != bookIndex) {
            versesBookIndex = bookIndex
            viewBinding.verseSelectionChapters.adapter = NumberAdapter(context, Bible.getChapterCount(bookIndex)) { chapter ->
                onViewEvent?.invoke(ViewEvent.OpenVerses(bookIndex, chapter))
            }
        }
        viewBinding.verseSelectionBook.text = bookNames[bookIndex].orEmpty()
        (viewBinding.verseSelectionChapters.adapter as NumberAdapter).selectedIndex = chapterIndex
        viewBinding.verseSelectionChapters.setSelection(chapterIndex)
        viewBinding.verseSelectionVerses.adapter = NumberAdapter(context, verseCount) { verse ->
            hideVerses()
            onViewEvent?.invoke(ViewEvent.SelectVerse(bookIndex, chapterIndex, verse))
        }
        viewBinding.verseSelectionBook.visibility = View.VISIBLE
        viewBinding.verseSelectionPanel.visibility = View.VISIBLE
        applyTestamentTabs()
    }

    fun hideVerses() {
        viewBinding.verseSelectionBook.visibility = View.GONE
        viewBinding.verseSelectionPanel.visibility = View.GONE
        applyTestamentTabs()
    }

    fun isShowingVerses(): Boolean = viewBinding.verseSelectionPanel.visibility == View.VISIBLE

    private fun updateSelection(@Selection newSelection: Int, scrollToFirstItem: Boolean) {
        selection = newSelection
        when (selection) {
            SELECTION_OLD_TESTAMENT -> {
                viewBinding.chapterSelectionOldTestament.isSelected = true
                viewBinding.chapterSelectionNewTestament.isSelected = false
                if (scrollToFirstItem) viewBinding.chapterListView.scrollToFirstBookFrom(0)
            }
            SELECTION_NEW_TESTAMENT -> {
                viewBinding.chapterSelectionOldTestament.isSelected = false
                viewBinding.chapterSelectionNewTestament.isSelected = true
                if (scrollToFirstItem) viewBinding.chapterListView.scrollToFirstBookFrom(Bible.OLD_TESTAMENT_COUNT)
            }
            else -> throw IllegalArgumentException("Unsupported selection type - $selection")
        }
    }

    fun initialize(onViewEvent: (ViewEvent) -> Unit) {
        this.onViewEvent = onViewEvent
        viewBinding.chapterListView.initialize(onViewEvent)
    }

    fun setCurrentChapter(currentBookIndex: Int, currentChapterIndex: Int) {
        viewBinding.chapterListView.setCurrentChapter(currentBookIndex = currentBookIndex, currentChapterIndex = currentChapterIndex)
    }

    fun setChapterSelectionItems(chapterSelectionItems: List<ChapterSelectionItem>) {
        bookNames = chapterSelectionItems.associate { it.bookIndex to it.bookName }
        viewBinding.chapterListView.setChapterSelectionItems(chapterSelectionItems = chapterSelectionItems)
        updateTestamentTabs(testamentTabVisibility(chapterSelectionItems.map { it.bookIndex }))
    }

    private fun updateTestamentTabs(visibility: TestamentTabVisibility) {
        showTestamentTabs = visibility.showOldTestament && visibility.showNewTestament
        applyTestamentTabs()
    }

    /** The book name takes the place of the tabs while the verses are shown. */
    private fun applyTestamentTabs() {
        val visible = showTestamentTabs && !isShowingVerses()
        val tabVisibility = if (visible) View.VISIBLE else View.GONE
        viewBinding.chapterSelectionOldTestament.visibility = tabVisibility
        viewBinding.chapterSelectionNewTestament.visibility = tabVisibility
        viewBinding.chapterSelectionOldTestament.isEnabled = visible
        viewBinding.chapterSelectionNewTestament.isEnabled = visible
    }

    fun expandCurrentBook() {
        viewBinding.chapterListView.expandCurrentBook()
    }
}

/** Numbers 1..[count], reporting the 0-based index of the one clicked. */
private class NumberAdapter(private val context: Context, private val count: Int, private val onClick: (Int) -> Unit) : BaseAdapter() {
    var selectedIndex = -1
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int = count

    override fun getItem(position: Int): Int = position

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = (convertView as? TextView)
            ?: LayoutInflater.from(context).inflate(R.layout.item_number, parent, false) as TextView
        view.text = (position + 1).localized()
        view.isSelected = position == selectedIndex
        view.setOnClickListener { onClick(position) }
        return view
    }
}
