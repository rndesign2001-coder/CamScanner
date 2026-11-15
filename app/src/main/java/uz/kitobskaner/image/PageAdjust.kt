package uz.kitobskaner.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import uz.kitobskaner.data.ImageFilter
import kotlin.math.hypot
import kotlin.math.max

/** Sahifa rasmini qo'lda tuzatish: 4 burchak bo'yicha perspektiva, yorqinlik/kontrast, filtr. */
object PageAdjust {

    /**
     * [q] — 8 ta qiymat (chap-yuqori, o'ng-yuqori, o'ng-past, chap-past), 0..1 nisbatda.
     * Natija — to'g'ri to'rtburchakka "yoyilgan" rasm.
     */
    fun warp(src: Bitmap, q: FloatArray): Bitmap {
        val p = FloatArray(8) { i -> q[i] * if (i % 2 == 0) src.width else src.height }
        fun d(a: Int, b: Int) = hypot(p[2 * a] - p[2 * b], p[2 * a + 1] - p[2 * b + 1])
        val w = max(d(0, 1), d(3, 2)).toInt().coerceIn(16, 6000)
        val h = max(d(0, 3), d(1, 2)).toInt().coerceIn(16, 6000)
        val m = Matrix()
        m.setPolyToPoly(p, 0, floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat()), 0, 4)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.WHITE)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** [brightness] -100..100, [contrast] 0.5..2.0. */
    fun adjust(src: Bitmap, brightness: Float, contrast: Float): Bitmap {
        if (brightness == 0f && contrast == 1f) return src
        val t = (1 - contrast) * 128f + brightness * 2.55f
        val cm = ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f))
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    fun full(src: Bitmap, q: FloatArray?, brightness: Float, contrast: Float, filter: ImageFilter): Bitmap {
        var b = if (q != null && !isIdentity(q)) warp(src, q) else src
        val a = adjust(b, brightness, contrast)
        if (a !== b && b !== src) b.recycle()
        b = a
        val f = ImageProcessing.applyFilter(b, filter)
        if (f !== b && b !== src) b.recycle()
        return f
    }

    fun isIdentity(q: FloatArray) = q.contentEquals(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))

    /**
     * Hujjat chegarasini taxminiy topadi: fondan yorqinroq katta soha, burchaklari
     * (x+y) va (x−y) ekstremumlari bo'yicha. Topilmasa — butun rasm.
     */
    fun detect(src: Bitmap): FloatArray {
        val small = ImageUtils.scaleDown(src, 240)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        val lum = IntArray(px.size) { val c = px[it]; ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000 }
        val hist = IntArray(256).also { hh -> lum.forEach { hh[it]++ } }
        val th = ImageProcessing.otsu(hist)
        var cnt = 0
        val sums = ArrayList<IntArray>()
        for (y in 0 until h) for (x in 0 until w) if (lum[y * w + x] > th) { cnt++; sums += intArrayOf(x, y) }
        if (cnt < w * h * 0.15 || cnt > w * h * 0.97) return floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        // ekstremumlarni shovqindan himoya qilish uchun 0.3% ichidan
        fun pick(key: (IntArray) -> Int, low: Boolean): IntArray {
            val sorted = sums.sortedBy(key)
            val k = (sorted.size * 0.003).toInt()
            return if (low) sorted[k] else sorted[sorted.size - 1 - k]
        }
        val tl = pick({ it[0] + it[1] }, true)
        val br = pick({ it[0] + it[1] }, false)
        val tr = pick({ it[0] - it[1] }, false)
        val bl = pick({ it[0] - it[1] }, true)
        val fw = (w - 1).toFloat(); val fh = (h - 1).toFloat()
        return floatArrayOf(tl[0] / fw, tl[1] / fh, tr[0] / fw, tr[1] / fh, br[0] / fw, br[1] / fh, bl[0] / fw, bl[1] / fh)
    }
}
