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
package io.github.danzuep.usfmbible.core.repository.remote

import co.touchlab.kermit.Logger
import io.github.danzuep.usfmbible.core.TranslationInfo
import io.github.danzuep.usfmbible.core.TranslationManager
import io.github.danzuep.usfmbible.utils.IOException
import kotlinx.coroutines.flow.first

/** Thrown instead of using mobile data when the data policy in settings does not allow the current network. */
class MobileDataBlockedException : IOException("Mobile data is not allowed, connect to Wi-Fi")

/**
 * Decides whether the app may use the network for downloads and update checks, following the user's data policy in
 * settings: an unmetered network (e.g. Wi-Fi) is always fine, mobile data and roaming need the matching policy.
 */
interface NetworkPolicy {
    suspend fun isAllowed(): Boolean

    /** Throws [MobileDataBlockedException] unless [isAllowed]. */
    suspend fun check() {
        if (!isAllowed()) throw MobileDataBlockedException()
    }
}

/** The list of translations that can be downloaded from the default site, read from the platform's bundled catalog. */
interface TranslationCatalog {
    fun rows(pageUrl: String): List<TranslationTableRow>
}

/** The `Last-Modified` header of each downloaded translation's zip, kept to tell the user when the site has a newer one. */
interface TranslationVersions {
    fun installed(shortName: String): String?

    fun save(shortName: String, version: String?)
}

/**
 * Updates translations in the background once the device is on an unmetered network. [schedule] with no names means
 * the check itself is deferred.
 */
interface TranslationUpdateScheduler {
    fun schedule(shortNames: List<String>)

    fun cancel()

    fun isPending(): Boolean
}

/** [unknownVersion] is true when no version was recorded at install time, e.g. the prepackaged WEB. */
data class TranslationUpdateCandidate(val translation: TranslationInfo, val unknownVersion: Boolean)

/** Finds installed translations with a newer version online, using [lastModified] to read a zip's version from its URL. */
class TranslationUpdateChecker(
    private val translationManager: TranslationManager,
    private val versions: TranslationVersions,
    private val lastModified: (String) -> String?,
) {
    /** The outdated translations, or null when no download site could be reached. */
    suspend fun find(): List<TranslationUpdateCandidate>? {
        translationManager.reload(false)
        val installed = translationManager.downloadedTranslations().first()
        val origins = translationManager.readDownloadSites().mapNotNull { siteOrigin(it) }.distinct()
        var reachable = false
        val candidates = installed.mapNotNull { translation ->
            val remote = origins.firstNotNullOfOrNull { origin ->
                runCatching { lastModified(usfmZipUrl(origin, translation.shortName)) }
                    .onFailure { e -> Logger.withTag("TranslationUpdates").w(e) { "Failed to check ${translation.shortName} at $origin" } }
                    .getOrNull()?.also { reachable = true }
            }
            val status = updateStatus(versions.installed(translation.shortName), remote)
            if (remote == null || status == UpdateStatus.UP_TO_DATE) null
            else TranslationUpdateCandidate(translation, unknownVersion = status == UpdateStatus.UNKNOWN)
        }
        return if (!reachable && installed.isNotEmpty()) null else candidates
    }
}
