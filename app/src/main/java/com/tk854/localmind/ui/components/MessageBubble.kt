package com.tk854.localmind.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tk854.localmind.domain.model.Message
import com.tk854.localmind.domain.model.MessageRole
import com.tk854.localmind.domain.model.Model
import com.tk854.localmind.ui.theme.*
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
    showReasoning: Boolean = true,
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
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.82f).dp

    // Streaming cursor blink
    val cursorTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "cursorAlpha"
    )

    // Speaking pulse animation
    val speakerTransition = rememberInfiniteTransition(label = "speakerPulse")
    val speakerScale by speakerTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "speakerScale"
    )

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showRegenerateWithMenu by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf<Boolean?>(null) }

    val timeLabel = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    val baseContent = visibleMessageContent(message)
    val streamingCursor = if (isStreaming && cursorAlpha > 0.5f) " ▌" else ""
    val contentWithCursor = if (streamingCursor.isNotEmpty()) baseContent + streamingCursor else baseContent
    val hasVisibleContent = contentWithCursor.isNotBlank() || !message.reasoningContent.isNullOrBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Attached image preview
        if (message.imageUri != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 240.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
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

        // Thinking / Reasoning Block for AI messages
        if (!isUser && showReasoning && !message.reasoningContent.isNullOrBlank()) {
            ThinkingBubble(
                reasoning = message.reasoningContent,
                isStreaming = false,
                thinkingDurationSec = message.thinkingDurationSec,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val clickModifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showOptionsMenu = true
            }
        )

        if (isUser) {
            // USER MESSAGE: Rounded soft bubble aligned to the right (ChatGPT style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    color = ChatUserBubble,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .widthIn(max = maxBubbleWidth)
                        .then(clickModifier)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        MarkdownText(
                            markdown = contentWithCursor,
                            color = ChatUserBubbleText,
                            fontSize = 15f,
                            isStreaming = isStreaming
                        )
                    }
                }
            }
        } else if (hasVisibleContent) {
            // ASSISTANT MESSAGE: Left-aligned, seamless background (ChatGPT canvas style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(clickModifier)
                    .padding(vertical = 2.dp)
            ) {
                MarkdownText(
                    markdown = contentWithCursor,
                    color = NeonText,
                    fontSize = 15.5f,
                    isStreaming = isStreaming
                )

                // ChatGPT Action Toolbar Below Response
                if (!isStreaming && hasVisibleContent) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Action Icons: Copy, Thumbs Up, Thumbs Down, Speaker, Regenerate, Share
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Copy button
                            IconButton(
                                onClick = { onCopy(baseContent) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    tint = NeonTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Like (Thumbs up)
                            IconButton(
                                onClick = {
                                    liked = if (liked == true) null else true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (liked == true) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "Good response",
                                    tint = if (liked == true) NeonPrimary else NeonTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Dislike (Thumbs down)
                            IconButton(
                                onClick = {
                                    liked = if (liked == false) null else false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (liked == false) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                    contentDescription = "Bad response",
                                    tint = if (liked == false) NeonError else NeonTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Read Aloud / TTS
                            IconButton(
                                onClick = onSpeak,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isSpeaking) "Stop reading" else "Read aloud",
                                    tint = if (isSpeaking) NeonPrimary else NeonTextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .scale(if (isSpeaking) speakerScale else 1f)
                                )
                            }

                            // Regenerate
                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate response",
                                    tint = NeonTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Share
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = NeonTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Subtle telemetry stats (e.g. "24.5 t/s")
                        val statsParts = mutableListOf<String>()
                        message.tokensPerSecond?.takeIf { it > 0f }?.let { tps ->
                            statsParts += String.format("%.1f t/s", tps)
                        }
                        if (statsParts.isNotEmpty()) {
                            Text(
                                text = statsParts.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = NeonTextExtraMuted
                            )
                        }
                    }
                }
            }
        }

        // Long-Press Context Menu
        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false },
            modifier = Modifier.background(NeonSurface)
        ) {
            DropdownMenuItem(
                text = { Text("Copy", color = NeonText) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = NeonTextSecondary, modifier = Modifier.size(18.dp)) },
                onClick = { onCopy(baseContent); showOptionsMenu = false }
            )
            DropdownMenuItem(
                text = { Text(if (isSpeaking) "Stop Reading" else "Read Aloud", color = NeonText) },
                leadingIcon = {
                    Icon(
                        if (isSpeaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        null,
                        tint = if (isSpeaking) NeonPrimary else NeonTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = { onSpeak(); showOptionsMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Share", color = NeonText) },
                leadingIcon = { Icon(Icons.Default.Share, null, tint = NeonTextSecondary, modifier = Modifier.size(18.dp)) },
                onClick = { onShare(); showOptionsMenu = false }
            )
            if (isUser) {
                DropdownMenuItem(
                    text = { Text("Edit Message", color = NeonText) },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = NeonTextSecondary, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEdit(message.id)
                        showOptionsMenu = false
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Regenerate", color = NeonText) },
                    leadingIcon = { Icon(Icons.Default.Refresh, null, tint = NeonTextSecondary, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRegenerate()
                        showOptionsMenu = false
                    }
                )
                if (availableModels.size > 1) {
                    DropdownMenuItem(
                        text = { Text("Try with different model", color = NeonText) },
                        leadingIcon = { Icon(Icons.Default.SmartToy, null, tint = NeonTextSecondary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showOptionsMenu = false
                            showRegenerateWithMenu = true
                        }
                    )
                }
            }
            HorizontalDivider(color = NeonDivider, thickness = 0.5.dp)
            DropdownMenuItem(
                text = { Text("Delete", color = NeonError) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = NeonError, modifier = Modifier.size(18.dp)) },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    showOptionsMenu = false
                }
            )
        }

        // Submenu: Try again with specific model
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
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRegenerateWithModel(model)
                        showRegenerateWithMenu = false
                    }
                )
            }
        }
    }
}

private fun visibleMessageContent(message: Message): String {
    return message.content
        .replace("<think>", "")
        .replace("</think>", "")
        .trim()
}
