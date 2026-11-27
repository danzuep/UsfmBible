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

package io.github.danzuep.usfmbible.reading

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ExpandableListView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.danzuep.usfmbible.DebugEntryPoints
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.core.repository.local.android.AndroidTranslationStorage
import io.github.danzuep.usfmbible.core.repository.local.android.db.AndroidDatabase
import io.github.danzuep.usfmbible.reading.chapter.ChapterListView
import io.github.danzuep.usfmbible.translations.TranslationsActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/** Opens the book list in the drawer on a whole-Bible translation and picks a book further down, as a user would. */
class ChapterDrawerTest {
    private companion object {
        const val JOHN = 42
        const val TRANSLATION = "WEBF"
        const val TIMEOUT_MS = 120_000L
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                screenshot("drawer-timeout")
                fail("Timed out waiting for $what")
            }
            Thread.sleep(250)
        }
    }

    private fun screenshot(name: String) {
        automation.executeShellCommand("mkdir -p /sdcard/Download/shots").close()
        automation.executeShellCommand("screencap -p /sdcard/Download/shots/$name.png").use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun installed() = runBlocking {
        AndroidTranslationStorage(AndroidDatabase(ApplicationProvider.getApplicationContext<Application>()), context)
            .readTranslations().filter { it.downloaded }.map { it.shortName }
    }

    private fun clickNode(text: String): Boolean {
        fun AccessibilityNodeInfo.find(): AccessibilityNodeInfo? =
            if (this.text?.toString() == text) this else (0 until childCount).firstNotNullOfOrNull { getChild(it)?.find() }
        return automation.rootInActiveWindow?.find()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun isShown(text: String): Boolean {
        fun AccessibilityNodeInfo.contains(): Boolean =
            this.text?.contains(text) == true || (0 until childCount).any { getChild(it)?.contains() == true }
        return automation.rootInActiveWindow?.contains() == true
    }

    private fun installWholeBible() {
        val bible = InstrumentationRegistry.getInstrumentation().context.assets.open("$TRANSLATION.zip").use { it.readBytes() }
        val file = File(context.filesDir, "$TRANSLATION.zip").apply { writeBytes(bible) }
        ActivityScenario.launch<TranslationsActivity>(Intent(Intent.ACTION_VIEW, Uri.fromFile(file), context, TranslationsActivity::class.java)).use {
            await("the import dialog") { isShown("Upload a Bible") }
            assertTrue(clickNode("OK") || run { Thread.sleep(1_000); clickNode("OK") }, "no OK button")
            await("$TRANSLATION to be installed") { TRANSLATION in installed() }
        }
    }

    @Test
    fun pickingJohnFromGenesisKeepsJohnInView() {
        if (TRANSLATION !in installed()) installWholeBible()
        runBlocking { EntryPointAccessors.fromApplication(context, DebugEntryPoints::class.java).bibleReadingRepository().saveCurrentTranslation(TRANSLATION) }

        val intent = Intent(context, ReadingActivity::class.java).putExtra(ReadingActivity.KEY_OPEN_BOOK, 0)
        ActivityScenario.launch<ReadingActivity>(intent).use { scenario ->
            await("Genesis in the reader") { isShown("In the beginning") }
            scenario.onActivity { it.findViewById<DrawerLayout>(R.id.drawer_layout).openDrawer(GravityCompat.START) }
            lateinit var list: ChapterListView
            scenario.onActivity { list = it.findViewById(R.id.chapter_list_view) }
            await("the whole book list") { var count = 0; scenario.onActivity { count = list.expandableListAdapter?.groupCount ?: 0 }; count > JOHN }

            // Scroll down until John's row is on screen, then tap it.
            scenario.onActivity {
                list.setSelectionFromTop(list.getFlatListPosition(ExpandableListView.getPackedPositionForGroup(JOHN - 3)), 0)
            }
            await("John's row") { Thread.sleep(500); isShown("John") }
            screenshot("drawer-before-john")
            assertTrue(clickNode("John"), "no John row to click")
            Thread.sleep(3_000)
            screenshot("drawer-after-john")

            var first = -1
            var last = -1
            var johnFlat = -1
            scenario.onActivity {
                johnFlat = list.getFlatListPosition(ExpandableListView.getPackedPositionForGroup(JOHN))
                first = list.firstVisiblePosition
                last = list.lastVisiblePosition
            }
            assertTrue(johnFlat in first..last && johnFlat - first <= 3, "John (row $johnFlat) should be near the top, but rows $first..$last are shown")
        }
    }
}
