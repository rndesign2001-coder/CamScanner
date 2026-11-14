package uz.kitobskaner.office

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/**
 * Office hujjatlarini (DOCX, XLSX, PPTX, CSV) asl ko'rinishiga yaqin HTML'ga aylantiradi:
 * shriftlar, o'lchamlar, ranglar, tekislash, jadvallar (birlashgan kataklar bilan), rasmlar,
 * ro'yxatlar, Excel formatlari va slaydlardagi elementlarning joylashuvi saqlanadi.
 * HTML WebView'da ko'rsatiladi (kattalashtirish, qidirish, chop etish/PDF).
 */
class HtmlRenderer(
    /** Rasm baytlari → data: URI (kerak bo'lsa kichraytiradi). null — rasm tashlab ketiladi. */
    private val imageUri: (ByteArray, String) -> String?,
) {

    // ---------------------------------------------------------------- umumiy

    /** Parol bilan himoyalangan (shifrlangan) Office fayli. */
    class EncryptedException : Exception("encrypted")

    private fun readZip(input: InputStream): Map<String, ByteArray> {
        val buf = java.io.BufferedInputStream(input)
        buf.mark(8)
        val sig = ByteArray(4)
        val n = buf.read(sig)
        buf.reset()
        // OLE2 konteyner (D0 CF 11 E0): shifrlangan OOXML yoki eski binar format
        if (n == 4 && sig[0] == 0xD0.toByte() && sig[1] == 0xCF.toByte() && sig[2] == 0x11.toByte() && sig[3] == 0xE0.toByte()) throw EncryptedException()
        return readZipStream(buf)
    }

    private fun readZipStream(input: InputStream): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) {
                    val b = z.readBytes()
                    total += b.size
                    if (total > 400L * 1024 * 1024) throw IllegalStateException("too large")
                    out[e.name.removePrefix("/")] = b
                }
                z.closeEntry()
            }
        }
        return out
    }

    private fun parser(b: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(b.inputStream(), null)
    }

    private fun XmlPullParser.a(n: String): String? = getAttributeValue(null, n)

    /** Joriy START_TAG ichidagi barcha elementlarni o'tkazib yuboradi. */
    private fun XmlPullParser.skipTag() {
        var depth = 1
        while (depth > 0) {
            when (next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** Joriy element ichidagi bolalar uchun [onChild] chaqiriladi; element oxirida qaytadi. */
    private inline fun XmlPullParser.children(onChild: (String) -> Unit, onText: (String) -> Unit = {}) {
        val depth = depth
        while (true) {
            val ev = next()
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && this.depth == depth) return
            if (ev == XmlPullParser.START_TAG) onChild(name)
            else if (ev == XmlPullParser.TEXT) onText(text)
        }
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    private fun rels(files: Map<String, ByteArray>, path: String): Map<String, String> {
        val dir = path.substringBeforeLast('/', "")
        val relPath = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + path.substringAfterLast('/') + ".rels"
        val b = files[relPath] ?: return emptyMap()
        val out = HashMap<String, String>()
        val p = parser(b)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                val id = p.a("Id") ?: continue
                val t = p.a("Target") ?: continue
                if (p.a("TargetMode") == "External") { out[id] = t; continue }
                out[id] = resolve(dir, t)
            }
        }
        return out
    }

    private fun resolve(dir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = ArrayList(if (dir.isEmpty()) emptyList() else dir.split('/'))
        for (seg in target.split('/')) {
            when (seg) {
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                ".", "" -> {}
                else -> parts += seg
            }
        }
        return parts.joinToString("/")
    }

    private fun image(files: Map<String, ByteArray>, path: String?): String? {
        val b = path?.let { files[it] } ?: return null
        return imageUri(b, path)
    }

    private fun page(title: String, css: String, body: String, script: String = ""): String = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=6">
<title>${esc(title)}</title><style>
html,body{margin:0;padding:0;background:#E9EBF0;-webkit-text-size-adjust:100%}
body{font-family:'Calibri','Carlito','Noto Sans',sans-serif;color:#111}
@media print{body{background:#fff}.page,.slide-wrap{box-shadow:none!important;margin:0 auto!important}.tabs{display:none}}
$css
</style></head><body>$body<script>$script</script></body></html>"""

    // ---------------------------------------------------------------- DOCX

    private class RunStyle {
        var b = false; var i = false; var u = false; var strike = false
        var sizePt = 0f; var color: String? = null; var bg: String? = null; var font: String? = null
        var vert: String? = null; var caps = false
        fun copy() = RunStyle().also {
            it.b = b; it.i = i; it.u = u; it.strike = strike; it.sizePt = sizePt; it.color = color; it.bg = bg
            it.font = font; it.vert = vert; it.caps = caps
        }
        fun css(): String {
            val sb = StringBuilder()
            if (b) sb.append("font-weight:bold;")
            if (i) sb.append("font-style:italic;")
            val dec = listOfNotNull(if (u) "underline" else null, if (strike) "line-through" else null)
            if (dec.isNotEmpty()) sb.append("text-decoration:${dec.joinToString(" ")};")
            if (sizePt > 0) sb.append("font-size:${fmt(sizePt)}pt;")
            color?.let { sb.append("color:#$it;") }
            bg?.let { sb.append("background:$it;") }
            font?.let { sb.append("font-family:'${it.replace("'", "")}',sans-serif;") }
            if (caps) sb.append("text-transform:uppercase;")
            when (vert) { "superscript" -> sb.append("vertical-align:super;font-size:smaller;"); "subscript" -> sb.append("vertical-align:sub;font-size:smaller;") }
            return sb.toString()
        }
    }

    private class ParaStyle {
        var align: String? = null; var before = -1f; var after = -1f; var indLeft = 0f; var indFirst = 0f
        var line = 0f; var heading = 0; var run = RunStyle(); var shading: String? = null
        var numId: String? = null; var numLvl = 0
        fun copy() = ParaStyle().also {
            it.align = align; it.before = before; it.after = after; it.indLeft = indLeft; it.indFirst = indFirst
            it.line = line; it.heading = heading; it.run = run.copy(); it.shading = shading; it.numId = numId; it.numLvl = numLvl
        }
    }

    private class Docx(
        val files: Map<String, ByteArray>,
        val rels: Map<String, String>,
        val styles: Map<String, ParaStyle>,
        val defaults: ParaStyle,
        val numFmt: Map<String, Map<Int, String>>, // numId → (ilvl → numFmt)
        val counters: HashMap<String, IntArray> = HashMap(),
    )

    private val highlight = mapOf(
        "yellow" to "#FFFF00", "green" to "#00FF00", "cyan" to "#00FFFF", "magenta" to "#FF00FF", "blue" to "#0000FF",
        "red" to "#FF0000", "darkBlue" to "#000080", "darkCyan" to "#008080", "darkGreen" to "#008000",
        "darkMagenta" to "#800080", "darkRed" to "#800000", "darkYellow" to "#808000", "darkGray" to "#808080",
        "lightGray" to "#C0C0C0", "black" to "#000000",
    )

    private fun XmlPullParser.readRunProps(rs: RunStyle) {
        children({ n ->
            when (n) {
                "w:b" -> rs.b = a("w:val").let { it == null || (it != "0" && it != "false") }
                "w:i" -> rs.i = a("w:val").let { it == null || (it != "0" && it != "false") }
                "w:u" -> rs.u = a("w:val").let { it != null && it != "none" }
                "w:strike", "w:dstrike" -> rs.strike = a("w:val").let { it == null || it != "0" }
                "w:caps" -> rs.caps = a("w:val").let { it == null || it != "0" }
                "w:sz" -> a("w:val")?.toFloatOrNull()?.let { rs.sizePt = it / 2f }
                "w:color" -> a("w:val")?.takeIf { it.length == 6 }?.let { rs.color = it }
                "w:highlight" -> highlight[a("w:val")]?.let { rs.bg = it }
                "w:shd" -> a("w:fill")?.takeIf { it.length == 6 && it != "auto" }?.let { rs.bg = "#$it" }
                "w:rFonts" -> (a("w:ascii") ?: a("w:hAnsi"))?.let { rs.font = it }
                "w:vertAlign" -> rs.vert = a("w:val")
            }
            skipTag()
        })
    }

    private fun XmlPullParser.readParaProps(ps: ParaStyle, d: Docx?, numOut: Array<String?>?) {
        children({ n ->
            when (n) {
                "w:pStyle" -> {
                    val st = d?.styles?.get(a("w:val") ?: "")
                    if (st != null) {
                        val keepRun = st.run.copy()
                        ps.align = st.align ?: ps.align; if (st.before >= 0) ps.before = st.before; if (st.after >= 0) ps.after = st.after
                        ps.indLeft = st.indLeft; ps.indFirst = st.indFirst; ps.heading = st.heading; ps.line = st.line
                        ps.run = keepRun; ps.shading = st.shading
                        if (st.numId != null) { ps.numId = st.numId; ps.numLvl = st.numLvl }
                    }
                    skipTag()
                }
                "w:jc" -> { ps.align = when (a("w:val")) { "center" -> "center"; "right", "end" -> "right"; "both", "distribute" -> "justify"; else -> "left" }; skipTag() }
                "w:spacing" -> {
                    a("w:before")?.toFloatOrNull()?.let { ps.before = it / 20f }
                    a("w:after")?.toFloatOrNull()?.let { ps.after = it / 20f }
                    val line = a("w:line")?.toFloatOrNull()
                    if (line != null && (a("w:lineRule") == null || a("w:lineRule") == "auto")) ps.line = line / 240f
                    skipTag()
                }
                "w:ind" -> {
                    (a("w:left") ?: a("w:start"))?.toFloatOrNull()?.let { ps.indLeft = it / 20f }
                    a("w:firstLine")?.toFloatOrNull()?.let { ps.indFirst = it / 20f }
                    a("w:hanging")?.toFloatOrNull()?.let { ps.indFirst = -it / 20f }
                    skipTag()
                }
                "w:shd" -> { a("w:fill")?.takeIf { it.length == 6 && it != "auto" }?.let { ps.shading = "#$it" }; skipTag() }
                "w:outlineLvl" -> { a("w:val")?.toIntOrNull()?.let { ps.heading = it + 1 }; skipTag() }
                "w:numPr" -> {
                    var numId: String? = null
                    var lvl = 0
                    children({ c ->
                        when (c) { "w:numId" -> numId = a("w:val"); "w:ilvl" -> lvl = a("w:val")?.toIntOrNull() ?: 0 }
                        skipTag()
                    })
                    if (numId != null) { ps.numId = if (numId == "0") null else numId; ps.numLvl = lvl }
                    if (numOut != null && numId != null && numId != "0") { numOut[0] = numId; numOut[1] = lvl.toString() }
                }
                "w:rPr" -> readRunProps(ps.run)
                else -> skipTag()
            }
        })
    }

    private fun docxStyles(files: Map<String, ByteArray>): Pair<Map<String, ParaStyle>, ParaStyle> {
        val out = HashMap<String, ParaStyle>()
        val defaults = ParaStyle().apply { run.sizePt = 11f; after = 8f; line = 1.08f }
        val b = files["word/styles.xml"] ?: return out to defaults
        val p = parser(b)
        val basedOn = HashMap<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "w:docDefaults" -> p.children({ n ->
                    when (n) {
                        "w:rPrDefault" -> p.children({ m -> if (m == "w:rPr") p.readRunProps(defaults.run) else p.skipTag() })
                        "w:pPrDefault" -> p.children({ m -> if (m == "w:pPr") p.readParaProps(defaults, null, null) else p.skipTag() })
                        else -> p.skipTag()
                    }
                })
                "w:style" -> {
                    val id = p.a("w:styleId") ?: ""
                    val type = p.a("w:type")
                    val st = ParaStyle()
                    var nameV = ""
                    p.children({ n ->
                        when (n) {
                            "w:name" -> { nameV = p.a("w:val") ?: ""; p.skipTag() }
                            "w:basedOn" -> { p.a("w:val")?.let { basedOn[id] = it }; p.skipTag() }
                            "w:pPr" -> p.readParaProps(st, null, null)
                            "w:rPr" -> p.readRunProps(st.run)
                            else -> p.skipTag()
                        }
                    })
                    if (type == "paragraph" || type == "character") {
                        val lower = nameV.lowercase()
                        if (st.heading == 0) {
                            if (lower == "title") st.heading = 1
                            else Regex("heading ?(\\d)").find(lower)?.let { st.heading = it.groupValues[1].toInt() }
                        }
                        out[id] = st
                    }
                }
            }
        }
        // meros (basedOn) — bo'sh qiymatlarni ota uslubdan to'ldirish
        fun resolveStyle(id: String, seen: MutableSet<String>): ParaStyle? {
            val st = out[id] ?: return null
            val parent = basedOn[id]?.takeIf { seen.add(it) }?.let { resolveStyle(it, seen) } ?: return st
            if (st.align == null) st.align = parent.align
            if (st.before < 0) st.before = parent.before
            if (st.after < 0) st.after = parent.after
            if (st.run.sizePt == 0f) st.run.sizePt = parent.run.sizePt
            if (st.run.color == null) st.run.color = parent.run.color
            if (st.run.font == null) st.run.font = parent.run.font
            if (!st.run.b) st.run.b = parent.run.b
            if (!st.run.i) st.run.i = parent.run.i
            if (st.heading == 0) st.heading = parent.heading
            if (st.numId == null) { st.numId = parent.numId; st.numLvl = parent.numLvl }
            return st
        }
        out.keys.toList().forEach { resolveStyle(it, mutableSetOf(it)) }
        return out to defaults
    }

    private fun docxNumbering(files: Map<String, ByteArray>): Map<String, Map<Int, String>> {
        val b = files["word/numbering.xml"] ?: return emptyMap()
        val abstract = HashMap<String, HashMap<Int, String>>()
        val nums = HashMap<String, String>()
        val p = parser(b)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "w:abstractNum" -> {
                    val id = p.a("w:abstractNumId") ?: continue
                    val levels = HashMap<Int, String>()
                    p.children({ n ->
                        if (n == "w:lvl") {
                            val lvl = p.a("w:ilvl")?.toIntOrNull() ?: 0
                            p.children({ m -> if (m == "w:numFmt") levels[lvl] = p.a("w:val") ?: "bullet"; p.skipTag() })
                        } else p.skipTag()
                    })
                    abstract[id] = levels
                }
                "w:num" -> {
                    val id = p.a("w:numId") ?: continue
                    p.children({ n -> if (n == "w:abstractNumId") p.a("w:val")?.let { nums[id] = it }; p.skipTag() })
                }
            }
        }
        return nums.mapValues { (_, abs) -> abstract[abs] ?: emptyMap() }
    }

    fun docx(input: InputStream, title: String): String {
        val files = readZip(input)
        if (files.isEmpty() || files.containsKey("EncryptedPackage")) throw EncryptedException()
        val docPath = "word/document.xml"
        val xml = files[docPath] ?: throw IllegalArgumentException("not docx")
        val (styles, defaults) = docxStyles(files)
        val d = Docx(files, rels(files, docPath), styles, defaults, docxNumbering(files))
        val p = parser(xml)
        val body = StringBuilder()
        var pageW = 595f
        var margin = 72f
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "w:body") {
                p.children({ n ->
                    when (n) {
                        "w:p" -> body.append(docxPara(p, d))
                        "w:tbl" -> body.append(docxTable(p, d))
                        "w:sdt" -> p.children({ c -> if (c == "w:sdtContent") p.children({ cc ->
                            when (cc) { "w:p" -> body.append(docxPara(p, d)); "w:tbl" -> body.append(docxTable(p, d)); else -> p.skipTag() }
                        }) else p.skipTag() })
                        "w:sectPr" -> p.children({ c ->
                            when (c) {
                                "w:pgSz" -> p.a("w:w")?.toFloatOrNull()?.let { pageW = it / 20f }
                                "w:pgMar" -> p.a("w:left")?.toFloatOrNull()?.let { margin = it / 20f }
                            }
                            p.skipTag()
                        })
                        else -> p.skipTag()
                    }
                })
            }
        }
        val css = """
.page{background:#fff;max-width:${fmt(pageW)}pt;margin:12px auto;padding:${fmt(margin * 0.75f)}pt ${fmt(margin)}pt;box-sizing:border-box;
box-shadow:0 2px 10px rgba(0,0,0,.18);font-size:${fmt(defaults.run.sizePt.takeIf { it > 0 } ?: 11f)}pt;line-height:1.25;overflow-wrap:break-word}
@media (max-width:640px){.page{padding:18pt 16pt;margin:8px}}
p{margin:0}
table.t{border-collapse:collapse;margin:6pt 0;max-width:100%}
table.t td{border:1px solid #999;padding:3pt 5pt;vertical-align:top}
img{max-width:100%;height:auto}
.pb{border:none;border-top:1px dashed #bbb;margin:14pt 0}
"""
        return page(title, css, "<div class=\"page\">$body</div>")
    }

    private fun docxPara(p: XmlPullParser, d: Docx): String {
        val ps = d.defaults.copy()
        val num = arrayOf<String?>(null, null)
        val content = StringBuilder()
        p.children({ n ->
            when (n) {
                "w:pPr" -> p.readParaProps(ps, d, num)
                "w:r" -> content.append(docxRun(p, d, ps.run))
                "w:hyperlink" -> {
                    val href = p.a("r:id")?.let { d.rels[it] }
                    val inner = StringBuilder()
                    p.children({ c -> if (c == "w:r") inner.append(docxRun(p, d, ps.run)) else p.skipTag() })
                    content.append(if (href != null) "<a href=\"${esc(href)}\">$inner</a>" else inner)
                }
                "w:ins", "w:smartTag", "w:fldSimple", "w:customXml" -> p.children({ c -> if (c == "w:r") content.append(docxRun(p, d, ps.run)) else p.skipTag() })
                else -> p.skipTag()
            }
        })
        val css = StringBuilder()
        ps.align?.let { css.append("text-align:$it;") }
        css.append("margin-top:${fmt(maxOf(0f, ps.before))}pt;margin-bottom:${fmt(if (ps.after < 0) 0f else ps.after)}pt;")
        if (ps.indLeft != 0f) css.append("margin-left:${fmt(ps.indLeft)}pt;")
        if (ps.indFirst != 0f) css.append("text-indent:${fmt(ps.indFirst)}pt;")
        if (ps.line > 0f) css.append("line-height:${fmt(ps.line * 1.15f)};")
        ps.shading?.let { css.append("background:$it;") }
        val runCss = ps.run.css()
        // ro'yxat belgisi
        var prefix = ""
        val numId = num[0] ?: ps.numId
        if (numId != null) {
            val lvl = num[1]?.toIntOrNull() ?: ps.numLvl
            val fmtName = d.numFmt[numId]?.get(lvl) ?: "bullet"
            val cnt = d.counters.getOrPut(numId) { IntArray(10) }
            cnt[lvl]++
            for (i in lvl + 1 until cnt.size) cnt[i] = 0
            prefix = when (fmtName) {
                "decimal" -> "${cnt[lvl]}."
                "lowerLetter" -> "${'a' + (cnt[lvl] - 1) % 26})"
                "upperLetter" -> "${'A' + (cnt[lvl] - 1) % 26}."
                "lowerRoman" -> roman(cnt[lvl]).lowercase() + "."
                "upperRoman" -> roman(cnt[lvl]) + "."
                "none" -> ""
                else -> if (lvl % 2 == 0) "•" else "◦"
            }
            if (ps.indLeft == 0f) css.append("margin-left:${18 * (lvl + 1)}pt;")
            css.append("text-indent:-14pt;")
            prefix = "<span style=\"display:inline-block;min-width:14pt\">$prefix</span>"
        }
        val body = if (content.isEmpty()) "&nbsp;" else content.toString()
        val tag = if (ps.heading in 1..6) "h${ps.heading}" else "p"
        val headingCss = if (ps.heading in 1..6) "font-weight:normal;" else ""
        return "<$tag style=\"$css$headingCss$runCss\">$prefix$body</$tag>"
    }

    private fun roman(n: Int): String {
        val v = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val s = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var x = n; val sb = StringBuilder()
        for (i in v.indices) while (x >= v[i]) { sb.append(s[i]); x -= v[i] }
        return sb.toString()
    }

    private fun docxRun(p: XmlPullParser, d: Docx, base: RunStyle): String {
        val rs = base.copy()
        // paragraf uslubidagi run xususiyatlari allaqachon <p> da; bu yerda faqat farqlar
        val inherited = base.css()
        val sb = StringBuilder()
        p.children({ n ->
            when (n) {
                "w:rPr" -> {
                    val st = RunStyle().also { it.b = rs.b; it.i = rs.i; it.sizePt = rs.sizePt; it.color = rs.color; it.font = rs.font }
                    p.readRunProps(rs)
                    @Suppress("UNUSED_VARIABLE") val unused = st
                }
                "w:t" -> {
                    val t = StringBuilder()
                    p.children({ p.skipTag() }, { t.append(it) })
                    sb.append(esc(t.toString()).replace("  ", " &nbsp;"))
                }
                "w:tab" -> { sb.append("<span style=\"display:inline-block;width:36pt\"></span>"); p.skipTag() }
                "w:br" -> { if (p.a("w:type") == "page") sb.append("<hr class=\"pb\">") else sb.append("<br>"); p.skipTag() }
                "w:cr" -> { sb.append("<br>"); p.skipTag() }
                "w:noBreakHyphen" -> { sb.append("&#8209;"); p.skipTag() }
                "w:sym" -> { p.a("w:char")?.toIntOrNull(16)?.let { sb.append("&#$it;") }; p.skipTag() }
                "w:drawing", "w:pict", "mc:AlternateContent" -> sb.append(docxDrawing(p, d))
                else -> p.skipTag()
            }
        })
        if (sb.isEmpty()) return ""
        val css = rs.css()
        return if (css == inherited) sb.toString() else "<span style=\"$css\">$sb</span>"
    }

    /** Rasm: a:blip r:embed (DrawingML) yoki v:imagedata r:id (VML); o'lcham wp:extent dan. */
    private fun docxDrawing(p: XmlPullParser, d: Docx): String {
        var rid: String? = null
        var cx = 0f
        var cy = 0f
        var fallbackDone = false
        val depth = p.depth
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && p.depth == depth) break
            if (ev != XmlPullParser.START_TAG) continue
            when (p.name) {
                "mc:Fallback" -> if (rid != null) { p.skipTag(); fallbackDone = true }
                "wp:extent" -> if (cx == 0f) { cx = (p.a("cx")?.toFloatOrNull() ?: 0f) / 12700f; cy = (p.a("cy")?.toFloatOrNull() ?: 0f) / 12700f }
                "a:blip" -> if (rid == null) rid = p.a("r:embed") ?: p.a("r:link")
                "v:imagedata" -> if (rid == null) rid = p.a("r:id")
            }
        }
        @Suppress("UNUSED_VARIABLE") val f = fallbackDone
        val src = image(d.files, rid?.let { d.rels[it] }) ?: return ""
        val w = if (cx > 0) "width:${fmt(cx)}pt;" else ""
        return "<img src=\"$src\" style=\"$w max-width:100%;height:auto;vertical-align:middle\">"
    }

    private var tableDepth = 0

    private fun docxTable(p: XmlPullParser, d: Docx): String {
        if (tableDepth > 8) { p.skipTag(); return "" }
        tableDepth++
        try { return docxTableInner(p, d) } finally { tableDepth-- }
    }

    private fun docxTableInner(p: XmlPullParser, d: Docx): String {
        val sb = StringBuilder("<div style=\"overflow-x:auto\"><table class=\"t\">")
        var borders = true
        p.children({ n ->
            when (n) {
                "w:tblPr" -> p.children({ c ->
                    if (c == "w:tblBorders") {
                        var any = false
                        p.children({ b -> if (p.a("w:val").let { it != null && it != "nil" && it != "none" }) any = true; p.skipTag() })
                        borders = any
                    } else p.skipTag()
                })
                "w:tr" -> {
                    sb.append("<tr>")
                    p.children({ c ->
                        if (c == "w:tc") {
                            var span = 1
                            var merge: String? = null
                            var fill: String? = null
                            var width = 0f
                            val cell = StringBuilder()
                            p.children({ cc ->
                                when (cc) {
                                    "w:tcPr" -> p.children({ x ->
                                        when (x) {
                                            "w:gridSpan" -> span = p.a("w:val")?.toIntOrNull() ?: 1
                                            "w:vMerge" -> merge = p.a("w:val") ?: "continue"
                                            "w:shd" -> fill = p.a("w:fill")?.takeIf { it.length == 6 && it != "auto" }
                                            "w:tcW" -> if (p.a("w:type") == "dxa") width = (p.a("w:w")?.toFloatOrNull() ?: 0f) / 20f
                                        }
                                        p.skipTag()
                                    })
                                    "w:p" -> cell.append(docxPara(p, d))
                                    "w:tbl" -> cell.append(docxTable(p, d))
                                    else -> p.skipTag()
                                }
                            })
                            if (merge == "continue") {
                                sb.append("<td style=\"border-top:none;${if (!borders) "border:none;" else ""}\"></td>")
                            } else {
                                val st = StringBuilder()
                                fill?.let { st.append("background:#$it;") }
                                if (width > 0) st.append("width:${fmt(width)}pt;")
                                if (!borders) st.append("border:none;")
                                sb.append("<td${if (span > 1) " colspan=\"$span\"" else ""} style=\"$st\">$cell</td>")
                            }
                        } else p.skipTag()
                    })
                    sb.append("</tr>")
                }
                else -> p.skipTag()
            }
        })
        sb.append("</table></div>")
        return sb.toString()
    }

    // ---------------------------------------------------------------- XLSX

    private class CellXf(val font: Int, val fill: Int, val numFmt: Int, val hAlign: String?, val wrap: Boolean, val border: Int)
    private class XFont(val b: Boolean, val i: Boolean, val u: Boolean, val size: Float, val color: String?)

    fun xlsx(input: InputStream, title: String): String {
        val files = readZip(input)
        val shared = ArrayList<String>()
        files["xl/sharedStrings.xml"]?.let { b ->
            val p = parser(b)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "si") {
                    val sb = StringBuilder()
                    var inT = false
                    p.children({ n ->
                        when (n) {
                            "t" -> { p.children({ p.skipTag() }, { sb.append(it) }) }
                            "r" -> p.children({ m -> if (m == "t") p.children({ p.skipTag() }, { sb.append(it) }) else p.skipTag() })
                            else -> p.skipTag()
                        }
                    })
                    @Suppress("UNUSED_VARIABLE") val x = inT
                    shared += sb.toString()
                }
            }
        }
        // uslublar
        val fonts = ArrayList<XFont>()
        val fills = ArrayList<String?>()
        val borders = ArrayList<Boolean>()
        val xfs = ArrayList<CellXf>()
        val customFmt = HashMap<Int, String>()
        files["xl/styles.xml"]?.let { b ->
            val p = parser(b)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name) {
                    "numFmt" -> { p.a("numFmtId")?.toIntOrNull()?.let { customFmt[it] = p.a("formatCode") ?: "" } }
                    "fonts" -> p.children({ n ->
                        if (n == "font") {
                            var bb = false; var ii = false; var uu = false; var sz = 11f; var col: String? = null
                            p.children({ m ->
                                when (m) {
                                    "b" -> bb = p.a("val") != "0"
                                    "i" -> ii = p.a("val") != "0"
                                    "u" -> uu = p.a("val") != "none"
                                    "sz" -> sz = p.a("val")?.toFloatOrNull() ?: 11f
                                    "color" -> col = p.a("rgb")?.takeLast(6)
                                }
                                p.skipTag()
                            })
                            fonts += XFont(bb, ii, uu, sz, col)
                        } else p.skipTag()
                    })
                    "fills" -> p.children({ n ->
                        if (n == "fill") {
                            var c: String? = null
                            p.children({ m ->
                                if (m == "patternFill") {
                                    val solid = p.a("patternType") == "solid"
                                    p.children({ x -> if (x == "fgColor" && solid) c = p.a("rgb")?.takeLast(6); p.skipTag() })
                                } else p.skipTag()
                            })
                            fills += c
                        } else p.skipTag()
                    })
                    "borders" -> p.children({ n ->
                        if (n == "border") {
                            var any = false
                            p.children({ m -> if (p.a("style") != null) any = true; p.skipTag() })
                            borders += any
                        } else p.skipTag()
                    })
                    "cellXfs" -> p.children({ n ->
                        if (n == "xf") {
                            val f = p.a("fontId")?.toIntOrNull() ?: 0
                            val fi = p.a("fillId")?.toIntOrNull() ?: 0
                            val nf = p.a("numFmtId")?.toIntOrNull() ?: 0
                            val bo = p.a("borderId")?.toIntOrNull() ?: 0
                            var h: String? = null
                            var wrap = false
                            p.children({ m -> if (m == "alignment") { h = p.a("horizontal"); wrap = p.a("wrapText") == "1" }; p.skipTag() })
                            xfs += CellXf(f, fi, nf, h, wrap, bo)
                        } else p.skipTag()
                    })
                }
            }
        }
        // varaqlar
        val wbRels = rels(files, "xl/workbook.xml")
        val sheets = ArrayList<Pair<String, String>>()
        files["xl/workbook.xml"]?.let { b ->
            val p = parser(b)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                    val path = p.a("r:id")?.let { wbRels[it] } ?: continue
                    sheets += (p.a("name") ?: "Sheet") to path
                }
            }
        }
        val date1904 = files["xl/workbook.xml"]?.let { String(it).contains("date1904=\"1\"") } == true
        val tabs = StringBuilder("<div class=\"tabs\">")
        val body = StringBuilder()
        sheets.forEachIndexed { si, (name, path) ->
            tabs.append("<button onclick=\"show($si)\" id=\"tb$si\"${if (si == 0) " class=\"on\"" else ""}>${esc(name)}</button>")
            body.append("<div class=\"sheet\" id=\"sh$si\" style=\"display:${if (si == 0) "block" else "none"}\">")
            body.append(files[path]?.let { xlsxSheet(it, shared, fonts, fills, borders, xfs, customFmt, date1904) } ?: "")
            body.append("</div>")
        }
        tabs.append("</div>")
        val css = """
.tabs{position:sticky;top:0;z-index:5;display:flex;gap:6px;overflow-x:auto;padding:8px;background:#E9EBF0}
.tabs button{border:1px solid #c9ccd6;background:#fff;border-radius:16px;padding:6px 14px;font-size:14px;white-space:nowrap}
.tabs button.on{background:#1D6F42;color:#fff;border-color:#1D6F42}
.sheet{overflow:auto;background:#fff;margin:0 8px 12px;box-shadow:0 1px 6px rgba(0,0,0,.15)}
table.x{border-collapse:collapse;font-size:11pt;table-layout:fixed}
table.x td{border:1px solid #E1E3E8;padding:2px 5px;height:20px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
table.x td.w{white-space:normal}
table.x th{background:#F3F4F6;color:#555;font-weight:normal;font-size:12px;border:1px solid #D5D8DE;position:sticky;top:0;z-index:2}
table.x th.r{position:sticky;left:0;z-index:3;min-width:34px}
table.x td.b{border:1px solid #444}
"""
        val script = """function show(i){var s=document.querySelectorAll('.sheet');for(var k=0;k<s.length;k++){s[k].style.display=k==i?'block':'none';document.getElementById('tb'+k).className=k==i?'on':''}}"""
        val tabBar = if (sheets.size > 1) tabs.toString() else ""
        return page(title, css, tabBar + body, script)
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) { if (c.isLetter()) n = n * 26 + (c.uppercaseChar() - 'A' + 1) else break }
        return n - 1
    }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, 'A' + m); n = (n - 1) / 26 }
        return sb.toString()
    }

    private class XCell(val text: String, val css: String, val numeric: Boolean, val wrap: Boolean, val bordered: Boolean)

    private fun xlsxSheet(
        b: ByteArray, shared: List<String>, fonts: List<XFont>, fills: List<String?>, borders: List<Boolean>,
        xfs: List<CellXf>, customFmt: Map<Int, String>, date1904: Boolean,
    ): String {
        val p = parser(b)
        val cells = HashMap<Long, XCell>()
        val colW = HashMap<Int, Float>()
        val rowH = HashMap<Int, Float>()
        val merges = ArrayList<IntArray>() // r0,c0,r1,c1
        var maxR = -1
        var maxC = -1
        var rowIdx = -1
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "col" -> {
                    val min = p.a("min")?.toIntOrNull() ?: continue
                    val max = p.a("max")?.toIntOrNull() ?: min
                    val w = p.a("width")?.toFloatOrNull() ?: continue
                    val hidden = p.a("hidden") == "1"
                    for (c in min..minOf(max, min + 200)) colW[c - 1] = if (hidden) 0f else w
                }
                "row" -> {
                    rowIdx = (p.a("r")?.toIntOrNull()?.minus(1)) ?: (rowIdx + 1)
                    p.a("ht")?.toFloatOrNull()?.let { rowH[rowIdx] = it }
                }
                "c" -> {
                    val ref = p.a("r")
                    val col = ref?.let { colIndex(it) } ?: (maxC + 1)
                    val t = p.a("t")
                    val s = p.a("s")?.toIntOrNull() ?: 0
                    var v = ""
                    p.children({ n ->
                        when (n) {
                            "v" -> p.children({ p.skipTag() }, { v += it })
                            "is" -> p.children({ m ->
                                when (m) {
                                    "t" -> p.children({ p.skipTag() }, { v += it })
                                    "r" -> p.children({ x -> if (x == "t") p.children({ p.skipTag() }, { v += it }) else p.skipTag() })
                                    else -> p.skipTag()
                                }
                            })
                            else -> p.skipTag()
                        }
                    })
                    if (rowIdx < 0 || rowIdx > 20000 || col > 300) continue
                    val xf = xfs.getOrNull(s)
                    var numeric = false
                    val text = when (t) {
                        "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) } ?: v
                        "b" -> if (v == "1") "TRUE" else "FALSE"
                        "str", "inlineStr", "e" -> v
                        else -> {
                            val d = v.toDoubleOrNull()
                            if (d == null) v else { numeric = true; formatNumber(d, xf?.numFmt ?: 0, customFmt, date1904) }
                        }
                    }
                    if (text.isEmpty() && xf == null) continue
                    val css = StringBuilder()
                    xf?.let { x ->
                        fonts.getOrNull(x.font)?.let { f ->
                            if (f.b) css.append("font-weight:bold;")
                            if (f.i) css.append("font-style:italic;")
                            if (f.u) css.append("text-decoration:underline;")
                            if (f.size != 11f) css.append("font-size:${fmt(f.size)}pt;")
                            f.color?.takeIf { it != "000000" }?.let { css.append("color:#$it;") }
                        }
                        fills.getOrNull(x.fill)?.let { css.append("background:#$it;") }
                        val h = x.hAlign ?: if (numeric) "right" else null
                        h?.let { if (it != "general") css.append("text-align:${if (it == "centerContinuous") "center" else it};") }
                    }
                    if (xf?.hAlign == null && numeric) css.append("text-align:right;")
                    cells[rowIdx.toLong() * 1000 + col] = XCell(text, css.toString(), numeric, xf?.wrap == true, xf?.let { borders.getOrNull(it.border) } == true)
                    if (text.isNotEmpty() || css.isNotEmpty()) { if (rowIdx > maxR) maxR = rowIdx; if (col > maxC) maxC = col }
                }
                "mergeCell" -> {
                    val r = p.a("ref") ?: continue
                    val parts = r.split(':')
                    if (parts.size == 2) {
                        fun rc(s: String): IntArray {
                            val c = colIndex(s)
                            val row = s.dropWhile { it.isLetter() }.toIntOrNull()?.minus(1) ?: 0
                            return intArrayOf(row, c)
                        }
                        val a0 = rc(parts[0]); val a1 = rc(parts[1])
                        merges += intArrayOf(a0[0], a0[1], a1[0], a1[1])
                    }
                }
            }
        }
        if (maxR < 0) return "<p style=\"padding:16px;color:#777\">—</p>"
        maxR = minOf(maxR, 5000)
        maxC = minOf(maxC, 150)
        val covered = HashSet<Long>()
        val spanAt = HashMap<Long, IntArray>()
        for (m in merges) {
            if (m[0] > maxR || m[1] > maxC) continue
            spanAt[m[0].toLong() * 1000 + m[1]] = intArrayOf(m[2] - m[0] + 1, m[3] - m[1] + 1)
            for (r in m[0]..minOf(m[2], maxR)) for (c in m[1]..minOf(m[3], maxC)) if (r != m[0] || c != m[1]) covered += r.toLong() * 1000 + c
        }
        val sb = StringBuilder("<table class=\"x\"><colgroup><col style=\"width:38px\">")
        for (c in 0..maxC) {
            val w = colW[c] ?: 8.43f
            sb.append("<col style=\"width:${(w * 7 + 5).roundToInt()}px\">")
        }
        sb.append("</colgroup><tr><th class=\"r\"></th>")
        for (c in 0..maxC) sb.append("<th>${colName(c)}</th>")
        sb.append("</tr>")
        for (r in 0..maxR) {
            val h = rowH[r]?.let { " style=\"height:${(it * 1.33f).roundToInt()}px\"" } ?: ""
            sb.append("<tr$h><th class=\"r\">${r + 1}</th>")
            for (c in 0..maxC) {
                val key = r.toLong() * 1000 + c
                if (key in covered) continue
                val cell = cells[key]
                val span = spanAt[key]
                val attrs = StringBuilder()
                if (span != null) {
                    if (span[0] > 1) attrs.append(" rowspan=\"${span[0]}\"")
                    if (span[1] > 1) attrs.append(" colspan=\"${span[1]}\"")
                }
                val cls = listOfNotNull(if (cell?.wrap == true) "w" else null, if (cell?.bordered == true) "b" else null)
                if (cls.isNotEmpty()) attrs.append(" class=\"${cls.joinToString(" ")}\"")
                if (cell != null && cell.css.isNotEmpty()) attrs.append(" style=\"${cell.css}\"")
                sb.append("<td$attrs>${cell?.let { esc(it.text) } ?: ""}</td>")
            }
            sb.append("</tr>")
        }
        sb.append("</table>")
        return sb.toString()
    }

    /** Excel son formatlari: foiz, sana/vaqt, kasr xonalari, minglik ajratgich. */
    private fun formatNumber(d: Double, id: Int, custom: Map<Int, String>, date1904: Boolean): String {
        val code = custom[id] ?: when (id) {
            1 -> "0"; 2 -> "0.00"; 3 -> "#,##0"; 4 -> "#,##0.00"; 9 -> "0%"; 10 -> "0.00%"; 11 -> "0.00E+00"
            14 -> "dd.mm.yyyy"; 15 -> "d-mmm-yy"; 16 -> "d-mmm"; 17 -> "mmm-yy"; 18 -> "h:mm AM/PM"; 19 -> "h:mm:ss AM/PM"
            20 -> "h:mm"; 21 -> "h:mm:ss"; 22 -> "dd.mm.yyyy h:mm"; 37, 38 -> "#,##0"; 39, 40 -> "#,##0.00"
            45 -> "mm:ss"; 46 -> "[h]:mm:ss"; 47 -> "mm:ss.0"; 49 -> "@"
            else -> "General"
        }
        val c = code.substringBefore(';').replace(Regex("\\[[^\\]]*\\]"), "").replace("\"", "").replace("\\", "")
        val lower = c.lowercase()
        val isDate = (lower.contains('y') || lower.contains('d') || (lower.contains('m') && (lower.contains('h') || lower.contains('s')))) && !lower.contains('0')
        if (isDate || lower.matches(Regex(".*[hs].*")) && !lower.contains('#') && !lower.contains('0')) {
            return try {
                val base = if (date1904) java.util.GregorianCalendar(1904, 0, 1) else java.util.GregorianCalendar(1899, 11, 30)
                val days = kotlin.math.floor(d).toInt()
                val secs = ((d - days) * 86400).roundToInt()
                base.add(java.util.Calendar.DAY_OF_MONTH, days)
                base.add(java.util.Calendar.SECOND, secs)
                val hasDate = lower.contains('y') || lower.contains('d')
                val hasTime = lower.contains('h') || lower.contains("ss")
                val pattern = (if (hasDate) "dd.MM.yyyy" else "") + (if (hasDate && hasTime) " " else "") + (if (hasTime) "HH:mm" + if (lower.contains("ss")) ":ss" else "" else "")
                java.text.SimpleDateFormat(pattern.ifEmpty { "dd.MM.yyyy" }, java.util.Locale.US).format(base.time)
            } catch (e: Exception) {
                d.toString()
            }
        }
        if (c.contains('%')) {
            val dec = c.substringAfter('.', "").count { it == '0' }
            return String.format(java.util.Locale.US, "%.${dec}f%%", d * 100)
        }
        if (c.contains('0') || c.contains('#')) {
            val dec = if (c.contains('.')) c.substringAfter('.').takeWhile { it == '0' || it == '#' }.count { it == '0' } else 0
            val grouped = c.contains(',')
            val f = java.text.DecimalFormat((if (grouped) "#,##0" else "0") + if (dec > 0) "." + "0".repeat(dec) else "",
                java.text.DecimalFormatSymbols(java.util.Locale.US).apply { groupingSeparator = ' ' })
            return f.format(d)
        }
        // General
        if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) return d.toLong().toString()
        val s = String.format(java.util.Locale.US, "%.10g", d).trimEnd('0').trimEnd('.')
        return if (s.contains('e')) d.toString() else s
    }

    // ---------------------------------------------------------------- PPTX

    private val schemeDefault = mapOf(
        "tx1" to "000000", "dk1" to "000000", "bg1" to "FFFFFF", "lt1" to "FFFFFF", "tx2" to "44546A", "dk2" to "44546A",
        "bg2" to "E7E6E6", "lt2" to "E7E6E6", "accent1" to "4472C4", "accent2" to "ED7D31", "accent3" to "A5A5A5",
        "accent4" to "FFC000", "accent5" to "5B9BD5", "accent6" to "70AD47", "hlink" to "0563C1",
    )

    private fun themeColors(files: Map<String, ByteArray>, themePath: String?): Map<String, String> {
        val out = HashMap(schemeDefault)
        val b = (themePath?.let { files[it] }) ?: files.keys.firstOrNull { it.startsWith("ppt/theme/theme") }?.let { files[it] } ?: return out
        val p = parser(b)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "a:clrScheme") {
                p.children({ n ->
                    val key = n.removePrefix("a:")
                    p.children({ m ->
                        val v = if (m == "a:srgbClr") p.a("val") else if (m == "a:sysClr") p.a("lastClr") else null
                        if (v != null) out[key] = v
                        p.skipTag()
                    })
                })
                break
            }
        }
        out["tx1"] = out["dk1"] ?: "000000"; out["bg1"] = out["lt1"] ?: "FFFFFF"
        out["tx2"] = out["dk2"] ?: "44546A"; out["bg2"] = out["lt2"] ?: "E7E6E6"
        return out
    }

    /** Rang elementini (srgbClr, schemeClr, sysClr, prstClr) o'qiydi, lumMod/lumOff/shade/tint taxminiy. */
    private fun XmlPullParser.colorElement(theme: Map<String, String>): String? {
        val n = name
        var col: String? = when (n) {
            "a:srgbClr" -> a("val")
            "a:schemeClr" -> theme[a("val") ?: ""]
            "a:sysClr" -> a("lastClr")
            "a:prstClr" -> when (a("val")) { "black" -> "000000"; "white" -> "FFFFFF"; "red" -> "FF0000"; "blue" -> "0000FF"; "green" -> "008000"; "yellow" -> "FFFF00"; "gray", "grey" -> "808080"; else -> null }
            else -> null
        }
        var lumMod = 1f; var lumOff = 0f; var shade = 1f; var tint = 1f
        children({ m ->
            val v = (a("val")?.toFloatOrNull() ?: 100000f) / 100000f
            when (m) { "a:lumMod" -> lumMod = v; "a:lumOff" -> lumOff = v; "a:shade" -> shade = v; "a:tint" -> tint = v }
            skipTag()
        })
        if (col != null && (lumMod != 1f || lumOff != 0f)) col = adjust(col, lumMod, lumOff)
        if (col != null && shade != 1f) col = adjust(col, shade, 0f)
        if (col != null && tint != 1f) col = adjust(col, tint, 1f - tint)
        return col
    }

    /** Joriy element (solidFill, fillRef, ...) ichidagi birinchi rangni o'qiydi. */
    private fun XmlPullParser.readColor(theme: Map<String, String>): String? {
        var c: String? = null
        children({ n ->
            if (n == "a:srgbClr" || n == "a:schemeClr" || n == "a:sysClr" || n == "a:prstClr") { val v = colorElement(theme); if (c == null) c = v }
            else skipTag()
        })
        return c
    }

    /** gradFill — birinchi va oxirgi to'xtash nuqtalari. */
    private fun XmlPullParser.readGradient(theme: Map<String, String>): List<String> {
        val out = ArrayList<String>()
        children({ n ->
            if (n == "a:gsLst") children({ g -> if (g == "a:gs") readColor(theme)?.let { out += it } else skipTag() })
            else skipTag()
        })
        return out
    }

    private fun adjust(hex: String, mod: Float, off: Float): String {
        val v = hex.toIntOrNull(16) ?: return hex
        fun ch(x: Int) = ((x / 255f * mod + off).coerceIn(0f, 1f) * 255).roundToInt()
        return String.format("%02X%02X%02X", ch((v shr 16) and 255), ch((v shr 8) and 255), ch(v and 255))
    }

    private class Xfrm(var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f, var rot: Float = 0f) {
        var flipH = false
        var flipV = false
    }

    /** Joy egasining maket/master dagi joylashuvi va matn uslubi. */
    private class PhInfo(val xf: Xfrm) {
        val sizes = FloatArray(9)
        var anchor: String? = null
        var align: String? = null
        var color: String? = null
        var bold = false
    }

    /** Master'ning title/body/other uslublari (daraja bo'yicha shrift o'lchami, rang, belgi). */
    private class TxStyles {
        val title = FloatArray(9) { 44f }
        val body = floatArrayOf(28f, 24f, 20f, 18f, 18f, 18f, 18f, 18f, 18f)
        val other = FloatArray(9) { 18f }
        var titleColor: String? = null
        var bodyColor: String? = null
        val bodyBullet = BooleanArray(9) { true }
        var titleBold = false
    }

    private fun XmlPullParser.readLevelStyles(sizes: FloatArray, bullets: BooleanArray?, theme: Map<String, String>, color: Array<String?>?, bold: BooleanArray? = null) {
        children({ n ->
            val m = Regex("a:lvl(\\d)pPr").matchEntire(n)
            if (m != null) {
                val lvl = m.groupValues[1].toInt() - 1
                var hasBullet: Boolean? = null
                children({ c ->
                    when (c) {
                        "a:defRPr" -> {
                            a("sz")?.toFloatOrNull()?.let { if (lvl in 0..8) sizes[lvl] = it / 100f }
                            if (lvl == 0 && a("b") == "1") bold?.set(0, true)
                            children({ d -> if (d == "a:solidFill" && lvl == 0) { val v = readColor(theme); if (color != null && v != null) color[0] = v } else skipTag() })
                        }
                        "a:buNone" -> { hasBullet = false; skipTag() }
                        "a:buChar", "a:buAutoNum" -> { hasBullet = true; skipTag() }
                        else -> skipTag()
                    }
                })
                if (bullets != null && hasBullet != null && lvl in 0..8) bullets[lvl] = hasBullet!!
            } else skipTag()
        })
    }

    private fun masterStyles(b: ByteArray, theme: Map<String, String>): TxStyles {
        val st = TxStyles()
        val p = parser(b)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "p:txStyles") {
                p.children({ n ->
                    when (n) {
                        "p:titleStyle" -> { val c = arrayOf<String?>(null); val bo = BooleanArray(1); p.readLevelStyles(st.title, null, theme, c, bo); st.titleColor = c[0]; st.titleBold = bo[0] }
                        "p:bodyStyle" -> { val c = arrayOf<String?>(null); p.readLevelStyles(st.body, st.bodyBullet, theme, c); st.bodyColor = c[0] }
                        "p:otherStyle" -> p.readLevelStyles(st.other, null, theme, null)
                        else -> p.skipTag()
                    }
                })
                break
            }
        }
        return st
    }

    private class Ctx(
        val files: Map<String, ByteArray>,
        val rels: Map<String, String>,
        val theme: Map<String, String>,
        val ph: Map<String, PhInfo>,
        val styles: TxStyles,
    )

    fun pptx(input: InputStream, title: String): String {
        val files = readZip(input)
        var sw = 9144000f
        var sh = 5143500f
        val presRels = rels(files, "ppt/presentation.xml")
        val order = ArrayList<String>()
        files["ppt/presentation.xml"]?.let { b ->
            val p = parser(b)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name) {
                    "p:sldSz" -> { sw = p.a("cx")?.toFloatOrNull() ?: sw; sh = p.a("cy")?.toFloatOrNull() ?: sh }
                    "p:sldId" -> p.a("r:id")?.let { presRels[it] }?.let { if (files.containsKey(it)) order += it }
                }
            }
        }
        if (order.isEmpty()) order += files.keys.filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val W = sw / 12700f
        val H = sh / 12700f
        val body = StringBuilder()
        val thumbs = StringBuilder()
        val masterCache = HashMap<String, Triple<TxStyles, Map<String, String>, String?>>()
        order.forEachIndexed { i, path ->
            val inner = StringBuilder()
            var bg = "FFFFFF"
            var bgImg: String? = null
            try {
                val xml = files.getValue(path)
                val slideRels = rels(files, path)
                val layoutPath = slideRels.values.firstOrNull { it.contains("slideLayouts/") }
                val layoutRels = layoutPath?.let { rels(files, it) } ?: emptyMap()
                val masterPath = layoutRels.values.firstOrNull { it.contains("slideMasters/") }
                val (styles, theme, masterBg) = masterCache.getOrPut(masterPath ?: "") {
                    val themePath = masterPath?.let { rels(files, it).values.firstOrNull { v -> v.contains("theme/") } }
                    val th = themeColors(files, themePath)
                    val st = masterPath?.let { files[it] }?.let { masterStyles(it, th) } ?: TxStyles()
                    Triple(st, th, masterPath?.let { files[it] }?.let { slideBg(it, th) })
                }
                val ph = HashMap<String, PhInfo>()
                layoutPath?.let { lp -> files[lp]?.let { collectPlaceholders(it, ph, theme) } }
                masterPath?.let { mp -> files[mp]?.let { b -> val m = HashMap<String, PhInfo>(); collectPlaceholders(b, m, theme); m.forEach { (k, v) -> ph.putIfAbsent(k, v) } } }
                bg = slideBg(xml, theme) ?: layoutPath?.let { files[it] }?.let { slideBg(it, theme) } ?: masterBg ?: "FFFFFF"
                if (bg.startsWith("img:")) { bgImg = bg.removePrefix("img:"); bg = "FFFFFF" }
                // maket va master'dagi bezak shakllari (logo, chiziqlar) — joy egalarisiz
                val ctxSlide = Ctx(files, slideRels, theme, ph, styles)
                if (showMasterShapes(xml)) {
                    masterPath?.let { mp -> files[mp]?.let { renderShapes(parser(it), inner, Ctx(files, rels(files, mp), theme, ph, styles), decorOnly = true) } }
                    layoutPath?.let { lp -> files[lp]?.let { renderShapes(parser(it), inner, Ctx(files, layoutRels, theme, ph, styles), decorOnly = true) } }
                }
                renderShapes(parser(xml), inner, ctxSlide)
            } catch (e: Throwable) {
                inner.append("<div class=\"sp\" style=\"left:20pt;top:20pt;width:400pt;height:40pt;color:#888\">⚠</div>")
            }
            val bgCss = if (bgImg != null) "background:#$bg url('$bgImg') center/cover no-repeat" else "background:#$bg"
            body.append("<div class=\"slide-wrap\" id=\"s$i\"><div class=\"no\">${i + 1} / ${order.size}</div><div class=\"slide\" style=\"width:${fmt(W)}pt;height:${fmt(H)}pt;$bgCss\">$inner</div></div>")
            thumbs.append("<button onclick=\"go($i)\">${i + 1}</button>")
        }
        val css = """
body{padding:10px 0 70px}
.slide-wrap{position:relative;margin:0 auto 16px;width:96%;box-shadow:0 3px 14px rgba(0,0,0,.22);border-radius:6px;overflow:hidden;background:#fff}
.slide{position:relative;transform-origin:0 0;overflow:hidden}
.no{position:absolute;right:8px;bottom:6px;z-index:99;font:12px sans-serif;color:#fff;background:rgba(0,0,0,.45);padding:2px 8px;border-radius:9px}
.sp{position:absolute;box-sizing:border-box;overflow:visible;display:flex;flex-direction:column}
.sp p{margin:0;line-height:1.12}
.sp img{width:100%;height:100%;object-fit:fill;display:block}
.ln{position:absolute;overflow:visible}
table.pt{border-collapse:collapse;width:100%;height:100%}
table.pt td{border:1px solid #fff;padding:4pt;font-size:12pt;vertical-align:top}
.bar{position:fixed;left:0;right:0;bottom:0;display:flex;gap:6px;overflow-x:auto;padding:8px 10px;background:rgba(20,22,30,.92);z-index:200}
.bar button{flex:0 0 auto;min-width:40px;height:34px;border:0;border-radius:10px;background:#2c3040;color:#fff;font:600 14px sans-serif}
.bar button.play{background:#1E6BFF;padding:0 14px}
body.show{background:#000;padding:0}
body.show .slide-wrap{display:none;margin:0;width:100%;border-radius:0;box-shadow:none}
body.show .slide-wrap.cur{display:block;position:fixed;top:50%;left:0;transform:translateY(-50%)}
body.show .bar{display:none}
@media print{.bar{display:none}}
"""
        val script = """
var cur=0,n=${order.size};
function fit(){var w=document.querySelectorAll('.slide-wrap');for(var i=0;i<w.length;i++){var s=w[i].querySelector('.slide');
var cw=w[i].clientWidth||window.innerWidth;var k=cw/s.offsetWidth;s.style.transform='scale('+k+')';w[i].style.height=(s.offsetHeight*k)+'px'}}
function go(i){cur=Math.max(0,Math.min(n-1,i));var e=document.getElementById('s'+cur);if(document.body.classList.contains('show')){mark()}else{e.scrollIntoView({behavior:'smooth',block:'start'})}}
function mark(){var w=document.querySelectorAll('.slide-wrap');for(var i=0;i<w.length;i++)w[i].classList.toggle('cur',i==cur);fit()}
function play(){document.body.classList.add('show');mark()}
function stop(){document.body.classList.remove('show');fit();go(cur)}
var sx=0;document.addEventListener('touchstart',function(e){sx=e.touches[0].clientX},{passive:true});
document.addEventListener('touchend',function(e){if(!document.body.classList.contains('show'))return;var dx=e.changedTouches[0].clientX-sx;
if(dx<-40)go(cur+1);else if(dx>40)go(cur-1);else if(e.changedTouches[0].clientX>window.innerWidth/2)go(cur+1);else go(cur-1)});
window.addEventListener('resize',fit);fit();"""
        val bar = "<div class=\"bar\"><button class=\"play\" onclick=\"play()\">▶</button>$thumbs</div>"
        return page(title, css, body.toString() + bar, script)
    }

    /** Slayd master shakllarini ko'rsatadimi (showMasterSp="0" bo'lsa yo'q). */
    private fun showMasterShapes(b: ByteArray): Boolean {
        val p = parser(b)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "p:sld") return p.a("showMasterSp") != "0"
        }
        return true
    }

    private fun slideBg(b: ByteArray, theme: Map<String, String>): String? {
        val p = parser(b)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "p:bg") {
                var c: String? = null
                p.children({ n ->
                    if (n == "p:bgPr") p.children({ m ->
                        when (m) {
                            "a:solidFill" -> c = p.readColor(theme)
                            "a:gradFill" -> c = p.readGradient(theme).firstOrNull()
                            else -> p.skipTag()
                        }
                    })
                    else if (n == "p:bgRef") c = p.readColor(theme)
                    else p.skipTag()
                })
                return c
            }
            if (p.eventType == XmlPullParser.START_TAG && p.name == "p:spTree") return null
        }
        return null
    }

    private fun collectPlaceholders(b: ByteArray, out: HashMap<String, PhInfo>, theme: Map<String, String>) {
        val p = parser(b)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG || p.name != "p:sp") continue
            var key: String? = null
            var type = ""
            val info = PhInfo(Xfrm())
            p.children({ n ->
                when (n) {
                    "p:nvSpPr" -> p.children({ a1 ->
                        if (a1 == "p:nvPr") p.children({ a2 ->
                            if (a2 == "p:ph") { type = p.a("type") ?: "body"; key = type + ":" + (p.a("idx") ?: "") }
                            p.skipTag()
                        }) else p.skipTag()
                    })
                    "p:spPr" -> p.children({ c -> if (c == "a:xfrm") p.readXfrm(info.xf) else p.skipTag() })
                    "p:txBody" -> p.children({ c ->
                        when (c) {
                            "a:bodyPr" -> { info.anchor = p.a("anchor"); p.skipTag() }
                            "a:lstStyle" -> { val col = arrayOf<String?>(null); val bo = BooleanArray(1); p.readLevelStyles(info.sizes, null, theme, col, bo); info.color = col[0]; info.bold = bo[0] }
                            "a:p" -> p.children({ pp ->
                                if (pp == "a:pPr") { info.align = when (p.a("algn")) { "ctr" -> "center"; "r" -> "right"; else -> info.align }; p.skipTag() } else p.skipTag()
                            })
                            else -> p.skipTag()
                        }
                    })
                    else -> p.skipTag()
                }
            })
            val k = key ?: continue
            out.putIfAbsent(k, info)
            out.putIfAbsent(type, info)
            if (type == "ctrTitle") out.putIfAbsent("title", info)
            if (type == "title") out.putIfAbsent("ctrTitle", info)
            if (type == "subTitle" || type == "obj") out.putIfAbsent("body", info)
        }
    }

    private fun XmlPullParser.readXfrm(x: Xfrm, ch: Xfrm? = null) {
        a("rot")?.toFloatOrNull()?.let { x.rot = it / 60000f }
        x.flipH = a("flipH") == "1"
        x.flipV = a("flipV") == "1"
        children({ n ->
            when (n) {
                "a:off" -> { x.x = a("x")?.toFloatOrNull() ?: 0f; x.y = a("y")?.toFloatOrNull() ?: 0f }
                "a:ext" -> { x.w = a("cx")?.toFloatOrNull() ?: 0f; x.h = a("cy")?.toFloatOrNull() ?: 0f }
                "a:chOff" -> ch?.let { it.x = a("x")?.toFloatOrNull() ?: 0f; it.y = a("y")?.toFloatOrNull() ?: 0f }
                "a:chExt" -> ch?.let { it.w = a("cx")?.toFloatOrNull() ?: 0f; it.h = a("cy")?.toFloatOrNull() ?: 0f }
            }
            skipTag()
        })
    }

    /**
     * spTree (yoki guruh) ichidagi shakllarni chizadi. [map] — guruh koordinatalarini slayd
     * koordinatalariga o'tkazadi. [decorOnly] — master/maket'dan faqat bezak shakllari (joy egalarisiz).
     */
    private fun renderShapes(p: XmlPullParser, out: StringBuilder, ctx: Ctx, decorOnly: Boolean = false) {
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "p:spTree") {
                renderTree(p, out, ctx, { it }, decorOnly, 0)
                return
            }
        }
    }

    private fun renderTree(p: XmlPullParser, out: StringBuilder, ctx: Ctx, map: (Xfrm) -> Xfrm, decorOnly: Boolean, depth: Int) {
        p.children({ n -> renderElement(p, n, out, ctx, map, decorOnly, depth) })
    }

    private fun renderElement(p: XmlPullParser, n: String, out: StringBuilder, ctx: Ctx, map: (Xfrm) -> Xfrm, decorOnly: Boolean, depth: Int) {
        when (n) {
            "p:sp", "p:cxnSp" -> out.append(renderShape(p, ctx, map, n == "p:cxnSp", decorOnly))
            "p:pic" -> out.append(renderPic(p, ctx, map))
            "p:graphicFrame" -> out.append(if (decorOnly) { p.skipTag(); "" } else renderFrame(p, ctx.theme, map))
            "p:grpSp" -> {
                if (depth > 12) { p.skipTag(); return }
                val off = Xfrm(); val ch = Xfrm()
                var mapper: (Xfrm) -> Xfrm = map
                p.children({ c ->
                    if (c == "p:grpSpPr") {
                        p.children({ g -> if (g == "a:xfrm") p.readXfrm(off, ch) else p.skipTag() })
                        val sx = if (ch.w > 0) off.w / ch.w else 1f
                        val sy = if (ch.h > 0) off.h / ch.h else 1f
                        mapper = { r -> map(Xfrm(off.x + (r.x - ch.x) * sx, off.y + (r.y - ch.y) * sy, r.w * sx, r.h * sy, r.rot).also { it.flipH = r.flipH; it.flipV = r.flipV }) }
                    } else renderElement(p, c, out, ctx, mapper, decorOnly, depth + 1)
                })
            }
            // yangi xususiyatlar mc:Choice'da, eski mos variant — mc:Fallback'da
            "mc:AlternateContent" -> {
                var done = false
                p.children({ c ->
                    if ((c == "mc:Choice" || c == "mc:Fallback") && !done) {
                        val before = out.length
                        p.children({ cc -> renderElement(p, cc, out, ctx, map, decorOnly, depth + 1) })
                        if (out.length > before) done = true
                    } else p.skipTag()
                })
            }
            else -> p.skipTag()
        }
    }

    /** custGeom → `viewBox=".." ><path d=".."` (birinchi yo'l o'lchamida). */
    private fun XmlPullParser.readCustGeom(): String? {
        val d = StringBuilder()
        var w = 0f
        var h = 0f
        children({ n ->
            if (n == "a:pathLst") children({ pn ->
                if (pn == "a:path") {
                    if (w == 0f) { w = a("w")?.toFloatOrNull() ?: 0f; h = a("h")?.toFloatOrNull() ?: 0f }
                    children({ cmd ->
                        val pts = ArrayList<String>()
                        if (cmd == "a:close") { d.append("Z "); skipTag() }
                        else children({ pt -> if (pt == "a:pt") pts += "${a("x") ?: "0"} ${a("y") ?: "0"}"; skipTag() })
                        when (cmd) {
                            "a:moveTo" -> pts.firstOrNull()?.let { d.append("M $it ") }
                            "a:lnTo" -> pts.firstOrNull()?.let { d.append("L $it ") }
                            "a:cubicBezTo" -> if (pts.size == 3) d.append("C ${pts.joinToString(" ")} ")
                            "a:quadBezTo" -> if (pts.size == 2) d.append("Q ${pts.joinToString(" ")} ")
                        }
                    })
                } else skipTag()
            }) else skipTag()
        })
        if (d.isEmpty() || w <= 0f || h <= 0f) return null
        return "viewBox=\"0 0 ${fmt(w)} ${fmt(h)}\"><path d=\"$d\""
    }

    private fun pos(x: Xfrm): String {
        val tr = ArrayList<String>()
        if (x.rot != 0f) tr += "rotate(${fmt(x.rot)}deg)"
        if (x.flipH) tr += "scaleX(-1)"
        if (x.flipV) tr += "scaleY(-1)"
        return "left:${fmt(x.x / 12700f)}pt;top:${fmt(x.y / 12700f)}pt;width:${fmt(x.w / 12700f)}pt;height:${fmt(x.h / 12700f)}pt;" +
            if (tr.isNotEmpty()) "transform:${tr.joinToString(" ")};" else ""
    }

    private fun renderShape(p: XmlPullParser, ctx: Ctx, map: (Xfrm) -> Xfrm, connector: Boolean, decorOnly: Boolean): String {
        val theme = ctx.theme
        var xf: Xfrm? = null
        var ph: String? = null
        var phType = ""
        var fill: String? = null
        var grad: List<String>? = null
        var line: String? = null
        var lineW = 0.75f
        var noLine = false
        var geom = "rect"
        var imgFill: String? = null
        var anchor: String? = null
        val insets = floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f)
        var txBox = false
        var styleFill: String? = null
        var styleFont: String? = null
        var styleLine: String? = null
        var noFill = false
        var fontScale = 1f
        var vert = false
        var custPath: String? = null
        val paras = ArrayList<PptPara>()
        p.children({ n ->
            when (n) {
                "p:nvSpPr", "p:nvCxnSpPr" -> p.children({ a1 ->
                    when (a1) {
                        "p:cNvSpPr" -> { txBox = p.a("txBox") == "1"; p.skipTag() }
                        "p:nvPr" -> p.children({ a2 ->
                            if (a2 == "p:ph") { phType = p.a("type") ?: "body"; ph = phType + ":" + (p.a("idx") ?: "") }
                            p.skipTag()
                        })
                        else -> p.skipTag()
                    }
                })
                "p:style" -> p.children({ c ->
                    when (c) {
                        "a:fillRef" -> { val idx = p.a("idx"); val col = p.readColor(theme); if (idx != "0") styleFill = col }
                        "a:lnRef" -> { val idx = p.a("idx"); val col = p.readColor(theme); if (idx != "0") styleLine = col }
                        "a:fontRef" -> styleFont = p.readColor(theme)
                        else -> p.skipTag()
                    }
                })
                "p:spPr" -> p.children({ c ->
                    when (c) {
                        "a:xfrm" -> { val x = Xfrm(); p.readXfrm(x); xf = x }
                        "a:solidFill" -> fill = p.readColor(theme)
                        "a:gradFill" -> grad = p.readGradient(theme)
                        "a:noFill" -> { noFill = true; p.skipTag() }
                        "a:prstGeom" -> { geom = p.a("prst") ?: "rect"; p.skipTag() }
                        "a:custGeom" -> { geom = "cust"; custPath = p.readCustGeom() }
                        "a:ln" -> {
                            p.a("w")?.toFloatOrNull()?.let { lineW = maxOf(0.5f, it / 12700f) }
                            p.children({ l ->
                                when (l) {
                                    "a:solidFill" -> line = p.readColor(theme)
                                    "a:noFill" -> { noLine = true; p.skipTag() }
                                    else -> p.skipTag()
                                }
                            })
                        }
                        "a:blipFill" -> p.children({ bf -> if (bf == "a:blip") { imgFill = p.a("r:embed"); p.skipTag() } else p.skipTag() })
                        else -> p.skipTag()
                    }
                })
                "p:txBody" -> p.children({ c ->
                    when (c) {
                        "a:bodyPr" -> {
                            anchor = p.a("anchor") ?: anchor
                            vert = p.a("vert").let { it != null && it != "horz" }
                            p.a("lIns")?.toFloatOrNull()?.let { insets[0] = it / 12700f }
                            p.a("tIns")?.toFloatOrNull()?.let { insets[1] = it / 12700f }
                            p.a("rIns")?.toFloatOrNull()?.let { insets[2] = it / 12700f }
                            p.a("bIns")?.toFloatOrNull()?.let { insets[3] = it / 12700f }
                            p.children({ b ->
                                if (b == "a:normAutofit") p.a("fontScale")?.toFloatOrNull()?.let { fontScale = it / 100000f }
                                p.skipTag()
                            })
                        }
                        "a:p" -> paras += readPptPara(p, theme)
                        else -> p.skipTag()
                    }
                })
                else -> p.skipTag()
            }
        })
        if (decorOnly && ph != null) return ""
        val phInfo = ph?.let { ctx.ph[it] ?: ctx.ph[phType] }
        val box = xf ?: phInfo?.xf?.takeIf { it.w > 0 }?.let { Xfrm(it.x, it.y, it.w, it.h) } ?: return ""
        val m = map(box)
        val isLine = connector || geom.startsWith("line") || geom.contains("Connector")
        if (isLine) {
            val col = if (noLine) return "" else line ?: styleLine ?: "000000"
            val w = m.w / 12700f
            val h = m.h / 12700f
            val x1 = if (m.flipH) w else 0f
            val y1 = if (m.flipV) h else 0f
            val rot = if (m.rot != 0f) "transform:rotate(${fmt(m.rot)}deg);" else ""
            return "<svg class=\"ln\" style=\"left:${fmt(m.x / 12700f)}pt;top:${fmt(m.y / 12700f)}pt;width:${fmt(maxOf(w, 1f))}pt;height:${fmt(maxOf(h, 1f))}pt;$rot\" " +
                "viewBox=\"0 0 ${fmt(maxOf(w, 1f))} ${fmt(maxOf(h, 1f))}\"><line x1=\"${fmt(x1)}\" y1=\"${fmt(y1)}\" x2=\"${fmt(w - x1)}\" y2=\"${fmt(h - y1)}\" stroke=\"#$col\" stroke-width=\"${fmt(lineW)}\"/></svg>"
        }
        val isShape = ph == null && !txBox
        val cp = custPath
        if (geom == "cust" && cp != null && paras.all { it.runs.isEmpty() }) {
            val fillCol = if (noFill) "none" else (fill ?: grad?.firstOrNull() ?: styleFill)?.let { "#$it" } ?: "none"
            val strokeCol = if (noLine) "none" else (line ?: styleLine)?.let { "#$it" } ?: "none"
            if (fillCol == "none" && strokeCol == "none") return ""
            return "<svg class=\"ln\" style=\"${pos(m)}\" preserveAspectRatio=\"none\" $cp fill=\"$fillCol\" stroke=\"$strokeCol\" stroke-width=\"${fmt(lineW)}\" vector-effect=\"non-scaling-stroke\"/></svg>"
        }
        val st = StringBuilder(pos(m))
        val bgColor = if (noFill) null else fill ?: if (isShape && grad == null) styleFill else null
        when {
            bgColor != null -> st.append("background:#$bgColor;")
            grad != null && grad!!.isNotEmpty() && !noFill -> st.append("background:linear-gradient(#${grad!!.first()},#${grad!!.last()});")
        }
        if (!noLine) (line ?: if (isShape && (bgColor != null || grad != null)) styleLine else null)?.let { st.append("border:${fmt(lineW)}pt solid #$it;") }
        when (geom) {
            "ellipse", "flowChartConnector" -> st.append("border-radius:50%;")
            "roundRect", "flowChartAlternateProcess" -> st.append("border-radius:${fmt(minOf(m.w, m.h) / 12700f * 0.16f)}pt;")
            "triangle" -> st.append("clip-path:polygon(50% 0,100% 100%,0 100%);")
            "rtTriangle" -> st.append("clip-path:polygon(0 0,100% 100%,0 100%);")
            "diamond", "flowChartDecision" -> st.append("clip-path:polygon(50% 0,100% 50%,50% 100%,0 50%);")
            "hexagon" -> st.append("clip-path:polygon(25% 0,75% 0,100% 50%,75% 100%,25% 100%,0 50%);")
            "rightArrow" -> st.append("clip-path:polygon(0 25%,65% 25%,65% 0,100% 50%,65% 100%,65% 75%,0 75%);")
            "leftArrow" -> st.append("clip-path:polygon(100% 25%,35% 25%,35% 0,0 50%,35% 100%,35% 75%,100% 75%);")
            "chevron" -> st.append("clip-path:polygon(0 0,75% 0,100% 50%,75% 100%,0 100%,25% 50%);")
            "homePlate" -> st.append("clip-path:polygon(0 0,80% 0,100% 50%,80% 100%,0 100%);")
            "parallelogram" -> st.append("clip-path:polygon(25% 0,100% 0,75% 100%,0 100%);")
            "pentagon" -> st.append("clip-path:polygon(50% 0,100% 38%,82% 100%,18% 100%,0 38%);")
        }
        st.append("padding:${fmt(insets[1])}pt ${fmt(insets[2])}pt ${fmt(insets[3])}pt ${fmt(insets[0])}pt;")
        // matn uslubi: slayd → maket joy egasi → master txStyles
        val styles = ctx.styles
        val levelSizes: FloatArray = when (phType) {
            "title", "ctrTitle" -> styles.title
            "body", "obj", "subTitle" -> styles.body
            "" -> styles.other
            else -> styles.other
        }
        val sizes = FloatArray(9) { lvl ->
            val fromPh = phInfo?.sizes?.get(lvl) ?: 0f
            when {
                fromPh > 0f -> fromPh
                phType == "subTitle" -> 20f
                phType in setOf("dt", "ftr", "sldNum") -> 12f
                else -> levelSizes[lvl]
            }
        }
        val defAlign = phInfo?.align ?: when {
            phType == "ctrTitle" || phType == "subTitle" -> "center"
            isShape -> "center"
            else -> null
        }
        val bullets = if (phType == "body" || phType == "obj") styles.bodyBullet else null
        val defColor = when {
            isShape && bgColor != null -> styleFont ?: "FFFFFF"
            phInfo?.color != null -> phInfo.color
            phType == "title" || phType == "ctrTitle" -> styles.titleColor ?: theme["tx1"]
            phType == "body" || phType == "obj" || phType == "subTitle" -> styles.bodyColor ?: theme["tx1"]
            else -> theme["tx1"]
        }
        val bold = (phType == "title" || phType == "ctrTitle") && (styles.titleBold || phInfo?.bold == true)
        val text = StringBuilder()
        for (pp in paras) text.append(pp.html(sizes, defAlign, bullets, defColor, fontScale, bold))
        val anc = anchor ?: phInfo?.anchor ?: if (isShape) "ctr" else if (phType == "title" || phType == "ctrTitle") "ctr" else "t"
        st.append("justify-content:${when (anc) { "ctr" -> "center"; "b" -> "flex-end"; else -> "flex-start" }};")
        if (vert) st.append("writing-mode:vertical-rl;")
        val img = imgFill?.let { image(ctx.files, ctx.rels[it]) }?.let { "<img src=\"$it\" style=\"position:absolute;inset:0\">" } ?: ""
        if (img.isEmpty() && text.isEmpty() && bgColor == null && grad == null && line == null) return ""
        return "<div class=\"sp\" style=\"$st\">$img$text</div>"
    }

    private class PptRun(val text: String, val css: String, val size: Float, val br: Boolean = false)

    private inner class PptPara(val align: String?, val bullet: String?, val noBullet: Boolean, val lvl: Int, val defSz: Float, val runs: List<PptRun>, val spaceBefore: Float) {
        fun html(sizes: FloatArray, defAlign: String?, bullets: BooleanArray?, defColor: String?, scale: Float, bold: Boolean): String {
            val base = (if (defSz > 0) defSz else sizes[lvl.coerceIn(0, 8)]) * scale
            val st = StringBuilder("font-size:${fmt(base)}pt;")
            (align ?: defAlign)?.let { st.append("text-align:$it;") }
            defColor?.let { st.append("color:#$it;") }
            if (bold) st.append("font-weight:bold;")
            if (spaceBefore > 0) st.append("margin-top:${fmt(spaceBefore * scale)}pt;")
            if (runs.isEmpty()) return "<p style=\"$st\">&nbsp;</p>"
            val b = when {
                noBullet -> null
                bullet != null -> bullet
                bullets != null && bullets[lvl.coerceIn(0, 8)] -> if (lvl == 0) "•" else "–"
                else -> null
            }
            if (b != null) st.append("padding-left:${18 + lvl * 20}pt;text-indent:-16pt;") else if (lvl > 0) st.append("padding-left:${lvl * 20}pt;")
            val body = runs.joinToString("") { r ->
                if (r.br) "<br>" else {
                    val css = r.css + if (r.size > 0) "font-size:${fmt(r.size * scale)}pt;" else ""
                    if (css.isEmpty()) esc(r.text) else "<span style=\"$css\">${esc(r.text)}</span>"
                }
            }
            return "<p style=\"$st\">${if (b != null) "<span style=\"display:inline-block;width:16pt;text-indent:0\">${esc(b)}</span>" else ""}$body</p>"
        }
    }

    private fun readPptPara(p: XmlPullParser, theme: Map<String, String>): PptPara {
        var align: String? = null
        var bullet: String? = null
        var noBullet = false
        var lvl = 0
        var defSz = 0f
        var spaceBefore = 0f
        val runs = ArrayList<PptRun>()
        p.children({ n ->
            when (n) {
                "a:pPr" -> {
                    align = when (p.a("algn")) { "ctr" -> "center"; "r" -> "right"; "just" -> "justify"; "l" -> "left"; else -> null }
                    lvl = p.a("lvl")?.toIntOrNull() ?: 0
                    p.children({ c ->
                        when (c) {
                            "a:buChar" -> { bullet = p.a("char"); p.skipTag() }
                            "a:buAutoNum" -> { bullet = "•"; p.skipTag() }
                            "a:buNone" -> { noBullet = true; p.skipTag() }
                            "a:defRPr" -> { p.a("sz")?.toFloatOrNull()?.let { defSz = it / 100f }; p.skipTag() }
                            "a:spcBef" -> p.children({ s -> if (s == "a:spcPts") p.a("val")?.toFloatOrNull()?.let { spaceBefore = it / 100f }; p.skipTag() })
                            else -> p.skipTag()
                        }
                    })
                }
                "a:r", "a:fld" -> {
                    var css = ""
                    var size = 0f
                    val t = StringBuilder()
                    p.children({ c ->
                        when (c) {
                            "a:rPr" -> {
                                val sb = StringBuilder()
                                p.a("sz")?.toFloatOrNull()?.let { size = it / 100f }
                                when (p.a("b")) { "1" -> sb.append("font-weight:bold;"); "0" -> sb.append("font-weight:normal;") }
                                if (p.a("i") == "1") sb.append("font-style:italic;")
                                if (p.a("u").let { it != null && it != "none" }) sb.append("text-decoration:underline;")
                                if (p.a("strike").let { it != null && it != "noStrike" }) sb.append("text-decoration:line-through;")
                                when (p.a("cap")) { "all" -> sb.append("text-transform:uppercase;"); "small" -> sb.append("font-variant:small-caps;") }
                                p.children({ x ->
                                    when (x) {
                                        "a:solidFill" -> p.readColor(theme)?.let { sb.append("color:#$it;") }
                                        "a:gradFill" -> p.readGradient(theme).firstOrNull()?.let { sb.append("color:#$it;") }
                                        "a:highlight" -> p.readColor(theme)?.let { sb.append("background:#$it;") }
                                        "a:latin" -> { p.a("typeface")?.takeIf { !it.startsWith("+") }?.let { sb.append("font-family:'$it',sans-serif;") }; p.skipTag() }
                                        else -> p.skipTag()
                                    }
                                })
                                css = sb.toString()
                            }
                            "a:t" -> p.children({ p.skipTag() }, { t.append(it) })
                            else -> p.skipTag()
                        }
                    })
                    runs += PptRun(t.toString(), css, size)
                }
                "a:br" -> { runs += PptRun("", "", 0f, br = true); p.skipTag() }
                "a:endParaRPr" -> { p.a("sz")?.toFloatOrNull()?.let { if (defSz == 0f && runs.isEmpty()) defSz = it / 100f }; p.skipTag() }
                else -> p.skipTag()
            }
        })
        return PptPara(align, bullet, noBullet, lvl, defSz, runs, spaceBefore)
    }

    private fun renderPic(p: XmlPullParser, ctx: Ctx, map: (Xfrm) -> Xfrm): String {
        var rid: String? = null
        val xf = Xfrm()
        var crop: FloatArray? = null
        p.children({ n ->
            when (n) {
                "p:blipFill" -> p.children({ c ->
                    when (c) {
                        "a:blip" -> { rid = p.a("r:embed"); p.skipTag() }
                        "a:srcRect" -> {
                            val l = (p.a("l")?.toFloatOrNull() ?: 0f) / 100000f; val t = (p.a("t")?.toFloatOrNull() ?: 0f) / 100000f
                            val r = (p.a("r")?.toFloatOrNull() ?: 0f) / 100000f; val b = (p.a("b")?.toFloatOrNull() ?: 0f) / 100000f
                            if (l + t + r + b > 0f) crop = floatArrayOf(l, t, r, b)
                            p.skipTag()
                        }
                        else -> p.skipTag()
                    }
                })
                "p:spPr" -> p.children({ c -> if (c == "a:xfrm") p.readXfrm(xf) else p.skipTag() })
                else -> p.skipTag()
            }
        })
        val src = image(ctx.files, rid?.let { ctx.rels[it] }) ?: return ""
        val c = crop
        val img = if (c != null && c[0] + c[2] < 1f && c[1] + c[3] < 1f) {
            // kesilgan rasm: kattaroq qilib, ko'rinadigan qismini siljitamiz
            val sw = 1f / (1f - c[0] - c[2]); val sh = 1f / (1f - c[1] - c[3])
            "<img src=\"$src\" style=\"position:absolute;width:${fmt(sw * 100)}%;height:${fmt(sh * 100)}%;left:${fmt(-c[0] * sw * 100)}%;top:${fmt(-c[1] * sh * 100)}%;max-width:none\">"
        } else "<img src=\"$src\">"
        return "<div class=\"sp\" style=\"${pos(map(xf))}padding:0;overflow:hidden\">$img</div>"
    }

    private fun renderFrame(p: XmlPullParser, theme: Map<String, String>, map: (Xfrm) -> Xfrm): String {
        val xf = Xfrm()
        val rows = ArrayList<List<Pair<String, String?>>>()
        p.children({ n ->
            when (n) {
                "p:xfrm" -> p.readXfrm(xf)
                "a:graphic" -> {
                    val depth = p.depth
                    var row: ArrayList<Pair<String, String?>>? = null
                    var cell: StringBuilder? = null
                    var cellFill: String? = null
                    while (true) {
                        val ev = p.next()
                        if (ev == XmlPullParser.END_DOCUMENT) break
                        if (ev == XmlPullParser.END_TAG && p.depth == depth) break
                        if (ev == XmlPullParser.START_TAG) when (p.name) {
                            "a:tr" -> row = ArrayList()
                            "a:tc" -> { cell = StringBuilder(); cellFill = null }
                            "a:p" -> if (cell != null && cell!!.isNotEmpty()) cell!!.append("<br>")
                            "a:tcPr" -> {
                                p.children({ c -> if (c == "a:solidFill") cellFill = p.readColor(theme) else p.skipTag() })
                                // tcPr tugadi — END_TAG children ichida iste'mol qilindi
                            }
                        } else if (ev == XmlPullParser.TEXT) cell?.append(esc(p.text))
                        else if (ev == XmlPullParser.END_TAG) when (p.name) {
                            "a:tc" -> { cell?.let { row?.add(it.toString() to cellFill) }; cell = null }
                            "a:tr" -> { row?.let { rows += it }; row = null }
                        }
                    }
                }
                else -> p.skipTag()
            }
        })
        if (rows.isEmpty()) return ""
        val sb = StringBuilder("<div class=\"sp\" style=\"${pos(map(xf))}padding:0\"><table class=\"pt\">")
        rows.forEachIndexed { i, r ->
            sb.append("<tr>")
            r.forEach { (c, f) ->
                val bg = f ?: if (i == 0) theme["accent1"] else if (i % 2 == 1) "E9EBF5" else "CFD5EA"
                val color = if (f == null && i == 0) "color:#fff;font-weight:bold;" else ""
                sb.append("<td style=\"background:#$bg;$color\">$c</td>")
            }
            sb.append("</tr>")
        }
        sb.append("</table></div>")
        return sb.toString()
    }

    // ---------------------------------------------------------------- CSV

    fun csv(text: String, title: String): String {
        val sep = listOf(',', ';', '\t').maxByOrNull { c -> text.lineSequence().take(5).sumOf { l -> l.count { it == c } } } ?: ','
        val sb = StringBuilder("<div class=\"sheet\"><table class=\"x\">")
        text.lineSequence().take(10000).forEachIndexed { i, line ->
            if (line.isEmpty()) return@forEachIndexed
            sb.append("<tr><th class=\"r\">${i + 1}</th>")
            parseCsvLine(line, sep).forEach { sb.append("<td>${esc(it)}</td>") }
            sb.append("</tr>")
        }
        sb.append("</table></div>")
        val css = """.sheet{overflow:auto;background:#fff;margin:8px}
table.x{border-collapse:collapse;font-size:11pt}table.x td{border:1px solid #E1E3E8;padding:3px 6px;white-space:nowrap}
table.x th{background:#F3F4F6;color:#555;font-weight:normal;font-size:12px;border:1px solid #D5D8DE;position:sticky;left:0}"""
        return page(title, css, sb.toString())
    }

    private fun parseCsvLine(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                q && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == sep && !q -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    companion object {
        fun fmt(f: Float): String {
            val r = (f * 10).roundToInt() / 10f
            return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
        }
    }
}
