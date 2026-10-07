package com.example.zoterohelpernative.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material roles filled from the active palette, so Material components
 * (dialogs, menus, chips, switches) inherit the same hierarchy as the custom
 * surfaces instead of drifting to their own defaults.
 */
private fun AppPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.18f),
        onPrimaryContainer = labelPrimary,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = fillPrimary,
        onSecondaryContainer = labelPrimary,
        tertiary = success,
        onTertiary = onAccent,
        tertiaryContainer = fillPrimary,
        onTertiaryContainer = labelPrimary,
        background = backgroundPrimary,
        onBackground = labelPrimary,
        surface = backgroundSecondary,
        onSurface = labelPrimary,
        surfaceVariant = backgroundTertiary,
        onSurfaceVariant = labelSecondary,
        surfaceTint = accent,
        inverseSurface = labelPrimary,
        inverseOnSurface = backgroundPrimary,
        error = danger,
        onError = onAccent,
        errorContainer = danger.copy(alpha = 0.18f),
        onErrorContainer = labelPrimary,
        outline = separator,
        outlineVariant = separatorOpaque,
        surfaceBright = backgroundTertiary,
        surfaceDim = backgroundPrimary,
        surfaceContainerLowest = backgroundPrimary,
        surfaceContainerLow = backgroundSecondary,
        surfaceContainer = backgroundSecondary,
        surfaceContainerHigh = backgroundTertiary,
        surfaceContainerHighest = backgroundTertiary
    )
}

@Composable
fun ZoteroHelperNativeTheme(
    theme: AppTheme = AppTheme.NOTTE,
    content: @Composable () -> Unit
) {
    val palette = theme.palette(systemDark = isSystemInDarkTheme())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = androidx.compose.ui.graphics.Color.Transparent.toArgb()
            window.navigationBarColor = androidx.compose.ui.graphics.Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            // Dark system icons on paper, light ones on ink
            controller.isAppearanceLightStatusBars = !palette.isDark
            controller.isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = appTypography(palette.editorialType),
            content = content
        )
    }
}
