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