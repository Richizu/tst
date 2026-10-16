package com.richizu.fixrkn.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

enum class RouteMode { SelectedApps, AllApps }
enum class Player { YouTube, SmartTube }

/**
 * Typed access to all user settings. [version] changes on every write so
 * Compose screens can simply observe it and re-read values.
 */
object Settings {
    private lateinit var prefs: SharedPreferences
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _version.value++ }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun bool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun setBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun str(key: String, def: String) = prefs.getString(key, def) ?: def
    private fun setStr(key: String, value: String?) = prefs.edit().putString(key, value).apply()
    private fun int(key: String, def: Int) = prefs.getInt(key, def)
    private fun setInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    // --- general -----------------------------------------------------------
    var onboarded: Boolean
        get() = bool("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).commit().let { }
    var tvMode: Boolean
        get() = bool("tv_mode", false)
        set(v) = setBool("tv_mode", v)
    var youtubeEnabled: Boolean
        get() = bool("svc_youtube", true)
        set(v) = setBool("svc_youtube", v)
    var telegramEnabled: Boolean
        get() = bool("svc_telegram", true)
        set(v) = setBool("svc_telegram", v)
    var lightUi: Boolean
        get() = bool("light_ui", false)
        set(v) = setBool("light_ui", v)
    var qrLinks: Boolean
        get() = bool("qr_links", false)
        set(v) = setBool("qr_links", v)
    var player: Player
        get() = if (str("player", "youtube") == "smarttube") Player.SmartTube else Player.YouTube
        set(v) = setStr("player", if (v == Player.SmartTube) "smarttube" else "youtube")
    var autoUpdate: Boolean
        get() = bool("auto_update", true)
        set(v) = setBool("auto_update", v)
    var haptics: Boolean
        get() = bool("haptics", true)
        set(v) = setBool("haptics", v)

    // --- bypass -----------------------------------------------------------
    /** The user wants protection running; used by boot start and the watchdog. */
    var bypassWanted: Boolean
        get() = bool("bypass_wanted", false)
        set(v) = setBool("bypass_wanted", v)
    var startOnBoot: Boolean
        get() = bool("boot_start", false)
        set(v) = setBool("boot_start", v)
    var economy: Boolean
        get() = bool("economy", false)
        set(v) = setBool("economy", v)
    var ipv6: Boolean
        get() = bool("ipv6", false)
        set(v) = setBool("ipv6", v)
    var tunnelDns: String
        get() = str("tun_dns", "1.1.1.1")
        set(v) = setStr("tun_dns", v)
    var routeMode: RouteMode
        get() = if (str("route_mode", "apps") == "all") RouteMode.AllApps else RouteMode.SelectedApps
        set(v) = setStr("route_mode", if (v == RouteMode.AllApps) "all" else "apps")
    var routedApps: Set<String>
        get() = prefs.getStringSet("route_apps", null) ?: DefaultApps.all
        set(v) = prefs.edit().putStringSet("route_apps", v).apply()
    var activeStrategy: String?
        get() = prefs.getString("strategy_active", null)
        set(v) = setStr("strategy_active", v)
    var setupDone: Boolean
        get() = bool("setup_done", false)
        set(v) = setBool("setup_done", v)
    var youtubeVerified: Boolean
        get() = bool("yt_verified", false)
        set(v) = setBool("yt_verified", v)

    fun strategyFor(kind: NetworkKind): String? = prefs.getString("strategy_${kind.key}", null)
    fun setStrategyFor(kind: NetworkKind, strategy: String?) = setStr("strategy_${kind.key}", strategy)

    // --- telegram proxy -----------------------------------------------------
    var tgPort: Int
        get() = int("tg_port", 1443)
        set(v) = setInt("tg_port", v)
    var tgSecret: String
        get() = str("tg_secret", "")
        set(v) = setStr("tg_secret", v)
    var tgDcIps: String
        get() = str("tg_dc", "")
        set(v) = setStr("tg_dc", v)
    var tgPoolSize: Int
        get() = int("tg_pool", 4)
        set(v) = setInt("tg_pool", v)
    var tgCloudflare: Boolean
        get() = bool("tg_cf", true)
        set(v) = setBool("tg_cf", v)
    var tgCloudflareFirst: Boolean
        get() = bool("tg_cf_first", true)
        set(v) = setBool("tg_cf_first", v)
    var tgWakeLock: Boolean
        get() = bool("tg_wakelock", false)
        set(v) = setBool("tg_wakelock", v)
    /** Telegram has already been given our proxy link. */
    var tgLinked: Boolean
        get() = bool("tg_linked", false)
        set(v) = setBool("tg_linked", v)
    var tgWakeHintHidden: Boolean
        get() = bool("tg_wake_hint_hidden", false)
        set(v) = setBool("tg_wake_hint_hidden", v)

    // --- backup -----------------------------------------------------------
    private val notExported = setOf("onboarded", "bypass_wanted")

    fun exportJson(): JSONObject {
        val root = JSONObject()
        for ((key, value) in prefs.all) {
            if (key in notExported) continue
            when (value) {
                is Set<*> -> root.put(key, org.json.JSONArray(value.toList()))
                else -> root.put(key, value)
            }
        }
        return root
    }

    fun importJson(root: JSONObject) {
        val edit = prefs.edit()
        for (key in root.keys()) {
            if (key in notExported) continue
            when (val value = root.get(key)) {
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putInt(key, value.toInt())
                is String -> edit.putString(key, value)
                is org.json.JSONArray -> edit.putStringSet(key, (0 until value.length()).map { value.getString(it) }.toSet())
            }
        }
        edit.apply()
    }

    fun resetAll() {
        prefs.edit().clear().commit()
    }
}

/** Apps routed through the bypass by default (YouTube clients and other often-blocked services). */
object DefaultApps {
    val youtube = setOf(
        "com.google.android.youtube",
        "com.google.android.youtube.tv",
        "com.google.android.youtube.googletv",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids",
        "com.liskovsoft.smarttubetv",
        "com.liskovsoft.smarttubetv.beta",
        "org.smarttube.stable",
        "org.smarttube.beta",
        "com.teamsmart.videomanager.tv",
        "app.revanced.android.youtube",
        "app.rvx.android.youtube",
        "org.schabi.newpipe",
        "com.github.libretube",
        "com.libretube"
    )
    val other = setOf(
        "com.discord",
        "com.instagram.android",
        "com.instagram.barcelona",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.thoughtcrime.securesms",
        "com.viber.voip",
        "tv.twitch.android.app",
        "com.spotify.music",
        "com.pinterest",
        "com.snapchat.android"
    )
    val all = youtube + other
}
