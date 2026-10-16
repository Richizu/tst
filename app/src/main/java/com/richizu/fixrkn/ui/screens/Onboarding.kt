package com.richizu.fixrkn.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.richizu.fixrkn.R
import com.richizu.fixrkn.bypass.Scanner
import com.richizu.fixrkn.core.Player
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.ui.Actions
import com.richizu.fixrkn.ui.kit.Body
import com.richizu.fixrkn.ui.kit.Btn
import com.richizu.fixrkn.ui.kit.Tone
import com.richizu.fixrkn.ui.kit.press
import com.richizu.fixrkn.ui.theme.C

private enum class Step { Welcome, Device, Services, Permissions, Finish }

@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(Step.Welcome) }
    var tv by remember { mutableStateOf(ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) }
    var youtube by remember { mutableStateOf(true) }
    var telegram by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Dots(step.ordinal, Step.entries.size)
            AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { s ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (s) {
                        Step.Welcome -> Welcome { step = Step.Device }
                        Step.Device -> DeviceStep(tv, { tv = it }) {
                            Settings.tvMode = tv
                            if (tv) Settings.player = Player.SmartTube
                            step = Step.Services
                        }
                        Step.Services -> ServicesStep(youtube, telegram, { youtube = it }, { telegram = it }) {
                            Settings.youtubeEnabled = youtube
                            Settings.telegramEnabled = telegram
                            step = Step.Permissions
                        }
                        Step.Permissions -> PermissionsStep(youtube) { step = Step.Finish }
                        Step.Finish -> FinishStep(youtube, onScan = {
                            Settings.onboarded = true
                            onDone()
                            Scanner.start(it)
                        }, onLater = {
                            Settings.onboarded = true
                            onDone()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Dots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(Modifier.height(6.dp).size(width = if (i == current) 24.dp else 6.dp, height = 6.dp).clip(CircleShape).background(if (i <= current) C.Accent else C.Surface3))
        }
    }
}

@Composable
private fun Title(text: String, sub: String) {
    Text(text, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    Body(sub, size = 15)
}

@Composable
private fun Welcome(next: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)))
    }
    Text("RichizuFixRKN", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Body("Одна кнопка — и YouTube, Discord и Telegram снова работают. Без внешних серверов: всё происходит на вашем устройстве.", size = 15)
    Feature(Icons.Default.PlayCircle, "Обход блокировок", "Подбирает способ обхода под вашего провайдера")
    Feature(Icons.AutoMirrored.Filled.Send, "Прокси Telegram", "Встроенный, работает даже без VPN")
    Feature(Icons.Default.Tv, "Телефоны и телевизоры", "Удобно управлять и пальцем, и пультом")
    Btn("Начать", Modifier.fillMaxWidth(), onClick = next)
}

@Composable
private fun Feature(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.Surface2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.Accent)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = C.Text, fontWeight = FontWeight.SemiBold)
            Text(sub, color = C.Sub, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().press(RoundedCornerShape(18.dp), onClick = onClick)
            .background(if (selected) C.AccentSoft else C.Surface, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) C.Accent else C.Sub, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = C.Sub, fontSize = 13.sp)
        }
        if (selected) Icon(Icons.Default.CheckCircle, null, tint = C.Accent)
    }
}

@Composable
private fun DeviceStep(tv: Boolean, onTv: (Boolean) -> Unit, next: () -> Unit) {
    Title("Где будет работать приложение?", "От этого зависит интерфейс и настройки обхода.")
    Option(Icons.Default.PhoneAndroid, "Телефон или планшет", "Сенсорный экран", !tv) { onTv(false) }
    Option(Icons.Default.Tv, "Телевизор или приставка", "Управление пультом, плеер SmartTube", tv) { onTv(true) }
    Btn("Далее", Modifier.fillMaxWidth(), onClick = next)
}

@Composable
private fun ServicesStep(youtube: Boolean, telegram: Boolean, onYoutube: (Boolean) -> Unit, onTelegram: (Boolean) -> Unit, next: () -> Unit) {
    Title("Что нужно починить?", "Можно выбрать оба. Изменить потом можно в настройках.")
    Option(Icons.Default.PlayCircle, "YouTube и сайты", "VPN-обход: YouTube, Discord, Instagram и другие", youtube) { onYoutube(!youtube) }
    Option(Icons.AutoMirrored.Filled.Send, "Telegram", "Встроенный прокси: быстрые фото, видео и звонки", telegram) { onTelegram(!telegram) }
    Btn("Далее", Modifier.fillMaxWidth(), enabled = youtube || telegram, onClick = next)
}

@Composable
private fun PermissionsStep(needVpn: Boolean, next: () -> Unit) {
    val ctx = LocalContext.current
    var notifications by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    var vpn by remember { mutableStateOf(Control.vpnConsentIntent(ctx) == null) }
    var battery by remember { mutableIntStateOf(if (Actions.isIgnoringBatteryOptimizations(ctx)) 1 else 0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = true }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vpn = it.resultCode == Activity.RESULT_OK || Control.vpnConsentIntent(ctx) == null
    }

    Title("Разрешения", "Нужны, чтобы обход и прокси работали в фоне.")
    Option(Icons.Default.Notifications, "Уведомления", if (notifications) "Разрешено" else "Показывают, что защита работает", notifications) {
        if (!notifications && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (needVpn) Option(Icons.Default.VpnKey, "Создание VPN", if (vpn) "Разрешено" else "Стандартный запрос Android, трафик не покидает устройство", vpn) {
        val intent = Control.vpnConsentIntent(ctx)
        if (intent == null) vpn = true
        else try {
            vpnLauncher.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Control.consentUnavailable.value = true
        }
    }
    Option(Icons.Default.BatterySaver, "Работа в фоне", if (battery == 1) "Разрешено" else "Чтобы система не останавливала защиту (по желанию)", battery == 1) {
        Actions.requestIgnoreBatteryOptimizations(ctx)
        battery = 1
    }
    Btn("Далее", Modifier.fillMaxWidth(), onClick = next)
}

@Composable
private fun FinishStep(youtube: Boolean, onScan: (android.content.Context) -> Unit, onLater: () -> Unit) {
    val ctx = LocalContext.current
    Title("Почти готово", if (youtube) "Осталось подобрать способ обхода для вашего провайдера. Приложение проверит десятки вариантов и выберет лучший — это займёт несколько минут." else "Нажмите большую кнопку на главном экране, чтобы включить прокси Telegram.")
    if (youtube) {
        Btn("Подобрать способ обхода", Modifier.fillMaxWidth()) { onScan(ctx) }
        Btn("Позже", Modifier.fillMaxWidth(), Tone.Ghost, onClick = onLater)
    } else {
        Btn("Готово", Modifier.fillMaxWidth(), onClick = onLater)
    }
    Spacer(Modifier.height(8.dp))
}
