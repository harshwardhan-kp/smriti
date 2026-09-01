package com.smriti.app.ai

import android.content.Context

object BackendFactory {
    suspend fun create(context: Context): Result<LlmBackend> {
        return LlmHolder.get(context).map { LocalLlmBackend(it) }
    }
}