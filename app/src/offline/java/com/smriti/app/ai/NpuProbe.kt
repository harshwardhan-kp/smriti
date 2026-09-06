package com.smriti.app.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import com.smriti.app.ModelProvisioner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hardware probe to determine whether Gemma 3n E4B runs on the Hexagon NPU
 * of an iQOO 15 (SM8850) via LiteRT-LM, and how fast, versus GPU and CPU.
 *
 * Triggerable via adb:
 *     adb shell am start -n com.smriti.app/.MainActivity --ez smriti_npu true
 *     adb logcat -s SmritiNPU:V
 */
object NpuProbe {

    const val EXTRA = "smriti_npu"
    private const val TAG = "SmritiNPU"

    private val PROMPT = """
        Extract the action items from this note and reply with a single JSON object only:
        "Rohit ships the API by Friday and we need two hundred more units from Sharma Traders."
    """.trimIndent()

    private class ProbeTarget(
        val name: String,
        val createBackend: () -> Backend
    )

    fun run(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            Log.i(TAG, "=== NPU PROBE START ===")
            try {
                val model = ModelProvisioner.locate(context)
                if (model == null) {
                    Log.e(TAG, "No model found via ModelProvisioner")
                    return@launch
                }
                val modelPath = model.file.absolutePath
                Log.i(TAG, "model: $modelPath (${model.label})")

                val targets = listOf(
                    ProbeTarget("NPU") {
                        Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
                    },
                    ProbeTarget("GPU") {
                        Backend.GPU()
                    },
                    ProbeTarget("CPU") {
                        Backend.CPU()
                    }
                )

                for (target in targets) {
                    // a. Log "ATTEMPT <name>" BEFORE constructing anything. THIS IS CRITICAL:
                    // a native backend can segfault and take the whole process down with it.
                    Log.i(TAG, "ATTEMPT ${target.name}")
                    var engine: Engine? = null
                    var session: Session? = null
                    try {
                        val backend = target.createBackend()
                        engine = Engine(EngineConfig(modelPath = modelPath, backend = backend))

                        val initStart = System.currentTimeMillis()
                        engine.initialize()
                        val initMs = System.currentTimeMillis() - initStart
                        Log.i(TAG, "  init: $initMs ms")

                        session = engine.createSession(SessionConfig())
                        val genStart = System.currentTimeMillis()
                        val out = session.generateContent(listOf(InputData.Text(PROMPT)))
                        val genMs = System.currentTimeMillis() - genStart
                        Log.i(TAG, "  generate: $genMs ms")

                        out.lines().forEach { Log.i(TAG, "  $it") }
                        Log.i(TAG, "  RESULT ${target.name}: OK")
                    } catch (t: Throwable) {
                        Log.e(TAG, "  RESULT ${target.name}: FAILED ${t.javaClass.simpleName}: ${t.message}")
                    } finally {
                        try {
                            session?.close()
                        } catch (closeEx: Throwable) {
                            Log.w(TAG, "Failed to close session for ${target.name}: ${closeEx.message}")
                        }
                        try {
                            engine?.close()
                        } catch (closeEx: Throwable) {
                            Log.w(TAG, "Failed to close engine for ${target.name}: ${closeEx.message}")
                        }
                    }
                }
            } finally {
                Log.i(TAG, "=== NPU PROBE END ===")
            }
        }
    }
}
