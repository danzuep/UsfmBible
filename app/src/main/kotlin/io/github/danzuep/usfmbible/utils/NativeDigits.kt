package io.github.danzuep.usfmbible.utils

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

fun Configuration.withNativeDigits(): Configuration = Configuration(this).apply {
    val forced = LocaleList(*Array(locales.size()) { locales[it].withNativeDigits() })
    if (!forced.isEmpty) setLocales(forced)
}

/** The per-app language if one is chosen, otherwise the first locale of the current configuration. */
fun Context.effectiveLocale(): Locale {
    val appLocale = if (Build.VERSION.SDK_INT >= 33) {
        getSystemService(LocaleManager::class.java)?.applicationLocales?.takeUnless { it.isEmpty }?.get(0)
    } else {
        AppCompatDelegate.getApplicationLocales()[0]
    }
    return appLocale ?: resources.configuration.locales[0]
}

/** Wraps the context so resources and formatters use native digits for languages that have their own. */
fun Context.withNativeDigits(): Context {
    val current = resources.configuration.locales
    val forced = effectiveLocale().withNativeDigits()
    if (forced == current[0]) return this
    val config = Configuration(resources.configuration).apply {
        setLocales(LocaleList(forced, *Array(current.size() - 1) { current[it + 1] }))
    }
    return createConfigurationContext(config)
}
