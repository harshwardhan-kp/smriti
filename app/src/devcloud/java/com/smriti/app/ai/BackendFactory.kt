package com.smriti.app.ai

import android.content.Context

object BackendFactory {
    suspend fun create(context: Context): Result<LlmBackend> {
        val key = RuntimeKeys.muse(context)
        return if (key.isNotBlank()) {
            Result.success(MuseBackend(key))
        } else {
            Result.failure(
                IllegalStateException(
                    "Missing museApiKey - put museApiKey=... in local.properties to use the devcloud flavor"
                )
            )
        }
    }
}