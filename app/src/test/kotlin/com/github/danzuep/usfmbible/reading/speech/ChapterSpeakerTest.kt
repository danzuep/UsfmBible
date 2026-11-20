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
import androidx.test.core.app.ApplicationProvider
import com.github.danzuep.usfmbible.core.VerseIndex
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
