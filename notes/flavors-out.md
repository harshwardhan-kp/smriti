FILE: app/src/main/java/com/smriti/app/ai/LlmBackend.kt
```kotlin
package com.smriti.app.ai

/**
 * Abstraction over on-device and cloud LLM backends.
 *
 * The shipping build must always be `offline`; `devcloud` exists solely so prompt work
 * does not cost 12-60 s per iteration on a handset.
 */
interface LlmBackend {
    val label: String
    suspend fun generate(prompt: String, maxTokens: Int = 512): String
    fun close() {}
}
```
FILE: app/src/main/java/com/smriti/app/ai/LocalLlmBackend.kt
```kotlin
package com.smriti.app.ai

class LocalLlmBackend(private val engine: LlmEngine) : LlmBackend {
    override val label: String get() = engine.backend
    override suspend fun generate(prompt: String, maxTokens: Int = 512): String = engine.generate(prompt, maxTokens)
    override fun close() = engine.close()
}
```
FILE: app/src/devcloud/java/com/smriti/app/ai/MuseBackend.kt
```kotlin
package com.smriti.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MuseBackend(
    private val apiKey: String,
    private val model: String = "muse-spark-1.2-contributor"
) : LlmBackend {

    override val label: String = "Muse Spark (cloud, dev only)"

    override suspend fun generate(prompt: String, maxTokens: Int = 512): String = withContext(Dispatchers.IO) {
        var lastIOException: IOException? = null
        var lastCode: Int? = null
        var lastBody: String? = null

        repeat(3) { attempt ->
            try {
                val url = URL("https://api.meta.ai/v1/messages")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 120000
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", maxTokens)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                conn.outputStream.use { os ->
                    os.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code in setOf(429, 500, 502, 503, 504)) {
                    lastCode = code
                    lastBody = try {
                        conn.errorStream?.bufferedReader()?.readText()
                            ?: conn.inputStream?.bufferedReader()?.readText() ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    conn.disconnect()
                    if (attempt < 2) {
                        delay(1000L * (1L shl attempt))
                        return@repeat
                    } else {
                        throw IllegalStateException("Muse API error $code: $lastBody")
                    }
                }

                if (code !in 200..299) {
                    val errBody = try {
                        conn.errorStream?.bufferedReader()?.readText()
                            ?: conn.inputStream?.bufferedReader()?.readText() ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    conn.disconnect()
                    throw IllegalStateException("Muse API error $code: $errBody")
                }

                val responseText = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(responseText)
                val content = json.getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val entry = content.getJSONObject(i)
                    if (entry.optString("type") == "text") {
                        sb.append(entry.optString("text", ""))
                    }
                }
                return@withContext sb.toString()
            } catch (e: IOException) {
                lastIOException = e
                if (attempt < 2) {
                    delay(1000L * (1L shl attempt))
                } else {
                    throw e
                }
            } catch (e: IllegalStateException) {
                throw e
            }
        }
        // Should not reach here; if we exhausted retries on IOException, rethrow
        lastIOException?.let { throw it }
        throw IllegalStateException("Muse API error ${lastCode ?: "unknown"}: ${lastBody ?: ""}")
    }
}
```
FILE: app/src/offline/java/com/smriti/app/ai/BackendFactory.kt
```kotlin
package com.smriti.app.ai

import android.content.Context

object BackendFactory {
    suspend fun create(context: Context): Result<LlmBackend> {
        return LlmHolder.get(context).map { LocalLlmBackend(it) }
    }
}
```
FILE: app/src/devcloud/java/com/smriti/app/ai/BackendFactory.kt
```kotlin
package com.smriti.app.ai

import android.content.Context
import com.smriti.app.BuildConfig

object BackendFactory {
    suspend fun create(context: Context): Result<LlmBackend> {
        val key = BuildConfig.MUSE_API_KEY
        return if (key.isNotBlank()) {
            Result.success(MuseBackend(key))
        } else {
            Result.failure(
                IllegalStateException(
                    "Missing museApiKey - put museApiKey=... in local.properties to use the devcloud flavor"
                )
            )
        }
    }
}
```
FILE: app/src/devcloud/AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!--
      devcloud flavor is for development iteration only and must never be released.
      The offline flavor is the shipping configuration.
    -->
    <uses-permission android:name="android.permission.INTERNET" />
</manifest>
```
FILE: app/build.gradle.kts
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val museKey = run {
    val props = java.util.Properties()
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        localProps.inputStream().use { props.load(it) }
        props.getProperty("museApiKey", "")?.trim() ?: ""
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

