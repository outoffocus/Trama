package com.trama.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8DEC0),
    secondary = Color(0xFF9FB8C9),
    tertiary = Color(0xFFB7D7B2),
    background = Color(0xFF0D1422),
    surface = Color(0xFF121A2A),
    surfaceVariant = Color(0xFF1B2740),
    primaryContainer = Color(0xFF24314F),
    secondaryContainer = Color(0xFF1A3141),
    tertiaryContainer = Color(0xFF22352A),
    onPrimary = Color(0xFF1A2233),
    onBackground = Color(0xFFE7EDF7),
    onSurface = Color(0xFFE7EDF7),
    onSurfaceVariant = Color(0xFFB4C0D4)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF273D75),
    secondary = Color(0xFF546C81),
    tertiary = Color(0xFF407B53),
    background = Color(0xFFF6F1E8),
    surface = Color(0xFFFFFCF6),
    surfaceVariant = Color(0xFFE6E0D5),
    primaryContainer = Color(0xFFD9E1F6),
    secondaryContainer = Color(0xFFDDE7EE),
    tertiaryContainer = Color(0xFFDDEEDC),
    onPrimary = Color.White,
    onBackground = Color(0xFF162033),
    onSurface = Color(0xFF162033),
    onSurfaceVariant = Color(0xFF5D6877)
)

@Composable
fun TramaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) DarkColorScheme else LightColorScheme
    } else if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
