package uz.kitobskaner.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import uz.kitobskaner.R

/** Sahifa manbalari: aqlli skaner (ML Kit), oddiy kamera, galereya. */
class PageSource(
    val scan: () -> Unit,
    val camera: () -> Unit,
    val gallery: () -> Unit,
)

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun rememberPageSource(onImages: (List<Uri>) -> Unit): PageSource {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onImages)
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uris = result?.pages?.map { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) callback(uris)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) callback(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) callback(listOf(Uri.parse(u)))
    }

    return remember {
        PageSource(
            scan = {
                val activity = context.findActivity()
                if (activity == null) {
                    Toast.makeText(context, context.getString(R.string.err_scanner_open), Toast.LENGTH_SHORT).show()
                } else {
                    val options = GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                        .addOnSuccessListener { sender ->
                            scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, context.getString(R.string.err_no_scanner), Toast.LENGTH_LONG).show()
                            val file = File(context.cacheDir, "camera/${System.currentTimeMillis()}.jpg")
                            file.parentFile?.mkdirs()
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                            cameraUri = uri.toString()
                            cameraLauncher.launch(uri)
                        }
                }
            },
            camera = {
                val file = File(context.cacheDir, "camera/${System.currentTimeMillis()}.jpg")
                file.parentFile?.mkdirs()
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                cameraUri = uri.toString()
                cameraLauncher.launch(uri)
            },
            gallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}
