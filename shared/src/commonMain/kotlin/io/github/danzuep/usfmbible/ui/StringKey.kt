package io.github.danzuep.usfmbible.ui

/** The user-visible strings that view models in `shared` need, resolved by the platform through [StringResolver]. */
enum class StringKey {
    HeaderAvailableTranslations,
    TranslationDetailId,
    TranslationDetailTerritory,
    TranslationDetailLanguage,
    TranslationDetailLanguageEnglish,
    TranslationDetailVernacularTitle,
    TranslationDetailEnglishTitle,
}

fun interface StringResolver {
    fun get(key: StringKey): String
}
