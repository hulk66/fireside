package com.firetv.immichslideshow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Warm, gallery-like palette so the chrome never fights the photos. */
object Palette {
    val Ink = Color(0xFF07080C)
    val InkSoft = Color(0xFF0E1118)
    val Panel = Color(0xFF171B25)
    val PanelHigh = Color(0xFF212736)
    val Amber = Color(0xFFF2A45C)
    val AmberSoft = Color(0xFFFFCB95)
    val Cream = Color(0xFFF5F0E8)
    val Muted = Color(0xFF9AA3B2)
    val MutedDim = Color(0xFF6A7280)
    val Hairline = Color(0x2EFFFFFF)
    val Danger = Color(0xFFE8705E)
    val Ok = Color(0xFF83C79A)
}

@Composable
fun ImmichTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Amber,
            onPrimary = Color(0xFF21150A),
            secondary = Palette.Cream,
            onSecondary = Color(0xFF101318),
            background = Palette.Ink,
            onBackground = Palette.Cream,
            surface = Palette.Panel,
            onSurface = Palette.Cream,
            surfaceVariant = Palette.PanelHigh,
            onSurfaceVariant = Palette.Muted,
            error = Palette.Danger,
            onError = Color(0xFF1B0C09),
        ),
        typography = TvTypography,
        content = content,
    )
}

private val Display = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Light,
    fontSize = 54.sp,
    letterSpacing = (-0.5).sp,
)

private val Heading = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 30.sp,
    letterSpacing = 0.2.sp,
)

private val Body = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 19.sp,
    letterSpacing = 0.2.sp,
)

private val TvTypography = Typography(
    displayLarge = Display.copy(fontSize = 64.sp),
    displayMedium = Display,
    displaySmall = Display.copy(fontSize = 42.sp),
    headlineLarge = Heading.copy(fontSize = 38.sp),
    headlineMedium = Heading,
    headlineSmall = Heading.copy(fontSize = 25.sp),
    titleLarge = Heading.copy(fontSize = 22.sp),
    titleMedium = Body.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleSmall = Body.copy(fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge = Body.copy(fontSize = 20.sp),
    bodyMedium = Body,
    bodySmall = Body.copy(fontSize = 16.sp, color = Palette.Muted),
    labelLarge = Body.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp),
    labelMedium = Body.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.8.sp),
    labelSmall = Body.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.2.sp),
)
