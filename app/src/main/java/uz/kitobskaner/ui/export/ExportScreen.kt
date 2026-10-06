package uz.kitobskaner.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.data.FontChoice
import uz.kitobskaner.data.ImageFilter
import uz.kitobskaner.data.ImageQuality
import uz.kitobskaner.data.PageSize
import uz.kitobskaner.ocr.OcrWorker
import uz.kitobskaner.pdf.ExportFormat
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.result.CreateDocument
import uz.kitobskaner.ui.theme.brandGradient
import java.io.File

private enum class Kind { PDF, DOC, JPG, XLS, TXT }
private enum class PdfKind { BOOK, SEARCHABLE, SCAN }
private enum class Dest { APP, DOWNLOADS, OTHER }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(projectId: String, onBack: () -> Unit, onDone: (File) -> Unit) {
    val context = LocalContext.current
    val app = App.instance
    val repo = app.repository
    val store = app.settingsStore
    val project by remember(projectId) { repo.projectFlow(projectId) }.collectAsState(initial = repo.get(projectId))
    val settings by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    var kind by rememberSaveable { mutableStateOf(Kind.PDF) }
    var pdfKind by rememberSaveable { mutableStateOf(PdfKind.BOOK) }
    var dest by rememberSaveable { mutableStateOf(Dest.APP) }
    var showBook by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val savedMsg = stringResource(R.string.saved_ok)
    val saveErr = stringResource(R.string.save_error)
    val saveAs = rememberLauncherForActivityResult(CreateDocument()) { uri ->
        val f = pendingFile
        if (uri != null && f != null) toast(context, if (app.exporter.copyTo(f, uri)) savedMsg else saveErr)
        if (f != null) onDone(f)
        pendingFile = null
    }

    val p = project ?: return
    val done = p.ocrDoneCount
    val total = p.pages.size
    val format = when (kind) {
        Kind.PDF -> when (pdfKind) {
            PdfKind.BOOK -> ExportFormat.BOOK_PDF
            PdfKind.SEARCHABLE -> ExportFormat.SEARCHABLE_PDF
            PdfKind.SCAN -> ExportFormat.IMAGE_PDF
        }
        Kind.DOC -> ExportFormat.DOCX
        Kind.JPG -> ExportFormat.JPG
        Kind.XLS -> ExportFormat.XLSX
        Kind.TXT -> ExportFormat.TXT
    }
    val canExport = total > 0 && (!format.needsOcr || done > 0)
    val imageBased = format == ExportFormat.SEARCHABLE_PDF || format == ExportFormat.IMAGE_PDF || format == ExportFormat.JPG
    val errPrefix = stringResource(R.string.error)

