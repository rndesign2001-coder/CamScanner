package uz.kitobskaner.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import uz.kitobskaner.data.AppSettings
import uz.kitobskaner.data.OcrPage
import uz.kitobskaner.image.ImageProcessing
import uz.kitobskaner.image.ImageUtils
import java.io.File
import java.io.OutputStream
import java.util.Calendar
import kotlin.math.max

/**
 * Skan qilingan sahifalardan PDF: oddiy (faqat rasm) yoki qidiriladigan
 * (rasm ustida ko'rinmas matn qatlami — nusxalash va qidirish mumkin).
 */
class ImagePdfWriter(private val context: Context, private val settings: AppSettings) {

    class Item(val image: File, val ocr: OcrPage?)

    fun write(title: String, items: List<Item>, searchable: Boolean, out: OutputStream, onProgress: (Float) -> Unit): Int {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir))
        try {
            doc.documentInformation = PDDocumentInformation().apply {
                this.title = title
                creator = "Kitob Skaner"
                producer = "Kitob Skaner"
                creationDate = Calendar.getInstance()
            }
            val font = if (searchable) {
                context.assets.open("fonts/NotoSans-Regular.ttf").use { PDType0Font.load(doc, it) }
            } else null
            val glyphCache = HashMap<Int, Boolean>()

            items.forEachIndexed { index, item ->
                val src = ImageUtils.decodeFile(item.image, settings.imageQuality.maxSide) ?: return@forEachIndexed
                val filtered = ImageProcessing.applyFilter(src, settings.imageFilter)
                val bytes = ImageUtils.jpegBytes(filtered, settings.imageQuality.jpeg)
                val iw = filtered.width
                val ih = filtered.height
                if (filtered != src) filtered.recycle()
                src.recycle()

                val img = JPEGFactory.createFromByteArray(doc, bytes)
                val pw = 595f
                val ph = pw * ih / iw
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, 0f, 0f, pw, ph)
                    val ocr = item.ocr
                    if (font != null && ocr != null && ocr.width > 0 && ocr.words.isNotEmpty()) {
                        val sx = pw / ocr.width
                        val sy = ph / ocr.height
                        cs.beginText()
                        cs.setRenderingMode(RenderingMode.NEITHER)
                        for (w in ocr.words) {
                            val text = sanitize(font, w.t, glyphCache)
                            if (text.isBlank()) continue
                            val fs = max(1f, w.s * sy * 0.8f)
                            val natural = try {
                                font.getStringWidth(text) / 1000f * fs
                            } catch (e: Exception) {
                                continue
                            }
                            if (natural <= 0f) continue
                            val target = w.w * sx
                            cs.setFont(font, fs)
                            cs.setHorizontalScaling((target / natural * 100f).coerceIn(10f, 1000f))
                            cs.setTextMatrix(Matrix.getTranslateInstance(w.x * sx, ph - w.b * sy))
                            try {
                                cs.showText("$text ")
                            } catch (e: Exception) {
                                // noyob belgi — o'tkazib yuboriladi
                            }
                        }
                        cs.endText()
                    }
                }
                onProgress((index + 1f) / items.size)
            }
            doc.save(out)
            return doc.numberOfPages
        } finally {
            doc.close()
        }
    }

    private fun sanitize(font: PDType0Font, s: String, cache: HashMap<Int, Boolean>): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ok = cache.getOrPut(cp) {
                try {
                    font.encode(String(Character.toChars(cp))); true
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) sb.appendCodePoint(cp) else if (cp == 0x02BB) sb.append('\'') else sb.append('?')
            i += Character.charCount(cp)
        }
        return sb.toString()
    }
}
