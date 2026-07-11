package com.lockedinlearning.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

import com.lockedinlearning.data.datastore.IconShape
import com.lockedinlearning.ui.deck.DeckManagerScreen
import com.lockedinlearning.ui.deck.QuestionEditorScreen
import com.lockedinlearning.ui.progress.ProgressScreen

// ---------------------------------------------------------------------------
// Settings nav host (PIN gate → Main settings → Disable picker)
// ---------------------------------------------------------------------------
@Composable
fun SettingsNavHost(
    state: SettingsUiState,
    onGateToggle: (Boolean) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onPinSet: (String) -> Unit,
    onPinVerify: (String) -> Unit,
    onPauseGate: (Float) -> Unit,
    onActiveDeckChange: (String) -> Unit,
    onEditDeck: (String) -> Unit,
    onIconShapeChange: (IconShape) -> Unit,
    onIconSizeChange: (Float) -> Unit,
    onShowLabelsToggle: (Boolean) -> Unit,
    onNavigate: (SettingsScreen) -> Unit,
    onNavigateBack: () -> Unit,
    onBack: () -> Unit
) {
    if (state.screen != SettingsScreen.PIN_GATE && state.screen != SettingsScreen.MAIN &&
        state.screen != SettingsScreen.PROGRESS
    ) {
        BackHandler(onBack = onNavigateBack)
    }

    when (state.screen) {
        SettingsScreen.PIN_GATE -> PinGateScreen(
            error = state.pinError,
            onVerify = onPinVerify,
            onBack = onBack
        )
        SettingsScreen.MAIN -> MainSettingsScreen(
            state = state,
            onGateToggle = onGateToggle,
            onCooldownChange = onCooldownChange,
            onPinSet = onPinSet,
            onPauseGate = onPauseGate,
            onActiveDeckChange = onActiveDeckChange,
            onManageDecks = { onNavigate(SettingsScreen.DECK_MANAGER) },
            onIconShapeChange = onIconShapeChange,
            onIconSizeChange = onIconSizeChange,
            onShowLabelsToggle = onShowLabelsToggle,
            onBack = onBack
        )
        SettingsScreen.DISABLE_PICKER -> DisablePicker(
            onConfirm = onPauseGate,
            onBack = onNavigateBack
        )
        SettingsScreen.DECK_MANAGER -> DeckManagerScreen(
            onBack = onNavigateBack,
            onOpenDeck = onEditDeck
        )
        SettingsScreen.QUESTION_EDITOR -> QuestionEditorScreen(
            deckId = state.editingDeckId ?: "",
            onBack = onNavigateBack
        )
        SettingsScreen.PROGRESS -> ProgressScreen(onBack = onBack)
    }
}

// ---------------------------------------------------------------------------
// S-14 — PIN gate
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinGateScreen(error: Boolean, onVerify: (String) -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter PIN") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔐", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) pin = it },
                label = { Text("Admin PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error,
                supportingText = if (error) {{ Text("Incorrect PIN") }} else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onVerify(pin) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Unlock Settings")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// S-13 — Main settings
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    state: SettingsUiState,
    onGateToggle: (Boolean) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onPinSet: (String) -> Unit,
    onPauseGate: (Float) -> Unit,
    onActiveDeckChange: (String) -> Unit,
    onManageDecks: () -> Unit,
    onIconShapeChange: (IconShape) -> Unit,
    onIconSizeChange: (Float) -> Unit,
    onShowLabelsToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // Gate toggle
            SectionHeader("Gate")
            SettingsRow(
                label = "Gate enabled",
                subtitle = "Show a question before every home screen access"
            ) {
                Switch(checked = state.gateEnabled, onCheckedChange = onGateToggle)
            }

            // Cooldown
            SettingsRow(
                label = "Cooldown",
                subtitle = "Minutes before gate shows again after correct answer"
            ) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = "${state.cooldownMinutes} min",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.width(100.dp).menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(1, 3, 5, 10, 15, 30).forEach { m ->
                            DropdownMenuItem(
                                text = { Text("$m min") },
                                onClick = { onCooldownChange(m); expanded = false }
                            )
                        }
                    }
                }
            }

            // Home Screen
            SectionHeader("Home Screen")
            SettingsRow(
                label = "Icon shape",
                subtitle = "Applies to home, dock, and folder icons"
            ) {
                var shapeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = shapeExpanded, onExpandedChange = { shapeExpanded = it }) {
                    OutlinedTextField(
                        value = state.iconShape.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.width(160.dp).menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = shapeExpanded, onDismissRequest = { shapeExpanded = false }) {
                        IconShape.entries.forEach { shape ->
                            DropdownMenuItem(
                                text = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = { onIconShapeChange(shape); shapeExpanded = false }
                            )
                        }
                    }
                }
            }
            SettingsRow(
                label = "Icon size",
                subtitle = "${(state.iconSizeScale * 100).toInt()}%"
            ) {
                Slider(
                    value = state.iconSizeScale,
                    onValueChange = onIconSizeChange,
                    valueRange = 0.8f..1.3f,
                    modifier = Modifier.width(160.dp)
                )
            }
            SettingsRow(
                label = "Show icon labels",
                subtitle = "Show app names under icons"
            ) {
                Switch(checked = state.showIconLabels, onCheckedChange = onShowLabelsToggle)
            }

            // Active deck
            SectionHeader("Active Deck")
            var deckExpanded by remember { mutableStateOf(false) }
            val activeDeck = state.decks.find { it.id == state.activeDeckId }
            ExposedDropdownMenuBox(expanded = deckExpanded, onExpandedChange = { deckExpanded = it }) {
                OutlinedTextField(
                    value = activeDeck?.name ?: "None selected",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor().padding(vertical = 8.dp),
                    label = { Text("Active deck") }
                )
                ExposedDropdownMenu(expanded = deckExpanded, onDismissRequest = { deckExpanded = false }) {
                    state.decks.forEach { deck ->
                        DropdownMenuItem(
                            text = { Text(deck.name) },
                            onClick = { onActiveDeckChange(deck.id); deckExpanded = false }
                        )
                    }
                }
            }
            SettingsButton("Manage Decks", "Import or create question banks") {
                onManageDecks()
            }

            // Admin
            SectionHeader("Administration")
            SettingsButton("Set Admin PIN", "Protect settings with a 4–8 digit PIN") {
                showPinDialog = true
            }
            SettingsButton("Pause Gate", "Temporarily disable the gate") {
                showPauseSheet = true
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // PIN dialog
    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set Admin PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8) newPin = it },
                    label = { Text("New PIN (leave blank to remove)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { onPinSet(newPin); showPinDialog = false }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Pause sheet (S-15)
    if (showPauseSheet) {
        DisablePicker(onConfirm = { hours -> onPauseGate(hours); showPauseSheet = false }, onBack = { showPauseSheet = false })
    }
}

// ---------------------------------------------------------------------------
// S-15 — Disable gate duration picker
// ---------------------------------------------------------------------------
@Composable
fun DisablePicker(onConfirm: (Float) -> Unit, onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Pause Gate") },
        text = {
            Column {
                listOf(1f to "1 hour", 4f to "4 hours", 24f to "Until tomorrow").forEach { (h, label) ->
                    TextButton(
                        onClick = { onConfirm(h) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onBack) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
    HorizontalDivider()
}

@Composable
private fun SettingsRow(label: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun SettingsButton(label: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text("Configure") }
    }
}
