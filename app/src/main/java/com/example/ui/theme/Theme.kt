package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(

    primary = RufusPrimary,
    onPrimary = RufusOnPrimary,
    primaryContainer = RufusSurfaceVariantDark,
    onPrimaryContainer = RufusPrimary,
    secondary = RufusSecondary,
    onSecondary = RufusOnSecondary,
    secondaryContainer = RufusSurfaceVariantDark,
    onSecondaryContainer = RufusOnSecondaryDark,
    tertiary = RufusTertiary,
    onTertiary = RufusOnTertiary,
    background = RufusBackgroundDark,
    onBackground = RufusOnBackgroundDark,
    surface = RufusSurfaceDark,
    onSurface = RufusOnSurfaceDark,
    surfaceVariant = RufusSurfaceVariantDark,
    onSurfaceVariant = RufusOnSurfaceVariantDark,
    outline = RufusOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = RufusPrimary,
    onPrimary = RufusOnPrimary,
    primaryContainer = RufusPrimaryContainer,
    onPrimaryContainer = RufusOnPrimaryContainer,
    secondary = RufusSecondary,
    onSecondary = RufusOnSecondary,
    secondaryContainer = RufusSecondaryContainer,
    onSecondaryContainer = RufusOnSecondaryContainer,
    tertiary = RufusTertiary,
    onTertiary = RufusOnTertiary,
    background = RufusBackgroundLight,
    onBackground = RufusOnBackgroundLight,
    surface = RufusSurfaceLight,
    onSurface = RufusOnSurfaceLight,
    surfaceVariant = RufusSurfaceVariantLight,
    onSurfaceVariant = RufusOnSurfaceVariantLight,
    outline = RufusOutlineLight
)

@Composable
private fun animateColor(target: Color): Color {
    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "themeColorTransition"
    ).value
}

@Composable
private fun ColorScheme.animateColors(): ColorScheme {
    return this.copy(
        primary = animateColor(this.primary),
        onPrimary = animateColor(this.onPrimary),
        primaryContainer = animateColor(this.primaryContainer),
        onPrimaryContainer = animateColor(this.onPrimaryContainer),
        inversePrimary = animateColor(this.inversePrimary),
        secondary = animateColor(this.secondary),
        onSecondary = animateColor(this.onSecondary),
        secondaryContainer = animateColor(this.secondaryContainer),
        onSecondaryContainer = animateColor(this.onSecondaryContainer),
        tertiary = animateColor(this.tertiary),
        onTertiary = animateColor(this.onTertiary),
        tertiaryContainer = animateColor(this.tertiaryContainer),
        onTertiaryContainer = animateColor(this.onTertiaryContainer),
        background = animateColor(this.background),
        onBackground = animateColor(this.onBackground),
        surface = animateColor(this.surface),
        onSurface = animateColor(this.onSurface),
        surfaceVariant = animateColor(this.surfaceVariant),
        onSurfaceVariant = animateColor(this.onSurfaceVariant),
        surfaceTint = animateColor(this.surfaceTint),
        inverseSurface = animateColor(this.inverseSurface),
        inverseOnSurface = animateColor(this.inverseOnSurface),
        error = animateColor(this.error),
        onError = animateColor(this.onError),
        errorContainer = animateColor(this.errorContainer),
        onErrorContainer = animateColor(this.onErrorContainer),
        outline = animateColor(this.outline),
        outlineVariant = animateColor(this.outlineVariant),
        scrim = animateColor(this.scrim),
        surfaceBright = animateColor(this.surfaceBright),
        surfaceDim = animateColor(this.surfaceDim),
        surfaceContainer = animateColor(this.surfaceContainer),
        surfaceContainerHigh = animateColor(this.surfaceContainerHigh),
        surfaceContainerHighest = animateColor(this.surfaceContainerHighest),
        surfaceContainerLow = animateColor(this.surfaceContainerLow),
        surfaceContainerLowest = animateColor(this.surfaceContainerLowest)
    )
}

data class AccentPreset(
    val name: String,
    val color: Color,
    val value: Long?
)

val AccentPresets = listOf(
    AccentPreset("Default", RufusPrimary, null),
    AccentPreset("Classic Blue", Color(0xFF0061A4), 0xFF0061A4),
    AccentPreset("Rufus Orange", Color(0xFFE35205), 0xFFE35205),
    AccentPreset("Emerald Green", Color(0xFF006D3A), 0xFF006D3A),
    AccentPreset("Royal Purple", Color(0xFF6750A4), 0xFF6750A4),
    AccentPreset("Neon Teal", Color(0xFF006B6B), 0xFF006B6B),
    AccentPreset("Crimson Red", Color(0xFFBA1A1A), 0xFFBA1A1A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColorOverride: Color? = null,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val targetColorScheme = if (accentColorOverride != null) {
        baseScheme.copy(
            primary = accentColorOverride,
            primaryContainer = accentColorOverride.copy(alpha = 0.25f),
            onPrimaryContainer = accentColorOverride
        )
    } else {
        baseScheme
    }

    val animatedColorScheme = targetColorScheme.animateColors()

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content
    )
}

