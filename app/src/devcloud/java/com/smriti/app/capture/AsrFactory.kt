package com.smriti.app.capture

import android.content.Context
import com.smriti.app.BuildConfig

object AsrFactory {
    fun create(context: Context): Asr {
        val key = BuildConfig.GROQ_API_KEY
        return if (key.isNotBlank()) {
            GroqWhisperAsr(context, key)
        } else {
            object : Asr {
                override suspend fun transcribe(maxMillis: Long): String {
                    throw AsrUnavailableException(
                        "Missing groqApiKey - put groqApiKey=... in local.properties to use the devcloud flavor"
                    )
                }

                override fun cancel() {}
            }
        }
    }
}
