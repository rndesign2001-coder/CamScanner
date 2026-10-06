package uz.kitobskaner.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import uz.kitobskaner.ui.components.EmptyState
import uz.kitobskaner.ui.components.GradientIcon
import uz.kitobskaner.ui.components.Languages
import uz.kitobskaner.ui.components.Pill
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.RenameDialog
import uz.kitobskaner.ui.components.SectionTitle
import uz.kitobskaner.ui.components.SourceSheet
import uz.kitobskaner.ui.components.formatSize
import uz.kitobskaner.ui.components.rememberPageSource
import uz.kitobskaner.ui.components.shareFile
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.theme.Emerald
import uz.kitobskaner.ui.theme.LocalBrand
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class Tool(
    val icon: ImageVector,
    val title: Int,
    val subtitle: Int,
    val colors: List<Color>,
    val action: () -> Unit,
)

@Composable
fun MainScreen(
    onOpenProject: (String) -> Unit,
    onOpenFile: (File) -> Unit,
    onSettings: () -> Unit,
    onCompress: () -> Unit,
    onConvert: (String?) -> Unit,
) {
    val context = LocalContext.current
    val app = App.instance
    val repo = app.repository
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSource by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val defaultTitle = stringResource(R.string.default_book_title)
    val errImages = stringResource(R.string.err_images)
    val errPdf = stringResource(R.string.err_pdf)

    val source = rememberPageSource { uris ->
        scope.launch {
            importing = 0 to uris.size
            val p = repo.createProject(
                uris,
                splitSpreads = app.settings.value.scanMode == ScanMode.SPREAD,
                titlePrefix = defaultTitle,
            ) { d, t -> importing = d to t }
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

    val tools = listOf(
        Tool(Icons.Rounded.DocumentScanner, R.string.tool_scan, R.string.tool_scan_sub, listOf(Color(0xFF6366F1), Color(0xFF9333EA))) { showSource = true },
        Tool(Icons.Rounded.PhotoLibrary, R.string.tool_images_pdf, R.string.tool_images_pdf_sub, listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))) { source.gallery() },
        Tool(Icons.Rounded.TextFields, R.string.tool_pdf_ocr, R.string.tool_pdf_ocr_sub, listOf(Color(0xFF10B981), Color(0xFF0EA5E9))) { openPdf() },
        Tool(Icons.Rounded.Compress, R.string.tool_compress, R.string.tool_compress_sub, listOf(Color(0xFFF59E0B), Color(0xFFEF4444))) { onCompress() },
        Tool(Icons.Rounded.Description, R.string.tool_pdf_word, R.string.tool_pdf_word_sub, listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))) { onConvert("docx") },
        Tool(Icons.Rounded.Image, R.string.tool_pdf_jpg, R.string.tool_pdf_jpg_sub, listOf(Color(0xFF22C55E), Color(0xFF15803D))) { onConvert("jpg") },
        Tool(Icons.Rounded.TableChart, R.string.tool_pdf_excel, R.string.tool_pdf_excel_sub, listOf(Color(0xFF16A34A), Color(0xFF0F766E))) { onConvert("xlsx") },
        Tool(Icons.AutoMirrored.Rounded.Article, R.string.tool_pdf_txt, R.string.tool_pdf_txt_sub, listOf(Color(0xFF64748B), Color(0xFF334155))) { onConvert("txt") },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.Home, null) }, label = { Text(stringResource(R.string.tab_home)) })
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.GridView, null) }, label = { Text(stringResource(R.string.tab_tools)) })
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.FolderOpen, null) }, label = { Text(stringResource(R.string.tab_files)) })
            }
        },
    ) { pad ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            label = "tab",
        ) { t ->
            when (t) {
                0 -> HomeTab(tools, onScan = { showSource = true }, onSettings = onSettings, onOpenProject = onOpenProject, onAllTools = { tab = 1 })
                1 -> ToolsTab(tools, onSettings)
                else -> FilesTab(onOpenFile)
            }
        }
    }

    if (showSource) SourceSheet(source, onDismiss = { showSource = false }, onPdf = openPdf)
    importing?.let { (d, t) ->
        ProgressDialog(stringResource(R.string.preparing_pages), if (t > 0) d.toFloat() / t else null, "$d / $t")
    }
}

