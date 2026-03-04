package com.localmind.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

data class Dimens(
    val spacingSmall: Dp = 8.dp,
    val spacingMedium: Dp = 16.dp,
    val spacingLarge: Dp = 24.dp,
    val spacingExtraLarge: Dp = 32.dp,
    val paddingScreenHorizontal: Dp = 16.dp,
    val paddingScreenVertical: Dp = 16.dp,
    val iconSizeSmall: Dp = 20.dp,
    val iconSizeMedium: Dp = 24.dp,
    val iconSizeLarge: Dp = 48.dp,
    val textSizeSmall: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    val textSizeMedium: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    val textSizeLarge: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    val textSizeTitle: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    val cornerRadiusSmall: Dp = 8.dp,
    val cornerRadiusMedium: Dp = 12.dp,
    val cornerRadiusLarge: Dp = 16.dp,
    val buttonHeight: Dp = 48.dp
)

val CompactDimens = Dimens(
    spacingSmall = 8.dp,
    spacingMedium = 16.dp,
    spacingLarge = 24.dp,
    paddingScreenHorizontal = 16.dp,
    iconSizeSmall = 20.dp,
    iconSizeMedium = 24.dp,
    iconSizeLarge = 48.dp,
    textSizeTitle = 30.sp
)

val MediumDimens = Dimens(
    spacingSmall = 10.dp,
    spacingMedium = 20.dp,
    spacingLarge = 30.dp,
    paddingScreenHorizontal = 24.dp,
    iconSizeSmall = 22.dp,
    iconSizeMedium = 28.dp,
    iconSizeLarge = 56.dp,
    textSizeTitle = 40.sp
)

val ExpandedDimens = Dimens(
    spacingSmall = 12.dp,
    spacingMedium = 24.dp,
    spacingLarge = 40.dp,
    paddingScreenHorizontal = 32.dp,
    iconSizeSmall = 24.dp,
    iconSizeMedium = 32.dp,
    iconSizeLarge = 64.dp,
    textSizeTitle = 50.sp
)

val LocalDimens = staticCompositionLocalOf { CompactDimens }
