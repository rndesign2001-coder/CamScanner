package uz.kitobskaner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kitobskaner.data.Palette
import uz.kitobskaner.data.ThemeMode

val Emerald = Color(0xFF10B981)
val Amber = Color(0xFFF59E0B)

class PaletteSpec(val primary: Color, val secondary: Color, val tertiary: Color, val gradient: List<Color>)

fun spec(p: Palette): PaletteSpec = when (p) {
    Palette.INDIGO, Palette.DYNAMIC -> PaletteSpec(
        Color(0xFF4F46E5), Color(0xFF0EA5E9), Color(0xFFF59E0B),
        listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF9333EA))
    )
    Palette.OCEAN -> PaletteSpec(
        Color(0xFF0284C7), Color(0xFF14B8A6), Color(0xFFF97316),
        listOf(Color(0xFF0369A1), Color(0xFF0284C7), Color(0xFF06B6D4))
    )
    Palette.EMERALD -> PaletteSpec(
        Color(0xFF059669), Color(0xFF0EA5E9), Color(0xFFEAB308),
        listOf(Color(0xFF065F46), Color(0xFF059669), Color(0xFF34D399))
    )
    Palette.SUNSET -> PaletteSpec(
        Color(0xFFEA580C), Color(0xFFDB2777), Color(0xFF8B5CF6),
        listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFDB2777))
    )
    Palette.ROSE -> PaletteSpec(
        Color(0xFFDB2777), Color(0xFF8B5CF6), Color(0xFFF59E0B),
        listOf(Color(0xFFBE185D), Color(0xFFDB2777), Color(0xFFA855F7))
    )
    Palette.GRAPHITE -> PaletteSpec(
        Color(0xFF334155), Color(0xFF6366F1), Color(0xFFF59E0B),
        listOf(Color(0xFF0F172A), Color(0xFF334155), Color(0xFF64748B))
    )
}

private val White = Color.White
private val Night = Color(0xFF07080E)

private fun lightScheme(s: PaletteSpec): ColorScheme = lightColorScheme(
    primary = s.primary,
    onPrimary = White,
    primaryContainer = lerp(s.primary, White, 0.84f),
    onPrimaryContainer = lerp(s.primary, Color.Black, 0.55f),
    secondary = s.secondary,
    onSecondary = White,
    secondaryContainer = lerp(s.secondary, White, 0.85f),
    onSecondaryContainer = lerp(s.secondary, Color.Black, 0.6f),
    tertiary = s.tertiary,
    tertiaryContainer = lerp(s.tertiary, White, 0.82f),
    onTertiaryContainer = lerp(s.tertiary, Color.Black, 0.62f),
    background = lerp(s.primary, White, 0.965f),
    onBackground = Color(0xFF14142B),
    surface = White,
    onSurface = Color(0xFF14142B),
    surfaceVariant = lerp(s.primary, White, 0.92f),
    onSurfaceVariant = Color(0xFF5B5D78),
    surfaceContainerLowest = White,
    surfaceContainerLow = lerp(s.primary, White, 0.975f),
    surfaceContainer = lerp(s.primary, White, 0.95f),
    surfaceContainerHigh = lerp(s.primary, White, 0.92f),
    surfaceContainerHighest = lerp(s.primary, White, 0.89f),
    outline = lerp(s.primary, Color(0xFFB0B3C6), 0.8f),
    outlineVariant = lerp(s.primary, Color(0xFFE2E3EE), 0.9f),
    error = Color(0xFFDC2626),
)

private fun darkScheme(s: PaletteSpec): ColorScheme {
    val p = lerp(s.primary, White, 0.42f)
    return darkColorScheme(
        primary = p,
        onPrimary = lerp(s.primary, Color.Black, 0.7f),
        primaryContainer = lerp(s.primary, Night, 0.45f),
        onPrimaryContainer = lerp(s.primary, White, 0.85f),
        secondary = lerp(s.secondary, White, 0.4f),
        onSecondary = lerp(s.secondary, Color.Black, 0.7f),
        secondaryContainer = lerp(s.secondary, Night, 0.55f),
        onSecondaryContainer = lerp(s.secondary, White, 0.85f),
        tertiary = lerp(s.tertiary, White, 0.35f),
        tertiaryContainer = lerp(s.tertiary, Night, 0.6f),
        onTertiaryContainer = lerp(s.tertiary, White, 0.85f),
        background = lerp(s.primary, Night, 0.93f),
        onBackground = Color(0xFFE7E7F3),
        surface = lerp(s.primary, Night, 0.88f),
        onSurface = Color(0xFFE7E7F3),
        surfaceVariant = lerp(s.primary, Night, 0.8f),
        onSurfaceVariant = Color(0xFFB4B6CF),
        surfaceContainerLowest = lerp(s.primary, Night, 0.95f),
        surfaceContainerLow = lerp(s.primary, Night, 0.9f),
        surfaceContainer = lerp(s.primary, Night, 0.86f),
        surfaceContainerHigh = lerp(s.primary, Night, 0.82f),
        surfaceContainerHighest = lerp(s.primary, Night, 0.78f),
        outline = lerp(s.primary, Color(0xFF4A4C66), 0.8f),
        outlineVariant = lerp(s.primary, Color(0xFF2E3046), 0.85f),
        error = Color(0xFFF87171),
    )
}

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
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

class Brand(val gradient: Brush, val colors: List<Color>, val isDark: Boolean)

val LocalBrand = staticCompositionLocalOf {
    Brand(Brush.linearGradient(spec(Palette.INDIGO).gradient), spec(Palette.INDIGO).gradient, false)
}

/** Joriy mavzuning brend gradienti. */
val brandGradient: Brush
    @Composable @ReadOnlyComposable get() = LocalBrand.current.gradient

@Composable
fun KitobTheme(mode: ThemeMode, palette: Palette, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val s = spec(palette)
    val scheme = if (palette == Palette.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkScheme(s) else lightScheme(s)
    val gradColors = if (palette == Palette.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        listOf(scheme.primary, lerp(scheme.primary, scheme.tertiary, 0.5f), scheme.tertiary)
    } else if (dark) s.gradient.map { lerp(it, Night, 0.18f) } else s.gradient
    CompositionLocalProvider(LocalBrand provides Brand(Brush.linearGradient(gradColors), gradColors, dark)) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
