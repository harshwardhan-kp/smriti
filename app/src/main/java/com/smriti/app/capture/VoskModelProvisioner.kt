package com.smriti.app.capture

import android.content.Context
import java.io.File

/**
 * Locates a Vosk model directory on-device.
 *
 * Checks the app-private files dir first, then the well-known tmp locations
 * used during development. A directory is only accepted as a valid model if it
 * contains `am/` or `conf/model.conf`, so a half-extracted download does not
 * crash the native layer.
 */
object VoskModelProvisioner {

    fun locate(context: Context): File? {
        val candidates = listOf(
            File(context.filesDir, "vosk/model"),
            File("/data/local/tmp/vosk/model"),
            File("/data/local/tmp/vosk/vosk-model-small-en-us-0.15"),
            File("/data/local/tmp/vosk/vosk-model-small-hi-0.22")
        )
        for (dir in candidates) {
            if (!dir.isDirectory) continue
            if (isValidModelDir(dir)) return dir
        }
        return null
    }

    private fun isValidModelDir(dir: File): Boolean {
        return File(dir, "am").isDirectory || File(dir, "conf/model.conf").isFile
    }

    fun missingMessage(): String = buildString {
        appendLine("Vosk model not found. Searched:")
        appendLine("  - <app filesDir>/vosk/model  (Context.filesDir/vosk/model)")
        appendLine("  - /data/local/tmp/vosk/model")
        appendLine("  - /data/local/tmp/vosk/vosk-model-small-en-us-0.15")
        appendLine("  - /data/local/tmp/vosk/vosk-model-small-hi-0.22")
        appendLine()
        appendLine("A valid Vosk model directory must contain a subdirectory \"am\" or a file \"conf/model.conf\".")
        appendLine()
        appendLine("Provision a model with:")
        appendLine("  adb shell mkdir -p /data/local/tmp/vosk")
        appendLine("  adb push vosk-model-small-en-us-0.15 /data/local/tmp/vosk/model")
        appendLine()
        appendLine("Or push to the app-private location (requires run-as or rooted adb):")
        appendLine("  adb push vosk-model-small-en-us-0.15 /data/data/com.smriti.app/files/vosk/model")
        appendLine()
        append("Models from https://alphacephei.com/vosk/models")
        append(" (small en-us is ~41 MB zipped, small hi is ~44 MB).")
    }
}
