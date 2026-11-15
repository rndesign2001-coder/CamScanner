package uz.kitobskaner.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.kitobskaner.image.ImageUtils
import java.io.File

/** Rasmlardan PDF: sahifa o'lchami, yo'nalish, chegaralar va sifat sozlanadi. */
object ImagesToPdf {
    enum class Size(val w: Float, val h: Float) { A4(595.28f, 841.89f), A5(419.53f, 595.28f), LETTER(612f, 792f), ORIGINAL(0f, 0f) }
    enum class Orient { AUTO, PORTRAIT, LANDSCAPE }
    enum class Quality(val maxSide: Int, val jpeg: Float) { HIGH(3000, 0.9f), MEDIUM(2000, 0.8f), LOW(1400, 0.65f) }

    class Item(val uri: Uri, val rotation: Int)

    suspend fun write(
        context: Context, items: List<Item>, size: Size, orient: Orient, marginMm: Float, quality: Quality, out: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument().use { doc ->
            items.forEachIndexed { i, it ->
                var bmp = ImageUtils.decodeUri(context, it.uri, quality.maxSide) ?: return@forEachIndexed
                if (it.rotation % 360 != 0) bmp = ImageUtils.rotate(bmp, it.rotation.toFloat())
                val img = JPEGFactory.createFromImage(doc, bmp, quality.jpeg)
                val landscapeImg = bmp.width > bmp.height
                var pw: Float
                var ph: Float
                if (size == Size.ORIGINAL) {
                    // asl nisbat: uzun tomoni A4 uzunligiga teng
                    val k = 841.89f / maxOf(bmp.width, bmp.height)
                    pw = bmp.width * k; ph = bmp.height * k
                } else {
                    pw = size.w; ph = size.h
                    val land = when (orient) { Orient.LANDSCAPE -> true; Orient.PORTRAIT -> false; Orient.AUTO -> landscapeImg }
                    if (land) { val t = pw; pw = ph; ph = t }
                }
                val m = marginMm * 72f / 25.4f
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val aw = (pw - 2 * m).coerceAtLeast(10f)
                val ah = (ph - 2 * m).coerceAtLeast(10f)
                val k = minOf(aw / bmp.width, ah / bmp.height)
                val dw = bmp.width * k
                val dh = bmp.height * k
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, (pw - dw) / 2, (ph - dh) / 2, dw, dh) }
                bmp.recycle()
                onProgress(i + 1, items.size)
            }
            require(doc.numberOfPages > 0) { "no images" }
            out.parentFile?.mkdirs()
            out.outputStream().buffered().use { doc.save(it) }
        }
        out
    }
}
