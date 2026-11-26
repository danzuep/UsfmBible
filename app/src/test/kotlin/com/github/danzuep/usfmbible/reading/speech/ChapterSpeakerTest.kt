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

import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.github.danzuep.usfmbible.core.VerseIndex
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class ChapterSpeakerTest {
    @Test
    fun `higher rates are persisted and unsupported rates are rejected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val listener = object : ChapterSpeaker.Listener {
            override fun onVerseQueued(verseIndex: VerseIndex) = Unit
            override fun onVerseStarted(verseIndex: VerseIndex) = Unit
            override fun onVerseFinished(verseIndex: VerseIndex) = Unit
            override fun onChapterFinished() = Unit
            override fun onLanguageUnavailable(language: String) = Unit
            override fun onError() = Unit
        }
        val first = ChapterSpeaker(context, listener)
        val second = ChapterSpeaker(context, listener)
        try {
            first.playbackSpeed = 4f
            assertEquals(4f, second.playbackSpeed)
            second.playbackSpeed = 5f
            assertEquals(5f, first.playbackSpeed)
            assertFailsWith<IllegalArgumentException> { first.playbackSpeed = 6f }
        } finally {
            first.shutdown()
            second.shutdown()
            context.getSharedPreferences("speech", Context.MODE_PRIVATE).edit().remove("playback_speed_v2").commit()
        }
    }

    @Test
    fun `verses without punctuation between them are spoken as one utterance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<String>()
        val listener = object : ChapterSpeaker.Listener {
            override fun onVerseQueued(verseIndex: VerseIndex) { events += "queued ${verseIndex.verseIndex}" }
            override fun onVerseStarted(verseIndex: VerseIndex) { events += "started ${verseIndex.verseIndex}" }
            override fun onVerseFinished(verseIndex: VerseIndex) { events += "finished ${verseIndex.verseIndex}" }
            override fun onChapterFinished() { events += "chapter" }
            override fun onLanguageUnavailable(language: String) = Unit
            override fun onError() { events += "error" }
        }
        ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH)
        val speaker = ChapterSpeaker(context, listener)
        try {
            speaker.selectedLanguage = "en"
            val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)
            shadowOf(Looper.getMainLooper()).idle()
            // Genesis 23:17-18 (WEB) has no punctuation between the verses, and 23:16 ends a sentence.
            val genesis = { verse: Int -> VerseIndex(0, 22, verse - 1) }
            val verse17 = "So the field of Ephron, which was in Machpelah, which was before Mamre, the field, the cave"
            val verse18 = "and all the trees that were in the field, that were in all of its borders, were deeded"
            speaker.speak(
                listOf(
                    genesis(16) to "Abraham listened to Ephron, and weighed the silver.",
                    genesis(17) to verse17,
                    genesis(18) to verse18
                ),
                "en"
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("Abraham listened to Ephron, and weighed the silver.", "$verse17 $verse18"), engine.spokenTextList)
            // The shadow engine starts both queued utterances before finishing the first, so only check each verse is reported.
            listOf(15, 16, 17).forEach { assertEquals(true, "started $it" in events && "finished $it" in events, "verse $it") }
            assertEquals("chapter", events.last())
        } finally {
            speaker.shutdown()
            context.getSharedPreferences("speech", Context.MODE_PRIVATE).edit().remove("language").commit()
        }
    }
}
