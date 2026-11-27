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
package io.github.danzuep.usfmbible.reading.speech

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.danzuep.usfmbible.ui.languageMatchesLocale
import java.util.Locale

/** The built-in Android [TextToSpeech], with its callbacks moved to the main thread as [SpeechEngine] requires. */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var closed = false

    override fun initialize(callback: SpeechEngine.Callback, onInit: (Boolean) -> Unit) {
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post { if (!closed) onInit(status == TextToSpeech.SUCCESS) }
        }.also { tts ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { main.post { callback.onStart(it) } }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    utteranceId?.let { main.post { callback.onRangeStart(it, start) } }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { main.post { callback.onDone(it) } }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { main.post { callback.onError(it) } }
                }
            })
        }
    }

    override fun selectVoice(translationLanguage: String, selectedLanguage: String?, selectedVoice: String?): String? {
        val engine = engine ?: return null
        val voice = selectedVoice?.let { name -> installedVoices().firstOrNull { it.name == name } }
        if (voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS) return voice.locale.toLanguageTag()
        val locale = selectedLanguage?.let(Locale::forLanguageTag)
            ?: supportedLocales()
                .filter { languageMatchesLocale(translationLanguage, it) }
                .sortedBy { if (it.country == Locale.getDefault().country) 0 else 1 }
                .firstOrNull()
            ?: return null
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return null
        // setLanguage alone can keep the previous voice, so pin a voice of exactly this locale.
        installedVoices()
            .filter { it.locale == locale }
            .minByOrNull { if (it.isNetworkConnectionRequired) 1 else 0 }
            ?.let(engine::setVoice)
        return locale.toLanguageTag()
    }

    override fun speak(utteranceId: String, text: String, rate: Float, pitch: Float, volume: Float, flush: Boolean): Boolean {
        val engine = engine ?: return false
        if (engine.setSpeechRate(rate) == TextToSpeech.ERROR || engine.setPitch(pitch) == TextToSpeech.ERROR) return false
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        return engine.speak(text, queueMode, params, utteranceId) != TextToSpeech.ERROR
    }

    override fun stop() {
        engine?.stop()
    }

    override fun voices(): List<SpeechVoice> = installedVoices().map {
        SpeechVoice(it.name, it.locale.toLanguageTag(), it.locale.displayName, it.isNetworkConnectionRequired)
    }

    override fun shutdown() {
        closed = true
        engine?.shutdown()
    }

    private fun supportedLocales(): List<Locale> {
        val engine = engine ?: return emptyList()
        return engine.availableLanguages.orEmpty().filter { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
    }

    private fun installedVoices(): List<Voice> =
        engine?.voices.orEmpty().filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
}

/** Keeps the speech choices in the app's shared preferences. */
class AndroidSpeechPreferences(context: Context) : SpeechPreferences {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override var language: String?
        get() = preferences.getString(KEY_LANGUAGE, null)
        set(value) {
            preferences.edit().apply { if (value == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, value) }.apply()
        }

    override var voice: String?
        get() = preferences.getString(KEY_VOICE, null)
        set(value) {
            preferences.edit().apply { if (value == null) remove(KEY_VOICE) else putString(KEY_VOICE, value) }.apply()
        }

    override var playbackSpeed: Float
        get() = preferences.getFloat(KEY_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED)
        set(value) {
            preferences.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
        }

    private companion object {
        const val PREFERENCES = "speech"
        const val KEY_LANGUAGE = "language"
        const val KEY_VOICE = "voice"
        const val KEY_PLAYBACK_SPEED = "playback_speed_v2"
        const val DEFAULT_PLAYBACK_SPEED = 1f
    }
}

/** A [ChapterSpeaker] on the Android text to speech engine and the app's saved speech choices. */
fun AndroidChapterSpeaker(context: Context, listener: ChapterSpeaker.Listener): ChapterSpeaker =
    ChapterSpeaker(AndroidSpeechEngine(context), AndroidSpeechPreferences(context), listener)
