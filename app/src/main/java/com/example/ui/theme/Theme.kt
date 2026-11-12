package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RomanticDarkColorScheme = darkColorScheme(
    primary = VelvetPrimary,
    onPrimary = VelvetOnPrimary,
    primaryContainer = VelvetPrimaryContainer,
    onPrimaryContainer = VelvetOnPrimaryContainer,
    secondary = VelvetSecondary,
    onSecondary = VelvetOnSecondary,
    secondaryContainer = VelvetSecondaryContainer,
    onSecondaryContainer = VelvetOnSecondaryContainer,
    tertiary = VelvetTertiary,
    onTertiary = VelvetOnTertiary,
    tertiaryContainer = VelvetTertiaryContainer,
    onTertiaryContainer = VelvetOnTertiaryContainer,
    background = VelvetBackground,
    onBackground = VelvetOnBackground,
    surface = VelvetSurface,
    onSurface = VelvetOnSurface,
    surfaceVariant = VelvetSurfaceVariant,
    onSurfaceVariant = VelvetOnSurfaceVariant,
    outline = VelvetOutline,
    outlineVariant = VelvetOutlineVariant
)

private val RomanticLightColorScheme = lightColorScheme(
    primary = Color(0xFF9C294E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F0018),
    secondary = Color(0xFF755659),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDADB),
    onSecondaryContainer = Color(0xFF2C1518),
    tertiary = Color(0xFF795377),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD6F9),
    onTertiaryContainer = Color(0xFF2F1130),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF0F3),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF3DDE1),
    onSurfaceVariant = Color(0xFF524346),
    outline = Color(0xFF847377)
)

@Composable
fun VelvetBoxTheme(
    darkTheme: Boolean = true, // Default to romantic velvet dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) RomanticDarkColorScheme else RomanticLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    VelvetBoxTheme(darkTheme = darkTheme, content = content)
}
