package com.richizu.fixrkn.tg

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.service.Notifications
import com.richizu.fixrkn.service.RunState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Foreground service hosting the local Telegram proxy. */
class TgProxyService : LifecycleService() {
    companion object {
        private const val TAG = "TgProxyService"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        /** Notification "Stop" button: stops the proxy and the bypass. */
        const val ACTION_STOP_ALL = "stop_all"
        const val ACTION_REFRESH = "refresh"
        const val EXTRA_OPEN_TELEGRAM = "open_telegram"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        goForeground()
        when (intent?.action) {
            ACTION_START -> start(intent.getBooleanExtra(EXTRA_OPEN_TELEGRAM, false))
            ACTION_STOP -> stopProxy(stopAll = false)
            ACTION_STOP_ALL -> stopProxy(stopAll = true)
            ACTION_REFRESH -> if (!TgProxy.running) stopSelf()
            else -> if (!TgProxy.running) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.STATUS_ID, Notifications.status(this), type)
    }

    private fun start(openTelegram: Boolean) {
        if (TgProxy.running) {
            Control.telegram.value = RunState.On
            if (openTelegram) openTelegram()
            return
        }
        Control.telegram.value = RunState.Starting
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { TgProxy.start(this@TgProxyService) }
            if (!ok) {
                Control.telegram.value = RunState.Failed
                stopSelfSafely()
                return@launch
            }
            Control.telegram.value = RunState.On
            Notifications.clearReminders(this@TgProxyService)
            TgProxy.onOwnVpnChanged(this@TgProxyService)
            acquireWakeLock()
            Control.refreshNotification(this@TgProxyService)
            if (openTelegram && !Settings.tgLinked) openTelegram()
            while (isActive && TgProxy.running) {
                TgProxy.refreshStats()
                delay(1000)
            }
        }
    }

    private fun openTelegram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TgProxy.link())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Settings.tgLinked = true
        } catch (e: Exception) {
            AppLog.w(TAG, "Telegram is not installed", e)
        }
    }

    private fun stopProxy(stopAll: Boolean) {
        val wasRunning = TgProxy.running
        TgProxy.stop()
        releaseWakeLock()
        Control.telegram.value = RunState.Off
        if (wasRunning && Settings.tgLinked) Notifications.telegramProxyStopped(this)
        if (stopAll) Control.stopAll(this)
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Control.refreshNotification(this)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (!Settings.tgWakeLock || wakeLock != null) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RichizuFixRKN:tgproxy")
            .apply { setReferenceCounted(false); acquire() }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        if (TgProxy.running) {
            TgProxy.stop()
            Control.telegram.value = RunState.Off
        }
        super.onDestroy()
    }
}
