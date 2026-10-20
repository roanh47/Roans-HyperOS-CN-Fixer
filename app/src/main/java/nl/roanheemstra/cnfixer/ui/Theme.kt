package nl.roanheemstra.cnfixer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CnColors = darkColorScheme(
    primary = Color(0xFF8C7BFF),
    onPrimary = Color(0xFF120A2E),
    secondary = Color(0xFF38E8FF),
    onSecondary = Color(0xFF00201F),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFEDEBF5),
    surface = Color(0xFF16161F),
    onSurface = Color(0xFFEDEBF5),
    surfaceVariant = Color(0xFF23232F),
    onSurfaceVariant = Color(0xFFB9B6C8),
    error = Color(0xFFFF6B6B),
)

@Composable
fun CnFixerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CnColors, content = content)
}
