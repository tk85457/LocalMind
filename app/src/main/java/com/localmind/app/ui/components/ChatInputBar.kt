package com.localmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.localmind.app.ui.theme.*

@Composable
fun ChatInputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSendClick: () -> Unit,
    isGenerating: Boolean,
    isLoadingModel: Boolean,
    canSend: Boolean,
    modifier: Modifier = Modifier
) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
        // Single consolidated attachment button (images + PDFs)
        IconButton(
            onClick = onAttachClick,
            modifier = Modifier.padding(bottom = 6.dp).size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "Attach file (image or PDF)",
                tint = NeonPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Text input field
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
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonPrimary,
                unfocusedBorderColor = NeonTextExtraMuted,
                cursorColor = NeonPrimary,
                focusedTextColor = NeonText,
                unfocusedTextColor = NeonText,
                focusedContainerColor = NeonElevated,
                unfocusedContainerColor = NeonElevated
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Start),
            shape = RoundedCornerShape(20.dp),
            maxLines = 5
        )

        // Mic / Stop
        IconButton(
            onClick = onMicClick,
            modifier = Modifier.padding(bottom = 6.dp).size(44.dp)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.StopCircle else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Voice input",
                tint = if (isListening) NeonError else NeonPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Model/Persona Switcher Toggle
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.padding(bottom = 6.dp).size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Switch Model or Persona",
                tint = NeonPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Send button
        IconButton(
            onClick = onSendClick,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .size(44.dp)
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
