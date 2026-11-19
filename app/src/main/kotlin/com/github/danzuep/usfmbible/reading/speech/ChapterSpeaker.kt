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
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.github.danzuep.usfmbible.core.Bible
import com.github.danzuep.usfmbible.core.VerseIndex
import com.github.danzuep.usfmbible.ui.languageMatchesLocale
import java.util.Locale

/** Reads chapters aloud with the built-in Android text to speech, verse by verse. */
class ChapterSpeaker(context: Context, private val listener: Listener) {
    interface Listener {
        fun onVerseStarted(verseIndex: VerseIndex)
        fun onVerseFinished(verseIndex: VerseIndex)
        fun onChapterFinished()
        /** No installed voice can speak [language], the language of the translation or the one the user selected. */
        fun onLanguageUnavailable(language: String)
        fun onError()
    }

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var failed = false
    private val whenReady = mutableListOf<() -> Unit>()
    private var generation = 0
    private var pendingSegments = emptyList<QueuedSegment>()
    private var nextSegmentIndex = 0
    private var finalUtteranceId: String? = null
    private var closed = false

    private val engine = TextToSpeech(context.applicationContext) { status ->
        main.post {
            if (closed) return@post
            ready = status == TextToSpeech.SUCCESS
            failed = !ready
            whenReady.toList().forEach { it() }
            whenReady.clear()
        }
    }

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId?.let(::parse) ?: return
                main.post { if (!closed && id.generation == generation) listener.onVerseStarted(id.verseIndex) }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                val parsed = parse(id) ?: return
                main.post {
                    if (closed || parsed.generation != generation) return@post
                    if (parsed.lastForVerse) listener.onVerseFinished(parsed.verseIndex)
                    if (id == finalUtteranceId) {
                        pendingSegments = emptyList()
                        finalUtteranceId = null
                        listener.onChapterFinished()
                    } else {
                        speakNextSegment(parsed.generation, TextToSpeech.QUEUE_ADD)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId?.let(::parse) ?: return
                main.post {
                    if (!closed && id.generation == generation) {
                        pendingSegments = emptyList()
                        finalUtteranceId = null
                        generation++
                        listener.onError()
                    }
                }
            }
        })
    }

    /** The language the user selected as a BCP 47 tag, or null to match the translation's language automatically. */
    var selectedLanguage: String?
        get() = preferences.getString(KEY_LANGUAGE, null)
        set(value) {
            preferences.edit().apply { if (value == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, value) }.apply()
        }

    var playbackSpeed: Float
        get() = preferences.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED)
        set(value) {
            require(value in PLAYBACK_SPEEDS) { "Unsupported playback speed: $value" }
            preferences.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
        }

    /** Speaks [verses] in order, stopping anything already being spoken, then calls [Listener.onChapterFinished]. */
    fun speak(verses: List<Pair<VerseIndex, String>>, translationLanguage: String) {
        val request = ++generation
        pendingSegments = emptyList()
        finalUtteranceId = null
        nextSegmentIndex = 0
        if (ready) engine.stop()
        whenEngineReady {
            if (closed || request != generation) return@whenEngineReady
            val locale = resolveLocale(translationLanguage)
            if (locale == null) {
                listener.onLanguageUnavailable(selectedLanguage ?: translationLanguage)
                return@whenEngineReady
            }
            if (verses.isEmpty()) {
                listener.onChapterFinished()
                return@whenEngineReady
            }
            val segments = verses.flatMap { (verseIndex, text) -> segmentsForVerse(verseIndex, text) }
            pendingSegments = segments.mapIndexed { position, segment ->
                val id = utteranceId(request, segment.verseIndex, position, position == segments.lastIndex ||
                    segments.getOrNull(position + 1)?.verseIndex != segment.verseIndex)
                QueuedSegment(id, segment)
            }
            finalUtteranceId = pendingSegments.lastOrNull()?.id
            speakNextSegment(request, TextToSpeech.QUEUE_FLUSH)
        }
    }

    fun stop() {
        generation++
        pendingSegments = emptyList()
        finalUtteranceId = null
        nextSegmentIndex = 0
        if (ready) engine.stop()
    }

    /** The languages the installed voices can speak, for the user to choose from. */
    fun availableLanguages(callback: (List<Locale>) -> Unit) = whenEngineReady {
        callback(supportedLocales().sortedBy { it.displayName })
    }

    fun shutdown() {
        closed = true
        whenReady.clear()
        pendingSegments = emptyList()
        finalUtteranceId = null
        if (ready) engine.stop()
        engine.shutdown()
    }

    private fun speakNextSegment(request: Int, queueMode: Int) {
        if (closed || request != generation) return
        val queued = pendingSegments.getOrNull(nextSegmentIndex) ?: return
        val segment = queued.segment
        // Rate and pitch are engine-wide, so speak one segment at a time to keep modifiers local.
        if (engine.setSpeechRate(segment.rate * playbackSpeed) == TextToSpeech.ERROR ||
            engine.setPitch(segment.pitch) == TextToSpeech.ERROR) {
            pendingSegments = emptyList()
            finalUtteranceId = null
            generation++
            listener.onError()
            return
        }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, segment.volume) }
        nextSegmentIndex++
        if (engine.speak(segment.text, queueMode, params, queued.id) == TextToSpeech.ERROR) {
            pendingSegments = emptyList()
            finalUtteranceId = null
            generation++
            listener.onError()
        }
    }

    private fun whenEngineReady(action: () -> Unit) {
        when {
            ready -> action()
            failed -> listener.onError()
            else -> whenReady += action
        }
    }

    private fun supportedLocales(): List<Locale> =
        engine.availableLanguages.orEmpty().filter { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }

    /** The selected language, or else the installed voice language matching the translation, preferring the user's region. */
    private fun resolveLocale(translationLanguage: String): Locale? {
        val locale = selectedLanguage?.let(Locale::forLanguageTag)
            ?: supportedLocales()
                .filter { languageMatchesLocale(translationLanguage, it) }
                .sortedBy { if (it.country == Locale.getDefault().country) 0 else 1 }
                .firstOrNull()
            ?: return null
        val result = engine.setLanguage(locale)
        return locale.takeIf { result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED }
    }

    private class UtteranceId(val generation: Int, val verseIndex: VerseIndex, val lastForVerse: Boolean)

    private fun utteranceId(generation: Int, v: VerseIndex, segment: Int, lastForVerse: Boolean) =
        "$generation:${v.bookIndex}:${v.chapterIndex}:${v.verseIndex}:$segment:${if (lastForVerse) 1 else 0}"

    private fun parse(id: String): UtteranceId? {
        val parts = id.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 6) UtteranceId(parts[0], VerseIndex(parts[1], parts[2], parts[3]), parts[5] == 1) else null
    }

    private fun splitSelah(text: String): List<Pair<String, Float>> {
        val result = mutableListOf<Pair<String, Float>>()
        val regex = Regex("(?i)\\bselah\\b[.!?,;:]?")
        var start = 0
        regex.findAll(text).forEach { match ->
            if (match.range.first > start) result += text.substring(start, match.range.first) to NORMAL_VOLUME
            result += match.value to SELAH_VOLUME
            start = match.range.last + 1
        }
        if (start < text.length || result.isEmpty()) result += text.substring(start) to NORMAL_VOLUME
        return result
    }

    private fun segmentsForVerse(verseIndex: VerseIndex, text: String): List<SpeechSegment> {
        if (verseIndex.bookIndex == PSALMS && verseIndex.chapterIndex == PSALM_9_CHAPTER_INDEX &&
            verseIndex.verseIndex == PSALM_9_16_VERSE_INDEX) {
            val words = Regex("\\S+").findAll(text).toList()
            if (words.size >= 2) {
                val higgaion = words[words.lastIndex - 1]
                val selah = words.last()
                return buildList {
                    if (higgaion.range.first > 0) add(SpeechSegment(verseIndex, text.substring(0, higgaion.range.first)))
                    add(
                        SpeechSegment(
                            verseIndex,
                            text.substring(higgaion.range.first, selah.range.first),
                            volume = HIGGAION_VOLUME,
                            pitch = HIGGAION_PITCH
                        )
                    )
                    add(
                        SpeechSegment(
                            verseIndex,
                            text.substring(selah.range.first),
                            volume = PSALM_9_SELAH_VOLUME,
                            rate = PSALM_9_SELAH_RATE
                        )
                    )
                }
            }
        }
        val pieces = if (verseIndex.bookIndex == PSALMS || verseIndex.bookIndex == HABAKKUK) {
            splitSelah(text).map { (piece, volume) -> SpeechSegment(verseIndex, piece, volume) }
        } else {
            listOf(SpeechSegment(verseIndex, text))
        }
        return pieces
    }

    private data class SpeechSegment(
        val verseIndex: VerseIndex,
        val text: String,
        val volume: Float = NORMAL_VOLUME,
        val rate: Float = NORMAL_RATE,
        val pitch: Float = NORMAL_PITCH
    )

    private data class QueuedSegment(val id: String, val segment: SpeechSegment)

    private companion object {
        const val PREFERENCES = "speech"
        const val KEY_LANGUAGE = "language"
        const val KEY_PLAYBACK_SPEED = "playback_speed_v2"
        const val DEFAULT_PLAYBACK_SPEED = 1f
        val PLAYBACK_SPEEDS = setOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 1.75f, 2f, 2.5f, 3f)
        const val PSALMS = 18
        const val HABAKKUK = 34
        const val PSALM_9_CHAPTER_INDEX = 8
        const val PSALM_9_16_VERSE_INDEX = 15
        const val NORMAL_VOLUME = 1f
        const val NORMAL_RATE = 1f
        const val NORMAL_PITCH = 1f
        const val SELAH_VOLUME = 0.18f
        const val HIGGAION_VOLUME = 0.18f
        const val HIGGAION_PITCH = 0.75f
        const val PSALM_9_SELAH_VOLUME = 0.05f
        const val PSALM_9_SELAH_RATE = 0.55f
    }
}
