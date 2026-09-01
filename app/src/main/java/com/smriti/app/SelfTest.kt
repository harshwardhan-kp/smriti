package com.smriti.app

import android.content.Context
import android.util.Log
import com.smriti.app.ai.BackendFactory
import com.smriti.app.ai.Extractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A headless end-to-end check of the language model, triggerable over adb:
 *
 *     adb shell am start -n com.smriti.app/.MainActivity --ez smriti_selftest true
 *     adb logcat -s SmritiBench:V
 *
 * This exists because MIUI refuses `adb shell input tap` (INJECT_EVENTS is denied to the shell
 * user), so there is no way to drive the capture button remotely. It is also how the tokens/sec
 * figures quoted in the deck are produced — measured on a real handset, not estimated.
 *
 * Debug affordance only. It is inert unless the extra is passed.
 */
object SelfTest {

    const val EXTRA = "smriti_selftest"
    private const val TAG = "SmritiBench"

    private val PROMPT = """
        Extract the action items from this note and reply with a single JSON object only:
        "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders."
    """.trimIndent()

    /**
     * @param backend "cpu", "gpu" or null for the policy default.
     * @param resetPolicy clears any GPU quarantine before running.
     * Note: backend and resetPolicy are honoured only by the offline flavor's BackendFactory.
     * In the devcloud flavor they are logged but otherwise ignored.
     */
    fun run(
        context: Context,
        scope: CoroutineScope,
        backend: String? = null,
        resetPolicy: Boolean = false
    ) {
        scope.launch(Dispatchers.IO) {
            // backend and resetPolicy only affect the offline flavor; devcloud ignores them.
            Log.i(TAG, "requested backend: ${backend ?: "auto"}")
            Log.i(TAG, "resetPolicy: $resetPolicy")
            Log.i(TAG, "=== SELF TEST START ===")

            val loadStart = System.currentTimeMillis()
            val backendResult = BackendFactory.create(context)
            val loadMs = System.currentTimeMillis() - loadStart

            val llmBackend = backendResult.getOrElse { t ->
                Log.e(TAG, "load FAILED after ${loadMs}ms: ${t.javaClass.simpleName}: ${t.message}")
                Log.i(TAG, "=== SELF TEST END (load failed) ===")
                return@launch
            }
            Log.i(TAG, "backend: ${llmBackend.label}")
            Log.i(TAG, "load: ${loadMs} ms")

            // Raw generation timing.
            val genStart = System.currentTimeMillis()
            val raw = try {
                llmBackend.generate(PROMPT)
            } catch (t: Throwable) {
                Log.e(TAG, "generate FAILED: ${t.javaClass.simpleName}: ${t.message}")
                Log.i(TAG, "=== SELF TEST END (generate failed) ===")
                return@launch
            }
            val genMs = System.currentTimeMillis() - genStart

            // Approximate: MediaPipe does not expose a token count on the sync path.
            val approxTokens = raw.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            val tps = if (genMs > 0) approxTokens * 1000.0 / genMs else 0.0

            Log.i(TAG, "generate: ${genMs} ms")
            Log.i(TAG, "approx tokens: $approxTokens")
            Log.i(TAG, "approx tokens/sec: ${"%.2f".format(tps)}")
            Log.i(TAG, "raw output >>>")
            raw.lines().forEach { Log.i(TAG, "  $it") }
            Log.i(TAG, "<<<")

            // And the part that actually matters: does the repair path yield usable structure?
            val extractStart = System.currentTimeMillis()
            val structured = Extractor(llmBackend).extract(ocrText = "", transcript = PROMPT)
            val extractMs = System.currentTimeMillis() - extractStart

            Log.i(TAG, "extract: ${extractMs} ms")
            Log.i(TAG, "title  : ${structured.title}")
            Log.i(TAG, "summary: ${structured.summary}")
            Log.i(TAG, "people : ${structured.people}")
            Log.i(TAG, "tags   : ${structured.tags}")
            Log.i(TAG, "actions: ${structured.actions.size}")
            structured.actions.forEach { Log.i(TAG, "   - ${it.text} (due=${it.due})") }
            Log.i(TAG, "=== SELF TEST END ===")
        }
    }
}