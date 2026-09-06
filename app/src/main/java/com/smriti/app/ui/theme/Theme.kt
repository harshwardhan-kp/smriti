package com.smriti.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Smriti runs one theme, in one direction: paper.
 *
 * There is no dark variant and the system setting is deliberately ignored. antimattr.one commits
 * to a single light look and gets its contrast from type and from one red, not from a dark
 * ground; a theme that flips underneath that would undo the whole thing. The camera screen is
 * the one place dark chrome survives, and it is dark because it sits over live video, not
 * because of a setting.
 *
 * Real colours live in [S] and real text styles in [SmritiType]. The Material scheme below
 * exists so that any stock Material 3 component still lands in the right palette, but screens
 * should reach for [S] directly.
 */

private val SmritiColorScheme = lightColorScheme(
    primary = S.Red,
    onPrimary = S.OnDark,
    primaryContainer = S.RedTint,
    onPrimaryContainer = S.Ink,
    secondary = S.Ink,
    onSecondary = S.OnDark,
    tertiary = S.Amber,
    onTertiary = S.Ink,
    background = S.Paper,
    onBackground = S.Ink,
    surface = S.Paper,
    onSurface = S.Ink,
    surfaceVariant = S.PaperSunk,
    onSurfaceVariant = S.Muted,
    outline = S.Hairline,
    outlineVariant = S.Hairline,
    error = S.Red,
    onError = S.OnDark
)

/**
 * Material's slots, pointed at Smriti's scale. This is a safety net, not the API: it means a
 * stray `MaterialTheme.typography.bodyMedium` renders in Schibsted Grotesk rather than dropping
 * to Roboto and quietly breaking the page.
 */
private val SmritiTypography = Typography(
    displayLarge = SmritiType.Display,
    displayMedium = SmritiType.Display,
    displaySmall = SmritiType.DisplaySmall,
    headlineLarge = SmritiType.DisplaySmall,
    headlineMedium = SmritiType.DisplaySmall,
    headlineSmall = SmritiType.DisplaySmall,
    titleLarge = SmritiType.Title,
    titleMedium = SmritiType.Title,
    titleSmall = SmritiType.Label,
    bodyLarge = SmritiType.Body,
    bodyMedium = SmritiType.Body,
    bodySmall = SmritiType.BodySmall,
    labelLarge = SmritiType.Label,
    labelMedium = SmritiType.Meta,
    labelSmall = SmritiType.Meta
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun SmritiTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // The app is paper now, so the system's own icons have to be dark to be seen.
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = SmritiColorScheme,
        typography = SmritiTypography,
        content = content
    )
}
