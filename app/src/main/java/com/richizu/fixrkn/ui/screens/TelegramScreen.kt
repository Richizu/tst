package com.richizu.fixrkn.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.service.RunState
import com.richizu.fixrkn.tg.TgProxy
import com.richizu.fixrkn.tg.TgRelay
import com.richizu.fixrkn.ui.Actions
import com.richizu.fixrkn.ui.kit.Body
import com.richizu.fixrkn.ui.kit.Btn
import com.richizu.fixrkn.ui.kit.Card
import com.richizu.fixrkn.ui.kit.Confirm
import com.richizu.fixrkn.ui.kit.Dot
import com.richizu.fixrkn.ui.kit.Item
import com.richizu.fixrkn.ui.kit.Qr
import com.richizu.fixrkn.ui.kit.Screen
import com.richizu.fixrkn.ui.kit.ScreenTitle
import com.richizu.fixrkn.ui.kit.SectionTitle
import com.richizu.fixrkn.ui.kit.Sheet
import com.richizu.fixrkn.ui.kit.SwitchRow
import com.richizu.fixrkn.ui.kit.Tone
import com.richizu.fixrkn.ui.kit.ValueText
import com.richizu.fixrkn.ui.kit.copy
import com.richizu.fixrkn.ui.kit.formatBytes
import com.richizu.fixrkn.ui.kit.toast
import com.richizu.fixrkn.ui.theme.C
import com.richizu.fixrkn.ui.theme.LocalTv

