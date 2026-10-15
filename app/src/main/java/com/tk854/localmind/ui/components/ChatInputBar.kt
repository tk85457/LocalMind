package com.tk854.localmind.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk854.localmind.domain.model.Model
import com.tk854.localmind.ui.theme.*
import com.tk854.localmind.ui.viewmodel.Attachment

@Composable
fun ChatInputBar(
    inputText: TextFieldValue,
    onInputTextChanged: (TextFieldValue) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit,
    isGenerating: Boolean,
    isLoadingModel: Boolean,
    canSend: Boolean,
    onModelClick: () -> Unit,
    activeModel: Model?,
    modifier: Modifier = Modifier,
    attachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (Attachment) -> Unit = {},
    isEditMode: Boolean = false,
    editingPreview: String? = null,
    onCancelEdit: () -> Unit = {},
    showThinkingToggle: Boolean = false,
    isThinkingEnabled: Boolean = true,
    onThinkingToggle: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    // Mic pulsing animation when recording
    val micTransition = rememberInfiniteTransition(label = "micPulse")
    val micScale by micTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "micScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoadingModel) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                color = ChatInputBackground,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, ChatInputBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = NeonTextSecondary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading model...",
                        color = NeonTextSecondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                    )
                }
            }
        } else {
            // Main ChatGPT Floating Capsule
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ChatInputBackground,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, ChatInputBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Edit Mode Header Banner
                    if (isEditMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = NeonText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Column {
                                    Text(
                                        text = "Editing message",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NeonText
                                    )
                                    if (!editingPreview.isNullOrBlank()) {
                                        Text(
                                            text = editingPreview.take(50).let {
                                                if (editingPreview.length > 50) "$it..." else it
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonTextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onCancelEdit, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel edit",
                                    tint = NeonTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = ChatInputBorder, thickness = 0.5.dp)
                    }

                    // Model Badge / Think Indicator Pill (if reasoning or custom model)
                    if (showThinkingToggle || activeModel != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Model selector pill
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NeonSurface)
                                    .border(BorderStroke(0.8.dp, ChatInputBorder), RoundedCornerShape(14.dp))
                                    .clickable { onModelClick() }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = NeonTextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = activeModel?.name?.let {
                                        if (it.length > 18) it.take(18) + "..." else it
                                    } ?: "Select Model",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = NeonText,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = NeonTextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            // Think Mode Toggle (ChatGPT o1/o3-mini style)
                            if (showThinkingToggle) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isThinkingEnabled) NeonText else NeonSurface
                                        )
                                        .border(BorderStroke(0.8.dp, ChatInputBorder), RoundedCornerShape(14.dp))
                                        .clickable { onThinkingToggle() }
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = if (isThinkingEnabled) "Thinking Enabled" else "Thinking Disabled",
                                        tint = if (isThinkingEnabled) NeonBackground else NeonTextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Reason",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = if (isThinkingEnabled) NeonBackground else NeonTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Attachment chips row inside input capsule
                    AnimatedVisibility(
                        visible = attachments.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(attachments) { attachment ->
                                Surface(
                                    color = NeonElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(0.8.dp, ChatInputBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = null,
                                            tint = NeonPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = attachment.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = NeonText,
                                            maxLines = 1,
                                            modifier = Modifier.widthIn(max = 120.dp)
                                        )
                                        IconButton(
                                            onClick = { onRemoveAttachment(attachment) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = NeonTextSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Input Row: [+] [Message...] [Mic] [Send]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Attachment Button (+)
                        IconButton(
                            onClick = onAttachClick,
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Attach file",
                                tint = NeonTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Text Field
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onInputTextChanged,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp),
                            placeholder = {
                                Text(
                                    "Message Local Mind...",
                                    color = NeonTextExtraMuted,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    textAlign = TextAlign.Start
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = NeonText,
                                focusedTextColor = NeonText,
                                unfocusedTextColor = NeonText,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = NeonText,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Start
                            ),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (isGenerating) ImeAction.Default else ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (!isGenerating && inputText.text.isNotBlank()) {
                                        onSendClick()
                                    }
                                }
                            )
                        )

                        // Voice / Microphone button
                        IconButton(
                            onClick = onMicClick,
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.StopCircle else Icons.Default.Mic,
                                contentDescription = if (isListening) "Stop listening" else "Voice input",
                                tint = if (isListening) NeonError else NeonTextSecondary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(if (isListening) micScale else 1f)
                            )
                        }

                        // Circular Send / Stop Button (ChatGPT signature circle)
                        val targetSendBgColor = when {
                            isGenerating -> NeonText
                            canSend -> ChatSendButton
                            else -> ChatInputBorder
                        }
                        val sendBgColor by animateColorAsState(
                            targetValue = targetSendBgColor,
                            animationSpec = tween(250),
                            label = "sendBgColor"
                        )
                        val targetSendIconColor = when {
                            isGenerating -> NeonBackground
                            canSend -> ChatSendButtonIcon
                            else -> NeonTextExtraMuted
                        }
                        val sendIconColor by animateColorAsState(
                            targetValue = targetSendIconColor,
                            animationSpec = tween(250),
                            label = "sendIconColor"
                        )

                        IconButton(
                            onClick = {
                                if (!isGenerating && canSend) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onSendClick()
                            },
                            modifier = Modifier
                                .padding(bottom = 2.dp, end = 2.dp)
                                .size(36.dp)
                                .background(color = sendBgColor, shape = CircleShape)
                        ) {
                            AnimatedContent(
                                targetState = isGenerating,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(180)) togetherWith
                                    fadeOut(animationSpec = tween(180))
                                },
                                label = "sendStopTransition"
                            ) { generating ->
                                if (generating) {
                                    // Stop generation square icon
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(sendIconColor, shape = RoundedCornerShape(2.dp))
                                    )
                                } else {
                                    // Crisp arrow upward
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Send",
                                        tint = sendIconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ChatGPT Signature Bottom Disclaimer
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Local Mind can make mistakes. Verify important info.",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            ),
            color = NeonTextExtraMuted,
            textAlign = TextAlign.Center
        )
    }
}
