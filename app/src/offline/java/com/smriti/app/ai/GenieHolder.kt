package com.smriti.app.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Creates the Genie dialog once and hands the same one to every caller, mirroring
 * [LiteRtLmHolder]. The dialog is the expensive thing — 4.3-32.0 s to create because of the
 * ~4 GB mmap — so a second one is never made.
 */
object GenieHolder {

    private const val TAG = "SmritiGenie"

    @Volatile
    private var backend: GenieBackend? = null
    private val mutex = Mutex()

    /** @return null when Genie is not applicable here — wrong SoC, no bundle, no JNI library. */
    suspend fun get(context: Context): GenieBackend? {
        backend?.let { return it }

        return mutex.withLock {
            backend?.let { return@withLock it }

            withContext(Dispatchers.IO) {
                if (!GenieBundle.isSupportedSoc()) return@withContext null

                if (!GenieBridge.load()) {
                    Log.i(TAG, "libsmriti_genie.so not present in this build")
                    return@withContext null
                }

                val bundle = GenieBundle.locate(context)
                if (bundle == null) {
                    Log.i(TAG, GenieBundle.missingMessage())
                    return@withContext null
                }

                // fastRPC loads libQnnHtpV81Skel.so onto the DSP from this directory. It is the
                // app's own native library directory because that is the one place an installed
                // APK is allowed to keep an executable file.
                val adspPath = context.applicationInfo.nativeLibraryDir

                Log.i(TAG, "ATTEMPT Genie NPU")
                val started = System.currentTimeMillis()
                val handle = GenieBridge.nativeCreate(bundle.configJson, adspPath)
                val initMs = System.currentTimeMillis() - started

                if (handle == 0L) {
                    // The negative control proved there is no CPU fallback here: without a
                    // reachable skel the dialog cannot be created at all. So a failure is a
                    // real failure, and the caller should fall through to LiteRT-LM.
                    Log.w(TAG, "RESULT Genie: FAILED after ${initMs}ms — ${GenieBridge.nativeLastError()}")
                    return@withContext null
                }

                Log.i(TAG, "RESULT Genie: OK (${initMs}ms)")
                GenieBackend(handle, "NPU · Gemma 4 E4B").also { backend = it }
            }
        }
    }

    fun reset() {
        val old = backend
        backend = null
        old?.close()
    }
}
