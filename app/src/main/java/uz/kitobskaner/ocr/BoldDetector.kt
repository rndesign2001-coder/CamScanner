package uz.kitobskaner.ocr

import kotlin.math.max
import kotlin.math.min

/**
 * Qalin (bold) so'zlarni aniqlash.
 *
 * Tesseract LSTM shrift qalinligini bermaydi, shuning uchun har bir so'z uchun
 * vertikal shtrixlarning o'rtacha qalinligi binar tasvirdan o'lchanadi va satrning
 * x-balandligiga nisbatan normallanadi. Sahifadagi oddiy matn darajasidan
 * sezilarli qalin bo'lgan so'zlar qalin deb belgilanadi.
 */
object BoldDetector {

    /**
     * @param ink binar tasvir (1 = siyoh)
     * @param sensitivity 0..1 (yuqori — ko'proq so'z qalin deb topiladi)
     */
    fun detect(page: HPage, ink: ByteArray, width: Int, height: Int, sensitivity: Float) {
        val lines = page.blocks.flatMap { b -> b.pars.flatMap { it.lines } }
        if (lines.isEmpty()) return

        for (line in lines) {
            val xh = line.xHeight
            for (w in line.words) w.stroke = measure(w.box, ink, width, height, xh)
        }

        // mos yozuv: kamida 3 harfli, o'lchangan so'zlar (harflar soni bo'yicha vaznli)
        val samples = ArrayList<Pair<Float, Int>>()
        for (line in lines) for (w in line.words) {
            val letters = w.text.count { it.isLetter() }
            if (letters >= 3 && !w.stroke.isNaN()) samples += w.stroke to letters
        }
        if (samples.size < 4) {
            lines.forEach { l -> l.words.forEach { it.bold = false } }
            return
        }
        // 35-persentil: hatto sahifaning yarmi qalin bo'lsa ham oddiy matn darajasi
        val reference = weightedQuantile(samples, 0.35f)
        val factor = 1.42f - sensitivity.coerceIn(0f, 1f) * 0.30f   // 1.42 .. 1.12

        for (line in lines) {
            for (w in line.words) {
                val letters = w.text.count { it.isLetterOrDigit() }
                w.bold = !w.stroke.isNaN() && letters >= 2 && w.stroke > reference * factor
            }
            smooth(line)
        }
    }

    /** O'lchab bo'lmaydigan / qisqa so'zlar qo'shnilaridan meros oladi. */
    private fun smooth(line: HLine) {
        val ws = line.words
        val n = ws.size
        if (n == 0) return
        val decided = BooleanArray(n) { i ->
            val w = ws[i]
            !w.stroke.isNaN() && w.text.count { it.isLetterOrDigit() } >= 2
        }
        for (i in 0 until n) {
            if (decided[i]) continue
            val prev = (i - 1 downTo 0).firstOrNull { decided[it] }?.let { ws[it].bold }
            val next = (i + 1 until n).firstOrNull { decided[it] }?.let { ws[it].bold }
            ws[i].bold = when {
                prev != null && next != null -> prev && next
                prev != null -> prev
                next != null -> next
                else -> false
            }
        }
        // qalin so'zlar orasida qolib ketgan yolg'iz so'z ham qalin (o'lchov shovqini)
        for (i in 1 until n - 1) {
            val w = ws[i]
            if (!w.bold && ws[i - 1].bold && ws[i + 1].bold) w.bold = true
        }
        // oddiy so'zlar orasidagi yolg'iz, chegaraga yaqin "qalin" so'z — shovqin
        for (i in 1 until n - 1) {
            val w = ws[i]
            if (w.bold && !ws[i - 1].bold && !ws[i + 1].bold && w.text.length <= 3) w.bold = false
        }
    }

    private fun measure(box: Box, ink: ByteArray, width: Int, height: Int, xHeight: Float): Float {
        val l = max(0, box.l)
        val r = min(width, box.r)
        val t = max(0, box.t)
        val b = min(height, box.b)
        if (r - l < 3 || b - t < 3 || xHeight < 4f) return Float.NaN
        val maxRun = max(2, (xHeight * 0.55f).toInt())
        val runs = IntArray(4096)
        var count = 0
        val step = max(1, (b - t) / 48)
        for (y in t until b step step) {
            var x = l
            val base = y * width
            while (x < r) {
                if (ink[base + x].toInt() == 1) {
                    val start = x
                    while (x < r && ink[base + x].toInt() == 1) x++
                    val len = x - start
                    // gorizontal chiziqlar (т, е, ғ tepasi) va shovqin hisobga olinmaydi
                    if (len in 1..maxRun && count < runs.size) runs[count++] = len
                } else x++
            }
        }
        if (count < 8) return Float.NaN
        runs.sort(0, count)
        val from = (count * 0.2f).toInt()
        val to = max(from + 1, (count * 0.8f).toInt())
        var sum = 0
        for (i in from until to) sum += runs[i]
        val avg = sum.toFloat() / (to - from)
        return avg / xHeight
    }

    private fun weightedQuantile(samples: List<Pair<Float, Int>>, q: Float): Float {
        val sorted = samples.sortedBy { it.first }
        val total = sorted.sumOf { it.second }
        var acc = 0
        val target = total * q
        for ((v, w) in sorted) {
            acc += w
            if (acc >= target) return v
        }
        return sorted.last().first
    }
}
