package com.smriti.app

import android.content.Context
import java.io.File

object ModelProvisioner {
    const val FILE_NAME = "gemma3-1b-it-int4.task"
    private const val MIN_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB

    fun locate(context: Context): File? {
        val candidates = listOf(
            File(context.filesDir, "models/$FILE_NAME"),
            File("/data/local/tmp/llm/$FILE_NAME")
        )
        return candidates.firstOrNull { it.exists() && it.length() > MIN_SIZE_BYTES }
    }

    fun missingMessage(): String {
        return """
            Model file '$FILE_NAME' not found or is smaller than 100 MB.
            Searched paths in order:
              1. <app_files_dir>/models/$FILE_NAME
              2. /data/local/tmp/llm/$FILE_NAME

            To push the model to your device via ADB:
              adb shell mkdir -p /data/local/tmp/llm
              adb push $FILE_NAME /data/local/tmp/llm/
              adb shell chmod 644 /data/local/tmp/llm/$FILE_NAME
        """.trimIndent()
    }
}