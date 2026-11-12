package uz.kitobskaner.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import uz.kitobskaner.App
import uz.kitobskaner.R
import uz.kitobskaner.data.ScanMode
import uz.kitobskaner.ocr.OcrWorker
import uz.kitobskaner.ui.components.ProgressDialog
import uz.kitobskaner.ui.components.toast
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

/**
 * Kamera kadrlarini tahlil qilib, "avtokadr" qarorini qabul qiladi.
 *
 * - Harakat: ketma-ket kichik kadrlar farqi. Kamera va varaq qimirlamay turgan payt kutiladi.
 * - Aniqlik: markaziy hududdagi gradientlar yig'indisi (xira kadr olinmaydi).
 * - Varaq o'girilishi: suratdan keyin katta harakat bo'lmaguncha yangi kadr olinmaydi —
 *   shu tufayli bir sahifa ikki marta tushmaydi, varaqlaganingiz sari o'zi davom etadi.
 */
private class AutoShot(private val onProgress: (Float) -> Unit, private val onFire: () -> Unit) : ImageAnalysis.Analyzer {
    @Volatile var enabled = true
    @Volatile var busy = false
    private var prev: FloatArray? = null
    private var still = 0
    private var armed = true
    private var moving = 0
    private var lastShot = 0L
    private var bestSharp = 0f

    fun shotTaken() {
        armed = false
        moving = 0
        still = 0
        lastShot = System.currentTimeMillis()
        onProgress(0f)
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buf = plane.buffer
            val rs = plane.rowStride
            val w = image.width
            val h = image.height
            val gw = 48
            val gh = 36
            val cur = FloatArray(gw * gh)
            var mean = 0f
            for (gy in 0 until gh) for (gx in 0 until gw) {
                val x = gx * w / gw
                val y = gy * h / gh
                val v = (buf.get(y * rs + x).toInt() and 0xFF).toFloat()
                cur[gy * gw + gx] = v
                mean += v
            }
            mean /= cur.size
            // aniqlik: markazdagi 60% hududda qo'shni piksellar farqi
            var sharp = 0f
            var n = 0
            val x0 = w / 5; val x1 = w * 4 / 5; val y0 = h / 5; val y1 = h * 4 / 5
            var y = y0
            while (y < y1 - 1) {
                var x = x0
                while (x < x1 - 1) {
                    val a = buf.get(y * rs + x).toInt() and 0xFF
                    val b = buf.get(y * rs + x + 1).toInt() and 0xFF
                    val c = buf.get((y + 1) * rs + x).toInt() and 0xFF
                    sharp += abs(a - b) + abs(a - c)
                    n++
                    x += 3
                }
                y += 3
            }
            sharp = if (n > 0) sharp / n / max(20f, mean) else 0f
            val p = prev
            val motion = if (p == null) 99f else {
                var d = 0f
                for (i in cur.indices) d += abs(cur[i] - p[i])
                d / cur.size
            }
            prev = cur
            bestSharp = max(bestSharp * 0.995f, sharp)

            if (!armed) {
                if (motion > 7f) moving++ else if (moving > 0 && motion < 3f) {
                    // harakat tugadi — yangi varaq tayyor
                    if (moving >= 2 && System.currentTimeMillis() - lastShot > 900) armed = true
                }
                onProgress(0f)
                return
            }
            if (motion < 2.2f) still++ else still = 0
            val need = 9
            onProgress((still.toFloat() / need).coerceAtMost(1f))
            if (enabled && !busy && still >= need && sharp >= bestSharp * 0.72f && mean > 35f) {
                busy = true
                onFire()
            }
        } finally {
            image.close()
        }
    }
}

