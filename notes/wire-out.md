FILE: app/src/main/java/com/smriti/app/ai/Extractor.kt
```kotlin
package com.smriti.app.ai

import com.google.gson.Gson
import java.time.LocalDate

/**
 * Turns an OCR string plus a voice transcript into a [StructuredRecord].
 *
 * [backend] is nullable so `repairJson` can be unit-tested without loading a 550 MB model.
 * A null backend always yields the fallback record.
 */
class Extractor(private val backend: LlmBackend? = null) {

    private val gson = Gson()

    suspend fun extract(ocrText: String, transcript: String): StructuredRecord {
        val fallbackText = transcript.ifBlank { ocrText }
        val activeBackend = backend ?: return StructuredRecord.fallback(fallbackText)

        return try {
            val prompt1 = buildPrompt(ocrText, transcript, includeOcr = true)
            val response1 = activeBackend.generate(prompt1)
            parseJson(response1) ?: retryShortened(activeBackend, transcript, fallbackText)
        } catch (e: Throwable) {
            try {
                retryShortened(activeBackend, transcript, fallbackText)
            } catch (retryError: Throwable) {
                StructuredRecord.fallback(fallbackText)
            }
        }
    }

    private suspend fun retryShortened(
        activeBackend: LlmBackend,
        transcript: String,
        fallbackText: String
    ): StructuredRecord {
        val prompt2 = buildPrompt(ocrText = "", transcript = transcript, includeOcr = false)
        val response2 = activeBackend.generate(prompt2)
        return parseJson(response2) ?: StructuredRecord.fallback(fallbackText)
    }

    private fun buildPrompt(ocrText: String, transcript: String, includeOcr: Boolean): String {
        val today = LocalDate.now().toString()
        val truncatedTranscript = transcript.take(1500)
        val ocrSection = if (includeOcr && ocrText.isNotBlank()) {
            """
            --- OCR TEXT ---
            ${ocrText.take(1500)}
            """.trimIndent()
        } else {
            ""
        }

        return """
            You are a helpful assistant extracting structured information. Today's date is $today.
            You must answer with a single JSON object and nothing else.
            The title must be concise and at most 8 words.

            Exact schema:
            {"title":"","summary":"","people":[],"amounts":[{"value":0,"currency":"INR","label":""}],"tags":[],"actions":[{"text":"","due":"YYYY-MM-DD or null"}]}

            --- TRANSCRIPT ---
            $truncatedTranscript
            $ocrSection
        """.trimIndent()
    }

    internal fun repairJson(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""

        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || firstBrace > lastBrace) {
            return ""
        }

        var jsonSlice = text.substring(firstBrace, lastBrace + 1)
        while (jsonSlice.contains(Regex(",\\s*([}\\]])"))) {
            jsonSlice = jsonSlice.replace(Regex(",\\s*([}\\]])"), "$1")
        }
        return jsonSlice
    }

    /**
     * Lenient parse. Strict POJO binding is the wrong tool here.
     *
     * Measured on a Redmi Note 10S, Qwen2.5-0.5B q8, 2026-09-01: asked for a `actions` key, the
     * model returned `{"actionItems": ["ship the API by Friday", ...]}` — right content, wrong
     * key, and plain strings instead of objects. Gson bound that to an all-null record, the
     * parse "failed", and the retry pushed extraction from 5.3 s to 21.9 s.
     *
     * A 0.5B model will not be argued into a schema. Meet it where it is: accept the common
     * key aliases, accept an array of strings where objects were asked for, and only fall back
     * when there is genuinely nothing usable.
     */
    private fun parseJson(raw: String): StructuredRecord? {
        val repaired = repairJson(raw)
        if (repaired.isBlank()) return null

        val root = try {
            com.google.gson.JsonParser.parseString(repaired).asJsonObject
        } catch (e: Exception) {
            return null
        }

        fun obj(vararg names: String): com.google.gson.JsonElement? =
            names.firstNotNullOfOrNull { n ->
                root.entrySet().firstOrNull { it.key.equals(n, ignoreCase = true) }?.value
            }

        fun str(vararg names: String): String =
            obj(*names)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

        fun strList(vararg names: String): List<String> {
            val el = obj(*names) ?: return emptyList()
            if (!el.isJsonArray) return emptyList()
            return el.asJsonArray.mapNotNull { item ->
                when {
                    item.isJsonPrimitive -> item.asString.trim()
                    item.isJsonObject -> item.asJsonObject.entrySet()
                        .firstOrNull { it.value.isJsonPrimitive }?.value?.asString?.trim()
                    else -> null
                }
            }.filter { it.isNotBlank() }
        }

        val actions = run {
            val el = obj("actions", "actionItems", "action_items", "tasks", "todos", "todo")
            if (el == null || !el.isJsonArray) emptyList()
            else el.asJsonArray.mapNotNull { item ->
                when {
                    // "ship the API by Friday"
                    item.isJsonPrimitive -> Action(item.asString.trim(), null)
                    // {"text": "...", "due": "2026-09-04"}
                    item.isJsonObject -> {
                        val o = item.asJsonObject
                        fun pick(vararg k: String) = k.firstNotNullOfOrNull { n ->
                            o.entrySet().firstOrNull { it.key.equals(n, ignoreCase = true) }
                                ?.value?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                        }
                        val text = pick("text", "task", "action", "item", "description")
                        val due = pick("due", "dueDate", "due_date", "date", "deadline")
                            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        text?.takeIf { it.isNotBlank() }?.let { Action(it, due) }
                    }
                    else -> null
                }
            }
        }

        val amounts = run {
            val el = obj("amounts", "quantities", "figures")
            if (el == null || !el.isJsonArray) emptyList()
            else el.asJsonArray.mapNotNull { item ->
                if (!item.isJsonObject) return@mapNotNull null
                val o = item.asJsonObject
                fun p(vararg k: String) = k.firstNotNullOfOrNull { n ->
                    o.entrySet().firstOrNull { it.key.equals(n, ignoreCase = true) }?.value
                }
                val v = p("value", "amount", "quantity")?.takeIf { it.isJsonPrimitive }
                    ?.let { runCatching { it.asDouble }.getOrNull() } ?: return@mapNotNull null
                Amount(
                    value = v,
                    currency = p("currency", "unit")?.takeIf { it.isJsonPrimitive }?.asString ?: "INR",
                    label = p("label", "for", "description")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                )
            }
        }

        val summary = str("summary", "note", "description")
        val title = str("title", "heading", "name").ifBlank {
            summary.split(Regex("[.!?]")).firstOrNull()?.trim().orEmpty().take(60)
        }

        // Nothing usable at all - let the caller retry or fall back.
        if (title.isBlank() && summary.isBlank() && actions.isEmpty()) return null

        return StructuredRecord(
            title = title,
            summary = summary,
            people = strList("people", "persons", "names", "assignees"),
            amounts = amounts,
            tags = strList("tags", "labels", "topics"),
            actions = actions
        )
    }
}
```
FILE: app/src/main/java/com/smriti/app/capture/CapturePipeline.kt
```kotlin
package com.smriti.app.capture

import android.content.Context
import com.google.gson.Gson
import com.smriti.app.ai.BackendFactory
import com.smriti.app.ai.Extractor
import com.smriti.app.ai.Ocr
import com.smriti.app.ai.StructuredRecord
import com.smriti.app.data.RecordDao
import com.smriti.app.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId
import com.smriti.app.data.RecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed interface CaptureStage {
    data object Photo : CaptureStage
    data object Reading : CaptureStage
    data object Listening : CaptureStage
    data object Thinking : CaptureStage
    data class Done(val recordId: Long) : CaptureStage
    data class Failed(val reason: String) : CaptureStage
}

class CapturePipeline(
    private val context: Context,
    private val dao: RecordDao,
    private val camera: CameraController,
    private val asr: Asr
) {

    fun run(withVoice: Boolean): Flow<CaptureStage> = flow {
        try {
            emit(CaptureStage.Photo)
            val photoFile = camera.capture()

            emit(CaptureStage.Reading)
            if (withVoice) {
                emit(CaptureStage.Listening)
            }

            val (ocrText, transcript) = coroutineScope {
                val ocrDeferred = async { Ocr.read(photoFile, context) }
                val asrDeferred = async {
                    if (withVoice) {
                        asr.transcribe()
                    } else {
                        ""
                    }
                }

                Pair(ocrDeferred.await(), asrDeferred.await())
            }

            emit(CaptureStage.Thinking)

            // The language model is optional at runtime. If it is missing or fails to load,
            // Extractor(null) yields a fallback record built from the raw text. A capture is
            // never lost because the model was unavailable.
            val backend = BackendFactory.create(context).getOrNull()
            val structured: StructuredRecord = Extractor(backend).extract(ocrText, transcript)

            val gson = Gson()
            val record = RecordEntity(
                createdAt = System.currentTimeMillis(),
                photoPath = photoFile.absolutePath,
                ocrText = ocrText,
                transcript = transcript,
                title = titleFor(structured.title, transcript, ocrText),
                summary = structured.summary,
                peopleJson = gson.toJson(structured.people),
                amountsJson = gson.toJson(structured.amounts),
                tagsJson = gson.toJson(structured.tags),
                embedding = null
            )

            val recordId = dao.insertRecord(record)

            val tasks = structured.actions
                .filter { it.text.isNotBlank() }
                .map { action ->
                    TaskEntity(
                        recordId = recordId,
                        text = action.text,
                        dueDateMillis = parseDue(action.due)
                    )
                }
            if (tasks.isNotEmpty()) dao.insertTasks(tasks)

            emit(CaptureStage.Done(recordId))
        } catch (t: Throwable) {
            emit(CaptureStage.Failed(t.message ?: "Capture pipeline failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Titles reach the timeline verbatim, so they have to survive raw OCR.
     *
     * Observed on a Redmi Note 10S, 2026-09-01: a photograph of a laptop screen produced a
     * title containing embedded newlines, which rendered as a three-line timeline card. And a
     * photograph of a blank surface produced an empty title and a card with no text at all.
     *
     * So: collapse all whitespace, trim, cut on a word boundary, and never return blank.
     */
    private fun titleFor(modelTitle: String, transcript: String, ocrText: String): String {
        val source = modelTitle.ifBlank { transcript }.ifBlank { ocrText }
        val flat = source.replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) {
            val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            return "Untitled capture · $clock"
        }
        if (flat.length <= 60) return flat
        val cut = flat.take(60)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > 30) cut.take(lastSpace) + "…" else cut + "…"
    }

    /**
     * A 1B model emits dates in whatever shape it feels like. Accept ISO-8601 and nothing else;
     * anything unparseable becomes a task with no due date rather than a wrong one.
     */
    private fun parseDue(due: String?): Long? {
        if (due.isNullOrBlank() || due.equals("null", ignoreCase = true)) return null
        return try {
            LocalDate.parse(due.trim())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Throwable) {
            null
        }
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
```
FILE: app/src/main/java/com/smriti/app/SelfTest.kt
```kotlin
package com.smriti.app

import android.content.Context
import android.util.Log
import com.smriti.app.ai.BackendFactory
import com.smriti.app.ai.Extractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A headless end-to-end check of the language model, triggerable over adb:
 *
 *     adb shell am start -n com.smriti.app/.MainActivity --ez smriti_selftest true
 *     adb logcat -s SmritiBench:V
 *
 * This exists because MIUI refuses `adb shell input tap` (INJECT_EVENTS is denied to the shell
 * user), so there is no way to drive the capture button remotely. It is also how the tokens/sec
 * figures quoted in the deck are produced — measured on a real handset, not estimated.
 *
 * Debug affordance only. It is inert unless the extra is passed.
 */
object SelfTest {

    const val EXTRA = "smriti_selftest"
    private const val TAG = "SmritiBench"

    private val PROMPT = """
        Extract the action items from this note and reply with a single JSON object only:
        "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders."
    """.trimIndent()

    /**
     * @param backend "cpu", "gpu" or null for the policy default.
     * @param resetPolicy clears any GPU quarantine before running.
     * Note: backend and resetPolicy are honoured only by the offline flavor's BackendFactory.
     * In the devcloud flavor they are logged but otherwise ignored.
     */
    fun run(
        context: Context,
        scope: CoroutineScope,
        backend: String? = null,
        resetPolicy: Boolean = false
    ) {
        scope.launch(Dispatchers.IO) {
            // backend and resetPolicy only affect the offline flavor; devcloud ignores them.
            Log.i(TAG, "requested backend: ${backend ?: "auto"}")
            Log.i(TAG, "resetPolicy: $resetPolicy")
            Log.i(TAG, "=== SELF TEST START ===")

            val loadStart = System.currentTimeMillis()
            val backendResult = BackendFactory.create(context)
            val loadMs = System.currentTimeMillis() - loadStart

            val llmBackend = backendResult.getOrElse { t ->
                Log.e(TAG, "load FAILED after ${loadMs}ms: ${t.javaClass.simpleName}: ${t.message}")
                Log.i(TAG, "=== SELF TEST END (load failed) ===")
                return@launch
            }
            Log.i(TAG, "backend: ${llmBackend.label}")
            Log.i(TAG, "load: ${loadMs} ms")

            // Raw generation timing.
            val genStart = System.currentTimeMillis()
            val raw = try {
                llmBackend.generate(PROMPT)
            } catch (t: Throwable) {
                Log.e(TAG, "generate FAILED: ${t.javaClass.simpleName}: ${t.message}")
                Log.i(TAG, "=== SELF TEST END (generate failed) ===")
                return@launch
            }
            val genMs = System.currentTimeMillis() - genStart

            // Approximate: MediaPipe does not expose a token count on the sync path.
            val approxTokens = raw.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            val tps = if (genMs > 0) approxTokens * 1000.0 / genMs else 0.0

            Log.i(TAG, "generate: ${genMs} ms")
            Log.i(TAG, "approx tokens: $approxTokens")
            Log.i(TAG, "approx tokens/sec: ${"%.2f".format(tps)}")
            Log.i(TAG, "raw output >>>")
            raw.lines().forEach { Log.i(TAG, "  $it") }
            Log.i(TAG, "<<<")

            // And the part that actually matters: does the repair path yield usable structure?
            val extractStart = System.currentTimeMillis()
            val structured = Extractor(llmBackend).extract(ocrText = "", transcript = PROMPT)
            val extractMs = System.currentTimeMillis() - extractStart

            Log.i(TAG, "extract: ${extractMs} ms")
            Log.i(TAG, "title  : ${structured.title}")
            Log.i(TAG, "summary: ${structured.summary}")
            Log.i(TAG, "people : ${structured.people}")
            Log.i(TAG, "tags   : ${structured.tags}")
            Log.i(TAG, "actions: ${structured.actions.size}")
            structured.actions.forEach { Log.i(TAG, "   - ${it.text} (due=${it.due})") }
            Log.i(TAG, "=== SELF TEST END ===")
        }
    }
}
```
FILE: app/src/main/java/com/smriti/app/ui/AskScreen.kt
```kotlin
package com.smriti.app.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smriti.app.ai.BackendFactory
import com.smriti.app.ai.Embedder
import com.smriti.app.ai.Recall
import com.smriti.app.ai.RecallAnswer
import com.smriti.app.capture.PlatformAsr
import com.smriti.app.data.Converters
import com.smriti.app.data.RecordDao
import com.smriti.app.data.SmritiDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ColorAmber = Color(0xFFF2B705)
private val ColorInk = Color(0xFF0B0B0B)
private val ColorCream = Color(0xFFFBF8F1)
private val ColorCardBg = Color(0xFF181818)

class AskViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: RecordDao = SmritiDb.get(app).recordDao()
    private val asr: PlatformAsr = PlatformAsr(app)
    private val converters: Converters = Converters()

    private val _answer = MutableStateFlow<RecallAnswer?>(null)
    val answer: StateFlow<RecallAnswer?> = _answer.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var recall: Recall? = null

    private suspend fun getOrCreateRecall(): Recall? = withContext(Dispatchers.IO) {
        recall?.let { return@withContext it }
        val context = getApplication<Application>()
        val backend = BackendFactory.create(context).getOrNull() ?: return@withContext null
        val embedder = Embedder.create(context).getOrNull()
        val newRecall = Recall(dao, backend, embedder, converters)
        recall = newRecall
        newRecall
    }

    fun ask(q: String) {
        if (q.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val recallInstance = getOrCreateRecall()
                if (recallInstance != null) {
                    val result = withContext(Dispatchers.IO) {
                        recallInstance.ask(q)
                    }
                    _answer.value = result
                } else {
                    _answer.value = RecallAnswer(
                        answer = "Unable to initialize on-device AI engine.",
                        evidenceRecordId = null,
                        evidencePhotoPath = null,
                        usedRecordIds = emptyList()
                    )
                }
            } catch (e: Exception) {
                _answer.value = RecallAnswer(
                    answer = "Failed to recall: ${e.message ?: "Unknown error"}",
                    evidenceRecordId = null,
                    evidencePhotoPath = null,
                    usedRecordIds = emptyList()
                )
            } finally {
                _busy.value = false
            }
        }
    }

    fun listenVoice(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val transcript = asr.transcribe()
                if (transcript.isNotBlank()) {
                    onResult(transcript)
                }
            } catch (e: Exception) {
                // Ignore errors during voice input
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    vm: AskViewModel = viewModel()
) {
    var query by remember { mutableStateOf("") }
    val answer by vm.answer.collectAsState()
    val busy by vm.busy.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ask Smriti",
                        color = ColorCream,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ColorCream
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorInk)
            )
        },
        containerColor = ColorInk
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "What did I commit to this week?",
                        color = ColorCream.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { vm.listenVoice { query = it } }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = ColorAmber
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ColorCream,
                    unfocusedTextColor = ColorCream,
                    focusedBorderColor = ColorAmber,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = ColorAmber
                ),
                singleLine = false,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { vm.ask(query) },
                enabled = !busy && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAmber,
                    contentColor = ColorInk,
                    disabledContainerColor = ColorAmber.copy(alpha = 0.3f),
                    disabledContentColor = ColorInk.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "ASK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (busy) {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = ColorAmber,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Thinking on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColorAmber,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            answer?.let { ans ->
                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorCardBg),
                    border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Answer",
                            style = MaterialTheme.typography.labelMedium,
                            color = ColorAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ans.answer,
                            style = MaterialTheme.typography.bodyLarge,
                            color = ColorCream,
                            lineHeight = 24.sp
                        )
                    }
                }

                if (ans.evidencePhotoPath != null) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Evidence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorAmber
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val evidenceBitmap: ImageBitmap? = remember(ans.evidencePhotoPath) {
                        try {
                            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(ans.evidencePhotoPath, boundsOpts)
                            val sampleSize = run {
                                val maxDim = 1024
                                var s = 1
                                val w = boundsOpts.outWidth
                                val h = boundsOpts.outHeight
                                while ((w / s) > maxDim || (h / s) > maxDim) {
                                    s *= 2
                                }
                                s
                            }
                            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            BitmapFactory.decodeFile(ans.evidencePhotoPath, decodeOpts)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorCardBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ans.evidenceRecordId?.let { onOpenRecord(it) }
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (evidenceBitmap != null) {
                                Image(
                                    bitmap = evidenceBitmap,
                                    contentDescription = "Evidence Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(4f / 3f)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to view full record",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorCream.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
```

