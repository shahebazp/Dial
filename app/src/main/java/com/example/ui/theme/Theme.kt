package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class DialThemePreference(val displayLabel: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System"),
    OLED_BLACK("True OLED")
}

private val NothingLightColorScheme = lightColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    primaryContainer = NothingSurfaceLight,
    onPrimaryContainer = NothingBlack,
    secondary = NothingBlack,
    onSecondary = NothingWhite,
    secondaryContainer = NothingSurfaceLight,
    onSecondaryContainer = NothingBlack,
    tertiary = NothingGrey,
    onTertiary = NothingWhite,
    background = NothingOffWhite,
    onBackground = NothingBlack,
    surface = NothingWhite,
    onSurface = NothingBlack,
    surfaceVariant = NothingSurfaceLight,
    onSurfaceVariant = NothingSubtleGrey,
    outline = NothingBorderLight,
    outlineVariant = NothingLightGrey,
    error = NothingRed,
    onError = NothingWhite
)

private val NothingDarkColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    primaryContainer = NothingSurfaceDark,
    onPrimaryContainer = NothingWhite,
    secondary = NothingWhite,
    onSecondary = NothingBlack,
    secondaryContainer = NothingSurfaceDark,
    onSecondaryContainer = NothingWhite,
    tertiary = NothingGrey,
    onTertiary = NothingBlack,
    background = NothingCharcoal,
    onBackground = NothingWhite,
    surface = NothingSurfaceDark,
    onSurface = NothingWhite,
    surfaceVariant = Color(0xFF25252D),
    onSurfaceVariant = NothingLightGrey,
    outline = NothingBorderDark,
    outlineVariant = NothingSubtleGrey,
    error = NothingRed,
    onError = NothingWhite
)

private val NothingOledBlackColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    primaryContainer = Color(0xFF121215),
    onPrimaryContainer = NothingWhite,
    secondary = NothingWhite,
    onSecondary = NothingBlack,
    secondaryContainer = Color(0xFF141418),
    onSecondaryContainer = NothingWhite,
    tertiary = NothingGrey,
    onTertiary = NothingBlack,
    background = Color(0xFF000000),
    onBackground = NothingWhite,
    surface = Color(0xFF0C0C0E),
    onSurface = NothingWhite,
    surfaceVariant = Color(0xFF16161B),
    onSurfaceVariant = NothingLightGrey,
    outline = Color(0xFF24242B),
    outlineVariant = NothingSubtleGrey,
    error = NothingRed,
    onError = NothingWhite
)

@Composable
fun DialTheme(
    themePreference: DialThemePreference = DialThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themePreference) {
        DialThemePreference.LIGHT -> false
        DialThemePreference.DARK, DialThemePreference.OLED_BLACK -> true
        DialThemePreference.SYSTEM -> systemDark
    }
    val colorScheme = when (themePreference) {
        DialThemePreference.LIGHT -> NothingLightColorScheme
        DialThemePreference.DARK -> NothingDarkColorScheme
        DialThemePreference.OLED_BLACK -> NothingOledBlackColorScheme
        DialThemePreference.SYSTEM -> if (systemDark) NothingDarkColorScheme else NothingLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    DialTheme(
        themePreference = if (darkTheme) DialThemePreference.DARK else DialThemePreference.LIGHT,
        content = content
    )
}
