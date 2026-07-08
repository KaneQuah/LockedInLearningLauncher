package com.lockedinlearning.domain

import android.content.Context
import android.net.Uri
import com.lockedinlearning.domain.model.Question
import com.lockedinlearning.domain.model.QuestionType
import com.opencsv.CSVReaderBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStreamReader
import java.util.UUID
import javax.inject.Inject

/**
 * Imports questions from a CSV file.
 *
 * Supported header formats (both detected automatically):
 *
 *   Format A — question-bank format (generated banks):
 *     id, front, back, type, category, distractors, hint, image
 *     where `distractors` is a JSON array string: ["opt1","opt2","opt3"]
 *
 *   Format B — manual format:
 *     front, back, type, hint, distractor1, distractor2, distractor3
 *
 * Minimum required columns: front (or prompt) + back (or answer)
 * `type` defaults to FLASHCARD if absent or unrecognised.
 */
class CsvImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class ImportResult(
        val questions: List<Question>,
        val skipped: Int,
        val errors: List<String>
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun import(uri: Uri, deckId: String): ImportResult {
        val questions = mutableListOf<Question>()
        val errors    = mutableListOf<String>()
        var skipped   = 0

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return ImportResult(emptyList(), 0, listOf("Cannot open file"))

        val reader  = CSVReaderBuilder(InputStreamReader(inputStream, Charsets.UTF_8)).build()
        val allRows = reader.readAll()
        reader.close()

        if (allRows.isEmpty()) return ImportResult(emptyList(), 0, listOf("Empty file"))

        val header = allRows.first().map { it.trim().lowercase() }

        // --- locate columns ---
        val frontIdx     = header.indexOfFirst { it == "front" || it == "prompt" }
        val backIdx      = header.indexOfFirst { it == "back"  || it == "answer" }
        if (frontIdx < 0 || backIdx < 0) {
            return ImportResult(
                emptyList(), 0,
                listOf("CSV must have 'front'/'prompt' and 'back'/'answer' columns. Found: $header")
            )
        }

        val typeIdx      = header.indexOf("type")
        val hintIdx      = header.indexOf("hint")
        // Format A: single JSON-array distractors column
        val distJsonIdx  = header.indexOf("distractors")
        // Format B: three separate distractor columns
        val d1Idx        = header.indexOf("distractor1")
        val d2Idx        = header.indexOf("distractor2")
        val d3Idx        = header.indexOf("distractor3")

        for ((lineNum, row) in allRows.drop(1).withIndex()) {
            val lineNumber = lineNum + 2
            if (row.size <= maxOf(frontIdx, backIdx)) {
                skipped++
                errors.add("Line $lineNumber: not enough columns (${row.size})")
                continue
            }

            val prompt = row[frontIdx].trim()
            val answer = row[backIdx].trim()
            if (prompt.isBlank() || answer.isBlank()) { skipped++; continue }

            val type = if (typeIdx >= 0 && typeIdx < row.size) {
                runCatching { QuestionType.valueOf(row[typeIdx].trim().uppercase()) }
                    .getOrDefault(QuestionType.FLASHCARD)
            } else QuestionType.FLASHCARD

            val hint = if (hintIdx >= 0 && hintIdx < row.size) row[hintIdx].trim().ifBlank { null } else null

            val distractors: List<String> = when {
                // Format A — JSON array column present and non-empty
                distJsonIdx >= 0 && distJsonIdx < row.size && row[distJsonIdx].trim().startsWith("[") -> {
                    parseDistractorsJson(row[distJsonIdx].trim())
                }
                // Format B — individual columns
                d1Idx >= 0 -> listOfNotNull(
                    row.getOrNull(d1Idx)?.trim()?.ifBlank { null },
                    row.getOrNull(d2Idx)?.trim()?.ifBlank { null },
                    row.getOrNull(d3Idx)?.trim()?.ifBlank { null }
                )
                else -> emptyList()
            }

            questions.add(
                Question(
                    id            = UUID.randomUUID().toString(),
                    deckId        = deckId,
                    type          = type,
                    prompt        = prompt,
                    correctAnswer = answer,
                    distractors   = distractors,
                    hint          = hint
                )
            )
        }

        return ImportResult(questions, skipped, errors)
    }

    /** Parse a JSON array string like `["Silver","Gold","Copper"]` into a string list. */
    private fun parseDistractorsJson(raw: String): List<String> =
        runCatching {
            json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
}
