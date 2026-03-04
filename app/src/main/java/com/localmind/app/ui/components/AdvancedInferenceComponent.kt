package com.localmind.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localmind.app.R
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.SettingsState

/**
 * Advanced Inference Settings Section
 * Encapsulates temperature, top-p, top-k, max tokens, repeat penalty, and thread count.
 * All sliders support real-time updates.
 */
@Composable
fun AdvancedInferenceSection(
    settings: SettingsState,
    maxThreads: Int,
    onTemperatureChange: (Float) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
    onTopPChange: (Float) -> Unit,
    onTopKChange: (Int) -> Unit,
    onRepeatPenaltyChange: (Float) -> Unit,
    onThreadCountChange: (Int) -> Unit,
    onBatchSizeChange: (Int) -> Unit,
    onPhysicalBatchSizeChange: (Int) -> Unit,
    onFlashAttentionChange: (String) -> Unit,
    onKeyCacheTypeChange: (String) -> Unit,
    onValueCacheTypeChange: (String) -> Unit,
    onShowRamWarning: (String) -> Unit
) {
    if (!settings.showAdvancedSettings) return

    SettingsSection(title = stringResource(R.string.settings_inference)) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Temperature
            RealTimeSlider(
                title = stringResource(R.string.settings_temperature),
                value = settings.defaultTemperature,
                valueRange = 0f..2f,
                onValueChangeFinished = { onTemperatureChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Max Tokens
            RealTimeSlider(
                title = "Response Max Tokens",
                value = settings.defaultMaxTokens.toFloat(),
                valueRange = 128f..131072f,
                steps = 1023,
                onValueChangeFinished = { onMaxTokensChange(it.toInt()) },
                formatValue = { it.toInt().toString() }
            )

            // Top-P
            RealTimeSlider(
                title = stringResource(R.string.settings_top_p),
                value = settings.defaultTopP,
                valueRange = 0f..1f,
                onValueChangeFinished = { onTopPChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Top-K
            RealTimeSlider(
                title = "Top-K Sampling",
                value = settings.topK.toFloat(),
                valueRange = 1f..100f,
                steps = 98,
                onValueChangeFinished = { onTopKChange(it.toInt()) },
                formatValue = { it.toInt().toString() }
            )

            // Repeat Penalty
            RealTimeSlider(
                title = stringResource(R.string.settings_repeat_penalty),
                value = settings.repeatPenalty,
                valueRange = 1.0f..2.0f,
                onValueChangeFinished = { onRepeatPenaltyChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Thread Count
            Column {
                RealTimeSlider(
                    title = stringResource(R.string.settings_threads),
                    value = settings.threadCount.toFloat(),
                    valueRange = 1f..maxThreads.toFloat(),
                    steps = (maxThreads - 2).coerceAtLeast(0),
                    onValueChangeFinished = {
                        val threads = it.toInt()
                        if (threads == maxThreads && maxThreads > 4) {
                            onShowRamWarning("Using all available threads ($maxThreads) may cause device overheating and system lag.")
                        }
                        onThreadCountChange(threads)
                    },
                    formatValue = { it.toInt().toString() }
                )
                Text(
                    stringResource(R.string.settings_requires_reload),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonTextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Batch Size
            RealTimeSlider(
                title = "Batch Size",
                value = settings.batchSize.toFloat(),
                valueRange = 1f..2048f,
                steps = 2046,
                onValueChangeFinished = { onBatchSizeChange(it.toInt()) },
                formatValue = { "Batch size: " + it.toInt().toString() }
            )

            // Physical Batch Size
            RealTimeSlider(
                title = "Physical Batch Size",
                value = settings.physicalBatchSize.toFloat(),
                valueRange = 1f..2048f,
                steps = 2046,
                onValueChangeFinished = { onPhysicalBatchSizeChange(it.toInt()) },
                formatValue = { "Physical batch size: " + it.toInt().toString() }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Flash Attention
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Flash Attention", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Must be disabled for OpenCL state save/load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf("Auto", "On", "Off")
                    options.forEach { option ->
                        val selected = settings.flashAttention == option
                        OutlinedButton(
                            onClick = { onFlashAttentionChange(option) },
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = if (option == "Auto") 16.dp else 0.dp,
                                bottomStart = if (option == "Auto") 16.dp else 0.dp,
                                topEnd = if (option == "Off") 16.dp else 0.dp,
                                bottomEnd = if (option == "Off") 16.dp else 0.dp
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(option, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Key Cache Type
            CacheTypeDropdown(
                title = "Key Cache Type",
                subtitle = "Select the cache type for key computation",
                selectedType = if (settings.keyCacheType == "F16") "F16 (Default)" else settings.keyCacheType,
                onTypeSelected = { onKeyCacheTypeChange(it.replace(" (Default)", "")) }
            )

            // Value Cache Type
            CacheTypeDropdown(
                title = "Value Cache Type",
                subtitle = "Select the cache type for value computation",
                selectedType = if (settings.valueCacheType == "F16") "F16 (Default)" else settings.valueCacheType,
                onTypeSelected = { onValueCacheTypeChange(it.replace(" (Default)", "")) }
            )
        }
    }
}

@Composable
fun CacheTypeDropdown(title: String, subtitle: String, selectedType: String, onTypeSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val cacheTypes = listOf("F16 (Default)", "F32", "Q8_0", "Q5_1", "Q5_0", "Q4_1", "Q4_0", "IQ4_NL")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedType)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                cacheTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            onTypeSelected(type)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
