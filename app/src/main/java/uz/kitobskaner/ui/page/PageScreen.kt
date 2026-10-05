package uz.kitobskaner.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.kitobskaner.App
import uz.kitobskaner.data.Align
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.OcrPage
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.ocr.OcrWorker
import uz.kitobskaner.ui.components.Pill
import uz.kitobskaner.ui.theme.Emerald
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(projectId: String, startIndex: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = App.instance.repository
    val project by remember(projectId) { repo.projectFlow(projectId) }.collectAsState(initial = repo.get(projectId))
    val ocrRevision by repo.ocrRevision.collectAsState()
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(startIndex) }
    var tab by remember { mutableIntStateOf(0) } // 0 - rasm, 1 - matn
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val p = project
    if (p == null || p.pages.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    BackHandler(enabled = editing) { editing = false }
    index = index.coerceIn(0, p.pages.size - 1)
    val page = p.pages[index]

    var ocr by remember { mutableStateOf<OcrPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(page.id, ocrRevision, page.ocrDone) {
        loading = true
        ocr = repo.loadOcr(projectId, page.id)
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (editing) editing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Orqaga")
                    }
                },
                title = {
                    Column {
                        Text(if (editing) "Tahrirlash" else "Sahifa ${index + 1}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${p.pages.size} sahifadan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (editing) {
                        IconButton(onClick = {
                            val cur = ocr
                            if (cur != null) {
                                val blocks = Markup.fromMarkup(editText, cur.blocks)
                                val updated = cur.copy(blocks = blocks, edited = true)
                                scope.launch { repo.saveOcr(projectId, page.id, updated) }
                                ocr = updated
                            }
                            editing = false
                        }) { Icon(Icons.Rounded.Check, "Saqlash", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        IconButton(onClick = {
                            scope.launch { repo.rotatePage(projectId, page.id, 90f); OcrWorker.start(context, projectId) }
                        }) { Icon(Icons.Rounded.RotateRight, "Burish") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Matnni nusxalash") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                    enabled = ocr != null,
                                    onClick = {
                                        menu = false
                                        ocr?.let { copyText(context, it.plainText()) }
                                    })
                                DropdownMenuItem(
                                    text = { Text("Matnni qayta aniqlash") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                    onClick = {
                                        menu = false
                                        scope.launch {
                                            repo.resetOcr(projectId, listOf(page.id))
                                            OcrWorker.start(context, projectId)
                                        }
                                    })
                                DropdownMenuItem(
                                    text = { Text("Oldinga surish") },
                                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
                                    enabled = index > 0,
                                    onClick = {
                                        menu = false
                                        scope.launch { repo.movePage(projectId, page.id, -1); index-- }
                                    })
                                DropdownMenuItem(
                                    text = { Text("Orqaga surish") },
                                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
                                    enabled = index < p.pages.size - 1,
                                    onClick = {
                                        menu = false
                                        scope.launch { repo.movePage(projectId, page.id, 1); index++ }
                                    })
                                DropdownMenuItem(
                                    text = { Text("Sahifani o'chirish") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (!editing) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconButton(onClick = { if (index > 0) index-- }, enabled = index > 0) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Oldingi")
                        }
                        Spacer(Modifier.width(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            SegmentedButton(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                icon = { Icon(Icons.Rounded.Image, null, Modifier.size(18.dp)) },
                            ) { Text("Rasm") }
                            SegmentedButton(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                icon = { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)) },
                            ) { Text("Matn") }
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { if (index < p.pages.size - 1) index++ }, enabled = index < p.pages.size - 1) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Keyingi")
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            when {
                editing -> OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                    shape = RoundedCornerShape(16.dp),
                    supportingText = { Text("# sarlavha  •  ## kichik sarlavha  •  **qalin**  •  bo'sh qator — yangi xatboshi") },
                )
                tab == 0 -> ZoomableImage(repo.pageFile(projectId, page.id), page.revision)
                else -> TextView(
                    ocr = ocr,
                    loading = loading,
                    pageDone = page.ocrDone,
                    imagesDir = repo.imagesDir(projectId),
                    onEdit = {
                        ocr?.let { editText = Markup.toMarkup(it.blocks); editing = true }
                    },
                )
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Sahifa o'chirilsinmi?") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                val id = page.id
                scope.launch { repo.deletePages(projectId, setOf(id)) }
            }) { Text("O'chirish", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Bekor qilish") } },
    )
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Kitob Skaner", text))
    Toast.makeText(context, "Matn nusxalandi", Toast.LENGTH_SHORT).show()
}

@Composable
private fun ZoomableImage(file: File, revision: Int) {
    var scale by remember(file, revision) { mutableFloatStateOf(1f) }
    var offset by remember(file, revision) { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale <= 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111118))
            .pointerInput(file, revision) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .transformable(state),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
        )
    }
}

@Composable
private fun TextView(ocr: OcrPage?, loading: Boolean, pageDone: Boolean, imagesDir: File, onEdit: () -> Unit) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (ocr == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                if (pageDone) "Matn topilmadi" else "Matn hali aniqlanmagan.\nLoyiha sahifasida \"Aniqlash\" tugmasini bosing.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ocr.confidence >= 0) {
                    val c = ocr.confidence
                    Pill(
                        "ANIQLIK $c%",
                        (if (c >= 80) Emerald else Color(0xFFF59E0B)).copy(alpha = 0.15f),
                        if (c >= 80) Emerald else Color(0xFFB45309)
                    )
                }
                if (ocr.edited) Pill("TAHRIRLANGAN", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tahrirlash")
                }
            }
        }
        if (ocr.blocks.isEmpty()) item {
            Text("Bu sahifada matn topilmadi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(ocr.blocks) { b -> BlockView(b, imagesDir) }
    }
}

@Composable
private fun BlockView(b: TextBlock, imagesDir: File) {
    when (b.type) {
        BlockType.IMAGE -> {
            val f = b.image?.let { File(imagesDir, it) }
            if (f != null) AsyncImage(
                model = f,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(b.imageAspect.coerceIn(0.2f, 5f))
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        BlockType.HEADING1, BlockType.HEADING2 -> Text(
            annotated(b),
            style = if (b.type == BlockType.HEADING1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            textAlign = if (b.align == Align.CENTER) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        else -> Text(
            annotated(b),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            fontFamily = FontFamily.Serif,
            textAlign = when (b.align) {
                Align.CENTER -> TextAlign.Center
                Align.RIGHT -> TextAlign.End
                Align.LEFT -> TextAlign.Start
                Align.JUSTIFY -> if (b.type == BlockType.VERSE) TextAlign.Start else TextAlign.Justify
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (b.type == BlockType.VERSE) Modifier.padding(start = 24.dp) else Modifier),
        )
    }
}

private fun annotated(b: TextBlock): AnnotatedString = buildAnnotatedString {
    for (s in b.spans) {
        if (s.bold || s.italic) {
            withStyle(
                SpanStyle(
                    fontWeight = if (s.bold) FontWeight.Bold else null,
                    fontStyle = if (s.italic) FontStyle.Italic else null,
                )
            ) { append(s.text) }
        } else append(s.text)
    }
}
