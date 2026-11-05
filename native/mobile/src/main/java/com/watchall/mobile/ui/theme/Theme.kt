package com.watchall.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object WatchColors {
    val Ink = Color(0xFF06070B)
    val DeepInk = Color(0xFF090B12)
    val Surface = Color(0xFF0E1119)
    val Card = Color(0xFF141823)
    val CardHigh = Color(0xFF1B2030)
    val Cyan = Color(0xFF64E8FF)
    val Teal = Color(0xFF38D7C8)
    val Violet = Color(0xFF7C6BFF)
    val Coral = Color(0xFFFF6B7D)
    val Amber = Color(0xFFFFC766)
    val Text = Color(0xFFF5F7FC)
    val TextMuted = Color(0xFF9AA4B8)
    val Hairline = Color(0x1FFFFFFF)
    val Success = Color(0xFF58E0A3)
}

private val colors = darkColorScheme(
    primary = WatchColors.Cyan,
    onPrimary = Color(0xFF021418),
    primaryContainer = Color(0xFF0B3A42),
    onPrimaryContainer = WatchColors.Cyan,
    secondary = WatchColors.Violet,
    onSecondary = Color.White,
    tertiary = WatchColors.Amber,
    background = WatchColors.Ink,
    onBackground = WatchColors.Text,
    surface = WatchColors.Surface,
    onSurface = WatchColors.Text,
    surfaceVariant = WatchColors.Card,
    onSurfaceVariant = WatchColors.TextMuted,
    outline = Color(0xFF3A4254),
    outlineVariant = WatchColors.Hairline,
    error = WatchColors.Coral,
    onError = Color.White,
)

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
        displayMedium = displayMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineLarge = headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun WatchAllTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}

val HeroTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 38.sp,
    lineHeight = 41.sp,
    letterSpacing = (-1).sp,
)
