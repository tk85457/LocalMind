package com.localmind.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.localmind.app.R
import com.localmind.app.core.utils.HardwareStats
import com.localmind.app.ui.viewmodel.SettingsState
import com.localmind.app.ui.theme.NeonText
import com.localmind.app.ui.theme.NeonPrimary
import com.localmind.app.ui.theme.NeonTextSecondary
import com.localmind.app.ui.theme.NeonSurface

@Composable
fun MemorySettingsSection(
    settings: SettingsState,
    hardwareStats: HardwareStats?,
    onContextSizeChange: (Int) -> Unit,
    onMemoryMappingChange: (String) -> Unit,
    onAutoOffloadChange: (Boolean) -> Unit,
    onForceLoadChange: (Boolean) -> Unit,
    onShowRamWarning: (String) -> Unit
) {
    SettingsSection(title = "Memory & Performance") {
        // Device Info (Inline)
        val deviceSubtitle = if (hardwareStats != null) {
            "${android.os.Build.MODEL}\n" +
            "RAM: ${String.format("%.1f", hardwareStats.usedRamGb)}/${String.format("%.1f", hardwareStats.totalRamGb)} GB | " +
            "CPU: ${(hardwareStats.cpuUsage * 100).toInt()}%"
        } else "Detecting hardware..."

        SettingsItem(
            icon = Icons.Default.Memory,
            title = "Hardware Status",
            subtitle = deviceSubtitle
        )

        // Context Size Input
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Context Size", color = NeonText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            var contextSizeInput by remember(settings.contextSize) { mutableStateOf(settings.contextSize.toString()) }
            var warningShownForCurrentHighRange by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current

            OutlinedTextField(
                value = contextSizeInput,
                onValueChange = { input ->
                    if (!input.all { c -> c.isDigit() }) return@OutlinedTextField
                    contextSizeInput = input

                    // Minimum 512 ho tabhi save karo — incomplete/tiny values avoid karo
                    val size = input.toIntOrNull() ?: return@OutlinedTextField
                    if (size < 512) return@OutlinedTextField
                    onContextSizeChange(size)

                    val recommended = 2480
                    val totalRam = hardwareStats?.totalRamGb ?: 4.0
                    val warningText = when {
                        size > recommended ->
                            "Context $size exceeds recommended ($recommended). Stability not guaranteed."
                        else -> null
                    }
                    if (warningText != null) {
                        if (!warningShownForCurrentHighRange) {
                            onShowRamWarning(warningText)
                            warningShownForCurrentHighRange = true
                        }
                    } else {
                        warningShownForCurrentHighRange = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NeonText,
                    unfocusedTextColor = NeonText,
                    focusedBorderColor = NeonPrimary,
                    unfocusedBorderColor = NeonTextSecondary.copy(alpha = 0.3f),
                    cursorColor = NeonPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                })
            )
            Text(
                "${stringResource(R.string.settings_requires_reload)} • Recommended: 2480 tokens",
                style = MaterialTheme.typography.bodySmall,
                color = NeonTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Memory Mapping Dialog
        var showMappingDialog by remember { mutableStateOf(false) }
        val mappingOptions = listOf("None", "Smart", "All")

        if (showMappingDialog) {
            AlertDialog(
                onDismissRequest = { showMappingDialog = false },
                title = { Text("Memory Mapping", color = NeonText) },
                text = {
                    Column {
                        mappingOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onMemoryMappingChange(option)
                                        showMappingDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.memoryMapping == option,
                                    onClick = {
                                        onMemoryMappingChange(option)
                                        showMappingDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = NeonPrimary,
                                        unselectedColor = NeonTextSecondary
                                    )
                                )
                                Text(option, color = NeonText, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMappingDialog = false }) {
                        Text("Close", color = NeonPrimary)
                    }
                },
                containerColor = NeonSurface,
                titleContentColor = NeonText,
                textContentColor = NeonText
            )
        }

        SettingsItem(
            icon = Icons.Default.Storage,
            title = "Memory Mapping",
            subtitle = "Speed up loading with mmap()",
            onClick = { showMappingDialog = true },
            trailing = {
                Text(
                    settings.memoryMapping,
                    color = NeonPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        )

        // Auto Offload
        SettingsToggleItem(
            icon = Icons.Default.PowerSettingsNew,
            title = "Auto Offload",
            subtitle = "Free RAM when app is in background",
            checked = settings.autoOffload,
            onCheckedChange = onAutoOffloadChange
        )

        // Force Load
        SettingsToggleItem(
            icon = Icons.Default.FlashOn,
            title = "Emergency Force Load",
            subtitle = "Enable loading on unsupported hardware",
            checked = settings.allowForceLoad,
            onCheckedChange = onForceLoadChange
        )
    }
}
