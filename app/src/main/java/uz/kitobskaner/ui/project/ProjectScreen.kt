package uz.kitobskaner.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uz.kitobskaner.App
import uz.kitobskaner.data.PageInfo
import uz.kitobskaner.ocr.OcrWorker
import uz.kitobskaner.ui.components.LanguageDialog
import uz.kitobskaner.ui.components.Languages
import uz.kitobskaner.ui.components.Pill
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.SourceSheet
import uz.kitobskaner.ui.components.rememberPageSource
import uz.kitobskaner.ui.home.RenameDialog
import uz.kitobskaner.ui.theme.BrandGradient
import uz.kitobskaner.ui.theme.Emerald

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    val repo = App.instance.repository
    val project by remember(projectId) { repo.projectFlow(projectId) }.collectAsState(initial = repo.get(projectId))
    val scope = rememberCoroutineScope()
    val workState by remember(projectId) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(OcrWorker.workName(projectId))
            .map { list -> list.firstOrNull { !it.state.isFinished } }
    }.collectAsState(initial = null)
    val running = workState != null
    val runningState = workState?.state

    var showSource by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showLanguage by remember { mutableStateOf(false) }
    var pendingLanguage by remember { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var confirmRerun by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }

    val p = project
    if (p == null) {
        LaunchedEffect(Unit) { repo.ensureLoaded() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val source = rememberPageSource { uris ->
        scope.launch {
            importing = 0 to uris.size
            repo.addPages(projectId, uris) { d, t -> importing = d to t }
            importing = null
            OcrWorker.start(context, projectId)
        }
    }

    // yangi loyiha ochilganda OCR avtomatik boshlanadi
    LaunchedEffect(projectId) {
        val cur = repo.get(projectId)
        if (cur != null && cur.pages.any { !it.ocrDone }) OcrWorker.start(context, projectId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selection.isNotEmpty()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Rounded.Close, null) } },
                    title = { Text("${selection.size} ta tanlandi") },
                    actions = {
                        IconButton(onClick = {
                            val ids = selection
                            selection = emptySet()
                            scope.launch { repo.resetOcr(projectId, ids); OcrWorker.start(context, projectId) }
                        }) { Icon(Icons.Rounded.Refresh, "Qayta aniqlash") }
                        IconButton(onClick = {
                            val ids = selection
                            selection = emptySet()
                            scope.launch { repo.deletePages(projectId, ids) }
                        }) { Icon(Icons.Rounded.Delete, "O'chirish") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Orqaga") }
                    },
                    title = {
                        Text(
                            p.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showRename = true }
                        )
                    },
                    actions = {
                        IconButton(onClick = { showLanguage = true }) { Icon(Icons.Rounded.Translate, "Til") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Nomini o'zgartirish") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menu = false; showRename = true })
                                DropdownMenuItem(
                                    text = { Text("Hammasini qayta aniqlash") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                    onClick = { menu = false; confirmRerun = true })
                                DropdownMenuItem(
                                    text = { Text("Kitobni o'chirish") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menu = false; showDelete = true })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSource = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Sahifa")
                    }
                    Button(
                        onClick = onExport,
                        enabled = p.pages.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.6f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (p.pages.isNotEmpty()) BrandGradient else androidx.compose.ui.graphics.SolidColor(Color.Gray)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Rounded.IosShare, null)
                        Spacer(Modifier.width(8.dp))
                        Text("PDF / Eksport", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StatusCard(
                    language = p.effectiveLanguage ?: p.language,
                    auto = p.language == "auto",
                    done = p.ocrDoneCount,
                    total = p.pages.size,
                    running = running,
                    enqueued = runningState == WorkInfo.State.ENQUEUED || runningState == WorkInfo.State.BLOCKED,
                    onLanguage = { showLanguage = true },
                    onStart = { OcrWorker.start(context, projectId) },
                    onStop = { OcrWorker.cancel(context, projectId) },
                )
            }
            itemsIndexed(p.pages, key = { _, it -> it.id }) { index, page ->
                PageThumb(
                    projectId = projectId,
                    page = page,
                    index = index,
                    running = running,
                    selected = page.id in selection,
                    onClick = {
                        if (selection.isNotEmpty()) {
                            selection = if (page.id in selection) selection - page.id else selection + page.id
                        } else onOpenPage(index)
                    },
                    onLongClick = { selection = selection + page.id },
                )
            }
        }
    }

    if (showSource) SourceSheet(source) { showSource = false }
    importing?.let { (d, t) -> ProgressDialog("Sahifalar qo'shilmoqda", if (t > 0) d.toFloat() / t else null, "$d / $t") }
    if (showRename) RenameDialog(p.title, onDismiss = { showRename = false }) {
        scope.launch { repo.rename(projectId, it) }
        showRename = false
    }
    if (showLanguage) LanguageDialog(p.language, onDismiss = { showLanguage = false }) { lang ->
        showLanguage = false
        if (lang != p.language) {
            if (p.ocrDoneCount > 0) pendingLanguage = lang
            else scope.launch {
                OcrWorker.cancel(context, projectId)
                repo.setLanguage(projectId, lang)
                OcrWorker.start(context, projectId)
            }
        }
    }
    pendingLanguage?.let { lang ->
        AlertDialog(
            onDismissRequest = { pendingLanguage = null },
            title = { Text("Til o'zgartirildi") },
            text = { Text("Barcha sahifalar matni \"${Languages.label(lang)}\" bilan qayta aniqlansinmi? Qo'lda kiritilgan tuzatishlar o'chadi.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingLanguage = null
                    scope.launch {
                        OcrWorker.cancel(context, projectId)
                        repo.setLanguage(projectId, lang)
                        repo.resetOcr(projectId, p.pages.map { it.id })
                        OcrWorker.start(context, projectId)
                    }
                }) { Text("Ha, qayta aniqlash") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingLanguage = null
                    scope.launch { repo.setLanguage(projectId, lang) }
                }) { Text("Faqat yangi sahifalar uchun") }
            },
        )
    }
    if (confirmRerun) AlertDialog(
        onDismissRequest = { confirmRerun = false },
        title = { Text("Qayta aniqlash") },
        text = { Text("Barcha sahifalar matni qaytadan aniqlanadi. Qo'lda kiritilgan tuzatishlar o'chadi.") },
        confirmButton = {
            TextButton(onClick = {
                confirmRerun = false
                scope.launch {
                    OcrWorker.cancel(context, projectId)
                    repo.resetOcr(projectId, p.pages.map { it.id })
                    OcrWorker.start(context, projectId)
                }
            }) { Text("Boshlash") }
        },
        dismissButton = { TextButton(onClick = { confirmRerun = false }) { Text("Bekor qilish") } },
    )
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Kitob o'chirilsinmi?") },
        text = { Text("Barcha sahifalar va aniqlangan matn o'chiriladi.") },
        confirmButton = {
            TextButton(onClick = {
                showDelete = false
                OcrWorker.cancel(context, projectId)
                scope.launch { repo.delete(projectId) }
                onBack()
            }) { Text("O'chirish", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Bekor qilish") } },
    )
}

@Composable
private fun StatusCard(
    language: String,
    auto: Boolean,
    done: Int,
    total: Int,
    running: Boolean,
    enqueued: Boolean,
    onLanguage: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BrandGradient),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.TextSnippet, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            total == 0 -> "Sahifa qo'shing"
                            done == total -> "Matn tayyor"
                            running && enqueued -> "Navbatda…"
                            running -> "Matn aniqlanmoqda…"
                            else -> "Matn aniqlanmagan"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "$done / $total sahifa • qurilmada, internetsiz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (total > 0 && done == total) Icon(Icons.Rounded.CheckCircle, null, tint = Emerald)
            }
            if (total > 0 && done < total) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onLanguage,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Translate, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (auto) "Avto: " else "") + Languages.label(if (auto && language == "auto") null else language),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = total > 0 && done < total) {
                    if (running) {
                        FilledTonalButton(onClick = onStop, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("To'xtatish")
                        }
                    } else {
                        Button(onClick = onStart, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Aniqlash")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumb(
    projectId: String,
    page: PageInfo,
    index: Int,
    running: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val repo = App.instance.repository
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    else Modifier
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            AsyncImage(
                model = repo.thumbFile(projectId, page.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                when {
                    page.ocrDone -> Icon(
                        Icons.Rounded.CheckCircle, null,
                        tint = Emerald,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    running -> Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.HourglassTop, null, Modifier.size(14.dp), tint = Color(0xFF6B7280)) }
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Pill(
                "${index + 1}",
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
