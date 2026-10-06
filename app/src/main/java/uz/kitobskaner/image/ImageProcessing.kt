package uz.kitobskaner.image

import android.graphics.Bitmap
import uz.kitobskaner.data.ImageFilter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Kulrang tasvir: har bir piksel 0..255 (unsigned byte). */
class GrayImage(val width: Int, val height: Int, val data: ByteArray) {
    operator fun get(x: Int, y: Int): Int = data[y * width + x].toInt() and 0xFF
}

object ImageProcessing {

    fun toGray(bmp: Bitmap): GrayImage {
        val w = bmp.width
        val h = bmp.height
        val out = ByteArray(w * h)
        val row = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            val base = y * w
            for (x in 0 until w) {
                val c = row[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                out[base + x] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
            }
        }
        return GrayImage(w, h, out)
    }

    /**
     * Qog'oz fonini (yorug'lik notekisligi, soyalar) baholaydi.
     * Natija — har bir piksel uchun fon yorqinligi (to'liq o'lcham).
     */
    fun background(gray: GrayImage): ByteArray {
        val w = gray.width
        val h = gray.height
        val block = max(8, min(w, h) / 90)
        val gw = (w + block - 1) / block
        val gh = (h + block - 1) / block
        val grid = IntArray(gw * gh)
        // har bir blokdagi eng yorug' qiymat (matn piksellari tushib qoladi)
        for (gy in 0 until gh) {
            val y0 = gy * block
            val y1 = min(h, y0 + block)
            for (gx in 0 until gw) {
                val x0 = gx * block
                val x1 = min(w, x0 + block)
                var m = 0
                var y = y0
                while (y < y1) {
                    var i = y * w + x0
                    val e = y * w + x1
                    while (i < e) {
                        val v = gray.data[i].toInt() and 0xFF
                        if (v > m) m = v
                        i += 2
                    }
                    y += 2
                }
                grid[gy * gw + gx] = m
            }
        }
        var g = grid
        repeat(2) { g = maxFilter(g, gw, gh) }
        repeat(3) { g = boxBlur(g, gw, gh) }
        // katta qorong'i hududlar (rasmlar) haddan tashqari yorqinlashib ketmasin
        val sorted = g.copyOf().also { it.sort() }
        val paper = sorted[(sorted.size * 0.9).toInt().coerceIn(0, sorted.size - 1)]
        val floor = max(40, (paper * 0.55f).toInt())
        for (i in g.indices) if (g[i] < floor) g[i] = floor

        // bilinear kattalashtirish
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / block - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(gh - 1, y0 + 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / block - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(gw - 1, x0 + 1)
                val tx = fx - x0
                val a = g[y0 * gw + x0] * (1 - tx) + g[y0 * gw + x1] * tx
                val b = g[y1 * gw + x0] * (1 - tx) + g[y1 * gw + x1] * tx
                out[y * w + x] = (a * (1 - ty) + b * ty).roundToInt().coerceIn(1, 255).toByte()
            }
        }
        return out
    }

    private fun maxFilter(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    val v = src[yy * w + xx]
                    if (v > m) m = v
                }
            }
            out[y * w + x] = m
        }
        return out
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            var n = 0
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    s += src[yy * w + xx]; n++
                }
            }
            out[y * w + x] = s / n
        }
        return out
    }

    /** Fonni tekislaydi: qog'oz oq, matn to'q bo'ladi. Natija yangi massivda. */
    fun normalize(gray: GrayImage): GrayImage {
        val bg = background(gray)
        val out = ByteArray(gray.data.size)
        for (i in out.indices) {
            val v = gray.data[i].toInt() and 0xFF
            val b = bg[i].toInt() and 0xFF
            out[i] = min(255, v * 255 / b).toByte()
        }
        // kontrastni cho'zish
        val hist = histogram(out)
        val lo = percentile(hist, out.size, 0.005f)
        val hi = max(lo + 32, percentile(hist, out.size, 0.6f).coerceAtMost(250))
        val lut = ByteArray(256) { v ->
            (((v - lo) * 255f) / (hi - lo)).roundToInt().coerceIn(0, 255).toByte()
        }
        for (i in out.indices) out[i] = lut[out[i].toInt() and 0xFF]
        return GrayImage(gray.width, gray.height, out)
    }

    fun histogram(data: ByteArray): IntArray {
        val hist = IntArray(256)
        for (b in data) hist[b.toInt() and 0xFF]++
        return hist
    }

    private fun percentile(hist: IntArray, total: Int, p: Float): Int {
        val target = (total * p).toLong()
        var acc = 0L
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= target) return i
        }
        return 255
    }

    fun otsu(hist: IntArray): Int {
        var total = 0L
        var sum = 0.0
        for (i in 0..255) { total += hist[i]; sum += i.toDouble() * hist[i] }
        var sumB = 0.0
        var wB = 0L
        var best = 0.0
        var threshold = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0L) continue
            val wF = total - wB
            if (wF == 0L) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = t }
        }
        return threshold
    }

    /** 1 — siyoh (qora), 0 — fon. */
    fun binarize(norm: GrayImage): ByteArray {
        val t = otsu(histogram(norm.data)).coerceIn(60, 215)
        val out = ByteArray(norm.data.size)
        for (i in out.indices) if ((norm.data[i].toInt() and 0xFF) <= t) out[i] = 1
        return out
    }

    /** Skan PDF uchun rasm filtri. Kiruvchi bitmap qayta ishlatilishi mumkin. */
    fun applyFilter(src: Bitmap, filter: ImageFilter): Bitmap {
        if (filter == ImageFilter.ORIGINAL) return src
        val w = src.width
        val h = src.height
        val gray = toGray(src)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        when (filter) {
            ImageFilter.GRAY, ImageFilter.BW -> {
                val norm = normalize(gray)
                val t = if (filter == ImageFilter.BW) otsu(histogram(norm.data)).coerceIn(60, 215) else -1
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        var v = norm.data[y * w + x].toInt() and 0xFF
                        if (t >= 0) v = if (v <= t) 0 else 255
                        row[x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                    }
                    out.setPixels(row, 0, w, 0, y, w, 1)
                }
            }
            else -> { // ENHANCED — rangli, fon oqartirilgan
                val bg = background(gray)
                for (y in 0 until h) {
                    src.getPixels(row, 0, w, 0, y, w, 1)
                    for (x in 0 until w) {
                        val c = row[x]
                        val b = bg[y * w + x].toInt() and 0xFF
                        var r = ((c shr 16) and 0xFF) * 255 / b
                        var g = ((c shr 8) and 0xFF) * 255 / b
                        var bl = (c and 0xFF) * 255 / b
                        r = levels(r); g = levels(g); bl = levels(bl)
                        // biroz to'yinganlik
                        val l = (r * 77 + g * 150 + bl * 29) shr 8
                        r = (l + (r - l) * 5 / 4).coerceIn(0, 255)
                        g = (l + (g - l) * 5 / 4).coerceIn(0, 255)
                        bl = (l + (bl - l) * 5 / 4).coerceIn(0, 255)
                        row[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                    }
                    out.setPixels(row, 0, w, 0, y, w, 1)
                }
            }
        }
        return out
    }

    private fun levels(v: Int): Int = ((v - 18) * 255 / 222).coerceIn(0, 255)

    fun grayToBitmap(g: GrayImage): Bitmap {
        val out = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
        val row = IntArray(g.width)
        for (y in 0 until g.height) {
            for (x in 0 until g.width) {
                val v = g.data[y * g.width + x].toInt() and 0xFF
                row[x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            out.setPixels(row, 0, g.width, 0, y, g.width, 1)
        }
        return out
    }

    /**
     * Satrlar orasidagi o'rtacha masofani (px) qatorlar bo'yicha siyoh profili avtokorrelyatsiyasi
     * yordamida baholaydi. Matn topilmasa null.
     */
    fun linePitch(norm: GrayImage): Float? {
        val w = norm.width
        val h = norm.height
        if (h < 200 || w < 200) return null
        val x0 = w / 10
        val x1 = w - w / 10
        val prof = FloatArray(h)
        for (y in 0 until h) {
            var c = 0
            val base = y * w
            var x = x0
            while (x < x1) {
                if ((norm.data[base + x].toInt() and 0xFF) < 128) c++
                x += 2
            }
            prof[y] = c.toFloat()
        }
        var mean = 0f
        for (v in prof) mean += v
        mean /= h
        for (i in prof.indices) prof[i] -= mean
        val maxLag = min(320, h / 3)
        val ac = FloatArray(maxLag + 1)
        for (lag in 0..maxLag) {
            var s = 0f
            for (y in 0 until h - lag) s += prof[y] * prof[y + lag]
            ac[lag] = s / (h - lag)
        }
        if (ac[0] <= 0f) return null
        // birinchi minimumdan keyingi eng katta cho'qqi
        var lag = 1
        while (lag < maxLag && ac[lag] > 0f && ac[lag] >= ac[lag + 1]) lag++
        var best = -1
        var bestV = 0f
        for (l in max(lag, 10)..maxLag) if (ac[l] > bestV) { bestV = ac[l]; best = l }
        if (best < 0 || bestV / ac[0] < 0.12f) return null
        // garmonikani (2× qadam) tuzatish: yarim masofada ham kuchli cho'qqi bo'lsa, o'sha haqiqiy qadam
        repeat(2) {
            val half = best / 2
            if (half < 10) return@repeat
            var j = max(1, half - 3)
            for (l in max(1, half - 3)..min(maxLag, half + 3)) if (ac[l] > ac[j]) j = l
            if (ac[j] > 0.5f * bestV) { best = j; bestV = ac[j] }
        }
        // aniqroq: parabolik interpolyatsiya
        val p = if (best in 1 until maxLag) {
            val a = ac[best - 1]; val b = ac[best]; val c = ac[best + 1]
            val d = a - 2 * b + c
            if (d != 0f) best + 0.5f * (a - c) / d else best.toFloat()
        } else best.toFloat()
        return p.takeIf { it in 12f..300f }
    }
}
