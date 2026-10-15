package com.tk854.localmind.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tk854.localmind.ui.viewmodel.ChatViewModel
import com.tk854.localmind.ui.viewmodel.ChatState
import com.tk854.localmind.domain.model.Message
import com.tk854.localmind.domain.model.MessageRole
import com.tk854.localmind.ui.components.ModelEmptyState
import com.tk854.localmind.ui.components.MarkdownText
import com.tk854.localmind.ui.components.MessageBubble
import com.tk854.localmind.ui.components.ChatInputBar
import com.tk854.localmind.ui.components.ChatMessageList
import com.tk854.localmind.ui.theme.NeonBorder
import com.tk854.localmind.ui.theme.NeonElevated
import com.tk854.localmind.ui.theme.NeonError
import com.tk854.localmind.ui.theme.NeonPrimary
import com.tk854.localmind.ui.theme.NeonPrimaryVariant
import com.tk854.localmind.ui.theme.NeonSurface
import com.tk854.localmind.ui.theme.NeonText
import com.tk854.localmind.ui.theme.NeonTextExtraMuted
import com.tk854.localmind.ui.theme.NeonTextSecondary
import com.tk854.localmind.ui.viewmodel.Attachment
import com.tk854.localmind.ui.viewmodel.SettingsViewModel
import com.tk854.localmind.data.local.entity.PromptTemplateEntity
import com.tk854.localmind.ui.components.ThinkingBubble
import com.tk854.localmind.ui.components.ThinkingIndicator
import com.tk854.localmind.core.utils.STTHelper
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.tk854.localmind.R
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
    onNavigateToPromptTemplates: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    // STREAMING FIX: state aur streamingText alag collect karo.
    // state: messages, isGenerating, etc.  —  infrequent changes
    // streamingText: har 32ms badalta hai  —  sirf streaming bubble recompose hoga
    val state by viewModel.state.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoningText by viewModel.streamingReasoningText.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    // AUDIT FIX #9: Lifecycle-safe coroutine scope for clipboard auto-clear
    val clipboardClearScope = androidx.compose.runtime.rememberCoroutineScope()
    // TextFieldValue: cursor position + scroll position dono control hoti hai
    // String se TextFieldValue: STT text aate hi cursor end mein ja sakta hai
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dimens = com.tk854.localmind.ui.theme.LocalDimens.current

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
                val newText = (if (stableInputText.isBlank()) "" else "$stableInputText ") + result
                // Cursor end mein set karo  —  user ko dikhe ki text aa gaya
                inputText = TextFieldValue(text = newText, selection = TextRange(newText.length))
                isListening = false
            },
            onPartialResults = { partial ->
                if (partial.isNotBlank()) {
                    val newText = (if (stableInputText.isBlank()) "" else "$stableInputText ") + partial
                    // Partial results bhi cursor end mein  —  real-time scroll
                    inputText = TextFieldValue(text = newText, selection = TextRange(newText.length))
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
            stableInputText = inputText.text
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

    // Screen on rakhne ke liye FLAG_KEEP_SCREEN_ON use karo  —  PARTIAL_WAKE_LOCK sirf CPU on rakhta hai.
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

    // STARTUP SAFETY NET: Sirf app open hone par ya model unloaded hone par auto-load karo.
    //
    // BUG FIX: Pehle LaunchedEffect(state.activeModel, state.isLoading) tha.
    // Jab bhi user model switch karta tha:
    //   1. switchModel() -> naya model load karta hai (CORRECT)
    //   2. observeActiveModel() -> state.activeModel update hoti hai
    //   3. LaunchedEffect FIRES AGAIN (activeModel reference changed)
    //   4. isLoadingModel=false, isModelLoaded()=false (tiny race window)
    //   5. ensureModelLoaded() -> PURANA model (DB se stale) wapas load karta tha
    //   Log proof: DeepSeek select -> SmolVLM load!
    //
    // FIX: state.activeModel?.id (by value, not reference) + isLoadingModel as key.
    // switchModel() pehle isLoadingModel=true set karta hai, baad mein false.
    // Jab isLoadingModel false hota hai tab isModelLoaded()=true hota hai -> ensureModelLoaded() return karta hai safely.
    // Startup pe: isLoadingModel=false AND isModelLoaded()=false -> correctly fires.
    // BLINK FIX: Only fire ensureModelLoaded AFTER isLoading=false AND isLoadingModel=false.
    // Both are needed: isLoading covers initial DB load, isLoadingModel covers model activation.
    // We also watch isLoading so that when startup finishes, this auto-fires.
    LaunchedEffect(state.activeModel?.id, state.isLoading, state.isLoadingModel) {
        if (state.activeModel != null
            && !state.isLoading
            && !state.isLoadingModel
            && !state.isGenerating) {
            viewModel.ensureModelLoaded()
        }
    }
    // Auto-scroll ab ChatMessageList ke andar handle hota hai  —  PocketPal style.
    // reverseLayout=true + derivedStateOf(atLatest) + LaunchedEffect(streamLen) = smooth auto-scroll.
    // Yahan sirf keyboard open hone par scroll karo (IME aane par newest content dikhao).
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    androidx.compose.runtime.LaunchedEffect(imeBottom) {
        // STOP FIX: sirf tab scroll karo jab keyboard actually khula ho (imeBottom > 100px)
        // aur generation chal rahi ho. Stop press hone par layout change se imeBottom
        // recompute hota tha aur ye fire ho jaata tha  —  input ke paas jarring scroll.
        if (imeBottom > 100 && state.isGenerating && listState.firstVisibleItemIndex == 0) {
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
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = { showBottomSheet = true },
                            shape = RoundedCornerShape(20.dp),
                            color = NeonElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.activeModel?.name ?: "Local Mind",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = NeonText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Model",
                                    tint = NeonTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // One-tap Dark / Light mode toggle
                    IconButton(onClick = { settingsViewModel.updateDarkMode(!settings.darkMode) }) {
                        Icon(
                            imageVector = if (settings.darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (settings.darkMode) "Light Mode" else "Dark Mode",
                            tint = NeonTextSecondary
                        )
                    }

                    // New Chat button
                    IconButton(onClick = { viewModel.createNewConversation() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = NeonTextSecondary
                        )
                    }

                    // 3-dots overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = NeonTextSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(NeonSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Search in Chat", color = NeonText) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = NeonTextSecondary) },
                                onClick = {
                                    showOverflowMenu = false
                                    showSearch = !showSearch
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Voice Options", color = NeonText) },
                                leadingIcon = { Icon(Icons.Default.RecordVoiceOver, null, tint = NeonTextSecondary) },
                                onClick = {
                                    showOverflowMenu = false
                                    showVoiceSelector = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Chat", color = NeonText) },
                                leadingIcon = { Icon(Icons.Default.Share, null, tint = NeonTextSecondary) },
                                onClick = {
                                    showOverflowMenu = false
                                    showExportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Model Manager", color = NeonText) },
                                leadingIcon = { Icon(Icons.Default.Tune, null, tint = NeonTextSecondary) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToModels()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings", color = NeonText) },
                                leadingIcon = { Icon(Icons.Default.Settings, null, tint = NeonTextSecondary) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToSettings()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonSurface),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            )

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

            if (showVoiceSelector) {
                val availableVoices = tts?.voices?.filter { it.locale.language == Locale.getDefault().language }?.sortedBy { it.name } ?: emptyList()
                AlertDialog(
                    onDismissRequest = { showVoiceSelector = false },
                    title = { Text("Select Voice", color = NeonText) },
                    text = {
                        if (availableVoices.isEmpty()) {
                            Text("No voices available for system language", color = NeonTextSecondary)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                items(availableVoices) { voice ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                tts?.voice = voice
                                                showVoiceSelector = false
                                                Toast.makeText(context, "Voice changed to ${voice.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = voice.name,
                                            color = if (tts?.voice?.name == voice.name) NeonPrimary else NeonText,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showVoiceSelector = false }) {
                            Text("Close", color = NeonPrimary)
                        }
                    },
                    containerColor = NeonSurface
                )
            }
        },
        modifier = Modifier.statusBarsPadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // BUG FIX: isLoadingModel check zaroori hai.
            // Pehle: activeModel==null pe turant EmptyState show hota tha, chahe model
            // background mein load ho raha ho  —  ye tab bhi trigger hota tha.
            // Ab: sirf tab show karo jab na koi model hai, na load ho raha ho, na generating ho.
            // BLINK FIX: Single stable gate.
            // isLoading=true on startup (ChatState default). ONE transition, no flicker.
            val isAppInitializing = state.isLoading || state.isLoadingModel

            if (isAppInitializing) {
                // Stable loading screen  —  no switch between ModelEmptyState/EmptyChatWelcome/spinner
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NeonPrimary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = if (state.isLoadingModel) "Loading model..." else "Starting...",
                            color = NeonTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                ChatInputBar(
                    inputText = inputText,
                    onInputTextChanged = { inputText = it },
                    isListening = false,
                    onMicClick = {},
                    onAttachClick = {},
                    onSendClick = {},
                    isGenerating = false,
                    isLoadingModel = true,
                    canSend = false,
                    activeModel = state.activeModel,
                    onModelClick = {}
                )
            } else if (state.activeModel == null) {
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
                // AUDIT FIX #15: remember memoization  —  large chats mein lag nahi hoga
                val displayMessages = remember(searchQuery, state.messages) {
                    if (searchQuery.isBlank()) state.messages
                    else state.messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
                }

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

                // Performance warning banner  —  3 seconds mein auto-dismiss hota hai
                var showPerfWarning by remember { mutableStateOf(true) }
                LaunchedEffect(state.performanceWarning) {
                    // AUDIT FIX #10: Pehle showPerfWarning=true set karo, PHIR delay
                    // Rapid updates pe previous coroutine cancel hoga (LaunchedEffect behavior)
                    if (state.performanceWarning != null) {
                        showPerfWarning = true
                        kotlinx.coroutines.delay(3000)
                        showPerfWarning = false
                    } else {
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

                // AUDIT FIX #4: streamingText bhi check karo  —  first message bhejte time
                // messages empty + generating true hota hai toh screen blank dikhta tha
                if (displayMessages.isEmpty() && !state.isGenerating && streamingText.isNullOrBlank()) {
                    EmptyChatWelcome(
                        modifier = Modifier.weight(1f),
                        onSuggestionClick = { suggestion ->
                            inputText = TextFieldValue(text = suggestion, selection = TextRange(suggestion.length))
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
                    // STREAMING FIX: alag StateFlow se liya  —  sirf streaming bubble recompose hoga
                    // AUDIT FIX #2 + #3: null guard  —  null aate hi bubble collapse/flicker hota tha
                    streamingResponse = streamingText ?: "",
                    streamingReasoning = streamingReasoningText ?: "",
                    isThinking = state.isThinking,
                    // PART 13: Show reasoning only when isThinkingEnabled setting is on
                    showReasoning = state.isThinkingEnabled,
                    isAnalyzingDocument = state.isAnalyzingDocument,
                    isAnalyzingMedia = state.isAnalyzingMedia,
                    currentlySpeakingMessageId = currentlySpeakingMessageId,
                    availableModels = state.downloadedModels,
                    streamingModelLabel = state.activeModel?.name ?: "AI",
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
                        if (content != null) inputText = TextFieldValue(text = content, selection = TextRange(content.length))
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
                        // SECURITY FIX: Clipboard auto-clear after 2 minutes
                        // Prevents sensitive AI chat content from persisting in clipboard
                        // OWASP MSTG-STORAGE-10
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        val clipData = android.content.ClipData.newPlainText("Message", content)
                        clipboardManager.setPrimaryClip(clipData)
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        // Auto-clear clipboard after 2 minutes
                        // AUDIT FIX #9: rememberCoroutineScope use karo  —  lifecycle-safe
                        // MainScope() se memory leak hota tha (lifecycle ignore)
                        clipboardClearScope.launch {
                            kotlinx.coroutines.delay(120_000L)
                            val current = clipboardManager.primaryClip
                            val currentText = current?.getItemAt(0)?.text?.toString()
                            if (currentText == content) {
                                clipboardManager.setPrimaryClip(
                                    android.content.ClipData.newPlainText("", "")
                                )
                            }
                        }
                    },
                )
                } // end else (messages not empty)
                // showScrollToBottom is now computed inside ChatMessageList




                // Performance metrics pill  —  shows tps after generation completes
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
                                            if (ttft > 0) append(" Â· TTFT ${ttft}ms")
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
                            inputText = TextFieldValue(text = template.content, selection = TextRange(template.content.length))
                            showPromptLibrary = false
                        },
                        onManage = {
                            showPromptLibrary = false
                            onNavigateToPromptTemplates()
                        }
                    )
                }

                // Context window usage indicator
                // Real context size use karo  —  avgTokensPerMsg ~200 assume (realistic for chat)
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
                    attachments = state.attachments,
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    isEditMode = state.pendingEditMessageId != null,
                    editingPreview = state.pendingEditMessageId?.let { id ->
                        state.messages.find { it.id == id }?.content
                    },
                    onCancelEdit = {
                        viewModel.cancelEdit()
                        // AUDIT FIX #6: Proper TextFieldValue reset with cursor position
                        inputText = TextFieldValue(text = "", selection = TextRange(0))
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
                                stableInputText = inputText.text
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
                        val canSend = inputText.text.isNotBlank() || state.attachments.isNotEmpty()
                        if (canSend && !state.isGenerating) {
                            viewModel.sendMessage(inputText.text.trim(), context)
                            // AUDIT FIX #6: Proper TextFieldValue reset with cursor position
                            inputText = TextFieldValue(text = "", selection = TextRange(0))
                        } else if (state.isGenerating) {
                            viewModel.stopGeneration()
                        }
                    },
                    isGenerating = state.isGenerating,
                    isLoadingModel = state.isLoadingModel,
                    canSend = inputText.text.isNotBlank() || state.attachments.isNotEmpty(),
                    activeModel = state.activeModel,
                    onModelClick = { showBottomSheet = true },
                    // PocketPal-style: sirf reasoning model pe Think button dikhao
                    showThinkingToggle = state.showThinkingToggle,
                    isThinkingEnabled = state.isThinkingEnabled,
                    onThinkingToggle = { viewModel.toggleThinking() }
                )
            }
        }
    }

    if (showBottomSheet) {
        ModelBottomSheet(
            models = state.downloadedModels,
            activeModel = state.activeModel,
            isLoadingModel = state.isLoadingModel,
            onDismiss = { showBottomSheet = false },
            onSelectModel = { model ->
                showBottomSheet = false
                viewModel.switchModel(model.id)
            },
            onNavigateToModels = {
                showBottomSheet = false
                onNavigateToModels()
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
    val dimens = com.tk854.localmind.ui.theme.LocalDimens.current
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBottomSheet(
    models: List<com.tk854.localmind.domain.model.Model>,
    activeModel: com.tk854.localmind.domain.model.Model?,
    isLoadingModel: Boolean = false,
    onDismiss: () -> Unit,
    onSelectModel: (com.tk854.localmind.domain.model.Model) -> Unit,
    onNavigateToModels: () -> Unit,
    sheetState: androidx.compose.material3.SheetState
) {
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
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Switch Model",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = NeonText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
            val listHeight = (screenHeight * 0.48f).coerceIn(200.dp, 440.dp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listHeight)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (models.isEmpty()) {
                    item {
                        Text(
                            text = "No downloaded models found.",
                            color = NeonTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                        )
                    }
                } else {
                    items(models, key = { it.id }) { model ->
                        ModelSwitcherCard(
                            model = model,
                            isActive = activeModel?.id == model.id,
                            isLoading = isLoadingModel && activeModel?.id == model.id,
                            onSelect = { onSelectModel(model) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onNavigateToModels,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Architecture, null, modifier = Modifier.size(16.dp), tint = NeonPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Model Library", color = NeonPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ModelSwitcherCard(
    model: com.tk854.localmind.domain.model.Model,
    isActive: Boolean,
    onSelect: () -> Unit,
    isLoading: Boolean = false
) {
    Surface(
        onClick = { if (!isLoading) onSelect() },
        color = if (isActive) NeonPrimary.copy(alpha = 0.13f) else NeonElevated,
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, NeonPrimary)
                 else androidx.compose.foundation.BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model icon: colour reflects capability
            val iconBg = when {
                model.supportsThinking -> NeonPrimary.copy(alpha = if (isActive) 0.25f else 0.1f)
                model.supportsVision           -> androidx.compose.ui.graphics.Color(0xFF00C8FF).copy(alpha = if (isActive) 0.22f else 0.1f)
                else                           -> NeonPrimary.copy(alpha = if (isActive) 0.18f else 0.08f)
            }
            val iconTint = when {
                model.supportsThinking -> NeonPrimary
                model.supportsVision           -> androidx.compose.ui.graphics.Color(0xFF00C8FF)
                else                           -> NeonPrimary.copy(alpha = 0.7f)
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NeonPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = when {
                            model.supportsThinking -> Icons.Default.AutoAwesome
                            model.supportsVision           -> Icons.Default.Visibility
                            else                           -> Icons.Default.SmartToy
                        },
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name + subtitle + capability badges
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) NeonPrimary else NeonText,
                    maxLines = 1
                )
                // Quant + params subtitle
                val subtitle = buildString {
                    val q = model.quantization?.takeIf { it.isNotBlank() && it != "Unknown" }
                    val p = model.parameterCount?.takeIf { it.isNotBlank() && it != "Unknown" }
                    if (q != null) append(q)
                    if (q != null && p != null) append(" / ")
                    if (p != null) append(p)
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonTextSecondary,
                        maxLines = 1
                    )
                }
                // Capability badges
                data class Badge(val label: String, val color: androidx.compose.ui.graphics.Color)
                val badges = buildList {
                    if (model.supportsThinking) add(Badge("Thinking", NeonPrimary))
                    if (model.supportsVision) add(Badge("Vision", androidx.compose.ui.graphics.Color(0xFF00C8FF)))
                    if (model.supportsDocument) add(Badge("Docs", androidx.compose.ui.graphics.Color(0xFFFFB300)))
                }
                if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        badges.forEach { badge ->
                            Surface(
                                color = badge.color.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = badge.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = badge.color,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Active checkmark or loading spinner on right
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = NeonPrimary,
                    strokeWidth = 2.dp
                )
                isActive -> Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Active",
                    tint = NeonPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryBottomSheet(
    prompts: List<com.tk854.localmind.data.local.entity.PromptTemplateEntity>,
    onDismiss: () -> Unit,
    onSelect: (com.tk854.localmind.data.local.entity.PromptTemplateEntity) -> Unit,
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
                                    color = NeonText,
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


data class WelcomePromptSuggestion(
    val title: String,
    val description: String,
    val prompt: String
)

@Composable
fun EmptyChatWelcome(
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = remember {
        listOf(
            WelcomePromptSuggestion(
                title = "Brainstorm ideas",
                description = "energizing routine for daily focus",
                prompt = "Brainstorm a practical and energizing morning routine that boosts daily focus and productivity."
            ),
            WelcomePromptSuggestion(
                title = "Write & Debug Code",
                description = "solve problems or write clean algorithms",
                prompt = "Write a clean Kotlin function with unit tests to process and validate structured data."
            ),
            WelcomePromptSuggestion(
                title = "Summarize text",
                description = "extract bullet points & action items",
                prompt = "Help me summarize complex text into clear, bulleted takeaways and actionable steps."
            ),
            WelcomePromptSuggestion(
                title = "Explain a concept",
                description = "understand difficult topics simply",
                prompt = "Explain how large language models generate tokens step by step in simple terms."
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            // ChatGPT-style clean emblem
            BrainLogoIcon(size = 48.dp)

            Spacer(Modifier.height(16.dp))

            Text(
                text = "What can I help with today?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp
                ),
                color = NeonText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // 2x2 grid of prompt suggestions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PromptCard(
                        modifier = Modifier.weight(1f),
                        item = suggestions[0],
                        onClick = { onSuggestionClick(suggestions[0].prompt) }
                    )
                    PromptCard(
                        modifier = Modifier.weight(1f),
                        item = suggestions[1],
                        onClick = { onSuggestionClick(suggestions[1].prompt) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PromptCard(
                        modifier = Modifier.weight(1f),
                        item = suggestions[2],
                        onClick = { onSuggestionClick(suggestions[2].prompt) }
                    )
                    PromptCard(
                        modifier = Modifier.weight(1f),
                        item = suggestions[3],
                        onClick = { onSuggestionClick(suggestions[3].prompt) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptCard(
    modifier: Modifier = Modifier,
    item: WelcomePromptSuggestion,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = NeonElevated,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonBorder),
        modifier = modifier.heightIn(min = 82.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = NeonText,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = NeonTextSecondary,
                maxLines = 2
            )
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
        title = { Text("Export Chat", color = NeonText) },
        text = { Text("Choose export format:", color = NeonTextSecondary) },
        containerColor = NeonSurface,
        titleContentColor = NeonText,
        textContentColor = NeonTextSecondary,
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
                    Text("Markdown (.md)", color = Color.White)
                }
                Button(
                    onClick = { onFormatSelected("json") },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("JSON (.json)", color = NeonPrimary)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = NeonPrimary)
                }
            }
        },
        dismissButton = null
    )
}

// ===== Minimalist ChatGPT-style Logo Icon for Empty Chat Screen =====
@Composable
fun BrainLogoIcon(size: Dp = 48.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = NeonElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NeonPrimary,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}
