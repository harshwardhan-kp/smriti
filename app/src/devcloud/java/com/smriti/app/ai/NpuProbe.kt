package com.smriti.app.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope

/**
 * No-op implementation for the devcloud flavor.
 * The NPU hardware probe only applies to on-device models in the offline flavor.
 */
object NpuProbe {

    const val EXTRA = "smriti_npu"
    private const val TAG = "SmritiNPU"

    fun run(context: Context, scope: CoroutineScope) {
        Log.i(TAG, "NPU probe is offline-only; devcloud flavor does not run on-device models.")
    }
}
