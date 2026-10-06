package uz.kitobskaner.ocr

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class Box(val l: Int, val t: Int, val r: Int, val b: Int) {
    val w get() = r - l
    val h get() = b - t
    val cx get() = (l + r) / 2f
    val area get() = w.toLong() * h
}

class HWord(var text: String, val box: Box, val conf: Int) {
    var bold = false
    var italic = false
    /** Shtrix qalinligi / x-balandlik nisbati (qalin matnni aniqlash uchun). */
    var stroke: Float = Float.NaN
}

class HLine(
    var box: Box,
    val size: Float,
    val ascenders: Float,
    val descenders: Float,
    val baselineSlope: Float,
    val baselineOffset: Float,
) {
    val words = ArrayList<HWord>()
    /** So'zlar olib tashlangandan keyin satr chegarasini qayta hisoblash. */
    fun refit() {
        if (words.isEmpty()) return
        box = Box(words.minOf { it.box.l }, words.minOf { it.box.t }, words.maxOf { it.box.r }, words.maxOf { it.box.b })
    }

    /** Satr bezak belgisi (♣, ✓, •) bilan boshlanadi — yangi xatboshi/ro'yxat bandi. */
    var bullet = false
    val text: String get() = words.joinToString(" ") { it.text }
    val xHeight: Float
        get() {
            val xh = size - ascenders - descenders
            return if (xh > 2f) xh else (box.h * 0.5f).coerceAtLeast(2f)
        }

    fun baselineAt(x: Int): Int = (box.b + baselineOffset + baselineSlope * (x - box.l)).toInt()
}

class HPar(val box: Box) {
    val lines = ArrayList<HLine>()
}

class HBlock(val box: Box, val isImage: Boolean) {
    val pars = ArrayList<HPar>()
}

class HPage(val width: Int, val height: Int) {
    val blocks = ArrayList<HBlock>()
}

object HocrParser {

    private val LINE_CLASSES = setOf("ocr_line", "ocr_header", "ocr_textfloat", "ocr_caption")

    fun parse(hocr: String, width: Int, height: Int): HPage {
        val page = HPage(width, height)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(hocr))

        var block: HBlock? = null
        var par: HPar? = null
        var line: HLine? = null
        var word: HWord? = null
        var wordDepth = -1
        val text = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val cls = parser.getAttributeValue(null, "class")
                    val title = parser.getAttributeValue(null, "title") ?: ""
                    when {
                        cls == "ocr_carea" -> {
                            block = HBlock(bbox(title) ?: Box(0, 0, width, height), false).also { page.blocks += it }
                            par = null; line = null
                        }
                        cls == "ocr_photo" || cls == "ocr_image" -> {
                            bbox(title)?.let { page.blocks += HBlock(it, true) }
                            block = null; par = null; line = null
                        }
                        cls == "ocr_par" -> {
                            val b = block ?: HBlock(Box(0, 0, width, height), false).also { page.blocks += it; block = it }
                            par = HPar(bbox(title) ?: b.box).also { b.pars += it }
                            line = null
                        }
                        cls in LINE_CLASSES -> {
                            val b = block ?: HBlock(Box(0, 0, width, height), false).also { page.blocks += it; block = it }
                            val p = par ?: HPar(b.box).also { b.pars += it; par = it }
                            val props = props(title)
                            val box = bbox(title) ?: p.box
                            val baseline = props["baseline"]?.split(' ')?.mapNotNull { it.toFloatOrNull() }
                            line = HLine(
                                box = box,
                                size = props["x_size"]?.toFloatOrNull() ?: box.h.toFloat(),
                                ascenders = props["x_ascenders"]?.toFloatOrNull() ?: 0f,
                                descenders = props["x_descenders"]?.toFloatOrNull() ?: 0f,
                                baselineSlope = baseline?.getOrNull(0) ?: 0f,
                                baselineOffset = baseline?.getOrNull(1) ?: 0f,
                            ).also { p.lines += it }
                        }
                        cls == "ocrx_word" -> {
                            val box = bbox(title)
                            val conf = props(title)["x_wconf"]?.toIntOrNull() ?: 0
                            if (box != null) {
                                word = HWord("", box, conf)
                                wordDepth = parser.depth
                                text.setLength(0)
                            }
                        }
                        word != null && parser.name == "strong" -> word!!.bold = true
                        word != null && parser.name == "em" -> word!!.italic = true
                    }
                }
                XmlPullParser.TEXT -> if (word != null) text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (word != null && parser.depth == wordDepth) {
                        val w = word!!
                        w.text = text.toString().trim()
                        if (w.text.isNotEmpty()) {
                            val l = line ?: run {
                                val b = block ?: HBlock(Box(0, 0, width, height), false).also { page.blocks += it; block = it }
                                val p = par ?: HPar(b.box).also { b.pars += it; par = it }
                                HLine(w.box, w.box.h.toFloat(), 0f, 0f, 0f, 0f).also { p.lines += it; line = it }
                            }
                            l.words += w
                        }
                        word = null
                        wordDepth = -1
                    }
                }
            }
            event = parser.next()
        }
        // bo'sh elementlarni tozalash
        page.blocks.forEach { b ->
            b.pars.forEach { p -> p.lines.removeAll { it.words.isEmpty() } }
            b.pars.removeAll { it.lines.isEmpty() }
        }
        page.blocks.removeAll { !it.isImage && it.pars.isEmpty() }
        return page
    }

    private fun props(title: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (part in title.split(';')) {
            val p = part.trim()
            val sp = p.indexOf(' ')
            if (sp > 0) map[p.substring(0, sp)] = p.substring(sp + 1).trim()
        }
        return map
    }

    private fun bbox(title: String): Box? {
        val v = props(title)["bbox"]?.split(' ')?.mapNotNull { it.toIntOrNull() } ?: return null
        if (v.size < 4) return null
        return Box(v[0], v[1], v[2], v[3])
    }
}
