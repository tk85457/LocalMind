package com.localmind.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.R
import com.localmind.app.domain.model.ModelCatalogItem
import com.localmind.app.ui.components.DownloadMetricsFormatter
import com.localmind.app.ui.components.ModelListItem
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.DownloadProgress
import com.localmind.app.ui.viewmodel.HubSortOrder
import com.localmind.app.ui.viewmodel.HuggingFaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelHubScreen(
    onNavigateBack: () -> Unit,
    onOnlineModelDetail: (String) -> Unit,
    onNavigateToChat: () -> Unit,
    viewModel: HuggingFaceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        if (!state.errorMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(state.errorMessage!!)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.successMessage) {
        if (!state.successMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(state.successMessage!!)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_manager_online_hf_title), color = NeonText) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = NeonText)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshCatalog() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonPrimary)
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.model_hub_search_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NeonText,
                        unfocusedTextColor = NeonText,
                        focusedContainerColor = NeonSurface,
                        unfocusedContainerColor = NeonSurface,
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = NeonTextExtraMuted
                    ),
                    singleLine = true
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.availableAuthors, key = { "author_$it" }) { author ->
                        FilterChip(
                            selected = state.selectedAuthor == author,
                            onClick = { viewModel.onAuthorSelected(author) },
                            label = { Text(author) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = NeonPrimary
                            )
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HubSortOrder.values().forEach { sort ->
                        FilterChip(
                            selected = state.hubSortOrder == sort,
                            onClick = { viewModel.onSortOrderChanged(sort) },
                            label = { Text(sort.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = NeonPrimary
                            )
                        )
                    }
                }
            }

            if (state.isLoading && state.filteredModels.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonPrimary)
                    }
                }
            } else {
                items(state.filteredModels, key = { "online_${it.id}" }) { model ->
                    val downloadProgress = state.downloadingModels[model.id]
                    ModelListItem(
                        name = model.name,
                        sizeText = String.format("%.1f GB", model.sizeGb),
                        isActive = model.id == state.activeModelId,
                        isLocal = model.isDownloaded,
                        supportsVision = model.isVision,
                        isPending = state.pendingDownloadModelIds.contains(model.id),
                        progress = downloadProgress?.progress,
                        downloadedBytes = downloadProgress?.downloadedBytes,
                        totalBytes = downloadProgress?.totalBytes,
                        downloadSpeed = downloadProgress?.speedBps?.let { DownloadMetricsFormatter.formatSpeed(it) },
                        eta = downloadProgress?.etaSeconds?.let { DownloadMetricsFormatter.formatEta(it) },
                        onActivate = { viewModel.activateModel(model.id) },
                        onDownload = { viewModel.downloadModel(model) },
                        onCancelDownload = { viewModel.cancelDownload(model.id) },
                        onDelete = { viewModel.deleteModel(model.id) },
                        onDetails = { onOnlineModelDetail(model.repoId) }
                    )
                }

                if (state.hasMore) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.loadNextPage() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            if (state.isLoadingNextPage) {
                                CircularProgressIndicator(color = NeonPrimary, modifier = Modifier.size(24.dp))
                            } else {
                                Text(stringResource(R.string.model_hub_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}


