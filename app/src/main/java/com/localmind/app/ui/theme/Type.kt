package com.localmind.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using system default Inter font (available on most Android devices)
// For custom fonts, add font files to res/font/
val Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

val availableFontFamilies = mapOf(
    "Default" to FontFamily.Default,
    "Serif" to FontFamily.Serif,
    "SansSerif" to FontFamily.SansSerif,
    "Monospace" to FontFamily.Monospace,
    "Cursive" to FontFamily.Cursive,
    "Casual" to FontFamily.Cursive,
    "Tech" to FontFamily.Monospace,
    "Classic" to FontFamily.Serif,
    "Modern" to FontFamily.SansSerif,
    "Soft" to FontFamily.Default
)

fun getTypography(fontScale: Float = 1.0f, fontFamilyName: String = "Default"): Typography {
    val family = availableFontFamilies[fontFamilyName] ?: FontFamily.Default

    return Typography(
        displayLarge = Typography.displayLarge.copy(fontFamily = family, fontSize = Typography.displayLarge.fontSize * fontScale, lineHeight = Typography.displayLarge.lineHeight * fontScale),
        displayMedium = Typography.displayMedium.copy(fontFamily = family, fontSize = Typography.displayMedium.fontSize * fontScale, lineHeight = Typography.displayMedium.lineHeight * fontScale),
        displaySmall = Typography.displaySmall.copy(fontFamily = family, fontSize = Typography.displaySmall.fontSize * fontScale, lineHeight = Typography.displaySmall.lineHeight * fontScale),
        headlineLarge = Typography.headlineLarge.copy(fontFamily = family, fontSize = Typography.headlineLarge.fontSize * fontScale, lineHeight = Typography.headlineLarge.lineHeight * fontScale),
        headlineMedium = Typography.headlineMedium.copy(fontFamily = family, fontSize = Typography.headlineMedium.fontSize * fontScale, lineHeight = Typography.headlineMedium.lineHeight * fontScale),
        headlineSmall = Typography.headlineSmall.copy(fontFamily = family, fontSize = Typography.headlineSmall.fontSize * fontScale, lineHeight = Typography.headlineSmall.lineHeight * fontScale),
        titleLarge = Typography.titleLarge.copy(fontFamily = family, fontSize = Typography.titleLarge.fontSize * fontScale, lineHeight = Typography.titleLarge.lineHeight * fontScale),
        titleMedium = Typography.titleMedium.copy(fontFamily = family, fontSize = Typography.titleMedium.fontSize * fontScale, lineHeight = Typography.titleMedium.lineHeight * fontScale),
        titleSmall = Typography.titleSmall.copy(fontFamily = family, fontSize = Typography.titleSmall.fontSize * fontScale, lineHeight = Typography.titleSmall.lineHeight * fontScale),
        bodyLarge = Typography.bodyLarge.copy(fontFamily = family, fontSize = Typography.bodyLarge.fontSize * fontScale, lineHeight = Typography.bodyLarge.lineHeight * fontScale),
        bodyMedium = Typography.bodyMedium.copy(fontFamily = family, fontSize = Typography.bodyMedium.fontSize * fontScale, lineHeight = Typography.bodyMedium.lineHeight * fontScale),
        bodySmall = Typography.bodySmall.copy(fontFamily = family, fontSize = Typography.bodySmall.fontSize * fontScale, lineHeight = Typography.bodySmall.lineHeight * fontScale),
        labelLarge = Typography.labelLarge.copy(fontFamily = family, fontSize = Typography.labelLarge.fontSize * fontScale, lineHeight = Typography.labelLarge.lineHeight * fontScale),
        labelMedium = Typography.labelMedium.copy(fontFamily = family, fontSize = Typography.labelMedium.fontSize * fontScale, lineHeight = Typography.labelMedium.lineHeight * fontScale),
        labelSmall = Typography.labelSmall.copy(fontFamily = family, fontSize = Typography.labelSmall.fontSize * fontScale, lineHeight = Typography.labelSmall.lineHeight * fontScale)
    )
}
