package com.smriti.app.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemma 4 E4B on the Hexagon NPU, through Genie's C API.
 *
 * Measured on an iQOO 15 (SM8850, HTP v81) with QAIRT 2.50: 16.2 tok/s decode, 475 tok/s
 * prefill, over seven runs. That the DSP is genuinely doing the work was established by a
 * negative control — pointing ADSP_LIBRARY_PATH at a directory that does not exist makes the
 * dialog fail to create in 4.6 s rather than run slower, so there is no CPU fallback path to
 * be quietly taken. See PROGRESS.md.
 *
 * The dialog handle is held for the life of the process on purpose. Creating it costs 4.3-32.0 s
 * because ~4 GB of context binaries and LUTs are mmap'd; a query against a live handle costs
 * only the generation.
 */
class GenieBackend(
    private var handle: Long,
    override val label: String
) : LlmBackend {

    private companion object {
        const val TAG = "SmritiGenie"

        /**
         * Gemma 4's turn structure, taken byte for byte from the bundle's own sample_prompt.txt.
         * Without it the model has no turn to complete and rambles.
         */
        const val TEMPLATE =
            "<bos><|turn>system\nYou are a helpful AI assistant.<turn|>\n" +
            "<|turn>user\n%s<turn|>\n<|turn>model\n"
    }

    override suspend fun generate(prompt: String, maxTokens: Int, jsonSchema: String?): String =
        withContext(Dispatchers.IO) {
            val live = handle
            check(live != 0L) { "Genie dialog is closed" }

            // Each capture is independent, so the KV cache from the previous one is not context,
            // it is just 4096 tokens of someone else's note crowding this one out.
            GenieBridge.nativeReset(live)

            val started = System.currentTimeMillis()
            val answer = GenieBridge.nativeQuery(live, TEMPLATE.format(prompt))
                ?: throw IllegalStateException("Genie query failed: ${GenieBridge.nativeLastError()}")
            Log.i(TAG, "generate: ${System.currentTimeMillis() - started} ms, ${answer.length} chars")

            answer.trim()
        }

    override fun close() {
        val live = handle
        handle = 0L
        if (live != 0L) GenieBridge.nativeDestroy(live)
    }
}
