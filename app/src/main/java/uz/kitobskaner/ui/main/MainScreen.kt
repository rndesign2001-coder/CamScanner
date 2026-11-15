package uz.kitobskaner.ui.main

import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.data.Project
import uz.kitobskaner.data.ScanMode
import uz.kitobskaner.pdf.PdfTools
import uz.kitobskaner.pdf.mimeOf
import uz.kitobskaner.ui.components.ConfirmDialog
import uz.kitobskaner.ui.components.Icon3D
import uz.kitobskaner.ui.components.Languages
import uz.kitobskaner.ui.components.Pill
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.RenameDialog
import uz.kitobskaner.ui.components.SourceSheet
import uz.kitobskaner.ui.components.formatSize
import uz.kitobskaner.ui.components.rememberPageSource
import uz.kitobskaner.ui.components.shareFile
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.theme.Emerald
import uz.kitobskaner.ui.theme.GlassCard
import uz.kitobskaner.ui.theme.LocalBrand
import uz.kitobskaner.ui.theme.glass
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private class Tool(val icon: Int, val title: Int, val subtitle: Int, val group: Int, val action: () -> Unit)

@Composable
fun MainScreen(
    onOpenProject: (String) -> Unit,
    onOpenFile: (File) -> Unit,
    onSettings: () -> Unit,
    onCompress: () -> Unit,
    onConvert: (String?) -> Unit,
    onOpenDoc: (android.net.Uri) -> Unit,
    onSign: () -> Unit,
    onBookCam: () -> Unit,
    onPdfOp: (String) -> Unit,
    onNewDoc: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = App.instance
    val repo = app.repository
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSource by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var crash by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val f = app.crashFile
        if (f.exists()) { crash = try { f.readText() } catch (e: Exception) { null }; f.delete() }
    }

    val defaultTitle = stringResource(R.string.default_book_title)
    val errImages = stringResource(R.string.err_images)
    val errPdf = stringResource(R.string.err_pdf)

    val source = rememberPageSource { uris ->
        scope.launch {
            importing = 0 to uris.size
            val p = repo.createProject(uris, app.settings.value.scanMode == ScanMode.SPREAD, defaultTitle) { d, t -> importing = d to t }
            importing = null
            if (p != null) onOpenProject(p.id) else toast(context, errImages)
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = 0 to 1
            val p = repo.createFromPdf(uri, PdfTools.displayName(context, uri)) { d, t -> importing = d to t }
            importing = null
            if (p != null) onOpenProject(p.id) else toast(context, errPdf)
        }
    }
    val openPdf = { pdfPicker.launch(arrayOf("application/pdf")) }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onOpenDoc(uri) }
    val openDoc = {
        // barcha fayllar: format mazmunidan aniqlanadi (Word, Excel, PowerPoint, PDF, ODF, RTF, EPUB, rasm, matn...)
        docPicker.launch(arrayOf("*/*"))
    }

    val tools = listOf(
        Tool(R.drawable.ic3d_scan, R.string.tool_scan, R.string.tool_scan_sub, 0) { showSource = true },
        Tool(R.drawable.ic3d_book, R.string.src_bookcam, R.string.src_bookcam_hint, 0, onBookCam),
        Tool(R.drawable.ic3d_gallery, R.string.tool_images_pdf, R.string.tool_images_pdf_sub, 0) { onPdfOp("images") },
        Tool(R.drawable.ic3d_ocr, R.string.tool_pdf_ocr, R.string.tool_pdf_ocr_sub, 0) { openPdf() },
        Tool(R.drawable.ic3d_word, R.string.new_word, R.string.new_word_sub, 1) { onNewDoc("docx") },
        Tool(R.drawable.ic3d_excel, R.string.new_excel, R.string.new_excel_sub, 1) { onNewDoc("xlsx") },
        Tool(R.drawable.ic3d_ppt, R.string.new_ppt, R.string.new_ppt_sub, 1) { onNewDoc("pptx") },
        Tool(R.drawable.ic3d_folder, R.string.tool_open_doc, R.string.tool_open_doc_sub, 1) { openDoc() },
        Tool(R.drawable.ic3d_sign, R.string.tool_sign, R.string.tool_sign_sub, 1, onSign),
        Tool(R.drawable.ic3d_pdf, R.string.pdf_pages, R.string.pdf_pages_sub, 1) { onPdfOp("pages") },
        Tool(R.drawable.ic3d_draw, R.string.pdf_annotate, R.string.pdf_annotate_sub, 1) { onPdfOp("annotate") },
        Tool(R.drawable.ic3d_merge, R.string.tool_merge, R.string.tool_merge_sub, 1) { onPdfOp("merge") },
        Tool(R.drawable.ic3d_compress, R.string.tool_compress, R.string.tool_compress_sub, 1, onCompress),
        Tool(R.drawable.ic3d_lock, R.string.tool_protect, R.string.tool_protect_sub, 1) { onPdfOp("protect") },
        Tool(R.drawable.ic3d_files, R.string.file_converter, R.string.file_converter_sub, 2) { onPdfOp("convert") },
        Tool(R.drawable.ic3d_word, R.string.tool_pdf_word, R.string.tool_pdf_word_sub, 2) { onConvert("docx") },
        Tool(R.drawable.ic3d_excel, R.string.tool_pdf_excel, R.string.tool_pdf_excel_sub, 2) { onConvert("xlsx") },
        Tool(R.drawable.ic3d_images, R.string.tool_pdf_jpg, R.string.tool_pdf_jpg_sub, 2) { onConvert("jpg") },
        Tool(R.drawable.ic3d_txt, R.string.tool_pdf_txt, R.string.tool_pdf_txt_sub, 2) { onConvert("txt") },
    )

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
            when (t) {
                0 -> HomeTab(
                    onScan = { showSource = true }, onSettings = onSettings, onOpenProject = onOpenProject, onOpenDoc = onOpenDoc,
                    onNewDoc = onNewDoc, onPdfOp = onPdfOp, onAllFiles = { tab = 1 },
                )
                1 -> uz.kitobskaner.ui.files.FilesScreen(onOpen = onOpenDoc)
                else -> ToolsTab(tools)
            }
        }
        TabBar(tab, onTab = { tab = it }, onScan = { showSource = true }, onSettings = onSettings, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showSource) SourceSheet(source, onDismiss = { showSource = false }, onPdf = openPdf, onBookCam = onBookCam)
    importing?.let { (d, t) -> ProgressDialog(stringResource(R.string.preparing_pages), if (t > 0) d.toFloat() / t else null, "$d / $t") }
    crash?.let { text ->
        AlertDialog(
            onDismissRequest = { crash = null },
            icon = { Icon3D(R.drawable.ic3d_warning, 44.dp) },
            title = { Text(stringResource(R.string.crash_title)) },
            text = { Text(stringResource(R.string.crash_text)) },
            confirmButton = {
                TextButton(onClick = {
                    val f = File(context.cacheDir, "share/QulayOfis-xatolik.txt").apply { parentFile?.mkdirs(); writeText(text) }
                    shareFile(context, f, "text/plain")
                    crash = null
                }) { Text(stringResource(R.string.share)) }
            },
            dismissButton = { TextButton(onClick = { crash = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- pastki panel (iOS tab bar)

@Composable
private fun TabBar(tab: Int, onTab: (Int) -> Unit, onScan: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val brand = LocalBrand.current
    Column(modifier.fillMaxWidth().background(if (brand.isDark) Color(0xF21A1C21) else Color(0xF7FFFFFF))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.navigationBarsPadding().height(62.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TabItem(Icons.Rounded.Home, stringResource(R.string.tab_home), tab == 0, Modifier.weight(1f)) { onTab(0) }
            TabItem(Icons.Rounded.Folder, stringResource(R.string.tab_files), tab == 1, Modifier.weight(1f)) { onTab(1) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(50.dp)
                        .shadow(6.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onScan),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.tool_scan), tint = Color.White) }
            }
            TabItem(Icons.Rounded.GridView, stringResource(R.string.tab_tools), tab == 2, Modifier.weight(1f)) { onTab(2) }
            TabItem(Icons.Rounded.Settings, stringResource(R.string.settings), false, Modifier.weight(1f), onSettings)
        }
    }
}

@Composable
private fun TabItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxHeight().clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------------------------------------------------------------- Asosiy

private class DocEntry(val uri: android.net.Uri, val name: String, val time: Long, val size: Long, val fav: Boolean)

private val categories = listOf(
    R.string.cat_all to emptySet(),
    R.string.cat_word to setOf("doc", "docx", "docm", "dotx", "rtf", "odt", "txt"),
    R.string.cat_excel to setOf("xls", "xlsx", "xlsm", "csv", "ods"),
    R.string.cat_ppt to setOf("ppt", "pptx", "ppsx", "pps", "odp"),
    R.string.cat_pdf to setOf("pdf"),
    R.string.cat_images to setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic"),
    R.string.cat_other to setOf("*"),
)
private val knownExt = categories.flatMap { it.second }.toSet() - "*"

private fun inCategory(name: String, cat: Int): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    val set = categories[cat].second
    return when {
        set.isEmpty() -> true
        set.contains("*") -> ext !in knownExt
        else -> ext in set
    }
}

@Composable
private fun HomeTab(
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenDoc: (android.net.Uri) -> Unit,
    onNewDoc: (String) -> Unit,
    onPdfOp: (String) -> Unit,
    onAllFiles: () -> Unit,
) {
    val app = App.instance
    val repo = app.repository
    val context = LocalContext.current
    val projects by repo.projects.collectAsState()
    val files by app.exporter.files.collectAsState()
    val recents by app.recents.recent.collectAsState()
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { app.exporter.refreshFiles() }

    // oxirgi ochilganlar + ilova hujjatlari (takrorlanmasdan)
    val entries = remember(files, recents) {
        val seen = HashSet<String>()
        val list = ArrayList<DocEntry>()
        recents.filter { it.time > 0 }.forEach { r -> if (seen.add(r.uri)) list += DocEntry(android.net.Uri.parse(r.uri), r.name, r.time, r.size, r.fav) }
        files.forEach { f -> val u = android.net.Uri.fromFile(f); if (seen.add(u.toString())) list += DocEntry(u, f.name, f.lastModified(), f.length(), false) }
        list.sortedByDescending { it.time }
    }
    val shown = entries.filter { inCategory(it.name, cat) && (query.isBlank() || it.name.contains(query.trim(), true)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(greeting(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            androidx.compose.material3.TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_documents)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) } },
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        if (query.isBlank()) {
            item { SectionTitle(stringResource(R.string.create_new)) }
            item {
                val actions = listOf(
                    Triple(R.drawable.ic3d_word, R.string.q_word) { onNewDoc("docx") },
                    Triple(R.drawable.ic3d_excel, R.string.q_excel) { onNewDoc("xlsx") },
                    Triple(R.drawable.ic3d_ppt, R.string.q_ppt) { onNewDoc("pptx") },
                    Triple(R.drawable.ic3d_pdf, R.string.q_pdf) { onPdfOp("convert") },
                    Triple(R.drawable.ic3d_scan, R.string.q_scan, onScan),
                    Triple(R.drawable.ic3d_gallery, R.string.q_images_pdf) { onPdfOp("images") },
                )
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    actions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (icon, label, act) ->
                                GlassCard(Modifier.weight(1f).height(92.dp), onClick = act) {
                                    Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon3D(icon, 36.dp)
                                        Spacer(Modifier.height(6.dp))
                                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories.size) { i ->
                    val on = cat == i
                    Text(
                        stringResource(categories[i].first),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (on) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                            .clickable { cat = i }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (query.isBlank()) R.string.recent_documents else R.string.search_results), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onAllFiles) { Text(stringResource(R.string.see_all)) }
            }
        }
        if (shown.isEmpty()) item {
            Text(
                stringResource(R.string.no_recent_docs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        items(shown.take(30), key = { "d" + it.uri }) { e -> DocRow(e, onOpenDoc) }
        if (query.isBlank()) {
            item { StorageCard() }
            item { SectionTitle(stringResource(R.string.my_books), if (projects.isNotEmpty()) projects.size.toString() else null) }
            if (projects.isEmpty()) item { Empty(R.drawable.ic3d_book, stringResource(R.string.no_books), stringResource(R.string.no_books_hint)) }
            items(projects, key = { it.id }) { p ->
                ProjectCard(p, onClick = { onOpenProject(p.id) }, onRename = { renameTarget = p }, onDelete = { deleteTarget = p })
            }
        }
    }

    renameTarget?.let { p ->
        RenameDialog(p.title, stringResource(R.string.book_name), onDismiss = { renameTarget = null }) { title ->
            scope.launch { repo.rename(p.id, title) }
            renameTarget = null
        }
    }
    deleteTarget?.let { p ->
        ConfirmDialog(
            stringResource(R.string.delete_q), stringResource(R.string.delete_book_text, p.title),
            stringResource(R.string.delete), destructive = true, onDismiss = { deleteTarget = null }
        ) { scope.launch { repo.delete(p.id) } }
    }
    @Suppress("UNUSED_VARIABLE") val c = context
}

@Composable
private fun SectionTitle(title: String, badge: String? = null) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (badge != null) Text(badge, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val thumbCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(40)

@Composable
private fun DocThumb(uri: android.net.Uri, name: String) {
    val context = LocalContext.current
    val ext = name.substringAfterLast('.', "").lowercase()
    val key = uri.toString()
    var bmp by remember(key) { mutableStateOf(thumbCache.get(key)) }
    Box(
        Modifier.size(width = 42.dp, height = 54.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic") ->
                coil.compose.AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            ext == "pdf" -> {
                LaunchedEffect(key) {
                    if (bmp == null) bmp = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                                android.graphics.pdf.PdfRenderer(fd).use { r ->
                                    r.openPage(0).use { p ->
                                        val b = android.graphics.Bitmap.createBitmap(120, (120f * p.height / p.width).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                                        b.eraseColor(android.graphics.Color.WHITE)
                                        p.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        b.asImageBitmap()
                                    }
                                }
                            }
                        } catch (_: Exception) { null }
                    }?.also { thumbCache.put(key, it) }
                }
                val b = bmp
                if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else uz.kitobskaner.ui.files.TypeIcon(name, 30.dp)
            }
            else -> uz.kitobskaner.ui.files.TypeIcon(name, 30.dp)
        }
    }
}

@Composable
private fun DocRow(e: DocEntry, onOpen: (android.net.Uri) -> Unit) {
    val app = App.instance
    val context = LocalContext.current
    var m by remember { mutableStateOf(false) }
    val type = e.name.substringAfterLast('.', "").uppercase()
    Box(Modifier.padding(horizontal = 16.dp)) {
        GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(e.uri) }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                DocThumb(e.uri, e.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(type.ifEmpty { null }, java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.time)),
                            if (e.size > 0) formatSize(e.size) else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                if (e.fav) Icon(Icons.Rounded.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                IconButton(onClick = { m = true }) { Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.open)) }, onClick = { m = false; onOpen(e.uri) })
            DropdownMenuItem(text = { Text(stringResource(if (e.fav) R.string.unfavorite else R.string.add_favorite)) }, onClick = { m = false; app.recents.setFav(e.uri, e.name, !e.fav) })
            DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = {
                m = false
                val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType(uz.kitobskaner.pdf.mimeOf(File(e.name)))
                    .putExtra(android.content.Intent.EXTRA_STREAM, if (e.uri.scheme == "file") androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", File(e.uri.path!!)) else e.uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { context.startActivity(android.content.Intent.createChooser(i, e.name)) } catch (_: Exception) {}
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_list)) }, onClick = { m = false; app.recents.remove(e.uri.toString()) })
        }
    }
}

