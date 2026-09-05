package com.smriti.app.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smriti.app.ui.theme.S

@Composable
private fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val resolver = context.contentResolver
            val animScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transScale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            animScale == 0f || transScale == 0f
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Three small rounded squares drawn on a Compose Canvas, in S.Amber.
 * Animate with rememberInfiniteTransition: each square's alpha and size eases up and back down,
 * staggered so the pulse travels left to right, roughly 1200 ms for the full cycle with about
 * 160 ms offset per dot. Uses FastOutSlowInEasing.
 * Total footprint about 26.dp wide, 6.dp tall.
 */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier) {
    val isReducedMotion = isReducedMotionEnabled()
    val transition = rememberInfiniteTransition(label = "ThinkingDotsTransition")

    val dot0Progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0 using FastOutSlowInEasing
                1f at 220 using FastOutSlowInEasing
                0f at 440
                0f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot0Progress"
    )

    val dot1Progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0
                0f at 160 using FastOutSlowInEasing
                1f at 380 using FastOutSlowInEasing
                0f at 600
                0f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1Progress"
    )

    val dot2Progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at 0
                0f at 320 using FastOutSlowInEasing
                1f at 540 using FastOutSlowInEasing
                0f at 760
                0f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2Progress"
    )

    Canvas(
        modifier = modifier.size(width = 26.dp, height = 6.dp)
    ) {
        val centerY = size.height / 2f
        val dot0X = 4.5.dp.toPx()
        val dot1X = 13.dp.toPx()
        val dot2X = 21.5.dp.toPx()

        val minSize = 3.5.dp.toPx()
        val maxSize = 5.dp.toPx()
        val minAlpha = 0.35f
        val maxAlpha = 1.0f

        val (f0, f1, f2) = if (isReducedMotion) {
            Triple(0f, 0f, 0f)
        } else {
            Triple(dot0Progress, dot1Progress, dot2Progress)
        }

        val s0 = minSize + (maxSize - minSize) * f0
        val a0 = minAlpha + (maxAlpha - minAlpha) * f0
        drawRoundRect(
            color = S.Amber.copy(alpha = a0),
            topLeft = Offset(dot0X - s0 / 2f, centerY - s0 / 2f),
            size = Size(s0, s0),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        val s1 = minSize + (maxSize - minSize) * f1
        val a1 = minAlpha + (maxAlpha - minAlpha) * f1
        drawRoundRect(
            color = S.Amber.copy(alpha = a1),
            topLeft = Offset(dot1X - s1 / 2f, centerY - s1 / 2f),
            size = Size(s1, s1),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        val s2 = minSize + (maxSize - minSize) * f2
        val a2 = minAlpha + (maxAlpha - minAlpha) * f2
        drawRoundRect(
            color = S.Amber.copy(alpha = a2),
            topLeft = Offset(dot2X - s2 / 2f, centerY - s2 / 2f),
            size = Size(s2, s2),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}

/**
 * A skeleton placeholder for the summary text that has not arrived yet: a rounded rect,
 * about 11.dp tall, filled with a horizontal Brush.linearGradient that sweeps across it
 * continuously (translates the gradient's start/end X with an infinite transition, roughly
 * 1400 ms per sweep). Sweeps between S.PaperSunk and S.Hairline.copy(alpha = 0.9f).
 */
@Composable
fun ShimmerLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f
) {
    val isReducedMotion = isReducedMotionEnabled()
    val transition = rememberInfiniteTransition(label = "ShimmerLineTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val baseColor = S.PaperSunk
    val highlightColor = S.Hairline.copy(alpha = 0.9f)

    Canvas(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(11.dp)
            .clip(RoundedCornerShape(2.dp))
    ) {
        val width = size.width
        val cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        if (isReducedMotion || width <= 0f) {
            drawRoundRect(
                color = baseColor,
                cornerRadius = cornerRadius
            )
        } else {
            val bandWidth = width * 0.6f
            val startX = -bandWidth + (width + bandWidth) * progress
            val endX = startX + bandWidth

            val brush = Brush.linearGradient(
                colors = listOf(baseColor, highlightColor, baseColor),
                start = Offset(startX, 0f),
                end = Offset(endX, 0f)
            )

            drawRoundRect(
                brush = brush,
                cornerRadius = cornerRadius
            )
        }
    }
}
