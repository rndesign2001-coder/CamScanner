package uz.kitobskaner.ui.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.pdf.mimeOf
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.formatSize
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.theme.GlassCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- fayl tizimi abstraksiyasi

/** Fayl yoki papka: ilova xotirasida (File) yoki foydalanuvchi bergan papkada (SAF). */
sealed class Node {
    abstract val name: String
    abstract val isDir: Boolean
    abstract val size: Long
    abstract val modified: Long
    abstract val uri: Uri

    class Local(val file: File) : Node() {
        override val name: String get() = file.name
        override val isDir: Boolean get() = file.isDirectory
        override val size: Long get() = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
        override val modified: Long get() = file.lastModified()
        override val uri: Uri get() = Uri.fromFile(file)
    }

    class Saf(val doc: DocumentFile) : Node() {
        override val name: String get() = doc.name ?: "?"
        override val isDir: Boolean get() = doc.isDirectory
        override val size: Long get() = if (doc.isDirectory) -1 else doc.length()
        override val modified: Long get() = doc.lastModified()
        override val uri: Uri get() = doc.uri
    }
}

private fun list(dir: Node): List<Node> = when (dir) {
    is Node.Local -> dir.file.listFiles()?.filter { !it.name.endsWith(".tmp") && !it.name.startsWith(".") }?.map { Node.Local(it) } ?: emptyList()
    is Node.Saf -> dir.doc.listFiles().filter { (it.name ?: "").isNotEmpty() && !(it.name ?: "").startsWith(".") }.map { Node.Saf(it) }
}

private fun mimeFor(name: String): String = mimeOf(File(name))

private fun copyInto(context: Context, src: Node, dstDir: Node): Boolean {
    if (src.isDir) {
        val target: Node = when (dstDir) {
            is Node.Local -> Node.Local(uniqueLocal(dstDir.file, src.name).also { it.mkdirs() })
            is Node.Saf -> Node.Saf(dstDir.doc.createDirectory(src.name) ?: return false)
        }
        return list(src).all { copyInto(context, it, target) }
    }
    val input = context.contentResolver.openInputStream(src.uri) ?: return false
    input.use { i ->
        when (dstDir) {
            is Node.Local -> uniqueLocal(dstDir.file, src.name).outputStream().use { i.copyTo(it) }
            is Node.Saf -> {
                val f = dstDir.doc.createFile(mimeFor(src.name), src.name) ?: return false
                context.contentResolver.openOutputStream(f.uri)?.use { i.copyTo(it) } ?: return false
            }
        }
    }
    return true
}

private fun uniqueLocal(dir: File, name: String): File {
    var f = File(dir, name)
    var i = 2
    val base = name.substringBeforeLast('.', name)
    val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
    while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
    return f
}

private fun delete(n: Node): Boolean = when (n) {
    is Node.Local -> n.file.deleteRecursively()
    is Node.Saf -> n.doc.delete()
}

private fun rename(n: Node, newName: String): Node? = when (n) {
    is Node.Local -> {
        val t = File(n.file.parentFile, newName)
        if (!t.exists() && n.file.renameTo(t)) Node.Local(t) else null
    }
    is Node.Saf -> if (n.doc.renameTo(newName)) Node.Saf(n.doc) else null
}

