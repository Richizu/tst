package com.richizu.fixrkn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richizu.fixrkn.BuildConfig
import com.richizu.fixrkn.bypass.Scanner
import com.richizu.fixrkn.core.Player
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.ui.Guide
import com.richizu.fixrkn.ui.LocalNavigate
import com.richizu.fixrkn.ui.Tab
import com.richizu.fixrkn.ui.kit.Body
import com.richizu.fixrkn.ui.kit.Btn
import com.richizu.fixrkn.ui.kit.Progress
import com.richizu.fixrkn.ui.kit.Sheet
import com.richizu.fixrkn.ui.kit.Tone
import com.richizu.fixrkn.ui.kit.copy
import com.richizu.fixrkn.ui.theme.C
import com.richizu.fixrkn.ui.theme.LocalTv
import com.richizu.fixrkn.update.ApkInstaller
import com.richizu.fixrkn.update.Updater
import kotlinx.coroutines.launch

/** Offers a newer version found on GitHub. */
@Composable
fun UpdateSheet() {
    val release by Updater.available.collectAsState()
    val downloads by ApkInstaller.progress.collectAsState()
    var dismissed by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val r = release ?: return
    if (dismissed) return
    val p = downloads[r.apkUrl]
    Sheet({ dismissed = true }, "Доступно обновление", "Версия ${r.version} (у вас ${BuildConfig.VERSION_NAME}).") {
        if (r.notes.isNotBlank()) Body(r.notes.take(1200))
        if (p != null) {
            Progress(p)
            Body("Скачивание: ${(p * 100).toInt()}%")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Позже", Modifier.weight(1f), Tone.Secondary) { dismissed = true }
                Btn("Обновить", Modifier.weight(1f)) {
                    scope.launch { ApkInstaller.downloadAndInstall(ctx, r.apkUrl, "update") }
                }
            }
        }
    }
}

/** Questions of the "check YouTube yourself" flow. */
@Composable
fun GuideSheets() {
    val ask by Guide.ask.collectAsState()
    val ctx = LocalContext.current
    val navigate = LocalNavigate.current
    when (ask) {
        Guide.Ask.None -> Unit
        Guide.Ask.DidItWork -> Sheet(Guide::dismiss, "YouTube работает?", "Видео открываются и воспроизводятся без долгой загрузки?") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Нет", Modifier.weight(1f), Tone.Danger) { Guide.answer(ctx, false) }
                Btn("Да, работает", Modifier.weight(1f), Tone.Success) { Guide.answer(ctx, true) }
            }
        }
        Guide.Ask.Exhausted -> Sheet(Guide::dismiss, "Подходящие способы закончились") {
            Body(
                if (Settings.tvMode && Settings.player != Player.SmartTube)
                    "Официальное приложение YouTube на телевизорах часто не работает через обход. Попробуйте SmartTube — его можно выбрать в настройках, приложение скачает его само."
                else "Попробуйте подобрать способы заново (например, в другое время или в другой сети) или добавьте свою стратегию."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Settings.tvMode && Settings.player != Player.SmartTube) {
                    Btn("Выбрать SmartTube", Modifier.weight(1f)) {
                        Settings.player = Player.SmartTube
                        Guide.dismiss()
                        navigate(Tab.Home)
                    }
                } else {
                    Btn("Подобрать заново", Modifier.weight(1f)) {
                        Guide.dismiss()
                        Scanner.start(ctx)
                        navigate(Tab.Bypass)
                    }
                }
                Btn("Закрыть", Modifier.weight(1f), Tone.Secondary) { Guide.dismiss() }
            }
        }
    }
}

/** Some TV firmwares have no VPN consent dialog; the permission can be granted over ADB. */
@Composable
fun ConsentUnavailableSheet() {
    val show by Control.consentUnavailable.collectAsState()
    if (!show) return
    val ctx = LocalContext.current
    val command = "adb shell appops set ${ctx.packageName} ACTIVATE_VPN allow"
    Sheet({ Control.consentUnavailable.value = false }, "Система не показывает запрос VPN") {
        Body("На этом устройстве нет окна разрешения VPN. Его можно выдать один раз с компьютера через ADB:")
        Text(
            command, color = C.Text, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface2).padding(12.dp)
        )
        Body("После этого снова нажмите кнопку включения. Прокси Telegram работает и без VPN.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!LocalTv.current) Btn("Копировать", Modifier.weight(1f), Tone.Secondary) { ctx.copy("adb", command) }
            Btn("Понятно", Modifier.weight(1f)) { Control.consentUnavailable.value = false }
        }
    }
}
