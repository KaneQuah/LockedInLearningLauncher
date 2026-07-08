package com.lockedinlearning.ui.deck

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockedinlearning.data.repository.DeckRepository
import com.lockedinlearning.domain.CsvImporter
import com.lockedinlearning.domain.model.Deck
import com.lockedinlearning.domain.model.FailureMode
import com.lockedinlearning.domain.model.FailurePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DeckUiState(
    val decks: List<Deck> = emptyList(),
    val questionCounts: Map<String, Int> = emptyMap(),
    val selectedDeckId: String? = null,
    val importResult: String? = null
)

@HiltViewModel
class DeckViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val csvImporter: CsvImporter
) : ViewModel() {

    private val _state = MutableStateFlow(DeckUiState())
    val state: StateFlow<DeckUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            deckRepository.observeDecks().collect { decks ->
                val counts = decks.associate { d ->
                    d.id to deckRepository.getQuestionsByDeck(d.id).size
                }
                _state.update { it.copy(decks = decks, questionCounts = counts) }
            }
        }
    }

    fun selectDeck(deckId: String) = _state.update { it.copy(selectedDeckId = deckId) }

    fun createDeck(name: String, policyMode: String) {
        viewModelScope.launch {
            val mode = runCatching { FailureMode.valueOf(policyMode) }.getOrDefault(FailureMode.RETRY)
            val deck = Deck(
                id = UUID.randomUUID().toString(),
                name = name,
                failurePolicy = FailurePolicy(mode = mode)
            )
            deckRepository.saveDeck(deck)
        }
    }

    fun deleteDeck(deck: Deck) {
        viewModelScope.launch { deckRepository.deleteDeck(deck) }
    }

    fun importCsv(uri: Uri, deckId: String) {
        if (deckId.isBlank()) return
        viewModelScope.launch {
            val result = csvImporter.import(uri, deckId)
            deckRepository.saveQuestions(result.questions)
            val msg = "Imported ${result.questions.size} questions" +
                (if (result.skipped > 0) ", skipped ${result.skipped}" else "")
            _state.update { it.copy(importResult = msg) }
        }
    }

    fun clearImportResult() = _state.update { it.copy(importResult = null) }

    fun updateDeckPolicy(deck: Deck, policy: FailurePolicy) {
        viewModelScope.launch { deckRepository.saveDeck(deck.copy(failurePolicy = policy)) }
    }
}
