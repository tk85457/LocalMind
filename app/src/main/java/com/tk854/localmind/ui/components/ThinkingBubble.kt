package com.tk854.localmind.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk854.localmind.ui.theme.*

@Composable
fun ThinkingBubble(
    reasoning: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    thinkingDurationSec: Int? = null
) {
    var userExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            userExpanded = false
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (userExpanded) 180f else 0f,
        animationSpec = tween(220),
        label = "arrowRotation"
    )

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ChatGPT o1/o3 style Thinking Header Capsule
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ChatInputBackground)
                .border(BorderStroke(0.8.dp, ChatInputBorder), RoundedCornerShape(16.dp))
                .clickable { userExpanded = !userExpanded }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NeonTextSecondary,
                modifier = Modifier.size(13.dp)
            )

            if (isStreaming) {
                ThinkingDotsText()
            } else {
                val label = when {
                    thinkingDurationSec != null && thinkingDurationSec > 0 ->
                        "Thought for ${thinkingDurationSec}s"
                    else -> "Thought"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = NeonTextSecondary
                )
            }

            if (reasoning.trim().isNotEmpty() || isStreaming) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (userExpanded) "Collapse" else "Expand",
                    tint = NeonTextSecondary,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(arrowRotation)
                )
            }
        }

        // Expanded Reasoning Block with elegant left border (ChatGPT quote style)
        val borderColor = ChatInputBorder
        AnimatedVisibility(
            visible = userExpanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 6.dp, end = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = borderColor,
                                start = Offset(x = 1.dp.toPx(), y = 0f),
                                end = Offset(x = 1.dp.toPx(), y = size.height),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        .padding(start = 12.dp)
                ) {
                    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                    val maxReasoningHeight = screenHeight * 0.42f
                    val scrollState = rememberScrollState()

                    LaunchedEffect(reasoning.length, isStreaming) {
                        if (isStreaming) {
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxReasoningHeight)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = reasoning.trim(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                fontStyle = FontStyle.Italic
                            ),
                            color = NeonTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingDotsText() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val dotCount by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotCount"
    )

    val dots = ".".repeat(dotCount.toInt() + 1)
    Text(
        text = "Thinking$dots",
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        ),
        color = NeonTextSecondary
    )
}
