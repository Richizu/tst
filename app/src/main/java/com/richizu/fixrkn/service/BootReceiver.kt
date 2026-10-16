package com.richizu.fixrkn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON"
        val updated = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!Settings.onboarded) return
        if ((boot && Settings.startOnBoot) || (updated && Settings.bypassWanted)) {
            AppLog.i("Boot", "Autostart (${intent.action})")
            Control.startAll(context)
        }
    }
}
