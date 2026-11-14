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

    private fun readZip(input: InputStream): Map<String, ByteArray> {
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

    private fun docxTable(p: XmlPullParser, d: Docx): String {
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

    private fun themeColors(files: Map<String, ByteArray>): Map<String, String> {
        val out = HashMap(schemeDefault)
        val b = files.keys.firstOrNull { it.startsWith("ppt/theme/theme") }?.let { files[it] } ?: return out
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

    /** a:srgbClr / a:schemeClr ichidagi rangni o'qiydi (lumMod/lumOff taxminiy). */
    private fun XmlPullParser.readColor(theme: Map<String, String>): String? {
        var c: String? = null
        children({ n ->
            when (n) {
                "a:srgbClr" -> { c = a("val"); skipTag() }
                "a:schemeClr" -> {
                    var col = theme[a("val") ?: ""]
                    var lumMod = 1f; var lumOff = 0f
                    children({ m ->
                        when (m) { "a:lumMod" -> lumMod = (a("val")?.toFloatOrNull() ?: 100000f) / 100000f; "a:lumOff" -> lumOff = (a("val")?.toFloatOrNull() ?: 0f) / 100000f }
                        skipTag()
                    })
                    if (col != null && (lumMod != 1f || lumOff != 0f)) col = adjust(col, lumMod, lumOff)
                    c = col
                }
                "a:sysClr" -> { c = a("lastClr"); skipTag() }
                "a:prstClr" -> { c = when (a("val")) { "black" -> "000000"; "white" -> "FFFFFF"; "red" -> "FF0000"; else -> null }; skipTag() }
                else -> skipTag()
            }
        })
        return c
    }

    private fun adjust(hex: String, mod: Float, off: Float): String {
        val v = hex.toIntOrNull(16) ?: return hex
        fun ch(x: Int) = ((x / 255f * mod + off).coerceIn(0f, 1f) * 255).roundToInt()
        return String.format("%02X%02X%02X", ch((v shr 16) and 255), ch((v shr 8) and 255), ch(v and 255))
    }

    private class Xfrm(var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f, var rot: Float = 0f)

    fun pptx(input: InputStream, title: String): String {
        val files = readZip(input)
        val theme = themeColors(files)
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
                    "p:sldId" -> p.a("r:id")?.let { presRels[it] }?.let { order += it }
                }
            }
        }
        if (order.isEmpty()) order += files.keys.filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val W = sw / 12700f // pt
        val H = sh / 12700f
        val body = StringBuilder()
        order.forEachIndexed { i, path ->
            val xml = files[path] ?: return@forEachIndexed
            val slideRels = rels(files, path)
            // joylashuvi berilmagan joy egalari uchun maket (layout) dan olinadi
            val layoutPath = slideRels.values.firstOrNull { it.contains("slideLayouts/") }
            val layoutPos = HashMap<String, Xfrm>()
            var layoutBg: String? = null
            layoutPath?.let { lp -> files[lp]?.let { collectPlaceholders(it, layoutPos) } }
            val masterPath = layoutPath?.let { rels(files, it).values.firstOrNull { v -> v.contains("slideMasters/") } }
            masterPath?.let { mp -> files[mp]?.let { b -> val mpPos = HashMap<String, Xfrm>(); collectPlaceholders(b, mpPos); mpPos.forEach { (k, v) -> layoutPos.putIfAbsent(k, v) }; layoutBg = slideBg(b, theme) } }
            val inner = StringBuilder()
            val bg = slideBg(xml, theme) ?: layoutPath?.let { files[it] }?.let { slideBg(it, theme) } ?: layoutBg ?: "FFFFFF"
            renderShapes(parser(xml), inner, files, slideRels, theme, layoutPos)
            body.append("<div class=\"slide-wrap\"><div class=\"no\">${i + 1}</div><div class=\"slide\" style=\"width:${fmt(W)}pt;height:${fmt(H)}pt;background:#$bg\">$inner</div></div>")
        }
        val css = """
body{padding:10px 0}
.slide-wrap{position:relative;margin:0 auto 18px;width:96%;box-shadow:0 3px 14px rgba(0,0,0,.25);border-radius:4px;overflow:hidden;background:#fff}
.slide{position:relative;transform-origin:0 0;overflow:hidden}
.no{position:absolute;right:8px;bottom:6px;z-index:9;font:12px sans-serif;color:#fff;background:rgba(0,0,0,.4);padding:1px 7px;border-radius:9px}
.sp{position:absolute;box-sizing:border-box;overflow:hidden;display:flex;flex-direction:column}
.sp p{margin:0;line-height:1.15}
.sp img{width:100%;height:100%;object-fit:contain}
table.pt{border-collapse:collapse;width:100%;height:100%}
table.pt td{border:1px solid #fff;padding:4pt;background:#E8EBF5;font-size:12pt}
"""
        val script = """
function fit(){var w=document.querySelectorAll('.slide-wrap');for(var i=0;i<w.length;i++){var s=w[i].firstElementChild.nextElementSibling;
var k=w[i].clientWidth/s.offsetWidth;s.style.transform='scale('+k+')';w[i].style.height=(s.offsetHeight*k)+'px'}}
window.addEventListener('resize',fit);fit();"""
        return page(title, css, body.toString(), script)
    }

    private fun slideBg(b: ByteArray, theme: Map<String, String>): String? {
        val p = parser(b)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "p:bg") {
                var c: String? = null
                p.children({ n ->
                    if (n == "p:bgPr") p.children({ m -> if (m == "a:solidFill") c = p.readColor(theme) else p.skipTag() })
                    else if (n == "p:bgRef") c = p.readColor(theme)
                    else p.skipTag()
                })
                return c
            }
            if (p.eventType == XmlPullParser.START_TAG && p.name == "p:spTree") return null
        }
        return null
    }

    private fun collectPlaceholders(b: ByteArray, out: HashMap<String, Xfrm>) {
        val p = parser(b)
        var ph: String? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "p:sp" -> ph = null
                "p:ph" -> ph = (p.a("type") ?: "body") + ":" + (p.a("idx") ?: "")
                "a:off" -> if (ph != null) {
                    val x = Xfrm(p.a("x")?.toFloatOrNull() ?: 0f, p.a("y")?.toFloatOrNull() ?: 0f)
                    out.putIfAbsent(ph!!, x); out.putIfAbsent(ph!!.substringBefore(':'), x)
                }
                "a:ext" -> if (ph != null) {
                    out[ph!!]?.let { if (it.w == 0f) { it.w = p.a("cx")?.toFloatOrNull() ?: 0f; it.h = p.a("cy")?.toFloatOrNull() ?: 0f } }
                }
            }
        }
    }

    private fun XmlPullParser.readXfrm(x: Xfrm, ch: Xfrm? = null) {
        a("rot")?.toFloatOrNull()?.let { x.rot = it / 60000f }
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

    /** spTree yoki grpSp ichidagi shakllarni chizadi. [map] — guruh koordinatalarini slayd koordinatalariga o'tkazish. */
    private fun renderShapes(
        p: XmlPullParser, out: StringBuilder, files: Map<String, ByteArray>, rels: Map<String, String>,
        theme: Map<String, String>, layout: Map<String, Xfrm>, map: (Xfrm) -> Xfrm = { it }, inTree: Boolean = false,
    ) {
        fun body() {
            p.children({ n ->
                when (n) {
                    "p:sp", "p:cxnSp" -> out.append(renderShape(p, files, rels, theme, layout, map))
                    "p:pic" -> out.append(renderPic(p, files, rels, map))
                    "p:graphicFrame" -> out.append(renderFrame(p, theme, map))
                    "p:grpSp" -> {
                        val off = Xfrm(); val ch = Xfrm()
                        val sub = StringBuilder()
                        var mapper: (Xfrm) -> Xfrm = map
                        p.children({ c ->
                            when (c) {
                                "p:grpSpPr" -> {
                                    p.children({ g -> if (g == "a:xfrm") p.readXfrm(off, ch) else p.skipTag() })
                                    val sx = if (ch.w > 0) off.w / ch.w else 1f
                                    val sy = if (ch.h > 0) off.h / ch.h else 1f
                                    mapper = { r -> map(Xfrm(off.x + (r.x - ch.x) * sx, off.y + (r.y - ch.y) * sy, r.w * sx, r.h * sy, r.rot)) }
                                }
                                "p:sp", "p:cxnSp" -> sub.append(renderShape(p, files, rels, theme, layout, mapper))
                                "p:pic" -> sub.append(renderPic(p, files, rels, mapper))
                                "p:graphicFrame" -> sub.append(renderFrame(p, theme, mapper))
                                "p:grpSp" -> {
                                    // ichma-ich guruh — sodda yondashuv: o'tkazib yuboramiz
                                    p.skipTag()
                                }
                                else -> p.skipTag()
                            }
                        })
                        out.append(sub)
                    }
                    else -> p.skipTag()
                }
            })
        }
        if (inTree) { body(); return }
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "p:spTree") { body(); return }
        }
    }

    private fun pos(x: Xfrm): String =
        "left:${fmt(x.x / 12700f)}pt;top:${fmt(x.y / 12700f)}pt;width:${fmt(x.w / 12700f)}pt;height:${fmt(x.h / 12700f)}pt;" +
            if (x.rot != 0f) "transform:rotate(${fmt(x.rot)}deg);" else ""

    private fun renderShape(
        p: XmlPullParser, files: Map<String, ByteArray>, rels: Map<String, String>, theme: Map<String, String>,
        layout: Map<String, Xfrm>, map: (Xfrm) -> Xfrm,
    ): String {
        var xf: Xfrm? = null
        var ph: String? = null
        var phType = ""
        var fill: String? = null
        var line: String? = null
        var geom = "rect"
        var imgFill: String? = null
        val text = StringBuilder()
        var anchor: String? = null
        var insets = floatArrayOf(7.2f, 3.6f, 7.2f, 3.6f)
        var txBox = false
        var styleFill: String? = null
        var styleFont: String? = null
        var styleLine: String? = null
        var noFill = false
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
                        "a:noFill" -> { noFill = true; p.skipTag() }
                        "a:prstGeom" -> { geom = p.a("prst") ?: "rect"; p.skipTag() }
                        "a:ln" -> p.children({ l -> if (l == "a:solidFill") line = p.readColor(theme) else p.skipTag() })
                        "a:blipFill" -> p.children({ bf -> if (bf == "a:blip") { imgFill = p.a("r:embed"); p.skipTag() } else p.skipTag() })
                        else -> p.skipTag()
                    }
                })
                "p:txBody" -> p.children({ c ->
                    when (c) {
                        "a:bodyPr" -> {
                            anchor = p.a("anchor") ?: anchor
                            p.a("lIns")?.toFloatOrNull()?.let { insets[0] = it / 12700f }
                            p.a("tIns")?.toFloatOrNull()?.let { insets[1] = it / 12700f }
                            p.a("rIns")?.toFloatOrNull()?.let { insets[2] = it / 12700f }
                            p.a("bIns")?.toFloatOrNull()?.let { insets[3] = it / 12700f }
                            p.skipTag()
                        }
                        "a:p" -> paras += readPptPara(p, theme)
                        else -> p.skipTag()
                    }
                })
                else -> p.skipTag()
            }
        })
        val box = xf ?: ph?.let { layout[it] ?: layout[it.substringBefore(':')] }?.let { Xfrm(it.x, it.y, it.w, it.h) } ?: return ""
        val m = map(box)
        val st = StringBuilder(pos(m))
        val isShape = ph == null && !txBox
        val bgColor = if (noFill) null else fill ?: if (isShape) styleFill else null
        bgColor?.let { st.append("background:#$it;") }
        (line ?: if (isShape && bgColor != null) styleLine else null)?.let { st.append("border:1px solid #$it;") }
        // joy egalari va shakllar uchun PowerPoint'ning odatiy matn o'lchamlari
        val defSize = when (phType) {
            "ctrTitle" -> 44f; "title" -> 36f; "subTitle" -> 22f; "body", "obj" -> 24f
            "dt", "ftr", "sldNum" -> 12f
            else -> 18f
        }
        val defAlign = when {
            phType == "ctrTitle" || phType == "subTitle" -> "center"
            isShape -> "center"
            else -> null
        }
        val bulleted = phType == "body" || phType == "obj"
        val defColor = if (isShape && bgColor != null) (styleFont ?: "FFFFFF") else null
        for (pp in paras) text.append(pp.html(defSize, defAlign, bulleted, defColor))
        if (anchor == null) anchor = if (isShape || phType == "ctrTitle") "ctr" else if (phType == "title") "ctr" else "t"
        if (geom == "ellipse") st.append("border-radius:50%;")
        if (geom == "roundRect") st.append("border-radius:10pt;")
        st.append("padding:${fmt(insets[1])}pt ${fmt(insets[2])}pt ${fmt(insets[3])}pt ${fmt(insets[0])}pt;")
        st.append("justify-content:${when (anchor) { "ctr" -> "center"; "b" -> "flex-end"; else -> "flex-start" }};")
        val img = imgFill?.let { image(files, rels[it]) }?.let { "<img src=\"$it\">" } ?: ""
        return "<div class=\"sp\" style=\"$st\">$img$text</div>"
    }

    private class PptRun(val text: String, val css: String, val hasSize: Boolean, val hasColor: Boolean, val br: Boolean = false)

    private inner class PptPara(val align: String?, val bullet: String?, val noBullet: Boolean, val lvl: Int, val defSz: Float, val runs: List<PptRun>) {
        fun html(defSize: Float, defAlign: String?, bulleted: Boolean, defColor: String?): String {
            val size = if (defSz > 0) defSz else defSize * (if (lvl > 0) 0.85f else 1f)
            val st = StringBuilder("font-size:${fmt(size)}pt;")
            (align ?: defAlign)?.let { st.append("text-align:$it;") }
            defColor?.let { st.append("color:#$it;") }
            if (runs.isEmpty()) return "<p style=\"$st\">&nbsp;</p>"
            val b = when {
                noBullet -> null
                bullet != null -> bullet
                bulleted -> if (lvl == 0) "•" else "–"
                else -> null
            }
            if (b != null) st.append("padding-left:${18 + lvl * 20}pt;text-indent:-16pt;") else if (lvl > 0) st.append("padding-left:${lvl * 20}pt;")
            val body = runs.joinToString("") { r -> if (r.br) "<br>" else if (r.css.isEmpty()) esc(r.text) else "<span style=\"${r.css}\">${esc(r.text)}</span>" }
            return "<p style=\"$st\">${if (b != null) "<span style=\"display:inline-block;width:16pt;text-indent:0\">${esc(b)}</span>" else ""}$body</p>"
        }
    }

    private fun readPptPara(p: XmlPullParser, theme: Map<String, String>): PptPara {
        var align: String? = null
        var bullet: String? = null
        var noBullet = false
        var lvl = 0
        var defSz = 0f
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
                            else -> p.skipTag()
                        }
                    })
                }
                "a:r", "a:fld" -> {
                    var css = ""
                    var hasSize = false
                    var hasColor = false
                    val t = StringBuilder()
                    p.children({ c ->
                        when (c) {
                            "a:rPr" -> {
                                val sb = StringBuilder()
                                p.a("sz")?.toFloatOrNull()?.let { sb.append("font-size:${fmt(it / 100f)}pt;"); hasSize = true }
                                if (p.a("b") == "1") sb.append("font-weight:bold;")
                                if (p.a("i") == "1") sb.append("font-style:italic;")
                                if (p.a("u").let { it != null && it != "none" }) sb.append("text-decoration:underline;")
                                p.children({ x ->
                                    when (x) {
                                        "a:solidFill" -> p.readColor(theme)?.let { sb.append("color:#$it;"); hasColor = true }
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
                    runs += PptRun(t.toString(), css, hasSize, hasColor)
                }
                "a:br" -> { runs += PptRun("", "", false, false, br = true); p.skipTag() }
                "a:endParaRPr" -> { p.a("sz")?.toFloatOrNull()?.let { if (defSz == 0f && runs.isEmpty()) defSz = it / 100f }; p.skipTag() }
                else -> p.skipTag()
            }
        })
        return PptPara(align, bullet, noBullet, lvl, defSz, runs)
    }

    private fun renderPic(p: XmlPullParser, files: Map<String, ByteArray>, rels: Map<String, String>, map: (Xfrm) -> Xfrm): String {
        var rid: String? = null
        val xf = Xfrm()
        p.children({ n ->
            when (n) {
                "p:blipFill" -> p.children({ c -> if (c == "a:blip") { rid = p.a("r:embed"); p.skipTag() } else p.skipTag() })
                "p:spPr" -> p.children({ c -> if (c == "a:xfrm") p.readXfrm(xf) else p.skipTag() })
                else -> p.skipTag()
            }
        })
        val src = image(files, rid?.let { rels[it] }) ?: return ""
        return "<div class=\"sp\" style=\"${pos(map(xf))}padding:0\"><img src=\"$src\"></div>"
    }

    private fun renderFrame(p: XmlPullParser, theme: Map<String, String>, map: (Xfrm) -> Xfrm): String {
        val xf = Xfrm()
        val rows = ArrayList<List<String>>()
        p.children({ n ->
            when (n) {
                "p:xfrm" -> p.readXfrm(xf)
                "a:graphic" -> {
                    val depth = p.depth
                    var row: ArrayList<String>? = null
                    var cell: StringBuilder? = null
                    while (true) {
                        val ev = p.next()
                        if (ev == XmlPullParser.END_DOCUMENT) break
                        if (ev == XmlPullParser.END_TAG && p.depth == depth) break
                        if (ev == XmlPullParser.START_TAG) when (p.name) {
                            "a:tr" -> row = ArrayList()
                            "a:tc" -> cell = StringBuilder()
                            "a:p" -> if (cell != null && cell!!.isNotEmpty()) cell!!.append("<br>")
                        } else if (ev == XmlPullParser.TEXT) cell?.append(esc(p.text))
                        else if (ev == XmlPullParser.END_TAG) when (p.name) {
                            "a:tc" -> { cell?.let { row?.add(it.toString()) }; cell = null }
                            "a:tr" -> { row?.let { rows += it }; row = null }
                        }
                    }
                }
                else -> p.skipTag()
            }
        })
        @Suppress("UNUSED_VARIABLE") val t = theme
        if (rows.isEmpty()) return ""
        val sb = StringBuilder("<div class=\"sp\" style=\"${pos(map(xf))}padding:0\"><table class=\"pt\">")
        rows.forEachIndexed { i, r ->
            sb.append("<tr>")
            r.forEach { c -> sb.append(if (i == 0) "<td style=\"background:#4472C4;color:#fff;font-weight:bold\">$c</td>" else "<td>$c</td>") }
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