@Composable
private fun StorageCard() {
    val app = App.instance
    val files by app.exporter.files.collectAsState()
    val stat = remember { try { android.os.StatFs(app.filesDir.absolutePath) } catch (_: Exception) { null } }
    val total = stat?.totalBytes ?: 0L
    val free = stat?.availableBytes ?: 0L
    val docs = files.sumOf { it.length() }
    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.storage), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.storage_free, formatSize(free)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (total > 0) (1f - free.toFloat() / total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(stringResource(R.string.storage_docs, files.size, formatSize(docs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun greeting(): String {
    val h = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return stringResource(
        when (h) {
            in 5..10 -> R.string.greet_morning
            in 11..17 -> R.string.greet_day
            else -> R.string.greet_evening
        }
    )
}

@Composable
private fun Header(title: String, badge: String? = null) {
    Row(Modifier.padding(start = 22.dp, end = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (badge != null) Pill(badge, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun Empty(icon: Int, title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon3D(icon, 96.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- Asboblar

@Composable
private fun ToolsTab(tools: List<Tool>) {
    val groups = listOf(R.string.sec_scan, R.string.sec_pdf, R.string.sec_convert)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 4.dp)
            ) {
                Text(stringResource(R.string.tab_tools), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.tools_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        groups.forEachIndexed { g, title ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            }
            val list = tools.filter { it.group == g }
            items(list.size) { i ->
                val t = list[i]
                GlassCard(shape = RoundedCornerShape(28.dp), onClick = t.action) {
                    Column(Modifier.padding(16.dp)) {
                        Icon3D(t.icon, 56.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(t.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(t.subtitle), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileTypeIcon(f: File, size: Dp = 48.dp) {
    val (icon, color, label) = when (f.extension.lowercase()) {
        "pdf" -> Triple(R.drawable.ic3d_pdf, Color(0xFFEF4444), "PDF")
        "docx" -> Triple(R.drawable.ic3d_word, Color(0xFF2563EB), "DOC")
        "xlsx" -> Triple(R.drawable.ic3d_excel, Color(0xFF16A34A), "XLS")
        "zip", "jpg", "jpeg" -> Triple(R.drawable.ic3d_images, Color(0xFF0D9488), "JPG")
        else -> Triple(R.drawable.ic3d_txt, Color(0xFF475569), "TXT")
    }
    Box(Modifier.size(size), contentAlignment = Alignment.BottomEnd) {
        Icon3D(icon, size)
        Text(
            label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .padding(horizontal = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val repo = App.instance.repository
    var menu by remember { mutableStateOf(false) }
    val first = project.pages.firstOrNull()
    GlassCard(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(66.dp)
                    .aspectRatio(0.72f)
                    .shadow(6.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
            ) {
                if (first != null) AsyncImage(
                    model = repo.thumbFile(project.id, first.id), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.n_pages, project.pages.size) + " • " +
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(project.updatedAt)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        Languages.short(project.effectiveLanguage ?: project.language),
                        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    val done = project.ocrDoneCount
                    val total = project.pages.size
                    if (total > 0 && done == total) {
                        Pill(stringResource(R.string.text_ready).uppercase(), Emerald.copy(alpha = 0.16f), Emerald)
                    } else if (done > 0) {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total },
                            modifier = Modifier
                                .width(70.dp)
                                .clip(CircleShape)
                        )
                        Text("$done/$total", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

