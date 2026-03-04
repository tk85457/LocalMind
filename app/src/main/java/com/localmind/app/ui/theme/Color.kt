package com.localmind.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Neon Amethyst Theme Colors
object LocalMindColors {
    // Dark Palette
    val DarkBackground = Color(0xFF0B0B12)
    val DarkSurface = Color(0xFF12121A)
    val DarkElevated = Color(0xFF1A1A24)
    val DarkTextPrimary = Color(0xFFF5F3FF)
    val DarkTextSecondary = Color(0xFFB8B5C3)
    val DarkTextTertiary = Color(0xFF6B6878)
    val DarkTextExtraMuted = Color(0xFF3F3D4A)
    val DarkDivider = Color(0xFF1F1F2E)

    // Light Palette
    val LightBackground = Color(0xFFF8FAFC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightElevated = Color(0xFFF1F5F9)
    val LightTextPrimary = Color(0xFF0F172A)
    val LightTextSecondary = Color(0xFF475569)
    val LightTextTertiary = Color(0xFF94A3B8)
    val LightTextExtraMuted = Color(0xFFCBD5E1)
    val LightDivider = Color(0xFFE2E8F0)

    // Shared Primary & Accents
    val Primary = Color(0xFF8B5CF6)
    val PrimaryVariant = Color(0xFF6D28D9)
    val PrimaryLight = Color(0xFFA855F7)
    val Accent = Color(0xFF8B5CF6)
    val AccentGlow = Color(0x408B5CF6)

    // Status
    // Status
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)

    // Blue Palette
    val BluePrimary = Color(0xFF3B82F6) // Blue 500
    val BluePrimaryVariant = Color(0xFF1D4ED8) // Blue 700
    val BluePrimaryLight = Color(0xFF60A5FA) // Blue 400
    val BlueAccent = Color(0xFF3B82F6)
    val BlueAccentGlow = Color(0x403B82F6)

    // Green Palette
    val GreenPrimary = Color(0xFF10B981) // Emerald 500
    val GreenPrimaryVariant = Color(0xFF047857) // Emerald 700
    val GreenPrimaryLight = Color(0xFF34D399) // Emerald 400
    val GreenAccent = Color(0xFF10B981)
    val GreenAccentGlow = Color(0x4010B981)

    // Orange Palette
    val OrangePrimary = Color(0xFFF97316) // Orange 500
    val OrangePrimaryVariant = Color(0xFFC2410C) // Orange 700
    val OrangePrimaryLight = Color(0xFFFB923C) // Orange 400
    val OrangeAccent = Color(0xFFF97316)
    val OrangeAccentGlow = Color(0x40F97316)
}

data class NeonColors(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textExtraMuted: Color,
    val primary: Color,
    val primaryVariant: Color,
    val primaryLight: Color,
    val accent: Color,
    val accentGlow: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val divider: Color
)

val LocalNeonColors = staticCompositionLocalOf<NeonColors> {
    error("No NeonColors provided")
}

// Helper accessors that use the CompositionLocal
val NeonBackground: Color @Composable get() = LocalNeonColors.current.background
val NeonSurface: Color @Composable get() = LocalNeonColors.current.surface
val NeonElevated: Color @Composable get() = LocalNeonColors.current.elevated
val NeonText: Color @Composable get() = LocalNeonColors.current.text
val NeonTextSecondary: Color @Composable get() = LocalNeonColors.current.textSecondary
val NeonTextTertiary: Color @Composable get() = LocalNeonColors.current.textTertiary
val NeonTextExtraMuted: Color @Composable get() = LocalNeonColors.current.textExtraMuted
val NeonPrimary: Color @Composable get() = LocalNeonColors.current.primary
val NeonPrimaryVariant: Color @Composable get() = LocalNeonColors.current.primaryVariant
val NeonPrimaryLight: Color @Composable get() = LocalNeonColors.current.primaryLight
val NeonAccent: Color @Composable get() = LocalNeonColors.current.accent
val NeonAccentGlow: Color @Composable get() = LocalNeonColors.current.accentGlow
val NeonSuccess: Color @Composable get() = LocalNeonColors.current.success
val NeonWarning: Color @Composable get() = LocalNeonColors.current.warning
val NeonError: Color @Composable get() = LocalNeonColors.current.error
val NeonInfo: Color @Composable get() = LocalNeonColors.current.info
val NeonDivider: Color @Composable get() = LocalNeonColors.current.divider
