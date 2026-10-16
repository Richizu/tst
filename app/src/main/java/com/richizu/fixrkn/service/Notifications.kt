package com.richizu.fixrkn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.richizu.fixrkn.MainActivity
import com.richizu.fixrkn.R
import com.richizu.fixrkn.bypass.BypassVpnService
import com.richizu.fixrkn.tg.TgProxyService

object Notifications {
    const val STATUS_ID = 1
    private const val SCAN_ID = 10
    private const val TG_REMINDER_ID = 11
    private const val VPN_REPLACED_ID = 12
    const val UPDATE_ID = 13

    private const val CH_STATUS = "status"
    private const val CH_EVENTS = "events"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Статус работы", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "События", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun service(context: Context, cls: Class<*>, action: String, code: Int): PendingIntent =
        PendingIntent.getForegroundService(
            context, code, Intent(context, cls).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** The shared foreground notification of both services. */
    fun status(context: Context): Notification {
        val vpn = Control.vpn.value
        val tg = Control.telegram.value == RunState.On
        val text = when {
            vpn == RunState.Paused -> "Обход на паузе" + if (tg) " · прокси Telegram работает" else ""
            vpn == RunState.On && tg -> "Обход и прокси Telegram работают"
            vpn == RunState.On || vpn == RunState.Starting -> "Обход блокировок работает"
            tg -> "Работает только прокси Telegram"
            else -> "Запуск…"
        }
        val b = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        when (vpn) {
            RunState.On -> b.addAction(0, "Пауза", service(context, BypassVpnService::class.java, BypassVpnService.ACTION_PAUSE, 1))
            RunState.Paused -> b.addAction(0, "Продолжить", service(context, BypassVpnService::class.java, BypassVpnService.ACTION_START, 2))
            else -> Unit
        }
        b.addAction(0, "Остановить", service(context, TgProxyService::class.java, TgProxyService.ACTION_STOP_ALL, 3))
        return b.build()
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun event(context: Context, title: String, text: String) = NotificationCompat.Builder(context, CH_EVENTS)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)
        .setContentIntent(openApp(context))

    fun scanFinished(context: Context, found: Boolean) = post(
        context, SCAN_ID,
        event(
            context, "Подбор завершён",
            if (found) "Рабочий способ найден и применён." else "Рабочих способов не найдено. Попробуйте другую сеть или свою стратегию."
        )
    )

    /** Telegram keeps using the proxy after it stops, so remind the user to switch it off there. */
    fun telegramProxyStopped(context: Context) = post(
        context, TG_REMINDER_ID,
        event(
            context, "Прокси Telegram остановлен",
            "Если Telegram перестал подключаться, отключите прокси в Telegram: Настройки → Данные и память → Прокси."
        )
    )

    fun vpnReplaced(context: Context, telegramStillRunning: Boolean) = post(
        context, VPN_REPLACED_ID,
        event(
            context, "Обход отключён другим VPN",
            if (telegramStillRunning) "Включён другой VPN. Прокси Telegram продолжает работать."
            else "Включён другой VPN. Чтобы вернуть обход, запустите RichizuFixRKN снова."
        )
    )

    fun clearReminders(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(TG_REMINDER_ID)
        nm.cancel(VPN_REPLACED_ID)
    }

    fun updateProgress(context: Context, percent: Int?) {
        val b = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Загрузка обновления")
            .setOnlyAlertOnce(true)
            .setOngoing(percent != null)
            .setSilent(true)
        if (percent == null) {
            context.getSystemService(NotificationManager::class.java).cancel(UPDATE_ID)
            return
        }
        b.setProgress(100, percent, percent < 0)
        post(context, UPDATE_ID, b)
    }
}
