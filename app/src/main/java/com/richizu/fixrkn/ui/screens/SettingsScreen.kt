package com.richizu.fixrkn.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.richizu.fixrkn.BuildConfig
import com.richizu.fixrkn.bypass.StrategyStore
import com.richizu.fixrkn.core.Player
import com.richizu.fixrkn.core.RouteMode
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.service.RunState
import com.richizu.fixrkn.service.Watchdog
import com.richizu.fixrkn.ui.Actions
import com.richizu.fixrkn.ui.kit.Body
import com.richizu.fixrkn.ui.kit.Btn
import com.richizu.fixrkn.ui.kit.Card
import com.richizu.fixrkn.ui.kit.Choice
import com.richizu.fixrkn.ui.kit.Confirm
import com.richizu.fixrkn.ui.kit.Item
import com.richizu.fixrkn.ui.kit.Screen
import com.richizu.fixrkn.ui.kit.ScreenTitle
import com.richizu.fixrkn.ui.kit.SectionTitle
import com.richizu.fixrkn.ui.kit.Sheet
import com.richizu.fixrkn.ui.kit.SwitchRow
import com.richizu.fixrkn.ui.kit.Tone
import com.richizu.fixrkn.ui.kit.ValueText
import com.richizu.fixrkn.ui.kit.toast
import com.richizu.fixrkn.ui.theme.C
import com.richizu.fixrkn.update.Updater
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class Page { Main, Apps, Logs }

private val tunnelDnsOptions = listOf(
    "1.1.1.1" to "Cloudflare (1.1.1.1)",
    "8.8.8.8" to "Google (8.8.8.8)",
    "9.9.9.9" to "Quad9 (9.9.9.9)",
    "94.140.14.14" to "AdGuard (94.140.14.14)",
    "77.88.8.8" to "Яндекс (77.88.8.8)"
)

@Composable
fun SettingsScreen() {
    var page by remember { mutableStateOf(Page.Main) }
    BackHandler(enabled = page != Page.Main) { page = Page.Main }
    when (page) {
        Page.Main -> SettingsMain(onApps = { page = Page.Apps }, onLogs = { page = Page.Logs })
        Page.Apps -> AppsScreen(onBack = { page = Page.Main })
        Page.Logs -> LogsScreen(onBack = { page = Page.Main })
    }
}

