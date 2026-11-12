package uz.kitobskaner.ui.office

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Approval
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.data.Align
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.office.DocxReader
import uz.kitobskaner.office.PptxReader
import uz.kitobskaner.office.Sheet
import uz.kitobskaner.office.Slide
import uz.kitobskaner.office.XlsxReader
import uz.kitobskaner.pdf.DocxWriter
import uz.kitobskaner.pdf.XlsxWriter
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.page.Markup
import uz.kitobskaner.ui.theme.GlassCard
import java.io.File

enum class DocKind { PDF, DOCX, XLSX, PPTX, TXT, UNKNOWN }

fun kindOf(name: String, mime: String?): DocKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext == "pdf" || mime == "application/pdf" -> DocKind.PDF
        ext == "docx" || mime?.contains("wordprocessingml") == true -> DocKind.DOCX
        ext == "xlsx" || mime?.contains("spreadsheetml") == true -> DocKind.XLSX
        ext == "pptx" || mime?.contains("presentationml") == true -> DocKind.PPTX
        ext in setOf("txt", "md", "csv", "log", "json", "xml") || mime?.startsWith("text/") == true -> DocKind.TXT
        else -> DocKind.UNKNOWN
    }
}

private fun queryName(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == "file") return File(uri.path ?: "doc").name
    try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "doc"
        }
    } catch (_: Exception) {
    }
    return uri.lastPathSegment ?: "doc"
}

/**
 * Hujjat ko'ruvchi va muharriri: PDF, Word (DOCX), Excel (XLSX), PowerPoint (PPTX), matn.
 * Tahrirlangan nusxa "Fayllar" bo'limiga yangi fayl sifatida saqlanadi (asl fayl o'zgarmaydi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    uri: Uri,
    onBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    onSign: (Uri) -> Unit,
    onCompress: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = App.instance
    val scope = rememberCoroutineScope()
    val name = remember(uri) { queryName(context, uri) }
    val kind = remember(uri) { kindOf(name, context.contentResolver.getType(uri)) }
    val base = name.substringBeforeLast('.')
    var loading by remember { mutableStateOf(kind != DocKind.PDF) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    // hujjat holatlari
    var blocks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }
    var markup by remember { mutableStateOf("") }
    val sheets = remember { mutableStateListOf<Sheet>() }
    var sheetIndex by remember { mutableIntStateOf(0) }
    var slides by remember { mutableStateOf<List<Slide>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    val errOpen = stringResource(R.string.err_open_doc)

    LaunchedEffect(uri) {
        if (kind == DocKind.PDF) return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    when (kind) {
                        DocKind.DOCX -> blocks = DocxReader.read(input)
                        DocKind.XLSX -> { val s = XlsxReader.read(input); withContext(Dispatchers.Main) { sheets.clear(); sheets.addAll(s) } }
                        DocKind.PPTX -> slides = PptxReader.read(input)
                        else -> text = input.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
        } catch (e: Throwable) {
            error = errOpen
        }
        loading = false
    }

    val savedMsg = stringResource(R.string.saved_ok)
    fun save() {
        busy = true
        scope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    when (kind) {
                        DocKind.DOCX -> {
                            val b = if (editing) Markup.fromMarkup(markup, blocks) else blocks
                            blocks = b
                            app.exporter.uniqueFile(app.exporter.safeName(base), "docx").also { out ->
                                out.outputStream().buffered().use { DocxWriter.write(base, b, File(context.cacheDir, "none"), it, withTitle = false) }
                            }
                        }
                        DocKind.XLSX -> app.exporter.uniqueFile(app.exporter.safeName(base), "xlsx").also { out ->
                            out.outputStream().buffered().use { XlsxWriter.writeNamed(sheets.map { it.name to it.rows }, it) }
                        }
                        DocKind.PPTX -> app.exporter.uniqueFile(app.exporter.safeName(base), "pdf").also { out -> slidesToPdf(slides, out) }
                        else -> app.exporter.uniqueFile(app.exporter.safeName(base), name.substringAfterLast('.', "txt")).also { it.writeText(text) }
                    }
                }
                app.exporter.refreshFiles()
                editing = false
                dirty = false
                toast(context, savedMsg)
                onOpenFile(f)
            } catch (e: Throwable) {
                toast(context, (e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    BackHandler(enabled = editing) { editing = false }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(name, maxLines = 1, style = MaterialTheme.typography.titleMedium) },
                actions = {
                    when (kind) {
                        DocKind.PDF -> {
                            IconButton(onClick = { onSign(uri) }) { Icon(Icons.Rounded.Approval, stringResource(R.string.sign_title)) }
                            IconButton(onClick = {
                                // siqish uchun fayl nusxasi kerak
                                scope.launch {
                                    val f = withContext(Dispatchers.IO) {
                                        File(context.cacheDir, "open_$name").also { out ->
                                            context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                                        }
                                    }
                                    onCompress(f.absolutePath)
                                }
                            }) { Icon(Icons.Rounded.Compress, null) }
                        }
                        DocKind.DOCX -> {
                            if (editing) IconButton(onClick = { save() }) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                            else IconButton(onClick = { markup = Markup.toMarkup(blocks); editing = true }) { Icon(Icons.Rounded.Edit, null) }
                        }
                        DocKind.XLSX, DocKind.TXT -> IconButton(onClick = { save() }, enabled = dirty) { Icon(Icons.Rounded.Save, null) }
                        DocKind.PPTX -> IconButton(onClick = { save() }) { Icon(Icons.Rounded.PictureAsPdf, null) }
                        else -> {}
                    }
                    if (kind != DocKind.PDF && kind != DocKind.UNKNOWN) IconButton(onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) {
                                File(context.cacheDir, "share/$name").also { out ->
                                    out.parentFile?.mkdirs()
                                    context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                                }
                            }
                            uz.kitobskaner.ui.components.shareFile(context, f, context.contentResolver.getType(uri) ?: "*/*")
                        }
                    }) { Icon(Icons.Rounded.IosShare, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            when {
                error != null -> Text(error!!, Modifier.align(Alignment.Center).padding(24.dp))
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                kind == DocKind.PDF -> PdfViewer(uri)
                kind == DocKind.DOCX -> if (editing) OutlinedTextField(
                    value = markup,
                    onValueChange = { markup = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                    shape = RoundedCornerShape(18.dp),
                    supportingText = { Text(stringResource(R.string.markup_hint)) },
                ) else DocView(blocks)
                kind == DocKind.XLSX -> SheetView(sheets, sheetIndex, onSheet = { sheetIndex = it }, onEdit = { dirty = true })
                kind == DocKind.PPTX -> SlidesView(slides)
                kind == DocKind.TXT -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; dirty = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(18.dp),
                )
                else -> Text(stringResource(R.string.err_open_doc), Modifier.align(Alignment.Center).padding(24.dp))
            }
        }
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}

