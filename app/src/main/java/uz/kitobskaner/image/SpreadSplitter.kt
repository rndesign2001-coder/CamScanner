package uz.kitobskaner.image

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Kitob yoyilmasini (ikki bet bitta suratda) ikki alohida sahifaga ajratadi.
 *
 * Bo'g'in (kitob o'rtasi) — markaz atrofidagi, matn chegaralari eng kam bo'lgan vertikal
 * yo'lak. Uning ichida soya (eng qorong'i ustun) bo'lsa, aynan o'sha joydan kesiladi.
 */
object SpreadSplitter {

    /** @return bo'g'in X koordinatasi (asl bitmap pikselida) */
    fun findGutter(bmp: Bitmap): Int {
        val k = min(1f, 700f / bmp.width)
        val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        val gray = ImageProcessing.toGray(small)
        if (small !== bmp) small.recycle()
        val w = gray.width
        val h = gray.height
        val y0 = (h * 0.08f).toInt()
        val y1 = (h * 0.92f).toInt()

        // har bir ustundagi vertikal o'tishlar (matn qirralari) va o'rtacha yorqinlik
        val edges = FloatArray(w)
        val bright = FloatArray(w)
        for (x in 0 until w) {
            var e = 0
            var b = 0L
            var prev = gray[x, y0]
            for (y in y0 + 1 until y1) {
                val v = gray[x, y]
                if (abs(v - prev) > 38) e++
                b += v
                prev = v
            }
            edges[x] = e.toFloat()
            bright[x] = b.toFloat() / (y1 - y0)
        }
        val se = smooth(edges, max(2, w / 120))
        val sb = smooth(bright, max(2, w / 150))

        val from = (w * 0.36f).toInt()
        val to = (w * 0.64f).toInt()
        var minE = Float.MAX_VALUE
        for (x in from until to) minE = min(minE, se[x])
        val median = se.sortedArray()[w / 2]
        val limit = minE + (median - minE) * 0.15f
        // markazga eng yaqin past-qirrali yo'lakni olamiz
        var bestStart = -1
        var bestEnd = -1
        var bestScore = Float.MAX_VALUE
        var x = from
        while (x < to) {
            if (se[x] <= limit) {
                val s = x
                while (x < to && se[x] <= limit) x++
                val e = x - 1
                val c = (s + e) / 2f
                val score = abs(c - w / 2f) - (e - s) * 0.5f
                if (score < bestScore) { bestScore = score; bestStart = s; bestEnd = e }
            } else x++
        }
        val gutterSmall = if (bestStart < 0) {
            w / 2
        } else {
            // yo'lak ichida soya bormi?
            var darkX = bestStart
            for (i in bestStart..bestEnd) if (sb[i] < sb[darkX]) darkX = i
            val avg = (bestStart..bestEnd).map { sb[it] }.average().toFloat()
            if (avg - sb[darkX] > 12f) darkX else (bestStart + bestEnd) / 2
        }
        return (gutterSmall / k).toInt().coerceIn(bmp.width / 4, bmp.width * 3 / 4)
    }

    /** @return chap va o'ng sahifalar */
    fun split(bmp: Bitmap, gutter: Int = findGutter(bmp)): Pair<Bitmap, Bitmap> {
        val overlap = (bmp.width * 0.004f).toInt()
        val left = Bitmap.createBitmap(bmp, 0, 0, min(bmp.width, gutter + overlap), bmp.height)
        val rx = max(0, gutter - overlap)
        val right = Bitmap.createBitmap(bmp, rx, 0, bmp.width - rx, bmp.height)
        return left to right
    }

    /** Surat yoyilmaga o'xshaydimi (eni bo'yidan katta). */
    fun looksLikeSpread(bmp: Bitmap) = bmp.width > bmp.height * 1.05f

    private fun smooth(a: FloatArray, r: Int): FloatArray {
        val out = FloatArray(a.size)
        for (i in a.indices) {
            var s = 0f
            var n = 0
            for (j in max(0, i - r)..min(a.size - 1, i + r)) { s += a[j]; n++ }
            out[i] = s / n
        }
        return out
    }
}
