package com.babytracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Палитра: тёплый крем днём, глубокая ночная синева в тёмной теме и тёмно-красная
 * «ночная» тема без синего света — для ночных кормлений.
 * У каждого раздела свой цвет: сон — лаванда, еда — персик, подгузник — бирюза, здоровье — роза.
 */
@Immutable
data class BabyColors(
    val bg: Color,
    val card: Color,
    val cardSoft: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val sleep: Color,
    val feed: Color,
    val diaper: Color,
    val health: Color,
    val sound: Color,
    val good: Color,
    val warn: Color,
    val danger: Color,
    val onAccent: Color,
    val isDark: Boolean,
    val isNight: Boolean = false,
)

val LightColors = BabyColors(
    bg = Color(0xFFF7F3EE),
    card = Color(0xFFFFFFFF),
    cardSoft = Color(0xFFF1ECE6),
    border = Color(0xFFE9E2D9),
    text = Color(0xFF2B2A35),
    muted = Color(0xFF7D7888),
    sleep = Color(0xFF6E62D0),
    feed = Color(0xFFE9794B),
    diaper = Color(0xFF1F9C8F),
    health = Color(0xFFD9577A),
    sound = Color(0xFF3F86D4),
    good = Color(0xFF2E9E62),
    warn = Color(0xFFD9922A),
    danger = Color(0xFFD14B4B),
    onAccent = Color.White,
    isDark = false,
)

val DarkColors = BabyColors(
    bg = Color(0xFF11111B),
    card = Color(0xFF1B1B28),
    cardSoft = Color(0xFF242435),
    border = Color(0xFF2E2E43),
    text = Color(0xFFEDEBF5),
    muted = Color(0xFF9C99B0),
    sleep = Color(0xFFA99CFF),
    feed = Color(0xFFFFA67C),
    diaper = Color(0xFF52D1C1),
    health = Color(0xFFFF8EAA),
    sound = Color(0xFF7DB6F5),
    good = Color(0xFF62D095),
    warn = Color(0xFFF4BF5E),
    danger = Color(0xFFFF7B73),
    onAccent = Color(0xFF15131F),
    isDark = true,
)

/** Только тёплые тёмные оттенки: не будит малыша и не сбивает маме сон. */
val NightColors = BabyColors(
    bg = Color(0xFF0B0606),
    card = Color(0xFF170D0C),
    cardSoft = Color(0xFF211211),
    border = Color(0xFF2C1816),
    text = Color(0xFFE59A87),
    muted = Color(0xFF8E5A4F),
    sleep = Color(0xFFC9705C),
    feed = Color(0xFFD98B52),
    diaper = Color(0xFFB97A4E),
    health = Color(0xFFC85F68),
    sound = Color(0xFFBF7357),
    good = Color(0xFFB98A4E),
    warn = Color(0xFFD99A4E),
    danger = Color(0xFFD9584E),
    onAccent = Color(0xFF0B0606),
    isDark = true,
    isNight = true,
)

private val LocalBabyColors = staticCompositionLocalOf { LightColors }

object Baby {
    val colors: BabyColors
        @Composable @ReadOnlyComposable get() = LocalBabyColors.current
}

/** Мягкая подложка в цвет раздела. */
fun Color.soft(colors: BabyColors): Color = copy(alpha = if (colors.isDark) 0.18f else 0.13f)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun BabyTheme(colors: BabyColors, content: @Composable () -> Unit) {
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.sleep, onPrimary = colors.onAccent,
            secondary = colors.feed, tertiary = colors.diaper,
            background = colors.bg, onBackground = colors.text,
            surface = colors.card, onSurface = colors.text,
            surfaceVariant = colors.cardSoft, onSurfaceVariant = colors.muted,
            surfaceContainerHigh = colors.card, surfaceContainerHighest = colors.cardSoft,
            surfaceContainerLow = colors.card, surfaceContainer = colors.card,
            outline = colors.border, outlineVariant = colors.border, error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.sleep, onPrimary = colors.onAccent,
            secondary = colors.feed, tertiary = colors.diaper,
            background = colors.bg, onBackground = colors.text,
            surface = colors.card, onSurface = colors.text,
            surfaceVariant = colors.cardSoft, onSurfaceVariant = colors.muted,
            surfaceContainerHigh = colors.card, surfaceContainerHighest = colors.cardSoft,
            surfaceContainerLow = colors.card, surfaceContainer = colors.card,
            outline = colors.border, outlineVariant = colors.border, error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalBabyColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
