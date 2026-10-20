package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class VidhyaExtendedColors(
    val primaryBackground: Color,
    val secondaryBackground: Color,
    val primarySurface: Color,
    val elevatedSurface: Color,
    val interactiveSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val divider: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val goldAccent: Color,
    val goldHighlight: Color,
    val podiumGradient: Brush,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color
)

val LightVidhyaExtendedColors = VidhyaExtendedColors(
    primaryBackground = LightBackground,
    secondaryBackground = LightSecondaryBackground,
    primarySurface = LightSurface,
    elevatedSurface = LightSurface,
    interactiveSurface = LightSubtleSurface,
    primaryText = LightTextPrimary,
    secondaryText = LightTextSecondary,
    tertiaryText = LightTextTertiary,
    divider = LightDivider,
    primaryAccent = LightPrimaryAccent,
    secondaryAccent = LightPrimaryAccent,
    goldAccent = LightAccentHighlight,
    goldHighlight = AcademicWarning,
    podiumGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFCAF00), Color(0xFFD99A2B))
    ),
    success = AcademicSuccess,
    successContainer = AcademicSuccessBgLight,
    warning = AcademicWarning,
    warningContainer = AcademicWarningBgLight,
    error = AcademicError,
    errorContainer = AcademicErrorBgLight
)

val DarkVidhyaExtendedColors = VidhyaExtendedColors(
    primaryBackground = DarkBackground,
    secondaryBackground = DarkSurface,
    primarySurface = DarkSurface,
    elevatedSurface = DarkElevatedSurface,
    interactiveSurface = DarkInteractiveSurface,
    primaryText = DarkTextPrimary,
    secondaryText = DarkTextSecondary,
    tertiaryText = DarkTextTertiary,
    divider = DarkDivider,
    primaryAccent = DarkPrimaryAccent,
    secondaryAccent = DarkSecondaryAccent,
    goldAccent = DarkGoldAccent,
    goldHighlight = DarkGoldHighlight,
    podiumGradient = Brush.horizontalGradient(
        colors = listOf(DarkPodiumGradientStart, DarkPodiumGradientEnd)
    ),
    success = AcademicSuccess,
    successContainer = AcademicSuccessBgDark,
    warning = AcademicWarning,
    warningContainer = AcademicWarningBgDark,
    error = AcademicError,
    errorContainer = AcademicErrorBgDark
)

private val VidhyaDarkColorScheme = darkColorScheme(
    primary = DarkPrimaryAccent,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkGoldAccent,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkSecondaryAccent,
    onTertiary = DarkTextPrimary,
    tertiaryContainer = DarkInteractiveSurface,
    onTertiaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkInteractiveSurface,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkElevatedSurface,
    surfaceContainerHighest = DarkInteractiveSurface,
    outline = DarkDivider,
    outlineVariant = DarkElevatedSurface,
    error = AcademicError,
    onError = Color.White,
    errorContainer = AcademicErrorBgDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val VidhyaLightColorScheme = lightColorScheme(
    primary = LightPrimaryAccent,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightAccentHighlight,
    onSecondary = LightTextPrimary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightPrimaryAccent,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightSubtleSurface,
    onTertiaryContainer = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSubtleSurface,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSecondaryBackground,
    surfaceContainer = LightSecondaryBackground,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSubtleSurface,
    outline = LightDivider,
    outlineVariant = LightDivider,
    error = AcademicError,
    onError = Color.White,
    errorContainer = AcademicErrorBgLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

val LocalDarkTheme = staticCompositionLocalOf { false }
val LocalVidhyaColors = staticCompositionLocalOf { LightVidhyaExtendedColors }

@Composable
fun VidhyaTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) VidhyaDarkColorScheme else VidhyaLightColorScheme
    val extendedColors = if (isDark) DarkVidhyaExtendedColors else LightVidhyaExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides isDark,
        LocalVidhyaColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
