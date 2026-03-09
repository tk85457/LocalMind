package com.localmind.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.content.Intent
import android.widget.Toast
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.localmind.app.ui.viewmodel.ChatViewModel
import com.localmind.app.ui.viewmodel.ChatState
import com.localmind.app.domain.model.Message
import com.localmind.app.domain.model.MessageRole
import com.localmind.app.domain.model.Persona
import com.localmind.app.ui.components.ModelEmptyState
import com.localmind.app.ui.components.MarkdownText
import com.localmind.app.ui.components.MessageBubble
import com.localmind.app.ui.components.ChatInputBar
import com.localmind.app.ui.components.ChatMessageList
import com.localmind.app.ui.theme.NeonElevated
import com.localmind.app.ui.theme.NeonError
import com.localmind.app.ui.theme.NeonPrimary
import com.localmind.app.ui.theme.NeonPrimaryVariant
import com.localmind.app.ui.theme.NeonSurface
import com.localmind.app.ui.theme.NeonText
import com.localmind.app.ui.theme.NeonTextExtraMuted
import com.localmind.app.ui.theme.NeonTextSecondary
import com.localmind.app.ui.viewmodel.Attachment
import com.localmind.app.data.local.entity.PromptTemplateEntity
import com.localmind.app.ui.components.ThinkingBubble
import com.localmind.app.ui.components.ThinkingIndicator
import com.localmind.app.core.utils.STTHelper
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.localmind.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun ChatScreen(
    conversationId: String?,
    onNavigateBack: () -> Unit,

    onNavigateToModels: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onNavigateToPersonaManagement: () -> Unit = {},
    onNavigateToPromptTemplates: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    // STREAMING FIX: state aur streamingText alag collect karo.
    // state: messages, isGenerating, etc. — infrequent changes
    // streamingText: har 32ms badalta hai — sirf streaming bubble recompose hoga
    val state by viewModel.state.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoningText by viewModel.streamingReasoningText.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var inputText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dimens = com.localmind.app.ui.theme.LocalDimens.current

    // Consolidated single attachment launcher – accepts images AND PDFs in one picker
    val attachFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachFile(it, context) }
    }

    // STT Helper
    var stableInputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val sttHelper = remember {
        STTHelper(
            context = context,
            onResults = { result ->
                inputText = (if (stableInputText.isBlank()) "" else "$stableInputText ") + result
                isListening = false
            },
            onPartialResults = { partial ->
                if (partial.isNotBlank()) {
                    inputText = (if (stableInputText.isBlank()) "" else "$stableInputText ") + partial
                }
            },
            onError = { error ->
                isListening = false
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            },
            onReady = { isListening = true },
            onEndOfSpeech = { isListening = false }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            sttHelper.destroy()
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            stableInputText = inputText
            isListening = true
            sttHelper.startListening()
        } else {
            Toast.makeText(context, "Microphone permission required for Speech-to-Text", Toast.LENGTH_SHORT).show()
        }
    }




    // TTS Setup
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var showVoiceSelector by remember { mutableStateOf(false) }
    var currentlySpeakingMessageId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized
            }
        }
        ttsInstance.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                currentlySpeakingMessageId = utteranceId
            }
            override fun onDone(utteranceId: String?) {
                if (currentlySpeakingMessageId == utteranceId) {
                    currentlySpeakingMessageId = null
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (currentlySpeakingMessageId == utteranceId) {
                    currentlySpeakingMessageId = null
                }
            }
        })
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    // Screen on rakhne ke liye FLAG_KEEP_SCREEN_ON use karo — PARTIAL_WAKE_LOCK sirf CPU on rakhta hai.
    // Window flag approach: Compose mein sabse reliable tarika.
    val activity = context as? android.app.Activity
    DisposableEffect(state.isGenerating) {
        if (state.isGenerating) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.bootstrapConversation(conversationId)
    }

    // PocketPal-style: App open hote hi active model auto-load karo
    // Ye sirf ek baar chalega jab model null ho ya loaded na ho
    LaunchedEffect(state.activeModel) {
        if (state.activeModel != null && !state.isLoadingModel && !state.isGenerating) {
            viewModel.ensureModelLoaded()
        }
    }

    // POCKETPAL FIX: Purana 80ms polling loop hataya.
    // Auto-scroll ab ChatMessageList ke andar handle hota hai — PocketPal style.
    // reverseLayout=true + derivedStateOf(atLatest) + LaunchedEffect(streamLen) = smooth auto-scroll.
    // Yahan sirf keyboard open hone par scroll karo (IME aane par newest content dikhao).
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    androidx.compose.runtime.LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && listState.firstVisibleItemIndex == 0) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = NeonTextSecondary
                        )
                    }
                },
                title = {
                    val persona = state.selectedPersona ?: com.localmind.app.domain.model.Persona.DEFAULT_ASSISTANT
                    // PocketPal-style: title tap karne pe bottom sheet khulta hai
                    // jisme Pals (personas) aur Models dono tabs hain
                    Row(
                        modifier = Modifier
                            .clickable { showBottomSheet = true }
                            .padding(vertical = 4.dp, horizontal = dimens.spacingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NeonPrimary.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(persona.icon, fontSize = 18.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(dimens.spacingSmall))

                        Column {
                            Text(
                                text = state.currentConversation?.title ?: "New Chat",
                                style = MaterialTheme.typography.titleSmall,
                                color = NeonText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            // Active model name as subtitle
                            state.activeModel?.let { model ->
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonPrimary.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = NeonPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showVoiceSelector = true }) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Change Voice",
                            tint = NeonTextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showVoiceSelector,
                        onDismissRequest = { showVoiceSelector = false },
                        modifier = Modifier.background(NeonSurface)
                    ) {
                        val availableVoices = tts?.voices?.filter { it.locale.language == Locale.getDefault().language }?.sortedBy { it.name } ?: emptyList()
                        if (availableVoices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No voices available", color = NeonTextSecondary) },
                                onClick = { showVoiceSelector = false }
                            )
                        } else {
                            availableVoices.forEachIndexed { index, voice ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Voice ${index + 1}",
                                            color = if (tts?.voice?.name == voice.name) NeonPrimary else NeonText
                                        )
                                    },
                                    onClick = {
                                        tts?.voice = voice
                                        showVoiceSelector = false
                                        Toast.makeText(context, "Voice changed to ${voice.name}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = NeonTextSecondary
                        )
                    }



                    IconButton(onClick = { viewModel.createNewConversation() }) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircleOutline,
                            contentDescription = "New Chat",
                            tint = NeonPrimary
                        )
                    }

                    var showExportDialog by remember { mutableStateOf(false) }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Chat",
                            tint = NeonTextSecondary
                        )
                    }

                    if (showExportDialog) {
                        ExportFormatDialog(
                            onDismiss = { showExportDialog = false },
                            onFormatSelected = { format ->
                                showExportDialog = false
                                val exportText = if (format == "json") viewModel.exportChatAsJson() else viewModel.exportChatAsText(context)
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, exportText)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Chat Export")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Chat"))
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonSurface),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues).imePadding()
        ) {
            if (state.activeModel == null && !state.isLoading) {
                ModelEmptyState(
                    onNavigateToLibrary = onNavigateToModels,
                    modifier = Modifier.weight(1f)
                )
            } else {


                // Message Search Bar (#7)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showSearch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.paddingScreenHorizontal, vertical = 4.dp),
                        placeholder = { Text("Search messages...", color = NeonTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = NeonPrimary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = NeonTextSecondary)
                                }
                            }
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
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }

                // Filtered messages based on search query
                val displayMessages = if (searchQuery.isBlank()) state.messages
                    else state.messages.filter { it.content.contains(searchQuery, ignoreCase = true) }

                // Reset list position when search query changes
                LaunchedEffect(searchQuery) {
                    if (listState.firstVisibleItemIndex != 0) {
                        listState.scrollToItem(0)
                    }
                }

                // Error banner
                if (state.error != null) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimens.paddingScreenHorizontal, vertical = 4.dp),
                            color = NeonError.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonError.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, null, tint = NeonError, modifier = Modifier.size(14.dp))
                                Text(
                                    text = state.error!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonError,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2
                                )
                                IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Default.Close, null, tint = NeonError, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Performance warning banner — 3 seconds mein auto-dismiss hota hai
                var showPerfWarning by remember { mutableStateOf(true) }
                LaunchedEffect(state.performanceWarning) {
                    showPerfWarning = true
                    if (state.performanceWarning != null) {
                        kotlinx.coroutines.delay(3000)
                        showPerfWarning = false
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.performanceWarning != null && showPerfWarning,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.paddingScreenHorizontal, vertical = 2.dp),
                        color = NeonPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPrimary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = NeonPrimary, modifier = Modifier.size(12.dp))
                            Text(
                                text = state.performanceWarning ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonPrimary.copy(alpha = 0.8f),
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showPerfWarning = false },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = NeonPrimary.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                // Loading indicator when model loading and no messages
                if (displayMessages.isEmpty() && state.isLoadingModel) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NeonPrimary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Loading model...", color = NeonTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (displayMessages.isEmpty() && !state.isGenerating && !state.isLoadingModel) {
                    EmptyChatWelcome(
                        modifier = Modifier.weight(1f),
                        personaName = state.selectedPersona?.name ?: "Assistant",
                        personaIcon = state.selectedPersona?.icon ?: "🤖",
                        onSuggestionClick = { suggestion ->
                            inputText = suggestion
                        }
                    )
                } else {
                ChatMessageList(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                    listState = listState,
                    messages = displayMessages,
                    isGenerating = state.isGenerating,
                    // STREAMING FIX: alag StateFlow se liya — sirf streaming bubble recompose hoga
                    streamingResponse = streamingText,
                    streamingReasoning = streamingReasoningText,
                    isAnalyzingDocument = state.isAnalyzingDocument,
                    isAnalyzingMedia = state.isAnalyzingMedia,
                    currentlySpeakingMessageId = currentlySpeakingMessageId,
                    availableModels = state.downloadedModels,
                    streamingModelLabel = state.selectedPersona?.name ?: state.activeModel?.name ?: "AI",
                    onRegenerateWithModel = { msgId, model ->
                        viewModel.regenerateWithModel(msgId, model, context)
                    },
                    onSpeakClick = { message ->
                        if (currentlySpeakingMessageId == message.id) {
                            tts?.stop()
                            currentlySpeakingMessageId = null
                        } else {
                            currentlySpeakingMessageId = message.id
                            tts?.speak(message.content, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, message.id)
                        }
                    },
                    onRegenerate = { viewModel.regenerateResponse(it, context) },
                    onEdit = { messageId ->
                        // PocketPal-style edit: message ID se content lo, pending edit set karo
                        val content = viewModel.startEditMessage(messageId)
                        if (content != null) inputText = content
                    },
                    onDelete = { viewModel.deleteMessage(it) },
                    onShare = { message ->
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, message.content)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Message"))
                    },
                    onCopy = { content ->
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        val clipData = android.content.ClipData.newPlainText("Message", content)
                        clipboardManager.setPrimaryClip(clipData)
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
                } // end else (messages not empty)
                // showScrollToBottom is now computed inside ChatMessageList

                // ── Attachment chips row ──────────────────────────────────────
                if (state.attachments.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.attachments) { attachment ->
                            AttachmentChip(
                                attachment = attachment,
                                onRemove = { viewModel.removeAttachment(attachment) }
                            )
                        }
                    }
                }



                // Performance metrics pill — shows tps after generation completes
                val lastPerf = state.lastPerfMetrics
                val streamTelemetry = state.streamingTelemetry
                val showPerfPill = !state.isGenerating && (lastPerf != null || streamTelemetry != null)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showPerfPill,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val tps = streamTelemetry?.tokensPerSecond?.toDouble() ?: lastPerf?.tokensPerSecond
                    val ttft = streamTelemetry?.ttftMs ?: 0L
                    if (tps != null && tps > 0.0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = NeonElevated.copy(alpha = 0.85f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        null,
                                        tint = NeonPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        buildString {
                                            append(String.format("%.1f t/s", tps))
                                            if (ttft > 0) append(" · TTFT ${ttft}ms")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Prompt Library quick-access button
                var showPromptLibrary by remember { mutableStateOf(false) }
                if (state.promptTemplates.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showPromptLibrary = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                tint = NeonPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Prompts", style = MaterialTheme.typography.labelSmall, color = NeonPrimary)
                        }
                    }
                }

                if (showPromptLibrary) {
                    PromptLibraryBottomSheet(
                        prompts = state.promptTemplates,
                        onDismiss = { showPromptLibrary = false },
                        onSelect = { template ->
                            inputText = template.content
                            showPromptLibrary = false
                        },
                        onManage = {
                            showPromptLibrary = false
                            onNavigateToPromptTemplates()
                        }
                    )
                }

                // Context window usage indicator
                // Real context size use karo — avgTokensPerMsg ~200 assume (realistic for chat)
                val msgCount = state.messages.size
                val contextTokens = state.activeModel?.contextLength?.takeIf { it > 0 } ?: 4096
                val avgTokensPerMsg = 200
                val maxMsgs = (contextTokens * 0.5f / avgTokensPerMsg).toInt().coerceAtLeast(10)
                if (msgCount > 5 && state.activeModel != null) {
                    val fillFraction = (msgCount.toFloat() / maxMsgs).coerceIn(0f, 1f)
                    val fillColor = when {
                        fillFraction > 0.85f -> NeonError
                        fillFraction > 0.65f -> NeonPrimary
                        else -> NeonTextExtraMuted
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { fillFraction },
                            modifier = Modifier.weight(1f).height(2.dp),
                            color = fillColor,
                            trackColor = NeonTextExtraMuted.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "ctx",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = NeonTextExtraMuted.copy(alpha = 0.5f)
                        )
                    }
                }

                ChatInputBar(
                    inputText = inputText,
                    onInputTextChanged = { inputText = it },
                    isEditMode = state.pendingEditMessageId != null,
                    editingPreview = state.pendingEditMessageId?.let { id ->
                        state.messages.find { it.id == id }?.content
                    },
                    onCancelEdit = {
                        viewModel.cancelEdit()
                        inputText = ""
                    },
                    isListening = isListening,
                    onMicClick = {
                        if (isListening) {
                            sttHelper.stopListening()
                            isListening = false
                        } else {
                            if (!sttHelper.isRecognitionAvailable()) {
                                Toast.makeText(context, "Speech recognition service unavailable on this phone.", Toast.LENGTH_SHORT).show()
                                return@ChatInputBar
                            }
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                stableInputText = inputText
                                isListening = true
                                sttHelper.startListening()
                            } else {
                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    onAttachClick = {
                        attachFileLauncher.launch(arrayOf("image/*", "application/pdf", "text/plain"))
                    },
                    onSendClick = {
                        val canSend = inputText.isNotBlank() || state.attachments.isNotEmpty()
                        if (canSend && !state.isGenerating) {
                            viewModel.sendMessage(inputText.trim(), context)
                            inputText = ""
                        } else if (state.isGenerating) {
                            viewModel.stopGeneration()
                        }
                    },
                    isGenerating = state.isGenerating,
                    isLoadingModel = state.isLoadingModel,
                    canSend = inputText.isNotBlank() || state.attachments.isNotEmpty(),
                    activeModel = state.activeModel,
                    onModelClick = { showBottomSheet = true }
                )
            }
        }
    }

    if (showBottomSheet) {
        ModelPersonaBottomSheet(
            state = state,
            onDismiss = { showBottomSheet = false },
            onSelectModel = { viewModel.switchModel(it.id) },
            onSelectPersona = { viewModel.selectPersona(it) },
            onNavigateToModels = onNavigateToModels,
            onNavigateToPersonas = {
                showBottomSheet = false
                onNavigateToPersonaManagement()
            },
            sheetState = sheetState
        )
    }
}

