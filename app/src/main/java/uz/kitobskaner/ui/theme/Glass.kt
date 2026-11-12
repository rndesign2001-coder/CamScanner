package uz.kitobskaner.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Butun ilova orqasidagi jonli fon: sekin suzib yuruvchi rangli "nur dog'lari".
 * Yarim shaffof (shisha) kartalar shu fon ustida chuqurlik va yorug'lik hosil qiladi.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val brand = LocalBrand.current
    val t = rememberInfiniteTransition(label = "backdrop")
    val phase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )
    val c = brand.colors
    val blobs = remember(c) { listOf(c.first(), c.getOrElse(1) { c.first() }, c.last()) }
    val alpha = if (brand.isDark) 0.55f else 0.38f
    Box(
        modifier
            .fillMaxSize()
            .background(brand.base)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val r = maxOf(w, h) * 0.55f
                    val pts = listOf(
                        Offset(w * (0.15f + 0.12f * cos(phase)), h * (0.12f + 0.06f * sin(phase))),
                        Offset(w * (0.9f + 0.1f * sin(phase * 1.3f)), h * (0.42f + 0.08f * cos(phase))),
                        Offset(w * (0.3f + 0.15f * sin(phase * 0.7f)), h * (0.92f + 0.05f * cos(phase * 1.1f))),
                    )
                    pts.forEachIndexed { i, p ->
                        drawCircle(
                            Brush.radialGradient(listOf(blobs[i].copy(alpha = alpha), Color.Transparent), p, r),
                            radius = r, center = p
                        )
                    }
                }
        )
    }
}

/** Shisha sirt: yarim shaffof to'ldirish, yuqoridan yorug' chegara va yumshoq soya. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    dark: Boolean,
    elevation: Dp = 10.dp,
    tint: Color = Color.White,
): Modifier = this
    .shadow(elevation, shape, ambientColor = Color(0x33000000), spotColor = Color(0x22000000))
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (dark) listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.06f))
            else listOf(tint.copy(alpha = 0.86f), tint.copy(alpha = 0.58f))
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(
            if (dark) listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))
            else listOf(Color.White, Color.White.copy(alpha = 0.25f))
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
