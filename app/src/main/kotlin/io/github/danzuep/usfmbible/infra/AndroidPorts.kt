package io.github.danzuep.usfmbible.infra

import android.app.Application
import android.net.Uri
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.core.DocumentReader
import io.github.danzuep.usfmbible.ui.StringKey
import io.github.danzuep.usfmbible.ui.StringResolver

class AndroidStringResolver(private val application: Application) : StringResolver {
    override fun get(key: StringKey): String = application.getString(
        when (key) {
            StringKey.HeaderAvailableTranslations -> R.string.header_available_translations
            StringKey.TranslationDetailId -> R.string.translation_detail_id
            StringKey.TranslationDetailTerritory -> R.string.translation_detail_territory
            StringKey.TranslationDetailLanguage -> R.string.translation_detail_language
            StringKey.TranslationDetailLanguageEnglish -> R.string.translation_detail_language_english
            StringKey.TranslationDetailVernacularTitle -> R.string.translation_detail_vernacular_title
            StringKey.TranslationDetailEnglishTitle -> R.string.translation_detail_english_title
        }
    )
}

class AndroidDocumentReader(private val application: Application) : DocumentReader {
    override fun readBytes(ref: String, limit: Int): ByteArray {
        val stream = requireNotNull(application.contentResolver.openInputStream(Uri.parse(ref))) { "Cannot open $ref" }
        return stream.use { if (limit == Int.MAX_VALUE) it.readBytes() else it.readNBytes(limit) }
    }
}
