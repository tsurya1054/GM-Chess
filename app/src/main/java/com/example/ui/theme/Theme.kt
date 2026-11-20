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
  primary = Emerald500,
  onPrimary = Obsidian950,
  primaryContainer = Emerald900,
  onPrimaryContainer = Emerald100,
  secondary = Amber400,
  onSecondary = Obsidian950,
  secondaryContainer = Color(0xFF451A03),
  onSecondaryContainer = Amber100,
  tertiary = Color(0xFF38BDF8),
  background = Obsidian950,
  onBackground = Slate50,
  surface = Obsidian900,
  onSurface = Slate50,
  surfaceVariant = Obsidian800,
  onSurfaceVariant = Color(0xFFCBD5E1),
  outline = Obsidian700,
  error = Crimson500,
  onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
  primary = Emerald700,
  onPrimary = Color.White,
  primaryContainer = Emerald100,
  onPrimaryContainer = Emerald900,
  secondary = Amber600,
  onSecondary = Color.White,
  secondaryContainer = Amber100,
  onSecondaryContainer = Color(0xFF78350F),
  tertiary = Color(0xFF0284C7),
  background = Slate100,
  onBackground = Obsidian900,
  surface = Color.White,
  onSurface = Obsidian900,
  surfaceVariant = Color(0xFFE2E8F0),
  onSurfaceVariant = Slate600,
  outline = Color(0xFFCBD5E1),
  error = Crimson500,
  onError = Color.White,
)

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(18.dp),
  extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun GmChessTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content,
  )
}
