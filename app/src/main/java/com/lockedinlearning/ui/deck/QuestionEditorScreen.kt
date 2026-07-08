package com.lockedinlearning.ui.deck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lockedinlearning.domain.model.Question
import com.lockedinlearning.domain.model.QuestionType

// ---------------------------------------------------------------------------
// S-12 — Question Editor
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditorScreen(
    deckId: String,
    onBack: () -> Unit,
    viewModel: QuestionEditorViewModel = hiltViewModel()
) {
    LaunchedEffect(deckId) { viewModel.loadDeck(deckId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deckName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add question")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            items(state.questions) { question ->
                QuestionCard(
                    question = question,
                    onDelete = { viewModel.deleteQuestion(question) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showAddDialog) {
        AddQuestionDialog(
            deckId = deckId,
            onAdd = { q -> viewModel.saveQuestion(q); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun QuestionCard(question: Question, onDelete: () -> Unit) {
    val masteryLabel = when (question.masteryLevel) {
        0 -> "Unseen"
        1 -> "Learning"
        else -> "Mastered ⭐"
    }
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(question.prompt, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text("→ ${question.correctAnswer}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("${question.type.name} · $masteryLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddQuestionDialog(deckId: String, onAdd: (Question) -> Unit, onDismiss: () -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(QuestionType.FLASHCARD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Question") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Type selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestionType.values().forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("Question / prompt") },
                    singleLine = false, maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer, onValueChange = { answer = it },
                    label = { Text("Correct answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hint, onValueChange = { hint = it },
                    label = { Text("Hint (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        Question(
                            deckId = deckId,
                            type = selectedType,
                            prompt = prompt.trim(),
                            correctAnswer = answer.trim(),
                            hint = hint.trim().ifBlank { null }
                        )
                    )
                },
                enabled = prompt.isNotBlank() && answer.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
