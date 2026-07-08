package com.lockedinlearning.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

// ---------------------------------------------------------------------------
// Question types
// ---------------------------------------------------------------------------
enum class QuestionType {
    FLASHCARD, MATH, MCQ, LANGUAGE
}

// ---------------------------------------------------------------------------
// Failure policy
// ---------------------------------------------------------------------------
enum class FailureMode {
    RETRY, MAX_ATTEMPTS, TIME_PENALTY, HARD_LOCK
}

@Serializable
data class FailurePolicy(
    val mode: FailureMode = FailureMode.RETRY,
    val maxAttempts: Int = Int.MAX_VALUE,
    val penaltySeconds: Int = 30,
    val lockoutMinutes: Int = 5,
    val bypassMessage: String = "Better luck next time 👀",
    val hintAllowed: Boolean = true
)

// Presets for onboarding
object FailurePolicyPresets {
    val Easy   = FailurePolicy(mode = FailureMode.RETRY, hintAllowed = true)
    val Normal = FailurePolicy(mode = FailureMode.MAX_ATTEMPTS, maxAttempts = 3, penaltySeconds = 15)
    val Hard   = FailurePolicy(mode = FailureMode.TIME_PENALTY, penaltySeconds = 60, hintAllowed = false)
    val Custom = FailurePolicy()
}

// ---------------------------------------------------------------------------
// Deck
// ---------------------------------------------------------------------------
data class Deck(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val failurePolicy: FailurePolicy = FailurePolicy(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------------------
// Question
// ---------------------------------------------------------------------------
data class Question(
    val id: String = UUID.randomUUID().toString(),
    val deckId: String,
    val type: QuestionType,
    val prompt: String,
    val correctAnswer: String,
    val distractors: List<String> = emptyList(),
    val hint: String? = null,
    val masteryLevel: Int = 0,       // 0=unseen, 1=learning, 2=mastered
    val consecutiveCorrect: Int = 0,
    val lastSeenAt: Long? = null
)

// ---------------------------------------------------------------------------
// Answer event
// ---------------------------------------------------------------------------
data class AnswerEvent(
    val questionId: String,
    val deckId: String,
    val correct: Boolean,
    val hintUsed: Boolean,
    val answeredAt: Long = System.currentTimeMillis(),
    val responseTimeMs: Long
)

// ---------------------------------------------------------------------------
// Gate result
// ---------------------------------------------------------------------------
sealed class GateResult {
    object Pass : GateResult()
    data class FailRetry(val attemptsUsed: Int, val maxAttempts: Int) : GateResult()
    data class FailPenalty(val waitSeconds: Int) : GateResult()
    data class FailBypass(val shameMessage: String) : GateResult()
    data class FailLockout(val lockedUntilEpoch: Long) : GateResult()
    object NoQuestion : GateResult()  // deck empty / gate disabled
}

// ---------------------------------------------------------------------------
// Eval result
// ---------------------------------------------------------------------------
data class EvalResult(val correct: Boolean, val normalised: String = "")
