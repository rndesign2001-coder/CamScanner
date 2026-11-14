package uz.kitobskaner.ui.office

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** WebView'ni tashqaridan boshqarish: qidirish, chop etish (PDF'ga saqlash). */
class WebController {
    var view: WebView? = null

    fun find(q: String) {
        view?.findAllAsync(q)
    }

    fun next(forward: Boolean) {
        view?.findNext(forward)
    }

    fun clearFind() {
        view?.clearMatches()
    }

    /** Android chop etish oynasi — u yerdan "PDF sifatida saqlash" ham mumkin. */
    fun print(context: Context, name: String) {
        val v = view ?: return
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(name, v.createPrintDocumentAdapter(name), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}

/** Office hujjatining HTML ko'rinishi: kattalashtirish (chimdish), aylantirish, qidirish. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficeWebView(htmlFile: File, controller: WebController, modifier: Modifier = Modifier) {
    val key = remember(htmlFile) { htmlFile.absolutePath + htmlFile.lastModified() }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true // faqat o'zimiz yaratgan HTML: varaq almashtirish, slayd masshtabi
                settings.allowFileAccess = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.blockNetworkLoads = true
                webViewClient = object : WebViewClient() {
                    // hujjat ichidagi havolalar tashqi brauzerda ochilmasin
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?) = true
                }
                setBackgroundColor(android.graphics.Color.rgb(233, 235, 240))
                controller.view = this
                tag = key
                loadUrl("file://" + htmlFile.absolutePath)
            }
        },
        update = { wv ->
            controller.view = wv
            if (wv.tag != key) {
                wv.tag = key
                wv.loadUrl("file://" + htmlFile.absolutePath)
            }
        },
        onRelease = { wv -> if (controller.view === wv) controller.view = null; wv.destroy() },
        modifier = modifier,
    )
}

/** Hujjat ichidagi rasmni data: URI ga aylantiradi; katta rasmlarni kichraytiradi. */
fun encodeImage(bytes: ByteArray, name: String): String? {
    val lower = name.lowercase()
    if (lower.endsWith(".emf") || lower.endsWith(".wmf") || lower.endsWith(".tif") || lower.endsWith(".tiff")) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    if (o.outWidth <= 0) return null
    val mime = o.outMimeType ?: "image/png"
    val maxSide = 1600
    if (max(o.outWidth, o.outHeight) <= maxSide && bytes.size < 1_500_000 && mime in setOf("image/png", "image/jpeg", "image/gif", "image/webp")) {
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    var s = 1
    while (max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    val bos = ByteArrayOutputStream()
    val png = mime == "image/png" && bmp.hasAlpha()
    bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, bos)
    bmp.recycle()
    return "data:${if (png) "image/png" else "image/jpeg"};base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
}
