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