@file:Suppress("DEPRECATION")

package com.tk854.localmind.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk854.localmind.R
import com.tk854.localmind.domain.model.Conversation
import com.tk854.localmind.navigation.Routes
import com.tk854.localmind.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun NavigationDrawerContent(
    currentRoute: String?,
    recentConversations: List<Conversation>,
    activeConversationId: String? = null,
    onNavigate: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {}
) {
    var renamingConversation by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingConversation by remember { mutableStateOf<Conversation?>(null) }

    val chatSelected = currentRoute?.startsWith(Routes.CHAT) == true
    val benchmarkSelected = currentRoute == Routes.BENCHMARK_HOME || currentRoute == Routes.BENCHMARK
    val modelLibrarySelected = currentRoute == Routes.MODEL_MANAGER ||
            currentRoute == Routes.MODEL_DETAIL ||
            currentRoute == Routes.ONLINE_MODEL_DETAIL

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonSurface)
            .padding(16.dp)
    ) {
        // Top Header: ChatGPT style "+ New chat" prominent button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, top = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = ChatInputBackground,
            border = BorderStroke(1.dp, ChatInputBorder),
            onClick = {
                onNavigate(Routes.CHAT)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = NeonText,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "New chat",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NeonText
                    )
                }

                // Small App Mark
                Surface(
                    shape = CircleShape,
                    color = NeonPrimary.copy(alpha = 0.12f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("AI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonPrimary)
                    }
                }
            }
        }

        // Scrollable middle section: Navigation Links + Chat History
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Main Navigation Links
            DrawerItem(
                label = stringResource(R.string.drawer_chat),
                icon = Icons.Default.ChatBubbleOutline,
                isSelected = chatSelected,
                onClick = {
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
                icon = Icons.Default.Speed,
                isSelected = benchmarkSelected,
                onClick = { onNavigate(Routes.BENCHMARK_HOME) }
            )


            DrawerItem(
                label = "Prompt Library",
                icon = Icons.Default.AutoAwesome,
                isSelected = currentRoute == Routes.PROMPT_TEMPLATE_MANAGER,
                onClick = { onNavigate(Routes.PROMPT_TEMPLATE_MANAGER) }
            )

            // Chat History section
            if (recentConversations.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))

                var showAll by remember { mutableStateOf(false) }
                val displayConversations = if (showAll) recentConversations else recentConversations.take(25)

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
                        color = NeonTextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    conversations.forEach { conversation ->
                        val isSelected = activeConversationId == conversation.id
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = conversation.title.ifBlank { stringResource(R.string.drawer_untitled_chat) },
                                    maxLines = 1,
                                    fontSize = 13.5.sp
                                )
                            },
                            selected = isSelected,
                            onClick = { onConversationClick(conversation.id) },
                            icon = null,
                            badge = {
                                if (isSelected) {
                                    Row {
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
                                                tint = NeonTextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { deletingConversation = conversation },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = NeonTextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                selectedContainerColor = ChatInputBackground,
                                unselectedTextColor = NeonText.copy(alpha = 0.85f),
                                selectedTextColor = NeonText,
                                unselectedIconColor = NeonTextSecondary,
                                selectedIconColor = NeonText
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                if (recentConversations.size > 25) {
                    TextButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showAll) "Show less" else "Show all (${recentConversations.size})",
                            color = NeonTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Bottom Footer: Theme Switcher & Settings (ChatGPT style)
        HorizontalDivider(color = NeonDivider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        // Direct Dark / Light Mode Switcher row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            onClick = onToggleDarkMode
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = NeonText,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isDarkMode) "Light mode" else "Dark mode",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = NeonText
                    )
                }

                // Switch Indicator
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleDarkMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPrimary,
                        uncheckedThumbColor = NeonTextSecondary,
                        uncheckedTrackColor = ChatInputBackground
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        // Settings row
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
            title = { Text("Delete chat?", color = NeonText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will delete \"${conv.title.ifBlank { "Untitled" }}\".",
                    color = NeonTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteConversation(conv.id); deletingConversation = null },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonError)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingConversation = null }) {
                    Text("Cancel", color = NeonText)
                }
            },
            containerColor = NeonSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Rename dialog
    renamingConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { renamingConversation = null },
            title = { Text("Rename chat", color = NeonText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = ChatInputBorder,
                        focusedTextColor = NeonText,
                        unfocusedTextColor = NeonText,
                        focusedContainerColor = ChatInputBackground,
                        unfocusedContainerColor = ChatInputBackground
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameConversation(conv.id, renameText.trim())
                        }
                        renamingConversation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonText)
                ) {
                    Text("Save", color = NeonBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingConversation = null }) {
                    Text("Cancel", color = NeonTextSecondary)
                }
            },
            containerColor = NeonSurface,
            shape = RoundedCornerShape(16.dp)
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
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            )
        },
        selected = isSelected,
        onClick = onClick,
        icon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            selectedContainerColor = ChatInputBackground,
            unselectedTextColor = NeonText.copy(alpha = 0.85f),
            selectedTextColor = NeonText,
            unselectedIconColor = NeonTextSecondary,
            selectedIconColor = NeonText
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
