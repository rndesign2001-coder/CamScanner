package uz.kitobskaner.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.kitobskaner.App
import uz.kitobskaner.data.Project
import uz.kitobskaner.ui.components.Languages
import uz.kitobskaner.ui.components.Pill
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.rememberPageSource
import uz.kitobskaner.ui.theme.BrandGradient
import uz.kitobskaner.ui.theme.Emerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val repo = App.instance.repository
    val context = LocalContext.current
    val projects by repo.projects.collectAsState()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }

    val source = rememberPageSource { uris ->
        scope.launch {
            importing = 0 to uris.size
            val p = repo.createProject(uris) { done, total -> importing = done to total }
            importing = null
            if (p != null) onOpen(p.id)
            else Toast.makeText(context, "Rasmlarni o'qib bo'lmadi. Boshqa rasm tanlab ko'ring.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Hero(onScan = source.scan, onGallery = source.gallery, onSettings = onSettings)
            }
            item { Features() }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Kitoblarim", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (projects.isNotEmpty()) {
                        Pill(
                            projects.size.toString(),
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            if (projects.isEmpty()) {
                item { EmptyState() }
            }
            items(projects, key = { it.id }) { p ->
                ProjectCard(
                    project = p,
                    onClick = { onOpen(p.id) },
                    onRename = { renameTarget = p },
                    onDelete = { deleteTarget = p },
                )
            }
        }
    }

    importing?.let { (d, t) ->
        ProgressDialog("Sahifalar tayyorlanmoqda", if (t > 0) d.toFloat() / t else null, "$d / $t")
    }
    renameTarget?.let { p ->
        RenameDialog(p.title, onDismiss = { renameTarget = null }) { title ->
            scope.launch { repo.rename(p.id, title) }
            renameTarget = null
        }
    }
    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("O'chirilsinmi?") },
            text = { Text("\"${p.title}\" va uning barcha sahifalari o'chiriladi.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.delete(p.id) }
                    deleteTarget = null
                }) { Text("O'chirish", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Bekor qilish") } },
        )
    }
}

@Composable
private fun Hero(onScan: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(BrandGradient)
    ) {
        // bezak doiralar
        Box(
            Modifier
                .padding(start = 220.dp, top = 10.dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
        )
        Box(
            Modifier
                .padding(start = 280.dp, top = 140.dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.AutoStories, null, tint = Color.White) }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Kitob Skaner",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, "Sozlamalar", tint = Color.White)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Kitobingizni\nraqamli nashrga aylantiring",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Suratga oling yoki rasmlarni tanlang — matn ajratiladi, qalin yozuvlar saqlanadi va chiroyli PDF kitob tayyorlanadi.",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onScan,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.DocumentScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Skanerlash", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onGallery,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rasmlar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Features() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Feature(Icons.Rounded.OfflineBolt, "Oflayn", "Internet shart emas", Modifier.weight(1f))
        Feature(Icons.Rounded.Translate, "4 til", "UZ • ЎЗ • RU • EN", Modifier.weight(1f))
        Feature(Icons.Rounded.FormatBold, "Qalin matn", "Uslub saqlanadi", Modifier.weight(1f))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, sub: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyState() {
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
            Icon(Icons.Rounded.AutoStories, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Hali kitob yo'q", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "\"Skanerlash\" tugmasi bilan kitob sahifalarini suratga oling yoki galereyadan rasmlarni tanlang.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
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
                if (first != null) {
                    AsyncImage(
                        model = repo.thumbFile(project.id, first.id),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    project.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${project.pages.size} sahifa • " + SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(project.updatedAt)),
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
                        Pill("MATN TAYYOR", Emerald.copy(alpha = 0.15f), Emerald)
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
                    DropdownMenuItem(
                        text = { Text("Nomini o'zgartirish") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text("O'chirish") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kitob nomi") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Saqlash") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } },
    )
}
