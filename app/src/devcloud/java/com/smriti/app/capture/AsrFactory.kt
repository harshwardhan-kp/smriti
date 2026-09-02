package com.smriti.app.capture

import android.content.Context
import com.smriti.app.ai.RuntimeKeys

object AsrFactory {
    fun create(context: Context): Asr {
        val key = RuntimeKeys.groq(context)
        return if (key.isNotBlank()) {
            GroqWhisperAsr(context, key)
        } else {
            object : Asr {
                override suspend fun transcribe(maxMillis: Long): String {
                    throw AsrUnavailableException(
                        RuntimeKeys.missingMessage("Groq")
                    )
                }

                override fun cancel() {}
            }
        }
    }
}
