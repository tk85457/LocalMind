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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.localmind.app.R
import com.localmind.app.domain.model.Message
import com.localmind.app.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// Sealed class for mixed list items (messages + date headers)
private sealed class ChatListItem {
    data class MessageItem(val message: Message) : ChatListItem()
    data class DateDivider(val label: String) : ChatListItem()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageList(
    listState: LazyListState,
    messages: List<Message>,
    isGenerating: Boolean,
    streamingResponse: String?,
    streamingReasoning: String? = null,
    isAnalyzingDocument: Boolean,
    isAnalyzingMedia: Boolean,
    currentlySpeakingMessageId: String?,
    onSpeakClick: (Message) -> Unit,
    onRegenerate: (String) -> Unit,
    onEdit: (messageId: String) -> Unit,
    onDelete: (String) -> Unit,
    onShare: (Message) -> Unit,
    onCopy: (String) -> Unit,
    availableModels: List<com.localmind.app.domain.model.Model> = emptyList(),
    onRegenerateWithModel: (String, com.localmind.app.domain.model.Model) -> Unit = { _, _ -> },
    streamingModelLabel: String = "MODEL",
    modifier: Modifier = Modifier
) {
    val dimens = LocalDimens.current
    val coroutineScope = rememberCoroutineScope()

    val LEAVE_PX = 40

    val atLatest by remember {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            val firstOff = listState.firstVisibleItemScrollOffset
            firstIdx == 0 && firstOff < LEAVE_PX
        }
    }

    val showJumpToLatest by remember {
        derivedStateOf { !atLatest }
    }

