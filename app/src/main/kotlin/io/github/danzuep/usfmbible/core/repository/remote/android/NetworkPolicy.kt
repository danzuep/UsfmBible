package io.github.danzuep.usfmbible.core.repository.remote.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.danzuep.usfmbible.core.Settings
import java.io.IOException

/** Thrown instead of using mobile data when the data policy in settings does not allow the current network. */
class MobileDataBlockedException : IOException("Mobile data is not allowed, connect to Wi-Fi")

/**
 * Decides whether the app may use the network for downloads and update checks, following the user's data policy in
 * settings: an unmetered network (e.g. Wi-Fi) is always fine, mobile data and roaming need the matching policy.
 */
class NetworkPolicy(context: Context, private val settings: suspend () -> Settings = { Settings.DEFAULT }) {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    suspend fun isAllowed(): Boolean {
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return true
        return settings().allowsNetwork(
            unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            roaming = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING),
        )
    }

    suspend fun check() {
        if (!isAllowed()) throw MobileDataBlockedException()
    }
}
