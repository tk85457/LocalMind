package com.localmind.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmind.app.R
import com.localmind.app.domain.model.Persona
import com.localmind.app.domain.model.PersonaContextMode
import com.localmind.app.ui.theme.NeonAccent
import com.localmind.app.ui.theme.NeonPrimary
import com.localmind.app.ui.theme.NeonSurface
import com.localmind.app.ui.theme.NeonTextTertiary
import com.localmind.app.ui.viewmodel.PersonaHubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaHubScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonaHubViewModel = hiltViewModel()
) {
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Persona Hub",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = NeonSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = NeonPrimary,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Persona")
            }
        },
        containerColor = com.localmind.app.ui.theme.NeonBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(personas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        isSelected = persona.id == selectedPersona?.id,
                        onSelect = { viewModel.selectPersona(it) },
                        onEdit = { editingPersona = it },
                        onDelete = { viewModel.deletePersona(it) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePersonaDialog(models = models,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, systemPrompt, icon, prefModelId ->
                viewModel.createPersona(
                    name = name,
                    icon = icon,
                    systemPrompt = systemPrompt,
                    contextMode = PersonaContextMode.NONE,
                    staticContext = "",
                    preferredModelId = prefModelId
                )
                showCreateDialog = false
            }
        )
    }

    editingPersona?.let { pEdit ->
        EditPersonaDialog(persona = pEdit, models = models,
            onDismiss = { editingPersona = null },
            onConfirm = { updatedPersona ->
                viewModel.updatePersona(updatedPersona)
                editingPersona = null
            }
        )
    }
}

@Composable
fun PersonaCard(
    persona: Persona,
    isSelected: Boolean,
    onSelect: (Persona) -> Unit,
    onEdit: (Persona) -> Unit = {},
    onDelete: (Persona) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(persona) }
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NeonSurface else NeonSurface.copy(alpha = 0.6f)
        ),
        border = if (isSelected) BorderStroke(2.dp, NeonPrimary) else BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = (if (isSelected) NeonPrimary else NeonAccent).copy(alpha = 0.15f),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(persona.icon, fontSize = 24.sp)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = persona.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (persona.id == "default_assistant") "System Default" else "Custom Pal",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) NeonPrimary else NeonTextTertiary
                    )
                }

                if (persona.id != "default_assistant") {
                    IconButton(onClick = { onEdit(persona) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = NeonPrimary.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = { onDelete(persona) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.3f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = persona.systemPrompt,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (persona.systemPrompt.length > 100) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (expanded) "Show Less" else "Read Full Prompt",
                        color = NeonPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePersonaDialog(
    models: List<com.localmind.app.domain.model.Model>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("🤖") }
    var preferredModelId by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val icons = listOf("🤖", "👨‍🏫", "👩‍⚕️", "🕵️", "👩‍🍳", "🧙", "🎨", "🎵", "🎮", "🚀")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assemble New Pal", color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Choose Icon", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    icons.forEach { icon ->
                        Text(
                            text = icon,
                            modifier = Modifier
                                .clickable { selectedIcon = icon }
                                .background(
                                    if (selectedIcon == icon) NeonPrimary.copy(alpha = 0.3f) else Color.Transparent,
                                    CircleShape
                                )
                                .padding(4.dp),
                            fontSize = 20.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Instructions") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Model Binding Dropdown (#9)
                Text("Pre-bind to Model (Optional)", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        val selectedName = models.find { it.id == preferredModelId }?.name ?: "No specific model (Default)"
                        Text(selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("No specific model (Default)") },
                            onClick = { preferredModelId = null; dropdownExpanded = false }
                        )
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = { preferredModelId = model.id; dropdownExpanded = false }
                            )
                        }
                    }
                }
                // Context Mode Explanation
                Text(
                    "Context Modes:",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "NONE - No conversation history sent (fastest, no memory)\n" +
                    "BASIC - Last few messages sent for context (balanced)\n" +
                    "FULL - Entire conversation sent (best coherence, slower)",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onConfirm(name, systemPrompt, selectedIcon, preferredModelId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary),
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank() && systemPrompt.isNotBlank()
            ) {
                Text("CREATE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = NeonPrimary)
            }
        },
        containerColor = NeonSurface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun EditPersonaDialog(
    persona: Persona,
    models: List<com.localmind.app.domain.model.Model>,
    onDismiss: () -> Unit,
    onConfirm: (Persona) -> Unit
) {
    var name by remember { mutableStateOf(persona.name) }
    var systemPrompt by remember { mutableStateOf(persona.systemPrompt) }
    var selectedIcon by remember { mutableStateOf(persona.icon) }
    var preferredModelId by remember { mutableStateOf(persona.preferredModelId) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val icons = listOf("🤖", "👨‍🏫", "👩‍⚕️", "🕵️", "👩‍🍳", "🧙", "🎨", "🎵", "🎮", "🚀")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Persona", color = Color.White, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Choose Icon", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    icons.forEach { icon ->
                        Text(
                            text = icon,
                            modifier = Modifier
                                .clickable { selectedIcon = icon }
                                .background(
                                    if (selectedIcon == icon) NeonPrimary.copy(alpha = 0.3f) else Color.Transparent,
                                    CircleShape
                                )
                                .padding(4.dp),
                            fontSize = 20.sp
                        )
                    }
                }

                // Model Binding Dropdown (#9)
                Text("Pre-bind to Model (Optional)", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        val selectedName = models.find { it.id == preferredModelId }?.name ?: "No specific model (Default)"
                        Text(selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("No specific model (Default)") },
                            onClick = { preferredModelId = null; dropdownExpanded = false }
                        )
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = { preferredModelId = model.id; dropdownExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Instructions") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onConfirm(persona.copy(
                            name = name.trim(),
                            icon = selectedIcon,
                            systemPrompt = systemPrompt.trim(),
                            preferredModelId = preferredModelId
                        ))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPrimary),
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank() && systemPrompt.isNotBlank()
            ) {
                Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = NeonPrimary)
            }
        },
        containerColor = NeonSurface,
        shape = RoundedCornerShape(24.dp)
    )
}
