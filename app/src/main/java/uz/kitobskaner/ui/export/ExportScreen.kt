package uz.kitobskaner.ui.export

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.data.FontChoice
import uz.kitobskaner.data.ImageFilter
import uz.kitobskaner.data.ImageQuality
import uz.kitobskaner.data.PageSize
import uz.kitobskaner.ocr.OcrWorker
import uz.kitobskaner.pdf.ExportFormat
import uz.kitobskaner.pdf.ExportResult
import uz.kitobskaner.ui.components.Pill
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.theme.BrandGradient
import java.io.File

private class CreateDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private data class FormatInfo(
    val format: ExportFormat,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String? = null,
)

private val FORMATS = listOf(
    FormatInfo(
        ExportFormat.BOOK_PDF, "Kitob PDF",
        "Matn qayta sahifalanadi: chiroyli shrift, qalin yozuvlar, sarlavhalar, mundarija. Matnni nusxalash mumkin.",
        Icons.Rounded.AutoStories, "TAVSIYA"
    ),
    FormatInfo(
        ExportFormat.SEARCHABLE_PDF, "Qidiriladigan skan PDF",
        "Asl sahifa ko'rinishi + ko'rinmas matn qatlami. Qidirish va nusxalash ishlaydi.",
        Icons.Rounded.ImageSearch
    ),
    FormatInfo(
        ExportFormat.IMAGE_PDF, "Oddiy skan PDF",
        "Faqat tozalangan sahifa rasmlari. Matn aniqlanishini kutish shart emas.",
        Icons.Rounded.PhotoLibrary
    ),
    FormatInfo(
        ExportFormat.DOCX, "Word (DOCX)",
        "Tahrirlash uchun: sarlavhalar, qalin matn va rasmlar saqlanadi.",
        Icons.Rounded.Description
    ),
    FormatInfo(ExportFormat.TXT, "Oddiy matn (TXT)", "Faqat matn, formatlashsiz.", Icons.Rounded.Article),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(projectId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = App.instance
    val repo = app.repository
    val project by remember(projectId) { repo.projectFlow(projectId) }.collectAsState(initial = repo.get(projectId))
    val settings by app.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var format by rememberSaveable { mutableStateOf(ExportFormat.BOOK_PDF) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<ExportResult?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(CreateDocument()) { uri ->
        val res = lastResult
        if (uri != null && res != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            res.file.inputStream().use { it.copyTo(out) }
                        } != null
                    } catch (e: Exception) {
                        false
                    }
                }
                Toast.makeText(context, if (ok) "Saqlandi ✓" else "Saqlashda xatolik", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val p = project ?: return
    val done = p.ocrDoneCount
    val total = p.pages.size
    val needsOcr = format.needsOcr
    val canExport = total > 0 && (!needsOcr || done > 0)

    fun doExport(action: (ExportResult) -> Unit) {
        if (busy) return
        busy = true
        progress = 0f
        scope.launch {
            try {
                val res = app.exporter.export(p, format, app.settings.value) { f -> progress = f }
                lastResult = res
                action(res)
            } catch (e: Throwable) {
                Toast.makeText(context, "Xatolik: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
                progress = null
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Orqaga") } },
                title = { Text("Eksport") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { doExport { res -> share(context, res.file, format.mime) } },
                            enabled = canExport,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ulashish")
                        }
                        Button(
                            onClick = { doExport { res -> saveLauncher.launch(res.file.name to format.mime) } },
                            enabled = canExport,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (canExport) BrandGradient else androidx.compose.ui.graphics.SolidColor(Color.Gray)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Saqlash", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (done < total) item {
                OcrWarning(done, total, onStart = { OcrWorker.start(context, projectId) })
            }
            item { SectionTitle("Format") }
            FORMATS.forEach { info ->
                item {
                    FormatCard(info, selected = format == info.format) { format = info.format }
                }
            }
            item { SectionTitle("Sozlamalar") }
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (format) {
                            ExportFormat.BOOK_PDF -> {
                                Label("Sahifa o'lchami")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PageSize.entries.forEach { s ->
                                        FilterChip(selected = settings.pageSize == s, onClick = {
                                            app.settingsStore.update { it.copy(pageSize = s) }
                                        }, label = { Text(s.label) })
                                    }
                                }
                                Label("Shrift")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FontChoice.entries.forEach { f ->
                                        FilterChip(selected = settings.font == f, onClick = {
                                            app.settingsStore.update { it.copy(font = f) }
                                        }, label = { Text(f.label) })
                                    }
                                }
                                Label("Shrift o'lchami: ${"%.1f".format(settings.fontSize)} pt")
                                Slider(
                                    value = settings.fontSize,
                                    onValueChange = { v -> app.settingsStore.update { it.copy(fontSize = (v * 2).toInt() / 2f) } },
                                    valueRange = 9f..16f,
                                )
                                Label("Qatorlar oralig'i: ${"%.2f".format(settings.lineSpacing)}")
                                Slider(
                                    value = settings.lineSpacing,
                                    onValueChange = { v -> app.settingsStore.update { it.copy(lineSpacing = (v * 20).toInt() / 20f) } },
                                    valueRange = 1.15f..1.8f,
                                )
                                SwitchRow("Titul sahifa", settings.titlePage) { v -> app.settingsStore.update { it.copy(titlePage = v) } }
                                SwitchRow("Sahifa raqamlari", settings.pageNumbers) { v -> app.settingsStore.update { it.copy(pageNumbers = v) } }
                                SwitchRow("Mundarija (oxirida)", settings.tableOfContents) { v -> app.settingsStore.update { it.copy(tableOfContents = v) } }
                            }
                            ExportFormat.SEARCHABLE_PDF, ExportFormat.IMAGE_PDF -> {
                                Label("Rasm filtri")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ImageFilter.entries.forEach { f ->
                                        FilterChip(selected = settings.imageFilter == f, onClick = {
                                            app.settingsStore.update { it.copy(imageFilter = f) }
                                        }, label = { Text(f.label) })
                                    }
                                }
                                Label("Sifat / hajm")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ImageQuality.entries.forEach { q ->
                                        FilterChip(selected = settings.imageQuality == q, onClick = {
                                            app.settingsStore.update { it.copy(imageQuality = q) }
                                        }, label = { Text(q.label) })
                                    }
                                }
                            }
                            else -> {
                                Text(
                                    "Bu format uchun qo'shimcha sozlama yo'q.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = {
                                    scope.launch {
                                        val text = app.exporter.plainText(p)
                                        copy(context, text)
                                    }
                                }, enabled = done > 0) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Butun matnni nusxalash")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (busy) ProgressDialog("Tayyorlanmoqda…", progress, (FORMATS.find { it.format == format }?.title ?: ""))
}

@Composable
private fun OcrWarning(done: Int, total: Int, onStart: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Matn $done / $total sahifada aniqlangan",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onStart) { Text("Davom etish") }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Matnli formatlarga faqat aniqlangan sahifalar kiradi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FormatCard(info: FormatInfo, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) BrandGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)),
                contentAlignment = Alignment.Center
            ) {
                Icon(info.icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info.title, style = MaterialTheme.typography.titleMedium)
                    if (info.badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Pill(info.badge, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(info.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

private fun share(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, file.name))
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Kitob Skaner", text))
    Toast.makeText(context, "Matn nusxalandi", Toast.LENGTH_SHORT).show()
}
