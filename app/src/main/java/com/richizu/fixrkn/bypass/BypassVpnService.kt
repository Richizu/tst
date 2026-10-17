package com.richizu.fixrkn.bypass

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import com.richizu.fixrkn.MainActivity
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.RouteMode
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.service.Notifications
import com.richizu.fixrkn.service.RunState
import com.richizu.fixrkn.service.Traffic
import com.richizu.fixrkn.service.Watchdog
import com.richizu.fixrkn.tg.TgProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.concurrent.thread

/**
 * The bypass: a VPN whose TUN interface is fed into hev-socks5-tunnel, which forwards
 * every connection to the local byedpi SOCKS proxy that applies the desync strategy.
 */
class BypassVpnService : VpnService() {
    companion object {
        private const val TAG = "BypassVpn"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_STOP_KEEP_WANTED = "stop_keep"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RELOAD = "reload"
        const val ACTION_REFRESH = "refresh"
        private const val PROXY_PORT = 10880

        @Volatile var alive = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var tun: ParcelFileDescriptor? = null
    private var proxyThread: Thread? = null
    private var statsJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        watchNetwork()
    }

    override fun onDestroy() {
        alive = false
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        scope.cancel()
        teardown()
        Control.setVpnState(RunState.Off)
        TgProxy.onOwnVpnChanged(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.d(TAG, "onStartCommand ${intent?.action}")
        try {
            goForeground()
        } catch (e: Exception) {
            AppLog.e(TAG, "startForeground failed", e)
            Control.lastError.value = "Не удалось запустить службу: ${e.message}"
            Control.setVpnState(RunState.Failed)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP, ACTION_STOP_KEEP_WANTED -> scope.launch { stop(true) }
            ACTION_PAUSE -> scope.launch { pause() }
            ACTION_RELOAD -> scope.launch { reload() }
            ACTION_REFRESH -> Unit
            // ACTION_START or SERVICE_INTERFACE (always-on VPN started by the system)
            else -> scope.launch { start() }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        AppLog.w(TAG, "VPN revoked by the system (another VPN started)")
        Notifications.vpnReplaced(this, Control.telegram.value == RunState.On)
        scope.launch { stop(true) }
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.STATUS_ID, Notifications.status(this), type)
    }

    private suspend fun start() = lock.withLock {
        if (tun != null) {
            Control.setVpnState(RunState.On)
            return
        }
        Notifications.clearReminders(this)
        Control.setVpnState(RunState.Starting)
        goForeground()
        try {
            selectStrategyForNetwork()
            startProxy()
            startTunnel()
            Control.setVpnState(RunState.On)
            AppLog.i(TAG, "Bypass started")
            if (!Settings.economy) Watchdog.schedule(this) else Watchdog.cancel(this)
            startStats()
        } catch (e: Throwable) {
            AppLog.e(TAG, "Failed to start bypass", e)
            Control.lastError.value = when (e) {
                is IllegalStateException -> e.message ?: "VPN не создан"
                is SecurityException -> "Android отозвал разрешение VPN. Нажмите кнопку ещё раз."
                is UnsatisfiedLinkError -> "Не загрузилась нативная библиотека: ${e.message}"
                else -> "${e.javaClass.simpleName}: ${e.message}"
            }
            teardown()
            Control.setVpnState(RunState.Failed)
            stopSelfSafely()
        }
        afterStateChange()
    }

    private suspend fun stop(removeService: Boolean) {
        lock.withLock {
            teardown()
            Control.setVpnState(RunState.Off)
            AppLog.i(TAG, "Bypass stopped")
        }
        if (!Settings.bypassWanted) Watchdog.cancel(this)
        afterStateChange()
        if (removeService) stopSelfSafely()
    }

    private suspend fun pause() {
        lock.withLock {
            teardown()
            Control.setVpnState(RunState.Paused)
            AppLog.i(TAG, "Bypass paused")
        }
        Watchdog.cancel(this)
        goForeground()
        afterStateChange()
    }

    private suspend fun reload() = lock.withLock {
        if (tun == null) return
        AppLog.i(TAG, "Reloading with the current strategy")
        try {
            stopProxy()
            startProxy()
        } catch (e: Exception) {
            AppLog.e(TAG, "Reload failed", e)
        }
    }

    private fun afterStateChange() {
        TgProxy.onOwnVpnChanged(this)
        Control.refreshNotification(this)
    }

    private fun stopSelfSafely() {
        if (Control.telegram.value == RunState.On) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            Control.refreshNotification(this)
        }
        stopSelf()
    }

