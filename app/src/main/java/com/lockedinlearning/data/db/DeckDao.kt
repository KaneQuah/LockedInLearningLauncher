package com.lockedinlearning.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getById(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeById(id: String): Flow<DeckEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deck: DeckEntity)

    @Delete
    suspend fun delete(deck: DeckEntity)

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun count(): Int
}
