package com.smriti.app

import android.content.Context
import android.util.Log
import com.smriti.app.ai.EmbeddingBackfill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SeedTrigger {
    const val EXTRA = "smriti_seed"
    const val EXTRA_CLEAR = "smriti_seed_clear"

    fun handle(context: Context, scope: CoroutineScope, seed: Boolean, clear: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                if (clear) {
                    val c = DemoSeed.clear(context)
                    Log.i("SmritiSeed", "clear: $c")
                }
                if (seed) {
                    val s = DemoSeed.seed(context, force = true)
                    Log.i("SmritiSeed", "seed: $s")
                }
                // Seeded rows arrive without embeddings, and Recall needs at least three
                // embedded records before it uses semantic search at all. Without this the
                // demo corpus would silently be searched by keyword only.
                val embedded = EmbeddingBackfill.run(context)
                Log.i("SmritiSeed", "embeddings backfilled: $embedded")
            } catch (t: Throwable) {
                Log.e("SmritiSeed", "error: ${t.message}", t)
            }
        }
    }
}
