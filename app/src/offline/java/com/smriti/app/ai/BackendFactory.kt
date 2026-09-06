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
            // Genie first: it is the only path that actually reaches the Hexagon NPU. It returns
            // null rather than throwing whenever it does not apply — wrong SoC, no bundle, no JNI
            // library — so a non-Qualcomm handset falls straight through without loading anything.
            try {
                val genieBackend = GenieHolder.get(context)
                if (genieBackend != null) {
                    return Result.success(genieBackend)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Genie failed (${t.message}); falling back to LiteRT-LM", t)
            }

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
