package com.lockedinlearning.di

import android.content.Context
import androidx.room.Room
import com.lockedinlearning.data.db.AppDatabase
import com.lockedinlearning.data.db.AnswerEventDao
import com.lockedinlearning.data.db.DeckDao
import com.lockedinlearning.data.db.QuestionDao
import com.lockedinlearning.launcher3.model.FavoritesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDeckDao(db: AppDatabase): DeckDao = db.deckDao()

    @Provides
    fun provideQuestionDao(db: AppDatabase): QuestionDao = db.questionDao()

    @Provides
    fun provideAnswerEventDao(db: AppDatabase): AnswerEventDao = db.answerEventDao()

    @Provides
    fun provideFavoritesDao(db: AppDatabase): FavoritesDao = db.favoritesDao()
}
