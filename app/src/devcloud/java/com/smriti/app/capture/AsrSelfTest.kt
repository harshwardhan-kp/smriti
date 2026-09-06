package com.smriti.app.capture

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope

/**
 * No-op implementation for the devcloud flavor.
 * The ASR self-test only applies to on-device models in the offline flavor.
 */
object AsrSelfTest {

    const val EXTRA = "smriti_asrtest"
    private const val TAG = "SmritiAsr"

    fun run(context: Context, scope: CoroutineScope) {
        Log.i(TAG, "ASR self-test is offline-only; devcloud flavor does not run on-device models.")
    }
}
