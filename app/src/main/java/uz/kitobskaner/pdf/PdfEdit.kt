package uz.kitobskaner.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF sahifalari va izohlari bilan ishlash (PdfBox): tartiblash, aylantirish, o'chirish,
 * ajratib olish/bo'lish, belgilash, chizish, matn va yopishqoq izoh qo'shish, matnni olish.
 * Har doim yangi fayl yaratiladi — asl PDF o'zgarmaydi.
 */
object PdfEdit {

    /** Natijadagi sahifa: asl tartib raqami va qo'shimcha burilish (90 ga karrali). */
    class PageSpec(val src: Int, val rot: Int)

    /** Izoh: koordinatalar ko'rinayotgan (burilgan) sahifaga nisbatan 0..1 oralig'ida. */
    class Annot(
        val page: Int,
        val kind: String, // highlight, underline, strike, text, note
        val u0: Float, val v0: Float, val u1: Float, val v1: Float,
        val text: String = "",
        val color: Int = 0xFFFFEB3B.toInt(),
    )

    private fun load(context: Context, uri: Uri): PDDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("open")
        return input.use { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)) }
    }

    /** Sahifalarni berilgan tartibda, burilish bilan saqlaydi (ro'yxatda yo'qlari o'chiriladi). */
    suspend fun rebuild(context: Context, uri: Uri, specs: List<PageSpec>, out: File) = withContext(Dispatchers.IO) {
        require(specs.isNotEmpty()) { "empty" }
        val doc = load(context, uri)
        try {
            val pages = doc.pages.toList()
            for (p in pages) doc.removePage(p)
            for (s in specs) {
                val p = pages.getOrNull(s.src) ?: continue
                if (s.rot % 360 != 0) p.rotation = ((p.rotation + s.rot) % 360 + 360) % 360
                doc.addPage(p)
            }
            out.parentFile?.mkdirs()
            out.outputStream().buffered().use { doc.save(it) }
        } finally {
            doc.close()
        }
        out
    }

    /** Har bir guruhni alohida PDF qilib saqlaydi ([outFor] — guruh tartib raqami bo'yicha fayl). */
    suspend fun split(context: Context, uri: Uri, groups: List<List<Int>>, outFor: (Int) -> File): List<File> = withContext(Dispatchers.IO) {
        val doc = load(context, uri)
        val res = ArrayList<File>()
        try {
            groups.forEachIndexed { gi, g ->
                PDDocument().use { nd ->
                    for (i in g) doc.pages.elementAtOrNull(i)?.let { nd.importPage(it) }
                    if (nd.numberOfPages > 0) {
                        val f = outFor(gi)
                        f.outputStream().buffered().use { nd.save(it) }
                        res += f
                    }
                }
            }
        } finally {
            doc.close()
        }
        res
    }

    /** Ko'rinish koordinatasini (0..1, burilgan sahifa) PDF foydalanuvchi fazosiga o'tkazadi. */
    private fun toPdf(page: PDPage, u: Float, v: Float): Pair<Float, Float> {
        val box = page.cropBox ?: page.mediaBox
        val w = box.width
        val h = box.height
        val (x, y) = when ((page.rotation % 360 + 360) % 360) {
            90 -> v * w to u * h
            180 -> (1 - u) * w to v * h
            270 -> (1 - v) * w to (1 - u) * h
            else -> u * w to (1 - v) * h
        }
        return box.lowerLeftX + x to box.lowerLeftY + y
    }

    suspend fun annotate(context: Context, uri: Uri, items: List<Annot>, out: File) = withContext(Dispatchers.IO) {
        val doc = load(context, uri)
        try {
            var font: PDType0Font? = null
            fun font(): PDType0Font = font ?: context.assets.open("fonts/NotoSans-Regular.ttf").use { PDType0Font.load(doc, it) }.also { font = it }
            for ((pi, list) in items.groupBy { it.page }) {
                val page = doc.pages.elementAtOrNull(pi) ?: continue
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (a in list) {
                        val (x0, y0) = toPdf(page, a.u0, a.v0)
                        val (x1, y1) = toPdf(page, a.u1, a.v1)
                        val l = minOf(x0, x1); val r = maxOf(x0, x1); val b = minOf(y0, y1); val t = maxOf(y0, y1)
                        val cr = ((a.color shr 16) and 255) / 255f; val cg = ((a.color shr 8) and 255) / 255f; val cb = (a.color and 255) / 255f
                        when (a.kind) {
                            "highlight" -> {
                                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.38f }
                                cs.saveGraphicsState()
                                cs.setGraphicsStateParameters(gs)
                                cs.setNonStrokingColor(cr, cg, cb)
                                cs.addRect(l, b, r - l, t - b)
                                cs.fill()
                                cs.restoreGraphicsState()
                            }
                            "underline", "strike" -> {
                                cs.saveGraphicsState()
                                cs.setStrokingColor(cr, cg, cb)
                                cs.setLineWidth(maxOf(1f, (t - b) * 0.08f))
                                // ko'rinishdagi gorizontal chiziq: sahifa burilgan bo'lsa ham to'g'ri yo'nalishda
                                val yy = if (a.kind == "underline") maxOf(a.v0, a.v1) else (a.v0 + a.v1) / 2
                                val (sx, sy) = toPdf(page, minOf(a.u0, a.u1), yy)
                                val (ex, ey) = toPdf(page, maxOf(a.u0, a.u1), yy)
                                cs.moveTo(sx, sy)
                                cs.lineTo(ex, ey)
                                cs.stroke()
                                cs.restoreGraphicsState()
                            }
                            "text" -> {
                                val f = font()
                                val size = ((page.cropBox ?: page.mediaBox).width / 36f).coerceIn(9f, 28f)
                                cs.beginText()
                                cs.setFont(f, size)
                                cs.setNonStrokingColor(cr, cg, cb)
                                cs.newLineAtOffset(x0, y0 - size)
                                a.text.split('\n').forEachIndexed { k, line ->
                                    if (k > 0) cs.newLineAtOffset(0f, -size * 1.25f)
                                    // shriftda yo'q belgilar tashlab ketiladi (aks holda PdfBox xato beradi)
                                    cs.showText(line.filter { ch -> try { f.encode(ch.toString()); true } catch (_: Exception) { false } })
                                }
                                cs.endText()
                            }
                            "note" -> {
                                // ko'rinadigan belgi + standart "yopishqoq izoh" annotatsiyasi
                                cs.saveGraphicsState()
                                cs.setNonStrokingColor(1f, 0.85f, 0.2f)
                                cs.addRect(x0, y0 - 14f, 14f, 14f)
                                cs.fill()
                                cs.restoreGraphicsState()
                                val ann = PDAnnotationText()
                                ann.contents = a.text
                                ann.rectangle = PDRectangle(x0, y0 - 18f, 18f, 18f)
                                ann.color = PDColor(floatArrayOf(1f, 0.85f, 0.2f), PDDeviceRGB.INSTANCE)
                                ann.titlePopup = "QulayOfis"
                                ann.name = PDAnnotationText.NAME_COMMENT
                                page.annotations.add(ann)
                            }
                        }
                    }
                }
            }
            out.parentFile?.mkdirs()
            out.outputStream().buffered().use { doc.save(it) }
        } finally {
            doc.close()
        }
        out
    }

    /** Har bir sahifaning matni (skanerlangan PDF'da bo'sh bo'ladi). */
    suspend fun pageTexts(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val doc = load(context, uri)
        try {
            val st = PDFTextStripper()
            (1..doc.numberOfPages).map { i ->
                st.startPage = i
                st.endPage = i
                try { st.getText(doc) } catch (_: Exception) { "" }
            }
        } finally {
            doc.close()
        }
    }
}
