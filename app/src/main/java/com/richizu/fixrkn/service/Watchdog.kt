package com.richizu.fixrkn.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.richizu.fixrkn.bypass.BypassVpnService
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.Settings
import java.util.concurrent.TimeUnit

/** Periodically restarts the bypass if the system killed it while the user wants it on. */
class Watchdog(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val NAME = "watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<Watchdog>(15, TimeUnit.MINUTES)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!Settings.bypassWanted || Settings.economy || com.richizu.fixrkn.bypass.Scanner.busy) return Result.success()
        if (Settings.youtubeEnabled && !BypassVpnService.alive && Control.vpn.value != RunState.Paused) {
            AppLog.w("Watchdog", "Bypass is not running, restarting")
            Control.startBypass(applicationContext)
        }
        if (Settings.telegramEnabled && Control.telegram.value == RunState.Off) {
            Control.startTelegram(applicationContext)
        }
        return Result.success()
    }
}
