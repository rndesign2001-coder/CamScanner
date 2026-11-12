package uz.kitobskaner.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.pdf.PdfTools
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.formatSize
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.theme.GlassButton
import uz.kitobskaner.ui.theme.GlassCard
import java.io.File

/** PDF amallari: "merge" — birlashtirish, "protect" — parol qo'yish. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOpsScreen(mode: String, onBack: () -> Unit, onDone: (File) -> Unit) {
    val context = LocalContext.current
    val exporter = App.instance.exporter
    val scope = rememberCoroutineScope()
    val merge = mode == "merge"
    val files = remember { mutableStateListOf<Uri>() }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val multi = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list -> files.addAll(list) }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { files.clear(); files += u }
    }
    val pick = { if (merge) multi.launch(arrayOf("application/pdf")) else single.launch(arrayOf("application/pdf")) }
    var asked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!asked) { asked = true; pick() } }
    val err = stringResource(R.string.pdfop_error)
    val ready = if (merge) files.size >= 2 else files.size == 1 && password.length >= 4

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } },
                title = { Text(stringResource(if (merge) R.string.tool_merge else R.string.tool_protect)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                GlassButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val first = PdfTools.displayName(context, files.first())
                                val out = exporter.uniqueFile(exporter.safeName(first + if (merge) " (birlashtirilgan)" else " (himoyalangan)"), "pdf")
                                if (merge) PdfTools.merge(context, files.toList(), out) else PdfTools.protect(context, files.first(), password, out)
                                exporter.refreshFiles()
                                onDone(out)
                            } catch (e: Throwable) {
                                toast(context, err)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = ready && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(if (merge) R.string.merge_action else R.string.protect_action),
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(if (merge) R.drawable.ic3d_merge else R.drawable.ic3d_lock), null, Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(if (merge) R.string.merge_hint else R.string.protect_hint),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(files) { i, u ->
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic3d_pdf), null, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(PdfTools.displayName(context, u), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatSize(PdfTools.size(context, u)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (merge) {
                            IconButton(onClick = { if (i > 0) files.add(i - 1, files.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, null) }
                            IconButton(onClick = { if (i < files.size - 1) files.add(i + 1, files.removeAt(i)) }, enabled = i < files.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, null) }
                        }
                        IconButton(onClick = { files.removeAt(i) }) { Icon(Icons.Rounded.Close, null) }
                    }
                }
            }
            item {
                GlassButton(onClick = { pick() }, filled = false, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (merge) R.string.add_pdf else R.string.choose_pdf))
                }
            }
            if (!merge) item {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.trim() },
                    label = { Text(stringResource(R.string.password_min)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (busy) ProgressDialog(stringResource(R.string.preparing), null)
}
