package com.smriti.app.ai

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Finds the Gemma 4 E4B QNN bundle on the device and prepares its config for this app.
 *
 * The bundle is 6.16 GB of context binaries and lookup tables and is provisioned by adb, the
 * same way the `.litertlm` model already is — `/data/local/tmp` is `drwxrwx--x`, so an app uid
 * can traverse it and read a 0666 file there. Nothing about it belongs in the APK.
 *
 *     adb shell mkdir -p /data/local/tmp/genie/model
 *     adb push <bundle>/. /data/local/tmp/genie/model/
 *     adb shell chmod -R a+rX /data/local/tmp/genie
 */
object GenieBundle {

    private const val TAG = "SmritiGenie"

    /** The prefix the shipped `genie_config_device.json` hard-codes for every asset it names. */
    private const val CONFIG_PREFIX = "/data/local/tmp/genie/model"

    private const val CONFIG_NAME = "genie_config_device.json"

    /** Files the dialog cannot be created without. `vision_encoder.bin` is deliberately not one. */
    private val REQUIRED = listOf(
        "genie_config_device.json", "htp_backend_ext_config.json", "tokenizer.json",
        "part1_of_4.bin", "part2_of_4.bin", "part3_of_4.bin", "part4_of_4.bin",
        "embedding_int16_lut.bin", "embed_token_int8_lut.bin"
    )

    /** Searched in order; the first directory holding a complete bundle wins. */
    private fun searchDirs(context: Context) = listOf(
        File(context.filesDir, "genie/model"),
        File("/data/local/tmp/genie/model")
    )

    data class Bundle(val dir: File, val configJson: String)

    /**
     * @return the bundle with its config rewritten to point at wherever it actually lives, or
     *         null if no complete bundle is present.
     */
    fun locate(context: Context): Bundle? {
        for (dir in searchDirs(context)) {
            val missing = REQUIRED.filterNot { File(dir, it).let { f -> f.isFile && f.canRead() } }
            if (missing.isEmpty()) {
                val raw = File(dir, CONFIG_NAME).readText()
                // The eight absolute paths in the config are all under one prefix, so following
                // the bundle wherever it was provisioned is a single substitution.
                val rewritten = raw.replace(CONFIG_PREFIX, dir.absolutePath)
                Log.i(TAG, "bundle at ${dir.absolutePath}")
                return Bundle(dir, rewritten)
            }
            if (dir.isDirectory) {
                Log.i(TAG, "incomplete bundle at ${dir.absolutePath}, missing: ${missing.joinToString()}")
            }
        }
        return null
    }

    /**
     * Genie only runs on Qualcomm silicon, and this bundle is compiled for HTP v81 specifically.
     * Checked before anything is loaded so a MediaTek handset never dlopens a QNN backend.
     */
    fun isSupportedSoc(): Boolean {
        // The iQOO 15 reports SOC_MANUFACTURER as "QTI" -- Qualcomm Technologies Inc -- not
        // "Qualcomm", so matching the full name silently skips the very device this was built
        // for. Both spellings are accepted, and Build.HARDWARE ("qcom") covers handsets older
        // than API 31, where SOC_MANUFACTURER does not exist at all.
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else ""
        val hardware = Build.HARDWARE.orEmpty()
        val supported = listOf("qualcomm", "qti", "qcom").any {
            soc.contains(it, ignoreCase = true) || hardware.contains(it, ignoreCase = true)
        }
        if (!supported) {
            Log.i(TAG, "SoC is '$soc' / hardware '$hardware', not Qualcomm — Genie skipped")
        }
        return supported
    }

    fun missingMessage(): String =
        """
        No Genie bundle found. Provision it with:
          adb shell mkdir -p /data/local/tmp/genie/model
          adb push <bundle>/* /data/local/tmp/genie/model/
          adb shell chmod -R a+rX /data/local/tmp/genie
        """.trimIndent()
}
