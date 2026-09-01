package com.smriti.app.ai

import android.content.Context
import android.util.Log
import com.smriti.app.data.Converters
import com.smriti.app.data.SmritiDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gives an embedding to records that do not have one.
 *
 * Records can end up without embeddings for ordinary reasons: they were captured before the
 * embedder asset shipped, the seeded demo corpus was inserted without one, or the embedder
 * failed to load on that particular run. Recall needs at least three embedded records before it
 * will use semantic search at all, so a database full of un-embedded rows silently behaves as
 * though the feature does not exist.
 *
 * Safe to call repeatedly; it only touches rows where `embedding IS NULL`.
 */
object EmbeddingBackfill {

    private const val TAG = "SmritiBackfill"

    suspend fun run(context: Context): Int = withContext(Dispatchers.IO) {
        val dao = SmritiDb.get(context).recordDao()
        val pending = dao.recordsMissingEmbedding()
        if (pending.isEmpty()) return@withContext 0

        val embedder = EmbedderHolder.get(context)
        if (embedder == null) {
            Log.w(TAG, "${pending.size} records need embeddings but the embedder is unavailable")
            return@withContext 0
        }

        val converters = Converters()
        var done = 0
        for (record in pending) {
            val text = listOf(record.title, record.summary, record.transcript, record.ocrText)
                .filter { it.isNotBlank() }
                .joinToString(" \n ")
                .take(1000)
            if (text.isBlank()) continue
            try {
                dao.setEmbedding(record.id, converters.fromFloatArray(embedder.embed(text)))
                done++
            } catch (t: Throwable) {
                Log.w(TAG, "embedding failed for record ${record.id}: ${t.message}")
            }
        }
        Log.i(TAG, "backfilled $done of ${pending.size} records")
        done
    }
}
