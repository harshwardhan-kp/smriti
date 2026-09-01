FILE: app/src/main/java/com/smriti/app/ModelProvisioner.kt
```kotlin
package com.smriti.app

import android.content.Context
import java.io.File

object ModelProvisioner {
    const val FILE_NAME = "gemma3-1b-it-int4.task"
    private const val MIN_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB

    fun locate(context: Context): File? {
        val candidates = listOf(
            File(context.filesDir, "models/$FILE_NAME"),
            File("/data/local/tmp/llm/$FILE_NAME")
        )
        return candidates.firstOrNull { it.exists() && it.length() > MIN_SIZE_BYTES }
    }

    fun missingMessage(): String {
        return """
            Model file '$FILE_NAME' not found or is smaller than 100 MB.
            Searched paths in order:
              1. <app_files_dir>/models/$FILE_NAME
              2. /data/local/tmp/llm/$FILE_NAME

            To push the model to your device via ADB:
              adb shell mkdir -p /data/local/tmp/llm
              adb push $FILE_NAME /data/local/tmp/llm/
              adb shell chmod 644 /data/local/tmp/llm/$FILE_NAME
        """.trimIndent()
    }
}
```
FILE: app/src/main/java/com/smriti/app/ai/LlmEngine.kt
```kotlin
package com.smriti.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.smriti.app.ModelProvisioner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ModelMissingException(message: String) : Exception(message)

class LlmEngine private constructor(
    private val inference: LlmInference,
    val backend: String
) {
    suspend fun generate(prompt: String, maxTokens: Int = 512): String = withContext(Dispatchers.IO) {
        inference.generateResponse(prompt)
    }

    fun stream(prompt: String): Flow<String> = callbackFlow {
        val listener = LlmInference.ProgressListener<String> { partial, done ->
            if (!partial.isNullOrEmpty()) {
                trySend(partial)
            }
            if (done) {
                close()
            }
        }
        val future = inference.generateResponseAsync(prompt, listener)
        awaitClose {
            future.cancel(true)
        }
    }

    fun close() {
        inference.close()
    }

    companion object {
        suspend fun create(context: Context): Result<LlmEngine> = withContext(Dispatchers.IO) {
            val modelFile = ModelProvisioner.locate(context)
                ?: return@withContext Result.failure(
                    ModelMissingException(ModelProvisioner.missingMessage())
                )

            val modelPath = modelFile.absolutePath

            try {
                val gpuOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .build()
                val gpuInference = LlmInference.createFromOptions(context, gpuOptions)
                Result.success(LlmEngine(gpuInference, "GPU"))
            } catch (gpuError: Throwable) {
                try {
                    val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setPreferredBackend(LlmInference.Backend.CPU)
                        .build()
                    val cpuInference = LlmInference.createFromOptions(context, cpuOptions)
                    Result.success(LlmEngine(cpuInference, "CPU (GPU unavailable)"))
                } catch (cpuError: Throwable) {
                    Result.failure(cpuError)
                }
            }
        }
    }
}

object LlmHolder {
    @Volatile
    private var engine: LlmEngine? = null
    private val mutex = Mutex()

    suspend fun get(context: Context): Result<LlmEngine> {
        val current = engine
        if (current != null) {
            return Result.success(current)
        }

        return mutex.withLock {
            val doubleCheck = engine
            if (doubleCheck != null) {
                Result.success(doubleCheck)
            } else {
                val result = LlmEngine.create(context.applicationContext)
                result.onSuccess { engine = it }
                result
            }
        }
    }
}
```
FILE: app/src/main/java/com/smriti/app/ai/StructuredRecord.kt
```kotlin
package com.smriti.app.ai

data class StructuredRecord(
    val title: String = "",
    val summary: String = "",
    val people: List<String> = emptyList(),
    val amounts: List<Amount> = emptyList(),
    val tags: List<String> = emptyList(),
    val actions: List<Action> = emptyList()
) {
    companion object {
        fun fallback(rawText: String): StructuredRecord = StructuredRecord(
            title = rawText.take(60),
            summary = "",
            people = emptyList(),
            amounts = emptyList(),
            tags = emptyList(),
            actions = emptyList()
        )
    }
}

data class Amount(
    val value: Double = 0.0,
    val currency: String = "INR",
    val label: String = ""
)

data class Action(
    val text: String = "",
    val due: String? = null
)
```
FILE: app/src/main/java/com/smriti/app/ai/Extractor.kt
```kotlin
package com.smriti.app.ai

import com.google.gson.Gson
import java.time.LocalDate

class Extractor internal constructor(private val engine: LlmEngine?) {

    constructor(engine: LlmEngine) : this(engine as LlmEngine?)

    private val gson = Gson()

    suspend fun extract(ocrText: String, transcript: String): StructuredRecord {
        val fallbackText = transcript.ifBlank { ocrText }
        val activeEngine = engine ?: return StructuredRecord.fallback(fallbackText)

        return try {
            val prompt1 = buildPrompt(ocrText, transcript, includeOcr = true)
            val response1 = activeEngine.generate(prompt1)
            parseJson(response1) ?: retryShortened(activeEngine, transcript, fallbackText)
        } catch (e: Throwable) {
            try {
                retryShortened(activeEngine, transcript, fallbackText)
            } catch (retryError: Throwable) {
                StructuredRecord.fallback(fallbackText)
            }
        }
    }

    private suspend fun retryShortened(
        activeEngine: LlmEngine,
        transcript: String,
        fallbackText: String
    ): StructuredRecord {
        val prompt2 = buildPrompt(ocrText = "", transcript = transcript, includeOcr = false)
        val response2 = activeEngine.generate(prompt2)
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

    private fun parseJson(raw: String): StructuredRecord? {
        val repaired = repairJson(raw)
        if (repaired.isBlank()) return null

        return try {
            val parsed = gson.fromJson(repaired, StructuredRecord::class.java) ?: return null
            StructuredRecord(
                title = parsed.title ?: "",
                summary = parsed.summary ?: "",
                people = parsed.people?.filterNotNull() ?: emptyList(),
                amounts = parsed.amounts?.filterNotNull()?.map {
                    Amount(
                        value = it.value,
                        currency = it.currency ?: "INR",
                        label = it.label ?: ""
                    )
                } ?: emptyList(),
                tags = parsed.tags?.filterNotNull() ?: emptyList(),
                actions = parsed.actions?.filterNotNull()?.map {
                    Action(
                        text = it.text ?: "",
                        due = it.due
                    )
                } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }
}
```
FILE: app/src/test/java/com/smriti/app/ai/ExtractorJsonTest.kt
```kotlin
package com.smriti.app.ai

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorJsonTest {

    private val extractor = Extractor(null)
    private val gson = Gson()

    @Test
    fun testCleanJson() {
        val input = """{"title":"Meeting Notes","summary":"Discuss budget","people":[],"amounts":[],"tags":[],"actions":[]}"""
        val repaired = extractor.repairJson(input)
        assertEquals(input, repaired)
        val record = gson.fromJson(repaired, StructuredRecord::class.java)
        assertNotNull(record)
        assertEquals("Meeting Notes", record.title)
    }

    @Test
    fun testJsonWrappedInFences() {
        val input = """
            ```json
            {
              "title": "Sprint Planning",
              "summary": "Plan sprint items",
              "people": ["Alice", "Bob"],
              "amounts": [],
              "tags": ["work"],
              "actions": []
            }
            ```
        """.trimIndent()
        val repaired = extractor.repairJson(input)
        val record = gson.fromJson(repaired, StructuredRecord::class.java)
        assertNotNull(record)
        assertEquals("Sprint Planning", record.title)
        assertEquals(listOf("Alice", "Bob"), record.people)
    }

    @Test
    fun testJsonWithLeadingProse() {
        val input = """
            Here is the extraction result:
            {
              "title": "Doctor Visit",
              "summary": "Annual checkup",
              "people": ["Dr. Sharma"],
              "amounts": [],
              "tags": ["health"],
              "actions": [{"text": "Follow up in 3 months", "due": "2026-12-01"}]
            }
            Hope this helps!
        """.trimIndent()
        val repaired = extractor.repairJson(input)
        val record = gson.fromJson(repaired, StructuredRecord::class.java)
        assertNotNull(record)
        assertEquals("Doctor Visit", record.title)
        assertEquals(1, record.actions.size)
        assertEquals("2026-12-01", record.actions[0].due)
    }

    @Test
    fun testJsonWithTrailingCommas() {
        val input = """
            {
              "title": "Market Run",
              "summary": "Weekly supplies",
              "people": ["John",],
              "amounts": [
                {
                  "value": 550.0,
                  "currency": "INR",
                  "label": "Groceries",
                },
              ],
              "tags": ["shopping",],
              "actions": [],
            }
        """.trimIndent()
        val repaired = extractor.repairJson(input)
        val record = gson.fromJson(repaired, StructuredRecord::class.java)
        assertNotNull(record)
        assertEquals("Market Run", record.title)
        assertEquals(1, record.amounts.size)
        assertEquals(550.0, record.amounts[0].value, 0.001)
        assertEquals("INR", record.amounts[0].currency)
    }

    @Test
    fun testTextWithNoBraces() {
        val input = "Unable to process the image and no content was found."
        val repaired = extractor.repairJson(input)
        assertTrue(repaired.isEmpty())
    }
}
```
