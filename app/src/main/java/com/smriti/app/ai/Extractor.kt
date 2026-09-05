package com.smriti.app.ai

import android.util.Log
import com.google.gson.Gson
import com.smriti.app.BuildConfig
import java.time.LocalDate

private const val TAG = "SmritiExtract"

/**
 * Turns an OCR string plus a voice transcript into a [StructuredRecord].
 *
 * [backend] is nullable so `repairJson` can be unit-tested without loading a 550 MB model.
 * A null backend always yields the fallback record.
 */
private const val MIN_MEANINGFUL_CHARS = 12

private const val EXTRACTION_SCHEMA = """{"type":"object","properties":{"actions":{"type":"array","items":{"type":"object","properties":{"text":{"type":"string"},"due":{"type":["string","null"]}},"required":["text","due"]}},"title":{"type":"string"},"summary":{"type":"string"},"people":{"type":"array","items":{"type":"string"}},"amounts":{"type":"array","items":{"type":"object","properties":{"value":{"type":"number"},"currency":{"type":"string"},"label":{"type":"string"}},"required":["value","currency","label"]}},"tags":{"type":"array","items":{"type":"string"}}},"required":["actions","title","summary","people","amounts","tags"]}"""

class Extractor(private val backend: LlmBackend? = null) {

    private val gson = Gson()

