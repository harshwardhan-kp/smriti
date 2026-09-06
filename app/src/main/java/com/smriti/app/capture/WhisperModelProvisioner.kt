package com.smriti.app.capture

import android.content.Context
import java.io.File

/**
 * Locates Whisper ONNX model files on-device for sherpa-onnx.
 *
 * Checks the app-private files dir first, then the well-known tmp locations
 * used during development. A directory is only accepted as valid if it contains
 * all three required components: an encoder ONNX file, a decoder ONNX file, and
 * a tokens file. When both int8 and non-int8 variants are present, the int8
 * files are preferred for reduced memory and footprint.
 */
object WhisperModelProvisioner {

    data class Paths(val encoder: File, val decoder: File, val tokens: File)

    fun locate(context: Context): Paths? {
        val candidates = listOf(
            File(context.filesDir, "asr"),
            File("/data/local/tmp/asr")
        )
        for (dir in candidates) {
            if (!dir.isDirectory) continue
            val paths = resolvePaths(dir)
            if (paths != null) return paths
        }
        return null
    }

    internal fun resolvePaths(dir: File): Paths? {
        val files = dir.listFiles() ?: return null
        val readableFiles = files.filter { it.isFile && it.canRead() }

        val encoders = readableFiles
            .filter { it.name.contains("encoder", ignoreCase = true) && it.name.endsWith(".onnx", ignoreCase = true) }
            .sortedBy { it.name }
        val decoders = readableFiles
            .filter { it.name.contains("decoder", ignoreCase = true) && it.name.endsWith(".onnx", ignoreCase = true) }
            .sortedBy { it.name }
        val tokensFiles = readableFiles
            .filter { it.name.contains("tokens", ignoreCase = true) && it.name.endsWith(".txt", ignoreCase = true) }
            .sortedBy { it.name }

        val encoder = encoders.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: encoders.firstOrNull()
        val decoder = decoders.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: decoders.firstOrNull()
        val tokens = tokensFiles.firstOrNull()

        if (encoder != null && decoder != null && tokens != null) {
            return Paths(encoder = encoder, decoder = decoder, tokens = tokens)
        }
        return null
    }

    fun missingMessage(): String = buildString {
        appendLine("Whisper model not found. Searched:")
        appendLine("  - <app filesDir>/asr  (Context.filesDir/asr)")
        appendLine("  - /data/local/tmp/asr")
        appendLine()
        appendLine("A valid Whisper model directory must contain readable files matching:")
        appendLine("  - *encoder*.onnx")
        appendLine("  - *decoder*.onnx")
        appendLine("  - *tokens*.txt")
        appendLine()
        appendLine("Provision a model with:")
        appendLine("  adb shell mkdir -p /data/local/tmp/asr")
        appendLine("  adb push small-encoder.int8.onnx /data/local/tmp/asr/")
        appendLine("  adb push small-decoder.int8.onnx /data/local/tmp/asr/")
        appendLine("  adb push small-tokens.txt /data/local/tmp/asr/")
        appendLine()
        appendLine("Or push to the app-private location (requires run-as or rooted adb):")
        appendLine("  adb push small-encoder.int8.onnx /data/data/com.smriti.app/files/asr/")
        appendLine("  adb push small-decoder.int8.onnx /data/data/com.smriti.app/files/asr/")
        appendLine("  adb push small-tokens.txt /data/data/com.smriti.app/files/asr/")
        appendLine()
        append("Models from sherpa-onnx (e.g. sherpa-onnx-whisper-small).")
    }
}
