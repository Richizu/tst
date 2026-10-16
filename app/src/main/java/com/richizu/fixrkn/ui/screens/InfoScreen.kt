package com.richizu.fixrkn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richizu.fixrkn.BuildConfig
import com.richizu.fixrkn.Links
import com.richizu.fixrkn.R
import com.richizu.fixrkn.ui.kit.Body
import com.richizu.fixrkn.ui.kit.Card
import com.richizu.fixrkn.ui.kit.Item
import com.richizu.fixrkn.ui.kit.Screen
import com.richizu.fixrkn.ui.kit.SectionTitle
import com.richizu.fixrkn.ui.kit.rememberLinkOpener
import com.richizu.fixrkn.ui.theme.C

private val licenses = listOf(
    Triple("byedpi", "hufrea · MIT", "https://github.com/hufrea/byedpi"),
    Triple("hev-socks5-tunnel", "hev · MIT", "https://github.com/heiher/hev-socks5-tunnel"),
    Triple("Список Cloudflare-узлов для Telegram", "tg-ws-proxy, Flowseal · MIT", "https://github.com/Flowseal/tg-ws-proxy"),
    Triple("ZXing", "Apache 2.0", "https://github.com/zxing/zxing"),
    Triple("AndroidX, Jetpack Compose, Kotlin", "Apache 2.0", "https://developer.android.com/jetpack/androidx")
)

@Composable
fun InfoScreen() {
    val open = rememberLinkOpener()
    Screen {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
                Column {
                    Text("RichizuFixRKN", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Версия ${BuildConfig.VERSION_NAME}", color = C.Sub, fontSize = 14.sp)
                }
            }
            Body("Обход блокировок YouTube, Discord и других сайтов и встроенный прокси для Telegram — на телефоне и телевизоре, одной кнопкой.")
        }

        Card(padding = 4.dp) {
            Item("Исходный код", "GitHub", Icons.Default.Code, onClick = { open(Links.REPO_URL, "GitHub") })
            Item("Последняя версия", "Скачать APK", Icons.Default.NewReleases, onClick = { open(Links.RELEASES, "Релизы") })
            Item("Решение проблем", "Частые вопросы и инструкции", Icons.AutoMirrored.Filled.Help, onClick = { open(Links.HELP, "Решение проблем") })
            Item("Сообщить об ошибке", "GitHub Issues", Icons.Default.BugReport, onClick = { open(Links.ISSUES, "GitHub Issues") })
        }

        SectionTitle("Как это работает")
        Card {
            Body("Обход — это локальный VPN на устройстве. Трафик выбранных приложений проходит через встроенный byedpi, который так изменяет первые пакеты соединения (разрезает, переставляет, добавляет фейковые), что фильтр провайдера не узнаёт сайт. Никаких внешних серверов нет.")
            Body("Прокси Telegram — локальный MTProto-прокси. Он передаёт трафик Telegram через WebSocket напрямую на серверы Telegram, через сеть Cloudflare или обычным TCP — в зависимости от того, что доступно.")
        }

        SectionTitle("Приватность")
        Card {
            Body("Приложение не собирает и никуда не отправляет ваши данные. Сетевые запросы самого приложения: проверка сайтов при подборе способа, проверка обновлений на GitHub, загрузка списка узлов Cloudflare и DNS-over-HTTPS запросы для них.")
        }

        SectionTitle("Лицензии")
        Card(padding = 4.dp) {
            for ((name, license, url) in licenses) Item(name, license, onClick = { open(url, name) })
        }
        Body("RichizuFixRKN распространяется под лицензией MIT. Программа предоставляется «как есть».", C.Muted, 12)
    }
}
