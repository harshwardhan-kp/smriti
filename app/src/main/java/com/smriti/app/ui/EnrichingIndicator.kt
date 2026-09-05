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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smriti.app.ui.theme.ColorAmber
import com.smriti.app.ui.theme.Cream

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
 * Three small circles drawn on a Compose Canvas, in the existing amber theme colour.
 * Animate with rememberInfiniteTransition: each dot's alpha and radius eases up and back down,
 * staggered so the pulse travels left to right, roughly 1200 ms for the full cycle with about
 * 160 ms offset per dot. Uses FastOutSlowInEasing.
 * Total footprint about 26.dp wide, 8.dp tall.
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
        modifier = modifier.size(width = 26.dp, height = 8.dp)
    ) {
        val centerY = size.height / 2f
        val dot0X = 4.5.dp.toPx()
        val dot1X = 13.dp.toPx()
        val dot2X = 21.5.dp.toPx()

        val minRadius = 1.75.dp.toPx()
        val maxRadius = 3.25.dp.toPx()
        val minAlpha = 0.35f
        val maxAlpha = 1.0f

        val (f0, f1, f2) = if (isReducedMotion) {
            Triple(0f, 0f, 0f)
        } else {
            Triple(dot0Progress, dot1Progress, dot2Progress)
        }

        val r0 = minRadius + (maxRadius - minRadius) * f0
        val a0 = minAlpha + (maxAlpha - minAlpha) * f0
        drawCircle(
            color = ColorAmber.copy(alpha = a0),
            radius = r0,
            center = Offset(dot0X, centerY)
        )

        val r1 = minRadius + (maxRadius - minRadius) * f1
        val a1 = minAlpha + (maxAlpha - minAlpha) * f1
        drawCircle(
            color = ColorAmber.copy(alpha = a1),
            radius = r1,
            center = Offset(dot1X, centerY)
        )

        val r2 = minRadius + (maxRadius - minRadius) * f2
        val a2 = minAlpha + (maxAlpha - minAlpha) * f2
        drawCircle(
            color = ColorAmber.copy(alpha = a2),
            radius = r2,
            center = Offset(dot2X, centerY)
        )
    }
}

/**
 * A skeleton placeholder for the summary text that has not arrived yet: a rounded rect,
 * about 11.dp tall, filled with a horizontal Brush.linearGradient that sweeps across it
 * continuously (translates the gradient's start/end X with an infinite transition, roughly
 * 1400 ms per sweep). Base colour is the existing surface/muted colour and the moving band is
 * a lighter tint of it -- subtle, not a flashy highlight.
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

    val surfaceColor = MaterialTheme.colorScheme.surface
    val baseColor = lerp(surfaceColor, Cream, 0.06f)
    val highlightColor = lerp(surfaceColor, Cream, 0.16f)

    Canvas(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(11.dp)
    ) {
        val width = size.width
        val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

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
