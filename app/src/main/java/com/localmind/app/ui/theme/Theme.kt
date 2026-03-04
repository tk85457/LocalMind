package com.localmind.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = LocalMindColors.Primary,
    onPrimary = LocalMindColors.DarkTextPrimary,
    primaryContainer = LocalMindColors.PrimaryVariant,
    onPrimaryContainer = LocalMindColors.DarkTextPrimary,
    secondary = LocalMindColors.PrimaryLight,
    onSecondary = LocalMindColors.DarkTextPrimary,
    secondaryContainer = LocalMindColors.DarkElevated,
    onSecondaryContainer = LocalMindColors.DarkTextSecondary,
    tertiary = LocalMindColors.Accent,
    onTertiary = LocalMindColors.DarkTextPrimary,
    background = LocalMindColors.DarkBackground,
    onBackground = LocalMindColors.DarkTextPrimary,
    surface = LocalMindColors.DarkSurface,
    onSurface = LocalMindColors.DarkTextPrimary,
    surfaceVariant = LocalMindColors.DarkElevated,
    onSurfaceVariant = LocalMindColors.DarkTextSecondary,
    error = LocalMindColors.Error,
    onError = Color.White,
    outline = LocalMindColors.DarkDivider,
    outlineVariant = LocalMindColors.DarkDivider.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = LocalMindColors.Primary,
    onPrimary = Color.White,
    primaryContainer = LocalMindColors.PrimaryLight,
    onPrimaryContainer = Color.White,
    secondary = LocalMindColors.Primary,
    onSecondary = Color.White,
    secondaryContainer = LocalMindColors.LightElevated,
    onSecondaryContainer = LocalMindColors.LightTextSecondary,
    tertiary = LocalMindColors.Accent,
    onTertiary = Color.White,
    background = LocalMindColors.LightBackground,
    onBackground = LocalMindColors.LightTextPrimary,
    surface = LocalMindColors.LightSurface,
    onSurface = LocalMindColors.LightTextPrimary,
    surfaceVariant = LocalMindColors.LightElevated,
    onSurfaceVariant = LocalMindColors.LightTextSecondary,
    error = LocalMindColors.Error,
    onError = Color.White,
    outline = LocalMindColors.LightDivider,
    outlineVariant = LocalMindColors.LightDivider.copy(alpha = 0.5f)
)

