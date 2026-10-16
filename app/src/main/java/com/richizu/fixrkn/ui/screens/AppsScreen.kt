package com.richizu.fixrkn.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.richizu.fixrkn.core.DefaultApps
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.service.RunState
import com.richizu.fixrkn.ui.kit.Btn
import com.richizu.fixrkn.ui.kit.Pill
import com.richizu.fixrkn.ui.kit.Tone
import com.richizu.fixrkn.ui.kit.press
import com.richizu.fixrkn.ui.kit.toast
import com.richizu.fixrkn.ui.theme.C
import com.richizu.fixrkn.ui.theme.LocalTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppInfo(val pkg: String, val label: String, val system: Boolean, val icon: ImageBitmap?)

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tv = LocalTv.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var selected by remember { mutableStateOf(Settings.routedApps) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val launchable = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0))
                .map { it.activityInfo.packageName }.toSet()
            pm.getInstalledApplications(0)
                .filter { it.packageName != ctx.packageName }
                .map { info ->
                    val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && info.packageName !in launchable
                    AppInfo(
                        info.packageName, pm.getApplicationLabel(info).toString(), system,
                        runCatching { pm.getApplicationIcon(info).toBitmap(72, 72).asImageBitmap() }.getOrNull()
                    )
                }
                .sortedWith(compareByDescending<AppInfo> { it.pkg in Settings.routedApps }.thenBy { it.label.lowercase() })
        }
    }

    fun save(new: Set<String>) {
        selected = new
        Settings.routedApps = new
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().then(if (tv) Modifier else Modifier.statusBarsPadding())) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.press(RoundedCornerShape(50), onClick = onBack).padding(10.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = C.Text)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Приложения", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Через обход: ${selected.size}", color = C.Sub, fontSize = 13.sp)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!tv) OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск", color = C.Muted) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line, focusedTextColor = C.Text, unfocusedTextColor = C.Text)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Рекомендуемые", Modifier.weight(1f), Tone.Secondary) { save(DefaultApps.all); ctx.toast("Выбраны рекомендуемые приложения") }
                    Btn(if (showSystem) "Скрыть системные" else "Системные", Modifier.weight(1f), Tone.Secondary) { showSystem = !showSystem }
                }
                if (Control.vpn.value == RunState.On) Text("Изменения применятся после перезапуска обхода", color = C.Muted, fontSize = 12.sp)
            }
            val list = apps
            if (list == null) {
                Text("Загрузка…", color = C.Sub, modifier = Modifier.padding(16.dp))
            } else {
                val q = query.trim().lowercase()
                val shown = list.filter { (showSystem || !it.system || it.pkg in selected) && (q.isEmpty() || q in it.label.lowercase() || q in it.pkg) }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown, key = { it.pkg }) { app ->
                        val on = app.pkg in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .press(RoundedCornerShape(14.dp)) { save(if (on) selected - app.pkg else selected + app.pkg) }
                                .background(if (on) C.Surface2 else C.Bg)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) Image(app.icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                            else Spacer(Modifier.size(36.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(app.label, color = C.Text, fontSize = 15.sp, maxLines = 1)
                                Text(app.pkg, color = C.Muted, fontSize = 11.sp, maxLines = 1)
                            }
                            if (app.pkg in DefaultApps.youtube) Pill("YouTube")
                            Spacer(Modifier.width(6.dp))
                            Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = C.Accent, uncheckedColor = C.Muted))
                        }
                    }
                }
            }
        }
    }
}
