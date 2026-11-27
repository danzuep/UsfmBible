package io.github.danzuep.usfmbible.infra

import android.app.Application
import android.net.Uri
import io.github.danzuep.usfmbible.R
import androidx.appcompat.app.AppCompatDelegate
import io.github.danzuep.usfmbible.core.DocumentReader
import io.github.danzuep.usfmbible.core.DocumentWriter
import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.ui.PlatformUi
import io.github.danzuep.usfmbible.ui.TextSizes
import io.github.danzuep.usfmbible.ui.getPrimaryTextSize
import io.github.danzuep.usfmbible.ui.getSecondaryTextSize
import io.github.danzuep.usfmbible.utils.appVersionName
import kotlin.math.roundToInt
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

class AndroidDocumentReader(private val application: Application) : DocumentReader, DocumentWriter {
    override fun readBytes(ref: String, limit: Int): ByteArray {
        val stream = requireNotNull(application.contentResolver.openInputStream(Uri.parse(ref))) { "Cannot open $ref" }
        return stream.use { if (limit == Int.MAX_VALUE) it.readBytes() else it.readNBytes(limit) }
    }

    override fun writeBytes(ref: String, bytes: ByteArray) {
        val stream = requireNotNull(application.contentResolver.openOutputStream(Uri.parse(ref))) { "Cannot open $ref" }
        stream.use { it.write(bytes) }
    }
}

class AndroidPlatformUi(private val application: Application) : PlatformUi {
    override val appVersion: String get() = runCatching { application.appVersionName }.getOrDefault("")

    override fun textSizes(settings: Settings) = TextSizes(
        settings.getPrimaryTextSize(application.resources).roundToInt(),
        settings.getSecondaryTextSize(application.resources).roundToInt(),
    )

    override fun applyNightMode(nightMode: Int) = AppCompatDelegate.setDefaultNightMode(
        when (nightMode) {
            Settings.NIGHT_MODE_ON -> AppCompatDelegate.MODE_NIGHT_YES
            Settings.NIGHT_MODE_OFF -> AppCompatDelegate.MODE_NIGHT_NO
            Settings.NIGHT_MODE_FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> throw IllegalArgumentException("Unsupported night mode - $nightMode")
        }
    )
}
