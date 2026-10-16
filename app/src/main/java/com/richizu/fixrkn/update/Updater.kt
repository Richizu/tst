package com.richizu.fixrkn.update

import android.content.Context
import android.content.Intent
import com.richizu.fixrkn.BuildConfig
import com.richizu.fixrkn.Links
import com.richizu.fixrkn.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val apkUrl: String, val notes: String)

/** Checks GitHub Releases for a newer APK of this app and finds SmartTube's latest APK. */
object Updater {
    private const val TAG = "Updater"
    private const val SMARTTUBE_REPO = "yuliskov/SmartTube"
    val SMARTTUBE_PACKAGES = listOf("com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta", "org.smarttube.stable", "org.smarttube.beta")

    val available = MutableStateFlow<Release?>(null)
    val checking = MutableStateFlow(false)
    val lastError = MutableStateFlow<String?>(null)

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        checking.value = true
        lastError.value = null
        try {
            val release = latest(Links.REPO) { it.endsWith(".apk") }
            val newer = release?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
            available.value = newer
            newer
        } catch (e: Exception) {
            lastError.value = "Не удалось проверить обновления"
            AppLog.w(TAG, "Update check failed", e)
            null
        } finally {
            checking.value = false
        }
    }

    suspend fun smartTubeApk(): String? = withContext(Dispatchers.IO) {
        runCatching {
            latest(SMARTTUBE_REPO) { name -> name.contains("stable", true) && name.endsWith(".apk") && (name.contains("universal") || name.contains("arm")) }?.apkUrl
        }.getOrNull()
    }

    private fun latest(repo: String, pick: (String) -> Boolean): Release? {
        val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "RichizuFixRKN")
        if (conn.responseCode != 200) return null
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val assets = json.optJSONArray("assets") ?: return null
        val names = (0 until assets.length()).map { assets.getJSONObject(it) }
        val asset = names.firstOrNull { pick(it.getString("name")) } ?: return null
        return Release(json.getString("tag_name"), asset.getString("browser_download_url"), json.optString("body"))
    }

    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun isSmartTubeInstalled(context: Context) =
        SMARTTUBE_PACKAGES.any { context.packageManager.launchIntent(it) != null }

    fun openSmartTube(context: Context): Boolean {
        val intent = SMARTTUBE_PACKAGES.firstNotNullOfOrNull { context.packageManager.launchIntent(it) } ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

fun android.content.pm.PackageManager.launchIntent(pkg: String): Intent? =
    getLaunchIntentForPackage(pkg) ?: getLeanbackLaunchIntentForPackage(pkg)
