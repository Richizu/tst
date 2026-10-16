package com.richizu.fixrkn.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.richizu.fixrkn.core.Settings
import com.richizu.fixrkn.ui.theme.C

/** Clickable that also draws a clear focus ring for D-pad navigation. */
@Composable
fun Modifier.press(shape: Shape = RoundedCornerShape(14.dp), enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val ring by animateColorAsState(if (focused) C.Accent else Color.Transparent, label = "focus")
    val haptic = LocalHapticFeedback.current
    return this
        .clip(shape)
        .border(2.dp, ring, shape)
        .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled) {
            if (Settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = C.Surface, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .border(1.dp, C.Line, RoundedCornerShape(20.dp))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(title, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = C.Sub, fontSize = 14.sp)
    }
}

@Composable
fun Body(text: String, color: Color = C.Sub, size: Int = 14) {
    Text(text, color = color, fontSize = size.sp, lineHeight = (size + 6).sp)
}

enum class Tone { Primary, Secondary, Danger, Ghost, Success }

@Composable
fun Btn(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val (bg, fg) = when (tone) {
        Tone.Primary -> C.Accent to Color.White
        Tone.Secondary -> C.Surface3 to C.Text
        Tone.Danger -> C.RedSoft to C.Red
        Tone.Ghost -> Color.Transparent to C.Sub
        Tone.Success -> C.Green to Color.White
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .press(shape, enabled, onClick)
            .background(if (enabled) bg else C.Surface2, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) fg else C.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) fg else C.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** A settings row: icon, title, subtitle and optional trailing content. */
@Composable
fun Item(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = C.Sub,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = if (onClick != null) ({ Chevron() }) else null
) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.press(RoundedCornerShape(14.dp), onClick = onClick) else base)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.Surface2), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = C.Sub, fontSize = 13.sp, lineHeight = 17.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Muted)

@Composable
fun SwitchRow(title: String, subtitle: String? = null, icon: ImageVector? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Item(title, subtitle, icon, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, uncheckedTrackColor = C.Surface3, uncheckedBorderColor = C.Surface3, uncheckedThumbColor = C.Sub)
        )
    }
}

@Composable
fun ValueText(text: String) = Text(text, color = C.Sub, fontSize = 14.sp)

@Composable
fun Pill(text: String, color: Color = C.Sub, background: Color = C.Surface2, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Progress(value: Float?, modifier: Modifier = Modifier) {
    if (value == null) {
        LinearProgressIndicator(modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = C.Accent, trackColor = C.Surface3, strokeCap = StrokeCap.Round)
    } else {
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = C.Accent, trackColor = C.Surface3, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {}
        )
    }
}

/** Centered modal panel (works with touch and a TV remote alike). */
@Composable
fun Sheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible)
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(C.Surface)
                .border(1.dp, C.Line, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Body(subtitle)
            content()
        }
    }
}

@Composable
fun Confirm(
    title: String,
    text: String,
    confirm: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Sheet(onDismiss, title, text) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Отмена", Modifier.weight(1f), Tone.Secondary, onClick = onDismiss)
            Btn(confirm, Modifier.weight(1f), if (danger) Tone.Danger else Tone.Primary) { onConfirm(); onDismiss() }
        }
    }
}

/** Option list inside a sheet. */
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((value, label) in options) {
            val active = value == selected
            Row(
                Modifier.fillMaxWidth()
                    .press { onSelect(value) }
                    .background(if (active) C.AccentSoft else C.Surface2)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = if (active) C.Text else C.Sub, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (active) Dot(C.Accent, 10.dp)
            }
        }
    }
}

/** Standard scrollable screen body. */
@Composable
fun Screen(content: @Composable ColumnScope.() -> Unit) {
    val tv = com.richizu.fixrkn.ui.theme.LocalTv.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .then(if (tv) Modifier else Modifier.statusBarsPadding())
                .padding(horizontal = if (tv) 28.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
