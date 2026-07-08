package com.lockedinlearning.ui.deck

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lockedinlearning.domain.model.Deck
import com.lockedinlearning.domain.model.FailureMode
import com.lockedinlearning.domain.model.FailurePolicy

// ---------------------------------------------------------------------------
// S-11 — Deck Manager
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckManagerScreen(
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit,
    viewModel: DeckViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPolicyDeck by remember { mutableStateOf<Deck?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importCsv(it, state.selectedDeckId ?: "") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decks") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New deck")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            items(state.decks) { deck ->
                DeckCard(
                    deck = deck,
                    questionCount = state.questionCounts[deck.id] ?: 0,
                    onOpen = { onOpenDeck(deck.id) },
                    onDelete = { viewModel.deleteDeck(deck) },
                    onImportCsv = { viewModel.selectDeck(deck.id); csvLauncher.launch("text/*") },
                    onEditPolicy = { editingPolicyDeck = deck }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showCreateDialog) {
        CreateDeckDialog(
            onCreate = { name, policy -> viewModel.createDeck(name, policy); showCreateDialog = false },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingPolicyDeck?.let { deck ->
        EditPolicyDialog(
            deck = deck,
            onSave = { policy ->
                viewModel.updateDeckPolicy(deck, policy)
                editingPolicyDeck = null
            },
            onDismiss = { editingPolicyDeck = null }
        )
    }
}

@Composable
private fun DeckCard(
    deck: Deck,
    questionCount: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onImportCsv: () -> Unit,
    onEditPolicy: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium)
                Text("$questionCount questions · ${deck.failurePolicy.mode.displayName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit questions") },
                        onClick = { onOpen(); menuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem(text = { Text("Edit failure policy") },
                        onClick = { onEditPolicy(); menuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Settings, null) })
                    DropdownMenuItem(text = { Text("Import CSV") },
                        onClick = { onImportCsv(); menuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Upload, null) })
                    DropdownMenuItem(text = { Text("Delete") },
                        onClick = { onDelete(); menuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
    }
}

@Composable
private fun CreateDeckDialog(onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var policy by remember { mutableStateOf("RETRY") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Failure policy", style = MaterialTheme.typography.labelMedium)
                FailureMode.values().forEach { mode ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = policy == mode.name, onClick = { policy = mode.name })
                        Text(mode.displayName(), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, policy) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------
// Edit failure policy dialog
// ---------------------------------------------------------------------------
@Composable
private fun EditPolicyDialog(
    deck: Deck,
    onSave: (FailurePolicy) -> Unit,
    onDismiss: () -> Unit
) {
    val existing = deck.failurePolicy
    var mode        by remember { mutableStateOf(existing.mode) }
    var maxAttempts by remember { mutableStateOf(
        if (existing.maxAttempts == Int.MAX_VALUE) "3" else existing.maxAttempts.toString()
    ) }
    var penaltySecs by remember { mutableStateOf(existing.penaltySeconds.toString()) }
    var lockoutMins by remember { mutableStateOf(existing.lockoutMinutes.toString()) }
    var bypassMsg   by remember { mutableStateOf(existing.bypassMessage) }
    var hintAllowed by remember { mutableStateOf(existing.hintAllowed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Failure Policy — ${deck.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode picker
                Text("On wrong answer", style = MaterialTheme.typography.labelMedium)
                FailureMode.values().forEach { fm ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = mode == fm, onClick = { mode = fm })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(fm.displayName(), style = MaterialTheme.typography.bodyMedium)
                            Text(fm.description(), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider()

                // Conditional fields
                when (mode) {
                    FailureMode.MAX_ATTEMPTS -> {
                        OutlinedTextField(
                            value = maxAttempts,
                            onValueChange = { maxAttempts = it.filter { c -> c.isDigit() } },
                            label = { Text("Max attempts") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bypassMsg,
                            onValueChange = { bypassMsg = it },
                            label = { Text("Bypass message (shown after all attempts)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    FailureMode.TIME_PENALTY -> {
                        OutlinedTextField(
                            value = penaltySecs,
                            onValueChange = { penaltySecs = it.filter { c -> c.isDigit() } },
                            label = { Text("Penalty duration (seconds)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    FailureMode.HARD_LOCK -> {
                        OutlinedTextField(
                            value = lockoutMins,
                            onValueChange = { lockoutMins = it.filter { c -> c.isDigit() } },
                            label = { Text("Lockout duration (minutes)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    FailureMode.RETRY -> { /* no extra fields */ }
                }

                HorizontalDivider()

                // Hint toggle (applies to all modes)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow hints", style = MaterialTheme.typography.bodyMedium)
                        Text("User can reveal a hint before answering",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = hintAllowed, onCheckedChange = { hintAllowed = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val policy = FailurePolicy(
                    mode           = mode,
                    maxAttempts    = if (mode == FailureMode.MAX_ATTEMPTS)
                        maxAttempts.toIntOrNull()?.coerceAtLeast(1) ?: 3
                        else Int.MAX_VALUE,
                    penaltySeconds = penaltySecs.toIntOrNull()?.coerceAtLeast(5) ?: 30,
                    lockoutMinutes = lockoutMins.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                    bypassMessage  = bypassMsg.ifBlank { "Better luck next time 👀" },
                    hintAllowed    = hintAllowed
                )
                onSave(policy)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun FailureMode.displayName() = when (this) {
    FailureMode.RETRY        -> "Retry until correct"
    FailureMode.MAX_ATTEMPTS -> "Max attempts"
    FailureMode.TIME_PENALTY -> "Time penalty"
    FailureMode.HARD_LOCK    -> "Hard lock"
}

private fun FailureMode.description() = when (this) {
    FailureMode.RETRY        -> "Unlimited retries, always let through eventually"
    FailureMode.MAX_ATTEMPTS -> "Limited tries, then bypass with a shame message"
    FailureMode.TIME_PENALTY -> "Wrong answer starts a countdown before retry"
    FailureMode.HARD_LOCK    -> "Lock phone access for N minutes on failure"
}
