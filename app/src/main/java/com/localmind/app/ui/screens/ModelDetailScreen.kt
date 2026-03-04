@file:Suppress("DEPRECATION")

package com.localmind.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.ModelDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailScreen(
    onNavigateBack: () -> Unit,
    onBenchmark: (String) -> Unit,
    viewModel: ModelDetailViewModel = hiltViewModel()
) {
    val model by viewModel.model.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val estimatedRAM by viewModel.estimatedRAM.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var hardwareExpanded by remember { mutableStateOf(true) }
    var briefExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Model Spec", color = NeonText, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonText)
                    }
                },
                actions = {
                    if (model?.isActive == true) {
                        Text(
                            "Offload",
                            color = NeonSuccess,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.offloadModel() }
                                .padding(end = 16.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonBackground)
            )
        },
        containerColor = NeonBackground
    ) { padding ->
        val currentModel = model
        if (isLoading || currentModel == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Core Info Card
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
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).padding(16.dp),
                                tint = NeonPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            currentModel.name,
                            color = NeonText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DetailStatItem(label = "Size", value = currentModel.formattedSize)
                            VerticalDivider(modifier = Modifier.height(40.dp), color = NeonElevated)
                            DetailStatItem(label = "Estimated Memory", value = estimatedRAM ?: "Calculating...")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Brief information
                ExpandableRow(
                    title = "Brief information",
                    expanded = briefExpanded,
                    onToggle = { briefExpanded = !briefExpanded }
                ) {
                    Text(
                        "This is a high-performance local language model optimized for mobile hardware.",
                        color = NeonTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hardware capabilities
                ExpandableSection(
                    title = "Hardware capabilities",
                    expanded = hardwareExpanded,
                    onToggle = { hardwareExpanded = !hardwareExpanded }
                ) {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Vision capabilities: ", color = NeonTextExtraMuted, fontSize = 14.sp)
                            Text(
                                if (currentModel.supportsVision) "Supported" else "Not Supported",
                                color = if (currentModel.supportsVision) NeonSuccess else NeonTextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (currentModel.supportsVision) {
                                Icon(Icons.Default.Check, null, tint = NeonSuccess, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text("Hardware specifics", color = NeonText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Specifics Grid (2x2)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            GridItem(
                                modifier = Modifier.weight(1f),
                                label = "Parameters",
                                value = metadata?.let { String.format(java.util.Locale.US, "%.2fM", it.nParams.toDouble() / 1_000_000.0) } ?: "38.29M"
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            GridItem(
                                modifier = Modifier.weight(1f),
                                label = "Context length",
                                value = metadata?.let { "${it.nCtxTrain / 1024}k" } ?: "2k"
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            GridItem(
                                modifier = Modifier.weight(1f),
                                label = "Architecture",
                                value = metadata?.hparams?.get("general.architecture") ?: metadata?.modelDesc ?: "gemma"
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            GridItem(
                                modifier = Modifier.weight(1f),
                                label = "Author",
                                value = metadata?.hparams?.get("general.author") ?: "ggml-org"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Terminal Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (currentModel.isActive == false) {
                        Button(
                            onClick = { viewModel.activateModel() },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary, contentColor = NeonBackground),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("ACTIVATE ENGINE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onBenchmark(currentModel.id) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPrimary),
                            border = BorderStroke(2.dp, NeonPrimary)
                        ) {
                            Icon(Icons.Default.Speed, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BENCHMARK", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                // Show confirmation before deletion in a real app
                                viewModel.deleteModel { onNavigateBack() }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonError),
                            border = BorderStroke(2.dp, NeonError)
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PURGE", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { /* Check on HuggingFace logic */ },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonElevated),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Check on HuggingFace", color = NeonPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.OpenInNew, null, tint = NeonPrimary, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
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
private fun ExpandableRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = NeonText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null,
                tint = NeonTextSecondary
            )
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
        HorizontalDivider(color = NeonElevated)
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = NeonText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null,
                tint = NeonTextSecondary
            )
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}

@Composable
private fun GridItem(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NeonElevated)
            .padding(16.dp)
    ) {
        Text(label, color = NeonTextExtraMuted, fontSize = 12.sp)
        Text(value, color = NeonText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
