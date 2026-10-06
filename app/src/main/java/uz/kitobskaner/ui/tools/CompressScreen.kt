package uz.kitobskaner.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.pdf.CompressLevel
import uz.kitobskaner.pdf.CompressResult
import uz.kitobskaner.pdf.PdfTools
import uz.kitobskaner.ui.components.GradientIcon
import uz.kitobskaner.ui.components.formatSize
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.theme.Emerald
import uz.kitobskaner.ui.theme.brandGradient
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(initialPath: String?, onBack: () -> Unit, onDone: (File) -> Unit) {
    val context = LocalContext.current
    val exporter = App.instance.exporter
    val scope = rememberCoroutineScope()

    var uri by remember { mutableStateOf(initialPath?.let { Uri.fromFile(File(it)) }) }
    var name by remember { mutableStateOf(initialPath?.let { File(it).nameWithoutExtension } ?: "") }
    var size by remember { mutableStateOf(-1L) }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var level by remember { mutableStateOf(CompressLevel.MEDIUM) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<CompressResult?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            uri = u; name = PdfTools.displayName(context, u); result = null
        }
    }
    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            size = PdfTools.size(context, u)
            // ko'rinish uchun vaqtinchalik nusxa
            thumb = try {
                val tmp = File(context.cacheDir, "preview.pdf")
                context.contentResolver.openInputStream(u)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                PdfTools.thumbnail(tmp, 700)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    LaunchedEffect(Unit) { if (uri == null) picker.launch(arrayOf("application/pdf")) }
    val errText = stringResource(R.string.compress_error)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(stringResource(R.string.tool_compress)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                val r = result
                Button(
                    onClick = {
                        if (r != null) onDone(r.file)
                        else {
                            val u = uri ?: return@Button
                            running = true; progress = 0f
                            scope.launch {
                                try {
                                    val out = exporter.uniqueFile(exporter.safeName("$name (compressed)"), "pdf")
                                    result = PdfTools.compress(context, u, level, out) { progress = it }
                                    exporter.refreshFiles()
                                } catch (e: Throwable) {
                                    toast(context, errText)
                                } finally {
                                    running = false
                                }
                            }
                        }
                    },
                    enabled = uri != null && !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(brandGradient),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        stringResource(if (r != null) R.string.done else R.string.compress_action),
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val t = thumb
                if (t != null) Image(
                    t, null, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .shadow(10.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                )
                else Surface(onClick = { picker.launch(arrayOf("application/pdf")) }, shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(200.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.FileOpen, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.choose_pdf), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (uri != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, enabled = !running) {
                        Text(stringResource(R.string.change))
                    }
                }
            }

            val r = result
            if (r == null) {
                Text(stringResource(R.string.compress_level), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val levels = listOf(
                        CompressLevel.LOW to R.string.level_low,
                        CompressLevel.MEDIUM to R.string.level_medium,
                        CompressLevel.HIGH to R.string.level_high,
                    )
                    levels.forEachIndexed { i, (l, label) ->
                        SegmentedButton(
                            selected = level == l, onClick = { level = l },
                            shape = SegmentedButtonDefaults.itemShape(i, levels.size), enabled = !running,
                        ) { Text(stringResource(label)) }
                    }
                }
                Text(
                    stringResource(
                        when (level) {
                            CompressLevel.LOW -> R.string.level_low_hint
                            CompressLevel.MEDIUM -> R.string.level_medium_hint
                            CompressLevel.HIGH -> R.string.level_high_hint
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                SizeRow(size, null, running)
                if (running) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
            } else {
                SizeRow(r.before, r.after, false)
                AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = if (r.saved) Emerald else MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.compress_done), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (r.saved) stringResource(R.string.compress_saved, r.percent) else stringResource(R.string.compress_optimal),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeRow(before: Long, after: Long?, animate: Boolean) {
    val tr = rememberInfiniteTransition(label = "arrow")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GradientIcon(Icons.Rounded.Compress, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626))), 64.dp, 18.dp)
            Spacer(Modifier.height(6.dp))
            Text(formatSize(before), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .alpha(if (animate) a else 1f)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GradientIcon(Icons.Rounded.Compress, brandGradient, 64.dp, 18.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                after?.let { formatSize(it) } ?: "?",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
