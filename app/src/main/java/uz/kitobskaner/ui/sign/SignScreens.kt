package uz.kitobskaner.ui.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Approval
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.image.ImageUtils
import uz.kitobskaner.image.InkColor
import uz.kitobskaner.image.InkExtractor
import uz.kitobskaner.pdf.PdfSigner
import uz.kitobskaner.pdf.PdfTools
import uz.kitobskaner.pdf.Placement
import uz.kitobskaner.ui.components.ConfirmDialog
import uz.kitobskaner.ui.components.EmptyState
import uz.kitobskaner.ui.components.GradientIcon
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.theme.GlassButton
import uz.kitobskaner.ui.theme.GlassCard
import uz.kitobskaner.ui.theme.brandGradient
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shaffof rasm ostidagi shaxmat fon — shaffoflik ko'rinib turishi uchun. */
fun Modifier.checkerboard(): Modifier = drawBehind {
    val s = 10.dp.toPx()
    val light = Color(0xFFF4F4F7)
    val darkC = Color(0xFFE2E3EA)
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else s
        while (x < size.width) {
            drawRect(darkC, Offset(x, y), androidx.compose.ui.geometry.Size(s, s))
            x += 2 * s
        }
        y += s; row++
    }
}

@Composable
private fun rememberFileBitmap(file: File): ImageBitmap? {
    var bmp by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.absolutePath, file.lastModified()) {
        bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
    }
    return bmp
}

// ---------------------------------------------------------------- kutubxona

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SignaturesScreen(onBack: () -> Unit, onExtract: () -> Unit, onDraw: () -> Unit, onPlace: (Uri) -> Unit) {
    val store = App.instance.signatures
    val items by store.items.collectAsState()
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) onPlace(u) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(stringResource(R.string.sign_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                GlassButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth(), enabled = items.isNotEmpty()) {
                    Icon(Icons.Rounded.Approval, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.sign_place_pdf), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(Icons.Rounded.PhotoLibrary, stringResource(R.string.sign_extract), stringResource(R.string.sign_extract_sub), Modifier.weight(1f), onExtract)
                    ActionTile(Icons.Rounded.Draw, stringResource(R.string.sign_draw), stringResource(R.string.sign_draw_sub), Modifier.weight(1f), onDraw)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.sign_library), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
            if (items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Rounded.Approval, stringResource(R.string.sign_empty), stringResource(R.string.sign_empty_hint))
            }
            items(items, key = { it.absolutePath }) { f ->
                val bmp = rememberFileBitmap(f)
                GlassCard(modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { deleteTarget = f })) {
                    Column(Modifier.padding(10.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.4f)
                                .clip(RoundedCornerShape(16.dp))
                                .checkerboard(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) Image(bmp, null, Modifier.padding(8.dp), contentScale = ContentScale.Fit)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(if (store.isStamp(f)) R.string.sign_kind_stamp else R.string.sign_kind_sign),
                                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { deleteTarget = f }) { Icon(Icons.Rounded.Delete, null) }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { f ->
        ConfirmDialog(stringResource(R.string.delete_q), null, stringResource(R.string.delete), true, onDismiss = { deleteTarget = null }) {
            store.delete(f)
        }
    }
}

@Composable
private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            GradientIcon(icon, brandGradient, 46.dp, 15.dp)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}

// ---------------------------------------------------------------- rasmdan ajratish

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = App.instance.signatures
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    var stamp by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(InkColor.ORIGINAL) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    var busy by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun load(uri: Uri) {
        scope.launch {
            busy = true
            source = withContext(Dispatchers.IO) { ImageUtils.decodeUri(context, uri, 1800) }
            busy = false
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) load(u) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) load(Uri.fromFile(f))
    }
    fun openCamera() {
        val f = File(context.cacheDir, "camera/sign_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        cameraFile = f
        camera.launch(uz.kitobskaner.ui.components.fileUri(context, f))
    }

    LaunchedEffect(source, stamp, color, sensitivity) {
        val s = source ?: return@LaunchedEffect
        delay(120)
        busy = true
        result = withContext(Dispatchers.Default) { InkExtractor.extract(s, sensitivity, stamp, color) }
        busy = false
    }
    val saved = stringResource(R.string.saved_ok)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(stringResource(R.string.sign_extract)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                GlassButton(
                    onClick = {
                        val r = result ?: return@GlassButton
                        scope.launch {
                            store.save(r, if (stamp) "stamp" else "sign")
                            toast(context, saved)
                            onBack()
                        }
                    },
                    enabled = result != null && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .checkerboard(),
                    contentAlignment = Alignment.Center
                ) {
                    val r = result
                    if (r != null) Image(r.asImageBitmap(), null, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
                    else if (source == null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.sign_extract_hint), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                    }
                    if (busy) CircularProgressIndicator()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f), filled = false) {
                    Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.src_gallery_short))
                }
                GlassButton(onClick = { openCamera() }, modifier = Modifier.weight(1f), filled = false) {
                    Icon(Icons.Rounded.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.src_camera))
                }
            }
            if (source != null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !stamp, onClick = { stamp = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.sign_kind_sign))
                    }
                    SegmentedButton(selected = stamp, onClick = { stamp = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.sign_kind_stamp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        InkColor.ORIGINAL to R.string.f_original, InkColor.BLUE to R.string.ink_blue,
                        InkColor.BLACK to R.string.ink_black, InkColor.RED to R.string.ink_red,
                    ).forEach { (c, l) ->
                        FilterChip(selected = color == c, onClick = { color = c }, label = { Text(stringResource(l)) })
                    }
                }
                Text(stringResource(R.string.sign_sensitivity), style = MaterialTheme.typography.labelLarge)
                Slider(value = sensitivity, onValueChange = { sensitivity = it })
            }
        }
    }
}

