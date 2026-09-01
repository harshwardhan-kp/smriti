package com.smriti.app.ai

import com.smriti.app.data.Converters
import com.smriti.app.data.RecordDao
import com.smriti.app.data.RecordEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecallAnswer(
    val answer: String,
    val evidenceRecordId: Long?,
    val evidencePhotoPath: String?,
    val usedRecordIds: List<Long>
)

class Recall(
    private val dao: RecordDao,
    private val backend: LlmBackend,
    private val embedder: Embedder?,
    private val converters: Converters
) {

    private val stopWords: Set<String> = setOf(
        // English stopwords (>3 chars or frequently non-discriminative)
        "about", "above", "after", "again", "also", "been", "before", "being", "both",
        "could", "did", "does", "doing", "down", "during", "each", "from", "further",
        "have", "having", "here", "into", "just", "more", "most", "only", "other",
        "over", "please", "same", "should", "some", "such", "than", "that", "their",
        "them", "then", "there", "these", "they", "this", "those", "through", "under",
        "very", "what", "when", "where", "which", "while", "whom", "whose", "with",
        "would", "tell", "show", "find", "give",

        // Hindi stopwords (Latin transliteration)
        "kya", "kyon", "kahan", "kaise", "kisne", "kisko", "kiska", "kaun",
        "mera", "meri", "mere", "tera", "teri", "tere", "uska", "uski", "uske",
        "unka", "unki", "unke", "isme", "usme", "hoga", "hogi", "hoge", "wala",
        "wali", "wale", "karo", "batao", "dikhao", "hai", "hain", "tha", "thi", "the",

        // Hindi stopwords (Devanagari script)
        "क्या", "क्यों", "कहाँ", "कब", "कैसे", "किसने", "किसको", "किसका", "कौन",
        "मेरा", "मेरी", "मेरे", "तेरा", "तेरी", "तेरे", "उसका", "उसकी", "उसके",
        "उनका", "उनकी", "उनके", "यहाँ", "वहाँ", "होगा", "होगी", "होंगे", "वाला",
        "वाली", "वाले", "बताओ", "दिखाओ", "करो", "था", "थी", "थे", "हैं"
    )

    suspend fun ask(question: String): RecallAnswer {
        return try {
            val allRecords = dao.allRecordsWithEmbedding()
            if (allRecords.isEmpty()) {
                return RecallAnswer(
                    answer = "No records found in memory.",
                    evidenceRecordId = null,
                    evidencePhotoPath = null,
                    usedRecordIds = emptyList()
                )
            }

            val recordsWithEmbeddings: List<Pair<RecordEntity, FloatArray>> = allRecords.mapNotNull { record ->
                val embeddingArray = record.embedding?.let { converters.toFloatArray(it) }
                if (embeddingArray != null && embeddingArray.isNotEmpty()) {
                    record to embeddingArray
                } else {
                    null
                }
            }

            val selectedRecords: List<RecordEntity> = if (embedder == null || recordsWithEmbeddings.size < 3) {
                fallbackKeywordMatch(question, allRecords)
            } else {
                val queryEmbedding = embedder.embed(question)
                val candidates = recordsWithEmbeddings.map { (record, embedding) ->
                    record.id to embedding
                }
                val topScored = VectorMath.topK(queryEmbedding, candidates, k = 4)
                val recordMap = allRecords.associateBy { it.id }
                topScored.mapNotNull { recordMap[it.first] }
            }

            if (selectedRecords.isEmpty()) {
                return RecallAnswer(
                    answer = "The stored records do not contain information to answer your question.",
                    evidenceRecordId = null,
                    evidencePhotoPath = null,
                    usedRecordIds = emptyList()
                )
            }

            val primaryRecord = selectedRecords.first()
            val prompt = buildPrompt(selectedRecords, question)
            val generatedAnswer = backend.generate(prompt)

            RecallAnswer(
                answer = generatedAnswer.trim(),
                evidenceRecordId = primaryRecord.id,
                evidencePhotoPath = primaryRecord.photoPath,
                usedRecordIds = selectedRecords.map { it.id }
            )
        } catch (t: Throwable) {
            RecallAnswer(
                answer = "Failed to answer question: ${t.message ?: "An unexpected error occurred."}",
                evidenceRecordId = null,
                evidencePhotoPath = null,
                usedRecordIds = emptyList()
            )
        }
    }

    private fun fallbackKeywordMatch(
        question: String,
        records: List<RecordEntity>
    ): List<RecordEntity> {
        val questionWords = question.lowercase(Locale.ROOT)
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length > 3 && it !in stopWords }
            .distinct()

        val scored = records.map { record ->
            val searchable = buildString {
                append(record.title).append(' ')
                append(record.summary).append(' ')
                append(record.ocrText).append(' ')
                append(record.transcript)
            }.lowercase(Locale.ROOT)

            val score = if (questionWords.isEmpty()) {
                0
            } else {
                questionWords.count { keyword -> searchable.contains(keyword) }
            }
            record to score
        }

        val matching = scored
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }

        return if (matching.isNotEmpty()) {
            matching
        } else {
            records.take(4)
        }
    }

    private fun buildPrompt(records: List<RecordEntity>, question: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val recordBlocks = records.mapIndexed { index, record ->
            val formattedDate = try {
                dateFormat.format(Date(record.createdAt))
            } catch (_: Throwable) {
                record.createdAt.toString()
            }

            val detailText = buildString {
                if (record.ocrText.isNotBlank()) append(record.ocrText).append(" ")
                if (record.transcript.isNotBlank()) append(record.transcript)
            }.trim().take(300)

            """
            Record ${index + 1}:
            Date: $formattedDate
            Title: ${record.title}
            Summary: ${record.summary}
            Details: $detailText
            """.trimIndent()
        }.joinToString("\n\n")

        return """
            You are Smriti, an offline personal memory assistant. Answer the user's question using ONLY the provided records below.
            Answer in at most three sentences. If the records do not contain the answer, say plainly that the information is not present in the records. Do not make up facts or extrapolate beyond what is stated.

            RECORDS:
            $recordBlocks

            QUESTION:
            $question

            ANSWER:
        """.trimIndent()
    }
}