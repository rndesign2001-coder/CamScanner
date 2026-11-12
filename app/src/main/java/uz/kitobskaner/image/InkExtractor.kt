package uz.kitobskaner.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class InkColor { ORIGINAL, BLUE, BLACK, RED, PURPLE }

/**
 * Rasmdan imzo yoki muhrni ajratib oladi: qog'oz foni shaffof bo'ladi, faqat siyoh qoladi.
 *
 * Har bir piksel qog'oz yorqinligiga (mahalliy fon) nisbatan qanchalik to'qligi va rangliligi
 * bo'yicha baholanadi. Muhrlar (ko'k/binafsha/qizil) uchun rang to'yinganligi ham hisobga olinadi,
 * shuning uchun ustiga tushgan qora matn kamroq qo'shiladi.
 */
object InkExtractor {

    /**
     * @param sensitivity 0..1 — yuqori qiymat xira chiziqlarni ham oladi
     * @param stampMode muhr rejimi: rangli siyohni afzal ko'radi, qora matnni susaytiradi
     * @param recolor siyohni bir xil toza rangga bo'yash (ORIGINAL — asl rangi)
     */
    fun extract(src: Bitmap, sensitivity: Float, stampMode: Boolean, recolor: InkColor): Bitmap {
        val w = src.width
        val h = src.height
        val gray = ImageProcessing.toGray(src)
        val bg = ImageProcessing.background(gray)
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val lo = 0.10f + (1f - sensitivity) * 0.22f   // shundan kam to'qlik — fon
        val hi = lo + 0.28f
        val out = IntArray(w * h)
        val target = when (recolor) {
            InkColor.BLUE -> Color.rgb(24, 52, 168)
            InkColor.BLACK -> Color.rgb(18, 18, 22)
            InkColor.RED -> Color.rgb(196, 28, 36)
            InkColor.PURPLE -> Color.rgb(92, 40, 160)
            InkColor.ORIGINAL -> 0
        }
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (i in 0 until w * h) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = (r * 77 + g * 150 + b * 29) shr 8
            val paper = (bg[i].toInt() and 0xFF).coerceAtLeast(1)
            val dark = (1f - l.toFloat() / paper).coerceIn(0f, 1f)
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val sat = if (mx == 0) 0f else (mx - mn).toFloat() / mx
            var ink = if (stampMode) {
                // rangli siyoh: to'yinganlik asosiy belgi; rangsiz qora matn susaytiriladi
                (sat * 1.25f - 0.12f).coerceIn(0f, 1f) * 0.75f + dark * (if (sat > 0.18f) 0.6f else 0.15f)
            } else {
                dark + sat * 0.25f * dark
            }
            ink = ((ink - lo) / (hi - lo)).coerceIn(0f, 1f)
            if (ink <= 0.02f) { out[i] = 0; continue }
            val a = (ink * ink * (3 - 2 * ink) * 255).roundToInt()
            val color = if (recolor == InkColor.ORIGINAL) {
                // fon ta'sirini olib tashlab, rangni to'yintiramiz
                val k = 255f / paper
                val rr = (r * k).toInt().coerceIn(0, 255)
                val gg = (g * k).toInt().coerceIn(0, 255)
                val bb = (b * k).toInt().coerceIn(0, 255)
                // shaffoflik bilan qo'shilganda xira ko'rinmasligi uchun to'qlashtiramiz
                Color.rgb((rr * 0.82f).toInt(), (gg * 0.82f).toInt(), (bb * 0.82f).toInt())
            } else target
            out[i] = (a shl 24) or (color and 0x00FFFFFF)
            if (a > 40) {
                val x = i % w
                val y = i / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        val full = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        if (maxX < 0) return full
        val pad = (max(w, h) * 0.02f).toInt()
        val x0 = max(0, minX - pad)
        val y0 = max(0, minY - pad)
        val x1 = min(w, maxX + pad + 1)
        val y1 = min(h, maxY + pad + 1)
        val cropped = Bitmap.createBitmap(full, x0, y0, x1 - x0, y1 - y0)
        if (cropped !== full) full.recycle()
        return cropped
    }
}
