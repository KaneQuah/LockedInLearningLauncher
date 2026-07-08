package com.lockedinlearning.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockedinlearning.data.datastore.GatePreferencesDataStore
import com.lockedinlearning.data.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { LAUNCHER_SETUP, CREATE_DECK, DONE }

data class OnboardingUiState(val step: OnboardingStep = OnboardingStep.LAUNCHER_SETUP)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefsStore: GatePreferencesDataStore,
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun advance() {
        val next = when (_state.value.step) {
            OnboardingStep.LAUNCHER_SETUP -> OnboardingStep.CREATE_DECK
            OnboardingStep.CREATE_DECK    -> OnboardingStep.DONE
            OnboardingStep.DONE           -> OnboardingStep.DONE
        }
        _state.update { it.copy(step = next) }
    }

    fun createSampleDeck() {
        viewModelScope.launch {
            val deck = deckRepository.createSampleDeck()
            prefsStore.setActiveDeckId(deck.id)
            prefsStore.setOnboardingComplete(true)
            _state.update { it.copy(step = OnboardingStep.DONE) }
        }
    }
}
