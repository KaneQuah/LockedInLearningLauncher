package com.lockedinlearning.domain.evaluator

import com.lockedinlearning.domain.model.EvalResult
import com.lockedinlearning.domain.model.Question
import com.lockedinlearning.domain.model.QuestionType
import java.text.Normalizer

/** Common interface for all evaluators. */
interface AnswerEvaluator {
    fun evaluate(question: Question, rawAnswer: String): EvalResult
}

// ---------------------------------------------------------------------------
// Factory
// ---------------------------------------------------------------------------
object EvaluatorFactory {
    fun forQuestion(question: Question, fuzzy: Boolean = true): AnswerEvaluator =
        when (question.type) {
            QuestionType.FLASHCARD -> FreeTextEvaluator(fuzzy)
            QuestionType.MATH      -> NumericEvaluator(tolerance = 1e-9)
            QuestionType.MCQ       -> MultipleChoiceEvaluator()
            QuestionType.LANGUAGE  -> LanguageFillEvaluator()
        }
}

// ---------------------------------------------------------------------------
// Sentinel values used by flip-card self-grading
// ---------------------------------------------------------------------------
const val FLASHCARD_SELF_CORRECT = "__FC_CORRECT__"
const val FLASHCARD_SELF_WRONG   = "__FC_WRONG__"

// ---------------------------------------------------------------------------
// Free-text (flashcard)
// ---------------------------------------------------------------------------
class FreeTextEvaluator(private val fuzzy: Boolean = true) : AnswerEvaluator {
    override fun evaluate(question: Question, rawAnswer: String): EvalResult {
        // Flip-card self-grading path
        if (rawAnswer == FLASHCARD_SELF_CORRECT) return EvalResult(true)
        if (rawAnswer == FLASHCARD_SELF_WRONG)   return EvalResult(false)

        val a = rawAnswer.trim().lowercase()
        val b = question.correctAnswer.trim().lowercase()
        val correct = if (fuzzy) levenshtein(a, b) <= 2 else a == b
        return EvalResult(correct, a)
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val m = s1.length; val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (s1[i - 1] == s2[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[m][n]
    }
}

// ---------------------------------------------------------------------------
// Numeric (math)
// ---------------------------------------------------------------------------
class NumericEvaluator(private val tolerance: Double = 1e-9) : AnswerEvaluator {
    override fun evaluate(question: Question, rawAnswer: String): EvalResult {
        val expected = question.correctAnswer.trim().toDoubleOrNull()
        val given    = rawAnswer.trim().toDoubleOrNull()
        val correct  = expected != null && given != null && kotlin.math.abs(expected - given) <= tolerance
        return EvalResult(correct, rawAnswer.trim())
    }
}

// ---------------------------------------------------------------------------
// Multiple choice
// ---------------------------------------------------------------------------
class MultipleChoiceEvaluator : AnswerEvaluator {
    override fun evaluate(question: Question, rawAnswer: String): EvalResult {
        val correct = rawAnswer.trim().equals(question.correctAnswer.trim(), ignoreCase = true)
        return EvalResult(correct, rawAnswer.trim())
    }
}

// ---------------------------------------------------------------------------
// Language fill-in (unicode normalisation + case fold + accent strip)
// ---------------------------------------------------------------------------
class LanguageFillEvaluator : AnswerEvaluator {
    override fun evaluate(question: Question, rawAnswer: String): EvalResult {
        val a = normalise(rawAnswer)
        val b = normalise(question.correctAnswer)
        return EvalResult(a == b, a)
    }

    private fun normalise(s: String): String {
        val nfd = Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
