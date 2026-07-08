package com.lockedinlearning.ui.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lockedinlearning.domain.GateController
import com.lockedinlearning.domain.evaluator.FLASHCARD_SELF_CORRECT
import com.lockedinlearning.domain.evaluator.FLASHCARD_SELF_WRONG
import com.lockedinlearning.domain.model.GateResult
import com.lockedinlearning.domain.model.Question
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GateUiState {
    object Loading        : GateUiState()
    object Skip           : GateUiState()   // gate not needed this unlock
    object NoQuestion     : GateUiState()   // deck empty
    data class Question(val q: com.lockedinlearning.domain.model.Question,
                        val attemptsUsed: Int = 0,
                        val maxAttempts: Int = Int.MAX_VALUE,
                        val showHint: Boolean = false,
                        val hintAllowed: Boolean = true) : GateUiState()
    data class Correct(val answer: String, val todayCount: Int, val streak: Int) : GateUiState()
    data class Wrong(val attemptsUsed: Int, val maxAttempts: Int)  : GateUiState()
    data class Penalty(val secondsRemaining: Int)                   : GateUiState()
    data class Bypass(val message: String, val question: String, val correctAnswer: String) : GateUiState()
    data class Lockout(val lockedUntilEpoch: Long)                  : GateUiState()
}

@HiltViewModel
class GateViewModel @Inject constructor(
    private val gateController: GateController
) : ViewModel() {

    private val _state = MutableStateFlow<GateUiState>(GateUiState.Loading)
    val state: StateFlow<GateUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { initGate() }
    }

    /** Called by HomeActivity.onResume to re-evaluate gate state. */
    fun reload() {
        viewModelScope.launch { initGate() }
    }

    /**
     * "Do More" — voluntarily start a practice question, bypassing the
     * shouldShowGate() cooldown check. Correct answers count toward the
     * daily goal and streak exactly like gated answers.
     */
    fun startPractice() {
        viewModelScope.launch {
            _state.value = GateUiState.Loading
            val question = gateController.loadQuestion()
            _state.value = if (question == null) GateUiState.NoQuestion
                           else GateUiState.Question(q = question, hintAllowed = gateController.hintAllowed())
        }
    }

    private suspend fun initGate() {
        if (!gateController.shouldShowGate()) {
            _state.value = GateUiState.Skip
            return
        }
        val question = gateController.loadQuestion()
        if (question == null) {
            _state.value = GateUiState.NoQuestion
            return
        }
        _state.value = GateUiState.Question(q = question, hintAllowed = gateController.hintAllowed())
    }

    fun submitAnswer(rawAnswer: String) {
        val current = _state.value as? GateUiState.Question ?: return
        viewModelScope.launch {
            when (val result = gateController.submitAnswer(rawAnswer)) {
                is GateResult.Pass -> {
                    _state.value = GateUiState.Correct(
                        answer = rawAnswer,
                        todayCount = 0,  // ProgressTracker will supply via Flow in HomeViewModel
                        streak = 0
                    )
                    delay(350)
                    _state.value = GateUiState.Skip  // signal HomeActivity to show home grid
                }
                is GateResult.FailRetry -> {
                    _state.value = GateUiState.Wrong(result.attemptsUsed, result.maxAttempts)
                    delay(1500)
                    _state.value = GateUiState.Question(
                        q = current.q,
                        attemptsUsed = result.attemptsUsed,
                        maxAttempts = result.maxAttempts,
                        hintAllowed = current.hintAllowed
                    )
                }
                is GateResult.FailPenalty -> {
                    startPenaltyCountdown(result.waitSeconds, current.q, current.attemptsUsed + 1, current.maxAttempts, current.hintAllowed)
                }
                is GateResult.FailBypass -> {
                    _state.value = GateUiState.Bypass(
                        message = result.shameMessage,
                        question = current.q.prompt,
                        correctAnswer = current.q.correctAnswer
                    )
                }
                is GateResult.FailLockout -> {
                    _state.value = GateUiState.Lockout(result.lockedUntilEpoch)
                    startLockoutCountdown(result.lockedUntilEpoch, current.q, current.hintAllowed)
                }
                GateResult.NoQuestion -> _state.value = GateUiState.NoQuestion
            }
        }
    }

    fun selectMcqOption(option: String) = submitAnswer(option)

    /** For FLASHCARD flip-card: user self-grades after seeing the answer. */
    fun gradeFlashcard(gotIt: Boolean) {
        submitAnswer(if (gotIt) FLASHCARD_SELF_CORRECT else FLASHCARD_SELF_WRONG)
    }

    fun revealHint() {
        gateController.markHintUsed()
        val current = _state.value as? GateUiState.Question ?: return
        _state.value = current.copy(showHint = true)
    }

    fun bypass() {
        viewModelScope.launch {
            _state.value = GateUiState.Skip
        }
    }

    private fun startPenaltyCountdown(seconds: Int, question: com.lockedinlearning.domain.model.Question,
                                      attemptsUsed: Int, maxAttempts: Int, hintAllowed: Boolean) {
        viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _state.value = GateUiState.Penalty(remaining)
                delay(1000)
            }
            _state.value = GateUiState.Question(q = question, attemptsUsed = attemptsUsed, maxAttempts = maxAttempts, hintAllowed = hintAllowed)
        }
    }

    private fun startLockoutCountdown(lockedUntilEpoch: Long, question: com.lockedinlearning.domain.model.Question, hintAllowed: Boolean) {
        viewModelScope.launch {
            while (System.currentTimeMillis() < lockedUntilEpoch) {
                _state.value = GateUiState.Lockout(lockedUntilEpoch)
                delay(1000)
            }
            _state.value = GateUiState.Question(q = question, hintAllowed = hintAllowed)
        }
    }
}
