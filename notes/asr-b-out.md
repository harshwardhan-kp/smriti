FILE: app/src/offline/java/com/smriti/app/capture/AsrFactory.kt
```kotlin
package com.smriti.app.capture

import android.content.Context

/**
 * Factory that returns the [Asr] implementation used in the **offline** product flavor.
 *
 * Measured on a Moto G05 running stock Android 15 on 2026-09-01, the platform
 * recogniser failed with `LANGUAGE_PACK_ERROR` (code 13) because no offline
 * speech language pack was installed. The shipping build must eventually bundle
 * whisper.cpp rather than depend on a pack that may be absent; this factory is
 * the seam where that swap happens.
 */
object AsrFactory {
    fun create(context: Context): Asr = PlatformAsr(context)
}
```

FILE: app/src/devcloud/java/com/smriti/app/capture/GroqWhisperAsr.kt
```kotlin
package com.smriti.app.capture

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GroqWhisperAsr(
    private val context: Context,
    private val apiKey: String,
    private val model: String = "whisper-large-v3-turbo",
) : Asr, PushToTalk {

    private val recorder = AudioRecorder(context)

    @Volatile
    private var stopDeferred = CompletableDeferred<Unit>()

    override suspend fun transcribe(maxMillis: Long): String = withContext(Dispatchers.IO) {
        // Fresh deferred for every transcription session.
        stopDeferred = CompletableDeferred()

        recorder.start()
        var file: File? = null
        try {
            withTimeoutOrNull(maxMillis) { stopDeferred.await() }
            file = recorder.stop()

            if (file == null || file.length() < 1000L) {
                return@withContext ""
            }

            return@withContext uploadWithRetry(file)
        } finally {
            file?.delete()
        }
    }

    override fun stopListening() {
        stopDeferred.complete(Unit)
    }

    override fun cancel() {
        recorder.cancel()
        stopDeferred.complete(Unit)
    }

    // ----- upload with retry ------------------------------------------------

    private companion object {
        private const val MAX_ATTEMPTS = 3
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 60_000
        private val RETRYABLE_CODES = setOf(429, 500, 502, 503, 504)
    }

    private fun uploadWithRetry(file: File): String {
        val bytes = file.readBytes()
        var lastException: Exception? = null

        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) {
                val backoffMs = (1 shl attempt) * 1000L          // 2 s, 4 s
                Thread.sleep(backoffMs)
            }
            try {
                return upload(bytes)
            } catch (e: IOException) {
                lastException = e
            } catch (e: AsrUnavailableException) {
                // Check whether the HTTP code is retryable.
                val codeStr = e.message
                    ?.removePrefix("Groq HTTP ")
                    ?.substringBefore(":")
                    ?.trim()
                val code = codeStr?.toIntOrNull()
                if (code != null && code in RETRYABLE_CODES) {
                    lastException = e
                } else {
                    throw e
                }
            }
        }
        throw lastException!!
    }

    private fun upload(wavBytes: ByteArray): String {
        val boundary = "----SmritiBoundary" + UUID.randomUUID().toString().replace("-", "")
        val crlf = "\r\n"
        val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            BufferedOutputStream(connection.outputStream).use { out ->
                fun writeString(s: String) = out.write(s.toByteArray(Charsets.UTF_8))

                // Part: file
                writeString("--$boundary$crlf")
                writeString("Content-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"$crlf")
                writeString("Content-Type: audio/wav$crlf")
                writeString(crlf)
                out.write(wavBytes)
                writeString(crlf)

                // Part: model
                writeString("--$boundary$crlf")
                writeString("Content-Disposition: form-data; name=\"model\"$crlf")
                writeString(crlf)
                writeString(model)
                writeString(crlf)

                // Part: response_format
                writeString("--$boundary$crlf")
                writeString("Content-Disposition: form-data; name=\"response_format\"$crlf")
                writeString(crlf)
                writeString("json")
                writeString(crlf)

                // Closing boundary
                writeString("--$boundary--$crlf")
                out.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return JSONObject(body).optString("text").trim()
            }

            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() } ?: ""
            throw AsrUnavailableException("Groq HTTP $code: $errorBody")
        } finally {
            connection.disconnect()
        }
    }
}
```

FILE: app/src/devcloud/java/com/smriti/app/capture/AsrFactory.kt
```kotlin
package com.smriti.app.capture

import android.content.Context
import com.smriti.app.BuildConfig

object AsrFactory {
    fun create(context: Context): Asr {
        val key = BuildConfig.GROQ_API_KEY
        if (key.isNotBlank()) {
            return GroqWhisperAsr(context, key)
        }
        return object : Asr {
            override suspend fun transcribe(maxMillis: Long): String {
                throw AsrUnavailableException(
                    "Groq API key is not configured. Add groqApiKey=YOUR_KEY to local.properties."
                )
            }

            override fun cancel() {
                // No-op.
            }
        }
    }
}
```
