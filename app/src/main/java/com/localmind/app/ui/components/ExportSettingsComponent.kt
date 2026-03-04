package com.localmind.app.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.localmind.app.R
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.SettingsState

/**
 * Component for Storage, Export, and Cache settings.
 */
@Composable
fun ExportSettingsSection(
    context: Context,
    settings: SettingsState,
    isExporting: Boolean,
    isImporting: Boolean,
    isClearingCache: Boolean,
    onExportChats: () -> Unit,
    onImportChats: (android.net.Uri) -> Unit,
    onClearCache: () -> Unit,
    onPermissionRequest: () -> Unit
) {
    // Import launcher for JSON files
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportChats(uri)
        }
    }

    SettingsSection(title = stringResource(R.string.settings_storage)) {
        // Storage Status / Permissions
        SettingsItem(
            icon = Icons.Default.Storage,
            title = stringResource(R.string.settings_permissions),
            subtitle = if (settings.permissionsRequested) "File access granted" else "Required for chat export/import",
            onClick = onPermissionRequest,
            trailing = {
                if (settings.permissionsRequested) {
                    Icon(Icons.Default.CheckCircle, null, tint = NeonPrimary)
                } else {
                    Icon(Icons.Default.Warning, null, tint = NeonError)
                }
            }
        )

        // Export Chats
        SettingsItem(
            icon = Icons.Default.FileUpload,
            title = stringResource(R.string.settings_export_chats),
            subtitle = stringResource(R.string.settings_export_desc),
            trailing = {
                Button(
                    onClick = { if (!isExporting) onExportChats() },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonSurface),
                    border = ButtonDefaults.outlinedButtonBorder,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NeonPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("JSON", color = NeonPrimary)
                    }
                }
            }
        )

        // Import Chats
        SettingsItem(
            icon = Icons.Default.FileDownload,
            title = "Import Chats from JSON",
            subtitle = "Restore chat history from a file",
            trailing = {
                Button(
                    onClick = {
                        if (!isImporting) {
                            importJsonLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    },
                    enabled = !isImporting,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonSurface),
                    border = ButtonDefaults.outlinedButtonBorder,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NeonPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("IMPORT", color = NeonPrimary)
                    }
                }
            }
        )

        // Clear App Cache
        SettingsItem(
            icon = Icons.Default.DeleteSweep,
            title = stringResource(R.string.settings_clear_cache),
            subtitle = if (isClearingCache) "Clearing temporary data..." else stringResource(R.string.settings_clear_cache_desc),
            trailing = {
                TextButton(
                    onClick = { if (!isClearingCache) onClearCache() },
                    enabled = !isClearingCache
                ) {
                    if (isClearingCache) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NeonError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.settings_clear), color = NeonError)
                    }
                }
            }
        )
    }
}
