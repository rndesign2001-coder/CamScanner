package uz.kitobskaner.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.data.ScanMode
import uz.kitobskaner.ui.theme.LocalBrand
import uz.kitobskaner.ui.theme.brandGradient
import java.io.File

object Languages {
    /** Tesseract kodi → nom (o'z tilida) */
    val ALL = listOf(
        "uzb" to "Oʻzbek (lotin)",
        "uzb_cyrl" to "Ўзбек (кирилл)",
        "rus" to "Русский",
        "eng" to "English",
    )

    @Composable
    fun label(code: String?): String {
        if (code == null || code == "auto") return stringResource(R.string.lang_auto)
        return code.split('+').joinToString(" + ") { c -> ALL.find { it.first == c }?.second ?: c }
    }

    @Composable
    fun short(code: String?): String {
        if (code == null || code == "auto") return stringResource(R.string.lang_auto).uppercase()
        return code.split('+').joinToString("+") {
            when (it) {
                "uzb" -> "UZ"
                "uzb_cyrl" -> "ЎЗ"
                "rus" -> "RU"
                "eng" -> "EN"
                else -> it.uppercase()
            }
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

fun fileUri(context: Context, file: File) = FileProvider.getUriForFile(context, context.packageName + ".files", file)

fun shareFile(context: Context, file: File, mime: String, pkg: String? = null, chooserTitle: String = file.name) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, fileUri(context, file))
        .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        if (pkg != null) context.startActivity(intent.setPackage(pkg))
        else context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        context.startActivity(Intent.createChooser(intent.setPackage(null), chooserTitle))
    }
}

fun openFile(context: Context, file: File, mime: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(fileUri(context, file), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: Exception) {
    false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var auto by remember { mutableStateOf(current == "auto") }
    var selected by remember {
        mutableStateOf(if (current == "auto") setOf<String>() else current.split('+').toSet())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, null) },
        title = { Text(stringResource(R.string.book_language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.book_language_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = auto,
                        onClick = { auto = true; selected = emptySet() },
                        label = { Text(stringResource(R.string.lang_auto)) },
                        leadingIcon = if (auto) ({ Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp)) }) else null,
                    )
                    Languages.ALL.forEach { (code, name) ->
                        val on = !auto && code in selected
                        FilterChip(
                            selected = on,
                            onClick = {
                                auto = false
                                selected = if (on) selected - code else selected + code
                                if (selected.isEmpty()) auto = true
                            },
                            label = { Text(name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val order = Languages.ALL.map { it.first }
                onSelect(if (auto || selected.isEmpty()) "auto" else selected.sortedBy { order.indexOf(it) }.joinToString("+"))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Sahifa qo'shish: rejim (1 bet / 2 bet yoyilma) + manba. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(source: PageSource, onDismiss: () -> Unit, onPdf: (() -> Unit)? = null) {
    val store = App.instance.settingsStore
    val settings by store.state.collectAsState()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val brand = LocalBrand.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.add_pages), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.scan_mode), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.scanMode == ScanMode.SINGLE,
                    onClick = { store.update { it.copy(scanMode = ScanMode.SINGLE) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Rounded.Description, null, Modifier.size(18.dp)) },
                ) { Text(stringResource(R.string.mode_single)) }
                SegmentedButton(
                    selected = settings.scanMode == ScanMode.SPREAD,
                    onClick = { store.update { it.copy(scanMode = ScanMode.SPREAD) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Rounded.AutoStories, null, Modifier.size(18.dp)) },
                ) { Text(stringResource(R.string.mode_spread)) }
            }
            Text(
                stringResource(if (settings.scanMode == ScanMode.SPREAD) R.string.mode_spread_hint else R.string.mode_single_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SourceRow(
                Icons.Rounded.DocumentScanner, stringResource(R.string.src_scanner),
                stringResource(R.string.src_scanner_hint), brand.gradient
            ) { onDismiss(); source.scan() }
            SourceRow(
                Icons.Rounded.PhotoLibrary, stringResource(R.string.src_gallery),
                stringResource(R.string.src_gallery_hint), Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)))
            ) { onDismiss(); source.gallery() }
            SourceRow(
                Icons.Rounded.CameraAlt, stringResource(R.string.src_camera),
                stringResource(R.string.src_camera_hint), Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444)))
            ) { onDismiss(); source.camera() }
            if (onPdf != null) SourceRow(
                Icons.Rounded.Description, stringResource(R.string.src_pdf),
                stringResource(R.string.src_pdf_hint), Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDB2777)))
            ) { onDismiss(); onPdf() }
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String, brush: Brush, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(icon, brush, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GradientIcon(icon: ImageVector, brush: Brush = brandGradient, size: Dp = 44.dp, corner: Dp = 14.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(brush),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun ProgressDialog(title: String, progress: Float?, message: String? = null) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        confirmButton = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress == null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                    )
                    Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                }
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun RenameDialog(initial: String, title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String?, confirm: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = if (text != null) ({ Text(text) }) else null,
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
