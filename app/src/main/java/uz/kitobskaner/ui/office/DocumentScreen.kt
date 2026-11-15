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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.BorderAll
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import uz.kitobskaner.office.Fmt
import uz.kitobskaner.office.FormatSniffer
import uz.kitobskaner.office.HtmlRenderer
import uz.kitobskaner.office.OfficeEditor
import uz.kitobskaner.office.Recovery
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import java.io.File
import kotlin.coroutines.resume

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

private class TooLargeException : Exception("too large")

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
    startEditing: Boolean = false,
) {
    val context = LocalContext.current
    val app = App.instance
    val scope = rememberCoroutineScope()
    val name = remember(uri) { queryName(context, uri) }
    var kind by remember(uri) { mutableStateOf(FormatSniffer.byExtension(name, context.contentResolver.getType(uri))) }
    val office = kind.editable
    // sahifa/ko'rinish rejimlari bor (QO dvigateli bilan) formatlar
    val paged = kind in setOf(Fmt.DOCX, Fmt.XLSX, Fmt.PPTX, Fmt.DOC, Fmt.XLS, Fmt.PPT, Fmt.ODT, Fmt.ODS, Fmt.ODP, Fmt.RTF)
    val kindStr = when (kind) { Fmt.DOCX -> "docx"; Fmt.XLSX -> "xlsx"; Fmt.PPTX -> "pptx"; else -> "" }
    val base = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.', "txt").lowercase()
    var loading by remember { mutableStateOf(kind != Fmt.PDF) }
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
    // yangi qo'yilgan rasmlar (kalit → JPEG baytlari) — saqlashda faylga qo'shiladi
    val newImages = remember { HashMap<String, ByteArray>() }
    var imgSeq by remember { mutableIntStateOf(0) }
    var domDirty by remember { mutableStateOf(false) }
    var origHash by remember { mutableStateOf<String?>(null) }
    var recoverOffer by remember { mutableStateOf<Pair<ByteArray, Long>?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var statsJson by remember { mutableStateOf<String?>(null) }
    var rangeMode by remember { mutableStateOf(false) }
    var pendingSheet by remember { mutableIntStateOf(-1) }
    var layoutNames by remember { mutableStateOf<List<String>>(emptyList()) }
    val web = remember { WebController() }
    val errOpen = stringResource(R.string.err_open_doc)
    val errEnc = stringResource(R.string.err_encrypted)
    val errLarge = stringResource(R.string.err_too_large)
    val savedMsg = stringResource(R.string.saved_ok)
    val savedOriginal = stringResource(R.string.saved_original)
    val savedFallback = stringResource(R.string.saved_copy_fallback)
    val lastSlideMsg = stringResource(R.string.last_slide)
    val newText = stringResource(R.string.new_text)
    val conflictMsg = stringResource(R.string.conflict_saved)
    val needCursorMsg = stringResource(R.string.need_cursor)
    val replacedFmt = stringResource(R.string.replaced_n)
    val needCellMsg = stringResource(R.string.need_cell)
    val lastSheetMsg = stringResource(R.string.last_sheet)
    val copiedFmt = stringResource(R.string.copied_n)
    val shownFmt = stringResource(R.string.shown_rows)

    suspend fun render(bytes: ByteArray) {
        val html = withContext(Dispatchers.Default) {
            HtmlRenderer(if (kind == Fmt.IMAGE) ::encodeImageLarge else ::encodeImage).render(kind, bytes, name)
        } ?: return
        htmlFile = withContext(Dispatchers.IO) {
            File(context.cacheDir, "view/doc_${System.currentTimeMillis()}.html").also { f ->
                f.parentFile?.apply { mkdirs(); listFiles()?.forEach { old -> if (old != f) old.delete() } }
                f.writeText(html)
            }
        }
    }

    suspend fun readOriginal(): ByteArray = withContext(Dispatchers.IO) {
        val size = uz.kitobskaner.pdf.PdfTools.size(context, uri)
        if (size > 300L * 1024 * 1024) throw TooLargeException()
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("open")
    }

    LaunchedEffect(uri) {
        if (kind == Fmt.PDF) return@LaunchedEffect
        try {
            // format fayl mazmunidan aniqlanadi (kengaytma noto'g'ri bo'lishi mumkin)
            val head = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { s -> ByteArray(8192).let { b -> val n = s.read(b); if (n > 0) b.copyOf(n) else ByteArray(0) } }
            } ?: throw IllegalStateException("open")
            if (FormatSniffer.detect(head, name, null) == Fmt.PDF) { kind = Fmt.PDF; loading = false; return@LaunchedEffect }
            val bytes = readOriginal()
            kind = withContext(Dispatchers.Default) { FormatSniffer.detect(bytes, name, context.contentResolver.getType(uri)) }
            if (kind == Fmt.PDF) { loading = false; return@LaunchedEffect }
            if (kind.editable) {
                work = bytes
                origHash = withContext(Dispatchers.Default) { Recovery.hash(bytes) }
                recoverOffer = withContext(Dispatchers.IO) { Recovery.load(context, uri) }
                if (startEditing && recoverOffer == null) editing = true
            }
            if (kind == Fmt.TXT || kind == Fmt.CSV) text = HtmlRenderer.decodeText(bytes)
            if (kind != Fmt.UNKNOWN && kind != Fmt.TXT) render(bytes)
        } catch (e: HtmlRenderer.EncryptedException) {
            error = errEnc
        } catch (e: TooLargeException) {
            error = errLarge
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
        if (pendingSheet >= 0) {
            web.eval("if(window.show)show($pendingSheet)")
            pendingSheet = -1
        }
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
        val imgs = HashMap(newImages)
        return withContext(Dispatchers.Default) { OfficeEditor.apply(w, kindStr, json, imgs) }
    }

    /** Ish nusxasini almashtiradi (qo'shilgan rasmlar endi faylning ichida). */
    fun setWork(b: ByteArray, dirtyNow: Boolean) {
        work = b
        newImages.clear()
        unsaved = dirtyNow
        if (dirtyNow) scope.launch(Dispatchers.IO) { try { Recovery.save(context, uri, b) } catch (_: Exception) {} }
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
                // boshqa ilova faylni o'zgartirganmi (ziddiyat) — unda ustiga yozmaymiz
                val conflict = original && withContext(Dispatchers.IO) {
                    try {
                        val now = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        now != null && origHash != null && Recovery.hash(now) != origHash
                    } catch (_: Exception) {
                        false
                    }
                }
                val wrote = original && !conflict && withContext(Dispatchers.IO) {
                    try {
                        // yoziladi, so'ng qayta o'qib tekshiriladi
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } != null &&
                            context.contentResolver.openInputStream(uri)?.use { Recovery.hash(it.readBytes()) } == Recovery.hash(bytes)
                    } catch (_: Exception) {
                        false
                    }
                }
                val msg = if (wrote) savedOriginal else {
                    val f = withContext(Dispatchers.IO) { app.exporter.uniqueFile(app.exporter.safeName(base), ext).also { it.writeBytes(bytes) } }
                    app.exporter.refreshFiles()
                    when {
                        conflict -> conflictMsg
                        original -> savedFallback
                        else -> "$savedMsg: ${f.name}"
                    }
                }
                if (wrote) origHash = withContext(Dispatchers.Default) { Recovery.hash(bytes) }
                setWork(bytes, false)
                domDirty = false
                withContext(Dispatchers.IO) { Recovery.clear(context, uri) }
                toast(context, msg)
                if (after != null) after() else {
                    pendingSlide = if (kind == Fmt.PPTX) (web.call("QO.cur()")?.toIntOrNull() ?: -1) else -1
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
                setWork(nb, unsaved || nb !== before)
                pendingSlide = if (dup) cur + 1 else maxOf(0, cur - 1)
                render(nb)
            } catch (e: Throwable) {
                toast(context, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun pageSetup(landscape: Boolean, margin: Double, numbers: Boolean, header: String) {
        scope.launch {
            busy = true
            try {
                val b = pending() ?: return@launch
                val nb = withContext(Dispatchers.Default) { OfficeEditor.pageSetup(b, landscape, margin, numbers, header) }
                setWork(nb, true)
                render(nb)
            } catch (e: Throwable) {
                toast(context, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    /** PowerPoint slayd amallari: yangi slayd, surish, maket, fon. */
    fun pptOp(op: String, arg: String?) {
        scope.launch {
            busy = true
            try {
                val b = pending() ?: return@launch
                val cur = web.call("QO.cur()")?.toIntOrNull() ?: 0
                var target = cur
                val nb = withContext(Dispatchers.Default) {
                    when (op) {
                        "new" -> { target = cur + 1; OfficeEditor.addSlide(b, cur, arg?.toIntOrNull() ?: 1) }
                        "up" -> { target = maxOf(0, cur - 1); OfficeEditor.moveSlide(b, cur, -1) }
                        "down" -> { target = cur + 1; OfficeEditor.moveSlide(b, cur, 1) }
                        "layout" -> OfficeEditor.setSlideLayout(b, cur, arg?.toIntOrNull() ?: 0)
                        "bg" -> OfficeEditor.setSlideBackground(b, cur, arg?.removePrefix("all:") ?: "#FFFFFF", arg?.startsWith("all:") == true)
                        else -> b
                    }
                }
                if (nb === b) return@launch
                setWork(nb, true)
                pendingSlide = target
                render(nb)
            } catch (e: Throwable) {
                toast(context, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    /** Excel tuzilma amali: joriy tanlov bo'yicha, so'ng qayta chiziladi. */
    fun xop(op: String, extra: String? = null) {
        scope.launch {
            busy = true
            try {
                val b = pending() ?: return@launch
                val info = jsString(web.call("XL.selInfo()")) ?: return@launch
                val o = Json.parseToJsonElement(info) as kotlinx.serialization.json.JsonObject
                fun n(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: -1
                val si = n("si").coerceAtLeast(0)
                var r0 = n("r0"); var c0 = n("c0"); var r1 = n("r1"); var c1 = n("c1")
                val needCell = op !in setOf("addSheet", "renameSheet", "delSheet", "freezeRow", "freezeCol", "unfreeze")
                if (needCell && r0 < 0) { toast(context, needCellMsg); return@launch }
                var realOp = op
                when (op) {
                    "freezeRow" -> { realOp = "freeze"; r0 = 1; c0 = 0 }
                    "freezeCol" -> { realOp = "freeze"; r0 = 0; c0 = 1 }
                    "unfreeze" -> { realOp = "freeze"; r0 = 0; c0 = 0 }
                    "sortAsc", "sortDesc" -> if (r1 <= r0) { r0 = 1; r1 = 1_048_575 }
                }
                if (op == "delSheet" && (web.call("document.querySelectorAll('.sheet').length")?.toIntOrNull() ?: 1) <= 1) { toast(context, lastSheetMsg); return@launch }
                val nb = withContext(Dispatchers.Default) { OfficeEditor.xlsxOp(b, realOp, si, r0, c0, r1, c1, extra) }
                pendingSheet = when (op) {
                    "addSheet" -> (web.call("document.querySelectorAll('.sheet').length")?.toIntOrNull() ?: 1)
                    "delSheet" -> 0
                    else -> si
                }
                setWork(nb, true)
                rangeMode = false
                render(nb)
            } catch (e: Throwable) {
                toast(context, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun insertImage(u: Uri) {
        scope.launch {
            val r = withContext(Dispatchers.Default) { prepareImage(context, u) } ?: return@launch
            val k = "${++imgSeq}"
            newImages[k] = r.bytes
            val src = "data:image/jpeg;base64," + android.util.Base64.encodeToString(r.bytes, android.util.Base64.NO_WRAP)
            val fn = if (kind == Fmt.PPTX) "QO.addPic" else "QO.insImg"
            web.eval("$fn('$k','$src',${r.wPt},${r.hPt})") { res -> if (res != "true") { newImages.remove(k); toast(context, needCursorMsg) } }
        }
    }

    fun discardAnd(action: () -> Unit) {
        scope.launch {
            busy = true
            try {
                val b = readOriginal()
                setWork(b, false)
                domDirty = false
                withContext(Dispatchers.IO) { Recovery.clear(context, uri) }
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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) insertImage(u) }

    // avtosaqlash: tahrir paytida holat kuzatiladi, ~20 soniyada bir ichki xotiraga yoziladi
    LaunchedEffect(editing) {
        var tick = 0
        while (editing) {
            kotlinx.coroutines.delay(3_000)
            domDirty = web.call("QO.changed()") == "true"
            if ((domDirty || unsaved) && ++tick % 7 == 0) {
                try {
                    val b = pending()
                    if (b != null) withContext(Dispatchers.IO) { Recovery.save(context, uri, b) }
                } catch (_: Exception) {
                }
            }
        }
    }
    // ilova fonga o'tganda ham saqlab qo'yamiz
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP && office && (editing || unsaved)) scope.launch {
                try {
                    if (hasPending()) pending()?.let { b -> withContext(Dispatchers.IO) { Recovery.save(context, uri, b) } }
                } catch (_: Exception) {
                }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    BackHandler(enabled = office && (editing || unsaved)) { if (editing && !unsaved) exitEdit() else goBack() }
    BackHandler(enabled = kind == Fmt.TXT && dirty) { leave = onBack }

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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(name, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        if (office && (editing || unsaved)) Text(
                            stringResource(if (unsaved || domDirty) R.string.status_unsaved else R.string.status_saved),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unsaved || domDirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    when {
                        kind == Fmt.PDF -> {
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
                        paged && htmlFile != null -> {
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
                            if (office) IconButton(onClick = {
                                searching = false
                                web.clearFind()
                                if (kindStr != "docx" && mobileView) mobileView = false
                                editing = true
                                web.eval("QO.edit(true)")
                            }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                        }
                        kind == Fmt.TXT -> IconButton(onClick = { saveTxt() }, enabled = dirty) { Icon(Icons.Rounded.Save, null) }
                        htmlFile != null ->
                            IconButton(onClick = { searching = !searching; if (!searching) web.clearFind() }) { Icon(Icons.Rounded.Search, null) }
                    }
                    if (kind != Fmt.PDF && kind != Fmt.UNKNOWN && !editing) Box {
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
                kind == Fmt.PDF -> PdfViewer(uri)
                kind == Fmt.UNKNOWN -> LegacyNotice(name)
                htmlFile != null -> Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    if (searching) SearchBar(query, onQuery = { query = it; web.find(it) }, onPrev = { web.next(false) }, onNext = { web.next(true) })
                    OfficeWebView(htmlFile!!, web, Modifier.weight(1f).fillMaxWidth(), scripts = kind != Fmt.HTML && kind != Fmt.EPUB)
                    if (editing && kind == Fmt.XLSX) XlsxToolbar(
                        rangeMode = rangeMode,
                        onRange = { rangeMode = !rangeMode; web.eval("XL.range($rangeMode)") },
                        onJs = { web.eval(it) },
                        onOp = { op, extra -> xop(op, extra) },
                        onDialog = { d ->
                            if (d == "copy") web.eval("XL.copy()") { r -> toast(context, String.format(copiedFmt, r?.toIntOrNull() ?: 0)) } else dialog = d
                        },
                    ) else if (editing) EditToolbar(
                        kind = kind,
                        moveMode = moveMode,
                        onJs = { web.eval(it) },
                        onMove = { moveMode = !moveMode; web.eval("QO.layout($moveMode)") },
                        onAddBox = { web.eval("QO.addBox(${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(newText))})") },
                        onDupSlide = { slideOp(true) },
                        onDelSlide = { slideOp(false) },
                        onImage = { imagePicker.launch("image/*") },
                        onDialog = { d ->
                            when (d) {
                                "count" -> web.eval("QO.stats()") { statsJson = jsString(it); dialog = d }
                                "layoutNew", "layoutChange" -> scope.launch {
                                    layoutNames = withContext(Dispatchers.Default) { work?.let { w -> OfficeEditor.pptxLayouts(w).map { it.name } } ?: emptyList() }
                                    dialog = d
                                }
                                else -> dialog = d
                            }
                        },
                        onSlide = { op, arg -> pptOp(op, arg) },
                    )
                }
                kind == Fmt.TXT -> OutlinedTextField(
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
                    if (kind == Fmt.TXT) { saveTxt(); return@TextButton }
                    afterSave = action
                    startSave()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { leave = null }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        leave = null
                        if (kind == Fmt.TXT) { dirty = false; action(); return@TextButton }
                        discardAnd(action)
                    }) { Text(stringResource(R.string.dont_save), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
    when (dialog) {
        "table" -> TableDialog(onDismiss = { dialog = null }) { r, c ->
            dialog = null
            web.eval("QO.insTable($r,$c)") { res -> if (res != "true") toast(context, needCursorMsg) }
        }
        "find" -> FindReplaceDialog(onDismiss = { dialog = null }) { f, r, mc ->
            web.eval("QO.replaceAll(${jsLit(f)},${jsLit(r)},$mc)") { res -> toast(context, String.format(replacedFmt, res?.toIntOrNull() ?: 0)) }
        }
        "count" -> StatsDialog(statsJson, onDismiss = { dialog = null })
        "layoutNew", "layoutChange" -> LayoutDialog(layoutNames, onDismiss = { dialog = null }) { i ->
            val d = dialog
            dialog = null
            pptOp(if (d == "layoutNew") "new" else "layout", i.toString())
        }
        "sheetAdd" -> TextInputDialog(stringResource(R.string.add_sheet), stringResource(R.string.sheet_name), onDismiss = { dialog = null }) { v ->
            dialog = null; xop("addSheet", v)
        }
        "sheetRename" -> TextInputDialog(stringResource(R.string.rename_sheet), stringResource(R.string.sheet_name), onDismiss = { dialog = null }) { v ->
            dialog = null; if (v.isNotEmpty()) xop("renameSheet", v)
        }
        "filter" -> TextInputDialog(stringResource(R.string.filter), stringResource(R.string.filter_hint), onDismiss = { dialog = null }) { v ->
            dialog = null
            scope.launch {
                val info = jsString(web.call("XL.selInfo()")) ?: return@launch
                val c = ((Json.parseToJsonElement(info) as kotlinx.serialization.json.JsonObject)["c0"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: -1
                if (c < 0) { toast(context, needCellMsg); return@launch }
                val n = web.call("XL.filter($c,${jsLit(v)})")?.toIntOrNull() ?: 0
                if (v.isNotEmpty()) toast(context, String.format(shownFmt, n))
            }
        }
        "page" -> PageSetupDialog(onDismiss = { dialog = null }) { land, margin, nums, header ->
            dialog = null
            pageSetup(land, margin, nums, header)
        }
    }

    recoverOffer?.let { (bytes, time) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.recover_title)) },
            text = {
                Text(stringResource(R.string.recover_msg, java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))))
            },
            confirmButton = {
                TextButton(onClick = {
                    recoverOffer = null
                    scope.launch {
                        setWork(bytes, true)
                        editing = true
                        render(bytes)
                    }
                }) { Text(stringResource(R.string.recover_yes)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    recoverOffer = null
                    scope.launch(Dispatchers.IO) { Recovery.clear(context, uri) }
                    if (startEditing) editing = true
                }) { Text(stringResource(R.string.recover_no)) }
            },
        )
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}

private fun jsLit(s: String) = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(s))

private class PreparedImage(val bytes: ByteArray, val wPt: Int, val hPt: Int)

/** Rasmni hujjatga qo'yish uchun tayyorlaydi: EXIF burilishi, 1600 px gacha, JPEG 85%. */
private fun prepareImage(context: android.content.Context, u: Uri): PreparedImage? = try {
    val raw = context.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: throw IllegalStateException()
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
    var sample = 1
    while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 1600) sample *= 2
    var bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalStateException()
    val rot = try {
        when (androidx.exifinterface.media.ExifInterface(raw.inputStream()).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
        }
    } catch (_: Exception) { 0f }
    if (rot != 0f) bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(rot) }, true)
    val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
    if (scale < 1f) bmp = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    val bo = java.io.ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bo)
    val wPt = minOf(420f, bmp.width * 0.75f)
    PreparedImage(bo.toByteArray(), wPt.toInt(), (wPt * bmp.height / bmp.width).toInt())
} catch (_: Exception) {
    null
}

// ---------------------------------------------------------------- tahrirlash paneli

private val textColors = listOf(
    "#000000", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00ACC1", "#1E6BFF", "#8E24AA", "#6D4C41", "#757575",
)
private val markColors = listOf("transparent", "#FFF176", "#A5D6A7", "#81D4FA", "#F48FB1", "#FFCC80", "#E0E0E0")
private val fontNames = listOf("Calibri", "Arial", "Times New Roman", "Georgia", "Verdana", "Tahoma", "Courier New")
private val fontSizes = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72)

@Composable
private fun EditToolbar(
    kind: Fmt,
    moveMode: Boolean,
    onJs: (String) -> Unit,
    onMove: () -> Unit,
    onAddBox: () -> Unit,
    onDupSlide: () -> Unit,
    onDelSlide: () -> Unit,
    onImage: () -> Unit,
    onDialog: (String) -> Unit,
    onSlide: (String, String?) -> Unit = { _, _ -> },
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<String?>(null) }
    var bgAll by remember { mutableStateOf(false) }
    val word = kind == Fmt.DOCX
    val ppt = kind == Fmt.PPTX
    val tabs = when (kind) {
        Fmt.DOCX -> listOf(R.string.tab_home, R.string.tab_insert, R.string.tab_layout, R.string.tab_review)
        Fmt.PPTX -> listOf(R.string.tab_home, R.string.tab_insert, R.string.tab_slide)
        else -> emptyList()
    }
    Surface(tonalElevation = 6.dp, shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            val hint = when {
                kind == Fmt.XLSX -> stringResource(R.string.xlsx_hint)
                moveMode -> stringResource(R.string.move_mode)
                else -> null
            }
            if (hint != null) Text(
                hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp),
            )
            if (tabs.isNotEmpty()) Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { i, t ->
                    val on = tab == i
                    Text(
                        stringResource(t),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { tab = i }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val t = if (tabs.isNotEmpty()) tab.coerceAtMost(tabs.size - 1) else 0
                if (t == 0) {
                    Tool(Icons.AutoMirrored.Rounded.Undo) { onJs("QO.cmd('undo')") }
                    Tool(Icons.AutoMirrored.Rounded.Redo) { onJs("QO.cmd('redo')") }
                }
                if (kind != Fmt.XLSX && t == 0) {
                    Sep()
                    if (word || kind == Fmt.PPTX) {
                        Box {
                            TextChip(stringResource(R.string.font_family), enabled = !moveMode) { menu = "font" }
                            DropdownMenu(expanded = menu == "font", onDismissRequest = { menu = null }) {
                                fontNames.forEach { f ->
                                    DropdownMenuItem(text = { Text(f) }, onClick = { menu = null; onJs("QO.font('$f')") })
                                }
                            }
                        }
                        Box {
                            TextChip(stringResource(R.string.font_size), enabled = !moveMode) { menu = "size" }
                            DropdownMenu(expanded = menu == "size", onDismissRequest = { menu = null }) {
                                fontSizes.forEach { z -> DropdownMenuItem(text = { Text("$z") }, onClick = { menu = null; onJs("QO.sizeTo($z)") }) }
                            }
                        }
                    }
                    Tool(Icons.Rounded.FormatBold, enabled = !moveMode) { onJs("QO.cmd('bold')") }
                    Tool(Icons.Rounded.FormatItalic, enabled = !moveMode) { onJs("QO.cmd('italic')") }
                    Tool(Icons.Rounded.FormatUnderlined, enabled = !moveMode) { onJs("QO.cmd('underline')") }
                    Tool(Icons.Rounded.FormatStrikethrough, enabled = !moveMode) { onJs("QO.cmd('strikeThrough')") }
                    Tool(Icons.Rounded.TextDecrease, enabled = !moveMode) { onJs("QO.size(-2)") }
                    Tool(Icons.Rounded.TextIncrease, enabled = !moveMode) { onJs("QO.size(2)") }
                    Box {
                        Tool(Icons.Rounded.FormatColorText, enabled = !moveMode) { menu = "color" }
                        DropdownMenu(expanded = menu == "color", onDismissRequest = { menu = null }) {
                            Swatches(textColors, stringResource(R.string.text_color)) { c -> menu = null; onJs("QO.cmd('foreColor','$c')") }
                        }
                    }
                    Box {
                        Tool(Icons.Rounded.FormatColorFill, enabled = !moveMode) { menu = "mark" }
                        DropdownMenu(expanded = menu == "mark", onDismissRequest = { menu = null }) {
                            Swatches(markColors, stringResource(R.string.highlight_color)) { c -> menu = null; onJs("QO.cmd('hiliteColor','$c')") }
                        }
                    }
                    Sep()
                    Tool(Icons.AutoMirrored.Rounded.FormatAlignLeft, enabled = !moveMode) { onJs("QO.cmd('justifyLeft')") }
                    Tool(Icons.Rounded.FormatAlignCenter, enabled = !moveMode) { onJs("QO.cmd('justifyCenter')") }
                    Tool(Icons.AutoMirrored.Rounded.FormatAlignRight, enabled = !moveMode) { onJs("QO.cmd('justifyRight')") }
                    Tool(Icons.Rounded.FormatAlignJustify, enabled = !moveMode) { onJs("QO.cmd('justifyFull')") }
                    if (word) {
                        Sep()
                        Tool(Icons.AutoMirrored.Rounded.FormatListBulleted, label = stringResource(R.string.bullets)) { onJs("QO.list('bullet')") }
                        Tool(Icons.Rounded.FormatListNumbered, label = stringResource(R.string.numbering)) { onJs("QO.list('number')") }
                        Tool(Icons.AutoMirrored.Rounded.FormatIndentDecrease, label = stringResource(R.string.indent_less)) { onJs("QO.indent(-1)") }
                        Tool(Icons.AutoMirrored.Rounded.FormatIndentIncrease, label = stringResource(R.string.indent_more)) { onJs("QO.indent(1)") }
                    }
                }
                if (word && t == 1) {
                    LabeledTool(Icons.Rounded.TableChart, stringResource(R.string.insert_table)) { onDialog("table") }
                    LabeledTool(Icons.Rounded.Image, stringResource(R.string.insert_image)) { onImage() }
                    LabeledTool(Icons.Rounded.HorizontalRule, stringResource(R.string.page_break)) { onJs("QO.insPB()") }
                    LabeledTool(Icons.Rounded.NoteAdd, stringResource(R.string.add_page)) { onJs("QO.addPage()") }
                }
                if (word && t == 2) {
                    Box {
                        LabeledTool(Icons.Rounded.FormatLineSpacing, stringResource(R.string.line_spacing)) { menu = "ls" }
                        DropdownMenu(expanded = menu == "ls", onDismissRequest = { menu = null }) {
                            listOf(1.0, 1.15, 1.5, 2.0, 2.5).forEach { x -> DropdownMenuItem(text = { Text("$x") }, onClick = { menu = null; onJs("QO.spacing($x)") }) }
                        }
                    }
                    Box {
                        LabeledTool(Icons.Rounded.VerticalAlignBottom, stringResource(R.string.para_after)) { menu = "sa" }
                        DropdownMenu(expanded = menu == "sa", onDismissRequest = { menu = null }) {
                            listOf(0, 6, 12, 18, 24).forEach { x -> DropdownMenuItem(text = { Text("$x pt") }, onClick = { menu = null; onJs("QO.after($x)") }) }
                        }
                    }
                    LabeledTool(Icons.Rounded.Description, stringResource(R.string.page_setup)) { onDialog("page") }
                }
                if (word && t == 3) {
                    LabeledTool(Icons.Rounded.FindReplace, stringResource(R.string.find_replace)) { onDialog("find") }
                    LabeledTool(Icons.Rounded.Tag, stringResource(R.string.word_count)) { onDialog("count") }
                }
                if (ppt && t == 0) {
                    Sep()
                    Tool(Icons.Rounded.OpenWith, selected = moveMode, label = stringResource(R.string.move_mode)) { onMove() }
                    Tool(Icons.Rounded.Delete, enabled = moveMode, label = stringResource(R.string.delete_shape)) { onJs("QO.del()") }
                }
                if (ppt && t == 1) {
                    LabeledTool(Icons.Rounded.TextFields, stringResource(R.string.add_textbox)) { onAddBox() }
                    LabeledTool(Icons.Rounded.Image, stringResource(R.string.insert_image)) { onImage() }
                    Box {
                        LabeledTool(Icons.Rounded.Category, stringResource(R.string.insert_shape)) { menu = "shape" }
                        DropdownMenu(expanded = menu == "shape", onDismissRequest = { menu = null }) {
                            listOf("rect" to R.string.shape_rect, "roundRect" to R.string.shape_round, "ellipse" to R.string.shape_ellipse,
                                "triangle" to R.string.shape_triangle, "rightArrow" to R.string.shape_arrow, "star5" to R.string.shape_star).forEach { (k, l) ->
                                DropdownMenuItem(text = { Text(stringResource(l)) }, onClick = { menu = null; onJs("QO.addShape('$k','#2563EB')") })
                            }
                        }
                    }
                    LabeledTool(Icons.Rounded.OpenWith, stringResource(R.string.move_mode)) { onMove() }
                }
                if (ppt && t == 2) {
                    LabeledTool(Icons.Rounded.Add, stringResource(R.string.new_slide)) { onDialog("layoutNew") }
                    LabeledTool(Icons.Rounded.AddToPhotos, stringResource(R.string.dup_slide)) { onDupSlide() }
                    LabeledTool(Icons.Rounded.DeleteSweep, stringResource(R.string.del_slide)) { onDelSlide() }
                    LabeledTool(Icons.Rounded.ArrowUpward, stringResource(R.string.move_up)) { onSlide("up", null) }
                    LabeledTool(Icons.Rounded.ArrowDownward, stringResource(R.string.move_down)) { onSlide("down", null) }
                    LabeledTool(Icons.Rounded.Dashboard, stringResource(R.string.change_layout)) { onDialog("layoutChange") }
                    Box {
                        LabeledTool(Icons.Rounded.FormatColorFill, stringResource(R.string.slide_bg)) { menu = "bg" }
                        DropdownMenu(expanded = menu == "bg", onDismissRequest = { menu = null }) {
                            Swatches(listOf("#FFFFFF", "#F7F8FA", "#17191F", "#2563EB", "#16A36A", "#E5484D", "#F59E0B", "#7C3AED", "#0EA5E9", "#FDE68A", "#E2E8F0"), stringResource(R.string.slide_bg)) { c ->
                                menu = null; onSlide("bg", (if (bgAll) "all:" else "") + c)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { bgAll = !bgAll }.padding(horizontal = 8.dp)) {
                                androidx.compose.material3.Checkbox(bgAll, { bgAll = it })
                                Text(stringResource(R.string.bg_all))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextChip(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        "$text ▾",
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun LabeledTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun TableDialog(onDismiss: () -> Unit, onOk: (Int, Int) -> Unit) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insert_table)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper(stringResource(R.string.table_rows), rows, 1..50) { rows = it }
                Stepper(stringResource(R.string.table_cols), cols, 1..10) { cols = it }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(rows, cols) }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }) { Icon(Icons.Rounded.Remove, null) }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }) { Icon(Icons.Rounded.Add, null) }
    }
}

@Composable
private fun FindReplaceDialog(onDismiss: () -> Unit, onReplace: (String, String, Boolean) -> Unit) {
    var f by rememberSaveable { mutableStateOf("") }
    var r by rememberSaveable { mutableStateOf("") }
    var mc by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.find_replace)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(f, { f = it }, label = { Text(stringResource(R.string.find_what)) }, singleLine = true)
                OutlinedTextField(r, { r = it }, label = { Text(stringResource(R.string.replace_with)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mc = !mc }) {
                    androidx.compose.material3.Checkbox(mc, { mc = it })
                    Text(stringResource(R.string.match_case))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onReplace(f, r, mc) }, enabled = f.isNotEmpty()) { Text(stringResource(R.string.replace_all)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun StatsDialog(json: String?, onDismiss: () -> Unit) {
    val o = try { Json.parseToJsonElement(json ?: "{}") as? kotlinx.serialization.json.JsonObject } catch (_: Exception) { null }
    fun v(k: String) = (o?.get(k) as? JsonPrimitive)?.contentOrNull ?: "0"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(R.string.wc_words to "words", R.string.wc_chars to "chars", R.string.wc_nospace to "nospace", R.string.wc_paras to "paras").forEach { (l, k) ->
                    Row { Text(stringResource(l), Modifier.weight(1f)); Text(v(k), style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun PageSetupDialog(onDismiss: () -> Unit, onOk: (Boolean, Double, Boolean, String) -> Unit) {
    var land by rememberSaveable { mutableStateOf(false) }
    var margin by rememberSaveable { mutableIntStateOf(1) }
    var nums by rememberSaveable { mutableStateOf(false) }
    var header by rememberSaveable { mutableStateOf("") }
    val margins = listOf(36.0 to R.string.margin_narrow, 54.0 to R.string.margin_normal, 72.0 to R.string.margin_wide)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_setup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(!land, { land = false }, label = { Text(stringResource(R.string.portrait)) })
                    androidx.compose.material3.FilterChip(land, { land = true }, label = { Text(stringResource(R.string.landscape)) })
                }
                Text(stringResource(R.string.margins), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    margins.forEachIndexed { i, (_, l) -> androidx.compose.material3.FilterChip(margin == i, { margin = i }, label = { Text(stringResource(l)) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nums = !nums }) {
                    androidx.compose.material3.Checkbox(nums, { nums = it })
                    Text(stringResource(R.string.page_numbers))
                }
                OutlinedTextField(header, { header = it }, label = { Text(stringResource(R.string.header_text)) }, singleLine = true)
                Text(stringResource(R.string.page_setup_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(land, margins[margin].first, nums, header) }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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

// ---------------------------------------------------------------- Excel paneli

@Composable
private fun XlsxToolbar(
    rangeMode: Boolean,
    onRange: () -> Unit,
    onJs: (String) -> Unit,
    onOp: (String, String?) -> Unit,
    onDialog: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<String?>(null) }
    Surface(tonalElevation = 6.dp, shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.xlsx_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp), maxLines = 2,
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(R.string.tab_home, R.string.tab_formulas, R.string.tab_rows, R.string.tab_sheet, R.string.tab_data).forEachIndexed { i, t ->
                    val on = tab == i
                    Text(
                        stringResource(t),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { tab = i }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (tab) {
                    0 -> {
                        Tool(Icons.AutoMirrored.Rounded.Undo) { onJs("XL.undo()") }
                        Tool(Icons.AutoMirrored.Rounded.Redo) { onJs("XL.redo()") }
                        Tool(Icons.Rounded.SelectAll, selected = rangeMode, label = stringResource(R.string.range_mode)) { onRange() }
                        Sep()
                        Tool(Icons.Rounded.FormatBold) { onJs("XL.toggle('b')") }
                        Tool(Icons.Rounded.FormatItalic) { onJs("XL.toggle('i')") }
                        Box {
                            Tool(Icons.Rounded.FormatColorText) { menu = "color" }
                            DropdownMenu(expanded = menu == "color", onDismissRequest = { menu = null }) {
                                Swatches(textColors, stringResource(R.string.text_color)) { c -> menu = null; onJs("XL.fmt({c:'$c'})") }
                            }
                        }
                        Box {
                            Tool(Icons.Rounded.FormatColorFill) { menu = "fill" }
                            DropdownMenu(expanded = menu == "fill", onDismissRequest = { menu = null }) {
                                Swatches(markColors.map { if (it == "transparent") "none" else it }, stringResource(R.string.highlight_color)) { c -> menu = null; onJs("XL.fmt({bg:'$c'})") }
                            }
                        }
                        Tool(Icons.AutoMirrored.Rounded.FormatAlignLeft) { onJs("XL.fmt({al:'left'})") }
                        Tool(Icons.Rounded.FormatAlignCenter) { onJs("XL.fmt({al:'center'})") }
                        Tool(Icons.AutoMirrored.Rounded.FormatAlignRight) { onJs("XL.fmt({al:'right'})") }
                        Tool(Icons.Rounded.BorderAll, label = stringResource(R.string.borders)) { onJs("XL.fmt({bd:true})") }
                        Box {
                            TextChip(stringResource(R.string.number_format)) { menu = "nf" }
                            DropdownMenu(expanded = menu == "nf", onDismissRequest = { menu = null }) {
                                listOf("gen" to R.string.nf_gen, "int" to R.string.nf_int, "num2" to R.string.nf_num2, "pct" to R.string.nf_pct, "cur" to R.string.nf_cur, "date" to R.string.nf_date)
                                    .forEach { (k, l) -> DropdownMenuItem(text = { Text(stringResource(l)) }, onClick = { menu = null; onJs("XL.fmt({nf:'$k'})") }) }
                            }
                        }
                        Sep()
                        Tool(Icons.Rounded.ContentCopy, label = stringResource(R.string.copy_cells)) { onDialog("copy") }
                        Tool(Icons.Rounded.ContentPaste, label = stringResource(R.string.paste_cells)) { onJs("XL.paste()") }
                        Tool(Icons.Rounded.Backspace, label = stringResource(R.string.clear_cells)) { onJs("XL.clear()") }
                    }
                    1 -> listOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "COUNTA", "IF", "SUMIF", "COUNTIF", "ROUND").forEach { f ->
                        Text(
                            if (f == "SUM") "Σ SUM" else f,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { onJs("XL.autoFn('$f')") }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }
                    2 -> {
                        LabeledTool(Icons.Rounded.VerticalAlignTop, stringResource(R.string.ins_row_above)) { onOp("insRowAbove", null) }
                        LabeledTool(Icons.Rounded.VerticalAlignBottom, stringResource(R.string.ins_row_below)) { onOp("insRowBelow", null) }
                        LabeledTool(Icons.Rounded.DeleteSweep, stringResource(R.string.del_row)) { onOp("delRow", null) }
                        LabeledTool(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.ins_col_left)) { onOp("insColLeft", null) }
                        LabeledTool(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.ins_col_right)) { onOp("insColRight", null) }
                        LabeledTool(Icons.Rounded.Delete, stringResource(R.string.del_col)) { onOp("delCol", null) }
                        LabeledTool(Icons.Rounded.CallMerge, stringResource(R.string.merge_cells)) { onOp("merge", null) }
                        LabeledTool(Icons.Rounded.CallSplit, stringResource(R.string.unmerge_cells)) { onOp("unmerge", null) }
                        LabeledTool(Icons.Rounded.ViewColumn, stringResource(R.string.col_narrow)) { onOp("colWidth", "8") }
                        LabeledTool(Icons.Rounded.ViewColumn, stringResource(R.string.col_normal)) { onOp("colWidth", "13") }
                        LabeledTool(Icons.Rounded.ViewColumn, stringResource(R.string.col_wide)) { onOp("colWidth", "25") }
                    }
                    3 -> {
                        LabeledTool(Icons.Rounded.Add, stringResource(R.string.add_sheet)) { onDialog("sheetAdd") }
                        LabeledTool(Icons.Rounded.Edit, stringResource(R.string.rename_sheet)) { onDialog("sheetRename") }
                        LabeledTool(Icons.Rounded.Delete, stringResource(R.string.del_sheet)) { onOp("delSheet", null) }
                        LabeledTool(Icons.Rounded.AcUnit, stringResource(R.string.freeze_row)) { onOp("freezeRow", null) }
                        LabeledTool(Icons.Rounded.AcUnit, stringResource(R.string.freeze_col)) { onOp("freezeCol", null) }
                        LabeledTool(Icons.Rounded.Close, stringResource(R.string.unfreeze)) { onOp("unfreeze", null) }
                    }
                    4 -> {
                        LabeledTool(Icons.Rounded.ArrowUpward, stringResource(R.string.sort_az)) { onOp("sortAsc", null) }
                        LabeledTool(Icons.Rounded.ArrowDownward, stringResource(R.string.sort_za)) { onOp("sortDesc", null) }
                        LabeledTool(Icons.Rounded.FilterList, stringResource(R.string.filter)) { onDialog("filter") }
                        LabeledTool(Icons.Rounded.FindReplace, stringResource(R.string.find_replace)) { onDialog("find") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(v.trim()) }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}


private val layoutLabels = mapOf(
    "title slide" to R.string.lay_title, "title and content" to R.string.lay_title_content, "section header" to R.string.lay_section,
    "two content" to R.string.lay_two, "comparison" to R.string.lay_compare, "title only" to R.string.lay_title_only, "blank" to R.string.lay_blank,
    "content with caption" to R.string.lay_caption, "picture with caption" to R.string.lay_pic_caption,
)

@Composable
private fun LayoutDialog(names: List<String>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_layout)) },
        text = {
            LazyColumn {
                items(names.size) { i ->
                    val n = names[i]
                    val label = layoutLabels[n.lowercase()]?.let { stringResource(it) } ?: n
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(i) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
