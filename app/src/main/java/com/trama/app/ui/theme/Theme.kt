package com.trama.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Semantic tokens consumed by screens that want accent colors matching the
 * Trama Redesign v2 mock regardless of the active light/dark scheme.
 *
 * Screens should prefer `MaterialTheme.colorScheme` for structural surfaces and
 * use `LocalTramaColors.current` only for accents/metadata chips.
 */
data class TramaColors(
    val amber: Color,
    val amberBg: Color,
    val teal: Color,
    val tealBg: Color,
    val red: Color,
    val redBg: Color,
    val warn: Color,
    val warnBg: Color,
    val watch: Color,
    val watchBg: Color,
    val mutedText: Color,
    val dimText: Color,
    val softBorder: Color,
    val hairline: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
)

private val TramaDarkColors = TramaColors(
    amber = TramaAmber,
    amberBg = TramaAmber.copy(alpha = 0.12f),
    teal = TramaTeal,
    tealBg = TramaTeal.copy(alpha = 0.12f),
    red = TramaRed,
    redBg = TramaRed.copy(alpha = 0.12f),
    warn = TramaWarn,
    warnBg = TramaWarn.copy(alpha = 0.12f),
    watch = TramaWatch,
    watchBg = TramaWatch.copy(alpha = 0.12f),
    mutedText = TramaMutedDark,
    dimText = TramaDimDark,
    softBorder = TramaBorderDark,
    hairline = TramaBorderDark2,
    surface = TramaSurfDark,
    surface2 = TramaSurf2Dark,
    surface3 = TramaSurf3Dark,
)

private val TramaLightColors = TramaColors(
    amber = TramaAmber,
    amberBg = TramaAmber.copy(alpha = 0.14f),
    teal = TramaTeal,
    tealBg = TramaTeal.copy(alpha = 0.14f),
    red = TramaRed,
    redBg = TramaRed.copy(alpha = 0.12f),
    warn = TramaWarn,
    warnBg = TramaWarn.copy(alpha = 0.16f),
    watch = TramaWatch,
    watchBg = TramaWatch.copy(alpha = 0.12f),
    mutedText = TramaMutedLight,
    dimText = TramaDimLight,
    softBorder = TramaBorderLight,
    hairline = TramaBorderLight2,
    surface = TramaSurfLight,
    surface2 = TramaSurf2Light,
    surface3 = TramaSurf3Light,
)

val LocalTramaColors = staticCompositionLocalOf { TramaDarkColors }

private val DarkColorScheme = darkColorScheme(
    primary = TramaAmber,
    onPrimary = Color.White,
    primaryContainer = TramaAmber.copy(alpha = 0.16f),
    onPrimaryContainer = TramaAmber,
    secondary = TramaTeal,
    onSecondary = Color.White,
    secondaryContainer = TramaTeal.copy(alpha = 0.16f),
    onSecondaryContainer = TramaTeal,
    tertiary = TramaWatch,
    onTertiary = Color.White,
    tertiaryContainer = TramaWatch.copy(alpha = 0.16f),
    onTertiaryContainer = TramaWatch,
    error = TramaRed,
    onError = Color.White,
    errorContainer = TramaRed.copy(alpha = 0.16f),
    onErrorContainer = TramaRed,
    background = TramaBgDark,
    onBackground = TramaTextDark,
    surface = TramaSurfDark,
    onSurface = TramaTextDark,
    surfaceVariant = TramaSurf2Dark,
    onSurfaceVariant = TramaMutedDark,
    surfaceTint = TramaAmber,
    outline = TramaMutedDark,
    outlineVariant = TramaDimDark,
)

private val LightColorScheme = lightColorScheme(
    primary = TramaAmber,
    onPrimary = Color.White,
    primaryContainer = TramaAmber.copy(alpha = 0.16f),
    onPrimaryContainer = Color(0xFF6F3A12),
    secondary = TramaTeal,
    onSecondary = Color.White,
    secondaryContainer = TramaTeal.copy(alpha = 0.16f),
    onSecondaryContainer = Color(0xFF1E5048),
    tertiary = TramaWatch,
    onTertiary = Color.White,
    tertiaryContainer = TramaWatch.copy(alpha = 0.16f),
    onTertiaryContainer = Color(0xFF1E3A7A),
    error = TramaRed,
    onError = Color.White,
    errorContainer = TramaRed.copy(alpha = 0.14f),
    onErrorContainer = Color(0xFF7A2A1F),
    background = TramaBgLight,
    onBackground = TramaTextLight,
    surface = TramaSurfLight,
    onSurface = TramaTextLight,
    surfaceVariant = TramaSurf2Light,
    onSurfaceVariant = TramaMutedLight,
    surfaceTint = TramaAmber,
    outline = TramaMutedLight,
    outlineVariant = TramaDimLight,
)

@Composable
fun TramaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled by default to preserve the Trama palette.
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val tramaColors = if (darkTheme) TramaDarkColors else TramaLightColors

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalTramaColors provides tramaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Keep Build.VERSION_CODES reference alive for potential future dynamic-color opt-in.
@Suppress("unused")
private val _keepS = Build.VERSION_CODES.S
