package com.smriti.app.ui

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smriti.app.capture.BubbleLauncher
import com.smriti.app.capture.CaptureStage
import com.smriti.app.ui.components.BracketLabel
import com.smriti.app.ui.components.BracketLabelLive
import com.smriti.app.ui.theme.S
import com.smriti.app.ui.theme.SmritiType

@Composable
fun CaptureScreen(
    onOpenTimeline: () -> Unit,
    onOpenAsk: () -> Unit,
    onRecordSaved: (Long) -> Unit,
    vm: CaptureViewModel = viewModel()
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(Unit) {
            val window = (view.context as? android.app.Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {
                controller?.isAppearanceLightStatusBars = true
                controller?.isAppearanceLightNavigationBars = true
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val stage by vm.stage.collectAsState()
    var isShutterHeld by remember { mutableStateOf(false) }
    var isMicHeld by remember { mutableStateOf(false) }
    var isVoiceOnlyCapture by remember { mutableStateOf(false) }
    var bubbleOn by remember { mutableStateOf(BubbleLauncher.isRunning()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bubbleOn = BubbleLauncher.isRunning()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            .background(S.InkDeep)
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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to S.InkDeep.copy(alpha = 0.72f),
                        0.72f to S.InkDeep.copy(alpha = 0.55f),
                        1f to Color.Transparent
                    )
                )
                .statusBarsPadding()
                .padding(bottom = S.gutter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = S.gutter, vertical = S.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenAsk) {
                    Text(
                        text = "ask",
                        style = SmritiType.Button,
                        color = S.OnDark
                    )
                }

                BracketLabel(
                    text = BuildBadge.label,
                    labelColor = S.OnDark.copy(alpha = 0.65f),
                    bracketColor = S.Red
                )

                TextButton(onClick = onOpenTimeline) {
                    Text(
                        text = "timeline",
                        style = SmritiType.Button,
                        color = S.OnDark
                    )
                }
            }

            BracketLabel(
                text = if (bubbleOn) "bubble on" else "bubble off",
                labelColor = if (bubbleOn) S.Red else S.OnDark.copy(alpha = 0.55f),
                bracketColor = if (bubbleOn) S.Red else S.OnDark.copy(alpha = 0.55f),
                modifier = Modifier
                    .clickable {
                        bubbleOn = if (bubbleOn) {
                            BubbleLauncher.stop(context)
                            false
                        } else {
                            BubbleLauncher.start(context)
                        }
                    }
                    .padding(vertical = S.xs)
            )
        }

        stage?.let { currentStage ->
            val statusText = when (currentStage) {
                is CaptureStage.Photo -> "capturing"
                is CaptureStage.Reading -> "reading the image"
                is CaptureStage.Listening -> "listening"
                is CaptureStage.Thinking -> "understanding, on this phone"
                is CaptureStage.Done -> "saved"
                is CaptureStage.Failed -> currentStage.reason
            }
            val isFailed = currentStage is CaptureStage.Failed

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 96.dp),
                shape = S.r6,
                color = S.InkDeep.copy(alpha = 0.75f),
                border = BorderStroke(S.hairlineWidth, if (isFailed) S.Red else S.HairlineOnDark)
            ) {
                BracketLabelLive(
                    text = statusText,
                    color = if (isFailed) S.Red else S.OnDark,
                    modifier = Modifier.padding(horizontal = S.gutter, vertical = S.sm)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = S.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isListeningHint = isMicHeld || isShutterHeld
            Text(
                text = when {
                    isMicHeld -> "listening — voice only, release to stop"
                    isShutterHeld -> "listening — release to stop"
                    // 40 characters. Measured on a 384dp screen: the pill has 332dp of usable
                    // width and Geist Mono at 12sp advances 7.2dp per character, so 46 is the
                    // ceiling. The previous 49-character version wrapped and left "only"
                    // orphaned on a line of its own.
                    else -> "tap photo · hold photo+voice · mic voice"
                },
                color = if (isListeningHint) S.Red else S.OnDark.copy(alpha = 0.65f),
                style = if (isListeningHint) SmritiType.MetaMedium else SmritiType.Meta,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = S.gutter)
                    .background(S.InkDeep.copy(alpha = 0.6f), S.r6)
                    .padding(horizontal = S.md, vertical = S.xs)
            )

            Spacer(modifier = Modifier.height(S.gutter))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(S.lg, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .border(
                            width = if (isMicHeld) 4.dp else 2.dp,
                            color = if (isMicHeld) S.Red else S.OnDark.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                        .background(
                            color = if (isMicHeld) S.RedTint else Color.Transparent,
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
                        tint = if (isMicHeld) S.Red else S.OnDark
                    )
                }

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .then(
                            if (isShutterListening) {
                                Modifier.border(4.dp, S.Red, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (isShutterListening) 6.dp else 0.dp)
                        .background(S.White, CircleShape)
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

                // Balances the mic on the left so the shutter sits on the screen's centre line.
                // Arrangement.spacedBy already contributes the S.lg gap on both sides, so this
                // spacer matches the mic's width alone — adding the gap again would push the
                // shutter S.lg to the left of centre.
                Spacer(modifier = Modifier.width(56.dp))
            }
        }
    }
}