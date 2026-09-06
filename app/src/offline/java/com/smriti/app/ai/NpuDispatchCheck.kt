package com.smriti.app.ai

import android.os.Process
import android.util.Log
import java.io.File

/**
 * Tells the difference between LiteRT-LM running on the NPU and LiteRT-LM *saying* it is.
 *
 * `Backend.NPU()` asks the runtime to try the vendor dispatch path. When the dispatch shared
 * library is missing the runtime writes its complaint to the log, registers the XNNPack CPU
 * accelerator instead, and hands back an engine that initialises and generates perfectly well —
 * on the CPU. Nothing in the Kotlin API says so, so a caller that trusts a clean `initialize()`
 * will label a CPU run "NPU".
 *
 * Two signals are available to us, and either one saying no is enough:
 *
 *  1. Whether a `libLiteRtDispatch*.so` exists in the directory handed to `Backend.NPU()`, which
 *     is where the runtime looks for it. (`libjnidispatch.so` is JNA's and is not one of these.)
 *  2. What the native runtime wrote to the log while the engine was initialising. An app may read
 *     its own log entries without READ_LOGS, and these lines come from our own process.
 */
object NpuDispatchCheck {

    private const val TAG = "SmritiEngine"

    /** Lines the runtime emits when the NPU path is not there. Matched case-insensitively. */
    private val LOG_MARKERS = listOf(
        "No dispatch library found",
        "Failed to initialize Dispatch API",
        "NPU accelerator could not be loaded and registered"
    )

    /**
     * Puts a unique line in the log immediately before an NPU attempt, so that the scan afterwards
     * reads only what that attempt produced and not an older one.
     */
    fun mark(): String {
        val sentinel = "NPU-DISPATCH-WATCH-${System.nanoTime()}"
        Log.i(TAG, sentinel)
        return sentinel
    }

    /**
     * @return a short reason why an apparently successful NPU init did not actually reach the NPU,
     *         or null if nothing we can see says it didn't.
     */
    fun failureReason(nativeLibraryDir: String, sentinel: String): String? {
        val dispatchLibs = File(nativeLibraryDir).listFiles()
            ?.filter { it.name.startsWith("libLiteRtDispatch", ignoreCase = true) }
            .orEmpty()
        if (dispatchLibs.isEmpty()) {
            return "no libLiteRtDispatch*.so in $nativeLibraryDir"
        }

        for (line in logSince(sentinel)) {
            val marker = LOG_MARKERS.firstOrNull { line.contains(it, ignoreCase = true) }
            if (marker != null) return marker
        }
        return null
    }

    /** Our own process's log entries written after [sentinel]. Empty if the log cannot be read. */
    private fun logSince(sentinel: String): List<String> = try {
        val process = ProcessBuilder("logcat", "-d", "-v", "brief", "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        process.destroy()
        val start = lines.indexOfLast { it.contains(sentinel) }
        if (start < 0) {
            Log.w(TAG, "dispatch check: sentinel not found in our own log; nothing to scan")
            emptyList()
        } else {
            lines.drop(start + 1)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "dispatch check: could not read our own log: ${t.message}")
        emptyList()
    }
}
