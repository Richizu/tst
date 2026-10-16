package com.richizu.fixrkn.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import com.richizu.fixrkn.core.Player
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.tg.TgProxy
import com.richizu.fixrkn.update.Updater
import com.richizu.fixrkn.update.launchIntent

object Actions {
    private val youtubePackages = listOf(
        "com.google.android.youtube.tv", "com.google.android.youtube.googletv", "com.google.android.youtube"
    )
    private val telegramPackages = listOf(
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram", "org.telegram.plus",
        "nekox.messenger", "tw.nekomimi.nekogram", "com.exteragram.messenger", "org.forkgram.messenger"
    )

    /** Opens the configured YouTube player. Returns false if it is not installed. */
    fun openYoutube(context: Context): Boolean {
        if (Settings.player == Player.SmartTube) return Updater.openSmartTube(context)
        val pm = context.packageManager
        val ordered = if (Settings.tvMode) youtubePackages else youtubePackages.reversed()
        val intent = ordered.firstNotNullOfOrNull { pm.launchIntent(it) }
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    fun isTelegramInstalled(context: Context) = telegramPackages.any { context.packageManager.launchIntent(it) != null }

    fun openTelegram(context: Context): Boolean {
        val intent = telegramPackages.firstNotNullOfOrNull { context.packageManager.launchIntent(it) } ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Hands the proxy link to Telegram (it asks the user to connect). */
    fun linkTelegram(context: Context): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TgProxy.link())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Settings.tgLinked = true
    }.isSuccess

    fun isIgnoringBatteryOptimizations(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        val list = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        for (i in listOf(direct, list)) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    /** Manufacturer screens that control background autostart. */
    private val autostartScreens = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity"
    )

    fun hasVendorAutostart(context: Context) = autostartScreens.any { (pkg, cls) ->
        context.packageManager.resolveActivity(Intent().setComponent(ComponentName(pkg, cls)), 0) != null
    }

    fun openVendorAutostart(context: Context): Boolean {
        for ((pkg, cls) in autostartScreens) {
            val intent = Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    /** Firmwares known to kill background services aggressively. */
    fun aggressiveFirmware(): String? {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> "MIUI / HyperOS"
            "huawei" in m || "honor" in m -> "EMUI / MagicOS"
            "oppo" in m || "realme" in m || "oneplus" in m -> "ColorOS"
            "vivo" in m || "iqoo" in m -> "Funtouch / OriginOS"
            "samsung" in m -> "One UI"
            "meizu" in m -> "Flyme"
            else -> null
        }
    }

    fun openPrivateDnsSettings(context: Context) {
        val candidates = listOf(
            Intent("android.settings.PRIVATE_DNS_SETTINGS"),
            Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS),
            Intent(AndroidSettings.ACTION_SETTINGS)
        )
        for (i in candidates) if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
