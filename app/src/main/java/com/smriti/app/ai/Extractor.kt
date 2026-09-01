package com.smriti.app.ai

import com.google.gson.Gson
import java.time.LocalDate

/**
 * Turns an OCR string plus a voice transcript into a [StructuredRecord].
 *
 * [engine] is nullable so `repairJson` can be unit-tested without loading a 550 MB model.
 * A null engine always yields the fallback record.
 */
class Extractor(private val engine: LlmEngine? = null) {

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