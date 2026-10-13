package com.tbtechs.focusflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// FocusFlow Exact Brand Palette matching React reference
val BrandPrimary = Color(0xFF6366F1)       // Indigo 500
val BrandPrimaryHover = Color(0xFF4F46E5)  // Indigo 600
val BrandPrimaryLight = Color(0xFFE0E7FF)  // Indigo 100
val BrandPrimaryDark = Color(0xFF4338CA)   // Indigo 700

// Dark Slate Theme (Reference Dark Background & Surfaces)
private val DarkPaletteBackground = Color(0xFF0D1322)
private val DarkPaletteCard = Color(0xFF151C2E)
private val DarkPaletteSurfaceVariant = Color(0xFF1C253B)
private val DarkPaletteBorder = Color(0xFF263249)
private val DarkPaletteTextPrimary = Color(0xFFF8FAFC)
private val DarkPaletteTextSecondary = Color(0xFF94A3B8)
private val DarkPaletteTextMuted = Color(0xFF64748B)

// Secondary surfaces use a low-contrast indigo tint instead of bright lavender blocks.
private val DarkPaletteInfoSurface = Color(0xFF1C2141)
private val DarkPaletteInfoBorder = Color(0xFF3D4380)
private val DarkPaletteInfoText = Color(0xFFD7D9FF)
private val DarkPaletteInfoBodyText = Color(0xFFB9BDEB)

private val LocalFocusFlowDarkTheme = staticCompositionLocalOf { true }
val LocalFocusFlowTextScale = staticCompositionLocalOf { 1f }

val Number.scaledSp: TextUnit
    @Composable get() = (toFloat() * LocalFocusFlowTextScale.current).sp

/**
 * Compatibility names used throughout the existing screens.
 *
 * These are composable color properties rather than fixed dark colors, so
 * older screens automatically follow the active light/dark Material scheme.
 */
val DarkBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background

val DarkCard: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val DarkSurfaceVariant: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val DarkBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val DarkTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

val DarkTextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val DarkTextMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)

val InfoSurface: Color
    @Composable get() = if (LocalFocusFlowDarkTheme.current) {
        DarkPaletteInfoSurface
    } else {
        Color(0xFFE8EBFF)
    }

val InfoBorder: Color
    @Composable get() = if (LocalFocusFlowDarkTheme.current) {
        DarkPaletteInfoBorder
    } else {
        Color(0xFFC7D2FE)
    }

val InfoText: Color
    @Composable get() = if (LocalFocusFlowDarkTheme.current) {
        DarkPaletteInfoText
    } else {
        Color(0xFF312E81)
    }

val InfoBodyText: Color
    @Composable get() = if (LocalFocusFlowDarkTheme.current) {
        DarkPaletteInfoBodyText
    } else {
        Color(0xFF4338CA)
    }

val WarningSurface: Color
    @Composable get() = if (LocalFocusFlowDarkTheme.current) {
        Color(0xFFF59E0B).copy(alpha = 0.12f)
    } else {
        Color(0xFFFFF4D6)
    }

val WarningBorder: Color
    @Composable get() = if (LocalFocusFlowDarkTheme.current) {
        Color(0xFFF59E0B).copy(alpha = 0.35f)
    } else {
        Color(0xFFF59E0B).copy(alpha = 0.65f)
    }

val WarningIcon: Color
    @Composable get() = if (LocalFocusFlowDarkTheme.current) {
        Color(0xFFFBBF24)
    } else {
        Color(0xFFB45309)
    }

val WarningText: Color
    @Composable get() = if (LocalFocusFlowDarkTheme.current) {
        Color(0xFFFDE68A)
    } else {
        Color(0xFF92400E)
    }

// Semantic Status Colors
val StatusReady = Color(0xFF10B981)
val StatusReadyBg = Color(0xFF064E3B)
val StatusReadyText = Color(0xFF34D399)

val StatusNotSetUp = Color(0xFFF59E0B)
val StatusNotSetUpBg = Color(0xFF451A03)
val StatusNotSetUpText = Color(0xFFFBBF24)

val StatusMissing = Color(0xFFEF4444)
val StatusMissingBg = Color(0xFF450A0A)
val StatusMissingText = Color(0xFFF87171)

val StatusOptional = Color(0xFF8B5CF6)
val StatusOptionalBg = Color(0xFFE7EAF1)
val StatusOptionalText = Color(0xFF687386)
val SunAmber = Color(0xFFFBBF24)

// Info Banner Lavender Colors
val LavenderBg = Color(0xFFEEF2FF)
val LavenderText = Color(0xFF312E81)
val LavenderBorder = Color(0xFFC7D2FE)

val FocusFlowDarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryHover,
    onPrimaryContainer = Color.White,
    secondary = BrandPrimaryLight,
    onSecondary = DarkPaletteBackground,
    background = DarkPaletteBackground,
    onBackground = DarkPaletteTextPrimary,
    surface = DarkPaletteCard,
    onSurface = DarkPaletteTextPrimary,
    surfaceVariant = DarkPaletteSurfaceVariant,
    onSurfaceVariant = DarkPaletteTextSecondary,
    outline = DarkPaletteBorder,
    outlineVariant = DarkPaletteBorder,
    error = StatusMissing,
    onError = Color.White,
    errorContainer = StatusMissingBg,
    onErrorContainer = StatusMissingText,
)

val FocusFlowLightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryLight,
    onPrimaryContainer = BrandPrimaryDark,
    secondary = BrandPrimaryLight,
    onSecondary = Color(0xFF1E1B4B),
    background = Color(0xFFF0F2FF),
    onBackground = Color(0xFF1E1B4B),
    surface = Color.White,
    onSurface = Color(0xFF1E1B4B),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFEF4444),
    onError = Color.White,
)

val FocusFlowShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

private fun focusFlowTypography(scale: Float): Typography {
    fun TextStyle.scaled() = copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )

    return Typography(
        displaySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.25).sp,
        ).scaled(),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            lineHeight = 31.sp,
            letterSpacing = (-0.2).sp,
        ).scaled(),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            lineHeight = 27.sp,
            letterSpacing = (-0.1).sp,
        ).scaled(),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.sp,
        ).scaled(),
        titleSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ).scaled(),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        ).scaled(),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ).scaled(),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ).scaled(),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ).scaled(),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
        ).scaled(),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
        ).scaled(),
    )
}

@Composable
fun FocusFlowTheme(
    darkTheme: Boolean = true,
    generalTextScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FocusFlowDarkColorScheme else FocusFlowLightColorScheme
    val dimensions = focusFlowDimensionsForWidth(LocalConfiguration.current.screenWidthDp)

    CompositionLocalProvider(LocalFocusFlowTextScale provides generalTextScale) {
        CompositionLocalProvider(LocalFocusFlowDimensions provides dimensions) {
            CompositionLocalProvider(LocalFocusFlowDarkTheme provides darkTheme) {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = focusFlowTypography(generalTextScale),
                    shapes = FocusFlowShapes,
                    content = content,
                )
            }
        }
    }
}
