package com.richizu.fixrkn.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.richizu.fixrkn.bypass.Scanner
import com.richizu.fixrkn.bypass.StrategyEntry
import com.richizu.fixrkn.bypass.StrategyStore
import com.richizu.fixrkn.core.Network
import com.richizu.fixrkn.core.NetworkKind
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.service.Control
import com.richizu.fixrkn.ui.kit.Body
import com.richizu.fixrkn.ui.kit.Btn
import com.richizu.fixrkn.ui.kit.Card
import com.richizu.fixrkn.ui.kit.Confirm
import com.richizu.fixrkn.ui.kit.Pill
import com.richizu.fixrkn.ui.kit.Progress
import com.richizu.fixrkn.ui.kit.Screen
import com.richizu.fixrkn.ui.kit.ScreenTitle
import com.richizu.fixrkn.ui.kit.SectionTitle
import com.richizu.fixrkn.ui.kit.Sheet
import com.richizu.fixrkn.ui.kit.Tone
import com.richizu.fixrkn.ui.kit.clipboardText
import com.richizu.fixrkn.ui.kit.copy
import com.richizu.fixrkn.ui.kit.formatDuration
import com.richizu.fixrkn.ui.kit.press
import com.richizu.fixrkn.ui.kit.toast
import com.richizu.fixrkn.ui.theme.C
import com.richizu.fixrkn.ui.theme.LocalTv
import kotlinx.coroutines.delay

private val facts = listOf(
    "DPI (Deep Packet Inspection) смотрит не только на адрес, но и на содержимое пакетов — например, на имя сайта в начале TLS-соединения.",
    "Имя сайта при HTTPS передаётся открыто в поле SNI первого пакета. Именно его чаще всего ищут фильтры.",
    "Обход работает без серверов: пакеты лишь режутся и перемешиваются так, чтобы фильтр не узнал сайт, а сервер собрал всё как обычно.",
    "Разные провайдеры используют разное оборудование, поэтому способ, который работает у соседа, может не работать у вас.",
    "Ваш трафик никуда не уходит: всё происходит прямо на устройстве, приложение — это локальный VPN без внешнего сервера.",
    "QUIC (HTTP/3) работает поверх UDP. Если он заблокирован, приложения сами переходят на обычный TCP.",
    "Фейковый пакет с маленьким TTL доходит до фильтра, но не до сервера — так фильтр «видит» другой сайт.",
    "Wi‑Fi и мобильная сеть — это разные провайдеры. Приложение помнит отдельный способ для каждой из них.",
    "Telegram умеет работать через WebSocket — тот же протокол, что и веб-версия. Его сложнее отличить от обычного сайта.",
    "Подбор проверяет не только соединение, но и загрузку данных: часто фильтры обрывают передачу на первых килобайтах."
)

private enum class Filter(val title: String) { Working("Рабочие"), Favorites("Избранные"), All("Все") }

