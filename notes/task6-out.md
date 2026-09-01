FILE: app/src/main/java/com/smriti/app/ai/Embedder.kt
```kotlin
package com.smriti.app.ai

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device text embedding generator using MediaPipe's TextEmbedder task.
 *
 * The .tflite embedder model ships in app assets and is a few megabytes, unlike the
 * language model which is provisioned separately at ~550 MB.
 */
class Embedder private constructor(private val embedder: TextEmbedder) : AutoCloseable {

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        val truncated = text.take(1000)
        val result = embedder.embed(truncated)
        result.embeddingResult().embeddings()[0].floatEmbedding()
    }

    override fun close() {
        embedder.close()
    }

    companion object {
        fun create(
            context: Context,
            modelAssetPath: String = "embedder.tflite"
        ): Result<Embedder> {
            return try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelAssetPath)
                    .build()

                val options = TextEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setL2Normalize(true)
                    .setQuantize(false)
                    .build()

                val textEmbedder = TextEmbedder.createFromOptions(context, options)
                Result.success(Embedder(textEmbedder))
            } catch (t: Throwable) {
                Result.failure(
                    IllegalStateException(
                        "Failed to initialize Embedder with asset '$modelAssetPath'. The model asset may be absent during development.",
                        t
                    )
                )
            }
        }
    }
}
```

FILE: app/src/main/java/com/smriti/app/ai/VectorMath.kt
```kotlin
package com.smriti.app.ai

import kotlin.math.sqrt

/**
 * Vector similarity utilities for in-memory record retrieval.
 *
 * Design Decision:
 * Brute-force linear scan over a few hundred records executes in sub-millisecond time on
 * modern mobile ARM cores. Introducing a dedicated vector database or SQLite vector extension
 * would add external dependencies, native compilation overhead, and edge-case failure modes
 * for no user-visible gain.
 *
 * Consider transitioning to an approximate nearest neighbor (ANN) index or SQLite vector
 * extension only if the database grows beyond ~10,000 embedded records.
 */
object VectorMath {

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) {
            return 0f
        }

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            val ai = a[i].toDouble()
            val bi = b[i].toDouble()
            dot += ai * bi
            normA += ai * ai
            normB += bi * bi
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0f
        }

        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator == 0.0) {
            return 0f
        }

        val similarity = (dot / denominator).toFloat()
        return when {
            similarity.isNaN() -> 0f
            similarity > 1f -> 1f
            similarity < -1f -> -1f
            else -> similarity
        }
    }

    fun topK(
        query: FloatArray,
        candidates: List<Pair<Long, FloatArray>>,
        k: Int
    ): List<Pair<Long, Float>> {
        if (candidates.isEmpty() || k <= 0) {
            return emptyList()
        }

        return candidates
            .asSequence()
            .map { (id, embedding) -> id to cosine(query, embedding) }
            .sortedByDescending { it.second }
            .take(k)
            .toList()
    }
}
```

FILE: app/src/main/java/com/smriti/app/ai/Recall.kt
```kotlin
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
    private val engine: LlmEngine,
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
            val generatedAnswer = engine.generate(prompt)

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
```

FILE: app/src/test/java/com/smriti/app/ai/VectorMathTest.kt
```kotlin
package com.smriti.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VectorMathTest {

    @Test
    fun `identical vectors score 1 within tolerance`() {
        val a = floatArrayOf(0.5f, 1.2f, -0.8f, 3.0f)
        val b = floatArrayOf(0.5f, 1.2f, -0.8f, 3.0f)
        val score = VectorMath.cosine(a, b)
        assertEquals(1.0f, score, 1e-5f)
    }

    @Test
    fun `orthogonal vectors score 0`() {
        val a = floatArrayOf(1.0f, 0.0f, 0.0f)
        val b = floatArrayOf(0.0f, 1.0f, 0.0f)
        val score = VectorMath.cosine(a, b)
        assertEquals(0.0f, score, 1e-5f)
    }

    @Test
    fun `mismatched vector lengths return 0`() {
        val a = floatArrayOf(1.0f, 2.0f)
        val b = floatArrayOf(1.0f, 2.0f, 3.0f)
        val score = VectorMath.cosine(a, b)
        assertEquals(0f, score, 0.0f)
    }

    @Test
    fun `empty arrays return 0`() {
        assertEquals(0f, VectorMath.cosine(floatArrayOf(), floatArrayOf(1f, 2f)), 0.0f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(1f, 2f), floatArrayOf()), 0.0f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(), floatArrayOf()), 0.0f)
    }

    @Test
    fun `zero vector does not produce NaN`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val nonZero = floatArrayOf(1f, 2f, 3f)

        val scoreWithNonZero = VectorMath.cosine(zero, nonZero)
        assertFalse(scoreWithNonZero.isNaN())
        assertEquals(0f, scoreWithNonZero, 0.0f)

        val scoreWithBothZero = VectorMath.cosine(zero, zero)
        assertFalse(scoreWithBothZero.isNaN())
        assertEquals(0f, scoreWithBothZero, 0.0f)
    }

    @Test
    fun `topK returns k items in descending score order`() {
        val query = floatArrayOf(1.0f, 0.0f, 0.0f)
        val candidates = listOf(
            1L to floatArrayOf(0.0f, 1.0f, 0.0f),         // cosine = 0.0
            2L to floatArrayOf(1.0f, 0.0f, 0.0f),         // cosine = 1.0
            3L to floatArrayOf(0.7071f, 0.7071f, 0.0f),  // cosine ~ 0.7071
            4L to floatArrayOf(-1.0f, 0.0f, 0.0f)        // cosine = -1.0
        )

        val top2 = VectorMath.topK(query, candidates, k = 2)

        assertEquals(2, top2.size)
        assertEquals(2L, top2[0].first)
        assertEquals(1.0f, top2[0].second, 1e-4f)

        assertEquals(3L, top2[1].first)
        assertEquals(0.7071f, top2[1].second, 1e-4f)
    }
}
```
