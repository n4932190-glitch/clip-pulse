package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkStudioColorScheme = darkColorScheme(
    primary = ElectricCoral,
    onPrimary = OnElectricCoral,
    primaryContainer = ElectricCoralContainer,
    onPrimaryContainer = OnElectricCoralContainer,
    secondary = NeonCyan,
    onSecondary = OnNeonCyan,
    secondaryContainer = NeonCyanContainer,
    onSecondaryContainer = OnNeonCyanContainer,
    tertiary = AmberGold,
    onTertiary = OnAmberGold,
    tertiaryContainer = AmberGoldContainer,
    onTertiaryContainer = OnAmberGoldContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = StudioSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = StudioSurfaceHigh,
    outline = StudioOutline,
    outlineVariant = StudioOutlineVariant,
    error = ErrorCoral,
    onError = OnElectricCoral
)

private val LightStudioColorScheme = lightColorScheme(
    primary = LightCoral,
    onPrimary = LightSurface,
    primaryContainer = OnElectricCoralContainer,
    onPrimaryContainer = ElectricCoralContainer,
    secondary = LightCyan,
    onSecondary = LightSurface,
    secondaryContainer = OnNeonCyanContainer,
    onSecondaryContainer = NeonCyanContainer,
    tertiary = LightGold,
    onTertiary = LightSurface,
    tertiaryContainer = OnAmberGoldContainer,
    onTertiaryContainer = AmberGoldContainer,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = ErrorCoral,
    onError = LightSurface
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClipPulseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkStudioColorScheme else LightStudioColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
