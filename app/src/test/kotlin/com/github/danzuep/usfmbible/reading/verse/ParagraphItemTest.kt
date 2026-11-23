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

package com.github.danzuep.usfmbible.reading.verse

import android.text.Spanned
import android.text.style.ImageSpan
import com.github.danzuep.usfmbible.core.Bookmark
import com.github.danzuep.usfmbible.core.RUBY_MARK
import com.github.danzuep.usfmbible.core.RUBY_SEPARATOR
import com.github.danzuep.usfmbible.core.Verse
import com.github.danzuep.usfmbible.core.VerseIndex
import com.github.danzuep.usfmbible.reading.VerseUpdate
import com.github.danzuep.usfmbible.tests.BaseUnitTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ParagraphItemTest : BaseUnitTest() {
    private val context = RuntimeEnvironment.getApplication()

    private val verses = (0..3).map { Verse(VerseIndex(57, 0, it), Verse.Text("T", "Verse $it"), emptyList()) }

    private fun paragraph(bookmarks: List<Bookmark> = emptyList()) =
            verses.toParagraphItems(context, bookmarks, emptyList(), emptyList()).single() as ParagraphItem

    private fun bookmarkCount(item: ParagraphItem): Int {
        val text = item.textForDisplay() as Spanned
        return text.getSpans(0, text.length, LabelWithIconSpan::class.java).size
    }

    private fun noteCount(item: ParagraphItem): Int {
        val text = item.textForDisplay() as Spanned
        return text.getSpans(0, text.length, ImageSpan::class.java).size
    }

    @Test
    fun `verse starts identify the rendered segment within a paragraph`() {
        val item = paragraph()
        val text = item.textForDisplay().toString()
        val offset = item.verseStart(verses[2].verseIndex)!!
        assertEquals(verses[2], item.verseAt(offset))
        assertTrue(text.substring(offset).contains("Verse 2"))
        assertEquals(null, item.verseStart(VerseIndex(57, 0, 9)))
        assertEquals(null, item.verseEnd(VerseIndex(57, 0, 9)))
        assertTrue(item.verseEnd(verses[2].verseIndex)!! > offset)
    }

    @Test
    fun `following a verse only scrolls when its line is outside the viewport`() {
        assertEquals(0, verseRevealScroll(30, 50, 20, 200))
        assertEquals(-10, verseRevealScroll(10, 30, 20, 200))
        assertEquals(170, verseRevealScroll(190, 210, 20, 200))
    }

    @Test
    fun `a verse taller than the viewport is pinned to the top without repeated scrolling`() {
        assertEquals(80, verseRevealScroll(100, 400, 20, 150))
        assertEquals(0, verseRevealScroll(20, 320, 20, 150))
        assertEquals(-30, verseRevealScroll(-10, 290, 20, 150))
    }

    @Test
    fun `test bookmark is added and removed live`() {
        val item = paragraph()
        assertEquals(0, bookmarkCount(item))

        item.update(VerseUpdate(VerseIndex(57, 0, 2), VerseUpdate.BOOKMARK_ADDED))
        assertEquals(1, bookmarkCount(item))

        item.update(VerseUpdate(VerseIndex(57, 0, 2), VerseUpdate.BOOKMARK_REMOVED))
        assertEquals(0, bookmarkCount(item))
    }

    @Test
    fun `test bookmark renders for saved state when chapter is loaded`() {
        assertEquals(1, bookmarkCount(paragraph(listOf(Bookmark(VerseIndex(57, 0, 2), 0L)))))
    }

    @Test
    fun `test note icon is an end of verse image span but the bookmark is not`() {
        val item = paragraph(listOf(Bookmark(VerseIndex(57, 0, 2), 0L)))
        assertEquals(0, noteCount(item))
        item.update(VerseUpdate(VerseIndex(57, 0, 2), VerseUpdate.NOTE_ADDED))
        assertEquals(1, noteCount(item))
    }

    @Test
    fun `test ruby is drawn above its base only when enabled`() {
        val rubyVerses = listOf(Verse(VerseIndex(57, 0, 0), Verse.Text("T", "A ${RUBY_MARK}神${RUBY_SEPARATOR}かみ${RUBY_MARK} B"), emptyList()))
        val item = rubyVerses.toParagraphItems(context, emptyList(), emptyList(), emptyList()).single() as ParagraphItem

        val on = item.textForDisplay(rubyOn = true) as Spanned
        assertEquals(1, on.getSpans(0, on.length, RubySpan::class.java).size)
        assertTrue(on.endsWith("A 神 B"))

        val off = item.textForDisplay(rubyOn = false) as Spanned
        assertEquals(0, off.getSpans(0, off.length, RubySpan::class.java).size)
        assertTrue(off.endsWith("A 神 B"))
    }
}