    suspend fun extract(ocrText: String, transcript: String): StructuredRecord {
        // Nothing to extract. Asking a language model to summarise the absence of input
        // makes it comment on that absence, and the comment then becomes the record's title:
        // three records on the test device were titled "Empty Transcript Provided". Skip the
        // call entirely — it is also several seconds saved on a capture that has no content.
        // Blank is not the only empty. A silent 4-second hold returned a 1-character
        // transcript and 3 characters of OCR noise, which is not blank — and the model duly
        // titled the record "Empty Document No Content". Anything under this threshold carries
        // no meaning worth extracting, so do not ask.
        val meaningful = (ocrText.trim() + " " + transcript.trim())
            .filter { it.isLetterOrDigit() }
            .length
        if (meaningful < MIN_MEANINGFUL_CHARS) {
            return StructuredRecord.fallback("")
        }

        val fallbackText = transcript.ifBlank { ocrText }
        val activeBackend = backend ?: return StructuredRecord.fallback(fallbackText)

        return try {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "attempt 1 (with OCR)")
            }
            val prompt1 = buildPrompt(ocrText, transcript, includeOcr = true)
            val response1 = activeBackend.generate(prompt1, jsonSchema = EXTRACTION_SCHEMA)
            if (BuildConfig.DEBUG) {
                response1.lines().forEach { Log.i(TAG, it) }
            }
            val record1 = parseJson(response1, transcript)
            if (BuildConfig.DEBUG) {
                if (record1 != null) {
                    Log.i(TAG, "parseJson returned record, actions.size = ${record1.actions.size}")
                } else {
                    Log.i(TAG, "parseJson returned null")
                }
            }
            record1 ?: retryShortened(activeBackend, transcript, fallbackText)
        } catch (e: Throwable) {
            try {
                retryShortened(activeBackend, transcript, fallbackText)
            } catch (retryError: Throwable) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "giving up, returning StructuredRecord.fallback(...)")
                }
                StructuredRecord.fallback(fallbackText)
            }
        }
    }

    private suspend fun retryShortened(
        activeBackend: LlmBackend,
        transcript: String,
        fallbackText: String
    ): StructuredRecord {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "attempt 2 (shortened retry)")
        }
        val prompt2 = buildPrompt(ocrText = "", transcript = transcript, includeOcr = false)
        val response2 = activeBackend.generate(prompt2, jsonSchema = EXTRACTION_SCHEMA)
        if (BuildConfig.DEBUG) {
            response2.lines().forEach { Log.i(TAG, it) }
        }
        val record2 = parseJson(response2, transcript)
        if (BuildConfig.DEBUG) {
            if (record2 != null) {
                Log.i(TAG, "parseJson returned record, actions.size = ${record2.actions.size}")
            } else {
                Log.i(TAG, "parseJson returned null")
            }
        }
        if (record2 != null) return record2
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "giving up, returning StructuredRecord.fallback(...)")
        }
        return StructuredRecord.fallback(fallbackText)
    }

    // WHY the schema below is ordered actions-first (measured 2026-09-05, iQOO 15,
    // Qwen2.5-1.5B q8): with the six-key schema led by title/summary, the model
    // returned title/summary/people/amounts correctly but omitted "actions" and "tags"
    // entirely, while a short focused prompt on the same sentence returned both action
    // items. Small models attend most reliably to what comes first, and actions is what
    // the product is built on ("what did I commit to?"), so actions leads the schema.
    private fun buildPrompt(ocrText: String, transcript: String, includeOcr: Boolean): String {
        val today = LocalDate.now().toString()
        // The worked example says "tomorrow", so its date MUST track today or the
        // example teaches the model that tomorrow == today. Wrong dates are worse
        // than no dates: see the date policy in PROGRESS.md.
        val tomorrow = LocalDate.now().plusDays(1).toString()
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
            Extract actions first. Today's date is $today.
            Reply with a single JSON object only. Every key is required - never omit one; use [] or "" when empty.
            Title at most 8 words.
            due must be null unless the person actually stated a day or date; never guess one.

            Exact schema:
            {"actions":[{"text":"","due":"YYYY-MM-DD or null"}],"title":"","summary":"","people":[],"amounts":[{"value":0,"currency":"INR","label":""}],"tags":[]}

            Example: "Pay Sharma Rs 500 tomorrow" -> {"actions":[{"text":"Pay Sharma Rs 500","due":"$tomorrow"}],"title":"Pay Sharma","summary":"Pay Sharma Rs 500","people":["Sharma"],"amounts":[],"tags":[]}

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
        // Unescape fallback (parse-first). WHY parse-first is mandatory: a blind global
        // replace of \" with " would corrupt legitimately escaped quotes inside string
        // values — e.g. {"title": "He said \"hi\""} is valid JSON today and must keep
        // parsing to the title He said "hi" — so the unescape below may only ever run
        // as a fallback after a genuine parse failure.
        try {
            com.google.gson.JsonParser.parseString(jsonSlice)
            return jsonSlice
        } catch (e: Exception) {
            // Genuine parse failure; try the unescaped candidate below.
        }
        val candidate = unescapeSlice(jsonSlice)
        try {
            com.google.gson.JsonParser.parseString(candidate)
            return candidate
        } catch (e: Exception) {
            return jsonSlice
        }
    }

    private fun unescapeSlice(slice: String): String {
        val out = StringBuilder(slice.length)
        var i = 0
        while (i < slice.length) {
            val c = slice[i]
            if (c == '\\' && i + 1 < slice.length) {
                when (slice[i + 1]) {
                    'n' -> { out.append('\n'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    'r' -> { out.append('\r'); i += 2 }
                    '"' -> { out.append('"'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    else -> { out.append(c); i += 1 }
                }
            } else {
                out.append(c); i += 1
            }
        }
        return out.toString()
    }

    /**
     * Exists solely so the lenient parser can be regression-tested against real model output.
     */
    internal fun parseForTest(raw: String, transcript: String = raw): StructuredRecord? =
        parseJson(raw, transcript)

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
    private fun parseJson(raw: String, transcript: String = ""): StructuredRecord? {
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
                    item.isJsonPrimitive -> {
                        val text = item.asString.trim()
                        val due = DueDateResolver.resolve(text, transcript, null, LocalDate.now())
                        text.takeIf { it.isNotBlank() }?.let { Action(it, due) }
                    }
                    // {"text": "...", "due": "2026-09-04"}
                    item.isJsonObject -> {
                        val o = item.asJsonObject
                        fun pick(vararg k: String) = k.firstNotNullOfOrNull { n ->
                            o.entrySet().firstOrNull { it.key.equals(n, ignoreCase = true) }
                                ?.value?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                        }
                        val text = pick("text", "task", "action", "item", "description")
                        val rawDue = pick("due", "dueDate", "due_date", "date", "deadline")
                            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        val due = text?.let { DueDateResolver.resolve(it, transcript, rawDue, LocalDate.now()) }
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
