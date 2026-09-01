FILE: app/src/main/java/com/smriti/app/capture/VoskModelProvisioner.kt
```kotlin
package com.smriti.app.capture

import android.content.Context
import java.io.File

/**
 * Locates a Vosk model directory on-device.
 *
 * Checks the app-private files dir first, then the well-known tmp locations
 * used during development. A directory is only accepted as a valid model if it
 * contains `am/` or `conf/model.conf`, so a half-extracted download does not
 * crash the native layer.
 */
object VoskModelProvisioner {

    fun locate(context: Context): File? {
        val candidates = listOf(
            File(context.filesDir, "vosk/model"),
            File("/data/local/tmp/vosk/model"),
            File("/data/local/tmp/vosk/vosk-model-small-en-us-0.15"),
            File("/data/local/tmp/vosk/vosk-model-small-hi-0.22")
        )
        for (dir in candidates) {
            if (!dir.isDirectory) continue
            if (isValidModelDir(dir)) return dir
        }
        return null
    }

    private fun isValidModelDir(dir: File): Boolean {
        return File(dir, "am").isDirectory || File(dir, "conf/model.conf").isFile
    }

    fun missingMessage(): String = buildString {
        appendLine("Vosk model not found. Searched:")
        appendLine("  - <app filesDir>/vosk/model  (Context.filesDir/vosk/model)")
        appendLine("  - /data/local/tmp/vosk/model")
        appendLine("  - /data/local/tmp/vosk/vosk-model-small-en-us-0.15")
        appendLine("  - /data/local/tmp/vosk/vosk-model-small-hi-0.22")
        appendLine()
        appendLine("A valid Vosk model directory must contain a subdirectory \"am\" or a file \"conf/model.conf\".")
        appendLine()
        appendLine("Provision a model with:")
        appendLine("  adb shell mkdir -p /data/local/tmp/vosk")
        appendLine("  adb push vosk-model-small-en-us-0.15 /data/local/tmp/vosk/model")
        appendLine()
        appendLine("Or push to the app-private location (requires run-as or rooted adb):")
        appendLine("  adb push vosk-model-small-en-us-0.15 /data/data/com.smriti.app/files/vosk/model")
        appendLine()
        append("Models from https://alphacephei.com/vosk/models")
        append(" (small en-us is ~41 MB zipped, small hi is ~44 MB).")
    }
}
```
FILE: app/src/offline/java/com/smriti/app/capture/VoskAsr.kt
```kotlin
package com.smriti.app.capture

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.FileInputStream

class VoskAsr(private val context: Context) : Asr, PushToTalk {

    private val lock = Any()

    @Volatile
    private var pending: CompletableDeferred<Unit>? = null
    private var recorder: AudioRecorder? = null

    companion object {
        @Volatile
        private var cachedModel: Model? = null
        private val modelMutex = Mutex()

        @Volatile
        private var logLevelSet = false
    }

    private suspend fun getOrLoadModel(): Model {
        cachedModel?.let { return it }
        return modelMutex.withLock {
            cachedModel?.let { return@withLock it }
            if (!logLevelSet) {
                try {
                    LibVosk.setLogLevel(LogLevel.WARNINGS)
                } catch (_: Exception) {
                    // ignore if native lib not yet loaded
                }
                logLevelSet = true
            }
            val dir = VoskModelProvisioner.locate(context)
                ?: throw AsrUnavailableException(VoskModelProvisioner.missingMessage())
            try {
                val m = Model(dir.absolutePath)
                cachedModel = m
                m
            } catch (e: AsrUnavailableException) {
                throw e
            } catch (e: Exception) {
                throw AsrUnavailableException(
                    "Failed to load Vosk model from ${dir.absolutePath}: ${e.message}",
                    e
                )
            }
        }
    }

    override suspend fun transcribe(maxMillis: Long): String = withContext(Dispatchers.IO) {
        try {
            val model = getOrLoadModel()

            val deferred = CompletableDeferred<Unit>()
            val rec = AudioRecorder(context)
            synchronized(lock) {
                pending = deferred
                recorder = rec
            }

            rec.start()

            withTimeoutOrNull(maxMillis) {
                deferred.await()
            }

            val wav = try {
                rec.stop()
            } catch (_: Exception) {
                null
            } finally {
                synchronized(lock) {
                    pending = null
                    recorder = null
                }
            }

            if (wav == null || !wav.exists()) return@withContext ""

            var recognizer: Recognizer? = null
            try {
                recognizer = Recognizer(model, 16000f)
                FileInputStream(wav).use { fis ->
                    var skipped = 0L
                    while (skipped < 44) {
                        val s = fis.skip(44 - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    val buf = ByteArray(4096)
                    var n: Int
                    while (fis.read(buf).also { n = it } != -1) {
                        recognizer.acceptWaveForm(buf, n)
                    }
                }
                val jsonStr = recognizer.getFinalResult()
                val text = try {
                    JSONObject(jsonStr).optString("text", "")
                } catch (_: Exception) {
                    ""
                }
                text.trim()
            } finally {
                try {
                    recognizer?.close()
                } catch (_: Exception) {
                }
                try {
                    if (wav.exists()) wav.delete()
                } catch (_: Exception) {
                }
            }
        } catch (e: AsrUnavailableException) {
            synchronized(lock) {
                pending = null
                recorder = null
            }
            throw e
        } catch (e: Exception) {
            synchronized(lock) {
                pending = null
                recorder = null
            }
            throw AsrUnavailableException(e.message ?: "Vosk transcription failed", e)
        }
    }

    override fun stopListening() {
        synchronized(lock) {
            val d = pending
            if (d != null && !d.isCompleted) {
                d.complete(Unit)
            }
        }
    }

    override fun cancel() {
        synchronized(lock) {
            try {
                recorder?.cancel()
            } catch (_: Exception) {
            }
            val d = pending
            if (d != null && !d.isCompleted) {
                d.complete(Unit)
            }
            pending = null
            recorder = null
        }
    }
}
```
FILE: app/src/offline/java/com/smriti/app/capture/AsrFactory.kt
```kotlin
package com.smriti.app.capture

import android.content.Context

/**
 * Offline flavor ASR factory.
 *
 * Prefers [VoskAsr] when a Vosk model is present on-device; falls back to
 * [PlatformAsr] otherwise.
 *
 * The platform recogniser (Android SpeechRecognizer) is known to fail with
 * LANGUAGE_PACK_ERROR (code 13) on devices without an offline speech language
 * pack installed — measured 2026-09-01 on Moto G05, stock Android 15: the
 * recogniser returns error 13 "Failed to get language pack of required locale"
 * and voice input is dead in that state. Vosk is therefore preferred whenever
 * a model is present because it provides fully offline recognition with no
 * language-pack dependency.
 */
object AsrFactory {
    fun create(context: Context): Asr {
        return if (VoskModelProvisioner.locate(context) != null) {
            VoskAsr(context)
        } else {
            PlatformAsr(context)
        }
    }
}
```
FILE: app/build.gradle.kts
```kotlin
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val museKey = run {
    val props = Properties()
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        localProps.inputStream().use { props.load(it) }
        props.getProperty("museApiKey", "")?.trim() ?: ""
    } else {
        ""
    }
}

val groqKey = run {
    val props = Properties()
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        localProps.inputStream().use { props.load(it) }
        props.getProperty("groqApiKey", "")?.trim() ?: ""
    } else {
        ""
    }
}

android {
    namespace = "com.smriti.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smriti.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    flavorDimensions += "llm"
    productFlavors {
        create("offline") {
            isDefault = true
        }
        create("devcloud") {
            applicationIdSuffix = ".devcloud"
            versionNameSuffix = "-devcloud"
            buildConfigField("String", "MUSE_API_KEY", "\"${museKey}\"")
            buildConfigField("String", "GROQ_API_KEY", "\"${groqKey}\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.google.mediapipe:tasks-genai:0.10.35")

    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")

    implementation("com.google.code.gson:gson:2.11.0")

    // On-device text embeddings for offline recall (brute-force cosine, no vector DB).
    implementation("com.google.mediapipe:tasks-text:0.10.35")

    // Vosk provides fully offline speech recognition and requires no NDK build.
    implementation("com.alphacephei:vosk-android:0.3.75")

    testImplementation("junit:junit:4.13.2")
}

/**
 * Smriti's entire pitch is that the app cannot reach the network. That claim is only true if
 * nothing re-introduces INTERNET through a transitive dependency's manifest — which ML Kit's
 * datatransport backend did, silently, on 2026-09-01.
 *
 * This task reads the MERGED manifest, which is what actually ships, and fails the build if
 * either network permission is present. A green build is now proof of the claim.
 */
val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE"
)

tasks.register("assertNoNetworkPermission") {
    group = "verification"
    description = "Fails if a network permission survives manifest merging."
    doLast {
        val allManifests = fileTree(layout.buildDirectory.dir("intermediates")) {
            include("**/merged_manifest*/**/AndroidManifest.xml")
            include("**/packaged_manifests/**/AndroidManifest.xml")
        }.files
        val manifests = allManifests.filter { it.path.contains("offline") }
        require(manifests.isNotEmpty()) { "No merged manifest found - run a build first." }

        val offenders = mutableListOf<String>()
        manifests.forEach { file ->
            val text = file.readText()
            forbiddenPermissions.forEach { perm ->
                if (text.contains(perm)) offenders += "$perm in ${file.name} (${file.parentFile.name})"
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Network permission leaked into the merged manifest:\n" +
                    offenders.joinToString("\n") { "  - $it" } +
                    "\n\nFind the source with:\n" +
                    "  grep -B2 'uses-permission#android.permission.INTERNET' " +
                    "app/build/outputs/logs/manifest-merger-debug-report.txt\n" +
                    "then strip it with tools:node=\"remove\" in app/src/main/AndroidManifest.xml."
            )
        }
        logger.lifecycle("assertNoNetworkPermission: clean - ${manifests.size} merged manifest(s) carry no network permission.")
    }
}

afterEvaluate {
    tasks.named("assembleOfflineDebug") { finalizedBy("assertNoNetworkPermission") }
    tasks.findByName("assembleOfflineRelease")?.finalizedBy("assertNoNetworkPermission")
}
```

