package com.lockedinlearning.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// DeckEntity
// ---------------------------------------------------------------------------
@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    /** JSON-serialised FailurePolicy */
    val failurePolicyJson: String,
    /** JSON list of {startHour, endHour} pairs for time-of-day scheduling */
    val activeTimeWindows: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------------------
// QuestionEntity
// ---------------------------------------------------------------------------
@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId"), Index("masteryLevel")]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    /** FLASHCARD | MATH | MCQ | LANGUAGE */
    val type: String,
    val prompt: String,
    val correctAnswer: String,
    /** JSON list of strings — used for MCQ distractors */
    val distractors: String = "[]",
    val hint: String? = null,
    /** 0 = unseen, 1 = learning, 2 = mastered */
    val masteryLevel: Int = 0,
    val consecutiveCorrect: Int = 0,
    val lastSeenAt: Long? = null
)

// ---------------------------------------------------------------------------
// AnswerEventEntity
// ---------------------------------------------------------------------------
@Entity(
    tableName = "answer_events",
    indices = [Index("questionId"), Index("answeredAt")]
)
data class AnswerEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val deckId: String,
    val correct: Boolean,
    val hintUsed: Boolean,
    val answeredAt: Long = System.currentTimeMillis(),
    val responseTimeMs: Long
)
