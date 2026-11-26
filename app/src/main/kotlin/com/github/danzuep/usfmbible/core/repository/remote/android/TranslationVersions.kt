package com.github.danzuep.usfmbible.core.repository.remote.android

import android.content.Context

/** The `Last-Modified` header of each downloaded translation's zip, kept to tell the user when the site has a newer one. */
class TranslationVersions(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("translation_versions", Context.MODE_PRIVATE)

    fun installed(shortName: String): String? = preferences.getString(shortName, null)

    fun save(shortName: String, version: String?) {
        preferences.edit().apply { if (version == null) remove(shortName) else putString(shortName, version) }.apply()
    }
}
