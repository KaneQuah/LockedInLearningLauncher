package com.lockedinlearning.data.repository

import com.lockedinlearning.data.db.DeckDao
import com.lockedinlearning.data.db.DeckEntity
import com.lockedinlearning.data.db.QuestionDao
import com.lockedinlearning.data.db.QuestionEntity
import com.lockedinlearning.domain.model.Deck
import com.lockedinlearning.domain.model.FailureMode
import com.lockedinlearning.domain.model.FailurePolicy
import com.lockedinlearning.domain.model.Question
import com.lockedinlearning.domain.model.QuestionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val questionDao: QuestionDao
) {
    fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getDeckById(id: String): Deck? = deckDao.getById(id)?.toDomain()

    suspend fun saveDeck(deck: Deck) = deckDao.upsert(deck.toEntity())

    suspend fun deleteDeck(deck: Deck) = deckDao.delete(deck.toEntity())

    fun observeQuestions(deckId: String): Flow<List<Question>> =
        questionDao.observeByDeck(deckId).map { entities -> entities.map { it.toDomain() } }

    fun observeAllQuestions(): Flow<List<Question>> =
        questionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getQuestionsByDeck(deckId: String): List<Question> =
        questionDao.getByDeck(deckId).map { it.toDomain() }

    suspend fun saveQuestion(question: Question) = questionDao.upsert(question.toEntity())

    suspend fun saveQuestions(questions: List<Question>) =
        questionDao.upsertAll(questions.map { it.toEntity() })

    suspend fun deleteQuestion(question: Question) = questionDao.delete(question.toEntity())

    suspend fun updateMastery(questionId: String, level: Int, consecutive: Int) =
        questionDao.updateMastery(questionId, level, consecutive, System.currentTimeMillis())

    suspend fun createSampleDeck(): Deck {
        val deckId = UUID.randomUUID().toString()
        val deck = Deck(
            id = deckId,
            name = "World Capitals",
            description = "Practice capital cities from around the world",
            failurePolicy = FailurePolicy(mode = FailureMode.RETRY)
        )
        saveDeck(deck)
        val questions = listOf(
            Question(id = UUID.randomUUID().toString(), deckId = deckId, type = QuestionType.FLASHCARD,
                prompt = "What is the capital of France?", correctAnswer = "Paris"),
            Question(id = UUID.randomUUID().toString(), deckId = deckId, type = QuestionType.FLASHCARD,
                prompt = "What is the capital of Japan?", correctAnswer = "Tokyo"),
            Question(id = UUID.randomUUID().toString(), deckId = deckId, type = QuestionType.FLASHCARD,
                prompt = "What is the capital of Australia?", correctAnswer = "Canberra"),
            Question(id = UUID.randomUUID().toString(), deckId = deckId, type = QuestionType.MCQ,
                prompt = "Which city is the capital of Brazil?", correctAnswer = "Brasília",
                distractors = listOf("São Paulo", "Rio de Janeiro", "Salvador")),
            Question(id = UUID.randomUUID().toString(), deckId = deckId, type = QuestionType.MCQ,
                prompt = "What is the capital of Canada?", correctAnswer = "Ottawa",
                distractors = listOf("Toronto", "Vancouver", "Montreal")),
            Question(id = UUID.randomUUID().toString(), deckId = deckId, type = QuestionType.MATH,
                prompt = "What is 12 × 12?", correctAnswer = "144"),
            Question(id = UUID.randomUUID().toString(), deckId = deckId, type = QuestionType.MATH,
                prompt = "What is the square root of 256?", correctAnswer = "16"),
        )
        saveQuestions(questions)
        return deck
    }
}

// ---------------------------------------------------------------------------
// Mapping helpers
// ---------------------------------------------------------------------------

private val json = Json { ignoreUnknownKeys = true }

private fun DeckEntity.toDomain(): Deck {
    val policy = runCatching { json.decodeFromString<FailurePolicy>(failurePolicyJson) }
        .getOrDefault(FailurePolicy())
    return Deck(
        id = id, name = name, description = description,
        failurePolicy = policy,
        createdAt = createdAt, updatedAt = updatedAt
    )
}

private fun Deck.toEntity() = DeckEntity(
    id = id, name = name, description = description,
    failurePolicyJson = json.encodeToString(failurePolicy),
    createdAt = createdAt, updatedAt = updatedAt
)

private fun QuestionEntity.toDomain(): Question {
    val dist = runCatching { json.decodeFromString<List<String>>(distractors) }.getOrDefault(emptyList())
    return Question(
        id = id, deckId = deckId,
        type = QuestionType.valueOf(type),
        prompt = prompt, correctAnswer = correctAnswer,
        distractors = dist, hint = hint,
        masteryLevel = masteryLevel,
        consecutiveCorrect = consecutiveCorrect,
        lastSeenAt = lastSeenAt
    )
}

private fun Question.toEntity() = QuestionEntity(
    id = id, deckId = deckId,
    type = type.name,
    prompt = prompt, correctAnswer = correctAnswer,
    distractors = json.encodeToString(distractors),
    hint = hint,
    masteryLevel = masteryLevel,
    consecutiveCorrect = consecutiveCorrect,
    lastSeenAt = lastSeenAt
)
