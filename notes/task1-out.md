FILE: settings.gradle.kts
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Smriti"
include(":app")
```
FILE: build.gradle.kts
```kotlin
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
```
FILE: gradle.properties
```properties
org.gradle.jvmargs=-Xmx1536m
org.gradle.daemon=true
android.useAndroidX=true
org.gradle.configuration-cache=false
```
FILE: app/build.gradle.kts
```kotlin
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
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

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
}
```
FILE: app/proguard-rules.pro
```pro
# Add project specific ProGuard rules here.
```
FILE: app/src/main/AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <uses-feature
        android:name="android.hardware.camera.any"
        android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@style/Theme.Smriti">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Smriti">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```
FILE: app/src/main/res/values/themes.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Smriti" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```
FILE: app/src/main/res/values/strings.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Smriti</string>
</resources>
```
FILE: app/src/main/java/com/smriti/app/ui/theme/Theme.kt
```kotlin
package com.smriti.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0B0B)
val Cream = Color(0xFFFBF8F1)
val Amber = Color(0xFFF2B705)
val AmberDark = Color(0xFFC49300)
val DarkSurface = Color(0xFF1E1E1E)
val LightSurface = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = AmberDark,
    onPrimaryContainer = Cream,
    secondary = Amber,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = DarkSurface,
    onSurface = Cream
)

private val LightColorScheme = lightColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = Amber,
    onPrimaryContainer = Ink,
    secondary = AmberDark,
    onSecondary = Cream,
    background = Cream,
    onBackground = Ink,
    surface = LightSurface,
    onSurface = Ink
)

@Composable
fun SmritiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```
FILE: app/src/main/java/com/smriti/app/data/RecordEntity.kt
```kotlin
package com.smriti.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val createdAt: Long,
    val photoPath: String,
    val ocrText: String,
    val transcript: String,
    val title: String,
    val summary: String,
    val peopleJson: String,
    val amountsJson: String,
    val tagsJson: String,
    val embedding: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecordEntity

        if (id != other.id) return false
        if (createdAt != other.createdAt) return false
        if (photoPath != other.photoPath) return false
        if (ocrText != other.ocrText) return false
        if (transcript != other.transcript) return false
        if (title != other.title) return false
        if (summary != other.summary) return false
        if (peopleJson != other.peopleJson) return false
        if (amountsJson != other.amountsJson) return false
        if (tagsJson != other.tagsJson) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + photoPath.hashCode()
        result = 31 * result + ocrText.hashCode()
        result = 31 * result + transcript.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + peopleJson.hashCode()
        result = 31 * result + amountsJson.hashCode()
        result = 31 * result + tagsJson.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
```
FILE: app/src/main/java/com/smriti/app/data/TaskEntity.kt
```kotlin
package com.smriti.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["recordId"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val recordId: Long,
    val text: String,
    val dueDateMillis: Long? = null,
    val done: Boolean = false
)
```
FILE: app/src/main/java/com/smriti/app/data/Converters.kt
```kotlin
package com.smriti.app.data

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): ByteArray? {
        if (array == null) return null
        val buffer = ByteBuffer.allocate(array.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (value in array) {
            buffer.putFloat(value)
        }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val array = FloatArray(bytes.size / 4)
        for (i in array.indices) {
            array[i] = buffer.getFloat()
        }
        return array
    }
}
```
FILE: app/src/main/java/com/smriti/app/data/RecordDao.kt
```kotlin
package com.smriti.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun observeRecords(): Flow<List<RecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecord(id: Long): RecordEntity?

    @Query("SELECT * FROM tasks ORDER BY dueDateMillis ASC NULLS LAST")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE recordId = :recordId")
    fun observeTasksForRecord(recordId: Long): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setTaskDone(id: Long, done: Boolean)

    @Query("SELECT * FROM records WHERE embedding IS NOT NULL")
    suspend fun allRecordsWithEmbedding(): List<RecordEntity>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun countRecords(): Int
}
```
FILE: app/src/main/java/com/smriti/app/data/SmritiDb.kt
```kotlin
package com.smriti.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RecordEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SmritiDb : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    companion object {
        @Volatile
        private var INSTANCE: SmritiDb? = null

        fun get(context: Context): SmritiDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmritiDb::class.java,
                    "smriti.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```
FILE: app/src/main/java/com/smriti/app/MainActivity.kt
```kotlin
package com.smriti.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smriti.app.ui.theme.SmritiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmritiTheme {
                SmritiApp()
            }
        }
    }
}

@Composable
fun SmritiApp() {
    PermissionGate {
        val navController = rememberNavController()
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "timeline",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("capture") {
                    PlaceholderScreen(name = "Capture")
                }
                composable("timeline") {
                    PlaceholderScreen(name = "Timeline")
                }
                composable(
                    route = "detail/{id}",
                    arguments = listOf(
                        navArgument("id") {
                            type = NavType.LongType
                        }
                    )
                ) { backStackEntry ->
                    val recordId = backStackEntry.arguments?.getLong("id") ?: 0L
                    PlaceholderScreen(name = "Detail: $recordId")
                }
                composable("ask") {
                    PlaceholderScreen(name = "Ask")
                }
            }
        }
    }
}

@Composable
fun PermissionGate(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = requiredPermissions.all { perm ->
            results[perm] == true || ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            launcher.launch(requiredPermissions)
        }
    }

    if (permissionsGranted) {
        content()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Smriti needs access to your Camera and Microphone to capture visual and audio work logs offline.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { launcher.launch(requiredPermissions) }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
```