@Composable
private fun Hero(onScan: () -> Unit, onSettings: () -> Unit) {
    val brand = LocalBrand.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(brand.gradient)
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-40).dp)
                .size(240.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 30.dp, y = 40.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.AutoStories, null, tint = Color.White) }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = Color.White) }
            }
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.hero_title), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.hero_subtitle), color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = brand.colors.first()),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.DocumentScanner, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.scan_now), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun HomeTab(
    tools: List<Tool>,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onOpenProject: (String) -> Unit,
    onAllTools: () -> Unit,
) {
    val repo = App.instance.repository
    val projects by repo.projects.collectAsState()
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(onScan, onSettings) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tools.subList(1, 5).forEach { t -> QuickTool(t, Modifier.weight(1f)) }
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.my_books),
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
            ) {
                if (projects.isNotEmpty()) Pill(
                    projects.size.toString(),
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                else androidx.compose.material3.TextButton(onClick = onAllTools) { Text(stringResource(R.string.all_tools)) }
            }
        }
        if (projects.isEmpty()) item {
            EmptyState(Icons.Rounded.AutoStories, stringResource(R.string.no_books), stringResource(R.string.no_books_hint))
        }
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
private fun QuickTool(t: Tool, modifier: Modifier) {
    Surface(
        onClick = t.action,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GradientIcon(t.icon, Brush.linearGradient(t.colors), 42.dp, 13.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(t.title), style = MaterialTheme.typography.labelMedium, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ToolsTab(tools: List<Tool>, onSettings: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_tools), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.tools_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, null) }
            }
        }
        items(tools) { t ->
            Surface(
                onClick = t.action,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    GradientIcon(t.icon, Brush.linearGradient(t.colors), 48.dp, 15.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(t.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(t.subtitle), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilesTab(onOpenFile: (File) -> Unit) {
    val context = LocalContext.current
    val exporter = App.instance.exporter
    val files by exporter.files.collectAsState()
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { exporter.refreshFiles() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                Text(stringResource(R.string.tab_files), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.files_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (files.isEmpty()) item {
            EmptyState(Icons.Rounded.FolderOpen, stringResource(R.string.no_files), stringResource(R.string.no_files_hint))
        }
        items(files, key = { it.absolutePath }) { f ->
            var menu by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(onClick = { onOpenFile(f) }, onLongClick = { menu = true }),
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

@Composable
fun FileTypeIcon(f: File, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val (icon, colors, label) = when (f.extension.lowercase()) {
        "pdf" -> Triple(Icons.Rounded.PictureAsPdf, listOf(Color(0xFFEF4444), Color(0xFFDC2626)), "PDF")
        "docx" -> Triple(Icons.Rounded.Description, listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)), "DOC")
        "xlsx" -> Triple(Icons.Rounded.TableChart, listOf(Color(0xFF22C55E), Color(0xFF15803D)), "XLS")
        "zip" -> Triple(Icons.Rounded.Image, listOf(Color(0xFF10B981), Color(0xFF0D9488)), "JPG")
        else -> Triple(Icons.AutoMirrored.Rounded.Article, listOf(Color(0xFF64748B), Color(0xFF334155)), "TXT")
    }
    Box(contentAlignment = Alignment.BottomCenter) {
        GradientIcon(icon, Brush.linearGradient(colors), size, size / 3.4f)
        Text(
            label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val repo = App.instance.repository
    var menu by remember { mutableStateOf(false) }
    val first = project.pages.firstOrNull()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(64.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (first != null) AsyncImage(
                    model = repo.thumbFile(project.id, first.id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.n_pages, project.pages.size) + " • " +
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(project.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        Languages.short(project.effectiveLanguage ?: project.language),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    val done = project.ocrDoneCount
                    val total = project.pages.size
                    if (total > 0 && done == total) {
                        Pill(stringResource(R.string.text_ready).uppercase(), Emerald.copy(alpha = 0.15f), Emerald)
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
