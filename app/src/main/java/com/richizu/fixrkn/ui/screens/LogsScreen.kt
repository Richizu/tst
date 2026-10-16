package com.richizu.fixrkn.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richizu.fixrkn.BuildConfig
import com.richizu.fixrkn.core.AppLog
import com.richizu.fixrkn.core.Network
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.ui.kit.Btn
import com.richizu.fixrkn.ui.kit.Tone
import com.richizu.fixrkn.ui.kit.copy
import com.richizu.fixrkn.ui.kit.press
import com.richizu.fixrkn.ui.theme.C
import com.richizu.fixrkn.ui.theme.LocalTv

private fun report(ctx: android.content.Context): String = buildString {
    appendLine("RichizuFixRKN ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, ABI ${Build.SUPPORTED_ABIS.joinToString()}")
    appendLine("Network: ${Network.currentKind(ctx)?.title ?: "none"}, private DNS: ${Network.privateDnsHost(ctx) ?: "off"}")
    appendLine("VPN: ${Control.vpn.value}, Telegram proxy: ${Control.telegram.value}, TV mode: ${Settings.tvMode}")
    appendLine("Strategy: ${Settings.activeStrategy ?: "none"}")
    appendLine("Routing: ${Settings.routeMode}, apps: ${Settings.routedApps.size}")
    appendLine("----")
    append(AppLog.dump())
}

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tv = LocalTv.current
    val revision by AppLog.revision.collectAsState()
    val entries = remember(revision) { AppLog.entries() }
    val list = rememberLazyListState()
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) list.scrollToItem(entries.lastIndex) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize().then(if (tv) Modifier else Modifier.statusBarsPadding())) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.press(RoundedCornerShape(50), onClick = onBack).padding(10.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = C.Text)
                }
                Text("Журнал", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!tv) Btn("Копировать", Modifier.weight(1f), Tone.Secondary) { ctx.copy("logs", report(ctx)) }
                if (!tv) Btn("Отправить", Modifier.weight(1f), Tone.Secondary) {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report(ctx)), "Журнал"))
                }
                Btn("Очистить", Modifier.weight(1f), Tone.Danger) { AppLog.clear() }
            }
            LazyColumn(state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entries.isEmpty()) item { Text("Записей пока нет", color = C.Sub) }
                items(entries) { e ->
                    Text(
                        e.format(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                        color = when (e.level) {
                            AppLog.Level.Error -> C.Red
                            AppLog.Level.Warn -> C.Amber
                            AppLog.Level.Info -> C.Text
                            AppLog.Level.Debug -> C.Muted
                        }
                    )
                }
            }
        }
    }
}