@Composable
fun TelegramScreen() {
    val ctx = LocalContext.current
    val tv = LocalTv.current
    val state by Control.telegram.collectAsState()
    val stats by TgProxy.stats.collectAsState()
    Settings.version.collectAsState().value
    val running = state == RunState.On
    var editPort by remember { mutableStateOf(false) }
    var editDc by remember { mutableStateOf(false) }
    var editPool by remember { mutableStateOf(false) }
    var confirmSecret by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(tv) }

    fun restartIfRunning() {
        if (running) {
            Control.stopTelegram(ctx)
            ctx.toast("Перезапустите прокси, чтобы применить")
        }
    }

    Screen {
        ScreenTitle("Прокси Telegram", "Встроенный MTProto-прокси через WebSocket")

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (running) C.Green else if (state == RunState.Failed) C.Red else C.Muted, 10.dp)
                Text(
                    when (state) {
                        RunState.On -> "Работает на 127.0.0.1:${Settings.tgPort}"
                        RunState.Starting -> "Запуск…"
                        RunState.Failed -> "Не удалось запустить (порт занят?)"
                        else -> "Выключен"
                    },
                    color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Body("Telegram подключается к прокси на этом устройстве, а тот передаёт трафик через WebSocket — как веб-версия Telegram. Прокси работает и без VPN-обхода.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (running) Btn("Остановить", Modifier.weight(1f), Tone.Danger) { Control.stopTelegram(ctx) }
                else Btn("Запустить", Modifier.weight(1f)) { Control.startTelegram(ctx) }
                Btn("Подключить в Telegram", Modifier.weight(1.4f), Tone.Secondary, icon = Icons.AutoMirrored.Filled.Send) {
                    if (!running) Control.startTelegram(ctx, openTelegram = true)
                    else if (!Actions.linkTelegram(ctx)) ctx.toast("Telegram не установлен")
                }
            }
        }

        if (running) {
            Card {
                Text("Статистика", color = C.Text, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("Активных", stats.active.toString(), Modifier.weight(1f))
                    MiniStat("Всего", stats.total.toString(), Modifier.weight(1f))
                    MiniStat("Принято", formatBytes(stats.bytesDown), Modifier.weight(1f))
                    MiniStat("Отправлено", formatBytes(stats.bytesUp), Modifier.weight(1f))
                }
                Body("Маршруты: WebSocket ${stats.viaWs} · Cloudflare ${stats.viaCf} · TCP ${stats.viaTcp}" + if (stats.failed > 0) " · ошибок ${stats.failed}" else "", C.Muted, 12)
            }
        }

        Card {
            Text("Ссылка для Telegram", color = C.Text, fontWeight = FontWeight.SemiBold)
            Text(TgProxy.link(), color = C.Sub, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            if (showQr) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Qr(TgProxy.webLink()) }
                Body("Ссылка ведёт на это же устройство (127.0.0.1), поэтому открывайте её в Telegram, установленном здесь.", C.Muted, 12)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!tv) Btn("Копировать", Modifier.weight(1f), Tone.Secondary) { ctx.copy("tg proxy", TgProxy.link()) }
                if (!tv) Btn("Поделиться", Modifier.weight(1f), Tone.Secondary) {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, TgProxy.webLink()), "Ссылка на прокси"))
                }
                Btn(if (showQr) "Скрыть QR" else "QR-код", Modifier.weight(1f), Tone.Secondary) { showQr = !showQr }
            }
        }

        Actions.aggressiveFirmware()?.let { fw ->
            if (!Settings.tgWakeLock && !Settings.tgWakeHintHidden) Card(color = C.AmberSoft) {
                Text("Совет для $fw", color = C.Text, fontWeight = FontWeight.SemiBold)
                Body("Эта прошивка может усыплять прокси в фоне, и Telegram будет долго подключаться. Включите «Не давать засыпать» ниже.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Скрыть", Modifier.weight(1f), Tone.Secondary) { Settings.tgWakeHintHidden = true }
                    Btn("Включить", Modifier.weight(1f)) { Settings.tgWakeLock = true; restartIfRunning() }
                }
            }
        }

        SectionTitle("Настройки прокси")
        Card(padding = 4.dp) {
            Item("Порт", "Если порт занят другим приложением, выберите другой", Icons.Default.Numbers, onClick = { editPort = true }) { ValueText(Settings.tgPort.toString()) }
            Item("Ключ (secret)", "Создать новый — Telegram нужно будет подключить заново", Icons.Default.Key, onClick = { confirmSecret = true })
            SwitchRow("Через Cloudflare", "Резервный маршрут через сеть Cloudflare, если прямой WebSocket заблокирован", Icons.Default.Cloud, Settings.tgCloudflare) {
                Settings.tgCloudflare = it; TgProxy.applySettings()
            }
            if (Settings.tgCloudflare) SwitchRow("Сначала Cloudflare", "Пробовать Cloudflare раньше прямого подключения", Icons.Default.Speed, Settings.tgCloudflareFirst) {
                Settings.tgCloudflareFirst = it; TgProxy.applySettings()
            }
            Item(
                "Адреса дата-центров", if (Settings.tgDcIps.isBlank()) "Не заданы — прямой WebSocket не используется" else Settings.tgDcIps,
                Icons.Default.Dns, onClick = { editDc = true }
            )
            Item("Заранее открытые соединения", "Ускоряют подключение, но расходуют немного трафика", Icons.Default.Layers, onClick = { editPool = true }) {
                ValueText(Settings.tgPoolSize.toString())
            }
            SwitchRow("Не давать засыпать", "Держит процессор активным, пока работает прокси (больше расход батареи)", Icons.Default.BatteryChargingFull, Settings.tgWakeLock) {
                Settings.tgWakeLock = it; restartIfRunning()
            }
        }
        Body("Если Telegram перестал подключаться после выключения прокси, отключите прокси в самом Telegram: Настройки → Данные и память → Прокси.", C.Muted, 12)
    }

    if (editPort) NumberSheet("Порт прокси", "От 1024 до 65535", Settings.tgPort, 1024..65535, { editPort = false }) {
        Settings.tgPort = it; Settings.tgLinked = false; restartIfRunning()
    }
    if (editPool) NumberSheet("Заранее открытые соединения", "От 0 до 8. 0 — открывать только по запросу", Settings.tgPoolSize, 0..8, { editPool = false }) {
        Settings.tgPoolSize = it; TgProxy.applySettings()
    }
    if (editDc) DcSheet { editDc = false }
    if (confirmSecret) Confirm(
        "Создать новый ключ?", "Старая ссылка перестанет работать, прокси нужно будет подключить в Telegram заново.", "Создать",
        onDismiss = { confirmSecret = false }
    ) { TgProxy.regenerateSecret(); restartIfRunning() }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = C.Muted, fontSize = 11.sp)
        Text(value, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun NumberSheet(title: String, hint: String, value: Int, range: IntRange, onClose: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()?.takeIf { it in range }
    Sheet(onClose, title, hint) {
        OutlinedTextField(
            text, { text = it.filter(Char::isDigit).take(5) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = parsed == null, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line, focusedTextColor = C.Text, unfocusedTextColor = C.Text)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Отмена", Modifier.weight(1f), Tone.Secondary, onClick = onClose)
            Btn("Сохранить", Modifier.weight(1f), enabled = parsed != null) { onSave(parsed!!); onClose() }
        }
    }
}

@Composable
private fun DcSheet(onClose: () -> Unit) {
    var text by remember { mutableStateOf(Settings.tgDcIps) }
    val parsed = TgRelay.parseDcIps(text)
    Sheet(
        onClose, "Адреса дата-центров",
        "Формат: номер:IP через запятую, например «2:149.154.167.220, 4:149.154.167.220». Для указанных DC прокси будет подключаться к WebSocket Telegram напрямую. Оставьте пустым, чтобы использовать Cloudflare и TCP."
    ) {
        OutlinedTextField(
            text, { text = it }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line, focusedTextColor = C.Text, unfocusedTextColor = C.Text)
        )
        Body(if (parsed.isEmpty()) "Ничего не задано" else "Распознано: " + parsed.entries.joinToString { "DC${it.key} → ${it.value}" }, C.Muted, 12)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Пример", Modifier.weight(1f), Tone.Secondary) { text = "2:149.154.167.220, 4:149.154.167.220" }
            Btn("Сохранить", Modifier.weight(1f)) {
                Settings.tgDcIps = parsed.entries.joinToString(", ") { "${it.key}:${it.value}" }
                TgProxy.applySettings()
                onClose()
            }
        }
    }
}
