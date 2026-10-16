package com.richizu.fixrkn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetworkKind(val key: String, val title: String, val short: String) {
    Wifi("wifi", "Wi-Fi", "Wi-Fi"),
    Mobile("mobile", "Мобильная сеть", "Моб. сеть")
}

object Network {
    private fun kindOf(caps: NetworkCapabilities): NetworkKind? = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Wifi
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Mobile
        else -> null
    }

    /** Kind of the physical network underneath (ignores VPN networks, including our own). */
    fun currentKind(context: Context): NetworkKind? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return try {
            cm.getNetworkCapabilities(cm.activeNetwork)?.let { kindOf(it) }?.let { return it }
            @Suppress("DEPRECATION")
            val candidates = cm.allNetworks.mapNotNull { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
                kindOf(caps)
            }
            candidates.firstOrNull { it == NetworkKind.Wifi } ?: candidates.firstOrNull()
        } catch (_: SecurityException) {
            null
        }
    }

    fun hasForeignVpn(context: Context, ownVpnActive: Boolean): Boolean {
        if (ownVpnActive) return false
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    fun privateDnsHost(context: Context): String? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return null
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return try {
            cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName
        } catch (_: SecurityException) {
            null
        }
    }
}