// ---------------------------------------------------------------- barmoq bilan chizish

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawSignatureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = App.instance.signatures
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<Pair<List<Offset>, Pair<Color, Float>>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(Color(0xFF1A3AA8)) }
    var width by remember { mutableFloatStateOf(6f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val saved = stringResource(R.string.saved_ok)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(stringResource(R.string.sign_draw)) },
                actions = {
                    IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1) }) { Icon(Icons.Rounded.Undo, null) }
                    IconButton(onClick = { strokes.clear() }) { Icon(Icons.Rounded.Delete, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                GlassButton(
                    onClick = {
                        if (strokes.isEmpty() || canvasSize.width == 0) return@GlassButton
                        val bmp = renderStrokes(strokes.toList(), canvasSize)
                        scope.launch { store.save(bmp, "sign"); toast(context, saved); onBack() }
                    },
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(color, width) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDrag = { ch, _ -> current = current + ch.position },
                                onDragEnd = { strokes += current to (color to width); current = emptyList() },
                                onDragCancel = { current = emptyList() },
                            )
                        }
                ) {
                    // imzo chizig'i
                    drawLine(Color(0x22000000), Offset(24f, size.height * 0.72f), Offset(size.width - 24f, size.height * 0.72f), 2f)
                    val all = strokes.toList() + listOf(current to (color to width))
                    for ((pts, style) in all) {
                        if (pts.size < 2) continue
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 1 until pts.size) {
                                val m = Offset((pts[i - 1].x + pts[i].x) / 2, (pts[i - 1].y + pts[i].y) / 2)
                                quadraticTo(pts[i - 1].x, pts[i - 1].y, m.x, m.y)
                            }
                        }
                        drawPath(path, style.first, style = Stroke(style.second * density / 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Color(0xFF1A3AA8), Color(0xFF111114), Color(0xFFC41C24), Color(0xFF5C28A0)).forEach { c ->
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = c }
                    )
                }
                Slider(value = width, onValueChange = { width = it }, valueRange = 2f..16f, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun renderStrokes(strokes: List<Pair<List<Offset>, Pair<Color, Float>>>, size: IntSize): Bitmap {
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    var minX = size.width.toFloat(); var minY = size.height.toFloat(); var maxX = 0f; var maxY = 0f
    for ((pts, style) in strokes) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = style.first.toArgb(); this.style = Paint.Style.STROKE
            strokeWidth = style.second * 1.6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path()
        pts.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x, p.y) else {
                val q = pts[i - 1]
                path.quadTo(q.x, q.y, (q.x + p.x) / 2, (q.y + p.y) / 2)
            }
            minX = min(minX, p.x); minY = min(minY, p.y); maxX = max(maxX, p.x); maxY = max(maxY, p.y)
        }
        c.drawPath(path, paint)
    }
    val pad = 20
    val x0 = (minX - pad).toInt().coerceIn(0, size.width - 1)
    val y0 = (minY - pad).toInt().coerceIn(0, size.height - 1)
    val x1 = (maxX + pad).toInt().coerceIn(x0 + 1, size.width)
    val y1 = (maxY + pad).toInt().coerceIn(y0 + 1, size.height)
    return Bitmap.createBitmap(bmp, x0, y0, x1 - x0, y1 - y0)
}

// ---------------------------------------------------------------- PDF'ga joylash

