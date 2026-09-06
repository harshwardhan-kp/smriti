package com.smriti.app.ai

/**
 * Thin `external fun` surface over [genie_jni.cpp]. Lives in `main` rather than `offline`
 * because the native library is built for both flavours; the .so files it dlopens are what
 * only the offline flavour ships, so devcloud simply never calls this.
 *
 * All calls are serialised behind a mutex on the native side. Handles are opaque pointers.
 */
object GenieBridge {

    @Volatile
    private var loaded = false

    /** @return false if the JNI library itself is absent, which is not an error worth crashing on. */
    fun load(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("smriti_genie")
            loaded = true
            true
        } catch (t: UnsatisfiedLinkError) {
            false
        }
    }

    /** @return an opaque dialog handle, or 0 on failure — call [nativeLastError] for the reason. */
    external fun nativeCreate(configJson: String, adspLibraryPath: String): Long
    external fun nativeQuery(handle: Long, prompt: String): String?
    external fun nativeReset(handle: Long)
    external fun nativeDestroy(handle: Long)
    external fun nativeLastError(): String
}
