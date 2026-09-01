FILE: app/src/main/java/com/smriti/app/capture/CameraController.kt
```kotlin
package com.smriti.app.capture

import android.content.Context
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(rotation)
                .build()
            imageCapture = capture

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
            } catch (e: Exception) {
                // Binding failures will surface during capture
            }
        }, mainExecutor)
    }

    suspend fun capture(): File = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resumeWithException(
                IllegalStateException("Camera is not bound or ImageCapture is not initialized")
            )
            return@suspendCancellableCoroutine
        }

        val photosDir = File(context.filesDir, "photos").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val photoFile = File(photosDir, "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        val executor = ContextCompat.getMainExecutor(context)

        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) {
                        continuation.resume(photoFile)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            }
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }
}
```
FILE: app/src/main/java/com/smriti/app/capture/Asr.kt
```kotlin
package com.smriti.app.capture

/**
 * Interface for Automated Speech Recognition (ASR).
 *
 * Note: The production implementation will be backed by whisper.cpp for high-accuracy,
 * fully offline speech recognition. This interface exists so that swapping out the
 * platform-based speech engine for whisper.cpp is a one-line change.
 */
interface Asr {
    suspend fun transcribe(maxMillis: Long = 15_000): String
    fun cancel()
}
```
FILE: app/src/main/java/com/smriti/app/capture/PlatformAsr.kt
```kotlin
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

class PlatformAsr(private val context: Context) : Asr {

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
```
FILE: app/src/main/java/com/smriti/app/ai/Ocr.kt
```kotlin
package com.smriti.app.ai

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object Ocr {

    /**
     * Indian workplace artefacts are routinely bilingual and the Latin model silently
     * returns near-empty text on Devanagari input. Running both Devanagari and Latin
     * recognizers and selecting the output with the most characters ensures accurate capture.
     */
    suspend fun read(file: File, context: Context): String {
        val devanagariClient = TextRecognition.getClient(
            DevanagariTextRecognizerOptions.Builder().build()
        )
        val latinClient = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))

            val devanagariText = devanagariClient.process(image).await()
            val latinText = latinClient.process(image).await()

            if (devanagariText.length >= latinText.length) {
                devanagariText
            } else {
                latinText
            }
        } catch (e: Exception) {
            // OCR failure must never abort a capture
            ""
        } finally {
            devanagariClient.close()
            latinClient.close()
        }
    }

    private suspend fun Task<Text>.await(): String =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { visionText ->
                if (continuation.isActive) {
                    continuation.resume(visionText.text.orEmpty())
                }
            }
            addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resume("")
                }
            }
        }
}
```
FILE: app/src/main/java/com/smriti/app/capture/CapturePipeline.kt
```kotlin
package com.smriti.app.capture

import android.content.Context
import com.smriti.app.ai.Ocr
import com.smriti.app.data.RecordDao
import com.smriti.app.data.RecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed interface CaptureStage {
    data object Photo : CaptureStage
    data object Reading : CaptureStage
    data object Listening : CaptureStage
    data object Thinking : CaptureStage
    data class Done(val recordId: Long) : CaptureStage
    data class Failed(val reason: String) : CaptureStage
}

class CapturePipeline(
    private val context: Context,
    private val dao: RecordDao,
    private val camera: CameraController,
    private val asr: Asr
) {

    fun run(withVoice: Boolean): Flow<CaptureStage> = flow {
        try {
            emit(CaptureStage.Photo)
            val photoFile = camera.capture()

            emit(CaptureStage.Reading)
            if (withVoice) {
                emit(CaptureStage.Listening)
            }

            val (ocrText, transcript) = coroutineScope {
                val ocrDeferred = async { Ocr.read(photoFile, context) }
                val asrDeferred = async {
                    if (withVoice) {
                        asr.transcribe()
                    } else {
                        ""
                    }
                }

                Pair(ocrDeferred.await(), asrDeferred.await())
            }

            emit(CaptureStage.Thinking)

            // TASK 5: replace the placeholder title/summary with Extractor
            val combinedText = transcript.ifBlank { ocrText }
            val title = combinedText.take(60)
            val summary = ""

            val record = RecordEntity(
                title = title,
                summary = summary,
                photoPath = photoFile.absolutePath,
                ocrText = ocrText,
                transcript = transcript,
                tasksJson = "[]",
                tagsJson = "[]",
                embedding = null
            )

            val recordId = dao.insert(record)
            emit(CaptureStage.Done(recordId))
        } catch (t: Throwable) {
            emit(CaptureStage.Failed(t.message ?: "Capture pipeline failed"))
        }
    }.flowOn(Dispatchers.IO)
}
```
