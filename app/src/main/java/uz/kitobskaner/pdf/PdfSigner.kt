package uz.kitobskaner.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Imzo/muhr joylashuvi. Koordinatalar sahifaga nisbatan (0..1), chap-yuqori burchakdan;
 * [cx], [cy] — markaz, [w] — sahifa eniga nisbatan kenglik, [rotation] — gradus.
 */
data class Placement(
    val page: Int,
    val file: File,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val aspect: Float,
    val rotation: Float,
)

object PdfSigner {

    /** Imzo/muhrlarni PDF sahifalariga joylab, yangi faylga saqlaydi (asl matn va sifat o'zgarmaydi). */
    suspend fun apply(context: Context, src: Uri, placements: List<Placement>, out: File) = withContext(Dispatchers.Default) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(src) ?: throw IllegalArgumentException("open")
        val doc = input.use { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)) }
        try {
            val cache = HashMap<String, PDImageXObject>()
            for (p in placements) {
                if (p.page !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(p.page)
                val box = page.cropBox
                val img = cache.getOrPut(p.file.absolutePath) {
                    val bmp = BitmapFactory.decodeFile(p.file.absolutePath)
                    LosslessFactory.createFromImage(doc, bmp).also { bmp.recycle() }
                }
                // sahifa burilgan bo'lsa ham ko'rinadigan yo'nalishda joylashtiramiz
                val rot = page.rotation
                val viewW = if (rot % 180 == 0) box.width else box.height
                val viewH = if (rot % 180 == 0) box.height else box.width
                val w = p.w * viewW
                val h = w / p.aspect
                val vx = p.cx * viewW
                val vy = p.cy * viewH
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    // ko'rinish koordinatalaridan sahifa koordinatalariga
                    val base = when (rot) {
                        90 -> Matrix(0f, 1f, -1f, 0f, box.upperRightX, box.lowerLeftY)
                        180 -> Matrix(-1f, 0f, 0f, -1f, box.upperRightX, box.upperRightY)
                        270 -> Matrix(0f, -1f, 1f, 0f, box.lowerLeftX, box.upperRightY)
                        else -> Matrix(1f, 0f, 0f, 1f, box.lowerLeftX, box.lowerLeftY)
                    }
                    cs.transform(base)
                    // markazga ko'chib, burib, rasmni chizamiz (PDF'da Y yuqoriga o'sadi)
                    cs.transform(Matrix.getTranslateInstance(vx, viewH - vy))
                    cs.transform(Matrix.getRotateInstance(Math.toRadians(-p.rotation.toDouble()), 0f, 0f))
                    cs.drawImage(img, -w / 2f, -h / 2f, w, h)
                }
            }
            out.parentFile?.mkdirs()
            out.outputStream().buffered().use { doc.save(it) }
        } finally {
            doc.close()
        }
    }
}