@Composable
private fun SettingsMain(onApps: () -> Unit, onLogs: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Settings.version.collectAsState().value
    val checking by Updater.checking.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val vpnRunning = Control.vpn.collectAsState().value == RunState.On

    fun vpnHint() {
        if (vpnRunning) ctx.toast("Изменение применится после перезапуска обхода")
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val root = JSONObject().put("app", "RichizuFixRKN").put("version", 1)
                .put("settings", Settings.exportJson()).put("strategies", StrategyStore.exportJson())
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(root.toString(2).toByteArray()) }
        }.onSuccess { ctx.toast("Настройки сохранены") }.onFailure { ctx.toast("Не удалось сохранить файл") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val root = JSONObject(ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            require(root.optString("app") == "RichizuFixRKN")
            root.optJSONObject("settings")?.let(Settings::importJson)
            root.optJSONObject("strategies")?.let(StrategyStore::importJson)
        }.onSuccess { ctx.toast("Настройки загружены") }.onFailure { ctx.toast("Это не файл настроек RichizuFixRKN") }
    }

    Screen {
        ScreenTitle("Настройки")

        SectionTitle("Сервисы")
        Card(padding = 4.dp) {
            SwitchRow("YouTube и сайты", "VPN-обход блокировок (YouTube, Discord и др.)", Icons.Default.PlayCircle, Settings.youtubeEnabled) {
                Settings.youtubeEnabled = it
                if (!it) Control.stopBypass(ctx)
            }
            SwitchRow("Прокси Telegram", "Запускать вместе с главной кнопкой", Icons.AutoMirrored.Filled.Send, Settings.telegramEnabled) {
                Settings.telegramEnabled = it
                if (!it) Control.stopTelegram(ctx)
            }
        }

        SectionTitle("Что пускать через обход")
        Card(padding = 4.dp) {
            Item(
                "Режим", if (Settings.routeMode == RouteMode.SelectedApps) "Только выбранные приложения" else "Все приложения",
                Icons.Default.Route, onClick = { sheet = "route" }
            )
            if (Settings.routeMode == RouteMode.SelectedApps) {
                Item("Приложения", "Выбрано: ${Settings.routedApps.size}", Icons.Default.Apps, onClick = onApps)
            }
        }

        SectionTitle("Сеть")
        Card(padding = 4.dp) {
            Item("DNS внутри обхода", tunnelDnsOptions.firstOrNull { it.first == Settings.tunnelDns }?.second ?: Settings.tunnelDns, Icons.Default.Dns, onClick = { sheet = "dns" })
            Item("Частный DNS системы", "Помогает, если провайдер подменяет адреса сайтов", Icons.Default.VerifiedUser, onClick = { sheet = "private_dns" })
            SwitchRow("IPv6", "Пускать IPv6-трафик через обход", Icons.Default.Language, Settings.ipv6) { Settings.ipv6 = it; vpnHint() }
        }

        SectionTitle("Работа в фоне")
        Card(padding = 4.dp) {
            SwitchRow("Запуск при включении устройства", null, Icons.Default.PowerSettingsNew, Settings.startOnBoot) { Settings.startOnBoot = it }
            SwitchRow("Экономный режим", "Не перезапускать обход автоматически, если система его остановила", Icons.Default.Eco, Settings.economy) {
                Settings.economy = it
                if (it) Watchdog.cancel(ctx) else if (vpnRunning) Watchdog.schedule(ctx)
            }
            Item(
                "Оптимизация батареи",
                if (Actions.isIgnoringBatteryOptimizations(ctx)) "Отключена — приложение не будут усыплять" else "Включена — система может останавливать обход",
                Icons.Default.BatterySaver, onClick = { Actions.requestIgnoreBatteryOptimizations(ctx) }
            )
            if (Actions.hasVendorAutostart(ctx)) Item("Автозапуск (${Build.MANUFACTURER})", "Разрешите приложению работать в фоне в настройках прошивки", Icons.Default.RestartAlt, onClick = { Actions.openVendorAutostart(ctx) })
        }

        SectionTitle("Интерфейс")
        Card(padding = 4.dp) {
            SwitchRow("Режим телевизора", "Интерфейс для пульта; на ТВ обход блокирует QUIC для стабильной работы YouTube", Icons.Default.Tv, Settings.tvMode) { Settings.tvMode = it; vpnHint() }
            Item("Плеер YouTube", if (Settings.player == Player.SmartTube) "SmartTube" else "Официальный YouTube", Icons.Default.PlayCircle, onClick = { sheet = "player" })
            SwitchRow("QR-коды вместо ссылок", "Показывать QR-код, чтобы открыть ссылку на телефоне", Icons.Default.QrCode, Settings.qrLinks) { Settings.qrLinks = it }
            SwitchRow("Меньше анимаций", "Экономит батарею на слабых устройствах", Icons.Default.Animation, Settings.lightUi) { Settings.lightUi = it }
            SwitchRow("Вибрация", null, Icons.Default.Vibration, Settings.haptics) { Settings.haptics = it }
        }

        SectionTitle("Обновления")
        Card(padding = 4.dp) {
            SwitchRow("Проверять при запуске", null, Icons.Default.SystemUpdate, Settings.autoUpdate) { Settings.autoUpdate = it }
            Item("Проверить сейчас", if (checking) "Проверка…" else "Версия ${BuildConfig.VERSION_NAME}", Icons.Default.Download, onClick = {
                scope.launch {
                    val r = Updater.check()
                    ctx.toast(if (r != null) "Доступна версия ${r.version}" else Updater.lastError.value ?: "У вас последняя версия")
                }
            })
        }

        SectionTitle("Данные")
        Card(padding = 4.dp) {
            Item("Экспорт настроек", "Сохранить настройки и стратегии в файл", Icons.Default.Upload, onClick = { exportLauncher.launch("richizufixrkn-settings.json") })
            Item("Импорт настроек", "Загрузить из файла", Icons.Default.SettingsBackupRestore, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) })
            Item("Журнал работы", "Для поиска проблем", Icons.AutoMirrored.Filled.Article, onClick = onLogs)
            Item("Сбросить всё", "Вернуть приложение к первому запуску", Icons.Default.RestartAlt, iconTint = C.Red, onClick = { confirmReset = true })
        }
        Body("RichizuFixRKN ${BuildConfig.VERSION_NAME}", C.Muted, 12)
    }

    when (sheet) {
        "route" -> Sheet({ sheet = null }, "Что пускать через обход") {
            Choice(
                listOf(RouteMode.SelectedApps to "Только выбранные приложения (рекомендуется)", RouteMode.AllApps to "Все приложения"),
                Settings.routeMode
            ) { Settings.routeMode = it; vpnHint(); sheet = null }
            Body("В режиме «выбранные» остальные приложения и банки работают напрямую, как без VPN.", C.Muted, 12)
        }
        "dns" -> Sheet({ sheet = null }, "DNS внутри обхода", "Используется приложениями, которые идут через обход (если не включён частный DNS системы).") {
            Choice(tunnelDnsOptions, Settings.tunnelDns) { Settings.tunnelDns = it; vpnHint(); sheet = null }
        }
        "private_dns" -> PrivateDnsSheet { sheet = null }
        "player" -> Sheet({ sheet = null }, "Плеер YouTube") {
            Choice(listOf(Player.YouTube to "Официальный YouTube", Player.SmartTube to "SmartTube (без рекламы, для ТВ)"), Settings.player) {
                Settings.player = it; sheet = null
            }
            Body("SmartTube приложение скачает и установит само при первом открытии.", C.Muted, 12)
        }
    }
    if (confirmReset) Confirm(
        "Сбросить всё?", "Будут удалены настройки, результаты подбора и свои стратегии. Обход и прокси остановятся.", "Сбросить", true,
        onDismiss = { confirmReset = false }
    ) {
        Control.stopAll(ctx)
        StrategyStore.resetAll()
        Settings.resetAll()
        (ctx as? android.app.Activity)?.recreate()
    }
}

private val privateDnsHosts = listOf(
    "dns.google" to "Google",
    "one.one.one.one" to "Cloudflare",
    "dns.quad9.net" to "Quad9",
    "dns.adguard-dns.com" to "AdGuard (блокирует рекламу)",
    "common.dot.dns.yandex.net" to "Яндекс"
)

@Composable
private fun PrivateDnsSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val current = com.richizu.fixrkn.core.Network.privateDnsHost(ctx)
    Sheet(
        onClose, "Частный DNS",
        "Выберите сервер: адрес скопируется, и откроются настройки системы. Там выберите «Частный DNS» → «Имя хоста» и вставьте адрес." +
            (current?.let { "\n\nСейчас используется: $it" } ?: "")
    ) {
        for ((host, name) in privateDnsHosts) {
            Item(name, host, onClick = {
                ctx.getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("dns", host))
                ctx.toast("$host скопирован")
                Actions.openPrivateDnsSettings(ctx)
                onClose()
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Закрыть", Modifier.fillMaxWidth(), Tone.Secondary, onClick = onClose)
        }
    }
}
