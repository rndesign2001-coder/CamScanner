package uz.kitobskaner.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.pdf.PdfEdit
import uz.kitobskaner.pdf.PdfTools
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import java.io.File

/** PdfRenderer'ni xavfsiz ochish/yopish. */
private class PdfSource(val fd: ParcelFileDescriptor, val r: PdfRenderer) {
    fun close() { try { r.close() } catch (_: Exception) {}; try { fd.close() } catch (_: Exception) {} }

    fun render(i: Int, width: Int): Pair<Bitmap, Float>? = synchronized(r) {
        try {
            r.openPage(i).use { p ->
                val aspect = p.width.toFloat() / p.height
                val b = Bitmap.createBitmap(width, (width / aspect).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                b.eraseColor(android.graphics.Color.WHITE)
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                b to aspect
            }
        } catch (_: Exception) {
            null
        }
    }
}

private fun openSource(context: android.content.Context, uri: Uri): PdfSource? = try {
    val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException()
    PdfSource(fd, PdfRenderer(fd))
} catch (_: Exception) {
    null
}

@Composable
private fun PickPdf(uri: Uri?, onPicked: (Uri) -> Unit, content: @Composable (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) onPicked(u) }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(uri) { if (uri == null && !asked) { asked = true; picker.launch(arrayOf("application/pdf")) } }
    if (uri != null) content(uri) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text(stringResource(R.string.choose_pdf)) }
    }
}

private fun outFile(context: android.content.Context, uri: Uri, suffix: String): File {
    val ex = App.instance.exporter
    val base = PdfTools.displayName(context, uri).substringBeforeLast('.')
    return ex.uniqueFile(ex.safeName("${base}_$suffix"), "pdf")
}

// ---------------------------------------------------------------- sahifalar boshqaruvi

private class PageItem(val src: Int, val rot: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPagesScreen(initial: Uri?, onBack: () -> Unit, onDone: (File) -> Unit) {
    var uri by remember { mutableStateOf(initial) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.pdf_pages)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            PickPdf(uri, { uri = it }) { u -> PagesEditor(u, onDone) }
        }
    }
}

