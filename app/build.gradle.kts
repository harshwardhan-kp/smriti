plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
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
        val manifests = fileTree(layout.buildDirectory.dir("intermediates")) {
            include("**/merged_manifest*/**/AndroidManifest.xml")
            include("**/packaged_manifests/**/AndroidManifest.xml")
        }.files
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
    tasks.named("assembleDebug") { finalizedBy("assertNoNetworkPermission") }
    tasks.findByName("assembleRelease")?.finalizedBy("assertNoNetworkPermission")
}
