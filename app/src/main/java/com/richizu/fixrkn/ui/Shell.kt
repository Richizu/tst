package com.richizu.fixrkn.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.ui.kit.LocalShowQr
import com.richizu.fixrkn.ui.kit.QrSheet
import com.richizu.fixrkn.ui.kit.press
import com.richizu.fixrkn.ui.screens.BypassScreen
import com.richizu.fixrkn.ui.screens.ConsentUnavailableSheet
import com.richizu.fixrkn.ui.screens.GuideSheets
import com.richizu.fixrkn.ui.screens.HomeScreen
import com.richizu.fixrkn.ui.screens.InfoScreen
import com.richizu.fixrkn.ui.screens.Onboarding
import com.richizu.fixrkn.ui.screens.SettingsScreen
import com.richizu.fixrkn.ui.screens.TelegramScreen
import com.richizu.fixrkn.ui.screens.UpdateSheet
import com.richizu.fixrkn.ui.theme.C
import com.richizu.fixrkn.ui.theme.LocalLightUi
import com.richizu.fixrkn.ui.theme.LocalTv
import com.richizu.fixrkn.ui.theme.RknTheme
import com.richizu.fixrkn.update.Updater

enum class Tab(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Default.Home),
    Bypass("Обход", Icons.Default.Tune),
    Telegram("Telegram", Icons.AutoMirrored.Filled.Send),
    Settings("Настройки", Icons.Default.Settings),
    Info("Инфо", Icons.Default.Info)
}

/** Starts protection, asking for the VPN permission first when needed. */
class Starter(val start: (openTelegram: Boolean, toBackground: Boolean) -> Unit)

val LocalStarter = compositionLocalOf { Starter { _, _ -> } }
val LocalNavigate = compositionLocalOf<(Tab) -> Unit> { {} }

@Composable
fun Shell(startRequested: Boolean = false) {
    val version by Settings.version.collectAsState()
    val ctx = LocalContext.current
    val activity = ctx as Activity
    var onboarded by remember { mutableStateOf(Settings.onboarded) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var qr by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pending by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }

    fun launchNow(openTg: Boolean, toBackground: Boolean) {
        Control.startAll(ctx, openTg)
        if (toBackground) activity.moveTaskToBack(true)
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending
        pending = null
        com.richizu.fixrkn.core.AppLog.i("Shell", "VPN consent result: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK && p != null) launchNow(p.first, p.second)
        else if (p != null) Control.lastError.value = "Запрос VPN отклонён. Без него обход не работает."
    }
    val starter = remember {
        Starter { openTg, toBackground ->
            val intent = if (Settings.youtubeEnabled) Control.vpnConsentIntent(ctx) else null
            com.richizu.fixrkn.core.AppLog.i("Shell", "Start requested; VPN consent needed: ${intent != null}")
            if (intent == null) {
                launchNow(openTg, toBackground)
            } else {
                pending = openTg to toBackground
                try {
                    consent.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    pending = null
                    Control.consentUnavailable.value = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Settings.onboarded && Settings.autoUpdate) Updater.check()
    }
    LaunchedEffect(startRequested, onboarded) {
        if (startRequested && onboarded && !Control.anyRunning) starter.start(false, false)
    }

    val tv = remember(version) { Settings.tvMode }
    val light = remember(version) { Settings.lightUi }
    RknTheme {
        CompositionLocalProvider(
            LocalTv provides tv,
            LocalLightUi provides light,
            LocalStarter provides starter,
            LocalNavigate provides { t: Tab -> tab = t },
            LocalShowQr provides { url: String, label: String -> qr = url to label }
        ) {
            Box(Modifier.fillMaxSize().background(C.Bg)) {
                if (!onboarded) {
                    Onboarding(onDone = { onboarded = true; tab = Tab.Home })
                } else {
                    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }
                    if (tv) TvLayout(tab) { tab = it } else PhoneLayout(tab) { tab = it }
                    UpdateSheet()
                    GuideSheets()
                }
                ConsentUnavailableSheet()
                qr?.let { (url, label) -> QrSheet(url, label) { qr = null } }
            }
        }
    }
}

@Composable
private fun TabContent(tab: Tab) {
    AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
        when (t) {
            Tab.Home -> HomeScreen()
            Tab.Bypass -> BypassScreen()
            Tab.Telegram -> TelegramScreen()
            Tab.Settings -> SettingsScreen()
            Tab.Info -> InfoScreen()
        }
    }
}

@Composable
private fun PhoneLayout(tab: Tab, onTab: (Tab) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { TabContent(tab) }
        Row(
            Modifier
                .fillMaxWidth()
                .background(C.Surface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            for (t in Tab.entries) NavItem(t, t == tab, Modifier.weight(1f)) { onTab(t) }
        }
    }
}

@Composable
private fun TvLayout(tab: Tab, onTab: (Tab) -> Unit) {
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Column(
            Modifier.fillMaxHeight().width(200.dp).background(C.Surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("RichizuFixRKN", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(8.dp, 12.dp))
            for (t in Tab.entries) {
                val active = t == tab
                Row(
                    Modifier.fillMaxWidth()
                        .press(RoundedCornerShape(12.dp)) { onTab(t) }
                        .background(if (active) C.AccentSoft else C.Surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(t.icon, null, tint = if (active) C.Accent else C.Sub, modifier = Modifier.size(20.dp))
                    Text(t.title, color = if (active) C.Text else C.Sub, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { TabContent(tab) }
    }
}

@Composable
private fun NavItem(t: Tab, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.press(RoundedCornerShape(12.dp), onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.background(if (active) C.AccentSoft else C.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Icon(t.icon, null, tint = if (active) C.Accent else C.Muted, modifier = Modifier.size(22.dp))
        }
        Text(t.title, color = if (active) C.Text else C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
