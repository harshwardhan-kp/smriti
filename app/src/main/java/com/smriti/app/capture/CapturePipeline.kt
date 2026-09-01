package com.smriti.app.capture

import android.content.Context
import com.google.gson.Gson
import com.smriti.app.ai.BackendFactory
import com.smriti.app.ai.Embedder
import com.smriti.app.ai.EmbedderHolder
import com.smriti.app.ai.Extractor
import com.smriti.app.ai.Ocr
import com.smriti.app.ai.StructuredRecord
import com.smriti.app.data.Converters
import com.smriti.app.data.RecordDao
import com.smriti.app.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId
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

            // The language model is optional at runtime. If it is missing or fails to load,
            // Extractor(null) yields a fallback record built from the raw text. A capture is
            // never lost because the model was unavailable.
            val backend = BackendFactory.create(context).getOrNull()
            val structured: StructuredRecord = Extractor(backend).extract(ocrText, transcript)

            val gson = Gson()
            val record = RecordEntity(
                createdAt = System.currentTimeMillis(),
                photoPath = photoFile.absolutePath,
                ocrText = ocrText,
                transcript = transcript,
                title = titleFor(structured.title, transcript, ocrText),
                summary = structured.summary,
                peopleJson = gson.toJson(structured.people),
                amountsJson = gson.toJson(structured.amounts),
                tagsJson = gson.toJson(structured.tags),
                // Embed at capture time. Doing it lazily later means the first Ask after a
                // capture silently falls back to keyword scoring, which looks like the feature
                // simply does not work.
                embedding = embedFor(structured.title, structured.summary, ocrText, transcript)
            )

            val recordId = dao.insertRecord(record)

            val tasks = structured.actions
                .filter { it.text.isNotBlank() }
                .map { action ->
                    TaskEntity(
                        recordId = recordId,
                        text = action.text,
                        dueDateMillis = parseDue(action.due)
                    )
                }
            if (tasks.isNotEmpty()) dao.insertTasks(tasks)

            emit(CaptureStage.Done(recordId))
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
    ): ByteArray? = try {
        val embedder = EmbedderHolder.get(context)
        val text = listOf(title, summary, transcript, ocrText)
            .filter { it.isNotBlank() }
            .joinToString(" \n ")
            .take(1000)
        if (embedder == null || text.isBlank()) null
        else Converters().fromFloatArray(embedder.embed(text))
    } catch (_: Throwable) {
        null
    }

    /**
     * Titles reach the timeline verbatim, so they have to survive raw OCR.
     *
     * Observed on a Redmi Note 10S, 2026-09-01: a photograph of a laptop screen produced a
     * title containing embedded newlines, which rendered as a three-line timeline card. And a
     * photograph of a blank surface produced an empty title and a card with no text at all.
     *
     * So: collapse all whitespace, trim, cut on a word boundary, and never return blank.
     */
    private fun titleFor(modelTitle: String, transcript: String, ocrText: String): String {
        val source = modelTitle.ifBlank { transcript }.ifBlank { ocrText }
        val flat = source.replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) {
            val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            return "Untitled capture · $clock"
        }
        if (flat.length <= 60) return flat
        val cut = flat.take(60)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > 30) cut.take(lastSpace) + "…" else cut + "…"
    }

    /**
     * A 1B model emits dates in whatever shape it feels like. Accept ISO-8601 and nothing else;
     * anything unparseable becomes a task with no due date rather than a wrong one.
     */
    private fun parseDue(due: String?): Long? {
        if (due.isNullOrBlank() || due.equals("null", ignoreCase = true)) return null
        return try {
            LocalDate.parse(due.trim())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Throwable) {
            null
        }
    }
}