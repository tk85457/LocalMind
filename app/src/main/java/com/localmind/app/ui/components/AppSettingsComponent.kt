package com.localmind.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localmind.app.R
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.SettingsState

/**
 * Component for general App Settings including personalization and security.
 */
@Composable
fun AppSettingsSection(
    settings: SettingsState,
    onLanguageChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onBiometricLockChange: (Boolean) -> Unit,
    onThemeColorChange: (String) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onAutoDeleteDaysChange: (Int) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_app)) {
        // Language
        var showLanguageDialog by remember { mutableStateOf(false) }
        val languages = listOf("English (EN)", "Hindi (HI)", "Urdu (UR)", "Spanish (ES)", "French (FR)", "German (DE)")

        if (showLanguageDialog) {
            SettingsSelectionDialog(
                title = stringResource(R.string.settings_language),
                options = languages,
                selectedOption = settings.language,
                onOptionSelected = {
                    onLanguageChange(it)
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false }
            )
        }

        SettingsItem(
            icon = Icons.Default.Language,
            title = stringResource(R.string.settings_language),
            subtitle = settings.language,
            onClick = { showLanguageDialog = true },
            trailing = { Icon(Icons.Default.KeyboardArrowDown, null, tint = NeonTextSecondary) }
        )

        // Dark Mode
        SettingsToggleItem(
            icon = Icons.Default.LightMode,
            title = stringResource(R.string.settings_dark_mode),
            subtitle = stringResource(R.string.settings_dark_mode_desc),
            checked = settings.darkMode,
            onCheckedChange = onDarkModeChange
        )

        // Biometric Lock
        SettingsToggleItem(
            icon = Icons.Default.Fingerprint,
            title = "Biometric App Lock",
            subtitle = "Secure access with your fingerprint or face",
            checked = settings.biometricLock,
            onCheckedChange = onBiometricLockChange
        )

        // Theme Color
        var showThemeDialog by remember { mutableStateOf(false) }
        val themes = listOf("Neon", "Blue", "Green", "Orange", "Purple", "Red", "Teal", "Pink", "Cyan")

        if (showThemeDialog) {
            SettingsSelectionDialog(
                title = "Select Theme Color",
                options = themes,
                selectedOption = settings.themeColor,
                onOptionSelected = {
                    onThemeColorChange(it)
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false }
            )
        }

        SettingsItem(
            icon = Icons.Default.Palette,
            title = "Theme Color",
            subtitle = settings.themeColor,
            onClick = { showThemeDialog = true },
            trailing = { Icon(Icons.Default.KeyboardArrowDown, null, tint = NeonTextSecondary) }
        )

        // Font Scale
        RealTimeSlider(
            title = "Font Scale",
            value = settings.fontScale,
            valueRange = 0.5f..1.5f,
            steps = 9,
            onValueChangeFinished = onFontScaleChange,
            formatValue = { String.format("%.1fx", it) }
        )

        // Font Family
        var showFontFamilyDialog by remember { mutableStateOf(false) }
        val fontFamilies = listOf("Default", "Serif", "SansSerif", "Monospace", "Cursive", "Casual", "Tech", "Classic", "Modern", "Soft")

        if (showFontFamilyDialog) {
            SettingsSelectionDialog(
                title = "Font Family",
                options = fontFamilies,
                selectedOption = settings.fontFamily,
                onOptionSelected = {
                    onFontFamilyChange(it)
                    showFontFamilyDialog = false
                },
                onDismiss = { showFontFamilyDialog = false }
            )
        }

        SettingsItem(
            icon = Icons.Default.FontDownload,
            title = "Font Family",
            subtitle = settings.fontFamily,
            onClick = { showFontFamilyDialog = true },
            trailing = { Icon(Icons.Default.KeyboardArrowDown, null, tint = NeonTextSecondary) }
        )

        // Auto Delete
        var showAutoDeleteDialog by remember { mutableStateOf(false) }
        val autoDeleteOptions = listOf(0 to "Never", 1 to "1 Day", 7 to "7 Days", 30 to "30 Days")

        if (showAutoDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showAutoDeleteDialog = false },
                title = { Text("Auto Delete Chats", color = NeonText) },
                text = {
                    Column {
                        autoDeleteOptions.forEach { (days, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAutoDeleteDaysChange(days)
                                        showAutoDeleteDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, color = NeonText)
                                RadioButton(
                                    selected = settings.autoDeleteDays == days,
                                    onClick = {
                                        onAutoDeleteDaysChange(days)
                                        showAutoDeleteDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = NeonPrimary,
                                        unselectedColor = NeonTextSecondary
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAutoDeleteDialog = false }) {
                        Text("Close", color = NeonPrimary)
                    }
                },
                containerColor = NeonSurface
            )
        }

        SettingsItem(
            icon = Icons.Default.AutoDelete,
            title = "Auto Delete Chats",
            subtitle = autoDeleteOptions.find { it.first == settings.autoDeleteDays }?.second ?: "Never",
            onClick = { showAutoDeleteDialog = true },
            trailing = { Icon(Icons.Default.KeyboardArrowDown, null, tint = NeonTextSecondary) }
        )
    }
}

@Composable
fun SettingsSelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = NeonText) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(option, color = NeonText)
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { onOptionSelected(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = NeonPrimary,
                                unselectedColor = NeonTextSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = NeonPrimary)
            }
        },
        containerColor = NeonSurface
    )
}
