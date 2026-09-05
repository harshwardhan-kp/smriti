package com.smriti.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

object BackendFactory {
    suspend fun create(
        context: Context,
        backend: String? = null,
        resetPolicy: Boolean = false
    ): Result<LlmBackend> {
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
