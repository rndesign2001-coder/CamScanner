package uz.kitobskaner.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Butun ilova orqasidagi jonli fon: sekin suzib yuruvchi rangli "nur dog'lari".
 * Yarim shaffof (shisha) kartalar shu fon ustida chuqurlik va yorug'lik hosil qiladi.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    // tekis, sokin fon (iOS tizim foni)
    Box(modifier.fillMaxSize().background(LocalBrand.current.base))
}

/** iOS uslubidagi sirt: oq (tunda to'q) to'ldirish, ingichka chegara va juda yengil soya. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(16.dp),
    dark: Boolean,
    elevation: Dp = 1.dp,
    @Suppress("UNUSED_PARAMETER") tint: Color = Color.White,
): Modifier = this
    .shadow(elevation, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
    .clip(shape)
    .background(if (dark) Color(0xFF1A1C21) else Color.White)
    .border(1.dp, if (dark) Color(0xFF2A2D35) else Color(0xFFE9ECF1), shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalBrand.current.isDark
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .scale(if (pressed) 0.985f else 1f)
            .glass(shape, dark)
            .then(if (onClick != null) Modifier.clickable(source, null, onClick = onClick) else Modifier),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        ) { content() }
    }
}

/** Oynasimon, yaltiroq gradient tugma (bosilganda siqiladi). */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val brand = LocalBrand.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    Box(
        modifier
            .heightIn(min = 50.dp)
            .scale(if (pressed) 0.98f else 1f)
            .clip(shape)
            .background(
                if (filled) (if (enabled) primary else primary.copy(alpha = 0.4f))
                else if (brand.isDark) Color(0xFF23262D) else Color(0xFFEEF1F5)
            )
            .clickable(source, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                if (filled) Color.White else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}
