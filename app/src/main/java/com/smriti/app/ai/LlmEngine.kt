package com.smriti.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import com.smriti.app.ModelProvisioner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ModelMissingException(message: String) : Exception(message)

class LlmEngine private constructor(
    private val inference: LlmInference,
    val backend: String
) {
    suspend fun generate(prompt: String, maxTokens: Int = 512): String = withContext(Dispatchers.IO) {
        inference.generateResponse(prompt)
    }

    fun stream(prompt: String): Flow<String> = callbackFlow {
        val listener = ProgressListener<String> { partial, done ->
            if (!partial.isNullOrEmpty()) {
                trySend(partial)
            }
            if (done) {
                close()
            }
        }
        val future = inference.generateResponseAsync(prompt, listener)
        awaitClose {
            future.cancel(true)
        }
    }

    fun close() {
        inference.close()
    }

    companion object {
        suspend fun create(context: Context): Result<LlmEngine> = withContext(Dispatchers.IO) {
            val modelFile = ModelProvisioner.locate(context)
                ?: return@withContext Result.failure(
                    ModelMissingException(ModelProvisioner.missingMessage())
                )

            val modelPath = modelFile.absolutePath

            try {
                val gpuOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .build()
                val gpuInference = LlmInference.createFromOptions(context, gpuOptions)
                Result.success(LlmEngine(gpuInference, "GPU"))
            } catch (gpuError: Throwable) {
                try {
                    val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setPreferredBackend(LlmInference.Backend.CPU)
                        .build()
                    val cpuInference = LlmInference.createFromOptions(context, cpuOptions)
                    Result.success(LlmEngine(cpuInference, "CPU (GPU unavailable)"))
                } catch (cpuError: Throwable) {
                    Result.failure(cpuError)
                }
            }
        }
    }
}

object LlmHolder {
    @Volatile
    private var engine: LlmEngine? = null
    private val mutex = Mutex()

    suspend fun get(context: Context): Result<LlmEngine> {
        val current = engine
        if (current != null) {
            return Result.success(current)
        }

        return mutex.withLock {
            val doubleCheck = engine
            if (doubleCheck != null) {
                Result.success(doubleCheck)
            } else {
                val result = LlmEngine.create(context.applicationContext)
                result.onSuccess { engine = it }
                result
            }
        }
    }
}