package com.smriti.app.capture

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.FileInputStream

class VoskAsr(private val context: Context) : Asr, PushToTalk {

    private val lock = Any()

    @Volatile
    private var pending: CompletableDeferred<Unit>? = null
    private var recorder: AudioRecorder? = null

    companion object {
        @Volatile
        private var cachedModel: Model? = null
        private val modelMutex = Mutex()

        @Volatile
        private var logLevelSet = false
    }

    private suspend fun getOrLoadModel(): Model {
        cachedModel?.let { return it }
        return modelMutex.withLock {
            cachedModel?.let { return@withLock it }
            if (!logLevelSet) {
                try {
                    LibVosk.setLogLevel(LogLevel.WARNINGS)
                } catch (_: Exception) {
                    // ignore if native lib not yet loaded
                }
                logLevelSet = true
            }
            val dir = VoskModelProvisioner.locate(context)
                ?: throw AsrUnavailableException(VoskModelProvisioner.missingMessage())
            try {
                val m = Model(dir.absolutePath)
                cachedModel = m
                m
            } catch (e: AsrUnavailableException) {
                throw e
            } catch (e: Exception) {
                throw AsrUnavailableException(
                    "Failed to load Vosk model from ${dir.absolutePath}: ${e.message}",
                    cause = e
                )
            }
        }
    }

    override suspend fun transcribe(maxMillis: Long): String = withContext(Dispatchers.IO) {
        try {
            val model = getOrLoadModel()

            val deferred = CompletableDeferred<Unit>()
            val rec = AudioRecorder(context)
            synchronized(lock) {
                pending = deferred
                recorder = rec
            }

            rec.start()

            withTimeoutOrNull(maxMillis) {
                deferred.await()
            }

            val wav = try {
                rec.stop()
            } catch (_: Exception) {
                null
            } finally {
                synchronized(lock) {
                    pending = null
                    recorder = null
                }
            }

            if (wav == null || !wav.exists()) return@withContext ""

            var recognizer: Recognizer? = null
            try {
                recognizer = Recognizer(model, 16000f)
                FileInputStream(wav).use { fis ->
                    var skipped = 0L
                    while (skipped < 44) {
                        val s = fis.skip(44 - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    val buf = ByteArray(4096)
                    var n: Int
                    while (fis.read(buf).also { n = it } != -1) {
                        recognizer.acceptWaveForm(buf, n)
                    }
                }
                val jsonStr = recognizer.getFinalResult()
                val text = try {
                    JSONObject(jsonStr).optString("text", "")
                } catch (_: Exception) {
                    ""
                }
                text.trim()
            } finally {
                try {
                    recognizer?.close()
                } catch (_: Exception) {
                }
                try {
                    if (wav.exists()) wav.delete()
                } catch (_: Exception) {
                }
            }
        } catch (e: AsrUnavailableException) {
            synchronized(lock) {
                pending = null
                recorder = null
            }
            throw e
        } catch (e: Exception) {
            synchronized(lock) {
                pending = null
                recorder = null
            }
            throw AsrUnavailableException(e.message ?: "Vosk transcription failed", cause = e)
        }
    }

    override fun stopListening() {
        synchronized(lock) {
            val d = pending
            if (d != null && !d.isCompleted) {
                d.complete(Unit)
            }
        }
    }

    override fun cancel() {
        synchronized(lock) {
            try {
                recorder?.cancel()
            } catch (_: Exception) {
            }
            val d = pending
            if (d != null && !d.isCompleted) {
                d.complete(Unit)
            }
            pending = null
            recorder = null
        }
    }
}