@Composable
fun LocalMindTheme(
    darkMode: Boolean = true,
    fontScale: Float = 1.0f,
    themeColor: String = "Neon",
    fontFamily: String = "Default",
    content: @Composable () -> Unit
) {
    // ... (color logic remains the same)
    val (primary, primaryVariant, primaryLight, accent, accentGlow) = when (themeColor) {
        "Blue" -> listOf(
            LocalMindColors.BluePrimary,
            LocalMindColors.BluePrimaryVariant,
            LocalMindColors.BluePrimaryLight,
            LocalMindColors.BlueAccent,
            LocalMindColors.BlueAccentGlow
        )
        "Green" -> listOf(
            LocalMindColors.GreenPrimary,
            LocalMindColors.GreenPrimaryVariant,
            LocalMindColors.GreenPrimaryLight,
            LocalMindColors.GreenAccent,
            LocalMindColors.GreenAccentGlow
        )
        "Orange" -> listOf(
            LocalMindColors.OrangePrimary,
            LocalMindColors.OrangePrimaryVariant,
            LocalMindColors.OrangePrimaryLight,
            LocalMindColors.OrangeAccent,
            LocalMindColors.OrangeAccentGlow
        )
        "Purple" -> listOf(
            Color(0xFF9C27B0), // Primary
            Color(0xFF7B1FA2), // Variant
            Color(0xFFE1BEE7), // Light
            Color(0xFFE040FB), // Accent
            Color(0xFFE040FB)  // Glow
        )
        "Red" -> listOf(
            Color(0xFFF44336),
            Color(0xFFD32F2F),
            Color(0xFFFFCDD2),
            Color(0xFFFF5252),
            Color(0xFFFF5252)
        )
        "Teal" -> listOf(
            Color(0xFF009688),
            Color(0xFF00796B),
            Color(0xFFB2DFDB),
            Color(0xFF64FFDA),
            Color(0xFF64FFDA)
        )
        "Pink" -> listOf(
            Color(0xFFE91E63),
            Color(0xFFC2185B),
            Color(0xFFF8BBD0),
            Color(0xFFFF4081),
            Color(0xFFFF4081)
        )
        "Cyan" -> listOf(
            Color(0xFF00BCD4),
            Color(0xFF0097A7),
            Color(0xFFB2EBF2),
            Color(0xFF18FFFF),
            Color(0xFF18FFFF)
        )
        else -> listOf(
            LocalMindColors.Primary,
            LocalMindColors.PrimaryVariant,
            LocalMindColors.PrimaryLight,
            LocalMindColors.Accent,
            LocalMindColors.AccentGlow
        )
    }

    // Cast colors to Color type to avoid type inference issues with List<Any>
    val safePrimary = primary as Color
    val safePrimaryVariant = primaryVariant as Color
    val safePrimaryLight = primaryLight as Color
    val safeAccent = accent as Color
    val safeAccentGlow = accentGlow as Color

    val neonColors = remember(darkMode, themeColor) {
        if (darkMode) {
            NeonColors(
                background = LocalMindColors.DarkBackground,
                surface = LocalMindColors.DarkSurface,
                elevated = LocalMindColors.DarkElevated,
                text = LocalMindColors.DarkTextPrimary,
                textSecondary = LocalMindColors.DarkTextSecondary,
                textTertiary = LocalMindColors.DarkTextTertiary,
                textExtraMuted = LocalMindColors.DarkTextExtraMuted,
                primary = safePrimary,
                primaryVariant = safePrimaryVariant,
                primaryLight = safePrimaryLight,
                accent = safeAccent,
                accentGlow = safeAccentGlow,
                success = LocalMindColors.Success,
                warning = LocalMindColors.Warning,
                error = LocalMindColors.Error,
                info = LocalMindColors.Info,
                divider = LocalMindColors.DarkDivider
            )
        } else {
            NeonColors(
                background = LocalMindColors.LightBackground,
                surface = LocalMindColors.LightSurface,
                elevated = LocalMindColors.LightElevated,
                text = LocalMindColors.LightTextPrimary,
                textSecondary = LocalMindColors.LightTextSecondary,
                textTertiary = LocalMindColors.LightTextTertiary,
                textExtraMuted = LocalMindColors.LightTextExtraMuted,
                primary = safePrimary,
                primaryVariant = safePrimaryVariant,
                primaryLight = safePrimaryLight,
                accent = safeAccent,
                accentGlow = safeAccentGlow,
                success = LocalMindColors.Success,
                warning = LocalMindColors.Warning,
                error = LocalMindColors.Error,
                info = LocalMindColors.Info,
                divider = LocalMindColors.LightDivider
            )
        }
    }

    val colorScheme = if (darkMode) {
        darkColorScheme(
            primary = safePrimary,
            onPrimary = LocalMindColors.DarkTextPrimary,
            primaryContainer = safePrimaryVariant,
            onPrimaryContainer = LocalMindColors.DarkTextPrimary,
            secondary = safePrimaryLight,
            onSecondary = LocalMindColors.DarkTextPrimary,
            secondaryContainer = LocalMindColors.DarkElevated,
            onSecondaryContainer = LocalMindColors.DarkTextSecondary,
            tertiary = safeAccent,
            onTertiary = LocalMindColors.DarkTextPrimary,
            background = LocalMindColors.DarkBackground,
            onBackground = LocalMindColors.DarkTextPrimary,
            surface = LocalMindColors.DarkSurface,
            onSurface = LocalMindColors.DarkTextPrimary,
            surfaceVariant = LocalMindColors.DarkElevated,
            onSurfaceVariant = LocalMindColors.DarkTextSecondary,
            error = LocalMindColors.Error,
            onError = Color.White,
            outline = LocalMindColors.DarkDivider,
            outlineVariant = LocalMindColors.DarkDivider.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = safePrimary,
            onPrimary = Color.White,
            primaryContainer = safePrimaryLight,
            onPrimaryContainer = Color.White,
            secondary = safePrimary,
            onSecondary = Color.White,
            secondaryContainer = LocalMindColors.LightElevated,
            onSecondaryContainer = LocalMindColors.LightTextSecondary,
            tertiary = safeAccent,
            onTertiary = Color.White,
            background = LocalMindColors.LightBackground,
            onBackground = LocalMindColors.LightTextPrimary,
            surface = LocalMindColors.LightSurface,
            onSurface = LocalMindColors.LightTextPrimary,
            surfaceVariant = LocalMindColors.LightElevated,
            onSurfaceVariant = LocalMindColors.LightTextSecondary,
            error = LocalMindColors.Error,
            onError = Color.White,
            outline = LocalMindColors.LightDivider,
            outlineVariant = LocalMindColors.LightDivider.copy(alpha = 0.5f)
        )
    }

    val scaledTypography = remember(fontScale, fontFamily) {
        getTypography(fontScale, fontFamily)
    }

    CompositionLocalProvider(LocalNeonColors provides neonColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            shapes = Shapes,
            content = content
        )
    }
}
