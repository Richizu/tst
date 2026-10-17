package com.richizu.fixrkn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.richizu.fixrkn.bypass.BypassVpnService
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.NetworkKind
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.tg.TgProxyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RunState { Off, Starting, On, Paused, Failed }

data class Traffic(val rxBytes: Long = 0, val txBytes: Long = 0, val rxRate: Long = 0, val txRate: Long = 0)

/** Single entry point for starting/stopping the bypass VPN and the Telegram proxy. */
object Control {
    private const val TAG = "Control"

    val vpn = MutableStateFlow(RunState.Off)
    val telegram = MutableStateFlow(RunState.Off)
    val traffic = MutableStateFlow(Traffic())
    /** Set when Android cannot show the VPN consent dialog (some TV firmwares). */
    val consentUnavailable = MutableStateFlow(false)
    /** Human-readable reason of the last bypass failure, shown on the home screen. */
    val lastError = MutableStateFlow<String?>(null)
    private val _connectedSince = MutableStateFlow(0L)
    val connectedSince: StateFlow<Long> = _connectedSince

    val anyRunning get() = vpn.value == RunState.On || telegram.value == RunState.On

    fun setVpnState(state: RunState) {
        if (state == RunState.On && vpn.value != RunState.On) _connectedSince.value = System.currentTimeMillis()
        if (state == RunState.Off || state == RunState.Failed) _connectedSince.value = 0
        if (state == RunState.On || state == RunState.Starting) lastError.value = null
        vpn.value = state
    }

    /** Intent for the system VPN consent dialog, or null when already granted. */
    fun vpnConsentIntent(context: Context): Intent? = VpnService.prepare(context)

    /** Starts everything the user enabled (bypass and/or Telegram proxy). */
    fun startAll(context: Context, openTelegram: Boolean = false) {
        Settings.bypassWanted = true
        if (Settings.youtubeEnabled) startBypass(context)
        if (Settings.telegramEnabled) startTelegram(context, openTelegram)
    }

    fun stopAll(context: Context) {
        Settings.bypassWanted = false
        stopBypass(context)
        stopTelegram(context)
    }

    fun startBypass(context: Context) {
        if (vpnConsentIntent(context) != null) {
            AppLog.w(TAG, "VPN permission not granted, bypass not started")
            lastError.value = "Нет разрешения на VPN. Нажмите кнопку ещё раз и подтвердите запрос системы."
            vpn.value = RunState.Failed
            return
        }
        if (com.richizu.fixrkn.bypass.Scanner.busy) {
            AppLog.w(TAG, "Scan in progress, bypass not started")
            lastError.value = "Идёт подбор способа обхода. Дождитесь окончания или нажмите «Завершить сейчас»."
            vpn.value = RunState.Failed
            return
        }
        AppLog.i(TAG, "Starting bypass service")
        send(context, BypassVpnService::class.java, BypassVpnService.ACTION_START)
    }

    fun stopBypass(context: Context) {
        if (vpn.value == RunState.Off) return
        send(context, BypassVpnService::class.java, BypassVpnService.ACTION_STOP)
    }

    fun pauseBypass(context: Context) = send(context, BypassVpnService::class.java, BypassVpnService.ACTION_PAUSE)

    fun startTelegram(context: Context, openTelegram: Boolean = false) {
        val intent = Intent(context, TgProxyService::class.java)
            .setAction(TgProxyService.ACTION_START)
            .putExtra(TgProxyService.EXTRA_OPEN_TELEGRAM, openTelegram)
        start(context, intent)
    }

    fun stopTelegram(context: Context) {
        if (telegram.value == RunState.Off) return
        send(context, TgProxyService::class.java, TgProxyService.ACTION_STOP)
    }

    /** Stops the VPN so byedpi can be used for testing; returns whether it was running. */
    suspend fun stopBypassForTesting(context: Context): Boolean {
        val wasRunning = vpn.value == RunState.On || vpn.value == RunState.Starting
        if (wasRunning) {
            send(context, BypassVpnService::class.java, BypassVpnService.ACTION_STOP_KEEP_WANTED)
            var waited = 0
            while (vpn.value != RunState.Off && vpn.value != RunState.Failed && waited < 5000) {
                delay(100); waited += 100
            }
        }
        return wasRunning
    }

    /** Makes [strategy] active (and remembers it for [network]); restarts the bypass if it runs. */
    fun applyStrategy(context: Context, strategy: String, network: NetworkKind?) {
        Settings.activeStrategy = strategy
        if (network != null) Settings.setStrategyFor(network, strategy)
        AppLog.i(TAG, "Active strategy${network?.let { " (${it.title})" } ?: ""}: $strategy")
        if (vpn.value == RunState.On) send(context, BypassVpnService::class.java, BypassVpnService.ACTION_RELOAD)
    }

    /** Asks the running services to re-post the shared status notification. */
    fun refreshNotification(context: Context) {
        if (vpn.value == RunState.On || vpn.value == RunState.Paused) {
            send(context, BypassVpnService::class.java, BypassVpnService.ACTION_REFRESH)
        }
        if (telegram.value == RunState.On) send(context, TgProxyService::class.java, TgProxyService.ACTION_REFRESH)
        QuickTile.refresh(context)
    }

    private fun send(context: Context, cls: Class<*>, action: String) = start(context, Intent(context, cls).setAction(action))

    private fun start(context: Context, intent: Intent) {
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            AppLog.w(TAG, "Cannot start ${intent.component?.shortClassName}", e)
            lastError.value = "Система не дала запустить службу: ${e.javaClass.simpleName}"
        }
    }
}
