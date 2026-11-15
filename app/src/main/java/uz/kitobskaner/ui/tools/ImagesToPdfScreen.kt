package uz.kitobskaner.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.pdf.ImagesToPdf
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import java.io.File

private class Pic(val uri: Uri, val rot: Int)

/** Rasmlarni tanlab, tartiblab, sozlab — bitta PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(onBack: () -> Unit, onDone: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pics = remember { mutableStateListOf<Pic>() }
    var sel by remember { mutableIntStateOf(-1) }
    var size by rememberSaveable { mutableStateOf(ImagesToPdf.Size.A4) }
    var orient by rememberSaveable { mutableStateOf(ImagesToPdf.Orient.AUTO) }
    var margin by rememberSaveable { mutableStateOf(10f) }
    var quality by rememberSaveable { mutableStateOf(ImagesToPdf.Quality.MEDIUM) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val errMsg = stringResource(R.string.pdf_edit_error)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { list -> pics.addAll(list.map { Pic(it, 0) }) }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!asked) { asked = true; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tool_images_pdf)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Rounded.Add, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(pics, key = { i, p -> p.uri.toString() + i }) { i, p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(if (sel == i) 2.5.dp else 1.dp, if (sel == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { sel = if (sel == i) -1 else i },
                        ) {
                            AsyncImage(p.uri, null, Modifier.fillMaxSize().padding(4.dp).rotate(p.rot.toFloat()), contentScale = ContentScale.Fit)
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(Color(0xAA000000))
                                    .clickable { pics.removeAt(i); if (sel >= pics.size) sel = -1 },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Surface(tonalElevation = 6.dp, shadowElevation = 10.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (sel in pics.indices) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.selected_image, sel + 1), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (sel > 0) { val t = pics[sel - 1]; pics[sel - 1] = pics[sel]; pics[sel] = t; sel-- } }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                        IconButton(onClick = { if (sel < pics.size - 1) { val t = pics[sel + 1]; pics[sel + 1] = pics[sel]; pics[sel] = t; sel++ } }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                        IconButton(onClick = { pics[sel] = Pic(pics[sel].uri, (pics[sel].rot + 90) % 360) }) { Icon(Icons.Rounded.RotateRight, null) }
                    }
                    Chips(stringResource(R.string.page_size), listOf(ImagesToPdf.Size.A4 to "A4", ImagesToPdf.Size.A5 to "A5", ImagesToPdf.Size.LETTER to "Letter", ImagesToPdf.Size.ORIGINAL to stringResource(R.string.size_original)), size) { size = it }
                    if (size != ImagesToPdf.Size.ORIGINAL) Chips(stringResource(R.string.orientation), listOf(ImagesToPdf.Orient.AUTO to stringResource(R.string.orient_auto), ImagesToPdf.Orient.PORTRAIT to stringResource(R.string.portrait), ImagesToPdf.Orient.LANDSCAPE to stringResource(R.string.landscape)), orient) { orient = it }
                    Chips(stringResource(R.string.margins), listOf(0f to "0", 5f to "5 mm", 10f to "10 mm", 20f to "20 mm"), margin) { margin = it }
                    Chips(stringResource(R.string.image_quality), listOf(ImagesToPdf.Quality.HIGH to stringResource(R.string.q_best), ImagesToPdf.Quality.MEDIUM to stringResource(R.string.q_better), ImagesToPdf.Quality.LOW to stringResource(R.string.q_good)), quality) { quality = it }
                    Button(
                        onClick = {
                            scope.launch {
                                progress = 0 to pics.size
                                try {
                                    val ex = App.instance.exporter
                                    val out = ex.uniqueFile(ex.safeName(context.getString(R.string.images_pdf_name)), "pdf")
                                    val f = ImagesToPdf.write(context, pics.map { ImagesToPdf.Item(it.uri, it.rot) }, size, orient, margin, quality, out) { d, t -> progress = d to t }
                                    ex.refreshFiles()
                                    onDone(f)
                                } catch (e: Throwable) {
                                    toast(context, errMsg + ": " + (e.message ?: e.javaClass.simpleName))
                                } finally {
                                    progress = null
                                }
                            }
                        },
                        enabled = pics.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null)
                        Text(stringResource(R.string.make_pdf_n, pics.size), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    progress?.let { (d, t) -> ProgressDialog(stringResource(R.string.preparing), if (t > 0) d.toFloat() / t else null) }
}

@Composable
private fun <T> Chips(label: String, options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (v, l) -> FilterChip(v == value, { onPick(v) }, label = { Text(l) }) }
        }
    }
}
