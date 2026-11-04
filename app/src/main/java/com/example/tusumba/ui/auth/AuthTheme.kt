package com.example.tusumba.ui.auth

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Semantic colors from the Tusumba v1.2 authorization preview (#18899).
private val LightAuthColors = lightColorScheme(
    primary = Color(0xFF4455BD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE2FF),
    onPrimaryContainer = Color(0xFF172465),
    tertiary = Color(0xFF006B60),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF2E2),
    onTertiaryContainer = Color(0xFF00201B),
    background = Color(0xFFFAF9FF),
    onBackground = Color(0xFF191B22),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF191B22),
    surfaceVariant = Color(0xFFE3E2ED),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkAuthColors = darkColorScheme(
    primary = Color(0xFFB9C4FF),
    onPrimary = Color(0xFF1A2773),
    primaryContainer = Color(0xFF2D3C9D),
    onPrimaryContainer = Color(0xFFDEE2FF),
    tertiary = Color(0xFF80D6C7),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005047),
    onTertiaryContainer = Color(0xFF9CF2E2),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE3E2EA),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFE3E2EA),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
internal fun authColorScheme() = if (isSystemInDarkTheme()) DarkAuthColors else LightAuthColors

@Composable
internal fun AuthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = authColorScheme(),
        content = content
    )
}
