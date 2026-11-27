package io.github.danzuep.usfmbible.translations

import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.core.TranslationInfo

/** A row of the translations list. The platform maps each row to its own view. */
sealed class TranslationsItem {
    data class Header(
        val settings: Settings,
        val title: String,
        val hideDivider: Boolean,
        val isLanguage: Boolean = false,
    ) : TranslationsItem()

    data class Translation(
        val settings: Settings,
        val translationInfo: TranslationInfo,
        val isCurrentTranslation: Boolean,
        val title: String = translationInfo.name,
    ) : TranslationsItem()
}
