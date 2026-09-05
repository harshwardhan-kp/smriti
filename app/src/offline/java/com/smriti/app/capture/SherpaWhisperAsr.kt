package com.smriti.app.capture

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SherpaWhisperAsr(private val context: Context) : Asr, PushToTalk {

    private val lock = Any()

    @Volatile
    private var pending: CompletableDeferred<Unit>? = null
    private var recorder: AudioRecorder? = null

    companion object {
        private const val TRAILING_PAD_MS = 700L

        @Volatile
        private var cachedRecognizer: OfflineRecognizer? = null
        private val recognizerMutex = Mutex()

        internal suspend fun getOrLoadRecognizer(context: Context): OfflineRecognizer {
            cachedRecognizer?.let { return it }
            return recognizerMutex.withLock {
                cachedRecognizer?.let { return@withLock it }
                val paths = WhisperModelProvisioner.locate(context)
                    ?: throw AsrUnavailableException(WhisperModelProvisioner.missingMessage())
                try {
                    val cfg = OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                        modelConfig = OfflineModelConfig(
                            whisper = OfflineWhisperModelConfig(
                                encoder = paths.encoder.absolutePath,
                                decoder = paths.decoder.absolutePath,
                                language = "",          // "" = auto-detect language
                                task = "transcribe"
                            ),
                            tokens = paths.tokens.absolutePath,
                            numThreads = 4,
                            debug = false
                        ),
                        decodingMethod = "greedy_search"
                    )
                    val r = OfflineRecognizer(config = cfg)
                    cachedRecognizer = r
                    r
                } catch (e: AsrUnavailableException) {
                    throw e
                } catch (e: Exception) {
                    throw AsrUnavailableException(
                        "Failed to load Whisper model from ${paths.encoder.parent}: ${e.message}",
                        cause = e
                    )
                }
            }
        }

        internal fun readWavSamples(wav: File): FloatArray {
            val bytes = wav.readBytes()
            if (bytes.size <= 44) return FloatArray(0)
            val sampleCount = (bytes.size - 44) / 2
            if (sampleCount <= 0) return FloatArray(0)
            val buffer = ByteBuffer.wrap(bytes, 44, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
            val samples = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                samples[i] = buffer.getShort() / 32768.0f
            }
            return samples
        }

        internal suspend fun transcribeFile(context: Context, wav: File): String = withContext(Dispatchers.IO) {
            val recognizer = getOrLoadRecognizer(context)
            val samples = readWavSamples(wav)
            if (samples.isEmpty()) return@withContext ""

            var stream: OfflineStream? = null
            try {
                val text = synchronized(recognizer) {
                    val s = recognizer.createStream()
                    stream = s
                    s.acceptWaveform(samples, 16000)
                    recognizer.decode(s)
                    recognizer.getResult(s).text
                }
                text.trim()
            } finally {
                try {
                    stream?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun getOrLoadRecognizer(): OfflineRecognizer = getOrLoadRecognizer(context)

    override suspend fun transcribe(maxMillis: Long): String = withContext(Dispatchers.IO) {
        try {
            val recognizer = getOrLoadRecognizer()

            val deferred = CompletableDeferred<Unit>()
            val rec = AudioRecorder(context)
            synchronized(lock) {
                pending = deferred
                recorder = rec
            }

            rec.start()

            val released = withTimeoutOrNull(maxMillis) {
                deferred.await()
            } != null

            if (released) {
                // Humans release the button as they finish the last word, so cutting the
                // microphone at that exact instant clips it. Wait a short trailing pad
                // before stopping the recorder so the final syllable is captured.
                delay(TRAILING_PAD_MS)
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

            var stream: OfflineStream? = null
            try {
                val samples = readWavSamples(wav)
                if (samples.isEmpty()) return@withContext ""

                val text = synchronized(recognizer) {
                    val s = recognizer.createStream()
                    stream = s
                    s.acceptWaveform(samples, 16000)
                    recognizer.decode(s)
                    recognizer.getResult(s).text
                }
                text.trim()
            } finally {
                try {
                    stream?.release()
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
            throw AsrUnavailableException(e.message ?: "Whisper transcription failed", cause = e)
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
