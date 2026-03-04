package com.localmind.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.R
import com.localmind.app.domain.model.ModelVariant
import com.localmind.app.ui.components.DownloadMetricsFormatter
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.DownloadProgress
import com.localmind.app.ui.viewmodel.HuggingFaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineModelDetailScreen(
    repoId: String,
    onNavigateBack: () -> Unit,
    viewModel: HuggingFaceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(repoId) {
        viewModel.loadOnlineModelDetails(repoId)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearOnlineModelDetails() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Model Details", color = NeonText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = NeonText)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Share model link maybe? */ }) {
                        Icon(Icons.Default.Share, null, tint = NeonText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonBackground)
            )
        },
        containerColor = NeonBackground
    ) { padding ->
        if (state.isOnlineDetailLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonPrimary)
            }
        } else {
            val model = state.onlineDetailModel
            if (model == null) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.errorMessage ?: stringResource(R.string.model_manager_detail_unavailable),
                        color = NeonTextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        // Header Section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = NeonSurface),
                            border = BorderStroke(1.dp, NeonElevated)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = NeonElevated,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).padding(16.dp),
                                        tint = NeonPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    model.name,
                                    color = NeonText,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    model.author,
                                    color = NeonTextSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    DetailStatItem(label = "Downloads", value = "${model.downloads}")
                                    VerticalDivider(modifier = Modifier.height(40.dp), color = NeonElevated)
                                    DetailStatItem(label = "Likes", value = "${model.likes}")
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AVAILABLE VARIANTS (GGUF)",
                            color = NeonPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    items(model.variants, key = { "variant_${it.filename}" }) { variant ->
                        val isDownloading =
                            state.pendingDownloadModelIds.contains(model.id) ||
                                state.downloadingModels[model.id] != null
                        VariantCard(
                            variant = variant,
                            downloadProgress = state.downloadingModels[model.id],
                            isDownloading = isDownloading,
                            onDownload = {
                                viewModel.onVariantSelected(model.id, variant.filename)
                                viewModel.downloadModelVariant(model, variant.filename)
                            },
                            onCancel = { viewModel.cancelDownload(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = NeonTextExtraMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = NeonText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VariantCard(
    variant: ModelVariant,
    downloadProgress: DownloadProgress?,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NeonElevated),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = variant.filename,
                        color = NeonText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatSize(variant.sizeBytes)} • ${variant.quantization}",
                        color = NeonTextSecondary,
                        fontSize = 13.sp
                    )
                }

                if (isDownloading) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = NeonError, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary, contentColor = NeonBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GET", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadProgress?.progress?.coerceIn(0f, 1f) ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = NeonPrimary,
                        trackColor = NeonTextExtraMuted.copy(alpha = 0.1f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = downloadProgress?.let { "${(it.progress * 100f).toInt().coerceIn(0, 100)}%" } ?: "Connecting...",
                            color = NeonText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = downloadProgress?.let { DownloadMetricsFormatter.formatSpeed(it.speedBps) } ?: "",
                                color = NeonTextSecondary,
                                fontSize = 12.sp
                            )
                            if (downloadProgress?.etaSeconds != null && downloadProgress.etaSeconds > 0) {
                                Text(
                                    text = "• ${DownloadMetricsFormatter.formatEta(downloadProgress.etaSeconds)}",
                                    color = NeonTextExtraMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (downloadProgress != null && downloadProgress.totalBytes > 0L) {
                        Text(
                            text = "${DownloadMetricsFormatter.formatBytes(downloadProgress.downloadedBytes)} / ${DownloadMetricsFormatter.formatBytes(downloadProgress.totalBytes)}",
                            color = NeonTextExtraMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.0f MB", mb)
}
