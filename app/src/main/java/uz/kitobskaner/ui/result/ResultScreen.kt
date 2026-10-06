package uz.kitobskaner.ui.result

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.pdf.PdfTools
import uz.kitobskaner.pdf.mimeOf
import uz.kitobskaner.ui.components.GradientIcon
import uz.kitobskaner.ui.components.formatSize
import uz.kitobskaner.ui.components.openFile
import uz.kitobskaner.ui.components.shareFile
import uz.kitobskaner.ui.components.toast
import uz.kitobskaner.ui.main.FileTypeIcon
import uz.kitobskaner.ui.theme.brandGradient
import java.io.File

class CreateDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: android.content.Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

private const val TELEGRAM = "org.telegram.messenger"
private const val WHATSAPP = "com.whatsapp"

/** Tayyor fayl: ko'rib chiqish, ulashish, saqlash (TapScanner "Share" ekrani kabi). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(path: String, onClose: () -> Unit, onHome: () -> Unit, onCompress: (String) -> Unit) {
    val context = LocalContext.current
    val exporter = App.instance.exporter
    val file = remember(path) { File(path) }
    val mime = remember(path) { mimeOf(file) }
    val isPdf = file.extension.equals("pdf", true)
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var pages by remember { mutableIntStateOf(0) }

    LaunchedEffect(path) {
        if (isPdf) withContext(Dispatchers.IO) {
            pages = PdfTools.pageCount(file)
            thumb = PdfTools.thumbnail(file, 900)?.asImageBitmap()
        }
    }

    val savedMsg = stringResource(R.string.saved_ok)
    val saveErr = stringResource(R.string.save_error)
    val saveAs = rememberLauncherForActivityResult(CreateDocument()) { uri ->
        if (uri != null) toast(context, if (exporter.copyTo(file, uri)) savedMsg else saveErr)
    }
    val pm = context.packageManager
    fun installed(pkg: String) = try {
        pm.getLaunchIntentForPackage(pkg) != null
    } catch (e: Exception) {
        false
    }

    if (!file.exists()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) } },
                title = { Text(stringResource(R.string.ready_title)) },
                actions = { IconButton(onClick = onHome) { Icon(Icons.Rounded.Home, null) } },
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
                Button(
                    onClick = { shareFile(context, file, mime) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(brandGradient),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.IosShare, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.share), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ko'rinish
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val t = thumb
                if (t != null) {
                    Image(
                        bitmap = t, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .shadow(12.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                    )
                } else {
                    Box(
                        Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) { FileTypeIcon(file, 96.dp) }
                }
            }
            // fayl kartasi
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileTypeIcon(file, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (pages > 0) stringResource(R.string.n_pages, pages) + " • " else "") + formatSize(file.length()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        if (!openFile(context, file, mime)) toast(context, context.getString(R.string.no_app_to_open))
                    }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }
                }
            }

            Text(stringResource(R.string.share), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (installed(TELEGRAM)) ShareTarget(Icons.AutoMirrored.Rounded.Send, "Telegram", Color(0xFF229ED9)) {
                    shareFile(context, file, mime, TELEGRAM)
                }
                if (installed(WHATSAPP)) ShareTarget(Icons.AutoMirrored.Rounded.Send, "WhatsApp", Color(0xFF25D366)) {
                    shareFile(context, file, mime, WHATSAPP)
                }
                ShareTarget(Icons.Rounded.Email, stringResource(R.string.email), Color(0xFF3B82F6)) {
                    val intent = Intent(Intent.ACTION_SEND).setType(mime)
                        .putExtra(Intent.EXTRA_STREAM, uz.kitobskaner.ui.components.fileUri(context, file))
                        .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .setSelector(Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:")))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        shareFile(context, file, mime)
                    }
                }
                ShareTarget(Icons.Rounded.MoreHoriz, stringResource(R.string.more), MaterialTheme.colorScheme.onSurfaceVariant) {
                    shareFile(context, file, mime)
                }
            }

            ActionRow(Icons.Rounded.Download, stringResource(R.string.save_downloads), stringResource(R.string.save_downloads_sub)) {
                val uri = exporter.saveToDownloads(file, mime)
                if (uri != null) toast(context, savedMsg)
                else saveAs.launch(file.name to mime)
            }
            ActionRow(Icons.Rounded.SaveAs, stringResource(R.string.save_as), stringResource(R.string.save_as_sub)) {
                saveAs.launch(file.name to mime)
            }
            if (isPdf) ActionRow(Icons.Rounded.Compress, stringResource(R.string.tool_compress), stringResource(R.string.tool_compress_sub)) {
                onCompress(file.absolutePath)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ShareTarget(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Surface(onClick = onClick, shape = CircleShape, color = color, modifier = Modifier.size(62.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(icon, brandGradient, 42.dp, 13.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