    // --- byedpi -------------------------------------------------------------

    private fun startProxy() {
        val strategy = Settings.activeStrategy ?: Strategies.builtIn.first()
        val args = byeDpiArgs(strategy, PROXY_PORT)
        AppLog.i(TAG, "byedpi: $strategy")
        proxyThread = thread(name = "byedpi", isDaemon = true) {
            val code = try {
                ByeDpiNative.run(args)
            } catch (e: Throwable) {
                AppLog.e(TAG, "byedpi crashed", e)
                -1
            }
            AppLog.d(TAG, "byedpi finished with code $code")
            if (code != 0 && proxyThread === Thread.currentThread()) {
                AppLog.e(TAG, "byedpi exited with code $code")
                Control.lastError.value = "Прокси byedpi завершился с кодом $code (стратегия: $strategy)"
                scope.launch {
                    Control.setVpnState(RunState.Failed)
                    stop(true)
                }
            }
        }
        // Give the listener a moment so the first connections are not refused.
        val deadline = System.currentTimeMillis() + 1500
        while (System.currentTimeMillis() < deadline) {
            val up = runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", PROXY_PORT), 100) } }.isSuccess
            if (up) return
            Thread.sleep(30)
        }
        AppLog.w(TAG, "byedpi listener not confirmed, continuing")
    }

    private fun stopProxy() {
        val t = proxyThread ?: return
        proxyThread = null
        ByeDpiNative.stop()
        t.join(2000)
        if (t.isAlive) {
            AppLog.w(TAG, "byedpi did not stop in time, forcing")
            ByeDpiNative.forceStop()
            t.join(1000)
        }
    }

    // --- tunnel -------------------------------------------------------------

