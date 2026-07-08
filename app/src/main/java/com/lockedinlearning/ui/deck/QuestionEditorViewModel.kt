package com.lockedinlearning.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockedinlearning.data.repository.DeckRepository
import com.lockedinlearning.domain.model.Question
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestionEditorUiState(
    val deckName: String = "",
    val questions: List<Question> = emptyList()
)

@HiltViewModel
class QuestionEditorViewModel @Inject constructor(
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _state = MutableStateFlow(QuestionEditorUiState())
    val state: StateFlow<QuestionEditorUiState> = _state.asStateFlow()

    fun loadDeck(deckId: String) {
        viewModelScope.launch {
            val deck = deckRepository.getDeckById(deckId)
            _state.update { it.copy(deckName = deck?.name ?: "Questions") }
        }
        viewModelScope.launch {
            deckRepository.observeQuestions(deckId).collect { questions ->
                _state.update { it.copy(questions = questions) }
            }
        }
    }

    fun saveQuestion(question: Question) {
        viewModelScope.launch { deckRepository.saveQuestion(question) }
    }

    fun deleteQuestion(question: Question) {
        viewModelScope.launch { deckRepository.deleteQuestion(question) }
    }
}
