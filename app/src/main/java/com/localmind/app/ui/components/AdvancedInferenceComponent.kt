package com.localmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localmind.app.R
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.SettingsState

/**
 * Advanced Inference Settings Section — Full PocketPal completion param parity
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
    onFlashAttentionChange: (Boolean) -> Unit,
    onUseMlockChange: (Boolean) -> Unit,
    onUseMmapChange: (Boolean) -> Unit,
    onKeyCacheTypeChange: (String) -> Unit,
    onValueCacheTypeChange: (String) -> Unit,
    onShowRamWarning: (String) -> Unit,
    onMinPChange: (Float) -> Unit = {},
    onSeedChange: (Int) -> Unit = {},
    // PocketPal full parity — new params
    onXtcThresholdChange: (Float) -> Unit = {},
    onXtcProbabilityChange: (Float) -> Unit = {},
    onTypicalPChange: (Float) -> Unit = {},
    onPenaltyLastNChange: (Int) -> Unit = {},
    onPenaltyRepeatChange: (Float) -> Unit = {},
    onPenaltyFreqChange: (Float) -> Unit = {},
    onPenaltyPresentChange: (Float) -> Unit = {},
    onMirostatChange: (Int) -> Unit = {},
    onMirostatTauChange: (Float) -> Unit = {},
    onMirostatEtaChange: (Float) -> Unit = {},
    onJinjaChange: (Boolean) -> Unit = {},
    onIncludeThinkingInContextChange: (Boolean) -> Unit = {}
) {
    if (!settings.showAdvancedSettings) return

    SettingsSection(title = stringResource(R.string.settings_inference)) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── GENERATION ───────────────────────────────────────────────

            SectionLabel("Generation")

            // Max Tokens (n_predict)
            RealTimeSlider(
                title = "Max Tokens (n_predict)",
                value = settings.defaultMaxTokens.toFloat(),
                valueRange = 128f..4096f,
                steps = 30,
                onValueChangeFinished = { onMaxTokensChange(it.toInt()) },
                formatValue = { it.toInt().toString() }
            )

            // Include Thinking in Context
            SettingsToggleItem(
                icon = Icons.Default.Lock,
                title = "Include Thinking in Context",
                subtitle = "Include model's thinking process in conversation context",
                checked = settings.includeThinkingInContext,
                onCheckedChange = onIncludeThinkingInContextChange
            )

            // Jinja Templating
            SettingsToggleItem(
                icon = Icons.Default.Storage,
                title = "Jinja Template",
                subtitle = "Use Jinja templating for chat formatting",
                checked = settings.jinja,
                onCheckedChange = onJinjaChange
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // ─── SAMPLING ─────────────────────────────────────────────────

            SectionLabel("Sampling")

            // Temperature
            RealTimeSlider(
                title = stringResource(R.string.settings_temperature),
                value = settings.defaultTemperature,
                valueRange = 0f..2f,
                onValueChangeFinished = { onTemperatureChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Top-K
            RealTimeSlider(
                title = "Top-K",
                value = settings.topK.toFloat(),
                valueRange = 1f..128f,
                steps = 126,
                onValueChangeFinished = { onTopKChange(it.toInt()) },
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

            // Min-P
            RealTimeSlider(
                title = "Min-P",
                value = settings.minP,
                valueRange = 0f..1f,
                onValueChangeFinished = { onMinPChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // XTC Threshold
            RealTimeSlider(
                title = "XTC Threshold",
                value = settings.xtcThreshold,
                valueRange = 0f..1f,
                onValueChangeFinished = { onXtcThresholdChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // XTC Probability
            RealTimeSlider(
                title = "XTC Probability",
                value = settings.xtcProbability,
                valueRange = 0f..1f,
                onValueChangeFinished = { onXtcProbabilityChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Typical-P
            RealTimeSlider(
                title = "Typical-P",
                value = settings.typicalP,
                valueRange = 0f..2f,
                onValueChangeFinished = { onTypicalPChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Seed
            var seedText by remember(settings.seed) {
                mutableStateOf(if (settings.seed == -1) "" else settings.seed.toString())
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Seed",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeonText
                    )
                    Surface(
                        color = NeonPrimary.copy(alpha = 0.1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPrimary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            if (seedText.isEmpty()) "random" else seedText,
                            color = NeonPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    "-1 = random, any other value = reproducible output",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { input ->
                        seedText = input.filter { it.isDigit() || it == '-' }
                        val parsed = seedText.toIntOrNull() ?: -1
                        onSeedChange(parsed)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("-1 (random)", color = NeonTextExtraMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = NeonTextExtraMuted,
                        cursorColor = NeonPrimary,
                        focusedTextColor = NeonText,
                        unfocusedTextColor = NeonText
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // ─── PENALTIES ────────────────────────────────────────────────

            SectionLabel("Penalties")

            // Penalty Last N
            RealTimeSlider(
                title = "Penalty Last N",
                value = settings.penaltyLastN.toFloat(),
                valueRange = 0f..256f,
                steps = 255,
                onValueChangeFinished = { onPenaltyLastNChange(it.toInt()) },
                formatValue = { it.toInt().toString() }
            )

            // Repeat Penalty (legacy repeat_penalty param — wired to onRepeatPenaltyChange)
            RealTimeSlider(
                title = "Repeat Penalty",
                value = settings.repeatPenalty,
                valueRange = 1f..2f,
                onValueChangeFinished = { onRepeatPenaltyChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Penalty Repeat
            RealTimeSlider(
                title = "Penalty Repeat",
                value = settings.penaltyRepeat,
                valueRange = 0f..2f,
                onValueChangeFinished = { onPenaltyRepeatChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Penalty Freq
            RealTimeSlider(
                title = "Penalty Freq",
                value = settings.penaltyFreq,
                valueRange = 0f..2f,
                onValueChangeFinished = { onPenaltyFreqChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            // Penalty Present
            RealTimeSlider(
                title = "Penalty Present",
                value = settings.penaltyPresent,
                valueRange = 0f..2f,
                onValueChangeFinished = { onPenaltyPresentChange(it) },
                formatValue = { String.format("%.2f", it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // ─── MIROSTAT ─────────────────────────────────────────────────

            SectionLabel("Mirostat")

            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Text(
                    "Mirostat Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = NeonText
                )
                Text(
                    "Perplexity-controlled sampling. Off = disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                SegmentedButton(
                    selected = settings.mirostat,
                    options = listOf("Off", "v1", "v2"),
                    onSelect = { onMirostatChange(it) }
                )
            }

            // Show Tau + Eta only when mirostat is active
            if (settings.mirostat > 0) {
                RealTimeSlider(
                    title = "Mirostat Tau",
                    value = settings.mirostatTau,
                    valueRange = 0f..10f,
                    steps = 9,
                    onValueChangeFinished = { onMirostatTauChange(it) },
                    formatValue = { String.format("%.1f", it) }
                )
                RealTimeSlider(
                    title = "Mirostat Eta",
                    value = settings.mirostatEta,
                    valueRange = 0f..1f,
                    onValueChangeFinished = { onMirostatEtaChange(it) },
                    formatValue = { String.format("%.2f", it) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // ─── BACKEND ──────────────────────────────────────────────────

            SectionLabel("Backend / Hardware")

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
                            onShowRamWarning("Using all $maxThreads threads may cause overheating and lag.")
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
                formatValue = { it.toInt().toString() }
            )

            // Physical Batch Size
            RealTimeSlider(
                title = "Physical Batch Size",
                value = settings.physicalBatchSize.toFloat(),
                valueRange = 1f..2048f,
                steps = 2046,
                onValueChangeFinished = { onPhysicalBatchSizeChange(it.toInt()) },
                formatValue = { it.toInt().toString() }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Flash Attention
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Text(
                    "Flash Attention",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = NeonText
                )
                Text(
                    "Must be disabled for OpenCL state save/load",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                SegmentedButton(
                    selected = if (settings.flashAttention) 0 else 1,
                    options = listOf("On", "Off"),
                    onSelect = { onFlashAttentionChange(it == 0) }
                )
            }

            SettingsToggleItem(
                icon = Icons.Default.Lock,
                title = "Use MLock",
                subtitle = "Pin model memory pages to reduce OS paging",
                checked = settings.useMlock,
                onCheckedChange = onUseMlockChange
            )

            SettingsToggleItem(
                icon = Icons.Default.Storage,
                title = "Use MMAP Flag",
                subtitle = "Enable mmap path when Memory Mapping is not set to None",
                checked = settings.useMmap,
                onCheckedChange = onUseMmapChange
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

            // Key Cache Type
            CacheTypeDropdown(
                title = "Key Cache Type",
                subtitle = "KV cache precision for keys",
                selectedType = settings.keyCacheType,
                onTypeSelected = { onKeyCacheTypeChange(it) }
            )

            // Value Cache Type
            CacheTypeDropdown(
                title = "Value Cache Type",
                subtitle = "KV cache precision for values",
                selectedType = settings.valueCacheType,
                onTypeSelected = { onValueCacheTypeChange(it) }
            )
        }
    }
}

// ─── Helper Composables ───────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NeonPrimary,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp),
        letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp)
    )
}

@Composable
private fun SegmentedButton(
    selected: Int,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            OutlinedButton(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = if (index == 0) 16.dp else 0.dp,
                    bottomStart = if (index == 0) 16.dp else 0.dp,
                    topEnd = if (index == options.lastIndex) 16.dp else 0.dp,
                    bottomEnd = if (index == options.lastIndex) 16.dp else 0.dp
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) NeonPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) NeonPrimary else NeonTextExtraMuted.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    label,
                    color = if (isSelected) NeonPrimary else NeonTextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun CacheTypeDropdown(title: String, subtitle: String, selectedType: String, onTypeSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val cacheTypes = listOf("F16", "F32", "Q8_0", "Q5_1", "Q5_0", "Q4_1", "Q4_0", "IQ4_NL")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = NeonText)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NeonTextSecondary)
        }
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = NeonBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPrimary.copy(alpha = 0.4f))
            ) {
                Text(selectedType, color = NeonPrimary, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = NeonPrimary)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(NeonSurface)
            ) {
                cacheTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type, color = if (type == selectedType) NeonPrimary else NeonText) },
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
