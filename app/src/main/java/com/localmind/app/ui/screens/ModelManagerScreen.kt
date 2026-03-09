package com.localmind.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.R
import com.localmind.app.domain.model.Model
import com.localmind.app.ui.components.DownloadMetricsFormatter
import com.localmind.app.ui.components.ModelListItem
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.HuggingFaceViewModel
import com.localmind.app.ui.viewmodel.ModelManagerViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onNavigateBack: () -> Unit,
    onImportModel: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToHub: () -> Unit,
    onModelDetail: (String) -> Unit,
    onOnlineModelDetail: (String) -> Unit,
    viewModel: ModelManagerViewModel = hiltViewModel(),
    huggingFaceViewModel: HuggingFaceViewModel = hiltViewModel()
) {
    val installedModels by viewModel.models.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val processingModelId by viewModel.processingModelId.collectAsStateWithLifecycle()
    val hubState by huggingFaceViewModel.uiState.collectAsStateWithLifecycle()
    val managerError by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf<Model?>(null) }
    var showSettingsDialog by remember { mutableStateOf<Model?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        huggingFaceViewModel.navigationEvents.collectLatest { event ->
            if (event is HuggingFaceViewModel.NavigationEvent.NavigateToChat) onNavigateToChat()
        }
    }
    LaunchedEffect(managerError) {
        if (!managerError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(managerError!!)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.model_manager_title), color = NeonText) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = NeonText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonSurface)
                )
            },
            containerColor = NeonBackground,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(snackbarData = data, containerColor = NeonElevated, contentColor = NeonText)
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Ready to Use
                item {
                    Text(
                        text = stringResource(R.string.model_hub_ready_to_use),
                        color = NeonPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (installedModels.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.model_manager_empty_body),
                            color = NeonTextExtraMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    items(installedModels, key = { "installed_${it.id}" }) { model ->
                        ModelListItem(
                            name = model.name,
                            sizeText = model.formattedSize,
                            isActive = model.id == activeModel?.id,
                            isLocal = true,
                            isWorking = model.id == processingModelId,
                            supportsVision = model.supportsVision,
                            onActivate = { viewModel.activateModel(model) },
                            onOffload = { viewModel.offloadModel() },
                            onDelete = { showDeleteDialog = model },
                            onSettings = { showSettingsDialog = model },
                            onDetails = { onModelDetail(model.id) }
                        )
                    }
                }

                // Section: Curated List / Recommendations
                if (hubState.curatedModels.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.model_hub_title),
                            color = NeonPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(hubState.curatedModels, key = { "curated_${it.id}" }) { model ->
                        val isDownloading = hubState.downloadingModels.containsKey(model.id)
                        val downloadEntry = hubState.downloadingModels[model.id]
                        val progress = downloadEntry?.progress
                        val isPending = hubState.pendingDownloadModelIds.contains(model.id)
                        val downloadedBytes = downloadEntry?.downloadedBytes
                        val totalBytes = downloadEntry?.totalBytes
                        val downloadSpeed = downloadEntry?.speedBps?.let {
                            com.localmind.app.ui.components.DownloadMetricsFormatter.formatSpeed(it)
                        }
                        val eta = downloadEntry?.etaSeconds?.let {
                            com.localmind.app.ui.components.DownloadMetricsFormatter.formatEta(it)
                        }

                        ModelListItem(
                            name = model.name,
                            sizeText = String.format("%.1f GB", model.sizeGb),
                            isActive = model.id == activeModel?.id,
                            isLocal = model.isDownloaded,
                            isPending = isPending && !isDownloading,
                            isWorking = isDownloading,
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            downloadSpeed = downloadSpeed,
                            eta = eta,
                            supportsVision = model.isVision,
                            onActivate = {
                                if (model.isDownloaded) {
                                    viewModel.activateModelById(model.id)
                                } else {
                                    huggingFaceViewModel.downloadModel(model)
                                }
                            },
                            onDownload = {
                                huggingFaceViewModel.downloadModel(model)
                            },
                            onCancelDownload = {
                                huggingFaceViewModel.cancelDownload(model.id)
                            },
                            onOffload = { viewModel.offloadModel() },
                            onDelete = {
                                huggingFaceViewModel.deleteModel(model.id)
                            },
                            onSettings = {
                                // Settings require a full Model entity, usually handled in Ready to Use section
                            },
                            onDetails = { onOnlineModelDetail(model.repoId) },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) } // Padding for FAB
            }
        }

        // Expanded FAB Overlay Context
        AnimatedVisibility(
            visible = fabExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeonBackground.copy(alpha = 0.9f))
                    .clickable { fabExpanded = false }
            ) {
                // Dimmed background
            }
        }

        // FAB Menu and Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // FAB Options
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                onNavigateToHub()
                            },
                            containerColor = NeonElevated,
                            contentColor = NeonPrimary,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.hf_hugging_icon),
                                contentDescription = "Hugging Face",
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.model_hub_add_from_hf), fontWeight = FontWeight.Bold)
                        }

                        ExtendedFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                onImportModel()
                            },
                            containerColor = NeonElevated,
                            contentColor = NeonPrimary,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.model_hub_add_local), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Main Sticky FAB
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = NeonPrimary,
                    contentColor = NeonText,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Menu FAB",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    // Dialogs
    showDeleteDialog?.let { model ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.model_manager_purge_title)) },
            text = { Text(stringResource(R.string.model_manager_purge_body, model.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteModel(model)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonError)
                ) {
                    Text(stringResource(R.string.model_manager_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel), color = NeonPrimary)
                }
            },
            containerColor = NeonSurface,
            titleContentColor = NeonText,
            textContentColor = NeonTextSecondary
        )
    }

    showSettingsDialog?.let { model ->
        ModelSettingsDialog(
            model = model,
            onDismiss = { showSettingsDialog = null },
            onSave = { bos, eos, addGen, system, stop ->
                viewModel.updateModelSettings(model.id, bos, eos, addGen, system, stop)
                showSettingsDialog = null
            }
        )
    }
}

