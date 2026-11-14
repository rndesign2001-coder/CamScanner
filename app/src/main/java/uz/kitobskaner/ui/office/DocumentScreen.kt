package uz.kitobskaner.ui.office

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddToPhotos
import androidx.compose.material.icons.rounded.Approval
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.office.HtmlRenderer
import uz.kitobskaner.office.OfficeEditor
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import java.io.File
import kotlin.coroutines.resume

enum class DocKind { PDF, DOCX, XLSX, PPTX, CSV, TXT, LEGACY, UNKNOWN }

fun kindOf(name: String, mime: String?): DocKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext == "pdf" || mime == "application/pdf" -> DocKind.PDF
        ext == "docx" || mime?.contains("wordprocessingml") == true -> DocKind.DOCX
        ext == "xlsx" || mime?.contains("spreadsheetml") == true -> DocKind.XLSX
        ext == "pptx" || mime?.contains("presentationml") == true -> DocKind.PPTX
        ext == "csv" || mime == "text/csv" || mime == "text/comma-separated-values" -> DocKind.CSV
        ext in setOf("doc", "xls", "ppt", "rtf", "odt", "ods", "odp", "pps") || mime == "application/msword" ||
            mime == "application/vnd.ms-excel" || mime == "application/vnd.ms-powerpoint" -> DocKind.LEGACY
        ext in setOf("txt", "md", "log", "json", "xml", "html", "htm", "ini", "kt", "java", "py") || mime?.startsWith("text/") == true -> DocKind.TXT
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

/** evaluateJavascript natijasini (JSON qatori) oddiy matnga aylantiradi. */
private fun jsString(raw: String?): String? = try {
    (Json.parseToJsonElement(raw ?: "null") as? JsonPrimitive)?.contentOrNull
} catch (_: Exception) {
    null
}

private suspend fun WebController.call(js: String): String? = suspendCancellableCoroutine { c ->
    eval(js) { r -> if (c.isActive) c.resume(r) }
}

