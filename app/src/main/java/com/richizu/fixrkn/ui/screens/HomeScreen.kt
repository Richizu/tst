package com.richizu.fixrkn.ui.screens

import android.app.Activity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richizu.fixrkn.bypass.Scanner
import com.richizu.fixrkn.bypass.StrategyStore
import com.richizu.fixrkn.core.Network
import com.richizu.fixrkn.core.NetworkKind
import com.richizu.fixrkn.core.Player
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.service.RunState
import com.richizu.fixrkn.tg.TgProxy
import com.richizu.fixrkn.ui.Actions
import com.richizu.fixrkn.ui.Guide
import com.richizu.fixrkn.ui.LocalNavigate
import com.richizu.fixrkn.ui.LocalStarter
import com.richizu.fixrkn.ui.Tab
import com.richizu.fixrkn.ui.kit.Body
import com.richizu.fixrkn.ui.kit.Btn
import com.richizu.fixrkn.ui.kit.Card
import com.richizu.fixrkn.ui.kit.Dot
import com.richizu.fixrkn.ui.kit.Pill
import com.richizu.fixrkn.ui.kit.Progress
import com.richizu.fixrkn.ui.kit.Screen
import com.richizu.fixrkn.ui.kit.Tone
import com.richizu.fixrkn.ui.kit.formatBytes
import com.richizu.fixrkn.ui.kit.formatDuration
import com.richizu.fixrkn.ui.kit.press
import com.richizu.fixrkn.ui.kit.toast
import com.richizu.fixrkn.ui.theme.C
import com.richizu.fixrkn.ui.theme.LocalLightUi
import com.richizu.fixrkn.update.ApkInstaller
import com.richizu.fixrkn.update.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val navigate = LocalNavigate.current
    val starter = LocalStarter.current
    val vpn by Control.vpn.collectAsState()
    val tg by Control.telegram.collectAsState()
    val traffic by Control.traffic.collectAsState()
    val since by Control.connectedSince.collectAsState()
    val scan by Scanner.state.collectAsState()
    val lastError by Control.lastError.collectAsState()
    Settings.version.collectAsState().value
    val entries by StrategyStore.entries.collectAsState()

    var network by remember { mutableStateOf(Network.currentKind(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            network = Network.currentKind(ctx)
            delay(1000)
        }
    }

    val on = Control.anyRunning
    val starting = vpn == RunState.Starting || tg == RunState.Starting
    val active = Settings.activeStrategy
    val activeEntry = entries.firstOrNull { it.command == active }

    Screen {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("RichizuFixRKN", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(statusText(vpn, tg), color = if (on) C.Green else C.Sub, fontSize = 14.sp)
            }
            NetworkPill(network)
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PowerButton(
                on = on, starting = starting, paused = vpn == RunState.Paused,
                onClick = {
                    when {
                        on || vpn == RunState.Paused -> Control.stopAll(ctx)
                        !Settings.youtubeEnabled && !Settings.telegramEnabled -> ctx.toast("Включите хотя бы один сервис в настройках")
                        else -> starter.start(false, false)
                    }
                },
                onLongClick = {
                    if (!on) {
                        ctx.toast("Запускаю и сворачиваю приложение")
                        starter.start(false, true)
                    } else (ctx as? Activity)?.moveTaskToBack(true)
                }
            )
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    starting -> "Подключение…"
                    vpn == RunState.Paused -> "Обход на паузе"
                    on -> "Защита включена"
                    vpn == RunState.Failed -> "Не удалось запустить. Попробуйте снова"
                    else -> "Нажмите, чтобы включить"
                },
                color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                if (on) "Удерживайте кнопку, чтобы свернуть приложение" else "Удерживайте, чтобы включить и свернуть",
                color = C.Muted, fontSize = 12.sp
            )
        }

        if (vpn == RunState.Failed && lastError != null) {
            Card(color = C.RedSoft) {
                Text("Обход не запустился", color = C.Text, fontWeight = FontWeight.SemiBold)
                Body(lastError!!, C.Text)
                Body("Подробности — в Настройки → Журнал работы.", C.Muted, 12)
            }
        }

        if (on) {
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Время работы", if (since > 0) formatDuration(now - since) else "—", Modifier.weight(1f))
                    Stat("Загрузка", if (vpn == RunState.On) "${formatBytes(traffic.rxRate)}/с" else "—", Modifier.weight(1f))
                    Stat("Отправка", if (vpn == RunState.On) "${formatBytes(traffic.txRate)}/с" else "—", Modifier.weight(1f))
                }
            }
        }

        if (scan.running) {
            Card(Modifier.press { navigate(Tab.Bypass) }) {
                Text(if (scan.finalPhase) "Проверяем лучшие способы" else "Подбираем способ обхода", color = C.Text, fontWeight = FontWeight.SemiBold)
                Progress(if (scan.total == 0) null else scan.done.toFloat() / scan.total)
                Body("${scan.done} из ${scan.total} · найдено рабочих: ${scan.found}")
            }
        } else if (Settings.youtubeEnabled && !Settings.setupDone) {
            Card(color = C.AccentSoft) {
                Text("Подберите способ обхода", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Body("Приложение проверит десятки стратегий на вашем провайдере и выберет лучшую. Это займёт несколько минут.")
                Btn("Начать подбор", Modifier.fillMaxWidth()) {
                    Scanner.start(ctx)
                    navigate(Tab.Bypass)
                }
            }
        } else if (Settings.youtubeEnabled && !Settings.youtubeVerified && active != null) {
            Card {
                Text("Проверьте YouTube", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Body("Откройте видео и вернитесь в приложение — мы спросим, всё ли работает. Если нет, попробуем следующий способ.")
                Btn("Открыть и проверить", Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow) {
                    if (vpn != RunState.On) starter.start(false, false)
                    Guide.openAndCheck(ctx)
                }
            }
        }

        if (Settings.youtubeEnabled) YoutubeTile(vpn, activeEntry?.result?.label, network)
        if (Settings.telegramEnabled) TelegramTile(tg)
    }
}

