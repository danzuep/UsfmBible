package io.github.danzuep.usfmbible.translations

import android.content.Context
import co.touchlab.kermit.Logger
import io.github.danzuep.usfmbible.core.TranslationInfo
import io.github.danzuep.usfmbible.core.TranslationManager
import io.github.danzuep.usfmbible.core.repository.remote.UpdateStatus
import io.github.danzuep.usfmbible.core.repository.remote.android.TranslationVersions
import io.github.danzuep.usfmbible.core.repository.remote.android.fetchLastModified
import io.github.danzuep.usfmbible.core.repository.remote.siteOrigin
import io.github.danzuep.usfmbible.core.repository.remote.updateStatus
import io.github.danzuep.usfmbible.core.repository.remote.usfmZipUrl
import kotlinx.coroutines.flow.first

/** [unknownVersion] is true when no version was recorded at install time, e.g. the prepackaged WEB. */
data class TranslationUpdateCandidate(val translation: TranslationInfo, val unknownVersion: Boolean)

/** Installed translations with a newer version online, or null when no download site could be reached. */
suspend fun findTranslationUpdates(context: Context, translationManager: TranslationManager): List<TranslationUpdateCandidate>? {
    translationManager.reload(false)
    val installed = translationManager.downloadedTranslations().first()
    val origins = translationManager.readDownloadSites().mapNotNull { siteOrigin(it) }.distinct()
    val versions = TranslationVersions(context)
    var reachable = false
    val candidates = installed.mapNotNull { translation ->
        val remote = origins.firstNotNullOfOrNull { origin ->
            runCatching { fetchLastModified(usfmZipUrl(origin, translation.shortName)) }
                .onFailure { e -> Logger.withTag("TranslationUpdates").w(e) { "Failed to check ${translation.shortName} at $origin" } }
                .getOrNull()?.also { reachable = true }
        }
        val status = updateStatus(versions.installed(translation.shortName), remote)
        if (remote == null || status == UpdateStatus.UP_TO_DATE) null
        else TranslationUpdateCandidate(translation, unknownVersion = status == UpdateStatus.UNKNOWN)
    }
    return if (!reachable && installed.isNotEmpty()) null else candidates
}
