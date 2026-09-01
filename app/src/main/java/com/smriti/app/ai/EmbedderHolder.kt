package com.smriti.app.ai

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One [Embedder] for the process.
 *
 * The Universal Sentence Encoder asset is ~6 MB and loading it costs real time, so building a
 * new one per capture would add a visible stall to the one interaction that has to feel instant.
 *
 * Returns null rather than throwing when the asset is missing or fails to load: recall degrades
 * to keyword scoring, which is worse but still useful, and a capture is never lost over it.
 */
object EmbedderHolder {

    @Volatile
    private var instance: Embedder? = null

    @Volatile
    private var failed = false

    private val mutex = Mutex()

    suspend fun get(context: Context): Embedder? {
        instance?.let { return it }
        if (failed) return null
        return mutex.withLock {
            instance ?: run {
                val result = Embedder.create(context.applicationContext)
                val built = result.getOrNull()
                if (built == null) failed = true else instance = built
                built
            }
        }
    }
}
