package com.smriti.app.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AsrUnavailableException(
    message: String,
    val errorCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class PlatformAsr(private val context: Context) : Asr, PushToTalk {

    private var activeRecognizer: SpeechRecognizer? = null

    override suspend fun transcribe(maxMillis: Long): String = withContext(Dispatchers.Main) {
        val result = withTimeoutOrNull(maxMillis) {
            suspendCancellableCoroutine { continuation ->
                val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
                activeRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        if (!continuation.isActive) return

                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                continuation.resume("")
                            }
                            else -> {
                                val readableName = getErrorName(error)
                                val message = if (error == 13 || error == 12) {
                                    "No offline voice pack installed ($readableName)"
                                } else {
                                    "Offline speech recognition unavailable: $readableName (code $error)"
                                }
                                continuation.resumeWithException(
                                    AsrUnavailableException(message = message, errorCode = error)
                                )
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        if (!continuation.isActive) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        continuation.resume(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                continuation.invokeOnCancellation {
                    recognizer.destroy()
                    if (activeRecognizer === recognizer) {
                        activeRecognizer = null
                    }
                }

                recognizer.startListening(intent)
            }
        }

        activeRecognizer?.destroy()
        activeRecognizer = null
        result ?: ""
    }

    /**
     * Release-to-stop, matching [com.smriti.app.capture.GroqWhisperAsr] and `VoskAsr`.
     *
     * Without this the offline flavor's fallback path behaved differently from every other ASR
     * implementation: letting go of the shutter did nothing, and the user waited for
     * SpeechRecognizer's own end-of-speech detection instead of getting an immediate result.
     * `stopListening` ends capture and still delivers whatever was heard, unlike `cancel`.
     */
    override fun stopListening() {
        try {
            activeRecognizer?.stopListening()
        } catch (_: Throwable) {
            // The recognizer may already be torn down; releasing a button must never crash.
        }
    }

    override fun cancel() {
        activeRecognizer?.destroy()
        activeRecognizer = null
    }

    private fun getErrorName(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            10 -> "ERROR_CANNOT_CHECK_SUPPORT"
            11 -> "ERROR_SERVER_DISCONNECTED"
            12 -> "ERROR_LANGUAGE_NOT_SUPPORTED"
            13 -> "ERROR_LANGUAGE_UNAVAILABLE"
            14 -> "ERROR_TOO_MANY_REQUESTS"
            else -> "ERROR_CODE_$errorCode"
        }
    }
}