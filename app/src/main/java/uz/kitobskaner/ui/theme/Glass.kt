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
    // Statik fon: bir marta chiziladi (avvalgi doimiy animatsiya har kadrda butun ekranni
    // qayta chizib, ilovani sekinlashtirardi).
    val brand = LocalBrand.current
    val c = brand.colors
    val alpha = if (brand.isDark) 0.30f else 0.16f
    Box(
        modifier
            .fillMaxSize()
            .background(brand.base)
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = maxOf(w, h) * 0.6f
                drawCircle(Brush.radialGradient(listOf(c.first().copy(alpha = alpha), Color.Transparent), Offset(w * 0.05f, 0f), r), r, Offset(w * 0.05f, 0f))
                drawCircle(Brush.radialGradient(listOf(c.last().copy(alpha = alpha * 0.8f), Color.Transparent), Offset(w, h * 0.55f), r * 0.8f), r * 0.8f, Offset(w, h * 0.55f))
            }
    )
}

/** Shisha sirt: yarim shaffof to'ldirish, yuqoridan yorug' chegara va yumshoq soya. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    dark: Boolean,
    elevation: Dp = 3.dp,
    tint: Color = Color.White,
): Modifier = this
    .shadow(elevation, shape, ambientColor = Color(0x33000000), spotColor = Color(0x22000000))
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (dark) listOf(Color(0xFF262838), Color(0xFF1E2030))
            else listOf(tint.copy(alpha = 0.98f), tint.copy(alpha = 0.94f))
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(
            if (dark) listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))
            else listOf(Color(0x14000000), Color(0x0A000000))
        ),
        shape
    )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalBrand.current.isDark
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .scale(if (pressed) 0.97f else 1f)
            .glass(shape, dark)
            .then(if (onClick != null) Modifier.clickable(source, null, onClick = onClick) else Modifier),
        content = content,
    )
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
    val shape = RoundedCornerShape(22.dp)
    val bg = if (filled) Brush.linearGradient(brand.colors.map { if (enabled) it else lerp(it, Color.Gray, 0.7f) })
    else Brush.verticalGradient(listOf(Color.White.copy(alpha = if (brand.isDark) 0.16f else 0.7f), Color.White.copy(alpha = if (brand.isDark) 0.06f else 0.35f)))
    Box(
        modifier
            .heightIn(min = 54.dp)
            .scale(if (pressed) 0.96f else 1f)
            .shadow(if (filled && enabled) 14.dp else 4.dp, shape, spotColor = brand.colors.first().copy(alpha = 0.6f))
            .clip(shape)
            .background(bg)
            // yuqori yarmidagi "shisha yaltirog'i"
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (filled) 0.35f else 0.25f), Color.Transparent),
                        endY = size.height * 0.55f
                    )
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), shape)
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
