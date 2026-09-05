package com.smriti.app.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.smriti.app.ModelProvisioner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LiteRtLmHolder {

    private const val TAG = "SmritiEngine"

    @Volatile
    private var backend: LiteRtLmBackend? = null
    private val mutex = Mutex()

    suspend fun get(context: Context): Result<LiteRtLmBackend> {
        val current = backend
        if (current != null) {
            return Result.success(current)
        }

        return mutex.withLock {
            val doubleCheck = backend
            if (doubleCheck != null) {
                return@withLock Result.success(doubleCheck)
            }

            withContext(Dispatchers.IO) {
                val model = ModelProvisioner.locate(context)
                    ?: return@withContext Result.failure(
                        ModelMissingException(ModelProvisioner.missingMessage(context))
                    )

                val modelPath = model.file.absolutePath
                val nativeLibDir = context.applicationInfo.nativeLibraryDir

                val targets: List<Pair<String, () -> Backend>> = listOf(
                    "NPU" to { Backend.NPU(nativeLibraryDir = nativeLibDir) },
                    "GPU" to { Backend.GPU() },
                    "CPU" to { Backend.CPU() }
                )

                var lastError: Throwable? = null
                for ((name, createBackend) in targets) {
                    Log.i(TAG, "ATTEMPT $name")
                    var engine: Engine? = null
                    try {
                        val nativeBackend = createBackend()
                        engine = Engine(EngineConfig(modelPath = modelPath, backend = nativeBackend))

                        val initStart = System.currentTimeMillis()
                        engine.initialize()
                        val initMs = System.currentTimeMillis() - initStart
                        Log.i(TAG, "RESULT $name: OK (${initMs}ms)")

                        val label = "$name · ${model.label}"
                        val litertBackend = LiteRtLmBackend(engine = engine, label = label)
                        backend = litertBackend
                        return@withContext Result.success(litertBackend)
                    } catch (t: Throwable) {
                        Log.w(TAG, "RESULT $name: FAILED ${t.javaClass.simpleName}: ${t.message}")
                        lastError = t
                        try {
                            engine?.close()
                        } catch (closeEx: Throwable) {
                            Log.w(TAG, "Failed to close engine for $name: ${closeEx.message}")
                        }
                    }
                }

                Result.failure(lastError ?: IllegalStateException("All LiteRT-LM backends failed"))
            }
        }
    }

    fun reset() {
        val old = backend
        backend = null
        old?.close()
    }
}
