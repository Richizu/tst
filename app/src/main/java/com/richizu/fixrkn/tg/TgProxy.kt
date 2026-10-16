package com.richizu.fixrkn.tg

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.richizu.fixrkn.bypass.BypassVpnService
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.SecureRandom

/** Owns the [TgRelay] instance and everything around it (secret, link, network monitoring). */
object TgProxy {
    private const val TAG = "TgProxy"

    @Volatile private var relay: TgRelay? = null
    private var monitor: ConnectivityManager.NetworkCallback? = null
    val stats = MutableStateFlow(RelayStats())

    val running get() = relay != null

    fun secret(): String {
        var s = Settings.tgSecret
        if (s.length != 32) {
            s = newSecret()
            Settings.tgSecret = s
        }
        return s
    }

    fun regenerateSecret(): String {
        val s = newSecret()
        Settings.tgSecret = s
        Settings.tgLinked = false
        return s
    }

    private fun newSecret(): String {
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Link that adds the proxy to Telegram. "dd" selects the padded-intermediate transport. */
    fun link(): String = "tg://proxy?server=127.0.0.1&port=${Settings.tgPort}&secret=dd${secret()}"

    fun webLink(): String = "https://t.me/proxy?server=127.0.0.1&port=${Settings.tgPort}&secret=dd${secret()}"

    private fun config() = RelayConfig(
        port = Settings.tgPort,
        secretHex = secret(),
        dcIps = TgRelay.parseDcIps(Settings.tgDcIps),
        poolSize = Settings.tgPoolSize,
        cloudflare = Settings.tgCloudflare,
        cloudflareFirst = Settings.tgCloudflareFirst
    )

    @Synchronized
    fun start(context: Context): Boolean {
        if (relay != null) return true
        CfFronts.init(context)
        CfFronts.resetCooldowns()
        val r = TgRelay(config())
        return try {
            r.start()
            relay = r
            watchNetwork(context.applicationContext)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Cannot start the Telegram proxy on port ${Settings.tgPort}", e)
            r.stop()
            false
        }
    }

    @Synchronized
    fun stop() {
        relay?.stop()
        relay = null
        stats.value = RelayStats()
        monitor?.let { m -> appContext?.getSystemService(ConnectivityManager::class.java)?.let { runCatching { it.unregisterNetworkCallback(m) } } }
        monitor = null
    }

    /** Re-reads settings that can change on the fly (DC IPs, pool, Cloudflare). */
    fun applySettings() {
        relay?.config = config()
    }

    fun refreshStats() {
        relay?.let { stats.value = it.stats() }
    }

    private var appContext: Context? = null

    /**
     * With another VPN active, Telegram traffic already leaves through it, so the
     * Cloudflare fronts only add latency: go direct until that VPN is gone.
     */
    fun onOwnVpnChanged(context: Context) {
        val r = relay ?: return
        val foreign = com.richizu.fixrkn.core.Network.hasForeignVpn(context, BypassVpnService.alive)
        if (r.cloudflareSuspended != foreign) {
            r.cloudflareSuspended = foreign
            AppLog.i(TAG, if (foreign) "Another VPN is active: Cloudflare route paused" else "Cloudflare route restored")
        }
    }

    private fun watchNetwork(context: Context) {
        appContext = context
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            private var last: Network? = null
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return
                if (last != null && last != network) {
                    AppLog.i(TAG, "Network changed: dropping pre-opened connections")
                    relay?.flushPools()
                }
                last = network
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                onOwnVpnChanged(context)
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            monitor = cb
        } catch (e: Exception) {
            AppLog.w(TAG, "Cannot watch network", e)
        }
    }
}
