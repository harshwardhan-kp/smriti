package com.smriti.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smriti.app.ui.components.BracketLabel
import com.smriti.app.ui.components.DisplayHeading
import com.smriti.app.ui.components.SmritiButton
import com.smriti.app.ui.theme.S
import com.smriti.app.ui.theme.SmritiType
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.smriti.app.ui.AskScreen
import com.smriti.app.ui.CaptureScreen
import com.smriti.app.ui.DetailScreen
import com.smriti.app.ui.TimelineScreen
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smriti.app.ai.Enricher
import android.content.Intent
import com.smriti.app.ai.NpuProbe
import com.smriti.app.capture.AsrSelfTest
import com.smriti.app.capture.BubbleService
import com.smriti.app.capture.ScreenCaptureConsentActivity
import com.smriti.app.capture.ScreenCaptureService
import com.smriti.app.ui.theme.SmritiTheme
import android.net.Uri
import android.os.Build
import android.provider.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Debug affordance. MIUI denies INJECT_EVENTS to the shell user, so the capture button
        // cannot be tapped over adb; this is how the model gets exercised headlessly.
        // Demo corpus, adb-triggerable:
        //   adb shell am start -n <pkg>/com.smriti.app.MainActivity --ez smriti_seed true
        //   ... --ez smriti_seed_clear true   to remove seeded records
        val wantSeed = intent?.getBooleanExtra(SeedTrigger.EXTRA, false) == true
        val wantClear = intent?.getBooleanExtra(SeedTrigger.EXTRA_CLEAR, false) == true
        if (wantSeed || wantClear) {
            SeedTrigger.handle(this, lifecycleScope, seed = wantSeed, clear = wantClear)
        }

        if (intent?.getBooleanExtra(SelfTest.EXTRA, false) == true) {
            SelfTest.run(
                context = this,
                scope = lifecycleScope,
                backend = intent.getStringExtra("backend"),
                resetPolicy = intent.getBooleanExtra("reset_backend", false)
            )
        }

        if (intent?.getBooleanExtra(NpuProbe.EXTRA, false) == true) {
            NpuProbe.run(this, lifecycleScope)
        }

        if (intent?.getBooleanExtra(AsrSelfTest.EXTRA, false) == true) {
            AsrSelfTest.run(this, lifecycleScope)
        }

        if (intent?.getBooleanExtra("smriti_screencap", false) == true) {
            startActivity(Intent(this, ScreenCaptureConsentActivity::class.java))
        }

        if (intent?.getBooleanExtra("smriti_screengrab", false) == true) {
            ScreenCaptureService.capture(this)
        }

        if (intent?.getBooleanExtra("smriti_bubble", false) == true) {
            handleStartBubble()
        }

        if (intent?.getBooleanExtra("smriti_bubble_off", false) == true) {
            BubbleService.stop(this)
        }

        Enricher.request(this)

        setContent {
            SmritiTheme {
                SmritiApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.getBooleanExtra("smriti_screencap", false)) {
            startActivity(Intent(this, ScreenCaptureConsentActivity::class.java))
        }

        if (intent.getBooleanExtra("smriti_screengrab", false)) {
            ScreenCaptureService.capture(this)
        }

        if (intent.getBooleanExtra("smriti_bubble", false)) {
            handleStartBubble()
        }

        if (intent.getBooleanExtra("smriti_bubble_off", false)) {
            BubbleService.stop(this)
        }
    }

    private fun handleStartBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
            return
        }

        if (!ScreenCaptureService.isArmed()) {
            startActivity(Intent(this, ScreenCaptureConsentActivity::class.java))
        }

        BubbleService.start(this)
    }
}

@Composable
fun SmritiApp() {
    PermissionGate {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            // Capture is the app. The timeline is where you go afterwards.
            startDestination = "capture",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("capture") {
                CaptureScreen(
                    onOpenTimeline = { navController.navigate("timeline") },
                    onOpenAsk = { navController.navigate("ask") },
                    onRecordSaved = { id -> navController.navigate("detail/$id") }
                )
            }
            composable("timeline") {
                TimelineScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRecord = { id -> navController.navigate("detail/$id") }
                )
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
                DetailScreen(
                    recordId = recordId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("ask") {
                AskScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRecord = { id -> navController.navigate("detail/$id") }
                )
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
                .background(S.Paper)
                .padding(horizontal = S.gutter)
                .padding(top = S.section),
            contentAlignment = Alignment.TopStart
        ) {
            Column {
                DisplayHeading(
                    text = "smriti needs two things",
                    italicWord = "two",
                    style = SmritiType.Display,
                    color = S.Ink
                )
                Spacer(modifier = Modifier.height(S.md))
                Text(
                    text = "the camera, to read what is in front of you. the microphone, to hear what you say. nothing leaves this phone.",
                    style = SmritiType.Body,
                    color = S.Muted
                )
                Spacer(modifier = Modifier.height(S.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(S.md)) {
                    BracketLabel("camera")
                    BracketLabel("microphone")
                }
                Spacer(modifier = Modifier.height(S.lg))
                SmritiButton(
                    label = "grant",
                    onClick = { launcher.launch(requiredPermissions) },
                    primary = true
                )
            }
        }
    }
}
