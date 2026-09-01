package com.smriti.app.ai

import android.content.Context
import com.smriti.app.BuildConfig

object BackendFactory {
    suspend fun create(context: Context): Result<LlmBackend> {
        val key = BuildConfig.MUSE_API_KEY
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