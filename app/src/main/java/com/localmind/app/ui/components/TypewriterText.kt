package com.localmind.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
    isStreaming: Boolean = false,
    typingSpeedMs: Long = 20 // Adjust speed here (lower is faster)
) {
    var displayedText by remember { mutableStateOf(if (isStreaming) "" else text) }

    LaunchedEffect(text) {
        if (isStreaming) {
            val currentLength = displayedText.length
            if (text.length > currentLength) {
                val newChars = text.length - currentLength
                // PERF FIX: Removed character-by-character loop with delay per char.
                // Old approach caused visible lag: 100 new chars = 100 * 20ms = 2s delay!
                // Now we emit in chunks instantly if falling behind, or with minimal
                // delay for small increments to keep the smooth feel.
                if (newChars > 8) {
                    // Falling behind — snap to latest text immediately
                    displayedText = text
                } else {
                    // Small increment — tiny delay for smooth feel (max 1 frame)
                    val boundedTypingDelay = typingSpeedMs.coerceIn(0L, 33L)
                    delay(boundedTypingDelay)
                    displayedText = text
                }
            } else {
                displayedText = text
            }
        } else {
            // Not streaming (e.g. history) — show immediately
            displayedText = text
        }
    }

    // Snap immediately when streaming ends to clear any remaining lag
    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            displayedText = text
        }
    }

    Text(
        text = displayedText,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
