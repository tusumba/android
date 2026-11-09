package com.example.tusumba.ui.catalog

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Цвета и шкала Material 3 из preview v1.3 задачи #18899.
// Оформление каталогов и страниц секций.
private val LightCatalogColors =
    lightColorScheme(
        primary = Color(0xFF4455BD),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEE2FF),
        onPrimaryContainer = Color(0xFF172465),
        secondary = Color(0xFF565D70),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDAE2F9),
        onSecondaryContainer = Color(0xFF131D2D),
        tertiary = Color(0xFF006B60),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF9CF2E2),
        onTertiaryContainer = Color(0xFF00201B),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFFAF9FF),
        onBackground = Color(0xFF191B22),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF191B22),
        surfaceVariant = Color(0xFFE3E2ED),
        onSurfaceVariant = Color(0xFF45464F),
        outline = Color(0xFF767680),
        outlineVariant = Color(0xFFC6C6D0),
        inverseSurface = Color(0xFF2E3038),
        inverseOnSurface = Color(0xFFF0EFF7),
        inversePrimary = Color(0xFFB9C4FF),
        surfaceTint = Color(0xFF4455BD),
        scrim = Color(0xFF000000),
    )

private val DarkCatalogColors =
    darkColorScheme(
        primary = Color(0xFFB9C4FF),
        onPrimary = Color(0xFF1A2773),
        primaryContainer = Color(0xFF2D3C9D),
        onPrimaryContainer = Color(0xFFDEE2FF),
        secondary = Color(0xFFBEC6DD),
        onSecondary = Color(0xFF283145),
        secondaryContainer = Color(0xFF3E465C),
        onSecondaryContainer = Color(0xFFDAE2F9),
        tertiary = Color(0xFF80D6C7),
        onTertiary = Color(0xFF003730),
        tertiaryContainer = Color(0xFF005047),
        onTertiaryContainer = Color(0xFF9CF2E2),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF11131A),
        onBackground = Color(0xFFE3E2EA),
        surface = Color(0xFF11131A),
        onSurface = Color(0xFFE3E2EA),
        surfaceVariant = Color(0xFF45464F),
        onSurfaceVariant = Color(0xFFC6C6D0),
        outline = Color(0xFF90909A),
        outlineVariant = Color(0xFF45464F),
        inverseSurface = Color(0xFFE3E2EA),
        inverseOnSurface = Color(0xFF2E3038),
        inversePrimary = Color(0xFF4455BD),
        surfaceTint = Color(0xFFB9C4FF),
        scrim = Color(0xFF000000),
    )

@Composable
internal fun catalogColorScheme() = if (isSystemInDarkTheme()) DarkCatalogColors else LightCatalogColors

@Composable
internal fun CatalogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = catalogColorScheme(),
        typography = Typography(),
        content = content,
    )
}
