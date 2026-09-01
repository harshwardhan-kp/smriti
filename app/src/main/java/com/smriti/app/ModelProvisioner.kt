package com.smriti.app

import android.content.Context
import java.io.File

/**
 * Finds whatever MediaPipe `.task` language model has been provisioned onto the device.
 *
 * The model is never bundled in the APK — it is 0.5 to 1.6 GB depending on which one is used,
 * and shipping it inside the package would put the APK far past anything installable.
 *
 * Deliberately model-agnostic. Gemma 3 1B is the preferred model but it is licence-gated on
 * HuggingFace; Qwen2.5 is Apache-2.0 and needs no acceptance. The app should run whichever is
 * present rather than hard-coding a filename it might not have, and it reports which one it
 * loaded so the UI can be honest about what is actually generating the text.
 */
object ModelProvisioner {

    private const val MIN_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB

    /** Searched in order; the first readable `.task` over 100 MB wins. */
    private fun searchDirs(context: Context) = listOf(
        File(context.filesDir, "models"),
        File("/data/local/tmp/llm")
    )

    /** Preferred first when several models are present. Substring match, case-insensitive. */
    private val preference = listOf("gemma3-1b", "gemma", "qwen2.5-1.5b", "qwen", "phi", "smollm")

    data class Model(val file: File, val label: String)

    fun locate(context: Context): Model? {
        val found = searchDirs(context)
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
            .filter { it.isFile && it.canRead() && it.name.endsWith(".task", ignoreCase = true) }
            .filter { it.length() > MIN_SIZE_BYTES }

        if (found.isEmpty()) return null

        val best = found.minByOrNull { file ->
            val idx = preference.indexOfFirst { file.name.contains(it, ignoreCase = true) }
            if (idx >= 0) idx else preference.size
        } ?: found.first()

        return Model(best, label(best))
    }

    /** A short human name for the status line, e.g. "Qwen2.5-0.5B-Instruct · 521 MB". */
    private fun label(file: File): String {
        val name = file.name
            .removeSuffix(".task")
            .substringBefore("_multi-prefill")
            .substringBefore("_seq")
        val mb = file.length() / (1024 * 1024)
        return "$name · $mb MB"
    }

    fun missingMessage(context: Context): String {
        val dirs = searchDirs(context).joinToString("\n") { "  ${it.absolutePath}" }
        return """
            No language model found.

            Searched for any *.task file over 100 MB in:
$dirs

            Push one with:
              adb shell mkdir -p /data/local/tmp/llm
              adb push <model>.task /data/local/tmp/llm/
              adb shell chmod 644 /data/local/tmp/llm/<model>.task

            Ungated option, no licence acceptance needed (Apache-2.0):
              litert-community/Qwen2.5-0.5B-Instruct
              Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task

            Everything else in the app works without this — capture, OCR, speech, timeline and
            keyword recall. Only structured extraction and answer synthesis need the model.
        """.trimIndent()
    }
}
