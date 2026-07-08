package com.lockedinlearning.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lockedinlearning.launcher3.model.FavoriteItemEntity
import com.lockedinlearning.launcher3.model.FavoritesDao

@Database(
    entities = [DeckEntity::class, QuestionEntity::class, AnswerEventEntity::class, FavoriteItemEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun questionDao(): QuestionDao
    abstract fun answerEventDao(): AnswerEventDao
    abstract fun favoritesDao(): FavoritesDao

    companion object {
        const val DATABASE_NAME = "lockedinlearning.db"
    }
}
