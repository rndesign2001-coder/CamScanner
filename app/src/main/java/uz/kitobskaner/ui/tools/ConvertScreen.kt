package uz.kitobskaner.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.ocr.OcrWorker
import uz.kitobskaner.pdf.ConvertTarget
import uz.kitobskaner.pdf.PdfConverter
import uz.kitobskaner.pdf.PdfTools
import uz.kitobskaner.ui.components.GradientIcon
import uz.kitobskaner.ui.components.formatSize
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.main.FileTypeIcon
import uz.kitobskaner.ui.theme.brandGradient
import java.io.File

private class TargetInfo(val target: ConvertTarget, val title: Int, val sub: Int, val icon: ImageVector, val colors: List<Color>)

private val TARGETS = listOf(
    TargetInfo(ConvertTarget.DOCX, R.string.fmt_word, R.string.fmt_word_sub, Icons.Rounded.Description, listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))),
    TargetInfo(ConvertTarget.JPG, R.string.fmt_jpg, R.string.fmt_jpg_sub, Icons.Rounded.Image, listOf(Color(0xFF22C55E), Color(0xFF15803D))),
    TargetInfo(ConvertTarget.XLSX, R.string.fmt_excel, R.string.fmt_excel_sub, Icons.Rounded.TableChart, listOf(Color(0xFF16A34A), Color(0xFF0F766E))),
    TargetInfo(ConvertTarget.TXT, R.string.fmt_txt, R.string.fmt_txt_sub, Icons.AutoMirrored.Rounded.Article, listOf(Color(0xFF64748B), Color(0xFF334155))),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    initialTarget: String?,
    onBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = App.instance
    val scope = rememberCoroutineScope()
    val converter = remember { PdfConverter(context, app.exporter) }

    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(-1L) }
    var pages by remember { mutableIntStateOf(0) }
    var selected by remember {
        mutableStateOf(setOfNotNull(ConvertTarget.entries.find { it.ext == initialTarget || it.name.equals(initialTarget, true) }
            ?: ConvertTarget.DOCX))
    }
    var dpi by remember { mutableIntStateOf(200) }
    var showAdvanced by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var results by remember { mutableStateOf<List<File>>(emptyList()) }
    var askOcr by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; name = PdfTools.displayName(context, u); results = emptyList() }
    }
    LaunchedEffect(Unit) { if (uri == null) picker.launch(arrayOf("application/pdf")) }
    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            size = PdfTools.size(context, u)
            pages = try {
                context.contentResolver.openFileDescriptor(u, "r")?.use { fd -> android.graphics.pdf.PdfRenderer(fd).use { it.pageCount } } ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }
    val errText = stringResource(R.string.convert_error)
    val defaultTitle = stringResource(R.string.default_book_title)

    fun runConvert(u: Uri, targets: Set<ConvertTarget>) {
        running = true; progress = 0f
        scope.launch {
            try {
                results = converter.convert(u, name, targets, dpi) { progress = it }
            } catch (e: Throwable) {
                toast(context, errText)
            } finally {
                running = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(stringResource(R.string.convert_title)) },
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
                    onClick = {
                        val u = uri ?: return@Button
                        scope.launch {
                            val textTargets = selected - ConvertTarget.JPG
                            if (textTargets.isNotEmpty() && !PdfTools.hasText(context, u)) askOcr = true
                            else runConvert(u, selected)
                        }
                    },
                    enabled = uri != null && selected.isNotEmpty() && !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(brandGradient),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Sync, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.convert_action).uppercase(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // QAYERDAN
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
                onClick = { if (!running) picker.launch(arrayOf("application/pdf")) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.from).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientIcon(Icons.Rounded.PictureAsPdf, Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626))), 60.dp, 16.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (uri == null) stringResource(R.string.choose_pdf) else "$name.pdf",
                                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            if (uri != null) Text(
                                (if (pages > 0) stringResource(R.string.n_pages, pages) + " • " else "") + formatSize(size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(brandGradient), contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.ArrowDownward, null, tint = Color.White) }
            }
            // QAYERGA
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.to).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TARGETS.forEach { t ->
                        val on = t.target in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GradientIcon(t.icon, Brush.linearGradient(t.colors), 50.dp, 14.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(t.title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(t.sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(checked = on, enabled = !running, onCheckedChange = {
                                selected = if (on) selected - t.target else selected + t.target
                            })
                        }
                    }
                }
            }
            // Qo'shimcha sozlamalar
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
                onClick = { showAdvanced = !showAdvanced }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.advanced), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                    if (showAdvanced) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.jpg_resolution), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(150, 200, 300).forEach { d ->
                                FilterChip(selected = dpi == d, onClick = { dpi = d }, label = { Text("$d DPI") })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.convert_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (running) LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )
            if (results.isNotEmpty()) {
                Text(stringResource(R.string.results), style = MaterialTheme.typography.titleLarge)
                results.forEach { f ->
                    Surface(onClick = { onOpenFile(f) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FileTypeIcon(f, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatSize(f.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (askOcr) AlertDialog(
        onDismissRequest = { askOcr = false },
        title = { Text(stringResource(R.string.scanned_pdf_title)) },
        text = { Text(stringResource(R.string.scanned_pdf_text)) },
        confirmButton = {
            TextButton(onClick = {
                askOcr = false
                val u = uri ?: return@TextButton
                running = true
                scope.launch {
                    val p = app.repository.createFromPdf(u, name.ifEmpty { defaultTitle })
                    running = false
                    if (p != null) {
                        OcrWorker.start(context, p.id)
                        onOpenProject(p.id)
                    } else toast(context, errText)
                }
            }) { Text(stringResource(R.string.recognize_text)) }
        },
        dismissButton = {
            TextButton(onClick = {
                askOcr = false
                val u = uri ?: return@TextButton
                if (ConvertTarget.JPG in selected) runConvert(u, setOf(ConvertTarget.JPG))
            }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
