package uz.kitobskaner.ui.page

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.data.ImageFilter
import uz.kitobskaner.image.ImageUtils
import uz.kitobskaner.image.PageAdjust
import uz.kitobskaner.ocr.OcrWorker
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast

/**
 * Skanerlangan sahifani qo'lda tuzatish: 4 burchakni surib kesish (perspektiva to'g'rilanadi),
 * avtomatik chegara, aylantirish, yorqinlik/kontrast va filtr. Saqlangach matn qayta aniqlanadi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditScreen(projectId: String, pageId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = App.instance.repository
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val quad = remember { floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f) }
    var quadRev by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var filter by remember { mutableStateOf(ImageFilter.ORIGINAL) }
    var rotation by remember { mutableIntStateOf(0) }
    var cropMode by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.saved_ok)

    LaunchedEffect(pageId, rotation) {
        src = withContext(Dispatchers.IO) {
            ImageUtils.decodeFile(repo.pageFile(projectId, pageId), 1400)?.let { b -> if (rotation != 0) ImageUtils.rotate(b, rotation.toFloat()) else b }
        }
    }
    // ko'rinish: kesish rejimida — asl rasm (burchaklar bilan), aks holda natija
    LaunchedEffect(src, quadRev, brightness, contrast, filter, cropMode) {
        val s = src ?: return@LaunchedEffect
        preview = if (cropMode) s else withContext(Dispatchers.Default) { PageAdjust.full(s, quad.copyOf(), brightness, contrast, filter) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.edit_page)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        val s = src ?: return@IconButton
                        busy = true
                        scope.launch {
                            try {
                                val full = withContext(Dispatchers.IO) {
                                    ImageUtils.decodeFile(repo.pageFile(projectId, pageId), repo.maxPageSide())?.let { b -> if (rotation != 0) ImageUtils.rotate(b, rotation.toFloat()) else b }
                                } ?: s
                                val out = withContext(Dispatchers.Default) { PageAdjust.full(full, quad.copyOf(), brightness, contrast, filter) }
                                repo.replacePage(projectId, pageId, out)
                                OcrWorker.start(context, projectId)
                                toast(context, savedMsg)
                                onBack()
                            } catch (e: Throwable) {
                                toast(context, e.message ?: e.javaClass.simpleName)
                            } finally {
                                busy = false
                            }
                        }
                    }) { Icon(Icons.Rounded.Check, stringResource(R.string.save), tint = MaterialTheme.colorScheme.primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val p = preview
                if (p == null) CircularProgressIndicator() else Box(Modifier.aspectRatio(p.width.toFloat() / p.height)) {
                    Image(p.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    if (cropMode) {
                        @Suppress("UNUSED_EXPRESSION") quadRev
                        var active by remember { mutableIntStateOf(-1) }
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { o ->
                                            // eng yaqin burchak
                                            var best = -1; var bd = Float.MAX_VALUE
                                            for (k in 0 until 4) {
                                                val dx = quad[2 * k] * size.width - o.x; val dy = quad[2 * k + 1] * size.height - o.y
                                                val d = dx * dx + dy * dy
                                                if (d < bd) { bd = d; best = k }
                                            }
                                            active = if (bd < 140f * 140f) best else -1
                                        },
                                        onDrag = { ch, drag ->
                                            if (active >= 0) {
                                                quad[2 * active] = (quad[2 * active] + drag.x / size.width).coerceIn(0f, 1f)
                                                quad[2 * active + 1] = (quad[2 * active + 1] + drag.y / size.height).coerceIn(0f, 1f)
                                                quadRev++
                                                ch.consume()
                                            }
                                        },
                                        onDragEnd = { active = -1 },
                                    )
                                },
                        ) {
                            @Suppress("UNUSED_EXPRESSION") quadRev
                            val pts = (0 until 4).map { Offset(quad[2 * it] * size.width, quad[2 * it + 1] * size.height) }
                            val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (k in 1 until 4) lineTo(pts[k].x, pts[k].y); close() }
                            drawPath(path, Color(0x332563EB))
                            drawPath(path, Color(0xFF2563EB), style = Stroke(width = 4f))
                            pts.forEach { drawCircle(Color.White, 22f, it); drawCircle(Color(0xFF2563EB), 16f, it) }
                        }
                    }
                }
            }
            Surface(tonalElevation = 6.dp, shadowElevation = 10.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(cropMode, { cropMode = true }, label = { Text(stringResource(R.string.crop_corners)) }, leadingIcon = { Icon(Icons.Rounded.CropFree, null) })
                        FilterChip(!cropMode, { cropMode = false }, label = { Text(stringResource(R.string.result_preview)) })
                        TextButton(onClick = {
                            val s = src ?: return@TextButton
                            scope.launch { val q = withContext(Dispatchers.Default) { PageAdjust.detect(s) }; q.copyInto(quad); quadRev++; cropMode = true }
                        }) { Icon(Icons.Rounded.AutoFixHigh, null); Text(" " + stringResource(R.string.auto_edges)) }
                        TextButton(onClick = { floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f).copyInto(quad); quadRev++ }) { Text(stringResource(R.string.full_image)) }
                        IconButton(onClick = { rotation = (rotation + 90) % 360; floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f).copyInto(quad); quadRev++ }) {
                            Icon(Icons.Rounded.RotateRight, stringResource(R.string.rotate))
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ImageFilter.ORIGINAL to R.string.f_original, ImageFilter.ENHANCED to R.string.f_enhanced, ImageFilter.GRAY to R.string.f_gray, ImageFilter.BW to R.string.f_bw)
                            .forEach { (f, l) -> FilterChip(filter == f, { filter = f; cropMode = false }, label = { Text(stringResource(l)) }) }
                    }
                    Text(stringResource(R.string.brightness) + ": ${brightness.toInt()}", style = MaterialTheme.typography.labelMedium)
                    Slider(brightness, { brightness = it; cropMode = false }, valueRange = -60f..60f)
                    Text(stringResource(R.string.contrast) + ": ${"%.2f".format(contrast)}", style = MaterialTheme.typography.labelMedium)
                    Slider(contrast, { contrast = it; cropMode = false }, valueRange = 0.6f..1.8f)
                }
            }
        }
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}
