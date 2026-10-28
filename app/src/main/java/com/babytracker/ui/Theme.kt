package com.babytracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SoftBlue = Color(0xFF7C9CF0)
private val SoftBlueDark = Color(0xFF4A6BC4)
private val Cream = Color(0xFFFFF6E9)
private val WarmPink = Color(0xFFF7C8D8)
private val Mint = Color(0xFFB8E6D0)
private val Ink = Color(0xFF2A2C3B)

private val LightColors = lightColorScheme(
    primary = SoftBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Ink,
    secondary = WarmPink,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFFE4EC),
    onSecondaryContainer = Ink,
    tertiary = Mint,
    onTertiary = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEFF7),
    onSurfaceVariant = Color(0xFF5A5D6E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C0FF),
    onPrimary = Color(0xFF1A2A5E),
    primaryContainer = SoftBlueDark,
    onPrimaryContainer = Color(0xFFDEE6FF),
    secondary = Color(0xFFF7C8D8),
    onSecondary = Color(0xFF4A2130),
    secondaryContainer = Color(0xFF6B3549),
    onSecondaryContainer = Color(0xFFFFE4EC),
    tertiary = Mint,
    background = Color(0xFF1A1B26),
    onBackground = Color(0xFFE6E6F0),
    surface = Color(0xFF232432),
    onSurface = Color(0xFFE6E6F0),
    surfaceVariant = Color(0xFF2E3040),
    onSurfaceVariant = Color(0xFFB9BCC9),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun BabyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
