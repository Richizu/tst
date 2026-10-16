package com.richizu.fixrkn.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.service.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads an APK and hands it to the system installer. */
object ApkInstaller {
    private const val TAG = "ApkInstaller"
    const val ACTION_STATUS = "com.richizu.fixrkn.INSTALL_STATUS"

    /** Download progress 0..1 per URL being downloaded; absent when idle. */
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val message = MutableStateFlow<String?>(null)

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun downloadAndInstall(context: Context, url: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!canInstall(app)) {
            withContext(Dispatchers.Main) { openInstallPermission(app) }
            message.value = "Разрешите установку из этого приложения и повторите"
            return@withContext false
        }
        val file = File(app.cacheDir, "$name.apk")
        try {
            progress.value = progress.value + (url to 0f)
            download(url, file) { p ->
                progress.value = progress.value + (url to p)
                Notifications.updateProgress(app, (p * 100).toInt())
            }
            install(app, file)
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Download/installation failed", e)
            message.value = "Не удалось скачать файл"
            false
        } finally {
            progress.value = progress.value - url
            Notifications.updateProgress(app, null)
        }
    }

    private fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        var current = url
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "RichizuFixRKN")
            val code = conn.responseCode
            if (code in 300..399 && redirects++ < 8) {
                current = URL(URL(current), conn.getHeaderField("Location")).toString()
                conn.disconnect()
                continue
            }
            if (code != 200) error("HTTP $code")
            break
        }
        val total = conn.contentLengthLong
        var done = 0L
        var lastReport = 0L
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = System.currentTimeMillis()
                    if (total > 0 && now - lastReport > 250) {
                        lastReport = now
                        onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        onProgress(1f)
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, id, Intent(ACTION_STATUS).setPackage(context.packageName), flags
            )
            session.commit(callback.intentSender)
        }
    }
}

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> ApkInstaller.message.value = "Установлено"
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                AppLog.w("ApkInstaller", "Install failed ($status): $msg")
                ApkInstaller.message.value = "Установка не завершена"
            }
        }
    }
}
