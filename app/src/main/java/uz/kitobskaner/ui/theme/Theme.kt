package uz.kitobskaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kitobskaner.data.ThemeMode

val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF7C3AED)
val Purple = Color(0xFF9333EA)
val Sky = Color(0xFF0EA5E9)
val Amber = Color(0xFFF59E0B)
val Emerald = Color(0xFF10B981)

val BrandGradient = Brush.linearGradient(listOf(Indigo, Violet, Purple))

private val Light = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Sky,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Amber,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF14142B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14142B),
    surfaceVariant = Color(0xFFEEEFF7),
    onSurfaceVariant = Color(0xFF5B5D78),
    surfaceContainer = Color(0xFFF1F1F8),
    surfaceContainerHigh = Color(0xFFEBEBF4),
    outline = Color(0xFFC7C9DB),
    outlineVariant = Color(0xFFE2E3EE),
    error = Color(0xFFDC2626),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF075985),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFFCD34D),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0E0F1A),
    onBackground = Color(0xFFE7E7F3),
    surface = Color(0xFF161727),
    onSurface = Color(0xFFE7E7F3),
    surfaceVariant = Color(0xFF23253A),
    onSurfaceVariant = Color(0xFFB4B6CF),
    surfaceContainer = Color(0xFF1B1C2E),
    surfaceContainerHigh = Color(0xFF222438),
    outline = Color(0xFF4A4C66),
    outlineVariant = Color(0xFF2E3046),
    error = Color(0xFFF87171),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KitobTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

val MonoLabel = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp)
