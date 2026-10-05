package uz.kitobskaner.ui.settings

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uz.kitobskaner.App
import uz.kitobskaner.BuildConfig
import uz.kitobskaner.data.ThemeMode
import uz.kitobskaner.ui.components.LanguageDialog
import uz.kitobskaner.ui.components.Languages
import uz.kitobskaner.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = App.instance.settingsStore
    val s by store.state.collectAsState()
    var showLang by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Orqaga") } },
                title = { Text("Sozlamalar") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Group("Matnni aniqlash (OCR)") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Standart til", style = MaterialTheme.typography.bodyLarge)
                            Text(Languages.label(s.defaultLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showLang = true }) { Text("O'zgartirish") }
                    }
                    Toggle(
                        "Kolontitul va sahifa raqamlarini olib tashlash",
                        "Asl kitobdagi yuqori/pastki sarlavhalar va raqamlar yangi kitobga o'tmaydi",
                        s.removeHeaders
                    ) { v -> store.update { it.copy(removeHeaders = v) } }
                    Toggle("Rasmlarni saqlash", "Kitobdagi suratlar va chizmalar kesib olinadi", s.keepImages) { v ->
                        store.update { it.copy(keepImages = v) }
                    }
                    Toggle(
                        "Oʻzbekcha apostrofni tuzatish",
                        "o' g' → oʻ gʻ (lotin yozuvi uchun)",
                        s.fixUzbekApostrophe
                    ) { v -> store.update { it.copy(fixUzbekApostrophe = v) } }
                    Column {
                        Text("Qalin matnni aniqlash sezgirligi", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                s.boldSensitivity < 0.35f -> "Past — faqat aniq qalin so'zlar"
                                s.boldSensitivity > 0.65f -> "Yuqori — ko'proq so'z qalin deb belgilanadi"
                                else -> "O'rtacha (tavsiya etiladi)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(value = s.boldSensitivity, onValueChange = { v -> store.update { it.copy(boldSensitivity = v) } })
                    }
                    Text(
                        "Sozlamalar keyingi aniqlashdan boshlab qo'llanadi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Group("Ko'rinish") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.SYSTEM to "Tizim", ThemeMode.LIGHT to "Yorug'", ThemeMode.DARK to "Qorong'i").forEach { (m, label) ->
                            FilterChip(selected = s.theme == m, onClick = { store.update { it.copy(theme = m) } }, label = { Text(label) })
                        }
                    }
                }
            }
            item { About() }
        }
    }

    if (showLang) LanguageDialog(s.defaultLanguage, onDismiss = { showLang = false }) { lang ->
        store.update { it.copy(defaultLanguage = lang) }
        showLang = false
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun About() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(BrandGradient)
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoStories, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Kitob Skaner ${BuildConfig.VERSION_NAME}", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "Barcha ishlov — skanerlash, matnni aniqlash (Tesseract 5), qalin matnni topish va PDF yaratish — " +
                    "telefoningiz protsessorida, internetsiz bajariladi. Rasmlaringiz hech qayerga yuborilmaydi.",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
