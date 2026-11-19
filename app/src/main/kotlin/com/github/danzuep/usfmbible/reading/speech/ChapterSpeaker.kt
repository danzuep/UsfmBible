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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.github.danzuep.usfmbible.core.VerseIndex
import com.github.danzuep.usfmbible.ui.languageMatchesLocale
import java.util.Locale

/** Reads chapters aloud with the built-in Android text to speech, verse by verse. */
class ChapterSpeaker(context: Context, private val listener: Listener) {
    interface Listener {
        fun onVerseStarted(verseIndex: VerseIndex)
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
    private var lastUtteranceId: String? = null
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
                main.post { if (!closed && id == lastUtteranceId) listener.onChapterFinished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId?.let(::parse) ?: return
                main.post { if (!closed && id.generation == generation) listener.onError() }
            }
        })
    }

    /** The language the user selected as a BCP 47 tag, or null to match the translation's language automatically. */
    var selectedLanguage: String?
        get() = preferences.getString(KEY_LANGUAGE, null)
        set(value) {
            preferences.edit().apply { if (value == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, value) }.apply()
        }

    /** Speaks [verses] in order, stopping anything already being spoken, then calls [Listener.onChapterFinished]. */
    fun speak(verses: List<Pair<VerseIndex, String>>, translationLanguage: String) = whenEngineReady {
        stop()
        val locale = resolveLocale(translationLanguage)
        if (locale == null) {
            listener.onLanguageUnavailable(selectedLanguage ?: translationLanguage)
            return@whenEngineReady
        }
        if (verses.isEmpty()) {
            listener.onChapterFinished()
            return@whenEngineReady
        }
        val current = generation
        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        verses.forEachIndexed { position, (verseIndex, text) ->
            val id = utteranceId(current, verseIndex)
            lastUtteranceId = id
            val mode = if (position == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(text.take(maxLength), mode, null, id) == TextToSpeech.ERROR) {
                listener.onError()
                return@whenEngineReady
            }
        }
    }

    fun stop() {
        generation++
        lastUtteranceId = null
        if (ready) engine.stop()
    }

    /** The languages the installed voices can speak, for the user to choose from. */
    fun availableLanguages(callback: (List<Locale>) -> Unit) = whenEngineReady {
        callback(supportedLocales().sortedBy { it.displayName })
    }

    fun shutdown() {
        closed = true
        whenReady.clear()
        if (ready) engine.stop()
        engine.shutdown()
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

    private class UtteranceId(val generation: Int, val verseIndex: VerseIndex)

    private fun utteranceId(generation: Int, v: VerseIndex) = "$generation:${v.bookIndex}:${v.chapterIndex}:${v.verseIndex}"

    private fun parse(id: String): UtteranceId? {
        val parts = id.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 4) UtteranceId(parts[0], VerseIndex(parts[1], parts[2], parts[3])) else null
    }

    private companion object {
        const val PREFERENCES = "speech"
        const val KEY_LANGUAGE = "language"
    }
}
