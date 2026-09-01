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
        // Nothing to extract. Asking a language model to summarise the absence of input
        // makes it comment on that absence, and the comment then becomes the record's title:
        // three records on the test device were titled "Empty Transcript Provided". Skip the
        // call entirely — it is also several seconds saved on a capture that has no content.
        if (ocrText.isBlank() && transcript.isBlank()) {
            return StructuredRecord.fallback("")
        }

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
     * Exists solely so the lenient parser can be regression-tested against real model output.
     */
    internal fun parseForTest(raw: String): StructuredRecord? = parseJson(raw)

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
