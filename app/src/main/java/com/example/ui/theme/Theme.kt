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
    primary = PrimaryTrading,
    secondary = PrimaryGrey,
    tertiary = AccentGold,
    background = BackgroundDark,
    surface = SurfaceSlate,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceBorder,
    onSurfaceVariant = TextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTrading,
    secondary = PrimaryGrey,
    tertiary = AccentGold,
    background = BackgroundDark, // Force dark background for professional fintech vibe
    surface = SurfaceSlate,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true for trading bot theme
    dynamicColor: Boolean = false, // Set false to preserve our custom premium palette
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