@Composable
fun BookCameraScreen(projectId: String?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val app = App.instance
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) onBack()
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val shots = remember { mutableStateListOf<Uri>() }
    var auto by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var flashAnim by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val spread = app.settings.value.scanMode == ScanMode.SPREAD
    val ring by animateFloatAsState(progress, label = "ring")
    val shutterAlpha by animateFloatAsState(if (flashAnim) 0.75f else 0f, label = "flash")

    val executor = remember { Executors.newSingleThreadExecutor() }
    val sound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    lateinit var analyzer: AutoShot
    fun takePhoto() {
        val f = File(context.cacheDir, "bookcam/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        sound.play(MediaActionSound.SHUTTER_CLICK)
        flashAnim = true
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    shots += Uri.fromFile(f)
                    flashAnim = false
                    analyzer.shotTaken()
                    analyzer.busy = false
                }

                override fun onError(exception: ImageCaptureException) {
                    flashAnim = false
                    analyzer.busy = false
                }
            }
        )
    }
    analyzer = remember {
        AutoShot(onProgress = { progress = it }, onFire = { ContextCompat.getMainExecutor(context).execute { takePhoto() } })
    }
    LaunchedEffect(auto) { analyzer.enabled = auto }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); sound.release() } }

    val defaultTitle = stringResource(R.string.default_book_title)
    val errImages = stringResource(R.string.err_images)
    fun finish() {
        if (shots.isEmpty()) { onBack(); return }
        saving = true
        scope.launch {
            val id = if (projectId == null) {
                app.repository.createProject(shots.toList(), spread, defaultTitle)?.id
            } else {
                if (app.repository.addPages(projectId, shots.toList(), spread) > 0) projectId else null
            }
            saving = false
            if (id != null) {
                OcrWorker.start(context, id)
                onDone(id)
            } else toast(context, errImages)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (granted) AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    view.scaleType = PreviewView.ScaleType.FIT_CENTER
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(
                                ResolutionSelector.Builder().setResolutionStrategy(
                                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                                ).build()
                            )
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also { it.setAnalyzer(executor, analyzer) }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // kitob ramkasi (2 betli rejimda o'rtada bo'g'in chizig'i)
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 120.dp)
        ) {
            val stroke = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
            drawRoundRect(Color.White.copy(alpha = 0.55f), size = size, cornerRadius = CornerRadius(28f), style = stroke)
            if (spread) drawLine(Color.White.copy(alpha = 0.45f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = shutterAlpha))
        )

        // yuqori panel
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIcon(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White) }
            Spacer(Modifier.weight(1f))
            GlassPill(
                text = stringResource(if (auto) R.string.cam_auto_on else R.string.cam_auto_off),
                active = auto,
                onClick = { auto = !auto },
            )
            Spacer(Modifier.width(8.dp))
            GlassPill(text = stringResource(if (spread) R.string.mode_spread else R.string.mode_single), active = false, onClick = {})
            Spacer(Modifier.width(8.dp))
            GlassIcon(onClick = { flash = !flash }) {
                Icon(if (flash) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, null, tint = Color.White)
            }
        }

        // pastki panel
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (auto) R.string.cam_hint_auto else R.string.cam_hint_manual),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    if (shots.isNotEmpty()) Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("${shots.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge) }
                }
                // tutqich: avtokadr tayyorligi halqa sifatida
                Box(
                    Modifier
                        .size(86.dp)
                        .clickable(enabled = !analyzer.busy) { analyzer.busy = true; takePhoto() },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color.White.copy(alpha = 0.35f), style = Stroke(5.dp.toPx()))
                        if (auto) drawArc(
                            Color(0xFF4ADE80), -90f, 360f * ring, false,
                            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                            size = GSize(size.width, size.height)
                        )
                    }
                    Box(
                        Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (shots.isEmpty()) Color.White.copy(alpha = 0.18f) else Color(0xFF22C55E))
                            .clickable { finish() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Check, null, tint = Color.White) }
                }
            }
        }
    }
    if (saving) ProgressDialog(stringResource(R.string.preparing_pages), null)
}

@Composable
private fun GlassIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) { IconButton(onClick = onClick) { content() } }
}

@Composable
private fun GlassPill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFF22C55E).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.3f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge) }
}
