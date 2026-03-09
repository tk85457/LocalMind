package com.localmind.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.data.local.entity.PromptTemplateEntity
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.PromptTemplateViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplateManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PromptTemplateViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<PromptTemplateEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PromptTemplateEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompt Library", color = NeonText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NeonText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonSurface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = NeonPrimary,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, "Add Template", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = NeonBackground
    ) { padding ->
        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = NeonTextExtraMuted
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No Prompt Templates Yet",
                        color = NeonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to create your first prompt template",
                        color = NeonTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    PromptTemplateCard(
                        template = template,
                        onEdit = { editingTemplate = template },
                        onDelete = { deleteTarget = template }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showCreateDialog || editingTemplate != null) {
        PromptTemplateEditDialog(
            template = editingTemplate,
            onDismiss = {
                showCreateDialog = false
                editingTemplate = null
            },
            onSave = { template ->
                viewModel.saveTemplate(template)
                showCreateDialog = false
                editingTemplate = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Template?", color = NeonText) },
            text = { Text("Delete \"${target.title}\"?", color = NeonTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTemplate(target)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonError)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = NeonPrimary)
                }
            },
            containerColor = NeonSurface,
            titleContentColor = NeonText,
            textContentColor = NeonTextSecondary
        )
    }
}

@Composable
private fun PromptTemplateCard(
    template: PromptTemplateEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeonElevated),
        border = BorderStroke(1.dp, NeonTextExtraMuted.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        template.title,
                        color = NeonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    template.category?.let { cat ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                cat,
                                color = NeonPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    template.content.take(100) + if (template.content.length > 100) "…" else "",
                    color = NeonTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = NeonPrimary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = NeonError, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun PromptTemplateEditDialog(
    template: PromptTemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (PromptTemplateEntity) -> Unit
) {
    val isNew = template == null
    var title by remember { mutableStateOf(template?.title ?: "") }
    var content by remember { mutableStateOf(template?.content ?: "") }
    var category by remember { mutableStateOf(template?.category ?: "") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeonSurface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (isNew) "New Prompt Template" else "Edit Template",
                    color = NeonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", color = NeonTextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = NeonElevated,
                        focusedTextColor = NeonText,
                        unfocusedTextColor = NeonText,
                        focusedContainerColor = NeonElevated,
                        unfocusedContainerColor = NeonElevated
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (optional)", color = NeonTextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = NeonElevated,
                        focusedTextColor = NeonText,
                        unfocusedTextColor = NeonText,
                        focusedContainerColor = NeonElevated,
                        unfocusedContainerColor = NeonElevated
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Prompt Content", color = NeonTextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = NeonElevated,
                        focusedTextColor = NeonText,
                        unfocusedTextColor = NeonText,
                        focusedContainerColor = NeonElevated,
                        unfocusedContainerColor = NeonElevated
                    )
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = NeonTextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && content.isNotBlank()) {
                                val saved = PromptTemplateEntity(
                                    id = template?.id ?: UUID.randomUUID().toString(),
                                    title = title.trim(),
                                    content = content.trim(),
                                    category = category.trim().takeIf { it.isNotBlank() },
                                    timestamp = template?.timestamp ?: System.currentTimeMillis()
                                )
                                onSave(saved)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = title.isNotBlank() && content.isNotBlank()
                    ) {
                        Text(
                            if (isNew) "Create" else "Save",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