// Keep the existing settings dialog code below this.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelSettingsDialog(
    model: Model,
    onDismiss: () -> Unit,
    onSave: (bos: Boolean, eos: Boolean, addGenPrompt: Boolean, systemPrompt: String, stopWords: List<String>) -> Unit
) {
    var bos by remember { mutableStateOf(model.bosEnabled) }
    var eos by remember { mutableStateOf(model.eosEnabled) }
    var addGenPrompt by remember { mutableStateOf(model.addGenPrompt) }
    var systemPrompt by remember { mutableStateOf(model.recommendedSystemPrompt ?: "") }
    var stopWords by remember {
        mutableStateOf(
            runCatching {
                val array = JSONArray(model.stopTokensJson)
                List(array.length()) { i -> array.getString(i) }
            }.getOrElse { listOf("<|eot_id|>") }
        )
    }
    var newStopWord by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 40.dp),
            colors = CardDefaults.cardColors(containerColor = NeonSurface),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Model Settings", color = NeonText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = NeonTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Column {
                            Text("Model Name", color = NeonTextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = NeonElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    model.name,
                                    color = NeonText,
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SettingSwitchRow("BOS", bos) { bos = it }
                            HorizontalDivider(color = NeonElevated.copy(alpha = 0.5f))
                            SettingSwitchRow("EOS", eos) { eos = it }
                            HorizontalDivider(color = NeonElevated.copy(alpha = 0.5f))
                            SettingSwitchRow("Add Generation Prompt", addGenPrompt) { addGenPrompt = it }
                        }
                    }

                    item {
                        Column {
                            Text("System Prompt", color = NeonTextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = systemPrompt,
                                onValueChange = { systemPrompt = it },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = NeonElevated,
                                    focusedBorderColor = NeonPrimary,
                                    unfocusedTextColor = NeonText,
                                    focusedTextColor = NeonText,
                                    unfocusedContainerColor = NeonElevated,
                                    focusedContainerColor = NeonElevated
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Template: ", color = NeonTextSecondary, fontSize = 14.sp)
                            Text(
                                "{{- bos_token }}{{%- if",
                                color = NeonTextExtraMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Edit",
                                color = NeonPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { /* Edit template logic */ }.padding(8.dp)
                            )
                        }
                    }

                    item {
                        Column {
                            Text("STOP WORDS", color = NeonTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                stopWords.forEach { word ->
                                    Surface(
                                        color = NeonElevated,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.clickable { stopWords = stopWords - word }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(word, color = NeonText, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.Close, contentDescription = null, tint = NeonTextExtraMuted, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = newStopWord,
                                onValueChange = { newStopWord = it },
                                placeholder = { Text("Add new stop word", color = NeonTextExtraMuted, fontSize = 14.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (newStopWord.isNotBlank()) {
                                        IconButton(onClick = {
                                            if (newStopWord.isNotBlank()) {
                                                stopWords = stopWords + newStopWord.trim()
                                                newStopWord = ""
                                            }
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = NeonPrimary)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = NeonPrimary,
                                    unfocusedContainerColor = NeonElevated,
                                    focusedContainerColor = NeonElevated
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reset",
                        color = NeonTextExtraMuted,
                        modifier = Modifier.clickable {
                            bos = model.bosEnabled
                            eos = model.eosEnabled
                            addGenPrompt = model.addGenPrompt
                            systemPrompt = model.recommendedSystemPrompt ?: ""
                            stopWords = runCatching {
                                val array = org.json.JSONArray(model.stopTokensJson)
                                List(array.length()) { i -> array.getString(i) }
                            }.getOrElse { listOf("<|eot_id|>") }
                            newStopWord = ""
                        }.padding(8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = NeonTextSecondary)
                        }
                        Button(
                            onClick = { onSave(bos, eos, addGenPrompt, systemPrompt, stopWords) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NeonText, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonText,
                checkedTrackColor = NeonPrimary,
                uncheckedThumbColor = NeonTextExtraMuted,
                uncheckedTrackColor = NeonElevated
            )
        )
    }
}
