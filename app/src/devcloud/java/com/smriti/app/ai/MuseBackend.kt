package com.smriti.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MuseBackend(
    private val apiKey: String,
    private val model: String = "muse-spark-1.2-contributor"
) : LlmBackend {

    override val label: String = "Muse Spark (cloud, dev only)"

    override suspend fun generate(prompt: String, maxTokens: Int): String = withContext(Dispatchers.IO) {
        var lastIOException: IOException? = null
        var lastCode: Int? = null
        var lastBody: String? = null

        repeat(3) { attempt ->
            try {
                val url = URL("https://api.meta.ai/v1/messages")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 120000
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("model", model)
                    // Muse Spark is a thinking model and its reasoning tokens count against
                    // max_tokens. Measured 2026-09-01: a short extraction prompt burned 509
                    // thinking tokens at max_tokens=512, returned stop_reason="max_tokens" and
                    // an EMPTY content array. At 4096 it used 800 thinking + 62 text and
                    // finished cleanly. The caller's budget describes the answer it wants, so
                    // add headroom for reasoning rather than passing it through.
                    put("max_tokens", maxOf(maxTokens * 4, 4096))
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                conn.outputStream.use { os ->
                    os.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code in setOf(429, 500, 502, 503, 504)) {
                    lastCode = code
                    lastBody = try {
                        conn.errorStream?.bufferedReader()?.readText()
                            ?: conn.inputStream?.bufferedReader()?.readText() ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    conn.disconnect()
                    if (attempt < 2) {
                        delay(1000L * (1L shl attempt))
                        return@repeat
                    } else {
                        throw IllegalStateException("Muse API error $code: $lastBody")
                    }
                }

                if (code !in 200..299) {
                    val errBody = try {
                        conn.errorStream?.bufferedReader()?.readText()
                            ?: conn.inputStream?.bufferedReader()?.readText() ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    conn.disconnect()
                    throw IllegalStateException("Muse API error $code: $errBody")
                }

                val responseText = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(responseText)
                val stopReason = json.optString("stop_reason", "")
                val content = json.getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val entry = content.getJSONObject(i)
                    if (entry.optString("type") == "text") {
                        sb.append(entry.optString("text", ""))
                    }
                }
                if (sb.isEmpty()) {
                    android.util.Log.w(
                        "MuseBackend",
                        "empty text from Muse (stop_reason=$stopReason). If this is " +
                            "\"max_tokens\", reasoning consumed the whole budget."
                    )
                }
                return@withContext sb.toString()
            } catch (e: IOException) {
                lastIOException = e
                if (attempt < 2) {
                    delay(1000L * (1L shl attempt))
                } else {
                    throw e
                }
            } catch (e: IllegalStateException) {
                throw e
            }
        }
        // Should not reach here; if we exhausted retries on IOException, rethrow
        lastIOException?.let { throw it }
        throw IllegalStateException("Muse API error ${lastCode ?: "unknown"}: ${lastBody ?: ""}")
    }
}