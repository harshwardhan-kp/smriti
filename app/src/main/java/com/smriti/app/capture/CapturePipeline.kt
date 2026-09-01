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
                createdAt = System.currentTimeMillis(),
                photoPath = photoFile.absolutePath,
                ocrText = ocrText,
                transcript = transcript,
                title = title,
                summary = summary,
                peopleJson = "[]",
                amountsJson = "[]",
                tagsJson = "[]",
                embedding = null
            )

            val recordId = dao.insertRecord(record)
            emit(CaptureStage.Done(recordId))
        } catch (t: Throwable) {
            emit(CaptureStage.Failed(t.message ?: "Capture pipeline failed"))
        }
    }.flowOn(Dispatchers.IO)
}