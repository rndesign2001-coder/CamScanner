package uz.kitobskaner.ui.main

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
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
        docPicker.launch(
            arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/*",
            )
        )
    }

    val tools = listOf(
        Tool(R.drawable.ic3d_scan, R.string.tool_scan, R.string.tool_scan_sub, 0) { showSource = true },
        Tool(R.drawable.ic3d_book, R.string.src_bookcam, R.string.src_bookcam_hint, 0, onBookCam),
        Tool(R.drawable.ic3d_gallery, R.string.tool_images_pdf, R.string.tool_images_pdf_sub, 0) { source.gallery() },
        Tool(R.drawable.ic3d_ocr, R.string.tool_pdf_ocr, R.string.tool_pdf_ocr_sub, 0) { openPdf() },
        Tool(R.drawable.ic3d_folder, R.string.tool_open_doc, R.string.tool_open_doc_sub, 1) { openDoc() },
        Tool(R.drawable.ic3d_sign, R.string.tool_sign, R.string.tool_sign_sub, 1, onSign),
        Tool(R.drawable.ic3d_merge, R.string.tool_merge, R.string.tool_merge_sub, 1) { onPdfOp("merge") },
        Tool(R.drawable.ic3d_compress, R.string.tool_compress, R.string.tool_compress_sub, 1, onCompress),
        Tool(R.drawable.ic3d_lock, R.string.tool_protect, R.string.tool_protect_sub, 1) { onPdfOp("protect") },
        Tool(R.drawable.ic3d_word, R.string.tool_pdf_word, R.string.tool_pdf_word_sub, 2) { onConvert("docx") },
        Tool(R.drawable.ic3d_excel, R.string.tool_pdf_excel, R.string.tool_pdf_excel_sub, 2) { onConvert("xlsx") },
        Tool(R.drawable.ic3d_images, R.string.tool_pdf_jpg, R.string.tool_pdf_jpg_sub, 2) { onConvert("jpg") },
        Tool(R.drawable.ic3d_txt, R.string.tool_pdf_txt, R.string.tool_pdf_txt_sub, 2) { onConvert("txt") },
    )

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
            when (t) {
                0 -> HomeTab(tools, onScan = { showSource = true }, onSettings = onSettings, onOpenProject = onOpenProject, onOpenFile = onOpenFile)
                1 -> ToolsTab(tools)
                else -> FilesTab(onOpenFile, openDoc)
            }
        }
        FloatingBar(tab, onTab = { tab = it }, onScan = { showSource = true }, modifier = Modifier.align(Alignment.BottomCenter))
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

// ---------------------------------------------------------------- pastki suzuvchi panel

@Composable
private fun FloatingBar(tab: Int, onTab: (Int) -> Unit, onScan: () -> Unit, modifier: Modifier) {
    val brand = LocalBrand.current
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(68.dp)
                .glass(RoundedCornerShape(34.dp), brand.isDark, elevation = 18.dp, tint = if (brand.isDark) Color(0xFF9FA4FF) else Color.White)
        ) {
            val itemW = maxWidth / 3
            val x by animateDpAsState(itemW * tab, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "pill")
            // tanlangan bo'lim ostidagi "suyuq" belgi
            Box(
                Modifier
                    .offset(x = x)
                    .width(itemW)
                    .fillMaxHeight()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(brand.colors.first().copy(alpha = 0.22f), brand.colors.last().copy(alpha = 0.12f))))
                    .border(1.dp, Color.White.copy(alpha = if (brand.isDark) 0.18f else 0.8f), RoundedCornerShape(28.dp))
            )
            Row(Modifier.fillMaxSize()) {
                BarItem(R.drawable.ic3d_home, stringResource(R.string.tab_home), tab == 0, Modifier.weight(1f)) { onTab(0) }
                BarItem(R.drawable.ic3d_tools, stringResource(R.string.tab_tools), tab == 1, Modifier.weight(1f)) { onTab(1) }
                BarItem(R.drawable.ic3d_files, stringResource(R.string.tab_files), tab == 2, Modifier.weight(1f)) { onTab(2) }
            }
        }
        Spacer(Modifier.width(12.dp))
        // skaner tugmasi
        Box(
            Modifier
                .size(68.dp)
                .shadow(18.dp, CircleShape, spotColor = brand.colors.first())
                .clip(CircleShape)
                .background(brand.gradient)
                .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.1f))), CircleShape)
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = 90f))
            )
            Icon3D(R.drawable.ic3d_scan, 40.dp)
        }
    }
}

@Composable
private fun BarItem(icon: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s by animateFloatAsState(if (selected) 1.12f else 0.9f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    Column(
        modifier
            .fillMaxHeight()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon3D(icon, 30.dp, Modifier.scale(s))
        Text(
            label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Asosiy

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
private fun HomeTab(
    tools: List<Tool>,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenFile: (File) -> Unit,
) {
    val app = App.instance
    val repo = app.repository
    val projects by repo.projects.collectAsState()
    val files by app.exporter.files.collectAsState()
    val scope = rememberCoroutineScope()
    val brand = LocalBrand.current
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 14.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(R.drawable.logo), null,
                    Modifier
                        .size(46.dp)
                        .shadow(8.dp, RoundedCornerShape(15.dp))
                        .clip(RoundedCornerShape(15.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
                Box(
                    Modifier
                        .size(46.dp)
                        .glass(CircleShape, brand.isDark, 6.dp)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) { Icon3D(R.drawable.ic3d_settings, 28.dp) }
            }
        }
        // bosh karta
        item {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .shadow(22.dp, RoundedCornerShape(36.dp), spotColor = brand.colors.first())
                    .clip(RoundedCornerShape(36.dp))
                    .background(brand.gradient)
                    .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(36.dp))
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 50.dp, y = (-60).dp)
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                )
                Icon3D(
                    R.drawable.ic3d_books, 132.dp,
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = 6.dp)
                        .rotate(8f)
                )
                Column(Modifier.padding(22.dp)) {
                    Text(
                        stringResource(R.string.hero_title), color = Color.White,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.fillMaxWidth(0.68f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.hero_subtitle), color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(0.7f)
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(
                        Modifier
                            .shadow(10.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.86f))))
                            .clickable(onClick = onScan)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon3D(R.drawable.ic3d_scan, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.scan_now), color = brand.colors.first(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        // tezkor amallar
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tools.filter { it.icon != R.drawable.ic3d_scan }.take(8)) { t ->
                    GlassCard(modifier = Modifier.size(width = 104.dp, height = 116.dp), shape = RoundedCornerShape(28.dp), onClick = t.action) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Icon3D(t.icon, 52.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(t.title), style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (files.isNotEmpty()) {
            item { Header(stringResource(R.string.recent_files)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(files.take(10), key = { it.absolutePath }) { f ->
                        GlassCard(modifier = Modifier.width(220.dp), shape = RoundedCornerShape(24.dp), onClick = { onOpenFile(f) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FileTypeIcon(f, 44.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatSize(f.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Header(stringResource(R.string.my_books), if (projects.isNotEmpty()) projects.size.toString() else null) }
        if (projects.isEmpty()) item { Empty(R.drawable.ic3d_book, stringResource(R.string.no_books), stringResource(R.string.no_books_hint)) }
        items(projects, key = { it.id }) { p ->
            ProjectCard(p, onClick = { onOpenProject(p.id) }, onRename = { renameTarget = p }, onDelete = { deleteTarget = p })
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

// ---------------------------------------------------------------- Fayllar

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilesTab(onOpenFile: (File) -> Unit, onOpenDoc: () -> Unit) {
    val context = LocalContext.current
    val exporter = App.instance.exporter
    val files by exporter.files.collectAsState()
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { exporter.refreshFiles() }
    val shown = remember(files, query) { if (query.isBlank()) files else files.filter { it.name.contains(query.trim(), true) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 4.dp)
            ) {
                Text(stringResource(R.string.tab_files), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.files_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_files)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                GlassCard(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(20.dp), onClick = onOpenDoc) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon3D(R.drawable.ic3d_folder, 34.dp) }
                }
            }
        }
        if (shown.isEmpty()) item { Empty(R.drawable.ic3d_files, stringResource(R.string.no_files), stringResource(R.string.no_files_hint)) }
        items(shown, key = { it.absolutePath }) { f ->
            var menu by remember { mutableStateOf(false) }
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onOpenFile(f) }, onLongClick = { menu = true }),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileTypeIcon(f)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            formatSize(f.length()) + " • " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified())),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { shareFile(context, f, mimeOf(f)) }) { Icon(Icons.Rounded.Share, null) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menu = false; renameTarget = f })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menu = false; deleteTarget = f })
                        }
                    }
                }
            }
        }
    }
    renameTarget?.let { f ->
        RenameDialog(f.nameWithoutExtension, stringResource(R.string.file_name), onDismiss = { renameTarget = null }) {
            exporter.rename(f, it); renameTarget = null
        }
    }
    deleteTarget?.let { f ->
        ConfirmDialog(stringResource(R.string.delete_q), f.name, stringResource(R.string.delete), true, onDismiss = { deleteTarget = null }) {
            exporter.delete(f)
        }
    }
}

/** Fayl turi uchun 3D ikonka va yorliq. */
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

