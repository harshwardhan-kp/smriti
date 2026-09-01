package com.smriti.app.ai

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device text embedding generator using MediaPipe's TextEmbedder task.
 *
 * The .tflite embedder model ships in app assets and is a few megabytes, unlike the
 * language model which is provisioned separately at ~550 MB.
 */
class Embedder private constructor(private val embedder: TextEmbedder) : AutoCloseable {

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        val truncated = text.take(1000)
        val result = embedder.embed(truncated)
        result.embeddingResult().embeddings()[0].floatEmbedding()
    }

    override fun close() {
        embedder.close()
    }

    companion object {
        fun create(
            context: Context,
            modelAssetPath: String = "embedder.tflite"
        ): Result<Embedder> {
            return try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelAssetPath)
                    .build()

                val options = TextEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setL2Normalize(true)
                    .setQuantize(false)
                    .build()

                val textEmbedder = TextEmbedder.createFromOptions(context, options)
                Result.success(Embedder(textEmbedder))
            } catch (t: Throwable) {
                Result.failure(
                    IllegalStateException(
                        "Failed to initialize Embedder with asset '$modelAssetPath'. The model asset may be absent during development.",
                        t
                    )
                )
            }
        }
    }
}