    private fun startTunnel() {
        val tunnelLog = File(cacheDir, "tunnel.log").apply { delete() }
        val config = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: 8500")
            appendLine("misc:")
            appendLine("  task-stack-size: 81920")
            appendLine("  log-file: ${tunnelLog.absolutePath}")
            appendLine("  log-level: info")
            appendLine("socks5:")
            appendLine("  address: 127.0.0.1")
            appendLine("  port: $PROXY_PORT")
            appendLine("  udp: udp")
        }
        val file = File(cacheDir, "tunnel.yml").apply { writeText(config) }
        AppLog.d(TAG, "Establishing VPN (route mode ${Settings.routeMode}, apps ${Settings.routedApps.size})")
        val fd = buildTun().establish()
            ?: error("Android не создал VPN-интерфейс: нет разрешения или его перехватил другой VPN")
        tun = fd
        AppLog.i(TAG, "VPN interface established, fd ${fd.fd}")
        // On TVs QUIC is dropped so YouTube falls back to TCP, which byedpi can handle.
        val dropQuic = Settings.tvMode
        if (!Tun2Socks.TProxyStartService(file.absolutePath, fd.fd, dropQuic)) {
            error("Туннель hev-socks5-tunnel не запустился")
        }
        Thread.sleep(400)
        if (!Tun2Socks.TProxyIsRunning()) {
            AppLog.e(TAG, "Tunnel log:\n" + runCatching { tunnelLog.readText() }.getOrDefault("(нет)"))
            error("Туннель завершился сразу после запуска (см. журнал)")
        }
        AppLog.i(TAG, "Tunnel started (dropQuic=$dropQuic)")
        scope.launch { selfTest(tunnelLog) }
    }

    /**
     * Checks the chain right after start: a request through byedpi's SOCKS port must succeed
     * (that path does not go through the VPN). Results only go to the log; they help telling
     * a byedpi problem from a tunnel problem.
     */
    private suspend fun selfTest(tunnelLog: File) {
        delay(1500)
        val ms = com.richizu.fixrkn.bypass.Prober().fetch("www.gstatic.com", PROXY_PORT, 8000)
        if (ms >= 0) AppLog.i(TAG, "Self-test: byedpi proxies traffic ($ms ms)")
        else AppLog.w(TAG, "Self-test: request through byedpi failed (no internet or the strategy breaks connections)")
        val log = runCatching { tunnelLog.readText() }.getOrNull()?.trim()
        if (!log.isNullOrEmpty()) AppLog.d(TAG, "Tunnel log:\n" + log.lines().takeLast(30).joinToString("\n"))
        val stats = runCatching { Tun2Socks.TProxyGetStats() }.getOrNull()
        if (stats != null) AppLog.i(TAG, "Tunnel stats after start: tx ${stats[0]} packets, rx ${stats[2]} packets")
    }

    private fun buildTun(): Builder {
        val b = Builder()
            .setSession(getString(com.richizu.fixrkn.R.string.app_name))
            .addAddress("10.42.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .setConfigureIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
        if (Settings.ipv6) b.addAddress("fd42::1", 128).addRoute("::", 0)
        // Respect the user's Private DNS; otherwise give the tunnel a public resolver.
        if (com.richizu.fixrkn.core.Network.privateDnsHost(this).isNullOrBlank()) b.addDnsServer(Settings.tunnelDns)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) b.setMetered(false)
        when (Settings.routeMode) {
            RouteMode.SelectedApps -> {
                var added = 0
                for (pkg in Settings.routedApps) {
                    try {
                        b.addAllowedApplication(pkg); added++
                    } catch (_: Exception) {
                        // not installed
                    }
                }
                // With an empty allow-list Android would route every app; route only ourselves instead.
                if (added == 0) b.addAllowedApplication(packageName)
            }
            RouteMode.AllApps -> b.addDisallowedApplication(packageName)
        }
        return b
    }

    private fun teardown() {
        statsJob?.cancel()
        statsJob = null
        if (tun != null) {
            runCatching { Tun2Socks.TProxyStopService() }
            runCatching { tun?.close() }
            tun = null
        }
        stopProxy()
        Control.traffic.value = Traffic()
    }

    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var last = LongArray(4)
            while (isActive) {
                delay(1000)
                val s = runCatching { Tun2Socks.TProxyGetStats() }.getOrNull() ?: continue
                Control.traffic.value = Traffic(
                    rxBytes = s[3], txBytes = s[1],
                    rxRate = (s[3] - last[3]).coerceAtLeast(0), txRate = (s[1] - last[1]).coerceAtLeast(0)
                )
                last = s
            }
        }
    }

    // --- network changes ------------------------------------------------------

    /** Applies the strategy remembered for the current network type, if any. Returns true if it changed. */
    private fun selectStrategyForNetwork(): Boolean {
        val kind = com.richizu.fixrkn.core.Network.currentKind(this) ?: return false
        val remembered = Settings.strategyFor(kind) ?: return false
        if (remembered == Settings.activeStrategy) return false
        Settings.activeStrategy = remembered
        AppLog.i(TAG, "${kind.title}: switching to its strategy")
        return true
    }

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return
                val previous = current
                current = network
                if (previous != null && previous != network && tun != null) {
                    scope.launch {
                        lock.withLock {
                            if (tun == null) return@withLock
                            AppLog.i(TAG, "Network changed, restarting the proxy")
                            selectStrategyForNetwork()
                            try {
                                stopProxy(); startProxy()
                            } catch (e: Exception) {
                                AppLog.e(TAG, "Restart after network change failed", e)
                            }
                        }
                    }
                }
            }
        }
        try {
            cm.registerNetworkCallback(request, cb)
            networkCallback = cb
        } catch (e: Exception) {
            AppLog.w(TAG, "Cannot watch network changes", e)
        }
    }
}
