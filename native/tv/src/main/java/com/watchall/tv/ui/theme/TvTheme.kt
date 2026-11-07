package com.watchall.tv.ui.theme

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

object TvColors {
    val Ink = Color(0xFF08090C)
    val DeepInk = Color(0xFF0C0D12)
    val Surface = Color(0xFF111318)
    val Card = Color(0xFF191B22)
    val CardHigh = Color(0xFF262932)
    val Cyan = Color(0xFF64E8FF)
    val Teal = Color(0xFF3BD9C5)
    val Violet = Color(0xFF8C7CFF)
    val Coral = Color(0xFFFF7185)
    val Amber = Color(0xFFFFC65C)
    val Text = Color(0xFFF7F8FA)
    val TextMuted = Color(0xFFA3A9B7)
    val Hairline = Color(0x1FFFFFFF)
    val Success = Color(0xFF5BE3A6)
}

private val colors = darkColorScheme(
    primary = TvColors.Cyan,
    onPrimary = Color(0xFF03171B),
    primaryContainer = Color(0xFF0B3A42),
    onPrimaryContainer = TvColors.Cyan,
    secondary = TvColors.Violet,
    onSecondary = Color.White,
    tertiary = TvColors.Amber,
    background = TvColors.Ink,
    onBackground = TvColors.Text,
    surface = TvColors.Surface,
    onSurface = TvColors.Text,
    surfaceVariant = TvColors.Card,
    onSurfaceVariant = TvColors.TextMuted,
    outline = Color(0xFF3A3E49),
    outlineVariant = TvColors.Hairline,
    error = TvColors.Coral,
    onError = Color.White,
)

/**
 * TV type scale.
 *
 * Android TV reports roughly 960 x 540 dp at 1080p, so the old 56-70sp display
 * sizes were almost twice the size of a normal 10-foot UI heading. These sizes
 * are deliberately compact, have no negative letter spacing, and every line
 * height leaves room for descenders so titles never look clipped.
 */
private val typography = Typography().run {
    copy(
        displayLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 42.sp,
            lineHeight = 50.sp,
            letterSpacing = 0.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            letterSpacing = 0.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            letterSpacing = 0.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp,
        ),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun WatchAllTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
