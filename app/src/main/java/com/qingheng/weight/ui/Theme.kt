package com.qingheng.weight.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Emerald = Color(0xFF16A274)
val Mint = Color(0xFFE8F8F1)
val Ink = Color(0xFF18332A)
val SoftGray = Color(0xFFF5F7F6)
val Amber = Color(0xFFF2A93B)

private val LightColors = lightColorScheme(
    primary = Emerald, onPrimary = Color.White, primaryContainer = Mint,
    onPrimaryContainer = Ink, secondary = Color(0xFF507B6A), background = Color(0xFFFAFCFB),
    surface = Color.White, onSurface = Ink, surfaceVariant = SoftGray,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DDAAA), primaryContainer = Color(0xFF114D3A),
    secondary = Color(0xFF9BCDBA), background = Color(0xFF0E1714), surface = Color(0xFF14201C),
)

@Composable fun QingHengTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, typography = Typography(), content = content)
}