/**
 * Hujjat ko'ruvchi va muharriri (WPS/Office uslubida):
 * - Word: sahifalarga bo'lingan "kompyuter" ko'rinishi yoki ekranga moslangan "mobil" ko'rinish,
 *   matnni joyida tahrirlash va formatlash;
 * - PowerPoint: slaydlar, namoyish, matn tahriri, shakllarni surish/o'lcham, matn qutisi, slayd nusxasi/o'chirish;
 * - Excel: kataklarni tahrirlash, mobil ko'rinishda kartochkalar.
 * Saqlashda o'zgarishlar asl faylning o'ziga yoziladi (yoki yangi nusxa) — format to'liq saqlanadi.
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
    val office = kind == DocKind.DOCX || kind == DocKind.XLSX || kind == DocKind.PPTX
    val kindStr = when (kind) { DocKind.DOCX -> "docx"; DocKind.XLSX -> "xlsx"; DocKind.PPTX -> "pptx"; else -> "" }
    val base = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.', "txt").lowercase()
    var loading by remember { mutableStateOf(kind != DocKind.PDF) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var moveMode by remember { mutableStateOf(false) }
    var mobileView by rememberSaveable { mutableStateOf(false) }
    // joriy fayl baytlari (slayd amallari va saqlashlar shu yerda to'planadi)
    var work by remember { mutableStateOf<ByteArray?>(null) }
    var unsaved by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    var htmlFile by remember { mutableStateOf<File?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf<ByteArray?>(null) }
    var leave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var afterSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSlide by remember { mutableIntStateOf(-1) }
    val web = remember { WebController() }
    val errOpen = stringResource(R.string.err_open_doc)
    val errEnc = stringResource(R.string.err_encrypted)
    val savedMsg = stringResource(R.string.saved_ok)
    val savedOriginal = stringResource(R.string.saved_original)
    val savedFallback = stringResource(R.string.saved_copy_fallback)
    val lastSlideMsg = stringResource(R.string.last_slide)
    val newText = stringResource(R.string.new_text)

    suspend fun render(bytes: ByteArray) {
        val html = withContext(Dispatchers.IO) {
            val r = HtmlRenderer(::encodeImage)
            when (kind) {
                DocKind.DOCX -> r.docx(bytes.inputStream(), name)
                DocKind.XLSX -> r.xlsx(bytes.inputStream(), name)
                DocKind.PPTX -> r.pptx(bytes.inputStream(), name)
                DocKind.CSV -> r.csv(bytes.toString(Charsets.UTF_8), name)
                else -> null
            }
        } ?: return
        htmlFile = withContext(Dispatchers.IO) {
            File(context.cacheDir, "view/doc_${System.currentTimeMillis()}.html").also { f ->
                f.parentFile?.apply { mkdirs(); listFiles()?.forEach { old -> if (old != f) old.delete() } }
                f.writeText(html)
            }
        }
    }

    suspend fun readOriginal(): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("open")
    }

    LaunchedEffect(uri) {
        if (kind == DocKind.PDF) return@LaunchedEffect
        if (kind == DocKind.LEGACY) { loading = false; return@LaunchedEffect }
        try {
            val bytes = readOriginal()
            if (office) work = bytes
            if (kind == DocKind.TXT || kind == DocKind.CSV) text = bytes.toString(Charsets.UTF_8)
            render(bytes)
        } catch (e: HtmlRenderer.EncryptedException) {
            error = errEnc
        } catch (e: Throwable) {
            error = errOpen
        }
        loading = false
    }

    // sahifa qayta yuklanganda holatni tiklash
    web.onLoaded = {
        if (mobileView) web.eval("QO.view('mobile')")
        if (editing) web.eval("QO.edit(true)")
        if (moveMode) web.eval("QO.layout(true)")
        if (pendingSlide >= 0) {
            web.eval("setTimeout(function(){if(window.go)go($pendingSlide)},150)")
            pendingSlide = -1
        }
    }

    /** Ekrandagi o'zgarishlar qo'llangan fayl baytlari. */
    suspend fun pending(): ByteArray? {
        val w = work ?: return null
        val json = jsString(web.call("QO.collect()")) ?: return w
        if (!OfficeEditor.hasChanges(json)) return w
        return withContext(Dispatchers.Default) { OfficeEditor.apply(w, kindStr, json) }
    }

    suspend fun hasPending(): Boolean = unsaved || web.call("QO.changed()") == "true"

    fun startSave() {
        scope.launch {
            busy = true
            try {
                askSave = pending()
            } catch (e: Throwable) {
                toast(context, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun doSave(bytes: ByteArray, original: Boolean) {
        val after = afterSave
        afterSave = null
        scope.launch {
            busy = true
            try {
                val wrote = original && withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
                    } catch (_: Exception) {
                        false
                    }
                }
                val msg = if (wrote) savedOriginal else {
                    val f = withContext(Dispatchers.IO) { app.exporter.uniqueFile(app.exporter.safeName(base), ext).also { it.writeBytes(bytes) } }
                    app.exporter.refreshFiles()
                    if (original) savedFallback else "$savedMsg: ${f.name}"
                }
                work = bytes
                unsaved = false
                toast(context, msg)
                if (after != null) after() else {
                    pendingSlide = if (kind == DocKind.PPTX) (web.call("QO.cur()")?.toIntOrNull() ?: -1) else -1
                    render(bytes) // identifikatorlar yangilanadi
                }
            } catch (e: Throwable) {
                toast(context, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun slideOp(dup: Boolean) {
        scope.launch {
            busy = true
            try {
                val before = work ?: return@launch
                val b = pending() ?: return@launch
                val cur = web.call("QO.cur()")?.toIntOrNull() ?: 0
                val nb = withContext(Dispatchers.Default) { if (dup) OfficeEditor.duplicateSlide(b, cur) else OfficeEditor.deleteSlide(b, cur) }
                if (!dup && nb === b) toast(context, lastSlideMsg)
                if (nb !== before) unsaved = true
                work = nb
                pendingSlide = if (dup) cur + 1 else maxOf(0, cur - 1)
                render(nb)
            } catch (e: Throwable) {
                toast(context, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun discardAnd(action: () -> Unit) {
        scope.launch {
            busy = true
            try {
                val b = readOriginal()
                work = b
                unsaved = false
                action()
                render(b)
            } catch (e: Throwable) {
                action()
            } finally {
                busy = false
            }
        }
    }

    fun exitEdit() {
        scope.launch {
            val stop = { editing = false; moveMode = false; web.eval("QO.edit(false)"); Unit }
            if (hasPending()) leave = stop else stop()
        }
    }

    fun goBack() {
        if (!office || (!editing && !unsaved)) { onBack(); return }
        scope.launch { if (hasPending()) leave = onBack else onBack() }
    }

    BackHandler(enabled = office && (editing || unsaved)) { if (editing && !unsaved) exitEdit() else goBack() }
    BackHandler(enabled = kind == DocKind.TXT && dirty) { leave = onBack }

    fun saveTxt() {
        scope.launch {
            busy = true
            try {
                val f = withContext(Dispatchers.IO) { app.exporter.uniqueFile(app.exporter.safeName(base), ext).also { it.writeText(text) } }
                app.exporter.refreshFiles()
                dirty = false
                toast(context, savedMsg)
                onOpenFile(f)
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (editing) IconButton(onClick = { exitEdit() }) { Icon(Icons.Rounded.Close, null) }
                    else IconButton(onClick = { goBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
                },
                title = {
                    Text(
                        if (editing) stringResource(R.string.edit) else name, maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    when {
                        kind == DocKind.PDF -> {
                            IconButton(onClick = { onSign(uri) }) { Icon(Icons.Rounded.Approval, stringResource(R.string.sign_title)) }
                            IconButton(onClick = {
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
                        editing -> {
                            IconButton(onClick = { startSave() }) {
                                Icon(Icons.Rounded.Save, stringResource(R.string.save), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        office && htmlFile != null -> {
                            IconButton(onClick = {
                                mobileView = !mobileView
                                web.eval("QO.view('${if (mobileView) "mobile" else "desktop"}')")
                            }) {
                                Icon(
                                    if (mobileView) Icons.Rounded.DesktopWindows else Icons.Rounded.PhoneAndroid,
                                    stringResource(if (mobileView) R.string.view_desktop else R.string.view_mobile),
                                )
                            }
                            IconButton(onClick = { searching = !searching; if (!searching) web.clearFind() }) { Icon(Icons.Rounded.Search, null) }
                            IconButton(onClick = {
                                searching = false
                                web.clearFind()
                                if (kindStr != "docx" && mobileView) mobileView = false
                                editing = true
                                web.eval("QO.edit(true)")
                            }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                        }
                        kind == DocKind.TXT -> IconButton(onClick = { saveTxt() }, enabled = dirty) { Icon(Icons.Rounded.Save, null) }
                        kind == DocKind.CSV && htmlFile != null ->
                            IconButton(onClick = { searching = !searching; if (!searching) web.clearFind() }) { Icon(Icons.Rounded.Search, null) }
                    }
                    if (kind != DocKind.PDF && kind != DocKind.UNKNOWN && !editing) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (htmlFile != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.print_pdf)) },
                                leadingIcon = { Icon(Icons.Rounded.Print, null) },
                                onClick = { menu = false; web.print(context, base) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(Icons.Rounded.IosShare, null) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        val bytes = work
                                        val f = withContext(Dispatchers.IO) {
                                            File(context.cacheDir, "share/$name").also { out ->
                                                out.parentFile?.mkdirs()
                                                if (bytes != null) out.writeBytes(bytes)
                                                else context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                                            }
                                        }
                                        uz.kitobskaner.ui.components.shareFile(context, f, context.contentResolver.getType(uri) ?: "*/*")
                                    }
                                },
                            )
                        }
                    }
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
                error != null -> Text(error!!, Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center)
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                kind == DocKind.PDF -> PdfViewer(uri)
                kind == DocKind.LEGACY -> LegacyNotice(name)
                htmlFile != null -> Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    if (searching) SearchBar(query, onQuery = { query = it; web.find(it) }, onPrev = { web.next(false) }, onNext = { web.next(true) })
                    OfficeWebView(htmlFile!!, web, Modifier.weight(1f).fillMaxWidth())
                    if (editing) EditToolbar(
                        kind = kind,
                        moveMode = moveMode,
                        onJs = { web.eval(it) },
                        onMove = { moveMode = !moveMode; web.eval("QO.layout($moveMode)") },
                        onAddBox = { web.eval("QO.addBox(${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(newText))})") },
                        onDupSlide = { slideOp(true) },
                        onDelSlide = { slideOp(false) },
                    )
                }
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

    askSave?.let { bytes ->
        AlertDialog(
            onDismissRequest = { askSave = null; afterSave = null },
            title = { Text(stringResource(R.string.save_where)) },
            text = {
                Column {
                    TextButton(onClick = { askSave = null; doSave(bytes, true) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.save_original), Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { askSave = null; doSave(bytes, false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.save_copy), Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askSave = null; afterSave = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    leave?.let { action ->
        AlertDialog(
            onDismissRequest = { leave = null },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    leave = null
                    if (kind == DocKind.TXT) { saveTxt(); return@TextButton }
                    afterSave = action
                    startSave()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { leave = null }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        leave = null
                        if (kind == DocKind.TXT) { dirty = false; action(); return@TextButton }
                        discardAnd(action)
                    }) { Text(stringResource(R.string.dont_save), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}

// ---------------------------------------------------------------- tahrirlash paneli

private val textColors = listOf(
    "#000000", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00ACC1", "#1E6BFF", "#8E24AA", "#6D4C41", "#757575",
)
private val markColors = listOf("transparent", "#FFF176", "#A5D6A7", "#81D4FA", "#F48FB1", "#FFCC80", "#E0E0E0")

@Composable
private fun EditToolbar(
    kind: DocKind,
    moveMode: Boolean,
    onJs: (String) -> Unit,
    onMove: () -> Unit,
    onAddBox: () -> Unit,
    onDupSlide: () -> Unit,
    onDelSlide: () -> Unit,
) {
    var colorMenu by remember { mutableStateOf(false) }
    var markMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 6.dp, shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            val hint = when {
                kind == DocKind.XLSX -> stringResource(R.string.xlsx_hint)
                moveMode -> stringResource(R.string.move_mode)
                else -> null
            }
            if (hint != null) Text(
                hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tool(Icons.AutoMirrored.Rounded.Undo) { onJs("QO.cmd('undo')") }
                Tool(Icons.AutoMirrored.Rounded.Redo) { onJs("QO.cmd('redo')") }
                if (kind != DocKind.XLSX) {
                    Sep()
                    Tool(Icons.Rounded.FormatBold, enabled = !moveMode) { onJs("QO.cmd('bold')") }
                    Tool(Icons.Rounded.FormatItalic, enabled = !moveMode) { onJs("QO.cmd('italic')") }
                    Tool(Icons.Rounded.FormatUnderlined, enabled = !moveMode) { onJs("QO.cmd('underline')") }
                    Tool(Icons.Rounded.FormatStrikethrough, enabled = !moveMode) { onJs("QO.cmd('strikeThrough')") }
                    Tool(Icons.Rounded.TextDecrease, enabled = !moveMode) { onJs("QO.size(-2)") }
                    Tool(Icons.Rounded.TextIncrease, enabled = !moveMode) { onJs("QO.size(2)") }
                    Box {
                        Tool(Icons.Rounded.FormatColorText, enabled = !moveMode) { colorMenu = true }
                        DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                            Swatches(textColors, stringResource(R.string.text_color)) { c -> colorMenu = false; onJs("QO.cmd('foreColor','$c')") }
                        }
                    }
                    Box {
                        Tool(Icons.Rounded.FormatColorFill, enabled = !moveMode) { markMenu = true }
                        DropdownMenu(expanded = markMenu, onDismissRequest = { markMenu = false }) {
                            Swatches(markColors, stringResource(R.string.highlight_color)) { c -> markMenu = false; onJs("QO.cmd('hiliteColor','$c')") }
                        }
                    }
                    Sep()
                    Tool(Icons.AutoMirrored.Rounded.FormatAlignLeft, enabled = !moveMode) { onJs("QO.cmd('justifyLeft')") }
                    Tool(Icons.Rounded.FormatAlignCenter, enabled = !moveMode) { onJs("QO.cmd('justifyCenter')") }
                    Tool(Icons.AutoMirrored.Rounded.FormatAlignRight, enabled = !moveMode) { onJs("QO.cmd('justifyRight')") }
                    Tool(Icons.Rounded.FormatAlignJustify, enabled = !moveMode) { onJs("QO.cmd('justifyFull')") }
                }
                if (kind == DocKind.PPTX) {
                    Sep()
                    Tool(Icons.Rounded.OpenWith, selected = moveMode, label = stringResource(R.string.move_mode)) { onMove() }
                    Tool(Icons.Rounded.Delete, enabled = moveMode, label = stringResource(R.string.delete_shape)) { onJs("QO.del()") }
                    Tool(Icons.Rounded.TextFields, label = stringResource(R.string.add_textbox)) { onAddBox() }
                    Tool(Icons.Rounded.AddToPhotos, label = stringResource(R.string.dup_slide)) { onDupSlide() }
                    Tool(Icons.Rounded.DeleteSweep, label = stringResource(R.string.del_slide)) { onDelSlide() }
                }
            }
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, enabled: Boolean = true, selected: Boolean = false, label: String? = null, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = if (selected) IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) else IconButtonDefaults.iconButtonColors(),
    ) { Icon(icon, label) }
}

@Composable
private fun Sep() {
    VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
}

@Composable
private fun Swatches(colors: List<String>, title: String, onPick: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.take(6).forEach { c -> Swatch(c, onPick) }
    }
    if (colors.size > 6) Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.drop(6).forEach { c -> Swatch(c, onPick) }
    }
}

@Composable
private fun Swatch(c: String, onPick: (String) -> Unit) {
    val color = if (c == "transparent") Color.White else Color(android.graphics.Color.parseColor(c))
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable { onPick(c) },
        contentAlignment = Alignment.Center,
    ) {
        if (c == "transparent") Text("∅", color = Color.Gray)
    }
}

// ---------------------------------------------------------------- PDF

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
    var offsetX by remember { mutableFloatStateOf(0f) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            // ikki barmoq — kattalashtirish/surish; bir barmoq — odatdagidek varaqlash
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * z).coerceIn(1f, 5f)
                            val maxX = size.width * (scale - 1f) / 2f
                            offsetX = if (scale <= 1f) 0f else (offsetX + pan.x).coerceIn(-maxX, maxX)
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f) {
                            // kattalashtirilganda bir barmoq bilan gorizontal surish
                            val ch = event.changes.firstOrNull()
                            if (ch != null && ch.pressed) {
                                val dx = ch.position.x - ch.previousPosition.x
                                val maxX = size.width * (scale - 1f) / 2f
                                offsetX = (offsetX + dx).coerceIn(-maxX, maxX)
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX },
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

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            placeholder = { Text(stringResource(R.string.search_in_doc)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPrev) { Icon(Icons.Rounded.KeyboardArrowUp, null) }
        IconButton(onClick = onNext) { Icon(Icons.Rounded.KeyboardArrowDown, null) }
    }
}

@Composable
private fun LegacyNotice(name: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        uz.kitobskaner.ui.components.Icon3D(R.drawable.ic3d_warning, 88.dp)
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.legacy_format), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}
