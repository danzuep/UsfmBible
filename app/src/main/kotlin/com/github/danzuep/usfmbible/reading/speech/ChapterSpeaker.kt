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
        fun onVerseQueued(verseIndex: VerseIndex)
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
    private var currentPosition = -1
    private var startedSpans = 0
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
                main.post {
                    if (closed || id.generation != generation) return@post
                    pendingSegments.getOrNull(id.position)?.let { enter(id.position, it, 0) }
                }
            }

            // Verses merged into one utterance are followed by their character offset in the text.
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val id = utteranceId?.let(::parse) ?: return
                main.post {
                    if (closed || id.generation != generation) return@post
                    val queued = pendingSegments.getOrNull(id.position) ?: return@post
                    enter(id.position, queued, queued.spans.indexOfLast { it.offset <= start }.coerceAtLeast(0))
                }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                val parsed = parse(id) ?: return
                main.post {
                    if (closed || parsed.generation != generation) return@post
                    pendingSegments.getOrNull(parsed.position)?.let { queued ->
                        enter(parsed.position, queued, queued.spans.lastIndex)
                        if (queued.lastForVerse) listener.onVerseFinished(queued.spans.last().verseIndex)
                    }
                    if (id == finalUtteranceId) {
                        pendingSegments = emptyList()
                        finalUtteranceId = null
                        listener.onChapterFinished()
                    } else {
                        // Keep one segment queued ahead so the engine never idles between verses.
                        speakNextSegment(parsed.generation, TextToSpeech.QUEUE_ADD, notifyQueued = false)
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
            require(value in speechPlaybackSpeeds) { "Unsupported playback speed: $value" }
            preferences.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
        }

    /** Speaks [verses] in order, stopping anything already being spoken, then calls [Listener.onChapterFinished]. */
    fun speak(verses: List<Pair<VerseIndex, String>>, translationLanguage: String) {
        val request = ++generation
        pendingSegments = emptyList()
        finalUtteranceId = null
        nextSegmentIndex = 0
        currentPosition = -1
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
            val merged = mergeSegments(verses.flatMap { (verseIndex, text) -> segmentsForVerse(verseIndex, text) })
            pendingSegments = merged.mapIndexed { position, item ->
                val lastVerse = item.spans.last().verseIndex
                val lastForVerse = merged.getOrNull(position + 1)?.spans?.first()?.verseIndex != lastVerse
                QueuedSegment(utteranceId(request, position), item.segment, item.spans, lastForVerse)
            }
            finalUtteranceId = pendingSegments.lastOrNull()?.id
            speakNextSegment(request, TextToSpeech.QUEUE_FLUSH)
            speakNextSegment(request, TextToSpeech.QUEUE_ADD, notifyQueued = false)
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

    private fun speakNextSegment(request: Int, queueMode: Int, notifyQueued: Boolean = true) {
        if (closed || request != generation) return
        val queued = pendingSegments.getOrNull(nextSegmentIndex) ?: return
        val segment = queued.segment
        // Rate and pitch are engine-wide but captured when each utterance is queued, so segments keep their own modifiers.
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
        } else if (notifyQueued) {
            listener.onVerseQueued(queued.spans.first().verseIndex)
        }
    }

    /** Reports the verses of utterance [position] up to span [upTo] as started, finishing the ones before them. */
    private fun enter(position: Int, queued: QueuedSegment, upTo: Int) {
        if (currentPosition != position) {
            currentPosition = position
            startedSpans = 0
        }
        while (startedSpans <= upTo) {
            if (startedSpans > 0) listener.onVerseFinished(queued.spans[startedSpans - 1].verseIndex)
            listener.onVerseStarted(queued.spans[startedSpans].verseIndex)
            startedSpans++
        }
    }

    /**
     * Joins neighbouring verses that share a voice into one utterance while a sentence is still incomplete, because the
     * engine pauses at every utterance boundary, even when the text continues. A joined text over [MAX_UTTERANCE_LENGTH]
     * is split at the last sentence end, else the last comma-like mark, else the last space, that fits.
     */
    private fun mergeSegments(segments: List<SpeechSegment>): List<MergedSegment> {
        val groups = mutableListOf<MergedSegment>()
        for (segment in segments) {
            val last = groups.lastOrNull()
            if (last != null && last.canJoin(segment)) {
                val offset = last.segment.text.length + 1
                groups[groups.lastIndex] = MergedSegment(
                    last.segment.copy(text = last.segment.text + " " + segment.text),
                    last.spans + VerseSpan(segment.verseIndex, offset)
                )
            } else {
                groups += MergedSegment(segment, listOf(VerseSpan(segment.verseIndex, 0)))
            }
        }
        return groups.flatMap(::splitLongSegment)
    }

    private fun splitLongSegment(group: MergedSegment): List<MergedSegment> {
        val text = group.segment.text
        if (text.length <= MAX_UTTERANCE_LENGTH) return listOf(group)
        val result = mutableListOf<MergedSegment>()
        var start = 0
        while (start < text.length) {
            val end = splitPoint(text, start)
            val chunk = text.substring(start, end).trim()
            val chunkStart = start + text.substring(start, end).indexOf(chunk)
            val spans = group.spans.mapIndexedNotNull { i, span ->
                val verseEnd = group.spans.getOrNull(i + 1)?.offset ?: (text.length + 1)
                if (span.offset < end && verseEnd > chunkStart) VerseSpan(span.verseIndex, maxOf(0, span.offset - chunkStart)) else null
            }
            if (chunk.isNotEmpty()) result += MergedSegment(group.segment.copy(text = chunk), spans)
            start = end
        }
        return result
    }

    private fun splitPoint(text: String, start: Int): Int {
        if (text.length - start <= MAX_UTTERANCE_LENGTH) return text.length
        val windowEnd = start + MAX_UTTERANCE_LENGTH
        val window = text.substring(start, windowEnd + 1)
        val end = lastMatchEnd(SENTENCE_BREAK, window) ?: lastMatchEnd(CLAUSE_BREAK, window)
            ?: window.lastIndexOf(' ').takeIf { it > 0 }
            ?: MAX_UTTERANCE_LENGTH
        return start + end
    }

    private fun lastMatchEnd(regex: Regex, window: String): Int? =
        regex.findAll(window).lastOrNull()?.range?.last?.plus(1)

    private fun MergedSegment.canJoin(next: SpeechSegment): Boolean =
        spans.last().verseIndex != next.verseIndex &&
            segment.volume == next.volume && segment.rate == next.rate && segment.pitch == next.pitch &&
            !SENTENCE_END.containsMatchIn(segment.text)

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

    private class UtteranceId(val generation: Int, val position: Int)

    private fun utteranceId(generation: Int, position: Int) = "$generation:$position"

    private fun parse(id: String): UtteranceId? {
        val parts = id.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 2) UtteranceId(parts[0], parts[1]) else null
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

    private class VerseSpan(val verseIndex: VerseIndex, val offset: Int)

    private class MergedSegment(val segment: SpeechSegment, val spans: List<VerseSpan>)

    private class QueuedSegment(
        val id: String,
        val segment: SpeechSegment,
        val spans: List<VerseSpan>,
        val lastForVerse: Boolean
    )

    private companion object {
        const val PREFERENCES = "speech"
        const val KEY_LANGUAGE = "language"
        const val KEY_PLAYBACK_SPEED = "playback_speed_v2"
        const val DEFAULT_PLAYBACK_SPEED = 1f
        const val MAX_UTTERANCE_LENGTH = 1000
        val SENTENCE_BREAK = Regex("[.!?…。！？][\"'”’»)\\]]*(?=\\s)")
        val CLAUSE_BREAK = Regex("[,;:，、；：][\"'”’»)\\]]*(?=\\s)")
        val SENTENCE_END = Regex("[.!?;:…。！？][\"'”’»)\\]]*\\s*$")
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
