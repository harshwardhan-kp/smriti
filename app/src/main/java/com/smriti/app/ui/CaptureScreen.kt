package com.smriti.app.ui

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smriti.app.capture.CaptureStage

private val ColorAmber = Color(0xFFF2B705)
private val ColorInk = Color(0xFF0B0B0B)
private val ColorCream = Color(0xFFFBF8F1)
private val ColorRedAlert = Color(0xFFE53935)

@Composable
fun CaptureScreen(
    onOpenTimeline: () -> Unit,
    onOpenAsk: () -> Unit,
    onRecordSaved: (Long) -> Unit,
    vm: CaptureViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val stage by vm.stage.collectAsState()
    var isShutterHeld by remember { mutableStateOf(false) }
    var isMicHeld by remember { mutableStateOf(false) }
    var isVoiceOnlyCapture by remember { mutableStateOf(false) }

    LaunchedEffect(stage) {
        val currentStage = stage
        if (currentStage is CaptureStage.Done) {
            // Consume before navigating: `Done` must not still be sitting in the StateFlow
            // when the user presses back and this screen recomposes, or we bounce them
            // straight forward again and the back button appears broken.
            vm.consumeTerminalStage()
            onRecordSaved(currentStage.recordId)
        }
        if (currentStage is CaptureStage.Done || currentStage is CaptureStage.Failed) {
            isVoiceOnlyCapture = false
        }
    }

    val isShutterListening = isShutterHeld || (!isVoiceOnlyCapture && stage is CaptureStage.Listening)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    vm.bindCamera(lifecycleOwner, this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenAsk) {
                Text(
                    text = "Ask",
                    color = ColorCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = ColorInk.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, ColorAmber.copy(alpha = 0.6f))
            ) {
                Text(
                    text = BuildBadge.label,
                    color = ColorAmber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            TextButton(onClick = onOpenTimeline) {
                Text(
                    text = "Timeline",
                    color = ColorCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        stage?.let { currentStage ->
            val statusText = when (currentStage) {
                is CaptureStage.Photo -> "Capturing"
                is CaptureStage.Reading -> "Reading the image"
                is CaptureStage.Listening -> "Listening"
                is CaptureStage.Thinking -> "Understanding, on this phone"
                is CaptureStage.Done -> "Saved"
                is CaptureStage.Failed -> currentStage.reason
            }
            val isFailed = currentStage is CaptureStage.Failed

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
                shape = RoundedCornerShape(12.dp),
                color = ColorInk.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, if (isFailed) ColorRedAlert else ColorAmber.copy(alpha = 0.5f))
            ) {
                Text(
                    text = statusText,
                    color = if (isFailed) ColorRedAlert else ColorCream,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isListeningHint = isMicHeld || isShutterHeld
            Text(
                text = when {
                    isMicHeld -> "LISTENING — voice only, release to stop"
                    isShutterHeld -> "LISTENING — release to stop"
                    else -> "Tap for a photo · Hold to add your voice · Hold the mic for voice only"
                },
                color = if (isListeningHint) ColorRedAlert else ColorCream,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isListeningHint) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .background(ColorInk.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .border(
                            width = if (isMicHeld) 4.dp else 2.dp,
                            color = if (isMicHeld) ColorRedAlert else ColorCream.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                        .background(
                            color = if (isMicHeld) ColorRedAlert.copy(alpha = 0.15f) else Color.Transparent,
                            shape = CircleShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        if (isMicHeld) vm.stopVoice()
                                        isMicHeld = false
                                    }
                                },
                                onLongPress = {
                                    isMicHeld = true
                                    isVoiceOnlyCapture = true
                                    vm.captureVoiceOnly()
                                }
                            )
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Record voice only",
                        tint = ColorCream
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .then(
                            if (isShutterListening) {
                                Modifier.border(4.dp, ColorRedAlert, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (isShutterListening) 6.dp else 0.dp)
                        .background(ColorAmber, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        // Releasing the button must END the recording. Without this
                                        // the recorder runs to its 15 s timeout and the user waits
                                        // for nothing after they have stopped speaking.
                                        if (isShutterHeld) vm.stopVoice()
                                        isShutterHeld = false
                                    }
                                },
                                onTap = {
                                    isVoiceOnlyCapture = false
                                    vm.capture(false)
                                },
                                onLongPress = {
                                    isShutterHeld = true
                                    isVoiceOnlyCapture = false
                                    vm.capture(true)
                                }
                            )
                        }
                )

                Spacer(modifier = Modifier.width(24.dp))

                Spacer(modifier = Modifier.width(56.dp))
            }
        }
    }
}