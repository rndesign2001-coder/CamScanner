package uz.kitobskaner.ui.components

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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import uz.kitobskaner.ui.theme.Indigo
import uz.kitobskaner.ui.theme.Sky
import uz.kitobskaner.ui.theme.Violet

object Languages {
    /** Tesseract kodi → foydalanuvchi uchun nom */
    val ALL = listOf(
        "uzb" to "Oʻzbek (lotin)",
        "uzb_cyrl" to "Ўзбек (кирилл)",
        "rus" to "Русский",
        "eng" to "English",
    )

    fun label(code: String?): String {
        if (code == null || code == "auto") return "Avto"
        return code.split('+').joinToString(" + ") { c -> ALL.find { it.first == c }?.second ?: c }
    }

    fun short(code: String?): String {
        if (code == null || code == "auto") return "AVTO"
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
        title = { Text("Kitob tili") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Avto rejim birinchi sahifalarga qarab tilni o'zi aniqlaydi. Aralash tilli kitoblar uchun bir nechta tilni tanlang (tezlik biroz pasayadi).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = auto,
                        onClick = { auto = true; selected = emptySet() },
                        label = { Text("Avto") },
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
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val order = Languages.ALL.map { it.first }
                onSelect(if (auto || selected.isEmpty()) "auto" else selected.sortedBy { order.indexOf(it) }.joinToString("+"))
            }) { Text("Saqlash") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(source: PageSource, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sahifa qo'shish", style = MaterialTheme.typography.titleLarge)
            SourceRow(
                Icons.Rounded.DocumentScanner, "Aqlli skaner",
                "Qirralarni avtomatik topadi, qiyshiqlikni to'g'rilaydi", Brush.linearGradient(listOf(Indigo, Violet))
            ) { onDismiss(); source.scan() }
            SourceRow(
                Icons.Rounded.PhotoLibrary, "Galereyadan rasmlar",
                "Bir nechta rasmni birdaniga tanlang", Brush.linearGradient(listOf(Sky, Indigo))
            ) { onDismiss(); source.gallery() }
            SourceRow(
                Icons.Rounded.CameraAlt, "Oddiy kamera",
                "Bitta sahifani suratga olish", Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444)))
            ) { onDismiss(); source.camera() }
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
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White) }
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
