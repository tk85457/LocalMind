@file:Suppress("DEPRECATION")

package com.localmind.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmind.app.R
import com.localmind.app.domain.model.Conversation
import com.localmind.app.navigation.Routes
import com.localmind.app.ui.theme.NeonError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NavigationDrawerContent(
    currentRoute: String?,
    recentConversations: List<Conversation>,
    activeConversationId: String? = null,
    onNavigate: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit = { _, _ -> }
) {
    var renamingConversation by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingConversation by remember { mutableStateOf<Conversation?>(null) }

    val chatSelected = currentRoute?.startsWith(Routes.CHAT) == true
    val benchmarkSelected =
        currentRoute == Routes.BENCHMARK_HOME || currentRoute == Routes.BENCHMARK
    val modelLibrarySelected =
        currentRoute == Routes.MODEL_MANAGER ||
            currentRoute == Routes.MODEL_DETAIL ||
            currentRoute == Routes.ONLINE_MODEL_DETAIL

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.drawer_title),
            style = MaterialTheme.typography.headlineMedium,
            color = com.localmind.app.ui.theme.NeonPrimary,
            modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
        )

        DrawerItem(
            label = stringResource(R.string.drawer_chat),
            icon = Icons.Default.Chat,
            isSelected = chatSelected,
            onClick = {
                // Active conversation hai to wahi open karo, warna new chat
                if (activeConversationId != null) {
                    onConversationClick(activeConversationId)
                } else {
                    onNavigate(Routes.CHAT)
                }
            }
        )

        DrawerItem(
            label = stringResource(R.string.drawer_model_library),
            icon = Icons.Default.LibraryBooks,
            isSelected = modelLibrarySelected,
            onClick = { onNavigate(Routes.MODEL_MANAGER) }
        )

        DrawerItem(
            label = stringResource(R.string.drawer_import_model),
            icon = Icons.Default.FileUpload,
            isSelected = currentRoute == Routes.IMPORT_MODEL,
            onClick = { onNavigate(Routes.IMPORT_MODEL) }
        )

        DrawerItem(
            label = stringResource(R.string.drawer_benchmark),
            icon = Icons.Default.Star,
            isSelected = benchmarkSelected,
            onClick = { onNavigate(Routes.BENCHMARK_HOME) }
        )

        DrawerItem(
            label = "Pals (Personas)",
            icon = Icons.Default.SmartToy,
            isSelected = currentRoute == Routes.PERSONA_MANAGEMENT,
            onClick = { onNavigate(Routes.PERSONA_MANAGEMENT) }
        )

        DrawerItem(
            label = "Prompt Library",
            icon = Icons.Default.AutoAwesome,
            isSelected = currentRoute == Routes.PROMPT_TEMPLATE_MANAGER,
            onClick = { onNavigate(Routes.PROMPT_TEMPLATE_MANAGER) }
        )




        if (recentConversations.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))

            var showAll by remember { mutableStateOf(false) }
            val displayConversations = if (showAll) recentConversations else recentConversations.take(20)

            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val oneWeekAgo = today.minusDays(7)
            val thirtyDaysAgo = today.minusDays(30)

            val groupedConversations = displayConversations.groupBy { conversation ->
                val date = Instant.ofEpochMilli(conversation.updatedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                when {
                    date.isEqual(today) -> stringResource(R.string.drawer_today)
                    date.isEqual(yesterday) -> stringResource(R.string.drawer_yesterday)
                    date.isAfter(oneWeekAgo) -> stringResource(R.string.drawer_previous_7_days)
                    date.isAfter(thirtyDaysAgo) -> stringResource(R.string.drawer_previous_30_days)
                    else -> stringResource(R.string.drawer_older)
                }
            }

            groupedConversations.forEach { (header, conversations) ->
                Text(
                    text = header,
                    color = com.localmind.app.ui.theme.NeonPrimary,
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                conversations.forEach { conversation ->
                NavigationDrawerItem(
                label = {
                Text(
                text = conversation.title.ifBlank { stringResource(R.string.drawer_untitled_chat) },
                    maxLines = 1
                    )
                },
                selected = activeConversationId == conversation.id,
                onClick = { onConversationClick(conversation.id) },
                icon = null,
                badge = {
                Row {
                // Rename button
                    IconButton(
                    onClick = {
                    renamingConversation = conversation
                    renameText = conversation.title
                },
                modifier = Modifier.size(24.dp)
                ) {
                        Icon(
                                imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = com.localmind.app.ui.theme.NeonTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    // Delete button
                                    IconButton(
                                        onClick = { deletingConversation = conversation },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = NeonError,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = com.localmind.app.ui.theme.NeonPrimary.copy(alpha = 0.15f),
                            unselectedTextColor = com.localmind.app.ui.theme.NeonText.copy(alpha = 0.7f),
                            selectedTextColor = com.localmind.app.ui.theme.NeonPrimary,
                            unselectedIconColor = com.localmind.app.ui.theme.NeonText.copy(alpha = 0.7f),
                            selectedIconColor = com.localmind.app.ui.theme.NeonPrimary
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Show more / show less button
            if (recentConversations.size > 20) {
                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showAll) "Show less" else "Show all (${recentConversations.size})",
                        color = com.localmind.app.ui.theme.NeonPrimary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        DrawerItem(
            label = stringResource(R.string.drawer_settings),
            icon = Icons.Default.Settings,
            isSelected = currentRoute == Routes.SETTINGS,
            onClick = { onNavigate(Routes.SETTINGS) }
        )
    }

    // Delete confirmation dialog
    deletingConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { deletingConversation = null },
            title = { Text("Delete Chat?", color = com.localmind.app.ui.theme.NeonText) },
            text = {
                Text(
                    "\"${conv.title.ifBlank { "Untitled" }}\" permanently delete ho jaayega.",
                    color = com.localmind.app.ui.theme.NeonTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteConversation(conv.id); deletingConversation = null },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = NeonError
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingConversation = null }) {
                    Text("Cancel", color = com.localmind.app.ui.theme.NeonPrimary)
                }
            },
            containerColor = com.localmind.app.ui.theme.NeonSurface
        )
    }

    // Rename dialog
    renamingConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { renamingConversation = null },
            title = { Text("Rename Chat", color = com.localmind.app.ui.theme.NeonText) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.localmind.app.ui.theme.NeonPrimary,
                        unfocusedBorderColor = com.localmind.app.ui.theme.NeonTextSecondary,
                        focusedTextColor = com.localmind.app.ui.theme.NeonText,
                        unfocusedTextColor = com.localmind.app.ui.theme.NeonText
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameConversation(conv.id, renameText.trim())
                    }
                    renamingConversation = null
                }) {
                    Text("Save", color = com.localmind.app.ui.theme.NeonPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingConversation = null }) {
                    Text("Cancel", color = com.localmind.app.ui.theme.NeonTextSecondary)
                }
            },
            containerColor = com.localmind.app.ui.theme.NeonSurface
        )
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = isSelected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            selectedContainerColor = com.localmind.app.ui.theme.NeonPrimary.copy(alpha = 0.15f),
            unselectedTextColor = com.localmind.app.ui.theme.NeonText.copy(alpha = 0.7f),
            selectedTextColor = com.localmind.app.ui.theme.NeonPrimary,
            unselectedIconColor = com.localmind.app.ui.theme.NeonText.copy(alpha = 0.7f),
            selectedIconColor = com.localmind.app.ui.theme.NeonPrimary
        ),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
