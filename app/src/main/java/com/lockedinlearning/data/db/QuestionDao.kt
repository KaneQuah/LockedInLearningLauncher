package com.lockedinlearning.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE deckId = :deckId ORDER BY masteryLevel ASC")
    fun observeByDeck(deckId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE deckId = :deckId")
    suspend fun getByDeck(deckId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: String): QuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(question: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(questions: List<QuestionEntity>)

    @Delete
    suspend fun delete(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE deckId = :deckId")
    suspend fun deleteByDeck(deckId: String)

    @Query("UPDATE questions SET masteryLevel = :level, consecutiveCorrect = :consecutive, lastSeenAt = :seenAt WHERE id = :id")
    suspend fun updateMastery(id: String, level: Int, consecutive: Int, seenAt: Long)

    @Query("SELECT COUNT(*) FROM questions WHERE deckId = :deckId")
    suspend fun countByDeck(deckId: String): Int
}
