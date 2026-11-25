package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = TealBright,
    onPrimary = ObsidianCanvas,
    primaryContainer = DarkTealContainer,
    onPrimaryContainer = SoftTealContainer,
    secondary = AmberBright,
    onSecondary = ObsidianCanvas,
    secondaryContainer = DarkAmberContainer,
    onSecondaryContainer = SoftAmberContainer,
    tertiary = Color(0xFF38BDF8),
    background = ObsidianCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder.copy(alpha = 0.5f),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D)
)

private val LightColorScheme = lightColorScheme(
    primary = LuminousTeal,
    onPrimary = Color.White,
    primaryContainer = SoftTealContainer,
    onPrimaryContainer = OnSoftTeal,
    secondary = WarmAmber,
    onSecondary = Color.White,
    secondaryContainer = SoftAmberContainer,
    onSecondaryContainer = OnSoftAmber,
    tertiary = MidnightInk,
    onTertiary = Color.White,
    background = AlabasterPaper,
    onBackground = MidnightInk,
    surface = PureCard,
    onSurface = MidnightInk,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = MutedSlateText,
    outline = SoftSlateBorder,
    outlineVariant = SoftSlateBorder.copy(alpha = 0.7f),
    error = CrispError,
    errorContainer = SoftErrorContainer
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MunasikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
