package com.lockedinlearning.domain

import com.lockedinlearning.data.datastore.GatePreferencesDataStore
import com.lockedinlearning.data.repository.DeckRepository
import com.lockedinlearning.data.repository.ProgressRepository
import com.lockedinlearning.domain.evaluator.EvaluatorFactory
import com.lockedinlearning.domain.model.AnswerEvent
import com.lockedinlearning.domain.model.GateResult
import com.lockedinlearning.domain.model.Question
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a single gate session.
 *
 * Call [shouldShowGate] first; if true, call [loadQuestion],
 * then [submitAnswer] for each user attempt.
 */
@Singleton
class GateController @Inject constructor(
    private val prefsStore: GatePreferencesDataStore,
    private val deckRepository: DeckRepository,
    private val progressRepository: ProgressRepository,
    private val questionSelector: QuestionSelector,
    private val failurePolicyEngine: FailurePolicyEngine
) {
    private var currentQuestion: Question? = null
    private var attemptsUsed: Int = 0
    private var sessionStart: Long = 0L
    private var hintUsed: Boolean = false
    private var hintAllowedForCurrent: Boolean = true

    /** Returns true if a gate screen should be presented to the user. */
    suspend fun shouldShowGate(): Boolean {
        val prefs = prefsStore.prefsFlow.first()

        // Gate disabled entirely
        if (!prefs.gateEnabled) return false

        // Temporarily disabled
        if (System.currentTimeMillis() < prefs.disableUntilEpoch) return false

        // No active deck
        if (prefs.activeDeckId.isBlank()) return false

        // Cooldown — if a correct answer was given recently, don't show
        val cooldownMs = prefs.cooldownMinutes * 60_000L
        val correctRecently = progressRepository.correctAnswersSince(
            System.currentTimeMillis() - cooldownMs
        ) > 0
        if (correctRecently) return false

        return true
    }

    /** Loads and caches the next question for the current session. */
    suspend fun loadQuestion(): Question? {
        val prefs = prefsStore.prefsFlow.first()
        val questions = deckRepository.getQuestionsByDeck(prefs.activeDeckId)
        val prefs2 = prefsStore.prefsFlow.first()
        val cooldownMs = prefs2.cooldownMinutes * 60_000L
        currentQuestion = questionSelector.select(questions, cooldownMs)
        attemptsUsed = 0
        hintUsed = false
        hintAllowedForCurrent = currentQuestion?.let { deckRepository.getDeckById(it.deckId)?.failurePolicy?.hintAllowed } ?: true
        sessionStart = System.currentTimeMillis()
        return currentQuestion
    }

    /** Whether the active deck's failure policy permits revealing a hint. */
    fun hintAllowed(): Boolean = hintAllowedForCurrent

    /** Call when the user reveals the hint. */
    fun markHintUsed() {
        hintUsed = true
    }

    /**
     * Evaluate [rawAnswer] against the current question.
     * Returns a [GateResult] and records the event.
     */
    suspend fun submitAnswer(rawAnswer: String): GateResult {
        val question = currentQuestion ?: return GateResult.NoQuestion
        val prefs = prefsStore.prefsFlow.first()
        val deck = deckRepository.getDeckById(question.deckId) ?: return GateResult.NoQuestion
        val evaluator = EvaluatorFactory.forQuestion(question)
        val evalResult = evaluator.evaluate(question, rawAnswer)

        val responseMs = System.currentTimeMillis() - sessionStart
        progressRepository.recordEvent(
            AnswerEvent(
                questionId = question.id,
                deckId = question.deckId,
                correct = evalResult.correct,
                hintUsed = hintUsed,
                responseTimeMs = responseMs
            )
        )

        if (evalResult.correct) {
            // Advance mastery
            val newConsecutive = question.consecutiveCorrect + 1
            val newLevel = when {
                newConsecutive >= 5 -> 2 // mastered
                newConsecutive >= 2 -> 1 // learning
                else -> 0
            }
            deckRepository.updateMastery(question.id, newLevel, newConsecutive)
            return GateResult.Pass
        }

        // Wrong answer — reset mastery streak
        deckRepository.updateMastery(question.id, minOf(question.masteryLevel, 1), 0)
        attemptsUsed++
        return failurePolicyEngine.evaluate(deck.failurePolicy, attemptsUsed)
    }

    /** Get current question without side-effects. */
    fun currentQuestion(): Question? = currentQuestion
}
