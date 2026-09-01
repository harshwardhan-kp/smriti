package com.smriti.app

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.smriti.app.ai.BackendPolicy
import com.smriti.app.ai.Extractor
import com.smriti.app.ai.LlmHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A headless end-to-end check of the on-device language model, triggerable over adb:
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
     */
    fun run(
        context: Context,
        scope: CoroutineScope,
        backend: String? = null,
        resetPolicy: Boolean = false
    ) {
        scope.launch(Dispatchers.IO) {
            if (resetPolicy) {
                BackendPolicy(context).reset()
                Log.i(TAG, "backend policy reset")
            }
            val forced = when (backend?.lowercase()) {
                "cpu" -> LlmInference.Backend.CPU
                "gpu" -> LlmInference.Backend.GPU
                else -> null
            }
            Log.i(TAG, "requested backend: ${backend ?: "auto"}")
            Log.i(TAG, "=== SELF TEST START ===")

            val model = ModelProvisioner.locate(context)
            if (model == null) {
                Log.e(TAG, "no model found")
                Log.e(TAG, ModelProvisioner.missingMessage(context))
                Log.i(TAG, "=== SELF TEST END (no model) ===")
                return@launch
            }
            Log.i(TAG, "model: ${model.label}")
            Log.i(TAG, "path : ${model.file.absolutePath}")

            val loadStart = System.currentTimeMillis()
            val engineResult = LlmHolder.get(context, forced)
            val loadMs = System.currentTimeMillis() - loadStart

            val engine = engineResult.getOrElse { t ->
                Log.e(TAG, "load FAILED after ${loadMs}ms: ${t.javaClass.simpleName}: ${t.message}")
                Log.i(TAG, "=== SELF TEST END (load failed) ===")
                return@launch
            }
            Log.i(TAG, "backend: ${engine.backend}")
            Log.i(TAG, "load: ${loadMs} ms")

            // Raw generation timing.
            val genStart = System.currentTimeMillis()
            val raw = try {
                engine.generate(PROMPT)
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
            val structured = Extractor(engine).extract(ocrText = "", transcript = PROMPT)
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