    fun doExport() {
        if (busy) return
        busy = true
        progress = 0f
        scope.launch {
            try {
                val res = app.exporter.export(p, format, app.settings.value) { f -> progress = f }
                when (dest) {
                    Dest.APP -> onDone(res.file)
                    Dest.DOWNLOADS -> {
                        if (app.exporter.saveToDownloads(res.file, res.mime) != null) {
                            toast(context, savedMsg); onDone(res.file)
                        } else { pendingFile = res.file; saveAs.launch(res.file.name to res.mime) }
                    }
                    Dest.OTHER -> { pendingFile = res.file; saveAs.launch(res.file.name to res.mime) }
                }
            } catch (e: Throwable) {
                toast(context, errPrefix + ": " + (e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
                progress = null
            }
        }
    }

    val pager = rememberPagerState { p.pages.size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(if (total > 0) "${pager.currentPage + 1} / $total" else stringResource(R.string.export_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { doExport() },
                    enabled = canExport && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (canExport) brandGradient else androidx.compose.ui.graphics.SolidColor(Color.Gray)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.IosShare, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // sahifalar ko'rinishi
            item {
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 56.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                ) { i ->
                    val page = p.pages[i]
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = repo.thumbFile(projectId, page.id),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(0.72f)
                                .shadow(10.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White),
                        )
                    }
                }
            }
            if (format.needsOcr && done < total) item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.ocr_progress, done, total),
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { OcrWorker.start(context, projectId) }) { Text(stringResource(R.string.continue_)) }
                        }
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else done.toFloat() / total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            stringResource(R.string.ocr_only_done), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            item { Label(stringResource(R.string.export_as)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KindTile(Icons.Rounded.PictureAsPdf, "PDF", Color(0xFFEF4444), kind == Kind.PDF, Modifier.weight(1f)) { kind = Kind.PDF }
                    KindTile(Icons.Rounded.Description, "DOC", Color(0xFF3B82F6), kind == Kind.DOC, Modifier.weight(1f)) { kind = Kind.DOC }
                    KindTile(Icons.Rounded.Image, "JPG", Color(0xFF22C55E), kind == Kind.JPG, Modifier.weight(1f)) { kind = Kind.JPG }
                    KindTile(Icons.Rounded.TableChart, "XLS", Color(0xFF16A34A), kind == Kind.XLS, Modifier.weight(1f)) { kind = Kind.XLS }
                    KindTile(Icons.AutoMirrored.Rounded.Article, "TXT", Color(0xFF64748B), kind == Kind.TXT, Modifier.weight(1f)) { kind = Kind.TXT }
                }
            }
            if (kind == Kind.PDF) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfKindRow(stringResource(R.string.pdf_book), stringResource(R.string.pdf_book_sub), pdfKind == PdfKind.BOOK, true) { pdfKind = PdfKind.BOOK }
                    PdfKindRow(stringResource(R.string.pdf_searchable), stringResource(R.string.pdf_searchable_sub), pdfKind == PdfKind.SEARCHABLE, false) { pdfKind = PdfKind.SEARCHABLE }
                    PdfKindRow(stringResource(R.string.pdf_scan), stringResource(R.string.pdf_scan_sub), pdfKind == PdfKind.SCAN, false) { pdfKind = PdfKind.SCAN }
                }
            } else item {
                Text(
                    stringResource(
                        when (kind) {
                            Kind.DOC -> R.string.fmt_word_sub
                            Kind.JPG -> R.string.fmt_jpg_export_sub
                            Kind.XLS -> R.string.fmt_excel_sub
                            else -> R.string.fmt_txt_sub
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { Label(stringResource(R.string.save_to)) }
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        DestRow(Icons.Rounded.Folder, stringResource(R.string.my_documents), dest == Dest.APP) { dest = Dest.APP }
                        DestRow(Icons.Rounded.Download, stringResource(R.string.downloads_folder), dest == Dest.DOWNLOADS) { dest = Dest.DOWNLOADS }
                        DestRow(Icons.Rounded.SaveAs, stringResource(R.string.save_as), dest == Dest.OTHER) { dest = Dest.OTHER }
                    }
                }
            }

            if (imageBased) {
                item { Label(stringResource(R.string.image_quality)) }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val qs = listOf(ImageQuality.LOW to R.string.q_good, ImageQuality.MEDIUM to R.string.q_better, ImageQuality.HIGH to R.string.q_best)
                        qs.forEachIndexed { i, (q, label) ->
                            SegmentedButton(
                                selected = settings.imageQuality == q,
                                onClick = { store.update { it.copy(imageQuality = q) } },
                                shape = SegmentedButtonDefaults.itemShape(i, qs.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                }
                item { Label(stringResource(R.string.image_filter)) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fs = listOf(
                            ImageFilter.ORIGINAL to R.string.f_original, ImageFilter.ENHANCED to R.string.f_enhanced,
                            ImageFilter.GRAY to R.string.f_gray, ImageFilter.BW to R.string.f_bw,
                        )
                        fs.forEach { (f, label) ->
                            FilterChip(selected = settings.imageFilter == f, onClick = { store.update { it.copy(imageFilter = f) } },
                                label = { Text(stringResource(label)) })
                        }
                    }
                }
            }

            if (format == ExportFormat.BOOK_PDF) {
                item {
                    Surface(onClick = { showBook = !showBook }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.book_settings), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(if (showBook) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                        }
                    }
                }
                if (showBook) item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Label(stringResource(R.string.page_size))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PageSize.entries.forEach { s ->
                                    FilterChip(selected = settings.pageSize == s, onClick = { store.update { it.copy(pageSize = s) } }, label = { Text(s.label) })
                                }
                            }
                            Label(stringResource(R.string.font))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = settings.font == FontChoice.SERIF, onClick = { store.update { it.copy(font = FontChoice.SERIF) } },
                                    label = { Text(stringResource(R.string.font_serif)) })
                                FilterChip(selected = settings.font == FontChoice.SANS, onClick = { store.update { it.copy(font = FontChoice.SANS) } },
                                    label = { Text(stringResource(R.string.font_sans)) })
                            }
                            Label(stringResource(R.string.font_size, "%.1f".format(settings.fontSize)))
                            Slider(value = settings.fontSize, valueRange = 9f..16f,
                                onValueChange = { v -> store.update { it.copy(fontSize = (v * 2).toInt() / 2f) } })
                            Label(stringResource(R.string.line_spacing, "%.2f".format(settings.lineSpacing)))
                            Slider(value = settings.lineSpacing, valueRange = 1.15f..1.8f,
                                onValueChange = { v -> store.update { it.copy(lineSpacing = (v * 20).toInt() / 20f) } })
                            SwitchRow(stringResource(R.string.title_page), settings.titlePage) { v -> store.update { it.copy(titlePage = v) } }
                            SwitchRow(stringResource(R.string.page_numbers), settings.pageNumbers) { v -> store.update { it.copy(pageNumbers = v) } }
                            SwitchRow(stringResource(R.string.toc), settings.tableOfContents) { v -> store.update { it.copy(tableOfContents = v) } }
                        }
                    }
                }
            }
        }
    }

    if (busy) ProgressDialog(stringResource(R.string.preparing), progress)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun KindTile(icon: ImageVector, label: String, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
            border = if (selected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) color else MaterialTheme.colorScheme.onSurface)
            }
        }
        if (selected) Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun PdfKindRow(title: String, sub: String, selected: Boolean, recommended: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (recommended) {
                        Spacer(Modifier.width(6.dp))
                        uz.kitobskaner.ui.components.Pill(
                            stringResource(R.string.recommended), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DestRow(icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
