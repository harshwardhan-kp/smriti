package com.smriti.app.capture

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Headless verification of on-device Whisper ASR using sherpa-onnx.
 *
 * Triggerable via adb:
 *     adb shell am start -n com.smriti.app/.MainActivity --ez smriti_asrtest true
 *     adb logcat -s SmritiAsr:V
 */
object AsrSelfTest {

    const val EXTRA = "smriti_asrtest"
    private const val TAG = "SmritiAsr"

    fun run(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            Log.i(TAG, "=== ASR SELF TEST START ===")
            try {
                val paths = WhisperModelProvisioner.locate(context)
                if (paths == null) {
                    Log.e(TAG, "Whisper model not found via WhisperModelProvisioner")
                    return@launch
                }
                Log.i(TAG, "model: ${paths.encoder.parent} (${paths.encoder.name})")

                val loadStart = System.currentTimeMillis()
                try {
                    SherpaWhisperAsr.getOrLoadRecognizer(context)
                } catch (t: Throwable) {
                    val loadMs = System.currentTimeMillis() - loadStart
                    Log.e(TAG, "load FAILED after ${loadMs} ms: ${t.javaClass.simpleName}: ${t.message}")
                    return@launch
                }
                val loadMs = System.currentTimeMillis() - loadStart
                Log.i(TAG, "load: $loadMs ms")

                val testDir = File("/data/local/tmp/asr/test_wavs")
                val wavFiles = testDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".wav", ignoreCase = true)
                }?.sortedBy { it.name } ?: emptyList()

                for (wav in wavFiles) {
                    Log.i(TAG, "file: ${wav.name}")
                    try {
                        val start = System.currentTimeMillis()
                        val transcript = SherpaWhisperAsr.transcribeFile(context, wav)
                        val ms = System.currentTimeMillis() - start
                        Log.i(TAG, "  ms: $ms")
                        Log.i(TAG, "  text: $transcript")
                    } catch (t: Throwable) {
                        Log.e(TAG, "  FAILED ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
            } finally {
                Log.i(TAG, "=== ASR SELF TEST END ===")
            }
        }
    }
}
