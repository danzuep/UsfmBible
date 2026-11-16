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

package com.github.danzuep.yeshua.android.translations

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.LabeledIntent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import com.github.danzuep.yeshua.android.core.repository.local.android.AndroidTranslationStorage
import com.github.danzuep.yeshua.android.core.repository.local.android.db.AndroidDatabase
import com.github.danzuep.yeshua.android.DebugEntryPoints
import com.github.danzuep.yeshua.android.reading.ReadingActivity
import com.github.danzuep.yeshua.android.ui.MarkdownActivity
import dagger.hilt.android.EntryPointAccessors
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Opens files the way another app would (a VIEW intent with the file's URI) and checks what the app does with them.
 * Screenshots go to /sdcard/Download/shots, where the workflow picks them up.
 */
class OpenFileTest {
    private companion object {
        const val DIALOG_TEXT = "Abbreviation, e.g. KJV"
        const val TIMEOUT_MS = 60_000L
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val repository get() = EntryPointAccessors.fromApplication(context, DebugEntryPoints::class.java).bibleReadingRepository()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun <T : Activity> open(activity: Class<T>, name: String, content: String): ActivityScenario<T> {
        val file = File(context.filesDir, name).apply { writeText(content) }
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, Uri.fromFile(file), context, activity))
    }

    /** Whether a node on screen has [text] as its text, description or hint (empty fields only expose their hint). */
    private fun isShown(text: String): Boolean {
        fun AccessibilityNodeInfo.contains(): Boolean =
            listOf(this.text, contentDescription, hintText).any { it?.contains(text) == true } ||
                (0 until childCount).any { getChild(it)?.contains() == true }
        return automation.rootInActiveWindow?.contains() == true
    }

    private fun click(text: String) {
        fun AccessibilityNodeInfo.find(): AccessibilityNodeInfo? =
            if (this.text?.toString().equals(text, ignoreCase = true)) this else (0 until childCount).firstNotNullOfOrNull { getChild(it)?.find() }
        assertTrue(automation.rootInActiveWindow?.find()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true, "no \"$text\" button to click")
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < deadline, "Timed out waiting for ")
            Thread.sleep(250)
        }
    }

    private fun screenshot(name: String) {
        automation.executeShellCommand("mkdir -p /sdcard/Download/shots").close()
        automation.executeShellCommand("screencap -p /sdcard/Download/shots/$name.png").use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun installed() = runBlocking {
        AndroidTranslationStorage(AndroidDatabase(ApplicationProvider.getApplicationContext<Application>()), context)
            .readTranslations().filter { it.downloaded }.associateBy { it.shortName }
    }

    @Test
    fun usfmFileThatNamesItsTranslationIsInstalledWithoutTheDialog() {
        open(TranslationsActivity::class.java, "direct.usfm", "\\id GEN EN_ULT en_English_ltr\n\\h Direct\n\\c 1\n\\p\n\\v 1 Direct verse\n").use {
            await("the translation to be installed") { "ULT" in installed() }
            assertEquals("en", installed().getValue("ULT").language)
            assertFalse(isShown(DIALOG_TEXT), "the import dialog was shown")
            screenshot("usfm-direct")
        }
    }

    @Test
    fun usfmFileWithoutMetadataAsksForIt() {
        open(TranslationsActivity::class.java, "plain.usfm", "\\id GEN\n\\h Plain\n\\c 1\n\\p\n\\v 1 Plain verse\n").use {
            await("the import dialog") { isShown(DIALOG_TEXT) }
            screenshot("usfm-dialog")
        }
    }

    @Test
    fun markdownFileIsShownInADialog() {
        open(MarkdownActivity::class.java, "licence.md", "# Smoke licence\n\nPermission is granted.\n").use {
            await("the licence text") { isShown("Permission is granted.") }
            screenshot("markdown")
        }
    }

    /** The app's activities that "Open with" offers for a VIEW intent; more than one makes the app appear twice in the chooser. */
    private fun offeredFor(name: String, mimeType: String?): List<String> {
        val uri = Uri.fromFile(File(context.filesDir, name))
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name.substringAfterLast('.') }
    }

    @Test
    fun markdownIsOfferedOnceAsMarkdown() {
        assertEquals(listOf("MarkdownActivity"), offeredFor("a.md", "text/markdown"))
    }

    @Test
    fun usfmAndZipAreOfferedOnceAsTranslations() {
        for ((name, type) in listOf("a.usfm" to "application/octet-stream", "a.usfm" to "text/plain", "a.sfm" to "text/x-usfm", "a.zip" to "application/zip")) {
            assertEquals(listOf("TranslationsActivity"), offeredFor(name, type), "$name as $type")
        }
    }

    @Test
    fun chooserForUsfmListsThisApp() {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://example.files/books/a.usfm"), "application/octet-stream")
        // The emulator has no other USFM handler and a lone match skips the chooser, so add a stand-in second choice.
        val other = LabeledIntent(Intent(Settings.ACTION_SETTINGS), "android", "Another app", 0)
        val chooser = Intent.createChooser(view, "Open with").putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(other)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        Thread.sleep(8_000)
        screenshot("usfm-open-with")
        await("this app in the chooser") { isShown(label) }
        automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
    }

    @Test
    fun realHaggaiFileInstallsAndOpensInTheReader() {
        val haggai = InstrumentationRegistry.getInstrumentation().context.assets.open("HAG.usfm").use { it.readBytes().decodeToString() }
        // A real eBible book has no translation in its \id line, so the dialog asks for it (pre-filled from the file name).
        open(TranslationsActivity::class.java, "HAG.usfm", haggai).use {
            await("the import dialog") { isShown(DIALOG_TEXT) }
            click("OK")
            await("HAG to be installed or an error") { "HAG" in installed() || isShown("Failed") || isShown("Error") }
            screenshot("hag-imported")
            assertTrue("HAG" in installed(), "the import failed")
        }
        val shortName = installed().keys.single { it == "HAG" }
        runBlocking { repository.saveCurrentTranslation(shortName) }
        ActivityScenario.launch<ReadingActivity>(Intent(context, ReadingActivity::class.java).putExtra(ReadingActivity.KEY_OPEN_BOOK, 36)).use {
            await("Haggai 1 in the reader") { isShown("Haggai") || isShown("second year") }
            screenshot("hag-reader")
        }
    }

    @Test
    fun failedImportShowsSelectableTechnicalDetails() {
        val usfm = "\\id GEN EN_DUP en_English_ltr\n\\h Dup\n\\c 1\n\\p\n\\v 1 Dup verse\n"
        open(TranslationsActivity::class.java, "dup.usfm", usfm).use { await("DUP to be installed") { "DUP" in installed() } }
        // Importing the same abbreviation again fails.
        open(TranslationsActivity::class.java, "dup2.usfm", usfm).use {
            await("the error popup") { isShown("Technical details") }
            assertFalse(isShown("already installed"), "the details should start collapsed")
            click("Technical details")
            await("the stack trace") { isShown("already installed") }
            screenshot("import-error-details")
        }
    }
}
