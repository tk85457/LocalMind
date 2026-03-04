package com.localmind.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmind.app.R
import com.localmind.app.domain.model.Message
import com.localmind.app.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageList(
    listState: LazyListState,
    messages: List<Message>,
    isGenerating: Boolean,
    streamingResponse: String?,
    isAnalyzingDocument: Boolean,
    isAnalyzingMedia: Boolean,
    currentlySpeakingMessageId: String?,
    onSpeakClick: (Message) -> Unit,
    onRegenerate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onShare: (Message) -> Unit,
    onCopy: (String) -> Unit,
    showScrollToBottom: Boolean,
    modifier: Modifier = Modifier
) {
    val dimens = LocalDimens.current
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = dimens.paddingScreenVertical, bottom = dimens.paddingScreenVertical),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                MessageBubble(
                    message = message,
                    modifier = Modifier.animateItemPlacement()
                        .padding(horizontal = dimens.paddingScreenHorizontal),
                    onRegenerate = { onRegenerate(message.id) },
                    onEdit = { onEdit(it) },
                    onDelete = { onDelete(message.id) },
                    onShare = { onShare(message) },
                    onCopy = { onCopy(it) },
                    onSpeak = { onSpeakClick(message) },
                    isSpeaking = currentlySpeakingMessageId == message.id
                )
            }

            // Streaming text bubble — styled to match model MessageBubble
            if (isGenerating && !streamingResponse.isNullOrEmpty()) {
                item(key = "streaming_response") {
                    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                    val cursorAlpha by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "cursorAlpha"
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.paddingScreenHorizontal),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "MODEL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp
                            ),
                            color = NeonTextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 3.dp, start = 6.dp)
                        )
                        val safeResponse = streamingResponse?.trimStart() ?: ""
                        val streamText = safeResponse + if (cursorAlpha > 0.5f) "▌" else ""
                        MarkdownText(
                            markdown = streamText,
                            color = NeonText,
                            fontSize = 15.5f,
                            isSelectable = false,
                            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 10.dp)
                        )
                    }
                }
            }

            // Pre-first-token indicator — animated dots while waiting for first token
            if (isGenerating && streamingResponse.isNullOrEmpty()) {
                item(key = "pre_response_typing") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = dimens.paddingScreenHorizontal + 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val indicatorText = if (isAnalyzingDocument) stringResource(R.string.chat_analyzing_document) else if (isAnalyzingMedia) "Analyzing image..." else "Thinking..."
                        ThinkingIndicator(
                            text = indicatorText,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Scroll to bottom FAB
        androidx.compose.animation.AnimatedVisibility(
            visible = showScrollToBottom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 8.dp),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            androidx.compose.material3.FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                },
                containerColor = NeonPrimary,
                contentColor = NeonSurface,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