@Composable
fun AttachmentChip(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    val dimens = com.localmind.app.ui.theme.LocalDimens.current
    Surface(
        color = NeonElevated,
        shape = RoundedCornerShape(dimens.cornerRadiusMedium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingSmall, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = NeonPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelMedium,
                color = NeonText,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = NeonTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}



@Composable
fun ThreeDotsTypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 7.dp,
    gap: Dp = 5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "typingPhase"
    )

    fun dotAlpha(index: Int): Float {
        val shifted = (phase + index * 0.22f) % 1f
        return 0.25f + (1f - kotlin.math.abs(shifted - 0.5f) * 2f) * 0.75f
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(NeonTextSecondary.copy(alpha = dotAlpha(index)))
            )
        }
    }
}

@Composable
fun GenerationStatusIndicator(
    isAnalyzingMedia: Boolean,
    modifier: Modifier = Modifier
) {
    val statusBase = if (isAnalyzingMedia) "Analyzing image" else "Thinking"
    val infiniteTransition = rememberInfiniteTransition(label = "statusAnimation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "statusPhase"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulseAlpha"
    )
    val spinnerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "analyzeSpinnerRotation"
    )
    val dotCount = when {
        phase < 0.25f -> 1
        phase < 0.5f -> 2
        phase < 0.75f -> 3
        else -> 2
    }

    Surface(
        modifier = modifier,
        color = NeonElevated.copy(alpha = 0.85f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isAnalyzingMedia) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(spinnerRotation),
                    color = NeonPrimary.copy(alpha = pulseAlpha),
                    strokeWidth = 2.dp
                )
            } else {
                ChatGptTypingDots(phase = phase)
            }
            Text(
                text = statusBase + ".".repeat(dotCount),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = NeonTextSecondary.copy(alpha = pulseAlpha)
            )
        }
    }
}

