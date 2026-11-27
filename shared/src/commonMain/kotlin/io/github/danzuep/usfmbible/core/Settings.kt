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

package io.github.danzuep.usfmbible.core

data class Settings(
    val keepScreenOn: Boolean,
    @NightMode val nightMode: Int,
    val fontSizeScale: Float,
    val simpleReadingModeOn: Boolean,
    val showSearchButton: Boolean,
    @Highlight.Companion.AvailableColor val defaultHighlightColor: Int,
    val rubyOn: Boolean = true,
    @DataPolicy val dataPolicy: Int = DATA_POLICY_MOBILE,
) {
    companion object {
        const val MIN_FONT_SIZE_SCALE = 0.5F
        const val MAX_FONT_SIZE_SCALE = 3.0F

        const val NIGHT_MODE_ON = 0
        const val NIGHT_MODE_OFF = 1
        const val NIGHT_MODE_FOLLOW_SYSTEM = 2

        @Retention(AnnotationRetention.SOURCE)
        annotation class NightMode

        /** Which networks may be used for downloads: unmetered ones only, also metered (mobile) ones, or also while roaming. */
        const val DATA_POLICY_WIFI = 0
        const val DATA_POLICY_MOBILE = 1
        const val DATA_POLICY_ROAMING = 2

        @Retention(AnnotationRetention.SOURCE)
        annotation class DataPolicy

        val DEFAULT = Settings(
            keepScreenOn = true,
            nightMode = NIGHT_MODE_FOLLOW_SYSTEM,
            fontSizeScale = 1.0F,
            simpleReadingModeOn = false,
            showSearchButton = true,
            defaultHighlightColor = Highlight.COLOR_NONE,
            rubyOn = true,
            dataPolicy = DATA_POLICY_MOBILE,
        )
    }

    /** Whether a download may use the current network under [dataPolicy]. */
    fun allowsNetwork(unmetered: Boolean, roaming: Boolean): Boolean = when {
        unmetered -> true
        dataPolicy == DATA_POLICY_WIFI -> false
        roaming -> dataPolicy == DATA_POLICY_ROAMING
        else -> true
    }
}
