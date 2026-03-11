package com.localmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmind.app.ui.theme.*

@Composable
fun ChatInputBar(
    // TextFieldValue: cursor position + scroll dono sahi kaam karte hain
    // String use karne par STT text aane ke baad cursor beginning mein reh jaata tha
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
    activeModel: com.localmind.app.domain.model.Model?,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    editingPreview: String? = null,
    onCancelEdit: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    Box(modifier = modifier.fillMaxWidth()) {
        if (isLoadingModel) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp),
                color = NeonElevated.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPrimary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading model ...",
                        color = NeonTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                color = NeonElevated,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                // PocketPal-style edit mode banner
                if (isEditMode) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = NeonPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            androidx.compose.foundation.layout.Column {
                                Text(
                                    text = "Editing message",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonPrimary
                                )
                                if (!editingPreview.isNullOrBlank()) {
                                    Text(
                                        text = editingPreview.take(40).let {
                                            if (editingPreview.length > 40) "$it…" else it
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonTextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onCancelEdit,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel edit",
                                tint = NeonTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(
                        color = NeonPrimary.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )
                }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // PocketPal-style: Model chip + attach button — LEFT side of text field
                        Column(
                            modifier = Modifier.padding(bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Model selector chip (inside input bar, above attach)
                            Row(
                                modifier = Modifier
                                    .padding(start = 8.dp, top = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeonPrimary.copy(alpha = 0.1f))
                                    .clickable { onModelClick() }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = NeonPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeModel?.name?.let {
                                        if (it.length > 12) it.take(12) + "…" else it
                                    } ?: "Select",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonPrimary,
                                    maxLines = 1,
                                    fontSize = 10.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = NeonPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            // Attach button below chip
                            IconButton(
                                onClick = onAttachClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Attach file",
                                    tint = NeonPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Text input field — TextFieldValue use karke cursor + scroll sahi kaam karta hai
                        // Jab STT se text aata hai: TextRange(text.length) cursor end mein le jaata hai
                        // Jyada text hone par field automatically scroll hoti hai cursor tak
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onInputTextChanged,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            placeholder = {
                                Text(
                                    "Message…",
                                    color = NeonTextExtraMuted,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = NeonPrimary,
                                focusedTextColor = NeonText,
                                unfocusedTextColor = NeonText,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Start),
                            shape = RoundedCornerShape(20.dp),
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

                        // Mic / Stop
                        IconButton(
                            onClick = onMicClick,
                            modifier = Modifier.padding(bottom = 4.dp, end = 6.dp).size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.StopCircle else Icons.Default.Mic,
                                contentDescription = if (isListening) "Stop" else "Voice",
                                tint = if (isListening) NeonError else NeonPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Send button
                        IconButton(
                            onClick = {
                                if (!isGenerating && canSend) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onSendClick()
                            },
                            modifier = Modifier
                                .padding(bottom = 4.dp, end = 4.dp)
                                .size(40.dp)
                                .background(
                                    color = if (isGenerating) NeonError.copy(alpha = 0.15f)
                                            else if (canSend) NeonPrimary.copy(alpha = 0.15f)
                                            else Color.Transparent,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isGenerating) Icons.Default.StopCircle
                                            else Icons.Default.ArrowUpward,
                                contentDescription = if (isGenerating) "Stop" else "Send",
                                tint = if (isGenerating) NeonError
                                    else if (canSend) NeonPrimary
                                    else NeonTextExtraMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
