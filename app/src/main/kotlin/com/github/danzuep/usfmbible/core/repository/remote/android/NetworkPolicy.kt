package com.github.danzuep.usfmbible.core.repository.remote.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

/** Thrown instead of using mobile data when the user turned it off in settings, or while roaming. */
class MobileDataBlockedException : IOException("Mobile data is not allowed, connect to Wi-Fi")

/**
 * Decides whether the app may use the network for downloads and update checks. An unmetered network (e.g. Wi-Fi) is always
 * fine. A metered one needs [allowMobileData], and is never used while roaming.
 */
class NetworkPolicy(context: Context, private val allowMobileData: suspend () -> Boolean = { true }) {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    suspend fun isAllowed(): Boolean {
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return true
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return true
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) && allowMobileData()
    }

    suspend fun check() {
        if (!isAllowed()) throw MobileDataBlockedException()
    }
}
