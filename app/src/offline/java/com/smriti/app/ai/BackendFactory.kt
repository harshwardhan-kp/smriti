package com.smriti.app.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference

object BackendFactory {

    private const val TAG = "SmritiEngine"

    suspend fun create(
        context: Context,
        backend: String? = null,
        resetPolicy: Boolean = false
    ): Result<LlmBackend> {
        if (backend == null && !resetPolicy) {
            try {
                val litertBackend = LiteRtLmHolder.get(context).getOrNull()
                if (litertBackend != null) {
                    return Result.success(litertBackend)
                }
                Log.i(TAG, "LiteRT-LM returned null; falling back to MediaPipe")
            } catch (t: Throwable) {
                Log.w(TAG, "LiteRT-LM failed (${t.message}); falling back to MediaPipe", t)
            }
        }

        if (resetPolicy) {
            BackendPolicy(context).reset()
        }
        val forced = when (backend?.lowercase()) {
            "cpu" -> LlmInference.Backend.CPU
            "gpu" -> LlmInference.Backend.GPU
            else -> null
        }
        return LlmHolder.get(context, forced).map { LocalLlmBackend(it) }
    }
}