@Composable
fun BypassScreen() {
    val ctx = LocalContext.current
    val scan by Scanner.state.collectAsState()
    val entries by StrategyStore.entries.collectAsState()
    Settings.version.collectAsState().value
    var filter by remember { mutableStateOf(Filter.Working) }
    var selected by remember { mutableStateOf<StrategyEntry?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val network = remember(scan.finishedAt) { Network.currentKind(ctx) }
    val active = Settings.activeStrategy

    Screen {
        ScreenTitle("Обход блокировок", "YouTube, Discord и другие сайты")

        Card {
            Text("Сейчас используется", color = C.Muted, fontSize = 12.sp)
            Text(
                active ?: "Способ ещё не выбран", color = C.Text, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (kind in NetworkKind.entries) {
                    val own = Settings.strategyFor(kind)
                    Pill(
                        "${kind.short}: " + if (own == null) "не задан" else if (own == active) "этот" else "свой",
                        color = if (kind == network) C.Text else C.Sub,
                        background = if (kind == network) C.AccentSoft else C.Surface2
                    )
                }
            }
        }

        if (scan.running) ScanProgress() else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Подобрать способ", Modifier.weight(1f), icon = Icons.Default.Search, enabled = scan.probing == null) { Scanner.start(ctx) }
                Btn("Свой", tone = Tone.Secondary, icon = Icons.Default.Add) { editing = "" }
            }
            if (scan.error != null) Card(color = C.RedSoft) { Body(scan.error!!, C.Text) }
            else if (scan.finishedAt > 0) Body(
                if (scan.best != null) "Подбор завершён: найден и применён лучший способ${network?.let { " для сети «${it.title}»" } ?: ""}."
                else "Подбор завершён, рабочих способов не найдено."
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in Filter.entries) {
                val on = f == filter
                Text(
                    f.title, color = if (on) C.Text else C.Sub, fontSize = 14.sp,
                    modifier = Modifier.press(RoundedCornerShape(50)) { filter = f }
                        .background(if (on) C.AccentSoft else C.Surface2, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        val shown = when (filter) {
            Filter.Working -> entries.filter { it.result?.ok == true || it.custom || it.pinned }
            Filter.Favorites -> entries.filter { it.pinned }
            Filter.All -> entries
        }
        if (shown.isEmpty()) {
            Body(
                when (filter) {
                    Filter.Working -> "Пока нет проверенных рабочих способов. Нажмите «Подобрать способ»."
                    Filter.Favorites -> "Отмечайте звёздочкой способы, которые хорошо работают."
                    Filter.All -> ""
                }
            )
        }
        for (e in shown) StrategyRow(e, e.command == active, scan.probing == e.command) { selected = e }

        SectionTitle("Сброс")
        Btn("Сбросить результаты проверок", Modifier.fillMaxWidth(), Tone.Secondary) { confirmReset = true }
    }

    selected?.let { e ->
        val fresh = entries.firstOrNull { it.command == e.command } ?: e
        StrategySheet(fresh, onEdit = { editing = fresh.command; selected = null }, onClose = { selected = null })
    }
    editing?.let { old ->
        EditStrategySheet(old, onClose = { editing = null })
    }
    if (confirmReset) Confirm(
        "Сбросить результаты?", "Оценки, ответы «работает/не работает» и замеры будут удалены. Избранное и свои стратегии останутся.",
        "Сбросить", danger = true, onDismiss = { confirmReset = false }
    ) { StrategyStore.clearResults(); Settings.setupDone = false; Settings.youtubeVerified = false }
}

@Composable
private fun ScanProgress() {
    val scan by Scanner.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fact by remember { mutableIntStateOf((facts.indices).random()) }
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(1000); now = System.currentTimeMillis()
            if (++tick % 10 == 0) fact = (fact + 1) % facts.size
        }
    }
    val elapsed = now - scan.startedAt
    val fraction = if (scan.total == 0) 0f else scan.done.toFloat() / scan.total
    Card(color = C.Surface2) {
        Text(
            if (scan.finalPhase) "Подробно проверяем лучшие способы" else "Быстрая проверка всех способов",
            color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
        )
        Progress(fraction)
        Body("${scan.done} из ${scan.total} · найдено рабочих: ${scan.found} · ${formatDuration(elapsed)}")
        if (!scan.finalPhase && scan.done > 2) {
            val left = elapsed / scan.done * (scan.total - scan.done)
            Body("Осталось примерно ${formatDuration(left)} (плюс проверка лучших)", C.Muted, 12)
        }
        scan.current?.let { Text(it, color = C.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        AnimatedContent(fact, label = "fact") { i ->
            Text(facts[i], color = C.Sub, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.heightIn(min = 54.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Завершить сейчас", Modifier.weight(1f), Tone.Secondary, enabled = scan.found > 0) { Scanner.finishNow() }
            Btn("Отменить", Modifier.weight(1f), Tone.Danger) { Scanner.cancel() }
        }
        Body("Во время подбора обход выключен — не включайте его, пока подбор не закончится. «Завершить сейчас» применит лучший из уже найденных способов.", C.Muted, 12)
    }
}

@Composable
private fun StrategyRow(e: StrategyEntry, active: Boolean, probing: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .press(RoundedCornerShape(16.dp), onClick = onClick)
            .background(if (active) C.AccentSoft else C.Surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val r = e.result
            when {
                probing -> Pill("проверка…", C.Accent, C.AccentSoft)
                r == null -> Pill("не проверен")
                r.ok -> Pill(r.label + if (r.thorough) " ✓" else "", C.Green, C.GreenSoft)
                else -> Pill("не работает", C.Red, C.RedSoft)
            }
            if (e.feedback == true) Pill("работает у вас", C.Green, C.GreenSoft)
            if (e.feedback == false) Pill("не подошёл", C.Amber, C.AmberSoft)
            if (e.custom) Pill("свой")
            if (active) Pill("активен", C.Accent, C.AccentSoft)
            Column(Modifier.weight(1f)) {}
            if (e.pinned) Icon(Icons.Default.Star, "Избранное", tint = C.Amber, modifier = Modifier.size(18.dp))
        }
        Text(e.command, color = C.Sub, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
        e.probe?.let { p ->
            Text(
                "YouTube ${p.youtubeOk}/${p.youtubeTotal} · Discord ${p.discordOk}/${p.discordTotal}" + if (p.averageMs >= 0) " · ${p.averageMs} мс" else "",
                color = C.Muted, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StrategySheet(e: StrategyEntry, onEdit: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scan by Scanner.state.collectAsState()
    val network = Network.currentKind(ctx)
    var confirmDelete by remember { mutableStateOf(false) }
    Sheet(onClose, "Способ обхода") {
        SelectionContainer {
            Text(
                e.command, color = C.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface2).padding(12.dp)
            )
        }
        e.probe?.let {
            Body("YouTube: ${it.youtubeOk} из ${it.youtubeTotal}, Discord: ${it.discordOk} из ${it.discordTotal}" + if (it.averageMs >= 0) ", в среднем ${it.averageMs} мс" else "")
        }
        if (scan.probing == e.command) Progress(null)
        Btn(
            if (network != null) "Применить для сети «${network.title}»" else "Применить", Modifier.fillMaxWidth()
        ) {
            Control.applyStrategy(ctx, e.command, network)
            Settings.setupDone = true
            ctx.toast("Способ применён")
            onClose()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Проверить", Modifier.weight(1f), Tone.Secondary, enabled = !Scanner.busy) { Scanner.probe(ctx, e.command) }
            Btn(if (e.pinned) "Из избранного" else "В избранное", Modifier.weight(1f), Tone.Secondary) { StrategyStore.togglePin(e.command) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!LocalTv.current) Btn("Копировать", Modifier.weight(1f), Tone.Secondary) { ctx.copy("strategy", e.command) }
            if (e.custom) {
                Btn("Изменить", Modifier.weight(1f), Tone.Secondary, onClick = onEdit)
                Btn("Удалить", Modifier.weight(1f), Tone.Danger) { confirmDelete = true }
            }
        }
        if (e.feedback != null) Btn("Сбросить мой ответ «${if (e.feedback) "работает" else "не подошёл"}»", Modifier.fillMaxWidth(), Tone.Ghost) {
            StrategyStore.setFeedback(e.command, null)
        }
    }
    if (confirmDelete) Confirm("Удалить стратегию?", "Её можно будет добавить снова.", "Удалить", true, { confirmDelete = false }) {
        StrategyStore.removeCustom(e.command)
        onClose()
    }
}

@Composable
private fun EditStrategySheet(old: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(old) }
    Sheet(
        onClose, if (old.isEmpty()) "Своя стратегия" else "Изменить стратегию",
        "Вставьте параметры byedpi (например, из ByeByeDPI). Адрес и порт удалятся сами, после сохранения стратегия сразу проверится."
    ) {
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = C.Text),
            placeholder = { Text("-o1 -d1 -r1+s -a1", color = C.Muted, fontFamily = FontFamily.Monospace) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Accent, unfocusedBorderColor = C.Line)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!LocalTv.current) Btn("Вставить", Modifier.weight(1f), Tone.Secondary, icon = Icons.Default.ContentPaste) {
                ctx.clipboardText()?.let { text = it } ?: ctx.toast("Буфер обмена пуст")
            }
            Btn("Сохранить", Modifier.weight(1f), enabled = text.isNotBlank()) {
                val ok = if (old.isEmpty()) StrategyStore.addCustom(text) else StrategyStore.replaceCustom(old, text)
                if (!ok) {
                    ctx.toast("Не удалось разобрать стратегию")
                    return@Btn
                }
                val saved = com.richizu.fixrkn.bypass.sanitizeStrategy(text)
                Scanner.probe(ctx, saved)
                onClose()
            }
        }
    }
}