@Composable
private fun PagesEditor(uri: Uri, onDone: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val src = remember(uri) { openSource(context, uri) }
    DisposableEffect(src) { onDispose { src?.close() } }
    if (src == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.err_pdf)) }
        return
    }
    val items = remember(uri) { mutableStateListOf<PageItem>().apply { addAll((0 until src.r.pageCount).map { PageItem(it, 0) }) } }
    val selected = remember(uri) { mutableStateListOf<Int>() } // items ichidagi o'rinlar emas, src raqamlari
    var busy by remember { mutableStateOf(false) }
    val thumbs = remember(uri) { HashMap<Int, ImageBitmap>() }
    val errMsg = stringResource(R.string.pdf_edit_error)
    val selectFirst = stringResource(R.string.select_pages_first)
    val splitDone = stringResource(R.string.split_done)

    fun selItems() = items.filter { it.src in selected }

    fun save(specs: List<PageItem>, suffix: String) {
        if (specs.isEmpty()) { toast(context, selectFirst); return }
        scope.launch {
            busy = true
            try {
                val f = PdfEdit.rebuild(context, uri, specs.map { PdfEdit.PageSpec(it.src, it.rot) }, outFile(context, uri, suffix))
                App.instance.exporter.refreshFiles()
                onDone(f)
            } catch (e: Throwable) {
                toast(context, errMsg + ": " + (e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.pdf_pages_hint, items.size, selected.size),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(items, key = { _, it -> it.src }) { i, it ->
                var bmp by remember(it.src) { mutableStateOf(thumbs[it.src]) }
                var aspect by remember(it.src) { mutableStateOf(0.707f) }
                LaunchedEffect(it.src) {
                    if (bmp == null) withContext(Dispatchers.IO) { src.render(it.src, 260) }?.let { (b, a) -> aspect = a; bmp = b.asImageBitmap().also { im -> thumbs[it.src] = im } }
                }
                val sel = it.src in selected
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { if (sel) selected.remove(it.src) else selected.add(it.src) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val b = bmp
                        if (b != null) Image(b, null, Modifier.padding(6.dp).rotate(it.rot.toFloat()), contentScale = ContentScale.Fit)
                        else CircularProgressIndicator(Modifier.size(22.dp))
                        if (sel) Icon(
                            Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.White, CircleShape),
                        )
                    }
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Surface(tonalElevation = 6.dp, shadowElevation = 10.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Act(Icons.Rounded.SelectAll, R.string.select_all) {
                        if (selected.size == items.size) selected.clear() else { selected.clear(); selected.addAll(items.map { it.src }) }
                    }
                    Act(Icons.Rounded.RotateLeft, R.string.rotate_left) {
                        if (selected.isEmpty()) toast(context, selectFirst)
                        for (k in items.indices) if (items[k].src in selected) items[k] = PageItem(items[k].src, (items[k].rot + 270) % 360)
                    }
                    Act(Icons.Rounded.RotateRight, R.string.rotate_right) {
                        if (selected.isEmpty()) toast(context, selectFirst)
                        for (k in items.indices) if (items[k].src in selected) items[k] = PageItem(items[k].src, (items[k].rot + 90) % 360)
                    }
                    Act(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, R.string.move_earlier) {
                        val idx = items.indices.filter { items[it].src in selected }
                        if (idx.isEmpty() || idx.first() == 0) return@Act
                        for (k in idx) { val t = items[k - 1]; items[k - 1] = items[k]; items[k] = t }
                    }
                    Act(Icons.AutoMirrored.Rounded.KeyboardArrowRight, R.string.move_later) {
                        val idx = items.indices.filter { items[it].src in selected }.reversed()
                        if (idx.isEmpty() || idx.first() == items.size - 1) return@Act
                        for (k in idx) { val t = items[k + 1]; items[k + 1] = items[k]; items[k] = t }
                    }
                    Act(Icons.Rounded.Delete, R.string.delete_pages) {
                        if (selected.isEmpty()) { toast(context, selectFirst); return@Act }
                        if (selected.size >= items.size) return@Act
                        items.removeAll { it.src in selected }
                        selected.clear()
                    }
                    Act(Icons.Rounded.ContentCut, R.string.extract_pages) { save(selItems(), "ajratilgan") }
                    Act(Icons.Rounded.CallSplit, R.string.split_each) {
                        scope.launch {
                            busy = true
                            try {
                                val ex = App.instance.exporter
                                val base = PdfTools.displayName(context, uri).substringBeforeLast('.')
                                val fs = PdfEdit.split(context, uri, items.map { listOf(it.src) }) { k -> ex.uniqueFile(ex.safeName("${base}_${k + 1}"), "pdf") }
                                ex.refreshFiles()
                                toast(context, String.format(splitDone, fs.size))
                            } catch (e: Throwable) {
                                toast(context, errMsg + ": " + (e.message ?: e.javaClass.simpleName))
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
                Button(onClick = { save(items.toList(), "tahrirlangan") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Icon(Icons.Rounded.Check, null)
                    Text(stringResource(R.string.save_new_pdf), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}

@Composable
private fun Act(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// ---------------------------------------------------------------- izoh va belgilash

private val annotColors = listOf(0xFFFFEB3B.toInt(), 0xFF66BB6A.toInt(), 0xFF4FC3F7.toInt(), 0xFFF48FB1.toInt(), 0xFFE53935.toInt(), 0xFF2563EB.toInt(), 0xFF17191F.toInt())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAnnotateScreen(initial: Uri?, onBack: () -> Unit, onDone: (File) -> Unit) {
    var uri by remember { mutableStateOf(initial) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.pdf_annotate)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            PickPdf(uri, { uri = it }) { u -> AnnotEditor(u, onDone) }
        }
    }
}

@Composable
private fun AnnotEditor(uri: Uri, onDone: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val src = remember(uri) { openSource(context, uri) }
    DisposableEffect(src) { onDispose { src?.close() } }
    if (src == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.err_pdf)) }
        return
    }
    val count = src.r.pageCount
    var page by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf("highlight") }
    var color by remember { mutableIntStateOf(annotColors[0]) }
    val annots = remember { mutableStateListOf<PdfEdit.Annot>() }
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    var aspect by remember { mutableStateOf(0.707f) }
    var drag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var askText by remember { mutableStateOf<Offset?>(null) }
    var busy by remember { mutableStateOf(false) }
    val errMsg = stringResource(R.string.pdf_edit_error)
    val nothing = stringResource(R.string.nothing_to_save)

    LaunchedEffect(page) {
        bmp = null
        withContext(Dispatchers.IO) { src.render(page, 1400) }?.let { (b, a) -> aspect = a; bmp = b.asImageBitmap() }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .aspectRatio(aspect)
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tool, page) {
                            if (tool == "text" || tool == "note") detectTapGestures { o -> askText = Offset(o.x / size.width, o.y / size.height) }
                            else detectDragGestures(
                                onDragStart = { o -> drag = o to o },
                                onDrag = { ch, _ -> drag = drag?.let { it.first to ch.position } },
                                onDragEnd = {
                                    drag?.let { (a, b) ->
                                        val w = size.width.toFloat(); val h = size.height.toFloat()
                                        if (kotlin.math.abs(a.x - b.x) > 6) annots += PdfEdit.Annot(page, tool, a.x / w, a.y / h, b.x / w, b.y / h, color = color)
                                    }
                                    drag = null
                                },
                                onDragCancel = { drag = null },
                            )
                        },
                ) {
                    fun rect(a: PdfEdit.Annot) = Offset(minOf(a.u0, a.u1) * size.width, minOf(a.v0, a.v1) * size.height) to
                        Size(kotlin.math.abs(a.u1 - a.u0) * size.width, kotlin.math.abs(a.v1 - a.v0) * size.height)
                    for (a in annots.filter { it.page == page }) {
                        val c = Color(a.color)
                        when (a.kind) {
                            "highlight" -> { val (o, s) = rect(a); drawRect(c.copy(alpha = 0.38f), o, s) }
                            "underline", "strike" -> {
                                val (o, s) = rect(a)
                                val y = if (a.kind == "underline") o.y + s.height else o.y + s.height / 2
                                drawLine(c, Offset(o.x, y), Offset(o.x + s.width, y), strokeWidth = maxOf(2f, s.height * 0.08f))
                            }
                            "note" -> drawRect(Color(0xFFFFD933), Offset(a.u0 * size.width, a.v0 * size.height), Size(28f, 28f))
                            "text" -> drawContext.canvas.nativeCanvas.drawText(
                                a.text.lineSequence().firstOrNull() ?: "", a.u0 * size.width, a.v0 * size.height + 30f,
                                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = a.color; textSize = 34f },
                            )
                        }
                    }
                    drag?.let { (a, b) ->
                        val o = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
                        drawRect(Color(color).copy(alpha = 0.3f), o, Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y)))
                    }
                }
            }
        }
        Surface(tonalElevation = 6.dp, shadowElevation = 10.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                    Text("${page + 1} / $count", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { if (page < count - 1) page++ }, enabled = page < count - 1) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolBtn(Icons.Rounded.Highlight, R.string.tool_highlight, tool == "highlight") { tool = "highlight"; if (color == annotColors[4] || color == annotColors[5] || color == annotColors[6]) color = annotColors[0] }
                    ToolBtn(Icons.Rounded.FormatUnderlined, R.string.tool_underline, tool == "underline") { tool = "underline"; color = annotColors[4] }
                    ToolBtn(Icons.Rounded.FormatStrikethrough, R.string.tool_strike, tool == "strike") { tool = "strike"; color = annotColors[4] }
                    ToolBtn(Icons.Rounded.TextFields, R.string.tool_text, tool == "text") { tool = "text"; color = annotColors[5] }
                    ToolBtn(Icons.Rounded.ChatBubbleOutline, R.string.tool_note, tool == "note") { tool = "note" }
                    ToolBtn(Icons.AutoMirrored.Rounded.Undo, R.string.undo_last, false) {
                        val i = annots.indexOfLast { it.page == page }
                        if (i >= 0) annots.removeAt(i)
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    annotColors.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (c == color) 3.dp else 1.dp, if (c == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                Button(
                    onClick = {
                        if (annots.isEmpty()) { toast(context, nothing); return@Button }
                        scope.launch {
                            busy = true
                            try {
                                val f = PdfEdit.annotate(context, uri, annots.toList(), outFile(context, uri, "izohli"))
                                App.instance.exporter.refreshFiles()
                                onDone(f)
                            } catch (e: Throwable) {
                                toast(context, errMsg + ": " + (e.message ?: e.javaClass.simpleName))
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Text(stringResource(R.string.save_new_pdf), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    askText?.let { at ->
        var v by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askText = null },
            title = { Text(stringResource(if (tool == "note") R.string.tool_note else R.string.tool_text)) },
            text = { OutlinedTextField(v, { v = it }, minLines = 2) },
            confirmButton = {
                TextButton(onClick = {
                    if (v.isNotBlank()) annots += PdfEdit.Annot(page, tool, at.x, at.y, at.x, at.y, v.trim(), if (tool == "note") 0xFFFFD933.toInt() else color)
                    askText = null
                }) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = { TextButton(onClick = { askText = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}

@Composable
private fun ToolBtn(icon: ImageVector, label: Int, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
