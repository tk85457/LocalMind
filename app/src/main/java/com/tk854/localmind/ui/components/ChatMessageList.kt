package com.tk854.localmind.ui.components

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
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
import com.tk854.localmind.R
import com.tk854.localmind.domain.model.Message
import com.tk854.localmind.ui.theme.*
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
    isThinking: Boolean = false,
    // PART 13: Settings toggle  —  "Show reasoning" option
    showReasoning: Boolean = true,
    isAnalyzingDocument: Boolean,
    isAnalyzingMedia: Boolean,
    currentlySpeakingMessageId: String?,
    onSpeakClick: (Message) -> Unit,
    onRegenerate: (String) -> Unit,
    onEdit: (messageId: String) -> Unit,
    onDelete: (String) -> Unit,
    onShare: (Message) -> Unit,
    onCopy: (String) -> Unit,
    availableModels: List<com.tk854.localmind.domain.model.Model> = emptyList(),
    onRegenerateWithModel: (String, com.tk854.localmind.domain.model.Model) -> Unit = { _, _ -> },
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

    // SCROLL FIX: Generation start pe latest pe scroll karo.
    // STOP FIX: Stop hone pe current scroll position freeze karo  —  scroll UP nahi hona chahiye.
    // Streaming bubble hatne se layout reflow hoti hai jo scroll ko shift karti thi.
    // Fix: stop hone pe scrollToItem(0) call karo taaki bottom pe fixed rahe.
    androidx.compose.runtime.LaunchedEffect(isGenerating) {
        if (isGenerating) {
            // Generation shuru hui  —  latest (bottom) pe jaao
            if (totalItems > 0) listState.scrollToItem(0)
        } else {
            // Stop hua  —  bottom pe freeze karo, scroll UP nahi hoga
            // Small delay: layout reflow complete hone do pehle
            kotlinx.coroutines.delay(80)
            if (totalItems > 0) listState.scrollToItem(0)
        }
    }

    // Message add hone par scroll karo
    androidx.compose.runtime.LaunchedEffect(messages.size) {
        if (isGenerating && atLatest && totalItems > 0) {
            listState.scrollToItem(0)
        }
    }

    // Scroll to show ThinkingBubble when reasoning starts
    val hasReasoning = !streamingReasoning.isNullOrBlank()
    androidx.compose.runtime.LaunchedEffect(hasReasoning) {
        if (isGenerating && hasReasoning && totalItems > 0) {
            kotlinx.coroutines.delay(50)
            listState.scrollToItem(0)
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

            // At date boundary or end of list  —  insert a date header
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
            // SIRF tab dikhao jab streaming response nahi aaya AUR isThinking=false
            // (agar isThinking=true toh ThinkingBubble ChatScreen mein show ho raha hai)
            if (isGenerating && streamingResponse.isNullOrBlank() && !isThinking) {
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

            // -- STREAMING REASONING (ThinkingBubble) ---------------------
            // reverseLayout=true → index 0 = bottom = right after user's question
            // This positions ThinkingBubble exactly where it should be
            if (isGenerating && !streamingResponse.isNullOrBlank()) {
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
                            color = NeonTextSecondary,
                            modifier = Modifier.padding(bottom = 3.dp, start = 6.dp)
                        )
                        val safeResponse = cleanStreamingText(streamingResponse)
                        val cursor = if (cursorAlpha > 0.5f) "\u258C" else ""
                        // FONT FIX: MarkdownText use karo har case mein  —  streaming aur final
                        // dono exactly same font/lineHeight/style dikhayenge.
                        // Plain Text() alag lineHeight deta tha jisse streaming-to-final jump visible tha.
                        MarkdownText(
                            markdown = safeResponse + cursor,
                            color = NeonText,
                            fontSize = 15.5f,
                            isStreaming = true,
                            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 10.dp)
                        )
                    }
                }
            }

            val hasReasoningToShow = showReasoning && isGenerating && !streamingReasoning.isNullOrBlank()
            if (hasReasoningToShow) {
                item(key = "streaming_reasoning") {
                    ThinkingBubble(
                        reasoning = streamingReasoning.orEmpty(),
                        isStreaming = isThinking,
                        modifier = Modifier.padding(
                            horizontal = dimens.paddingScreenHorizontal,
                            vertical = 4.dp
                        )
                    )
                }
            }

            // -- STREAMING ANSWER -------------------------------------
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
                                .animateItem()
                                .fillMaxWidth()
                                .padding(horizontal = dimens.paddingScreenHorizontal),
                            showReasoning = showReasoning,
                            availableModels = availableModels,
                            onRegenerate = { onRegenerate(item.message.id) },
                            onRegenerateWithModel = { model -> onRegenerateWithModel(item.message.id, model) },
                            onEdit = { onEdit(it) },
                            onDelete = { onDelete(item.message.id) },
                            onShare = { onShare(item.message) },
                            onCopy = { onCopy(it) },
                            onSpeak = { onSpeakClick(item.message) },
                            isSpeaking = currentlySpeakingMessageId == item.message.id,
                            isStreaming = false
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
            Surface(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                shape = CircleShape,
                color = NeonElevated,
                border = BorderStroke(1.dp, NeonBorder),
                shadowElevation = 3.dp,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Jump to latest",
                        tint = NeonText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Streaming text cleaner  —  removes all template/stop token artifacts:
 * - <think>, </think>, <thinking>, </thinking> etc.
 * - < | token | > style: <|im_end|>, < | end_of_sentence | >, <|eot_id|> etc.
 * - Standalone dangling < at word boundaries (not valid HTML/markdown)
 * - Pipe-only artifacts: | end_of_sentence |
 */
// Pre-compiled regexes  —  compiled ONCE, reused on every token/recompose
private val REGEX_THINK_BLOCK = Regex("(?is)<think>[\\s\\S]*?</think>")
private val REGEX_THINKING_BLOCK = Regex("(?is)<thinking>[\\s\\S]*?</thinking>")
private val REGEX_THOUGHT_BLOCK = Regex("(?is)<thought>[\\s\\S]*?</thought>")
private val REGEX_THINK_TAGS = Regex("</?(think|thinking|thought|reasoning)>", RegexOption.IGNORE_CASE)
private val REGEX_PIPE_SPACED = Regex("<\\s*\\|[^>]{0,80}\\|\\s*>")
private val REGEX_PIPE_COMPACT = Regex("<\\|[^|>]{0,60}\\|>")
private val REGEX_PIPE_STANDALONE = Regex("\\|\\s*[a-zA-Z0-9_ ]{1,40}\\s*\\|")
private val REGEX_TEMPLATE_TAGS = Regex("</?[a-z0-9]+_[a-z0-9_\\s|]{0,40}>", RegexOption.IGNORE_CASE)
private val REGEX_DANGLING_LT = Regex("<[^>\\n]{0,10}$")
private val REGEX_LONE_LT = Regex("(?<![\\w\\s])<(?![\\w/])")
private val REGEX_TRAILING_GT = Regex("(?<![\\w])\\s*>\\s*$")

/**
 * Strips ALL model template/stop-token artifacts from text.
 * Uses pre-compiled regexes for performance  —  safe to call on every token.
 */
fun cleanModelText(text: String): String {
    if (text.isBlank()) return text
    return text
        .replace(REGEX_THINK_BLOCK, "")
        .replace(REGEX_THINKING_BLOCK, "")
        .replace(REGEX_THOUGHT_BLOCK, "")
        .replace(REGEX_THINK_TAGS, "")
        .replace(REGEX_PIPE_SPACED, "")
        .replace(REGEX_PIPE_COMPACT, "")
        .replace(REGEX_PIPE_STANDALONE, "")
        .replace(REGEX_TEMPLATE_TAGS, "")
        .replace(REGEX_DANGLING_LT, "")
        .replace(REGEX_LONE_LT, "")
        .replace(REGEX_TRAILING_GT, "")
        .trim()
}

// Alias for streaming  —  same function
fun cleanStreamingText(text: String): String = cleanModelText(text)