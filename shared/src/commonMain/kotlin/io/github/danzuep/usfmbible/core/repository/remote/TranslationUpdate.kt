package io.github.danzuep.usfmbible.core.repository.remote

enum class UpdateStatus { UP_TO_DATE, UPDATE_AVAILABLE, UNKNOWN }

/**
 * Compares the `Last-Modified` header recorded when a translation was installed with the one the site reports now.
 * Translations installed without a recorded version, e.g. the prepackaged WEB, are [UpdateStatus.UNKNOWN].
 */
fun updateStatus(installedVersion: String?, remoteVersion: String?): UpdateStatus = when {
    remoteVersion.isNullOrBlank() || installedVersion.isNullOrBlank() -> UpdateStatus.UNKNOWN
    installedVersion.trim() == remoteVersion.trim() -> UpdateStatus.UP_TO_DATE
    else -> UpdateStatus.UPDATE_AVAILABLE
}
