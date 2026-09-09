package com.example.zoterohelpernative.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark-only scheme (the app is a reading tool). Every role is filled from the
 * semantic tokens in [AppColors] so Material components inherit the same
 * hierarchy the custom surfaces use, instead of drifting to their own defaults.
 */
private val AppColorScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    secondary = AppColors.Accent,
    onSecondary = AppColors.OnAccent,
    tertiary = AppColors.Status.Success,
    background = AppColors.Background.Primary,
    onBackground = AppColors.Label.Primary,
    surface = AppColors.Background.Secondary,
    onSurface = AppColors.Label.Primary,
    surfaceVariant = AppColors.Background.Tertiary,
    onSurfaceVariant = AppColors.Label.Secondary,
    error = AppColors.Status.Danger,
    onError = AppColors.OnAccent,
    outline = AppColors.Separator,
    outlineVariant = AppColors.SeparatorOpaque
)

@Composable
fun ZoteroHelperNativeTheme(
    // We ignore isSystemInDarkTheme() because the app is purely dark mode for reading
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = androidx.compose.ui.graphics.Color.Transparent.toArgb()
            window.navigationBarColor = androidx.compose.ui.graphics.Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
