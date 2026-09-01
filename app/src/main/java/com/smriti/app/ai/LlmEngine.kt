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
    val backend: String,
    private val policy: BackendPolicy? = null
) {
    suspend fun generate(prompt: String, maxTokens: Int = 512): String = withContext(Dispatchers.IO) {
        val out = inference.generateResponse(prompt)
        // Surviving one full generation is what proves this backend is safe on this device.
        policy?.markGpuAttemptSurvived()
        out
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
        /**
         * Backend selection is a safety decision, not a performance one.
         *
         * Observed on a Redmi Note 10S (Mali-G76, MediaPipe 0.10.35, 2026-09-01): the GPU
         * backend initialises successfully and then dies with SIGSEGV at 0x0 inside
         * libllm_inference_engine_jni.so during generation. A native segfault cannot be caught
         * from Kotlin - the process is simply gone - so "try GPU, catch, fall back" does not
         * work. The only defence is to record that we are about to attempt GPU, and to notice
         * on the next launch that the attempt never completed. See [BackendPolicy].
         */
        suspend fun create(
            context: Context,
            forced: LlmInference.Backend? = null
        ): Result<LlmEngine> = withContext(Dispatchers.IO) {
            val model = ModelProvisioner.locate(context)
                ?: return@withContext Result.failure(
                    ModelMissingException(ModelProvisioner.missingMessage(context))
                )

            val modelPath = model.file.absolutePath

            val policy = BackendPolicy(context)
            val useGpu = when {
                forced == LlmInference.Backend.CPU -> false
                forced == LlmInference.Backend.GPU -> true
                else -> policy.gpuAllowed()
            }

            if (!useGpu) {
                return@withContext try {
                    val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setPreferredBackend(LlmInference.Backend.CPU)
                        .build()
                    val cpuInference = LlmInference.createFromOptions(context, cpuOptions)
                    val why = if (policy.gpuAllowed()) "" else " (GPU quarantined after a native crash)"
                    Result.success(LlmEngine(cpuInference, "CPU$why · ${model.label}"))
                } catch (t: Throwable) {
                    Result.failure(t)
                }
            }

            policy.markGpuAttemptStarted()
            try {
                val gpuOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .build()
                val gpuInference = LlmInference.createFromOptions(context, gpuOptions)
                Result.success(LlmEngine(gpuInference, "GPU · ${model.label}", policy))
            } catch (gpuError: Throwable) {
                try {
                    val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setPreferredBackend(LlmInference.Backend.CPU)
                        .build()
                    val cpuInference = LlmInference.createFromOptions(context, cpuOptions)
                    Result.success(LlmEngine(cpuInference, "CPU · ${model.label}"))
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

    suspend fun get(
        context: Context,
        forced: LlmInference.Backend? = null
    ): Result<LlmEngine> {
        val current = engine
        if (current != null) {
            return Result.success(current)
        }

        return mutex.withLock {
            val doubleCheck = engine
            if (doubleCheck != null) {
                Result.success(doubleCheck)
            } else {
                val result = LlmEngine.create(context.applicationContext, forced)
                result.onSuccess { engine = it }
                result
            }
        }
    }
}