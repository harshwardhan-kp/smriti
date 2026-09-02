package com.smriti.app.ai

import android.content.Context
import android.util.Log
import com.smriti.app.BuildConfig
import java.io.File
import java.util.Properties

/**
 * Where the devcloud flavor gets its API keys.
 *
 * Baking a key into `BuildConfig` is fine on a developer's own machine and NOT fine in anything
 * anyone else can download. Verified on 2026-09-01: the keys compiled into a debug APK are
 * absent from a raw scan of the `.apk` — because the dex is deflated inside the zip — but
 * decompress `classes*.dex` and both keys are plainly there. A published release asset is a
 * published credential.
 *
 * So a distribution build is made with `-PdistributionBuild=true`, which blanks the BuildConfig
 * fields, and the tester supplies keys at runtime instead:
 *
 *     printf 'museApiKey=...\ngroqApiKey=...\n' > keys.properties
 *     adb push keys.properties /data/local/tmp/smriti-keys.properties
 *
 * Lookup order: BuildConfig (developer build) → /data/local/tmp → app-private storage.
 * Nothing here is a secret store; it is a way to keep credentials out of a shared binary.
 */
object RuntimeKeys {

    private const val TAG = "SmritiKeys"
    private const val TMP_PATH = "/data/local/tmp/smriti-keys.properties"
    private const val PRIVATE_NAME = "smriti-keys.properties"

    @Volatile
    private var cached: Properties? = null

    fun muse(context: Context): String = get(context, "museApiKey", BuildConfig.MUSE_API_KEY)

    fun groq(context: Context): String = get(context, "groqApiKey", BuildConfig.GROQ_API_KEY)

    private fun get(context: Context, name: String, compiledIn: String): String {
        if (compiledIn.isNotBlank()) return compiledIn
        return load(context).getProperty(name, "").trim()
    }

    private fun load(context: Context): Properties {
        cached?.let { return it }
        val props = Properties()
        val candidates = listOf(
            File(TMP_PATH),
            File(context.filesDir, PRIVATE_NAME)
        )
        for (file in candidates) {
            try {
                if (file.isFile && file.canRead()) {
                    file.inputStream().use { props.load(it) }
                    Log.i(TAG, "loaded runtime keys from ${file.absolutePath}")
                    break
                }
            } catch (t: Throwable) {
                Log.w(TAG, "could not read ${file.absolutePath}: ${t.message}")
            }
        }
        cached = props
        return props
    }

    /** Message shown when a key is missing, naming exactly how to supply one. */
    fun missingMessage(which: String): String = """
        No $which API key.

        This build ships without credentials on purpose — a key compiled into a shared APK is a
        published key. Supply one at runtime:

          printf 'museApiKey=...\ngroqApiKey=...\n' > keys.properties
          adb push keys.properties $TMP_PATH

        Then restart the app. Or use the `offline` build, which needs no keys at all.
    """.trimIndent()
}
