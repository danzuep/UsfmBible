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

/** An installed text-to-speech voice, for the user to choose from. */
data class SpeechVoice(
    val name: String,
    /** BCP 47 tag of the voice's language. */
    val languageTag: String,
    val languageDisplayName: String,
    val requiresNetwork: Boolean,
)

/**
 * The platform text-to-speech engine. Every callback, including [initialize]'s `onInit` and the [Callback] functions,
 * must be delivered on the thread that calls the engine (the main thread on Android).
 */
interface SpeechEngine {
    interface Callback {
        fun onStart(utteranceId: String)

        /** [start] is the character offset in the utterance that is about to be spoken. */
        fun onRangeStart(utteranceId: String, start: Int)

        fun onDone(utteranceId: String)

        fun onError(utteranceId: String)
    }

    /** Starts the engine, then calls [onInit] with whether it can be used. */
    fun initialize(callback: Callback, onInit: (Boolean) -> Unit)

    /**
     * Picks the voice to speak with: [selectedVoice] if installed, else [selectedLanguage], else an installed voice
     * matching [translationLanguage]. Returns the language tag in use, or null if no installed voice can speak it.
     */
    fun selectVoice(translationLanguage: String, selectedLanguage: String?, selectedVoice: String?): String?

    /** Queues [text], dropping anything queued before when [flush] is set. Returns false if the engine refused it. */
    fun speak(utteranceId: String, text: String, rate: Float, pitch: Float, volume: Float, flush: Boolean): Boolean

    fun stop()

    fun voices(): List<SpeechVoice>

    fun shutdown()
}

/** The user's speech choices, which outlive the speaker. */
interface SpeechPreferences {
    /** A BCP 47 tag, or null to match the translation's language automatically. */
    var language: String?

    /** The name of a specific voice, which takes priority over [language], or null for automatic. */
    var voice: String?

    var playbackSpeed: Float
}