private fun statusText(vpn: RunState, tg: RunState): String = when {
    vpn == RunState.On && tg == RunState.On -> "Обход и прокси Telegram работают"
    vpn == RunState.On -> "Обход блокировок работает"
    tg == RunState.On && vpn == RunState.Paused -> "Обход на паузе, прокси Telegram работает"
    tg == RunState.On -> "Работает прокси Telegram"
    vpn == RunState.Paused -> "Обход на паузе"
    else -> "Выключено"
}

@Composable
private fun NetworkPill(kind: NetworkKind?) {
    val icon = if (kind == NetworkKind.Mobile) Icons.Default.SignalCellularAlt else Icons.Default.Wifi
    Row(
        Modifier.clip(CircleShape).background(C.Surface2).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (kind == null) C.Red else C.Sub, modifier = Modifier.size(16.dp))
        Text(kind?.short ?: "Нет сети", color = C.Sub, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PowerButton(on: Boolean, starting: Boolean, paused: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val light = LocalLightUi.current
    val haptic = LocalHapticFeedback.current
    val color by animateColorAsState(
        when {
            starting -> C.Accent
            paused -> C.Amber
            on -> C.Green
            else -> C.Surface3
        }, label = "power"
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow"
    )
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val haloAlpha = if (light || !(on || starting)) 0.35f else glow
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Box(
            Modifier.size(220.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.35f * haloAlpha), Color.Transparent)))
        )
        Box(
            Modifier
                .size(156.dp)
                .scale(if (focused) 1.06f else 1f)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.7f))))
                .border(if (focused) 3.dp else 1.dp, if (focused) C.Text else C.Line, CircleShape)
                .combinedClickable(
                    interactionSource = source, indication = androidx.compose.material3.ripple(),
                    onClick = {
                        if (Settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                    onLongClick = {
                        if (Settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PowerSettingsNew, "Вкл/выкл", tint = if (on || starting || paused) Color.White else C.Sub, modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Text(value, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ServiceTile(
    title: String,
    subtitle: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    status: Pair<String, Color>?,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {}
) {
    Card(Modifier.press(RoundedCornerShape(20.dp), onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = C.Sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (status != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(status.second)
                Text(status.first, color = C.Sub, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        extra()
    }
}

@Composable
private fun YoutubeTile(vpn: RunState, label: String?, network: NetworkKind?) {
    val ctx = LocalContext.current
    val starter = LocalStarter.current
    val scope = rememberCoroutineScope()
    val downloads by ApkInstaller.progress.collectAsState()
    val smartTube = Settings.player == Player.SmartTube
    val needsInstall = smartTube && !Updater.isSmartTubeInstalled(ctx)
    val downloading = downloads.isNotEmpty() && smartTube
    val name = if (smartTube) "SmartTube" else "YouTube"
    val subtitle = when {
        downloading -> "Скачивание SmartTube: ${((downloads.values.firstOrNull() ?: 0f) * 100).toInt()}%"
        needsInstall -> "Нажмите, чтобы скачать и установить SmartTube"
        label != null -> "Способ: $label" + (network?.let { " · ${it.short}" } ?: "")
        else -> "Способ обхода ещё не выбран"
    }
    ServiceTile(
        title = name, subtitle = subtitle, accent = C.YouTube, icon = Icons.Default.PlayArrow,
        status = if (vpn == RunState.On) "обход" to C.Green else null,
        onClick = {
            if (needsInstall) {
                if (!downloading) scope.launch {
                    val url = Updater.smartTubeApk()
                    if (url == null) ctx.toast("Не удалось найти SmartTube") else ApkInstaller.downloadAndInstall(ctx, url, "smarttube")
                }
                return@ServiceTile
            }
            if (vpn != RunState.On && vpn != RunState.Starting) starter.start(false, false)
            if (!Actions.openYoutube(ctx)) ctx.toast("$name не установлен")
        }
    )
}

@Composable
private fun TelegramTile(tg: RunState) {
    val ctx = LocalContext.current
    val starter = LocalStarter.current
    val stats by TgProxy.stats.collectAsState()
    val linked = Settings.tgLinked
    ServiceTile(
        title = "Telegram",
        subtitle = when {
            tg == RunState.On && !linked -> "Нажмите, чтобы подключить прокси в Telegram"
            tg == RunState.On -> "Прокси работает · активных соединений: ${stats.active}"
            !linked -> "Встроенный прокси: нажмите, чтобы включить и подключить"
            else -> "Прокси выключен"
        },
        accent = C.Telegram, icon = Icons.AutoMirrored.Filled.Send,
        status = if (tg == RunState.On) "прокси" to C.Green else null,
        onClick = {
            when {
                tg != RunState.On -> {
                    if (Settings.telegramEnabled) Control.startTelegram(ctx, openTelegram = !linked)
                    else starter.start(true, false)
                }
                !linked -> if (!Actions.linkTelegram(ctx)) ctx.toast("Telegram не установлен")
                else -> if (!Actions.openTelegram(ctx)) ctx.toast("Telegram не установлен")
            }
        }
    )
}