@Composable
private fun ChatGptTypingDots(
    phase: Float,
    modifier: Modifier = Modifier,
    dotSize: Dp = 5.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val shifted = (phase + index * 0.18f) % 1f
            val wave = (1f - abs(shifted - 0.5f) * 2f).coerceIn(0f, 1f)
            val alpha = 0.25f + (wave * 0.75f)
            val scale = 0.8f + (wave * 0.35f)
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(CircleShape)
                    .background(NeonTextSecondary.copy(alpha = alpha))
            )
        }
    }
}



@Composable
fun AttachmentPreview(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NeonElevated)
    ) {
        AsyncImage(
            model = attachment.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Surface(
            onClick = onRemove,
            color = Color.Black.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ModelPersonaBottomSheet(
    state: ChatState,
    onDismiss: () -> Unit,
    onSelectModel: (com.localmind.app.domain.model.Model) -> Unit,
    onSelectPersona: (com.localmind.app.domain.model.Persona) -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToPersonas: () -> Unit = {},
    sheetState: androidx.compose.material3.SheetState
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val sheetContext = LocalContext.current
    var lastSelectedPersona by remember { mutableStateOf(state.selectedPersona) }

    // Persona switch toast
    LaunchedEffect(state.selectedPersona) {
        val newPersona = state.selectedPersona
        if (newPersona != null && newPersona.id != lastSelectedPersona?.id) {
            android.widget.Toast.makeText(
                sheetContext,
                "Switched to ${newPersona.name} 😄",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        lastSelectedPersona = newPersona
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeonSurface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                color = NeonTextSecondary.copy(alpha = 0.4f),
                shape = CircleShape
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = NeonPrimary,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = NeonPrimary
                    )
                }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Pals", fontWeight = FontWeight.Bold) },
                    selectedContentColor = NeonPrimary,
                    unselectedContentColor = NeonTextSecondary
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Models", fontWeight = FontWeight.Bold) },
                    selectedContentColor = NeonPrimary,
                    unselectedContentColor = NeonTextSecondary
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) { page ->
                when (page) {
                    0 -> PersonaPage(
                        personas = state.availablePersonas,
                        selectedPersona = state.selectedPersona,
                        onSelect = { persona ->
                            onSelectPersona(persona)
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        },
                        onManage = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onNavigateToPersonas()
                            }
                        }
                    )
                    1 -> ModelPage(
                        models = state.downloadedModels,
                        activeModel = state.activeModel,
                        onSelect = {
                            onSelectModel(it)
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        },
                        onManage = onNavigateToModels
                    )
                }
            }
        }
    }
}

