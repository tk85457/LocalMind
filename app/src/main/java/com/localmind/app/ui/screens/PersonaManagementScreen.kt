package com.localmind.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.localmind.app.domain.model.Persona
import com.localmind.app.domain.model.PersonaContextMode
import com.localmind.app.ui.theme.*
import com.localmind.app.ui.viewmodel.PersonaManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonaManagementViewModel = hiltViewModel()
) {
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    var deleteTarget by remember { mutableStateOf<Persona?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Manage Pals", color = NeonText, fontWeight = FontWeight.Bold)
                },
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
                Icon(Icons.Default.Add, "Create Persona", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = NeonBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Featured / Built-in section
            item {
                Text(
                    "Built-in Pals",
                    color = NeonPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(Persona.FEATURED_PERSONAS + listOf(Persona.DEFAULT_ASSISTANT), key = { "builtin_${it.id}" }) { p ->
                PersonaCard(
                    persona = p,
                    isBuiltIn = true,
                    onEdit = { editingPersona = p.copy() },
                    onDelete = null
                )
            }

            // Custom personas
            if (personas.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your Custom Pals",
                        color = NeonPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(personas, key = { "custom_${it.id}" }) { p ->
                    PersonaCard(
                        persona = p,
                        isBuiltIn = false,
                        onEdit = { editingPersona = p },
                        onDelete = { deleteTarget = p }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Create / Edit Dialog
    if (showCreateDialog || editingPersona != null) {
        PersonaEditDialog(
            persona = editingPersona,
            onDismiss = {
                showCreateDialog = false
                editingPersona = null
            },
            onSave = { persona ->
                viewModel.savePersona(persona)
                showCreateDialog = false
                editingPersona = null
            }
        )
    }

    // Delete confirmation
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Pal?", color = NeonText) },
            text = { Text("Are you sure you want to delete \"${target.name}\"?", color = NeonTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePersona(target)
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
private fun PersonaCard(
    persona: Persona,
    isBuiltIn: Boolean,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
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
            Surface(
                shape = CircleShape,
                color = NeonPrimary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(persona.icon, fontSize = 24.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        persona.name,
                        color = NeonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (isBuiltIn) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Built-in",
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
                    persona.systemPrompt.take(80) + if (persona.systemPrompt.length > 80) "…" else "",
                    color = NeonTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = NeonPrimary, modifier = Modifier.size(20.dp))
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = NeonError, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditDialog(
    persona: Persona?,
    onDismiss: () -> Unit,
    onSave: (Persona) -> Unit
) {
    val isNew = persona == null
    var name by remember { mutableStateOf(persona?.name ?: "") }
    var icon by remember { mutableStateOf(persona?.icon ?: "🤖") }
    var systemPrompt by remember { mutableStateOf(persona?.systemPrompt ?: "") }
    var contextMode by remember { mutableStateOf(persona?.contextMode ?: PersonaContextMode.NONE) }

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
                    if (isNew) "Create New Pal" else "Edit Pal",
                    color = NeonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(Modifier.height(20.dp))

                // Icon picker row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NeonElevated,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(icon.ifBlank { "🤖" }, fontSize = 28.sp)
                        }
                    }
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { if (it.length <= 2) icon = it },
                        label = { Text("Icon (emoji)", color = NeonTextSecondary) },
                        modifier = Modifier.width(140.dp),
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
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Pal Name", color = NeonTextSecondary) },
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

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt", color = NeonTextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = NeonElevated,
                        focusedTextColor = NeonText,
                        unfocusedTextColor = NeonText,
                        focusedContainerColor = NeonElevated,
                        unfocusedContainerColor = NeonElevated
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Context Mode selector
                Text("Context Mode", color = NeonTextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersonaContextMode.entries.forEach { mode ->
                        FilterChip(
                            selected = contextMode == mode,
                            onClick = { contextMode = mode },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = NeonPrimary,
                                labelColor = NeonTextSecondary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = NeonTextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    // isBuiltIn: built-in personas ka ID hard-coded hota hai
                    // Agar user edit kare to new UUID generate karo — original ko overwrite mat karo
                    val isBuiltIn = persona != null &&
                        (com.localmind.app.domain.model.Persona.FEATURED_PERSONAS +
                         listOf(com.localmind.app.domain.model.Persona.DEFAULT_ASSISTANT))
                            .any { it.id == persona.id }

                    Button(
                        onClick = {
                            if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                                val baseId = if (isBuiltIn) java.util.UUID.randomUUID().toString()
                                             else persona?.id ?: java.util.UUID.randomUUID().toString()
                                val saved = Persona(
                                    id = baseId,
                                    name = name,
                                    icon = icon.ifBlank { "🤖" },
                                    systemPrompt = systemPrompt,
                                    contextMode = contextMode,
                                    staticContext = persona?.staticContext ?: "",
                                    preferredModelId = persona?.preferredModelId
                                )
                                onSave(saved)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = name.isNotBlank() && systemPrompt.isNotBlank()
                    ) {
                        Text(if (isNew) "Create" else "Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