private class Placed(val file: File, val aspect: Float, page: Int) {
    var page by mutableIntStateOf(page)
    var cx by mutableFloatStateOf(0.5f)
    var cy by mutableFloatStateOf(0.75f)
    var w by mutableFloatStateOf(0.32f)
    var rot by mutableFloatStateOf(0f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(src: Uri, onBack: () -> Unit, onDone: (File) -> Unit) {
    val context = LocalContext.current
    val app = App.instance
    val items by app.signatures.items.collectAsState()
    val scope = rememberCoroutineScope()
    var pageCount by remember { mutableIntStateOf(0) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageBmp by remember { mutableStateOf<ImageBitmap?>(null) }
    val placed = remember { mutableStateListOf<Placed>() }
    var selected by remember { mutableStateOf<Placed?>(null) }
    var busy by remember { mutableStateOf(false) }
    val name = remember(src) { PdfTools.displayName(context, src) }

    val renderer = remember(src) {
        try {
            val fd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(src, "r")
            fd?.let { it to PdfRenderer(it) }
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(renderer) {
        onDispose { renderer?.second?.close(); renderer?.first?.close() }
    }
    LaunchedEffect(renderer) { pageCount = renderer?.second?.pageCount ?: 0 }
    LaunchedEffect(renderer, pageIndex) {
        val r = renderer?.second ?: return@LaunchedEffect
        pageBmp = withContext(Dispatchers.IO) {
            synchronized(r) {
                r.openPage(pageIndex).use { p ->
                    val k = 1400f / p.width
                    val b = Bitmap.createBitmap(1400, (p.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    b.eraseColor(android.graphics.Color.WHITE)
                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    b.asImageBitmap()
                }
            }
        }
    }
    val err = stringResource(R.string.error)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(if (pageCount > 0) "${pageIndex + 1} / $pageCount" else stringResource(R.string.sign_place_pdf)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.absolutePath }) { f ->
                        val bmp = rememberFileBitmap(f)
                        GlassCard(
                            modifier = Modifier.size(width = 96.dp, height = 64.dp),
                            shape = RoundedCornerShape(16.dp),
                            onClick = {
                                val b = bmp ?: return@GlassCard
                                val p = Placed(f, b.width.toFloat() / b.height, pageIndex)
                                placed += p; selected = p
                            },
                        ) {
                            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                        }
                    }
                }
                GlassButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val out = app.exporter.uniqueFile(app.exporter.safeName("$name (imzolangan)"), "pdf")
                                PdfSigner.apply(context, src, placed.map {
                                    Placement(it.page, it.file, it.cx, it.cy, it.w, it.aspect, it.rot)
                                }, out)
                                app.exporter.refreshFiles()
                                onDone(out)
                            } catch (e: Throwable) {
                                toast(context, err + ": " + (e.message ?: ""))
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = placed.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = pageBmp
            if (renderer == null) Text(stringResource(R.string.err_pdf))
            else if (bmp == null) CircularProgressIndicator()
            else BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                val aspect = bmp.width.toFloat() / bmp.height
                val density = LocalDensity.current
                val boxW = min(maxWidth.value, maxHeight.value * aspect)
                val pw = with(density) { boxW.dp.toPx() }
                val ph = pw / aspect
                Box(
                    Modifier
                        .size(boxW.dp, (boxW / aspect).dp)
                        .background(Color.White)
                        .pointerInput(Unit) { detectTapGestures { selected = null } }
                ) {
                    Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    placed.filter { it.page == pageIndex }.forEach { pl ->
                        val sb = rememberFileBitmap(pl.file)
                        val iw = pl.w * pw
                        val ih = iw / pl.aspect
                        Box(
                            Modifier
                                .offset { IntOffset((pl.cx * pw - iw / 2).roundToInt(), (pl.cy * ph - ih / 2).roundToInt()) }
                                .size(with(density) { iw.toDp() }, with(density) { ih.toDp() })
                                .graphicsLayer { rotationZ = pl.rot }
                                .then(
                                    if (selected === pl) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    else Modifier
                                )
                                .pointerInput(pl) {
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        selected = pl
                                        pl.cx = (pl.cx + pan.x / pw).coerceIn(0f, 1f)
                                        pl.cy = (pl.cy + pan.y / ph).coerceIn(0f, 1f)
                                        pl.w = (pl.w * zoom).coerceIn(0.05f, 1f)
                                        pl.rot += rotation
                                    }
                                }
                        ) {
                            if (sb != null) Image(sb, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            if (selected === pl) Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(12.dp, (-12).dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .clickable { placed.remove(pl); selected = null },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            if (pageCount > 1) {
                Row(Modifier.align(Alignment.CenterStart)) {
                    IconButton(onClick = { if (pageIndex > 0) pageIndex-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                }
                Row(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { if (pageIndex < pageCount - 1) pageIndex++ }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                }
            }
            if (items.isEmpty()) Text(
                stringResource(R.string.sign_empty_hint),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}
