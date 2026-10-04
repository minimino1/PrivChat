package dev.and.chatt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BrandBlue = Color(0xFF2F6FED)
private val BrandBlueDark = Color(0xFF9CB9FF)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    secondary = Color(0xFF00A37A),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    secondary = Color(0xFF6FDCB8),
    background = Color(0xFF101317),
    surface = Color(0xFF1A1F26),
)

private val AppTypography = Typography(
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp),
)

@Composable
fun ChattTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}