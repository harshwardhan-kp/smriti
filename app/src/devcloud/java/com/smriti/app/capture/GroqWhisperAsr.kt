package com.smriti.app.capture

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GroqWhisperAsr(
    private val context: Context,
    private val apiKey: String,
    private val model: String = "whisper-large-v3-turbo"
) : Asr, PushToTalk {

    @Volatile
    private var stopSignal: CompletableDeferred<Unit>? = null

    @Volatile
    private var isCancelled = false

    @Volatile
    private var recorder: AudioRecorder? = null

    override fun stopListening() {
        stopSignal?.let { d ->
            if (!d.isCompleted) d.complete(Unit)
        }
    }

    override fun cancel() {
        isCancelled = true
        try {
            recorder?.cancel()
        } catch (_: Exception) {}
        stopSignal?.let { d ->
            if (!d.isCompleted) d.complete(Unit)
        }
    }

    override suspend fun transcribe(maxMillis: Long): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw AsrUnavailableException(
                "Missing groqApiKey - put groqApiKey=... in local.properties to use the devcloud flavor"
            )
        }

        val rec = AudioRecorder(context)
        recorder = rec
        val signal = CompletableDeferred<Unit>()
        stopSignal = signal
        isCancelled = false
        var wavFile: File? = null
        try {
            rec.start()
            withTimeoutOrNull(maxMillis) {
                signal.await()
            }
            if (isCancelled) {
                // Ensure recording is stopped and file cleaned up; abort without upload
                try { rec.cancel() } catch (_: Exception) {}
                throw AsrUnavailableException("ASR cancelled")
            }
            wavFile = rec.stop()
            if (wavFile == null || !wavFile.exists() || wavFile.length() <= 44L) {
                return@withContext ""
            }
            uploadWithRetry(wavFile)
        } finally {
            // Delete temp wav in a finally, always
            try {
                wavFile?.let { if (it.exists()) it.delete() }
            } catch (_: Exception) {}
            // In case stop() wasn't called (cancel path), ensure recorder released
            try { rec.cancel() } catch (_: Exception) {}
            recorder = null
            stopSignal = null
        }
    }

    private suspend fun uploadWithRetry(file: File): String {
        var lastException: IOException? = null
        var lastCode: Int? = null
        var lastBody: String? = null

        repeat(3) { attempt ->
            try {
                return uploadOnce(file)
            } catch (e: IOException) {
                lastException = e
                if (attempt < 2) {
                    delay(1000L * (1L shl attempt))
                } else {
                    throw e
                }
            } catch (e: AsrUnavailableException) {
                val code = e.errorCode
                if (code != null && code in setOf(429, 500, 502, 503, 504)) {
                    lastCode = code
                    lastBody = e.message
                    if (attempt < 2) {
                        delay(1000L * (1L shl attempt))
                        return@repeat
                    }
                }
                throw e
            }
        }
        lastException?.let { throw it }
        throw AsrUnavailableException("Groq API error ${lastCode ?: "unknown"}: ${lastBody ?: ""}", lastCode)
    }

    private fun uploadOnce(file: File): String {
        val boundary = "Boundary-${UUID.randomUUID()}"
        val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            doInput = true
        }

        val lineEnd = "\r\n"
        val twoHyphens = "--"

        conn.outputStream.use { os ->
            // file part
            os.write("$twoHyphens$boundary$lineEnd".toByteArray(Charsets.UTF_8))
            os.write("Content-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"$lineEnd".toByteArray(Charsets.UTF_8))
            os.write("Content-Type: audio/wav$lineEnd$lineEnd".toByteArray(Charsets.UTF_8))
            file.inputStream().use { fis ->
                fis.copyTo(os)
            }
            os.write(lineEnd.toByteArray(Charsets.UTF_8))

            // model part
            os.write("$twoHyphens$boundary$lineEnd".toByteArray(Charsets.UTF_8))
            os.write("Content-Disposition: form-data; name=\"model\"$lineEnd$lineEnd".toByteArray(Charsets.UTF_8))
            os.write(model.toByteArray(Charsets.UTF_8))
            os.write(lineEnd.toByteArray(Charsets.UTF_8))

            // response_format part
            os.write("$twoHyphens$boundary$lineEnd".toByteArray(Charsets.UTF_8))
            os.write("Content-Disposition: form-data; name=\"response_format\"$lineEnd$lineEnd".toByteArray(Charsets.UTF_8))
            os.write("json".toByteArray(Charsets.UTF_8))
            os.write(lineEnd.toByteArray(Charsets.UTF_8))

            // end
            os.write("$twoHyphens$boundary$twoHyphens$lineEnd".toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            val body = try {
                conn.errorStream?.bufferedReader()?.readText()
                    ?: conn.inputStream?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) {
                ""
            }
            conn.disconnect()
            if (code in setOf(429, 500, 502, 503, 504)) {
                throw AsrUnavailableException("Groq API error $code: $body", code)
            } else {
                throw AsrUnavailableException("Groq API error $code: $body", code)
            }
        }

        val responseText = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = JSONObject(responseText)
        return json.optString("text", "").trim()
    }
}
