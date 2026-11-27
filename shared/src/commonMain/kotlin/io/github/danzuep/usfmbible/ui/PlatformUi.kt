package io.github.danzuep.usfmbible.ui

import io.github.danzuep.usfmbible.core.Settings

data class TextSizes(val bodyPx: Int, val captionPx: Int)

/** Display behaviour that only the platform can provide. */
interface PlatformUi {
    val appVersion: String

    /** The body and caption text sizes in pixels at the font scale of [settings]. */
    fun textSizes(settings: Settings): TextSizes

    /** Applies the night mode (one of the [Settings] `NIGHT_MODE_*` values). May restart the visible screen. */
    fun applyNightMode(nightMode: Int)
}