private fun shareNode(context: Context, n: Node) {
    val uri = when (n) {
        is Node.Local -> androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", n.file)
        is Node.Saf -> n.uri
    }
    val i = Intent(Intent.ACTION_SEND).setType(mimeFor(n.name)).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(i, n.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Fayl kengaytmasi bo'yicha tur belgisi. */
fun typeIcon(name: String, dir: Boolean = false): Int {
    if (dir) return R.drawable.ic3d_folder
    return when (name.substringAfterLast('.', "").lowercase()) {
        "doc", "docx", "docm", "dotx", "rtf", "odt" -> R.drawable.ic3d_word
        "xls", "xlsx", "xlsm", "csv", "ods" -> R.drawable.ic3d_excel
        "ppt", "pptx", "ppsx", "pps", "odp" -> R.drawable.ic3d_ppt
        "pdf" -> R.drawable.ic3d_pdf
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic" -> R.drawable.ic3d_images
        "txt", "md", "log", "json", "xml", "html", "htm", "epub" -> R.drawable.ic3d_txt
        "zip" -> R.drawable.ic3d_files
        else -> R.drawable.ic3d_files
    }
}

@Composable
fun TypeIcon(name: String, size: Dp, dir: Boolean = false) {
    Image(painterResource(typeIcon(name, dir)), null, Modifier.size(size))
}

private fun date(t: Long) = if (t <= 0) "" else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(t))

// ---------------------------------------------------------------- ekran

private enum class Sort { NAME, DATE, SIZE }

/**
 * Fayl menejeri: ilova hujjatlari va foydalanuvchi qo'shgan papkalar (Yuklamalar, Hujjatlar...),
 * sevimlilar, saralash, to'r/ro'yxat, qidirish, nusxalash/ko'chirish, nomlash, o'chirish, ulashish.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(onOpen: (Uri) -> Unit, bottomPadding: Dp = 110.dp) {
    val context = LocalContext.current
    val app = App.instance
    val scope = rememberCoroutineScope()
    val folders by app.recents.folders.collectAsState()
    val recent by app.recents.recent.collectAsState()
    val stack = remember { mutableStateListOf<Node>() }
    val items = remember { mutableStateListOf<Node>() }
    var reload by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(Sort.DATE) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var clip by remember { mutableStateOf<Pair<Node, Boolean>?>(null) } // (manba, ko'chirishmi)
    var dialog by remember { mutableStateOf<Pair<String, Node?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val done = stringResource(R.string.done)
    val fail = stringResource(R.string.op_failed)
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            val d = DocumentFile.fromTreeUri(context, u)
            app.recents.addFolder(u, d?.name ?: u.lastPathSegment ?: "Papka")
        }
    }
    val cur = stack.lastOrNull()
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.size - 1) }

    LaunchedEffect(cur, reload) {
        val c = cur ?: run { items.clear(); return@LaunchedEffect }
        val l = withContext(Dispatchers.IO) { try { list(c) } catch (_: Exception) { emptyList() } }
        items.clear(); items.addAll(l)
    }

    fun op(block: suspend () -> Boolean) {
        scope.launch {
            busy = true
            val ok = try { withContext(Dispatchers.IO) { block() } } catch (_: Exception) { false }
            busy = false
            toast(context, if (ok) done else fail)
            app.exporter.refreshFiles()
            reload++
        }
    }

    val shown = items
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<Node> { it.isDir }.then(
            when (sort) {
                Sort.NAME -> compareBy { it.name.lowercase() }
                Sort.DATE -> compareByDescending { it.modified }
                Sort.SIZE -> compareByDescending { if (it is Node.Local && it.isDir) 0L else it.size }
            }
        ))

    Column(Modifier.fillMaxSize()) {
        // sarlavha
        Row(Modifier.statusBarsPadding().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (cur != null) IconButton(onClick = { stack.removeAt(stack.size - 1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
            Column(Modifier.weight(1f).padding(start = if (cur == null) 12.dp else 0.dp)) {
                Text(cur?.name ?: stringResource(R.string.tab_files), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (stack.size > 1) Text(stack.joinToString(" › ") { it.name }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (cur != null) {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Rounded.Search, null) }
                Box {
                    var m by remember { mutableStateOf(false) }
                    IconButton(onClick = { m = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort)) }
                    DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
                        listOf(Sort.NAME to R.string.sort_name, Sort.DATE to R.string.sort_date, Sort.SIZE to R.string.sort_size).forEach { (s, l) ->
                            DropdownMenuItem(text = { Text(stringResource(l) + if (sort == s) "  ✓" else "") }, onClick = { sort = s; m = false })
                        }
                    }
                }
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, null) }
                IconButton(onClick = { dialog = "mkdir" to null }) { Icon(Icons.Rounded.CreateNewFolder, stringResource(R.string.new_folder)) }
            }
        }
        if (searching && cur != null) OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.search_files)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        clip?.let { (src, move) ->
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (move) R.string.moving_x else R.string.copying_x, src.name), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(enabled = cur != null, onClick = {
                        val dst = cur ?: return@TextButton
                        clip = null
                        op { val ok = copyInto(context, src, dst); if (ok && move) delete(src); ok }
                    }) { Icon(Icons.Rounded.ContentPaste, null); Text(" " + stringResource(R.string.paste_here)) }
                    IconButton(onClick = { clip = null }) { Icon(Icons.Rounded.Close, null) }
                }
            }
        }

        if (cur == null) {
            // ildiz: joylar, sevimlilar
            val favs = recent.filter { it.fav }
            LazyVerticalGrid(GridCells.Fixed(1), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Section(stringResource(R.string.locations)) }
                item {
                    PlaceRow(R.drawable.ic3d_folder, stringResource(R.string.app_documents), stringResource(R.string.app_documents_sub)) { stack.add(Node.Local(app.exporter.exportsDir)) }
                }
                items(folders, key = { it.uri }) { f ->
                    var m by remember { mutableStateOf(false) }
                    Box {
                        PlaceRow(R.drawable.ic3d_folder, f.name, stringResource(R.string.device_folder), onLong = { m = true }) {
                            val d = DocumentFile.fromTreeUri(context, Uri.parse(f.uri))
                            if (d != null && d.canRead()) stack.add(Node.Saf(d)) else toast(context, fail)
                        }
                        DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_list)) }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                                onClick = { m = false; app.recents.removeFolder(f.uri) })
                        }
                    }
                }
                item {
                    PlaceRow(R.drawable.ic3d_files, stringResource(R.string.add_device_folder), stringResource(R.string.add_device_folder_sub)) {
                        // Yuklamalar papkasidan boshlab taklif qilamiz
                        val start = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
                        treePicker.launch(start)
                    }
                }
                item { Section(stringResource(R.string.favorites)) }
                if (favs.isEmpty()) item { Text(stringResource(R.string.no_favorites), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp)) }
                items(favs, key = { "fav" + it.uri }) { r ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(Uri.parse(r.uri)) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TypeIcon(r.name, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(r.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { app.recents.toggleFav(r.uri) }) { Icon(Icons.Rounded.Star, null, tint = Color(0xFFF59E0B)) }
                        }
                    }
                }
            }
        } else {
            if (shown.isEmpty()) Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_folder), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyVerticalGrid(
                if (grid) GridCells.Adaptive(108.dp) else GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(if (grid) 10.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.uri.toString() }) { n ->
                    var m by remember { mutableStateOf(false) }
                    val fav = recent.any { it.uri == n.uri.toString() && it.fav }
                    Box {
                        val open: () -> Unit = { if (n.isDir) { stack.add(n); Unit } else onOpen(n.uri) }
                        if (grid) Column(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .combinedClickable(onClick = open, onLongClick = { m = true })
                                .padding(10.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TypeIcon(n.name, 48.dp, n.isDir)
                            Spacer(Modifier.height(6.dp))
                            Text(n.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .combinedClickable(onClick = open, onLongClick = { m = true })
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TypeIcon(n.name, 40.dp, n.isDir)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(date(n.modified).ifEmpty { null }, if (n.isDir) null else formatSize(n.size)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (fav) Icon(Icons.Rounded.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                            IconButton(onClick = { m = true }) { Icon(Icons.Rounded.MoreVert, null) }
                        }
                        DropdownMenu(expanded = m, onDismissRequest = { m = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { m = false; dialog = "rename" to n })
                            DropdownMenuItem(text = { Text(stringResource(R.string.copy_cells)) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { m = false; clip = n to false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_to)) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null) }, onClick = { m = false; clip = n to true })
                            if (!n.isDir) DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, leadingIcon = { Icon(Icons.Rounded.IosShare, null) }, onClick = { m = false; shareNode(context, n) })
                            if (!n.isDir) DropdownMenuItem(
                                text = { Text(stringResource(if (fav) R.string.unfavorite else R.string.add_favorite)) },
                                leadingIcon = { Icon(if (fav) Icons.Rounded.StarBorder else Icons.Rounded.Star, null) },
                                onClick = { m = false; app.recents.setFav(n.uri, n.name, !fav) },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.file_info)) }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { m = false; dialog = "info" to n })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { m = false; dialog = "delete" to n })
                        }
                    }
                }
            }
        }
    }

    when (dialog?.first) {
        "mkdir" -> NameDialog(stringResource(R.string.new_folder), "", onDismiss = { dialog = null }) { name ->
            dialog = null
            val c = cur ?: return@NameDialog
            op {
                when (c) {
                    is Node.Local -> File(c.file, name).let { !it.exists() && it.mkdirs() }
                    is Node.Saf -> c.doc.createDirectory(name) != null
                }
            }
        }
        "rename" -> dialog?.second?.let { n ->
            NameDialog(stringResource(R.string.rename), n.name, onDismiss = { dialog = null }) { name ->
                dialog = null
                // kengaytma yozilmagan bo'lsa, eskisini saqlaymiz
                val ext = n.name.substringAfterLast('.', "")
                val finalName = if (!n.isDir && ext.isNotEmpty() && !name.contains('.')) "$name.$ext" else name
                op { rename(n, finalName)?.also { app.recents.renamed(n.uri.toString(), it.uri.toString(), finalName) } != null }
            }
        }
        "delete" -> dialog?.second?.let { n ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.delete_q)) },
                text = { Text(n.name) },
                confirmButton = { TextButton(onClick = { dialog = null; op { delete(n).also { app.recents.remove(n.uri.toString()) } } }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        "info" -> dialog?.second?.let { n ->
            var size by remember(n) { mutableStateOf<Long?>(null) }
            LaunchedEffect(n) { size = withContext(Dispatchers.IO) { n.size } }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.file_info)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow(stringResource(R.string.info_name), n.name)
                        InfoRow(stringResource(R.string.info_type), if (n.isDir) stringResource(R.string.folder) else mimeFor(n.name))
                        InfoRow(stringResource(R.string.info_size), size?.let { if (it >= 0) formatSize(it) else "—" } ?: "…")
                        InfoRow(stringResource(R.string.info_modified), date(n.modified).ifEmpty { "—" })
                        InfoRow(stringResource(R.string.info_location), if (n is Node.Local) n.file.parent ?: "" else (stack.joinToString("/") { it.name }))
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) } },
            )
        }
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}

@Composable
private fun InfoRow(k: String, v: String) {
    Column { Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun Section(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceRow(icon: Int, title: String, sub: String, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), null, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    val bad = v.isBlank() || v.any { it in "\\/:*?\"<>|" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, singleLine = true, isError = bad && v.isNotEmpty()) },
        confirmButton = { TextButton(onClick = { onOk(v.trim()) }, enabled = !bad) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
