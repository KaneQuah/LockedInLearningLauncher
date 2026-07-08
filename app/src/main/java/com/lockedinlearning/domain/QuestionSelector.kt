package com.lockedinlearning.domain

import com.lockedinlearning.domain.model.Question
import javax.inject.Inject

/**
 * Picks the next question from the active deck using weighted random selection.
 *
 * Weight table (v1):
 *   masteryLevel 0 (unseen)   → weight 3
 *   masteryLevel 1 (learning) → weight 2
 *   masteryLevel 2 (mastered) → weight 1
 *
 * Questions seen within the cooldown window are excluded.
 */
class QuestionSelector @Inject constructor() {

    fun select(
        questions: List<Question>,
        cooldownMillis: Long = 0L
    ): Question? {
        if (questions.isEmpty()) return null

        val cutoff = if (cooldownMillis > 0) System.currentTimeMillis() - cooldownMillis else 0L
        val eligible = questions.filter { q ->
            q.lastSeenAt == null || q.lastSeenAt < cutoff
        }.ifEmpty { questions } // fall back to all if all are in cooldown

        val weighted = eligible.flatMap { q ->
            val weight = when (q.masteryLevel) {
                0    -> 3
                1    -> 2
                else -> 1
            }
            List(weight) { q }
        }

        return weighted.randomOrNull()
    }
}
