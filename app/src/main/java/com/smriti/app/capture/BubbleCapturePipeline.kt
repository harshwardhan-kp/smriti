package com.smriti.app.capture

import android.content.Context
import com.smriti.app.ai.Enricher
import com.smriti.app.data.RecordDao
import com.smriti.app.data.RecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BubbleCapturePipeline(
    private val context: Context,
    private val dao: RecordDao,
    private val asr: Asr
) {

    suspend fun capture(photoFile: File): Long = withContext(Dispatchers.IO) {
        val transcript = asr.transcribe()
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
        Enricher.request(context)
        recordId
    }

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
     * Shared title formatting logic. Collapses whitespace, trims, cuts on word boundary at 60 chars.
     */
    private fun titleFor(modelTitle: String, transcript: String, ocrText: String): String =
        Enricher.titleFor(modelTitle, transcript, ocrText)
}
