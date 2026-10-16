package com.richizu.fixrkn.ui.kit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.ui.theme.C

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

fun Context.copy(label: String, text: String, toast: String = "Скопировано") {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    toast(toast)
}

fun Context.clipboardText(): String? =
    getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()

/** Opens a URL; returns false when no app can handle it. */
fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

/** Shows a QR code instead of opening links (TV or the "QR codes" setting). Set by the app shell. */
val LocalShowQr = compositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

@Composable
fun rememberLinkOpener(): (url: String, label: String) -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val showQr = LocalShowQr.current
    return remember(ctx, showQr) {
        { url, label ->
            if (Settings.qrLinks || !ctx.openUrl(url)) showQr(url, label)
        }
    }
}

fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}

@Composable
fun Qr(text: String, size: Dp = 220.dp) {
    val bmp = remember(text) { qrBitmap(text).asImageBitmap() }
    Image(
        bmp, null, filterQuality = FilterQuality.None,
        modifier = Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp)
    )
}

@Composable
fun QrSheet(url: String, label: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Sheet(onDismiss, label, "Наведите камеру телефона на код, чтобы открыть ссылку.") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Qr(url) }
        Body(url, C.Muted, 12)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Копировать", Modifier.weight(1f), Tone.Secondary) { ctx.copy(label, url) }
            Btn("Закрыть", Modifier.weight(1f), onClick = onDismiss)
        }
    }
}

fun formatBytes(bytes: Long): String {
    val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "${bytes} ${units[0]}" else String.format(java.util.Locale.US, "%.1f %s", v, units[i])
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s} с"
        s < 3600 -> "${s / 60} мин ${s % 60} с"
        else -> "${s / 3600} ч ${(s % 3600) / 60} мин"
    }
}
