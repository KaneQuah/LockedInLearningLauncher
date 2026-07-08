package com.lockedinlearning.data.repository

import com.lockedinlearning.data.db.AnswerEventDao
import com.lockedinlearning.data.db.AnswerEventEntity
import com.lockedinlearning.domain.model.AnswerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepository @Inject constructor(
    private val answerEventDao: AnswerEventDao
) {
    suspend fun recordEvent(event: AnswerEvent) {
        answerEventDao.insert(event.toEntity())
    }

    /** Returns number of correct answers since [sinceEpoch] — for cooldown check. */
    suspend fun correctAnswersSince(sinceEpoch: Long): Int =
        answerEventDao.countCorrectSince(sinceEpoch)

    /** Correct answers keyed by epoch-day (ms / 86400000) for the last 30 days. */
    suspend fun dailyCorrects(days: Int = 30): Map<Long, Int> {
        val since = System.currentTimeMillis() - days * 86_400_000L
        return answerEventDao.dailyCorrectSince(since)
            .associate { it.day to it.cnt }
    }

    fun observeTotalCorrect(): Flow<Int> = answerEventDao.observeTotalCorrect()

    fun observeTodayCorrect(): Flow<Int> {
        val startOfDay = startOfTodayMillis()
        return answerEventDao.observeTodayCorrect(startOfDay)
    }

    /** Compute streak: consecutive days (ending today) with at least [goal] correct answers. */
    suspend fun computeStreak(goal: Int = 1): Int {
        val dailyMap = dailyCorrects(365)
        val todayDay = System.currentTimeMillis() / 86_400_000L
        var streak = 0
        var day = todayDay
        while (true) {
            val count = dailyMap[day] ?: 0
            if (count < goal) break
            streak++
            day--
        }
        return streak
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

private fun AnswerEvent.toEntity() = AnswerEventEntity(
    questionId = questionId,
    deckId = deckId,
    correct = correct,
    hintUsed = hintUsed,
    answeredAt = answeredAt,
    responseTimeMs = responseTimeMs
)
