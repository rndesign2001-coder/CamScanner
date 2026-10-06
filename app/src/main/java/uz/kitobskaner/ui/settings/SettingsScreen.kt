package uz.kitobskaner.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.kitobskaner.App
import uz.kitobskaner.BuildConfig
import uz.kitobskaner.R
import uz.kitobskaner.data.Palette
import uz.kitobskaner.data.ThemeMode
import uz.kitobskaner.ui.components.LanguageDialog
import uz.kitobskaner.ui.components.Languages
import uz.kitobskaner.ui.theme.brandGradient
import uz.kitobskaner.ui.theme.spec

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLanguageChanged: () -> Unit) {
    val store = App.instance.settingsStore
    val s by store.state.collectAsState()
    var showLang by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(stringResource(R.string.settings)) },
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
                Group(stringResource(R.string.appearance)) {
                    Text(stringResource(R.string.ui_language), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "system" to stringResource(R.string.system_default),
                            "uz" to "Oʻzbekcha", "ru" to "Русский", "en" to "English",
                        ).forEach { (code, label) ->
                            FilterChip(selected = s.appLanguage == code, onClick = {
                                if (s.appLanguage != code) {
                                    store.update { it.copy(appLanguage = code) }
                                    onLanguageChanged()
                                }
                            }, label = { Text(label) })
                        }
                    }
                    Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            Triple(ThemeMode.SYSTEM, R.string.theme_system, Icons.Rounded.PhoneAndroid),
                            Triple(ThemeMode.LIGHT, R.string.theme_light, Icons.Rounded.LightMode),
                            Triple(ThemeMode.DARK, R.string.theme_dark, Icons.Rounded.DarkMode),
                        )
                        modes.forEachIndexed { i, (m, label, icon) ->
                            SegmentedButton(
                                selected = s.theme == m,
                                onClick = { store.update { it.copy(theme = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                icon = { Icon(icon, null, Modifier.size(18.dp)) },
                            ) { Text(stringResource(label)) }
                        }
                    }
                    Text(stringResource(R.string.color_theme), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val palettes = buildList {
                            add(Palette.INDIGO to R.string.pal_indigo)
                            add(Palette.OCEAN to R.string.pal_ocean)
                            add(Palette.EMERALD to R.string.pal_emerald)
                            add(Palette.SUNSET to R.string.pal_sunset)
                            add(Palette.ROSE to R.string.pal_rose)
                            add(Palette.GRAPHITE to R.string.pal_graphite)
                            if (Build.VERSION.SDK_INT >= 31) add(Palette.DYNAMIC to R.string.pal_dynamic)
                        }
                        palettes.forEach { (pal, label) ->
                            val selected = s.palette == pal
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                                Surface(
                                    onClick = { store.update { it.copy(palette = pal) } },
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .then(
                                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            else Modifier
                                        ),
                                ) {
                                    Box(
                                        Modifier
                                            .padding(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (pal == Palette.DYNAMIC) Brush.sweepGradient(
                                                    listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFEF4444))
                                                ) else Brush.linearGradient(spec(pal).gradient)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
                                }
                                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item {
                Group(stringResource(R.string.ocr_section)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.default_language), style = MaterialTheme.typography.bodyLarge)
                            Text(Languages.label(s.defaultLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showLang = true }) { Text(stringResource(R.string.change)) }
                    }
                    Toggle(stringResource(R.string.opt_headers), stringResource(R.string.opt_headers_sub), s.removeHeaders) { v ->
                        store.update { it.copy(removeHeaders = v) }
                    }
                    Toggle(stringResource(R.string.opt_images), stringResource(R.string.opt_images_sub), s.keepImages) { v ->
                        store.update { it.copy(keepImages = v) }
                    }
                    Toggle(stringResource(R.string.opt_apostrophe), stringResource(R.string.opt_apostrophe_sub), s.fixUzbekApostrophe) { v ->
                        store.update { it.copy(fixUzbekApostrophe = v) }
                    }
                    Column {
                        Text(stringResource(R.string.bold_sensitivity), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                when {
                                    s.boldSensitivity < 0.35f -> R.string.bold_low
                                    s.boldSensitivity > 0.65f -> R.string.bold_high
                                    else -> R.string.bold_medium
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(value = s.boldSensitivity, onValueChange = { v -> store.update { it.copy(boldSensitivity = v) } })
                    }
                    Text(stringResource(R.string.settings_apply_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
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
            .clip(RoundedCornerShape(24.dp))
            .background(brandGradient)
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoStories, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.about_text), color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
