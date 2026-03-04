package com.localmind.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.localmind.app.domain.model.Message
import com.localmind.app.domain.model.MessageRole
import com.localmind.app.ui.theme.NeonElevated
import com.localmind.app.ui.theme.NeonError
import com.localmind.app.ui.theme.NeonPrimary
import com.localmind.app.ui.theme.NeonPrimaryVariant
import com.localmind.app.ui.theme.NeonSurface
import com.localmind.app.ui.theme.NeonText
import com.localmind.app.ui.theme.NeonTextSecondary

/**
 * Premium Neon-themed message bubble.
 *
 * - **User messages**: right-aligned with a subtle neon-gradient background and
 *   asymmetric rounded corners (top-start, top-end, bottom-start rounded; bottom-end sharp).
 * - **Model messages**: left-aligned with an elevated dark surface and
 *   mirrored asymmetric corners (top-start, top-end, bottom-end rounded; bottom-start sharp).
 * - Smooth content-size animation when streaming text grows.
 * - Long-press context menu for actions (Copy, Share, Edit, Regenerate, Delete).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    isSpeaking: Boolean = false,
    onRegenerate: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onCopy: (String) -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER

    // Asymmetric bubble corners – the "origin" corner is sharp
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    // Neon gradient for user bubble
    val userGradient = Brush.linearGradient(
        colors = listOf(
            NeonPrimary.copy(alpha = 0.18f),
            NeonPrimaryVariant.copy(alpha = 0.10f)
        )
    )

    // Streaming cursor animation
    val cursorTransition = rememberInfiniteTransition(label = "streamCursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streamCursorAlpha"
    )

    var showOptionsMenu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Root container — full width, alignment handled inside
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role label
        Text(
            text = if (isUser) "YOU" else "MODEL",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            ),
            color = if (isUser) NeonPrimary.copy(alpha = 0.8f) else NeonTextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 3.dp, start = 6.dp, end = 6.dp)
        )

        // Thinking / reasoning section (model only)
        if (!message.reasoningContent.isNullOrBlank()) {
            ThinkingBubble(
                reasoning = message.reasoningContent,
                isStreaming = isStreaming && message.content.isEmpty(),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Attached image preview
        if (message.imageUri != null) {
            Surface(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 240.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp)),
                color = NeonElevated
            ) {
                AsyncImage(
                    model = message.imageUri,
                    contentDescription = "Attached Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // ── Main Bubble ────────────────────────────────────────────────
        val displayContent = if (isStreaming) {
            message.content + if (cursorAlpha > 0.5f) "▌" else ""
        } else {
            message.content
        }

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                        )
                        showOptionsMenu = true
                    }
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                MarkdownText(
                    markdown = displayContent,
                    color = NeonText,
                    fontSize = 15.5f
                )

                // Inference statistics (model messages only, after streaming)
                if (!isUser && !isStreaming) {
                    val statsParts = mutableListOf<String>()
                    message.ttftMs?.let { statsParts += "TTFT ${it}ms" }
                    message.tokensPerSecond?.takeIf { it > 0f }?.let { tps ->
                        statsParts += String.format("%.1f tok/s", tps)
                        statsParts += String.format("%.0f ms/token", 1000f / tps)
                    }
                    message.inferenceSource?.let { source ->
                        if (source.isNotBlank()) statsParts += source
                    }
                    if (statsParts.isNotEmpty()) {
                        Text(
                            text = statsParts.joinToString(" | "),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonTextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // ── Action buttons (model messages only, after streaming) ────────
        if (!isStreaming && !isUser) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { onCopy(message.content) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = NeonTextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = NeonTextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop Reading" else "Read Aloud",
                        tint = NeonTextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(onClick = onRegenerate, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate",
                        tint = NeonTextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // ── Context menu (long press) ────────────────────────────────────
        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false },
            modifier = Modifier.background(NeonSurface)
        ) {
            DropdownMenuItem(
                text = { Text(if (isSpeaking) "Stop Reading" else "Speak", color = NeonText) },
                leadingIcon = {
                    Icon(
                        if (isSpeaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = NeonPrimary
                    )
                },
                onClick = {
                    onSpeak()
                    showOptionsMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Copy", color = NeonText) },
                onClick = {
                    onCopy(message.content)
                    showOptionsMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Share", color = NeonText) },
                onClick = {
                    onShare()
                    showOptionsMenu = false
                }
            )
            if (isUser) {
                DropdownMenuItem(
                    text = { Text("Edit", color = NeonText) },
                    onClick = {
                        onEdit(message.content)
                        showOptionsMenu = false
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Regenerate", color = NeonText) },
                    onClick = {
                        onRegenerate()
                        showOptionsMenu = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = NeonError) },
                onClick = {
                    onDelete()
                    showOptionsMenu = false
                }
            )
        }
    }
}

