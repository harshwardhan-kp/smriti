package com.smriti.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smriti.app.ui.components.BracketLabel
import com.smriti.app.ui.theme.S

@Composable
fun PhotoViewer(
    photoPath: String,
    onDismiss: () -> Unit
) {
    val bitmap: ImageBitmap? = remember(photoPath) {
        try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(photoPath, boundsOptions)
            val outWidth = boundsOptions.outWidth
            val outHeight = boundsOptions.outHeight
            var inSampleSize = 1
            if (outWidth > 0 && outHeight > 0) {
                while (outWidth / (inSampleSize * 2) >= 2048 && outHeight / (inSampleSize * 2) >= 2048) {
                    inSampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            BitmapFactory.decodeFile(photoPath, decodeOptions)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(S.InkDeep)
        ) {
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    BracketLabel("could not open the photo")
                }
            } else {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                var size by remember { mutableStateOf(IntSize.Zero) }

                Image(
                    bitmap = bitmap,
                    contentDescription = "Captured Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale <= 1f) {
                                    offset = Offset.Zero
                                } else {
                                    val newOffset = offset + pan
                                    val maxX = (size.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    val maxY = (size.height * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    offset = Offset(
                                        x = newOffset.x.coerceIn(-maxX, maxX),
                                        y = newOffset.y.coerceIn(-maxY, maxY)
                                    )
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            // Double tap only. There is deliberately no onTap-to-dismiss.
                            //
                            // The two cannot coexist: Compose has to hold a single tap for the
                            // system double-tap window (~300 ms) before it can know the second
                            // tap is not coming, so any tap pair slower than that dismisses the
                            // viewer instead of zooming it. Tested on the device — double tap
                            // closed the photo every time. Closing is the ✕ and the system back
                            // gesture, both of which are unambiguous and immediate.
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                        offset = Offset.Zero
                                    }
                                }
                            )
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(S.gutter)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = S.OnDark
                    )
                }

                // Top-start, mirroring the close button, rather than along the bottom.
                // A Dialog window does not carry the host window's bottom inset:
                // navigationBarsPadding() resolves to nothing in here, so a bottom-aligned label
                // lands under the gesture pill and gets drawn over — seen on the device. The top
                // inset does resolve, which is why the ✕ opposite it sits correctly, so the
                // label goes where the padding is known to work.
                BracketLabel(
                    text = "${bitmap.width} × ${bitmap.height}",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(S.gutter),
                    labelColor = S.OnDark.copy(alpha = 0.6f)
                )
            }
        }
    }
}
