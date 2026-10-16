package com.richizu.fixrkn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object C {
    val Bg = Color(0xFF0E1015)
    val Surface = Color(0xFF161920)
    val Surface2 = Color(0xFF1E222B)
    val Surface3 = Color(0xFF272C37)
    val Line = Color(0x1FFFFFFF)
    val Text = Color(0xFFF3F4F6)
    val Sub = Color(0xFFA3A8B4)
    val Muted = Color(0xFF6B7280)
    val Accent = Color(0xFF3B82F6)
    val AccentSoft = Color(0x333B82F6)
    val Green = Color(0xFF22C55E)
    val GreenSoft = Color(0x2622C55E)
    val Amber = Color(0xFFF59E0B)
    val AmberSoft = Color(0x26F59E0B)
    val Red = Color(0xFFEF4444)
    val RedSoft = Color(0x26EF4444)
    val Telegram = Color(0xFF2AABEE)
    val YouTube = Color(0xFFFF3B30)
}

/** True when the UI is laid out for a TV remote. */
val LocalTv = staticCompositionLocalOf { false }
/** True when the user asked for fewer animations. */
val LocalLightUi = staticCompositionLocalOf { false }

@Composable
fun RknTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            onPrimary = Color.White,
            background = C.Bg,
            surface = C.Surface,
            onSurface = C.Text,
            onBackground = C.Text,
            surfaceVariant = C.Surface2,
            onSurfaceVariant = C.Sub,
            outline = C.Line,
            error = C.Red
        ),
        content = content
    )
}
