package com.localmind.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import com.localmind.app.domain.model.Message
import com.localmind.app.domain.model.MessageRole
import com.localmind.app.domain.model.Model
import com.localmind.app.ui.theme.NeonElevated
import com.localmind.app.ui.theme.NeonError
import com.localmind.app.ui.theme.NeonPrimary
import com.localmind.app.ui.theme.NeonSurface
import com.localmind.app.ui.theme.NeonText
import com.localmind.app.ui.theme.NeonTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    isSpeaking: Boolean = false,
    availableModels: List<Model> = emptyList(),
    onRegenerate: () -> Unit = {},
    onRegenerateWithModel: (Model) -> Unit = {},
    onEdit: (messageId: String) -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    onSpeak: () -> Unit = {},
    onCopy: (String) -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER

    // Streaming cursor blink
    val cursorTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "cursorAlpha"
    )

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showRegenerateWithMenu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val timeLabel = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role label — small muted text above message
        Text(
            text = if (isUser) "YOU" else "LOCAL MIND AI",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            ),
            color = if (isUser) NeonPrimary.copy(alpha = 0.6f) else NeonTextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
        )

        // Thinking bubble (model only, DeepSeek/QwQ)
        if (!message.reasoningContent.isNullOrBlank()) {
            ThinkingBubble(
                reasoning = message.reasoningContent,
                isStreaming = isStreaming && message.content.isEmpty(),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Attached image preview
        if (message.imageUri != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 220.dp)
                    .align(if (isUser) Alignment.End else Alignment.Start)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonElevated)
            ) {
                AsyncImage(
                    model = message.imageUri,
                    contentDescription = "Attached Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Main message — NO background, plain text, left/right aligned
        // STREAMING FIX: cursor ko content se alag rakho taaki code block regex match na tute.
        // Agar cursor "▌" code ke andar ghus jaaye toh closing ``` match fail ho jaata hai.
        val baseContent = message.content
        val streamingCursor = if (isStreaming && cursorAlpha > 0.5f) "▌" else ""

        val hasCodeBlock = baseContent.contains("```")
        val contentWithCursor = if (streamingCursor.isNotEmpty()) baseContent + streamingCursor else baseContent

        val longPressModifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                showOptionsMenu = true
            }
        )

        if (isUser) {
            // USER: Row + Arrangement.End = hamesha right side, koi bhi content size ho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .then(longPressModifier)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    MarkdownText(
                        markdown = contentWithCursor,
                        color = NeonText,
                        fontSize = 15.5f,
                        isStreaming = isStreaming
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = NeonTextSecondary.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        } else {
            // AI: left-aligned, full width allowed (code blocks ke liye)
            Box(
                modifier = Modifier
                    .then(if (hasCodeBlock) Modifier.fillMaxWidth() else Modifier.widthIn(max = 300.dp))
                    .then(longPressModifier)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Column {
                    MarkdownText(
                        markdown = contentWithCursor,
                        color = NeonText,
                        fontSize = 15.5f,
                        isStreaming = isStreaming
                    )
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isStreaming) {
                            val statsParts = mutableListOf<String>()
                            message.ttftMs?.takeIf { it > 0L }?.let { ttft ->
                                statsParts += "TTFT ${ttft}ms"
                            }
                            message.tokensPerSecond?.takeIf { it > 0f }?.let { tps ->
                                statsParts += String.format("%.1f t/s", tps)
                            }
                            if (statsParts.isNotEmpty()) {
                                Text(
                                    text = statsParts.joinToString(" · ") + " · ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonTextSecondary.copy(alpha = 0.35f)
                                )
                            }
                        }
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = NeonTextSecondary.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }

        // Action buttons (model messages only, after streaming)
        if (!isStreaming && !isUser) {
            Row(
                modifier = Modifier.padding(top = 2.dp, start = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = { onCopy(message.content) }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy",
                        tint = NeonTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Share, "Share",
                        tint = NeonTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(26.dp)) {
                    Icon(
                        if (isSpeaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        if (isSpeaking) "Stop" else "Speak",
                        tint = NeonTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(14.dp)
                    )
                }
                IconButton(onClick = onRegenerate, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Refresh, "Regenerate",
                        tint = NeonTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }

        // Long-press context menu
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
                        null, tint = NeonPrimary
                    )
                },
                onClick = { onSpeak(); showOptionsMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Copy", color = NeonText) },
                onClick = { onCopy(message.content); showOptionsMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Share", color = NeonText) },
                onClick = { onShare(); showOptionsMenu = false }
            )
            if (isUser) {
                DropdownMenuItem(
                    text = { Text("Edit", color = NeonText) },
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onEdit(message.id); showOptionsMenu = false
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Regenerate", color = NeonText) },
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onRegenerate(); showOptionsMenu = false
                    }
                )
                // "Try again with different model" — PocketPal style
                if (availableModels.size > 1) {
                    DropdownMenuItem(
                        text = { Text("Try again with...", color = NeonTextSecondary) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                null, tint = NeonTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = { showRegenerateWithMenu = true; showOptionsMenu = false }
                    )
                }
            }
            DropdownMenuItem(
                text = { Text("Delete", color = NeonError) },
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onDelete(); showOptionsMenu = false
                }
            )
        }

        // "Try again with [Model]" submenu
        DropdownMenu(
            expanded = showRegenerateWithMenu,
            onDismissRequest = { showRegenerateWithMenu = false },
            modifier = Modifier.background(NeonSurface)
        ) {
            Text(
                text = "Select model",
                style = MaterialTheme.typography.labelSmall,
                color = NeonTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model.name,
                            color = NeonText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onRegenerateWithModel(model)
                        showRegenerateWithMenu = false
                    }
                )
            }
        }
    }
}
