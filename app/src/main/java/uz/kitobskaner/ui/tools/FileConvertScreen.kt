package uz.kitobskaner.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.office.Fmt
import uz.kitobskaner.office.FormatSniffer
import uz.kitobskaner.office.OfficeConvert
import uz.kitobskaner.pdf.PdfTools
import uz.kitobskaner.ui.components.Icon3D
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.theme.GlassCard
import java.io.File

/**
 * Universal konvertor: fayl tanlanadi, format mazmundan aniqlanadi va faqat haqiqatan
 * bajariladigan yo'nalishlar ko'rsatiladi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileConvertScreen(onBack: () -> Unit, onDone: (File) -> Unit, onPdfConvert: (String?) -> Unit, onImagesPdf: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var fmt by remember { mutableStateOf<Fmt?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    val errMsg = stringResource(R.string.convert_failed)
    val tooBig = stringResource(R.string.err_too_large)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        uri = u; name = PdfTools.displayName(context, u); fmt = null; bytes = null
        scope.launch {
            busy = true
            try {
                if (PdfTools.size(context, u) > 150L * 1024 * 1024) { toast(context, tooBig); return@launch }
                val b = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(u)?.use { it.readBytes() } }
                bytes = b
                fmt = b?.let { withContext(Dispatchers.Default) { FormatSniffer.detect(it, name, context.contentResolver.getType(u)) } } ?: Fmt.UNKNOWN
            } finally {
                busy = false
            }
        }
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!asked) { asked = true; picker.launch(arrayOf("*/*")) } }

    fun run(target: String) {
        val b = bytes ?: return
        val f = fmt ?: return
        scope.launch {
            busy = true
            try {
                val ex = App.instance.exporter
                val base = ex.safeName(name.substringBeforeLast('.'))
                val out = ex.uniqueFile(base, target)
                val ok = when (target) {
                    "pdf" -> OfficeConvert.toPdf(context, b, f, name, out)
                    "docx" -> OfficeConvert.toDocx(context, b, f, base, out)
                    "xlsx" -> OfficeConvert.toXlsx(b, f, out)
                    else -> false
                }
                if (ok) { ex.refreshFiles(); onDone(out) } else { out.delete(); toast(context, errMsg) }
            } catch (e: Throwable) {
                toast(context, errMsg + ": " + (e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.file_converter)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth(), onClick = { picker.launch(arrayOf("*/*")) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon3D(R.drawable.ic3d_files, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (uri == null) stringResource(R.string.choose_file) else name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        fmt?.let { Text(stringResource(R.string.detected_format, it.name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.change)) }
                }
            }
            val f = fmt
            if (f != null) {
                val options = buildList {
                    if (f == Fmt.PDF) {
                        add(Triple("pdf2docx", R.string.to_docx, R.drawable.ic3d_word))
                        add(Triple("pdf2xlsx", R.string.to_xlsx, R.drawable.ic3d_excel))
                        add(Triple("pdf2jpg", R.string.to_images, R.drawable.ic3d_images))
                        add(Triple("pdf2txt", R.string.to_txt, R.drawable.ic3d_txt))
                    }
                    if (OfficeConvert.pdfSources(f)) add(Triple("pdf", R.string.to_pdf, R.drawable.ic3d_pdf))
                    if (OfficeConvert.docxSources(f)) add(Triple("docx", R.string.to_docx, R.drawable.ic3d_word))
                    if (OfficeConvert.xlsxSources(f)) add(Triple("xlsx", R.string.to_xlsx, R.drawable.ic3d_excel))
                    if (f == Fmt.IMAGE) add(Triple("images", R.string.tool_images_pdf, R.drawable.ic3d_gallery))
                }
                if (options.isEmpty()) Text(stringResource(R.string.no_conversions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text(stringResource(R.string.convert_to), style = MaterialTheme.typography.labelLarge)
                options.forEach { (key, label, icon) ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = {
                        when (key) {
                            "pdf2docx" -> onPdfConvert("docx")
                            "pdf2xlsx" -> onPdfConvert("xlsx")
                            "pdf2jpg" -> onPdfConvert("jpg")
                            "pdf2txt" -> onPdfConvert("txt")
                            "images" -> onImagesPdf()
                            else -> run(key)
                        }
                    }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon3D(icon, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                        }
                    }
                }
                if (f in setOf(Fmt.DOC, Fmt.RTF, Fmt.ODT)) Text(
                    stringResource(R.string.docx_conv_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uri == null) Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.choose_file)) }
            }
        }
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}
