package com.lockedinlearning.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnswerEventDao {
    @Insert
    suspend fun insert(event: AnswerEventEntity): Long

    /** Correct answers in the last N milliseconds (for cooldown check). */
    @Query(
        """SELECT COUNT(*) FROM answer_events
           WHERE correct = 1 AND answeredAt > :since"""
    )
    suspend fun countCorrectSince(since: Long): Int

    /** Daily correct count per day (epoch-day bucket). */
    @Query(
        """SELECT (answeredAt / 86400000) AS day, COUNT(*) AS cnt
           FROM answer_events
           WHERE correct = 1 AND answeredAt > :since
           GROUP BY day
           ORDER BY day DESC"""
    )
    suspend fun dailyCorrectSince(since: Long): List<DayCount>

    /** Total correct answers ever. */
    @Query("SELECT COUNT(*) FROM answer_events WHERE correct = 1")
    fun observeTotalCorrect(): Flow<Int>

    /** Correct answers today. */
    @Query(
        """SELECT COUNT(*) FROM answer_events
           WHERE correct = 1 AND answeredAt >= :startOfDay"""
    )
    fun observeTodayCorrect(startOfDay: Long): Flow<Int>

    /** Total bypass events. */
    @Query("SELECT COUNT(*) FROM answer_events WHERE correct = 0 AND hintUsed = 0")
    suspend fun countBypasses(): Int

    @Query("SELECT * FROM answer_events ORDER BY answeredAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<AnswerEventEntity>
}

data class DayCount(val day: Long, val cnt: Int)
