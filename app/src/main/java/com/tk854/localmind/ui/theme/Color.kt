package com.tk854.localmind.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ChatGPT Aesthetic Colors - Clean White & Dark Modes
object LocalMindColors {
    // ChatGPT Dark Palette
    val DarkBackground = Color(0xFF212121)    // ChatGPT web dark background
    val DarkSurface = Color(0xFF171717)       // ChatGPT sidebar/drawer dark
    val DarkElevated = Color(0xFF2F2F2F)      // ChatGPT card/bubble dark
    val DarkTextPrimary = Color(0xFFECECEC)   // Crisp soft white
    val DarkTextSecondary = Color(0xFFB4B4B4) // Readable secondary text
    val DarkTextTertiary = Color(0xFF8E8E8E)
    val DarkTextExtraMuted = Color(0xFF71717A)
    val DarkDivider = Color(0xFF333333)       // Clean subtle dark divider

    // ChatGPT Light Palette (Pure White Theme)
    val LightBackground = Color(0xFFFFFFFF)   // Pure White
    val LightSurface = Color(0xFFFFFFFF)      // Pure White
    val LightElevated = Color(0xFFF4F4F4)     // Soft neutral grey (ChatGPT user bubble & cards)
    val LightTextPrimary = Color(0xFF0D0D0D)  // Deep Charcoal (ChatGPT primary text)
    val LightTextSecondary = Color(0xFF5D5D5D)// Neutral grey (ChatGPT subtext)
    val LightTextTertiary = Color(0xFF71717A)
    val LightTextExtraMuted = Color(0xFF8E8E93)
    val LightDivider = Color(0xFFE5E5E5)      // Crisp subtle border

    // Shared OpenAI / ChatGPT Signature Accents
    val Primary = Color(0xFF10A37F)           // OpenAI Emerald Green
    val PrimaryVariant = Color(0xFF0E8C6D)
    val PrimaryLight = Color(0xFF1CD0A2)
    val Accent = Color(0xFF10A37F)
    val AccentGlow = Color(0x2510A37F)

    // ChatGPT Specific Pill & Capsule Colors
    val CharcoalDark = Color(0xFF0D0D0D)
    val PureWhite = Color(0xFFFFFFFF)

    // Status
    val Success = Color(0xFF10A37F)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)

    // Blue Palette
    val BluePrimary = Color(0xFF3B82F6)
    val BluePrimaryVariant = Color(0xFF1D4ED8)
    val BluePrimaryLight = Color(0xFF60A5FA)
    val BlueAccent = Color(0xFF3B82F6)
    val BlueAccentGlow = Color(0x403B82F6)

    // Green Palette
    val GreenPrimary = Color(0xFF10A37F)
    val GreenPrimaryVariant = Color(0xFF047857)
    val GreenPrimaryLight = Color(0xFF34D399)
    val GreenAccent = Color(0xFF10A37F)
    val GreenAccentGlow = Color(0x4010A37F)

    // Orange Palette
    val OrangePrimary = Color(0xFFF97316)
    val OrangePrimaryVariant = Color(0xFFC2410C)
    val OrangePrimaryLight = Color(0xFFFB923C)
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
    val divider: Color,
    val userBubble: Color = if (background == LocalMindColors.LightBackground) LocalMindColors.LightElevated else LocalMindColors.DarkElevated,
    val userBubbleText: Color = if (background == LocalMindColors.LightBackground) LocalMindColors.LightTextPrimary else LocalMindColors.DarkTextPrimary,
    val inputBackground: Color = if (background == LocalMindColors.LightBackground) LocalMindColors.LightElevated else LocalMindColors.DarkElevated,
    val inputBorder: Color = if (background == LocalMindColors.LightBackground) LocalMindColors.LightDivider else LocalMindColors.DarkDivider,
    val sendButton: Color = if (background == LocalMindColors.LightBackground) LocalMindColors.CharcoalDark else LocalMindColors.PureWhite,
    val sendButtonIcon: Color = if (background == LocalMindColors.LightBackground) LocalMindColors.PureWhite else LocalMindColors.CharcoalDark
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
val ChatUserBubble: Color @Composable get() = LocalNeonColors.current.userBubble
val ChatUserBubbleText: Color @Composable get() = LocalNeonColors.current.userBubbleText
val ChatInputBackground: Color @Composable get() = LocalNeonColors.current.inputBackground
val ChatInputBorder: Color @Composable get() = LocalNeonColors.current.inputBorder
val ChatSendButton: Color @Composable get() = LocalNeonColors.current.sendButton
val ChatSendButtonIcon: Color @Composable get() = LocalNeonColors.current.sendButtonIcon
val NeonBorder: Color @Composable get() = LocalNeonColors.current.inputBorder
