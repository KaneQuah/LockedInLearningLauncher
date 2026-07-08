package com.lockedinlearning.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockedinlearning.data.repository.DeckRepository
import com.lockedinlearning.data.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DayBar(val epochDay: Long, val count: Int, val isToday: Boolean)
data class DeckStat(val name: String, val masteryPercent: Int)

data class ProgressUiState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val weeklyData: List<DayBar> = emptyList(),
    val deckStats: List<DeckStat> = emptyList(),
    val totalCorrect: Int = 0,
    val bypasses: Int = 0
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val todayDay = System.currentTimeMillis() / 86_400_000L
        val daily = progressRepository.dailyCorrects(7)

        // Build 7-day bars
        val weekBars = (6 downTo 0).map { daysAgo ->
            val day = todayDay - daysAgo
            DayBar(day, daily[day] ?: 0, daysAgo == 0)
        }

        // Deck stats — take the first emission from the flow
        val decks = deckRepository.observeDecks().first()

        val deckStats = decks.map { deck ->
            val questions = deckRepository.getQuestionsByDeck(deck.id)
            val mastered = questions.count { it.masteryLevel == 2 }
            val pct = if (questions.isEmpty()) 0 else (mastered * 100 / questions.size)
            DeckStat(deck.name, pct)
        }

        _state.value = ProgressUiState(
            currentStreak = progressRepository.computeStreak(),
            bestStreak    = progressRepository.computeStreak(),   // v1: same as current
            weeklyData    = weekBars,
            deckStats     = deckStats,
            totalCorrect  = progressRepository.correctAnswersSince(0L),
            bypasses      = 0
        )
    }
}