// ---------------------------------------------------------------- PDF

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfViewer(uri: Uri) {
    val context = LocalContext.current
    val renderer = remember(uri) {
        try {
            val fd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            fd?.let { it to PdfRenderer(it) }
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(renderer) { onDispose { renderer?.second?.close(); renderer?.first?.close() } }
    if (renderer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.err_pdf)) }
        return
    }
    val count = remember(renderer) { renderer.second.pageCount }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { z, pan, _ ->
        scale = (scale * z).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + pan
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(count) { i ->
            var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
            var aspect by remember { mutableFloatStateOf(0.707f) }
            LaunchedEffect(i) {
                bmp = withContext(Dispatchers.IO) {
                    synchronized(renderer.second) {
                        renderer.second.openPage(i).use { p ->
                            aspect = p.width.toFloat() / p.height
                            val w = 1240
                            val b = Bitmap.createBitmap(w, (w / aspect).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            b.eraseColor(AColor.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            b.asImageBitmap()
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .shadow(10.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                val b = bmp
                if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                else CircularProgressIndicator()
            }
        }
    }
}

// ---------------------------------------------------------------- Word

@Composable
private fun DocView(blocks: List<TextBlock>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (blocks.isEmpty()) Text(stringResource(R.string.no_text_found))
                    blocks.forEach { b ->
                        val annotated = buildAnnotatedString {
                            for (s in b.spans) {
                                withStyle(SpanStyle(fontWeight = if (s.bold) FontWeight.Bold else null, fontStyle = if (s.italic) FontStyle.Italic else null)) {
                                    append(s.text)
                                }
                            }
                        }
                        val style = when (b.type) {
                            BlockType.HEADING1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            BlockType.HEADING2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            else -> MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp)
                        }
                        Text(
                            annotated, style = style, fontFamily = FontFamily.Serif,
                            textAlign = when (b.align) {
                                Align.CENTER -> TextAlign.Center
                                Align.RIGHT -> TextAlign.End
                                Align.JUSTIFY -> TextAlign.Justify
                                Align.LEFT -> TextAlign.Start
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Excel

private fun colName(i: Int): String {
    var n = i + 1
    val sb = StringBuilder()
    while (n > 0) { val m = (n - 1) % 26; sb.insert(0, 'A' + m); n = (n - 1) / 26 }
    return sb.toString()
}

@Composable
private fun SheetView(sheets: SnapshotStateList<Sheet>, index: Int, onSheet: (Int) -> Unit, onEdit: () -> Unit) {
    if (sheets.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_text_found)) }
        return
    }
    val sheet = sheets[index.coerceIn(0, sheets.size - 1)]
    // tahrirlanadigan nusxa: o'zgarish darhol ko'rinishi uchun versiya hisoblagichi
    var version by remember(sheet) { mutableIntStateOf(0) }
    val cols = maxOf(6, (sheet.rows.maxOfOrNull { it.size } ?: 0) + 1).coerceAtMost(40)
    val rowsCount = maxOf(20, sheet.rows.size + 5)
    val hScroll = rememberScrollState()
    val border = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        if (sheets.size > 1) Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sheets.forEachIndexed { i, s -> FilterChip(selected = i == index, onClick = { onSheet(i) }, label = { Text(s.name) }) }
        }
        GlassCard(
            Modifier
                .fillMaxSize()
                .padding(10.dp), shape = RoundedCornerShape(18.dp)
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.horizontalScroll(hScroll)) {
                        HeaderCell("", 44)
                        for (c in 0 until cols) HeaderCell(colName(c), 112)
                    }
                }
                items(rowsCount) { r ->
                    Row(Modifier.horizontalScroll(hScroll)) {
                        HeaderCell("${r + 1}", 44)
                        for (c in 0 until cols) {
                            val value = sheet.rows.getOrNull(r)?.getOrNull(c) ?: ""
                            if (version < 0) return@items
                            BasicTextField(
                                value = value,
                                onValueChange = { v ->
                                    while (sheet.rows.size <= r) sheet.rows += ArrayList<String>()
                                    val row = sheet.rows[r]
                                    while (row.size <= c) row += ""
                                    row[c] = v
                                    version++
                                    onEdit()
                                },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = if (value.toDoubleOrNull() != null) TextAlign.End else TextAlign.Start),
                                modifier = Modifier
                                    .width(112.dp)
                                    .height(40.dp)
                                    .border(0.5.dp, border)
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Int) {
    Box(
        Modifier
            .width(width.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center
    ) { Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
}

// ---------------------------------------------------------------- PowerPoint

@Composable
private fun SlidesView(slides: List<Slide>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(slides) { i, s ->
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    s.lines.take(8).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
                }
            }
        }
    }
}

/** Slaydlarni 16:9 PDF sahifalariga chiqaradi (matn ko'rinishida). */
private fun slidesToPdf(slides: List<Slide>, out: File) {
    val doc = PdfDocument()
    try {
        val w = 960; val h = 540
        slides.forEachIndexed { i, s ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            val c = page.canvas
            c.drawColor(AColor.WHITE)
            val accent = android.graphics.Paint().apply { color = AColor.rgb(79, 70, 229) }
            c.drawRect(0f, 0f, w.toFloat(), 8f, accent)
            val tp = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; isFakeBoldText = true; color = AColor.rgb(20, 20, 30) }
            val title = StaticLayout.Builder.obtain(s.title, 0, s.title.length, tp, w - 96).build()
            c.save(); c.translate(48f, 48f); title.draw(c); c.restore()
            val bp = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = AColor.rgb(50, 50, 64) }
            val body = s.lines.joinToString("\n") { "•  $it" }
            val bl = StaticLayout.Builder.obtain(body, 0, body.length, bp, w - 120).setLineSpacing(6f, 1f)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(14).build()
            c.save(); c.translate(60f, 64f + title.height + 18f); bl.draw(c); c.restore()
            doc.finishPage(page)
        }
        if (slides.isEmpty()) { doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())) }
        out.outputStream().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
}