@Composable
fun PersonaPage(
    personas: List<com.localmind.app.domain.model.Persona>,
    selectedPersona: com.localmind.app.domain.model.Persona?,
    onSelect: (com.localmind.app.domain.model.Persona) -> Unit,
    onManage: () -> Unit
) {
    Column {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(personas, key = { it.id }) { p ->
                PersonaCard(
                    persona = p,
                    isSelected = selectedPersona?.id == p.id,
                    onSelect = { onSelect(p) }
                )
            }
        }
        TextButton(
            onClick = onManage,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp), tint = NeonPrimary)
            Spacer(Modifier.width(6.dp))
            Text("Manage Pals", color = NeonPrimary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PersonaCard(
    persona: com.localmind.app.domain.model.Persona,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = if (isSelected) NeonPrimary.copy(alpha = 0.15f) else NeonElevated,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, NeonPrimary) else null,
        modifier = Modifier.size(100.dp, 120.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(persona.icon, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                persona.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) NeonPrimary else NeonText,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun ModelPage(
    models: List<com.localmind.app.domain.model.Model>,
    activeModel: com.localmind.app.domain.model.Model?,
    onSelect: (com.localmind.app.domain.model.Model) -> Unit,
    onManage: () -> Unit
) {
    Column {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(models, key = { it.id }) { model ->
                ModelSwitcherCard(
                    model = model,
                    isActive = activeModel?.id == model.id,
                    onSelect = { onSelect(model) }
                )
            }
        }

        TextButton(
            onClick = onManage,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
        ) {
            Icon(Icons.Default.Architecture, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Model Library", color = NeonPrimary)
        }
    }
}

@Composable
fun ModelSwitcherCard(
    model: com.localmind.app.domain.model.Model,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = if (isActive) NeonPrimary.copy(alpha = 0.15f) else NeonElevated,
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, NeonPrimary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NeonPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = NeonPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) NeonPrimary else NeonText
                )
                Text(
                    "${model.quantization} • ${model.parameterCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonTextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model.supportsVision) {
                    Icon(Icons.Default.Visibility, null, tint = NeonPrimary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                }
                if (model.supportsDocument) {
                    Icon(Icons.Default.HistoryEdu, null, tint = NeonPrimary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryBottomSheet(
    prompts: List<com.localmind.app.data.local.entity.PromptTemplateEntity>,
    onDismiss: () -> Unit,
    onSelect: (com.localmind.app.data.local.entity.PromptTemplateEntity) -> Unit,
    onManage: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NeonSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Prompt Library",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonPrimary,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onManage) {
                    Text("Manage", color = NeonPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (prompts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = NeonTextExtraMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No prompt templates yet",
                            color = NeonTextExtraMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(prompts) { prompt ->
                        Surface(
                            onClick = { onSelect(prompt) },
                            color = NeonElevated,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    prompt.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    prompt.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonTextSecondary,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun EmptyChatWelcome(
    modifier: Modifier = Modifier,
    personaName: String,
    personaIcon: String,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = listOf(
        "Explain quantum computing simply",
        "Write a Python script to sort a list",
        "What are the best productivity tips?",
        "Help me debug my code"
    )
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Persona icon: custom persona ke liye emoji, default ke liye animated brain
            if (personaIcon != "🤖" && personaIcon.isNotBlank()) {
                Text(
                    text = personaIcon,
                    fontSize = 72.sp
                )
            } else {
                BrainLogoIcon(size = 96.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Hi, I'm $personaName",
                style = MaterialTheme.typography.headlineSmall,
                color = NeonText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Private · On-Device · Always Ready",
                style = MaterialTheme.typography.bodySmall,
                color = NeonPrimary.copy(alpha = 0.7f),
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            suggestions.forEach { suggestion ->
                Surface(
                    onClick = { onSuggestionClick(suggestion) },
                    color = NeonElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = suggestion,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onFormatSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Chat", color = Color.White) },
        text = { Text("Choose export format:", color = NeonTextSecondary) },
        containerColor = NeonSurface,
        confirmButton = {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onFormatSelected("markdown") },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Markdown (.md)", color = Color.Black)
                }
                Button(
                    onClick = { onFormatSelected("json") },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("JSON (.json)", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NeonPrimary)
            }
        }
    )
}

// ===== Animated Brain Logo Icon for Empty Chat Screen =====
@Composable
fun BrainLogoIcon(size: Dp = 88.dp) {
    val inf = rememberInfiniteTransition(label = "brain")
    val ringRot by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "ringRot"
    )
    val glow by inf.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val nodeBlink by inf.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "node"
    )
    val primary = NeonPrimary
    val primaryVar = NeonPrimaryVariant

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(size).graphicsLayer { rotationZ = ringRot }
        ) {
            val r = this.size.minDimension / 2f - 3.dp.toPx()
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val circumference = 2f * Math.PI.toFloat() * r
            val dashLen = 14f
            val gapLen = 8f
            val count = (circumference / (dashLen + gapLen)).toInt()
            repeat(count) { i ->
                val startAngle = i * (360f / count)
                drawArc(
                    color = primary.copy(alpha = glow * 0.6f),
                    startAngle = startAngle,
                    sweepAngle = dashLen / circumference * 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                )
            }
        }
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val br = w * 0.42f
            drawCircle(
                color = primary.copy(alpha = glow * 0.12f),
                radius = br * 1.15f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
            drawOval(
                color = primaryVar.copy(alpha = 0.85f),
                topLeft = androidx.compose.ui.geometry.Offset(cx - br, cy - br * 0.85f),
                size = androidx.compose.ui.geometry.Size(br * 1.05f, br * 1.7f)
            )
            drawOval(
                color = primary.copy(alpha = 0.75f),
                topLeft = androidx.compose.ui.geometry.Offset(cx - 0.05f * br, cy - br * 0.85f),
                size = androidx.compose.ui.geometry.Size(br * 1.05f, br * 1.7f)
            )
            drawLine(
                color = primary.copy(alpha = 0.4f),
                start = androidx.compose.ui.geometry.Offset(cx, cy - br * 0.7f),
                end = androidx.compose.ui.geometry.Offset(cx, cy + br * 0.7f),
                strokeWidth = 1.5.dp.toPx()
            )
            val nodes = listOf(
                androidx.compose.ui.geometry.Offset(cx - br * 0.55f, cy - br * 0.35f),
                androidx.compose.ui.geometry.Offset(cx - br * 0.2f, cy - br * 0.6f),
                androidx.compose.ui.geometry.Offset(cx + br * 0.2f, cy - br * 0.5f),
                androidx.compose.ui.geometry.Offset(cx + br * 0.5f, cy - br * 0.2f),
                androidx.compose.ui.geometry.Offset(cx + br * 0.4f, cy + br * 0.3f),
                androidx.compose.ui.geometry.Offset(cx - br * 0.1f, cy + br * 0.5f),
                androidx.compose.ui.geometry.Offset(cx - br * 0.5f, cy + br * 0.25f),
                androidx.compose.ui.geometry.Offset(cx, cy)
            )
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 0, 1 to 7, 3 to 7, 5 to 7).forEach { (a, b) ->
                drawLine(
                    color = primary.copy(alpha = 0.45f * glow),
                    start = nodes[a], end = nodes[b],
                    strokeWidth = 1.dp.toPx()
                )
            }
            nodes.forEachIndexed { i, pos ->
                val alpha = if (i % 2 == 0) nodeBlink else (1f - nodeBlink + 0.4f)
                drawCircle(color = primary.copy(alpha = alpha), radius = if (i == 7) 4.dp.toPx() else 2.5.dp.toPx(), center = pos)
                drawCircle(color = Color.White.copy(alpha = alpha * 0.6f), radius = 1.dp.toPx(), center = pos)
            }
        }
    }
}
