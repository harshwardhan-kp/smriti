package com.smriti.app.capture

import android.content.Context
import com.smriti.app.ai.Enricher
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
    // Retained for compatibility; no longer emitted.
    data object Reading : CaptureStage
    data object Listening : CaptureStage
    // Retained for compatibility; no longer emitted.
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
            if (withVoice) {
                emit(CaptureStage.Listening)
            }

            val (photoFile, transcript) = coroutineScope {
                val asrDeferred = async { if (withVoice) asr.transcribe() else "" }
                val photoDeferred = async { camera.capture() }
                Pair(photoDeferred.await(), asrDeferred.await())
            }
            val ocrText = ""

            val now = System.currentTimeMillis()
            val record = RecordEntity(
                createdAt = now,
                photoPath = photoFile.absolutePath,
                ocrText = ocrText,
                transcript = transcript,
                title = titleFor("", transcript, ""),
                summary = "",
                peopleJson = "[]",
                amountsJson = "[]",
                tagsJson = "[]",
                embedding = embedFor("", "", "", transcript),
                enrichmentState = "PENDING"
            )

            val recordId = dao.insertRecord(record)
            emit(CaptureStage.Done(recordId))
            Enricher.request(context)
        } catch (t: Throwable) {
            emit(CaptureStage.Failed(t.message ?: "Capture pipeline failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Returns the little-endian float32 blob for this record, or null when the embedder asset
     * is unavailable. A missing embedding is not an error: [com.smriti.app.ai.Recall] degrades
     * to keyword scoring, so a capture is still worth keeping.
     */
    private suspend fun embedFor(
        title: String,
        summary: String,
        ocrText: String,
        transcript: String
    ): ByteArray? = Enricher.embedFor(context, title, summary, ocrText, transcript)

    /**
     * Titles reach the timeline verbatim, so they have to survive raw OCR.
     *
     * Observed on a Redmi Note 10S, 2026-09-01: a photograph of a laptop screen produced a
     * title containing embedded newlines, which rendered as a three-line timeline card. And a
     * photograph of a blank surface produced an empty title and a card with no text at all.
     *
     * So: collapse all whitespace, trim, cut on a word boundary, and never return blank.
     */
    private fun titleFor(modelTitle: String, transcript: String, ocrText: String): String =
        Enricher.titleFor(modelTitle, transcript, ocrText)
}