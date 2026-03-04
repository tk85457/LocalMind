@file:Suppress("DEPRECATION")

package com.localmind.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.ImportModelViewModel
import com.localmind.app.ui.viewmodel.ImportState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportModelScreen(
    onNavigateBack: () -> Unit,
    onImportSuccess: () -> Unit,
    viewModel: ImportModelViewModel = hiltViewModel()
) {
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment ?: "model.gguf"
            viewModel.importModel(it, fileName)
        }
    }

    LaunchedEffect(importState) {
        if (importState is ImportState.Success) {
            kotlinx.coroutines.delay(1500)
            onImportSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Engine", color = NeonText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeonSurface
                )
            )
        },
        containerColor = NeonBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = importState) {
                is ImportState.Idle, is ImportState.Selecting -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))

                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = NeonPrimary.copy(alpha = 0.1f),
                                modifier = Modifier.size(160.dp)
                            ) {}
                            Surface(
                                shape = CircleShape,
                                color = NeonPrimary.copy(alpha = 0.2f),
                                modifier = Modifier.size(120.dp)
                            ) {}
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = NeonPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Local Calibration",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = NeonText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Select a GGUF model from your file system to begin local initialization.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        Button(
                            onClick = { filePicker.launch("application/octet-stream") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPrimary,
                                contentColor = NeonText
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("SELECT GGUF FILE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = NeonElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Info, null, tint = NeonPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "INITIALIZATION PROTOCOL",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = NeonText
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                RequirementLine("Quantization must be GGUF format")
                                RequirementLine("Recommended size: under 8GB for mobile")
                                RequirementLine("Supports Q4_K_M, Q5_K_M, and more")
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                is ImportState.Importing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.size(120.dp),
                            color = NeonPrimary,
                            strokeWidth = 12.dp,
                            trackColor = NeonTextExtraMuted.copy(alpha = 0.2f),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            "MIGRATING ENGINE...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = NeonText,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "${(state.progress * 100).toInt()}% Transferred",
                            style = MaterialTheme.typography.headlineMedium,
                            color = NeonPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is ImportState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NeonSuccess.copy(alpha = 0.1f),
                            modifier = Modifier.size(100.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(50.dp),
                                    tint = NeonSuccess
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            "SYSTEM READY",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = NeonText
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            state.modelName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is ImportState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = NeonError
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            "DATA CORRUPTION",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = NeonError
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        Button(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPrimary,
                                contentColor = NeonText
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("RETRY IMPORT", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementLine(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(NeonPrimary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = NeonTextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}
