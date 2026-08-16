package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