    val totalItems by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }

    // SCROLL FIX: Jab generation start ho toh latest pe scroll karo.
    // Jab generation STOP ho toh streaming bubble hata aur layout shift fix karo.
    //
    // BUG ROOT CAUSE:
    // reverseLayout=true mein streaming_response item index=0 pe hota hai (screen ke bottom pe).
    // Jab stop press hota hai:
    //   1. streamingResponse = null → streaming bubble LazyColumn se hata
    //   2. Layout recalculate hoti hai, scroll anchor shift ho jaata hai
    //   3. Yahi "jump" visible hota hai
    // FIX: Stop hone ke baad 50ms delay se scrollToItem(0) instantly call karo —
    // sirf tab jab user bottom ke paas tha (firstVisibleItemIndex <= 2).
    // Instant scroll (no animation) koi jarring effect nahi deta kyunki position pehle se wahi hai.
    androidx.compose.runtime.LaunchedEffect(isGenerating) {
        if (isGenerating) {
            // Generation shuru hui — latest pe jaao
            if (totalItems > 0) listState.animateScrollToItem(0)
        } else {
            // Generation ruk gayi — streaming items hatenge, layout shift hogi
            // 50ms wait karo taaki Compose streaming bubble remove kar sake
            kotlinx.coroutines.delay(50)
            // Sirf tab correct karo jab user bottom ke paas tha
            // (agar user upar scroll karke pura messages dekh raha tha toh disturb mat karo)
            if (listState.firstVisibleItemIndex <= 2) {
                listState.scrollToItem(0) // Instant snap — no animation, no jump
            }
        }
    }

    // Message add hone par scroll karo — SIRF jab token streaming active ho.
    // "isGenerating=true" kaafi nahi — stop press ke baad bhi briefly true hota hai
    // (partial response save hone tak). streamingResponse check se race condition fix hoti hai.
    androidx.compose.runtime.LaunchedEffect(messages.size) {
        if (isGenerating && !streamingResponse.isNullOrEmpty() && atLatest && totalItems > 0) {
            listState.animateScrollToItem(0)
        }
    }

    // Build flat list: messages (reversed = newest first) + date dividers
    val chatListItems: List<ChatListItem> = remember(messages) {
        if (messages.isEmpty()) return@remember emptyList()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val result = mutableListOf<ChatListItem>()
        val reversedMsgs = messages.asReversed() // newest first

        reversedMsgs.forEachIndexed { index, msg ->
            result.add(ChatListItem.MessageItem(msg))

            val msgDate = Instant.ofEpochMilli(msg.timestamp)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val nextDate = reversedMsgs.getOrNull(index + 1)?.let {
                Instant.ofEpochMilli(it.timestamp)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
            }

            // At date boundary or end of list — insert a date header
            if (nextDate == null || nextDate != msgDate) {
                val label = when {
                    msgDate.isEqual(today) -> "Today"
                    msgDate.isEqual(yesterday) -> "Yesterday"
                    msgDate.isAfter(today.minusDays(7)) ->
                        msgDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                            .replaceFirstChar { it.uppercase() }
                    else ->
                        msgDate.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
                            .replaceFirstChar { it.uppercase() } + " ${msgDate.dayOfMonth}"
                }
                result.add(ChatListItem.DateDivider(label))
            }
        }
        result
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(top = dimens.paddingScreenVertical, bottom = dimens.paddingScreenVertical),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
        ) {
            // Pre-first-token typing indicator
            if (isGenerating && streamingResponse.isNullOrEmpty()) {
                item(key = "pre_response_typing") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = dimens.paddingScreenHorizontal + 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val indicatorText = if (isAnalyzingDocument)
                            stringResource(R.string.chat_analyzing_document)
                        else if (isAnalyzingMedia) "Analyzing image..."
                        else "Thinking..."
                        ThinkingIndicator(
                            text = indicatorText,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Streaming text bubble
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
                            text = streamingModelLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp
                            ),
                            color = NeonTextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 3.dp, start = 6.dp)
                        )
                        val safeResponse = streamingResponse.trimStart()
                        val cursor = if (cursorAlpha > 0.5f) "\u258c" else ""
                        // CODE BLOCK FIX: MarkdownText use karo taaki streaming mein bhi
                        // CodeBlock.kt ka dark theme render ho.
                        // Performance: parseSegments ab codeBlockCount pe remember karta hai,
                        // plain text tokens pe zero regex work hota hai.
                        // Agar ``` nahi hai toh fast-path = plain Text widget hi render hoga.
                        val hasCode = safeResponse.contains("```")
                        if (hasCode) {
                            MarkdownText(
                                markdown = safeResponse + cursor,
                                color = NeonText,
                                fontSize = 15.5f,
                                isStreaming = true,
                                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 10.dp)
                            )
                        } else {
                            // No code block — plain Text is fastest, zero overhead
                            Text(
                                text = safeResponse + cursor,
                                color = NeonText,
                                fontSize = 15.5.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 10.dp)
                            )
                        }
                    }
                }
            }

            // Streaming reasoning bubble
            if (isGenerating && !streamingReasoning.isNullOrEmpty()) {
                item(key = "streaming_reasoning") {
                    ThinkingBubble(
                        reasoning = streamingReasoning,
                        isStreaming = streamingResponse.isNullOrEmpty(),
                        modifier = Modifier.padding(horizontal = dimens.paddingScreenHorizontal)
                    )
                }
            }

            // Messages + Date headers as a flat list
            items(
                items = chatListItems,
                key = { item ->
                    when (item) {
                        is ChatListItem.MessageItem -> item.message.id
                        is ChatListItem.DateDivider -> "date_${item.label}"
                    }
                }
            ) { item ->
                when (item) {
                    is ChatListItem.DateDivider -> {
                        // Date divider between message groups
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = NeonTextExtraMuted.copy(alpha = 0.2f)
                            )
                            Surface(
                                color = NeonElevated.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonTextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = NeonTextExtraMuted.copy(alpha = 0.2f)
                            )
                        }
                    }
                    is ChatListItem.MessageItem -> {
                        MessageBubble(
                            message = item.message,
                            modifier = Modifier
                                .animateItemPlacement()
                                .fillMaxWidth()
                                .padding(horizontal = dimens.paddingScreenHorizontal),
                            availableModels = availableModels,
                            onRegenerate = { onRegenerate(item.message.id) },
                            onRegenerateWithModel = { model -> onRegenerateWithModel(item.message.id, model) },
                            onEdit = { onEdit(it) },
                            onDelete = { onDelete(item.message.id) },
                            onShare = { onShare(item.message) },
                            onCopy = { onCopy(it) },
                            onSpeak = { onSpeakClick(item.message) },
                            isSpeaking = currentlySpeakingMessageId == item.message.id
                        )
                    }
                }
            }
        }

        // Scroll-to-latest FAB
        AnimatedVisibility(
            visible = showJumpToLatest,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 8.dp),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = NeonPrimary,
                contentColor = NeonSurface,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Jump to latest",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
