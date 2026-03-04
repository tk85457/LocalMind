@file:Suppress("DEPRECATION")

package com.localmind.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.R
import com.localmind.app.core.utils.UiState
import com.localmind.app.ui.components.*
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val importExportStatus by viewModel.importExportStatus.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val maxThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    LaunchedEffect(importExportStatus) {
        when (val status = importExportStatus) {
            is UiState.Success -> {
                android.widget.Toast.makeText(context, status.data, android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetImportExportStatus()
            }
            is UiState.Error -> {
                android.widget.Toast.makeText(context, status.message, android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetImportExportStatus()
            }
            else -> {}
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.applyPendingRuntimeSettingChanges()
        }
    }



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(com.localmind.app.R.string.settings_title), color = NeonText) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.applyPendingRuntimeSettingChanges()
                        onNavigateBack()
                    }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var showRamWarning by remember { mutableStateOf<String?>(null) }

            showRamWarning?.let { warningText ->
                AlertDialog(
                    onDismissRequest = { showRamWarning = null },
                    title = { Text("Performance Warning", color = NeonError) },
                    text = { Text(warningText, color = NeonText) },
                    confirmButton = {
                        TextButton(onClick = { showRamWarning = null }) {
                            Text("I Understand", color = NeonPrimary)
                        }
                    },
                    containerColor = NeonSurface,
                    titleContentColor = NeonError,
                    textContentColor = NeonText
                )
            }

            // Navigation Section
            SettingsSection(title = "Navigation") {
                SettingsToggleItem(
                    icon = Icons.Default.Launch,
                    title = "Auto Navigate to Chat",
                    subtitle = "Open chat screen automatically when loading starts.",
                    checked = settings.autoNavigateChat,
                    onCheckedChange = { viewModel.updateAutoNavigateChat(it) }
                )
            }

            // Memory & Performance Section (Modularized)
            val hardwareStats by viewModel.hardwareStats.collectAsStateWithLifecycle()
            MemorySettingsSection(
                settings = settings,
                hardwareStats = hardwareStats,
                onContextSizeChange = { viewModel.updateContextSize(it) },
                onMemoryMappingChange = { viewModel.updateMemoryMapping(it) },
                onAutoOffloadChange = { viewModel.updateAutoOffload(it) },
                onForceLoadChange = { viewModel.setAllowForceLoad(it) },
                onShowRamWarning = { showRamWarning = it }
            )

            // Advanced Toggle
            SettingsSection(title = "Experimental") {
                SettingsToggleItem(
                    icon = Icons.Default.SettingsSuggest,
                    title = "Show Advanced Settings",
                    subtitle = "Expose detailed inference and backend control",
                    checked = settings.showAdvancedSettings,
                    onCheckedChange = { viewModel.setShowAdvancedSettings(it) }
                )
            }

            // App Settings (Modularized)
            AppSettingsSection(
                settings = settings,
                onLanguageChange = { viewModel.updateLanguage(it) },
                onDarkModeChange = { viewModel.updateDarkMode(it) },
                onBiometricLockChange = { viewModel.updateBiometricLock(it) },
                onThemeColorChange = { viewModel.updateThemeColor(it) },
                onFontScaleChange = { viewModel.updateFontScale(it) },
                onFontFamilyChange = { viewModel.updateFontFamily(it) },
                onAutoDeleteDaysChange = { viewModel.updateAutoDeleteDays(it) }
            )

            // Storage & Backup (Modularized)
            val isClearingCache by viewModel.isClearingCache.collectAsStateWithLifecycle()
            val isGeneratingExport by viewModel.isExporting.collectAsState(initial = false)
            val isImportingData by viewModel.isImporting.collectAsState(initial = false)

            ExportSettingsSection(
                context = context,
                settings = settings,
                isExporting = isGeneratingExport,
                isImporting = isImportingData,
                isClearingCache = isClearingCache,
                onExportChats = {
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    // Note: Export logic handled by component or passed launcher
                    // For now, keeping the launcher logic in component if possible or passing it
                },
                onImportChats = { uri -> viewModel.importChatsFromJsonUri(uri, context) },
                onClearCache = { viewModel.clearCache(context) },
                onPermissionRequest = {
                    // Logic handled in component or passed
                }
            )

            // Advanced Inference (Modularized)
            AdvancedInferenceSection(
                settings = settings,
                maxThreads = maxThreads,
                onTemperatureChange = { viewModel.updateTemperature(it) },
                onMaxTokensChange = { viewModel.updateMaxTokens(it) },
                onTopPChange = { viewModel.updateTopP(it) },
                onTopKChange = { viewModel.updateTopK(it) },
                onRepeatPenaltyChange = { viewModel.updateRepeatPenalty(it) },
                onThreadCountChange = { viewModel.updateThreadCount(it) },
                onBatchSizeChange = { viewModel.updateBatchSize(it) },
                onPhysicalBatchSizeChange = { viewModel.updatePhysicalBatchSize(it) },
                onFlashAttentionChange = { viewModel.updateFlashAttention(it) },
                onKeyCacheTypeChange = { viewModel.updateKeyCacheType(it) },
                onValueCacheTypeChange = { viewModel.updateValueCacheType(it) },
                onShowRamWarning = { showRamWarning = it }
            )

            // About Section
            SettingsSection(title = stringResource(R.string.settings_about)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_version),
                    subtitle = "1.0.0"
                )
                SettingsItem(
                    icon = Icons.Default.Replay,
                    title = "View Intro Again",
                    subtitle = "Replay the onboarding walkthrough",
                    onClick = {
                        viewModel.setOnboardingCompleted(false)
                        android.widget.Toast.makeText(context, "Restart the app to see the intro", android.widget.Toast.LENGTH_LONG).show()
                    }
                )
            }

        }
    }
}

// Local components have been moved to com.localmind.app.ui.components.SettingsComponents.kt
