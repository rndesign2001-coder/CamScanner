package uz.kitobskaner.office

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * Ko'ruvchida qilingan o'zgarishlarni asl OOXML faylga (DOCX/PPTX/XLSX) yozadi.
 *
 * Faqat o'zgargan paragraflar, kataklar va shakllar qayta yoziladi — qolgan hamma narsa
 * (uslublar, rasmlar, sarlavhalar, maketlar, formulalar) baytma-bayt saqlanadi.
 * Elementlar [HtmlRenderer] bergan identifikatorlar bo'yicha topiladi: "qism|teg|tartib",
 * bu yerda tartib — asl XML'dagi shu tegning getElementsByTagName tartibi.
 */
object OfficeEditor {

    // ---------------------------------------------------------------- paket

    private class Pkg(val files: LinkedHashMap<String, ByteArray>) {
        val docs = LinkedHashMap<String, Document>()
        private val index = HashMap<String, Map<String, List<Element>>>()
        val deleted = HashSet<String>()

        /** XML qismini DOM sifatida ochadi; [tags] ro'yxati o'zgartirishlardan oldingi tartibda eslab qolinadi. */
        fun doc(path: String, tags: List<String> = emptyList()): Document? {
            docs[path]?.let { return it }
            val b = files[path] ?: return null
            val d = parse(b)
            docs[path] = d
            index[path] = tags.associateWith { t -> d.getElementsByTagName(t).let { nl -> List(nl.length) { nl.item(it) as Element } } }
            return d
        }

        fun el(path: String, tag: String, n: Int): Element? = index[path]?.get(tag)?.getOrNull(n)

        fun put(path: String, d: Document) { docs[path] = d; deleted -= path }

        fun delete(path: String) { deleted += path; docs.remove(path); files.remove(path) }

        fun bytes(): ByteArray {
            for ((p, d) in docs) files[p] = serialize(d)
            val bo = ByteArrayOutputStream()
            ZipOutputStream(bo).use { z ->
                val keys = files.keys.sortedBy { if (it == "[Content_Types].xml") 0 else 1 }
                for (k in keys) {
                    if (k in deleted) continue
                    z.putNextEntry(ZipEntry(k))
                    z.write(files.getValue(k))
                    z.closeEntry()
                }
            }
            return bo.toByteArray()
        }
    }

    private fun readZip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val tmp = File.createTempFile("qo_ed", ".zip")
        try {
            tmp.writeBytes(bytes)
            try {
                ZipFile(tmp).use { z ->
                    for (e in z.entries()) if (!e.isDirectory) out[e.name.removePrefix("/")] = z.getInputStream(e).use { it.readBytes() }
                }
            } catch (_: Exception) {
            }
            if (out.isEmpty()) ZipInputStream(bytes.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory) out[e.name.removePrefix("/")] = z.readBytes()
                }
            }
        } finally {
            tmp.delete()
        }
        if (out.isEmpty()) throw IllegalArgumentException("not a zip")
        return out
    }

    private fun parse(b: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = false
        f.isValidating = false
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
        try { f.isExpandEntityReferences = false } catch (_: Exception) {}
        return f.newDocumentBuilder().parse(b.inputStream() as InputStream)
    }

    /** Oddiy, bashorat qilinadigan XML yozuvchi (Transformer'ning platformaga bog'liq xatti-harakatisiz). */
    private fun serialize(d: Document): ByteArray {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n")
        fun text(s: String, attr: Boolean) {
            for (ch in s) when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> if (attr) sb.append("&quot;") else sb.append(ch)
                '\n' -> if (attr) sb.append("&#10;") else sb.append(ch)
                '\r' -> sb.append("&#13;")
                '\t' -> if (attr) sb.append("&#9;") else sb.append(ch)
                else -> if (ch.code < 0x20) Unit else sb.append(ch)
            }
        }
        fun node(n: Node) {
            when (n.nodeType) {
                Node.ELEMENT_NODE -> {
                    val e = n as Element
                    sb.append('<').append(e.tagName)
                    val at = e.attributes
                    for (i in 0 until at.length) {
                        val a = at.item(i)
                        sb.append(' ').append(a.nodeName).append("=\"")
                        text(a.nodeValue ?: "", true)
                        sb.append('"')
                    }
                    if (!e.hasChildNodes()) sb.append("/>") else {
                        sb.append('>')
                        var c = e.firstChild
                        while (c != null) { node(c); c = c.nextSibling }
                        sb.append("</").append(e.tagName).append('>')
                    }
                }
                Node.TEXT_NODE -> text(n.nodeValue ?: "", false)
                Node.CDATA_SECTION_NODE -> sb.append("<![CDATA[").append(n.nodeValue).append("]]>")
                Node.COMMENT_NODE -> sb.append("<!--").append(n.nodeValue).append("-->")
                Node.PROCESSING_INSTRUCTION_NODE -> {
                    val pi = n as org.w3c.dom.ProcessingInstruction
                    sb.append("<?").append(pi.target).append(' ').append(pi.data).append("?>")
                }
                Node.ENTITY_REFERENCE_NODE -> { var c = n.firstChild; while (c != null) { node(c); c = c.nextSibling } }
            }
        }
        var c = d.firstChild
        while (c != null) { node(c); c = c.nextSibling }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    // ---------------------------------------------------------------- DOM yordamchilari

    private fun Element.kids(): List<Element> {
        val out = ArrayList<Element>()
        var c = firstChild
        while (c != null) { if (c is Element) out += c; c = c.nextSibling }
        return out
    }

    private fun Element.child(name: String): Element? = kids().firstOrNull { it.tagName == name }

    private fun Element.make(name: String, vararg attrs: Pair<String, String>): Element =
        ownerDocument.createElement(name).also { e -> attrs.forEach { (k, v) -> e.setAttribute(k, v) } }

    private fun insertAfter(ref: Node, n: Node) {
        val parent = ref.parentNode ?: return
        val next = ref.nextSibling
        if (next == null) parent.appendChild(n) else parent.insertBefore(n, next)
    }

    /** [name] bolasini sxema tartibida ([order]) o'rnatadi; attrs == null bo'lsa — o'chiradi. */
    private fun Element.setOrdered(name: String, order: List<String>, attrs: Map<String, String>?) {
        kids().filter { it.tagName == name }.forEach { removeChild(it) }
        if (attrs == null) return
        val e = ownerDocument.createElement(name)
        attrs.forEach { (k, v) -> e.setAttribute(k, v) }
        val idx = order.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it }
        val before = kids().firstOrNull { k -> order.indexOf(k.tagName).let { it >= 0 && it > idx } }
        if (before == null) appendChild(e) else insertBefore(e, before)
    }

    private class Id(val part: String, val tag: String, val n: Int)

    private fun id(s: String?): Id? {
        val p = s?.split('|') ?: return null
        if (p.size != 3) return null
        return Id(p[0], p[1], p[2].toIntOrNull() ?: return null)
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.has(k: String) = containsKey(k)

    // ---------------------------------------------------------------- umumiy kirish

    /** [kind]: "docx", "pptx", "xlsx". [json] — QO.collect() natijasi. */
    fun apply(bytes: ByteArray, kind: String, json: String): ByteArray {
        val root = Json.parseToJsonElement(json).jsonObject
        val pkg = Pkg(readZip(bytes))
        when (kind) {
            "docx" -> docx(pkg, root)
            "pptx" -> pptx(pkg, root)
            "xlsx" -> xlsx(pkg, root)
        }
        return pkg.bytes()
    }

    /** O'zgarishlar bormi (bo'sh JSON emasmi). */
    fun hasChanges(json: String): Boolean = try {
        val o = Json.parseToJsonElement(json).jsonObject
        listOf("paras", "cells", "removed", "boxes").any { (o[it] as? JsonArray)?.isNotEmpty() == true } ||
            (o["moved"] as? JsonObject)?.isNotEmpty() == true
    } catch (_: Exception) {
        false
    }

    // ---------------------------------------------------------------- paragraflar (DOCX + PPTX)

    private class Flavor(
        val docx: Boolean,
        val pathOf: (String) -> String?,
        val tags: List<String>,
    ) {
        val p = if (docx) "w:p" else "a:p"
        val pPr = if (docx) "w:pPr" else "a:pPr"
    }

    private fun runById(pkg: Pkg, fl: Flavor, s: String?): Element? {
        val i = id(s) ?: return null
        val path = fl.pathOf(i.part) ?: return null
        pkg.doc(path, fl.tags)
        return pkg.el(path, i.tag, i.n)
    }

    private fun paragraphs(pkg: Pkg, root: JsonObject, fl: Flavor) {
        val list = root["paras"] as? JsonArray ?: return
        // avval barcha kerakli qismlarni ochib, asl tartibni eslab qolamiz
        for (pe in list) id(pe.jsonObject.str("id"))?.let { i -> fl.pathOf(i.part)?.let { pkg.doc(it, fl.tags) } }
        for (pe in list) {
            val o = pe.jsonObject
            val i = id(o.str("id")) ?: continue
            val path = fl.pathOf(i.part) ?: continue
            pkg.doc(path, fl.tags) ?: continue
            val el = pkg.el(path, i.tag, i.n) ?: continue
            if (el.parentNode == null) continue
            val ps = o["ps"] as? JsonArray ?: continue
            if (ps.isEmpty()) {
                val keepSect = fl.docx && el.child("w:pPr")?.child("w:sectPr") != null
                if (keepSect) rebuild(pkg, fl, el, JsonObject(mapOf("rs" to JsonArray(emptyList()))))
                else el.parentNode.removeChild(el)
                continue
            }
            var prev: Element = el
            ps.forEachIndexed { k, pj ->
                val target = if (k == 0) el else newParaLike(el, fl).also { insertAfter(prev, it) }
                rebuild(pkg, fl, target, pj.jsonObject)
                prev = target
            }
        }
    }

    private fun newParaLike(src: Element, fl: Flavor): Element {
        val p = src.cloneNode(false) as Element
        listOf("w14:paraId", "w14:textId", "w:rsidR", "w:rsidRDefault", "w:rsidP").forEach { p.removeAttribute(it) }
        src.child(fl.pPr)?.let { ppr ->
            val c = ppr.cloneNode(true) as Element
            c.child("w:sectPr")?.let { c.removeChild(it) }
            p.appendChild(c)
        }
        if (!fl.docx) src.child("a:endParaRPr")?.let { p.appendChild(it.cloneNode(true)) }
        return p
    }

    private val markerStarts = setOf("w:bookmarkStart", "w:commentRangeStart", "w:permStart", "w:moveFromRangeStart", "w:moveToRangeStart")
    private val markerEnds = setOf("w:bookmarkEnd", "w:commentRangeEnd", "w:permEnd", "w:moveFromRangeEnd", "w:moveToRangeEnd")

    private fun rebuild(pkg: Pkg, fl: Flavor, p: Element, pj: JsonObject) {
        val doc = p.ownerDocument
        val kids = p.kids()
        val pPr = kids.firstOrNull { it.tagName == fl.pPr }
        val endRPr = if (fl.docx) null else kids.firstOrNull { it.tagName == "a:endParaRPr" }
        val starts = kids.filter { it.tagName in markerStarts }
        val ends = kids.filter { it.tagName in markerEnds }
        var c = p.firstChild
        while (c != null) { val n = c.nextSibling; if (c !== pPr && c !== endRPr) p.removeChild(c); c = n }
        starts.forEach { p.appendChild(it) }

        // tekislash
        pj.str("a")?.let { a ->
            if (fl.docx) {
                val ppr = pPr ?: doc.createElement("w:pPr").also { p.insertBefore(it, p.firstChild) }
                val v = when (a) { "center" -> "center"; "right" -> "right"; "justify" -> "both"; else -> "left" }
                ppr.setOrdered("w:jc", PPR_ORDER, mapOf("w:val" to v))
            } else {
                val ppr = pPr ?: doc.createElement("a:pPr").also { p.insertBefore(it, p.firstChild) }
                ppr.setAttribute("algn", when (a) { "center" -> "ctr"; "right" -> "r"; "justify" -> "just"; else -> "l" })
            }
        }

        val rs = pj["rs"] as? JsonArray ?: JsonArray(emptyList())
        var lastKey: String? = null
        var lastT: Element? = null
        fun add(n: Node) { if (endRPr != null) p.insertBefore(n, endRPr) else p.appendChild(n) }
        for (sj in rs) {
            val s = sj.jsonObject
            when {
                s.has("o") -> {
                    runById(pkg, fl, s.str("o"))?.let { add(it.cloneNode(true)) }
                    lastKey = null
                }
                s.has("br") -> {
                    val base = runById(pkg, fl, s.str("r"))
                    if (fl.docx) {
                        val r = doc.createElement("w:r")
                        base?.child("w:rPr")?.let { r.appendChild(it.cloneNode(true)) }
                        r.appendChild(doc.createElement("w:br"))
                        add(r)
                    } else {
                        val br = doc.createElement("a:br")
                        base?.child("a:rPr")?.let { br.appendChild(it.cloneNode(true)) }
                        add(br)
                    }
                    lastKey = null
                }
                s.has("tab") -> {
                    val base = runById(pkg, fl, s.str("r"))
                    if (fl.docx) {
                        val r = doc.createElement("w:r")
                        base?.child("w:rPr")?.let { r.appendChild(it.cloneNode(true)) }
                        r.appendChild(doc.createElement("w:tab"))
                        add(r)
                        lastKey = null
                    } else {
                        add(textRun(doc, fl, base, s, "\t").also { lastT = it.child("a:t") })
                        lastKey = null
                    }
                }
                else -> {
                    val t = s.str("t") ?: continue
                    val key = s.filterKeys { it != "t" }.toString()
                    val prevT = lastT
                    if (key == lastKey && prevT != null) {
                        prevT.textContent = prevT.textContent + t
                    } else {
                        val run = textRun(doc, fl, runById(pkg, fl, s.str("r")), s, t)
                        add(run)
                        lastT = run.child(if (fl.docx) "w:t" else "a:t")
                        lastKey = key
                    }
                }
            }
        }
        ends.forEach { add(it) }
    }

    private fun textRun(doc: Document, fl: Flavor, base: Element?, s: JsonObject, text: String): Element {
        if (fl.docx) {
            val r = doc.createElement("w:r")
            val rPr = (base?.child("w:rPr")?.cloneNode(true) as? Element) ?: doc.createElement("w:rPr")
            applyDocxProps(rPr, s)
            if (rPr.hasChildNodes()) r.appendChild(rPr)
            val t = doc.createElement("w:t")
            t.setAttribute("xml:space", "preserve")
            t.textContent = text
            r.appendChild(t)
            return r
        }
        val r = doc.createElement("a:r")
        val rPr = (base?.child("a:rPr")?.cloneNode(true) as? Element) ?: doc.createElement("a:rPr").also { it.setAttribute("lang", "ru-RU"); it.setAttribute("dirty", "0") }
        applyPptProps(rPr, s)
        r.appendChild(rPr)
        val t = doc.createElement("a:t")
        t.textContent = text
        r.appendChild(t)
        return r
    }

    private val RPR_ORDER = listOf(
        "w:rStyle", "w:rFonts", "w:b", "w:bCs", "w:i", "w:iCs", "w:caps", "w:smallCaps", "w:strike", "w:dstrike", "w:outline",
        "w:shadow", "w:emboss", "w:imprint", "w:noProof", "w:snapToGrid", "w:vanish", "w:webHidden", "w:color", "w:spacing",
        "w:w", "w:kern", "w:position", "w:sz", "w:szCs", "w:highlight", "w:u", "w:effect", "w:bdr", "w:shd", "w:fitText",
        "w:vertAlign", "w:rtl", "w:cs", "w:em", "w:lang", "w:eastAsianLayout", "w:specVanish", "w:oMath", "w:rPrChange",
    )
    private val PPR_ORDER = listOf(
        "w:pStyle", "w:keepNext", "w:keepLines", "w:pageBreakBefore", "w:framePr", "w:widowControl", "w:numPr",
        "w:suppressLineNumbers", "w:pBdr", "w:shd", "w:tabs", "w:suppressAutoHyphens", "w:kinsoku", "w:wordWrap",
        "w:overflowPunct", "w:topLinePunct", "w:autoSpaceDE", "w:autoSpaceDN", "w:bidi", "w:adjustRightInd", "w:snapToGrid",
        "w:spacing", "w:ind", "w:contextualSpacing", "w:mirrorIndents", "w:suppressOverlap", "w:jc", "w:textDirection",
        "w:textAlignment", "w:textboxTightWrap", "w:outlineLvl", "w:divId", "w:cnfStyle", "w:rPr", "w:sectPr", "w:pPrChange",
    )

    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.isNull(k: String) = this[k] is JsonNull

    private fun applyDocxProps(rPr: Element, s: JsonObject) {
        s.bool("b")?.let { on ->
            rPr.setOrdered("w:b", RPR_ORDER, if (on) emptyMap() else mapOf("w:val" to "0"))
            rPr.setOrdered("w:bCs", RPR_ORDER, if (on) emptyMap() else mapOf("w:val" to "0"))
        }
        s.bool("i")?.let { on ->
            rPr.setOrdered("w:i", RPR_ORDER, if (on) emptyMap() else mapOf("w:val" to "0"))
            rPr.setOrdered("w:iCs", RPR_ORDER, if (on) emptyMap() else mapOf("w:val" to "0"))
        }
        s.bool("u")?.let { on -> rPr.setOrdered("w:u", RPR_ORDER, mapOf("w:val" to if (on) "single" else "none")) }
        s.bool("s")?.let { on ->
            rPr.setOrdered("w:dstrike", RPR_ORDER, null)
            rPr.setOrdered("w:strike", RPR_ORDER, if (on) emptyMap() else mapOf("w:val" to "0"))
        }
        s.str("c")?.let { c -> rPr.setOrdered("w:color", RPR_ORDER, mapOf("w:val" to c)) }
        s.num("sz")?.let { pt ->
            val v = (pt * 2).roundToLong().coerceIn(2, 3276).toString()
            rPr.setOrdered("w:sz", RPR_ORDER, mapOf("w:val" to v))
            rPr.setOrdered("w:szCs", RPR_ORDER, mapOf("w:val" to v))
        }
        if (s.has("bg")) {
            rPr.setOrdered("w:highlight", RPR_ORDER, null)
            val bg = s.str("bg")
            rPr.setOrdered("w:shd", RPR_ORDER, bg?.let { mapOf("w:val" to "clear", "w:color" to "auto", "w:fill" to it) })
        }
    }

    private val pptFills = setOf("a:noFill", "a:solidFill", "a:gradFill", "a:blipFill", "a:pattFill", "a:grpFill")
    private val pptAfterHighlight = listOf("a:uLnTx", "a:uLn", "a:uFillTx", "a:uFill", "a:latin", "a:ea", "a:cs", "a:sym", "a:hlinkClick", "a:hlinkMouseOver", "a:rtl", "a:extLst")

    private fun applyPptProps(rPr: Element, s: JsonObject) {
        s.bool("b")?.let { rPr.setAttribute("b", if (it) "1" else "0") }
        s.bool("i")?.let { rPr.setAttribute("i", if (it) "1" else "0") }
        s.bool("u")?.let { rPr.setAttribute("u", if (it) "sng" else "none") }
        s.bool("s")?.let { rPr.setAttribute("strike", if (it) "sngStrike" else "noStrike") }
        s.num("sz")?.let { rPr.setAttribute("sz", (it * 100).roundToLong().coerceIn(100, 400000).toString()) }
        s.str("c")?.let { c ->
            rPr.kids().filter { it.tagName in pptFills }.forEach { rPr.removeChild(it) }
            val fill = rPr.make("a:solidFill")
            fill.appendChild(rPr.make("a:srgbClr", "val" to c))
            val ln = rPr.child("a:ln")
            if (ln != null) insertAfter(ln, fill) else rPr.insertBefore(fill, rPr.firstChild)
        }
        if (s.has("bg")) {
            rPr.kids().filter { it.tagName == "a:highlight" }.forEach { rPr.removeChild(it) }
            s.str("bg")?.let { bg ->
                val h = rPr.make("a:highlight")
                h.appendChild(rPr.make("a:srgbClr", "val" to bg))
                val before = rPr.kids().firstOrNull { it.tagName in pptAfterHighlight }
                if (before == null) rPr.appendChild(h) else rPr.insertBefore(h, before)
            }
        }
    }

    /** Bo'sh qolgan katak/matn qutisiga bitta bo'sh paragraf (format talabi). */
    private fun ensureParagraphs(d: Document, container: String, para: String) {
        val nl = d.getElementsByTagName(container)
        for (i in 0 until nl.length) {
            val e = nl.item(i) as Element
            if (e.kids().none { it.tagName == para }) {
                val p = d.createElement(para)
                val tail = e.kids().lastOrNull { it.tagName == "w:sdt" || it.tagName == "a:extLst" }
                if (tail != null && tail.tagName == "a:extLst") e.insertBefore(p, tail) else e.appendChild(p)
            }
        }
    }

    // ---------------------------------------------------------------- DOCX

    private fun docx(pkg: Pkg, root: JsonObject) {
        val fl = Flavor(true, { if (it == "d") "word/document.xml" else null }, HtmlRenderer.DOCX_TAGS)
        paragraphs(pkg, root, fl)
        pkg.docs["word/document.xml"]?.let { ensureParagraphs(it, "w:tc", "w:p") }
    }

    // ---------------------------------------------------------------- PPTX

    private fun rels(pkg: Pkg, path: String): Map<String, String> {
        val d = pkg.doc(relsPath(path)) ?: return emptyMap()
        val dir = path.substringBeforeLast('/', "")
        val out = HashMap<String, String>()
        val nl = d.getElementsByTagName("Relationship")
        for (i in 0 until nl.length) {
            val e = nl.item(i) as Element
            if (e.getAttribute("TargetMode") == "External") continue
            out[e.getAttribute("Id")] = resolve(dir, e.getAttribute("Target"))
        }
        return out
    }

    private fun relsPath(path: String): String {
        val dir = path.substringBeforeLast('/', "")
        return (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + path.substringAfterLast('/') + ".rels"
    }

    private fun resolve(dir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = ArrayList(if (dir.isEmpty()) emptyList() else dir.split('/'))
        for (seg in target.split('/')) when (seg) {
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            ".", "" -> {}
            else -> parts += seg
        }
        return parts.joinToString("/")
    }

    private class SlideRef(val sldId: Element, val rid: String, val path: String)

    /** Slaydlar tartibi — [HtmlRenderer.pptx] bilan bir xil qoida. */
    private fun slides(pkg: Pkg): List<SlideRef> {
        val pres = pkg.doc("ppt/presentation.xml") ?: return emptyList()
        val r = rels(pkg, "ppt/presentation.xml")
        val out = ArrayList<SlideRef>()
        val nl = pres.getElementsByTagName("p:sldId")
        for (i in 0 until nl.length) {
            val e = nl.item(i) as Element
            val rid = e.getAttribute("r:id")
            val path = r[rid] ?: continue
            if (pkg.files.containsKey(path)) out += SlideRef(e, rid, path)
        }
        return out
    }

    private fun slidePaths(pkg: Pkg): List<String> {
        val s = slides(pkg).map { it.path }
        if (s.isNotEmpty()) return s
        return pkg.files.keys.filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    }

    private fun pptx(pkg: Pkg, root: JsonObject) {
        val paths = slidePaths(pkg)
        val tags = HtmlRenderer.PPTX_TAGS
        val pathOf: (String) -> String? = { paths.getOrNull(it.toIntOrNull() ?: -1) }
        val fl = Flavor(false, pathOf, tags)
        // barcha qismlar oldindan (asl tartib)
        (root["moved"] as? JsonObject)?.keys?.forEach { k -> id(k)?.let { i -> pathOf(i.part)?.let { pkg.doc(it, tags) } } }
        (root["removed"] as? JsonArray)?.forEach { k -> id(k.jsonPrimitive.content)?.let { i -> pathOf(i.part)?.let { pkg.doc(it, tags) } } }
        paragraphs(pkg, root, fl)
        // surilgan / o'lchami o'zgargan shakllar
        (root["moved"] as? JsonObject)?.forEach { (k, v) ->
            val i = id(k) ?: return@forEach
            val path = pathOf(i.part) ?: return@forEach
            val el = pkg.el(path, i.tag, i.n) ?: return@forEach
            val o = v.jsonObject
            setXfrm(el, o.num("x") ?: return@forEach, o.num("y") ?: 0.0, o.num("w") ?: 10.0, o.num("h") ?: 10.0)
        }
        // o'chirilgan shakllar
        (root["removed"] as? JsonArray)?.forEach { k ->
            val i = id(k.jsonPrimitive.content) ?: return@forEach
            val path = pathOf(i.part) ?: return@forEach
            pkg.el(path, i.tag, i.n)?.let { it.parentNode?.removeChild(it) }
        }
        // yangi matn qutilari
        (root["boxes"] as? JsonArray)?.forEach { bj ->
            val b = bj.jsonObject
            val path = paths.getOrNull((b["slide"] as? JsonPrimitive)?.intOrNull ?: -1) ?: return@forEach
            val d = pkg.doc(path, tags) ?: return@forEach
            addTextBox(pkg, fl, d, b)
        }
        for (p in paths) pkg.docs[p]?.let { d -> ensureParagraphs(d, "p:txBody", "a:p"); ensureParagraphs(d, "a:txBody", "a:p") }
    }

    private fun emu(pt: Double) = (pt * 12700).roundToLong().toString()

    private fun setXfrm(el: Element, x: Double, y: Double, w: Double, h: Double) {
        val xf: Element = when (el.tagName) {
            "p:graphicFrame" -> el.child("p:xfrm") ?: el.make("p:xfrm").also { n ->
                val nv = el.child("p:nvGraphicFramePr")
                if (nv != null) insertAfter(nv, n) else el.insertBefore(n, el.firstChild)
            }
            else -> {
                val holderName = if (el.tagName == "p:grpSp") "p:grpSpPr" else "p:spPr"
                val holder = el.child(holderName) ?: el.make(holderName).also { n ->
                    val nv = el.kids().firstOrNull { it.tagName.startsWith("p:nv") }
                    if (nv != null) insertAfter(nv, n) else el.insertBefore(n, el.firstChild)
                }
                holder.child("a:xfrm") ?: holder.make("a:xfrm").also { holder.insertBefore(it, holder.firstChild) }
            }
        }
        val off = xf.child("a:off") ?: xf.make("a:off").also { xf.insertBefore(it, xf.firstChild) }
        val ext = xf.child("a:ext") ?: xf.make("a:ext").also { insertAfter(off, it) }
        off.setAttribute("x", emu(x)); off.setAttribute("y", emu(y))
        ext.setAttribute("cx", emu(maxOf(1.0, w))); ext.setAttribute("cy", emu(maxOf(1.0, h)))
    }

    private fun addTextBox(pkg: Pkg, fl: Flavor, d: Document, b: JsonObject) {
        val tree = d.getElementsByTagName("p:spTree").item(0) as? Element ?: return
        var maxId = 1
        val nl = d.getElementsByTagName("p:cNvPr")
        for (i in 0 until nl.length) (nl.item(i) as Element).getAttribute("id").toIntOrNull()?.let { if (it > maxId) maxId = it }
        val sp = d.createElement("p:sp")
        val nv = sp.make("p:nvSpPr")
        nv.appendChild(sp.make("p:cNvPr", "id" to "${maxId + 1}", "name" to "TextBox ${maxId + 1}"))
        nv.appendChild(sp.make("p:cNvSpPr", "txBox" to "1"))
        nv.appendChild(sp.make("p:nvPr"))
        sp.appendChild(nv)
        val spPr = sp.make("p:spPr")
        val xf = sp.make("a:xfrm")
        xf.appendChild(sp.make("a:off", "x" to emu(b.num("x") ?: 50.0), "y" to emu(b.num("y") ?: 50.0)))
        xf.appendChild(sp.make("a:ext", "cx" to emu(b.num("w") ?: 300.0), "cy" to emu(b.num("h") ?: 40.0)))
        spPr.appendChild(xf)
        val geom = sp.make("a:prstGeom", "prst" to "rect")
        geom.appendChild(sp.make("a:avLst"))
        spPr.appendChild(geom)
        spPr.appendChild(sp.make("a:noFill"))
        sp.appendChild(spPr)
        val tx = sp.make("p:txBody")
        val bp = sp.make("a:bodyPr", "wrap" to "square", "rtlCol" to "0")
        bp.appendChild(sp.make("a:spAutoFit"))
        tx.appendChild(bp)
        tx.appendChild(sp.make("a:lstStyle"))
        (b["ps"] as? JsonArray)?.forEach { pj ->
            val p = sp.make("a:p")
            tx.appendChild(p)
            rebuild(pkg, fl, p, pj.jsonObject)
        }
        if (tx.kids().none { it.tagName == "a:p" }) tx.appendChild(sp.make("a:p"))
        sp.appendChild(tx)
        val ext = tree.child("p:extLst")
        if (ext != null) tree.insertBefore(sp, ext) else tree.appendChild(sp)
    }

    private const val SLIDE_CT = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"

    /** [index]-slaydning nusxasini undan keyin qo'yadi. */
    fun duplicateSlide(bytes: ByteArray, index: Int): ByteArray {
        val pkg = Pkg(readZip(bytes))
        val list = slides(pkg)
        val src = list.getOrNull(index) ?: return bytes
        val pres = pkg.doc("ppt/presentation.xml")!!
        val presRels = pkg.doc(relsPath("ppt/presentation.xml")) ?: return bytes
        val n = (pkg.files.keys.mapNotNull { Regex("ppt/slides/slide(\\d+)\\.xml").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val newPath = "ppt/slides/slide$n.xml"
        pkg.files[newPath] = pkg.files.getValue(src.path)
        // munosabatlar: izohlar (notes) va sharhlar nusxalanmaydi
        pkg.files[relsPath(src.path)]?.let { rb ->
            val rd = parse(rb)
            val nl = rd.getElementsByTagName("Relationship")
            for (i in nl.length - 1 downTo 0) {
                val e = nl.item(i) as Element
                val t = e.getAttribute("Type")
                if (t.endsWith("/notesSlide") || t.endsWith("/comments") || t.endsWith("/comment")) e.parentNode.removeChild(e)
            }
            pkg.files[relsPath(newPath)] = serialize(rd)
        }
        pkg.doc("[Content_Types].xml")?.let { ct ->
            val o = ct.createElement("Override")
            o.setAttribute("PartName", "/$newPath")
            o.setAttribute("ContentType", SLIDE_CT)
            ct.documentElement.appendChild(o)
        }
        val ids = HashSet<String>()
        val rl = presRels.getElementsByTagName("Relationship")
        var srcType = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"
        for (i in 0 until rl.length) {
            val e = rl.item(i) as Element
            ids += e.getAttribute("Id")
            if (e.getAttribute("Id") == src.rid) srcType = e.getAttribute("Type")
        }
        var k = ids.size + 1
        while ("rIdQ$k" in ids) k++
        val rid = "rIdQ$k"
        val rel = presRels.createElement("Relationship")
        rel.setAttribute("Id", rid)
        rel.setAttribute("Type", srcType)
        rel.setAttribute("Target", "slides/slide$n.xml")
        presRels.documentElement.appendChild(rel)
        var maxId = 255L
        list.forEach { it.sldId.getAttribute("id").toLongOrNull()?.let { v -> if (v > maxId) maxId = v } }
        val newId = (maxId + 1).toString()
        val el = pres.createElement("p:sldId")
        el.setAttribute("id", newId)
        el.setAttribute("r:id", rid)
        insertAfter(src.sldId, el)
        // bo'limlar (p14:sectionLst) ichida ham shu joyga
        val sec = pres.getElementsByTagName("p14:sldId")
        for (i in 0 until sec.length) {
            val e = sec.item(i) as Element
            if (e.getAttribute("id") == src.sldId.getAttribute("id")) {
                val c = e.cloneNode(false) as Element
                c.setAttribute("id", newId)
                insertAfter(e, c)
                break
            }
        }
        return pkg.bytes()
    }

    /** [index]-slaydni (va uning izohlarini) o'chiradi. Oxirgi slayd o'chirilmaydi. */
    fun deleteSlide(bytes: ByteArray, index: Int): ByteArray {
        val pkg = Pkg(readZip(bytes))
        val list = slides(pkg)
        if (list.size <= 1) return bytes
        val src = list.getOrNull(index) ?: return bytes
        val pres = pkg.doc("ppt/presentation.xml")!!
        val presRels = pkg.doc(relsPath("ppt/presentation.xml"))
        val oldId = src.sldId.getAttribute("id")
        src.sldId.parentNode.removeChild(src.sldId)
        val sec = pres.getElementsByTagName("p14:sldId")
        for (i in sec.length - 1 downTo 0) {
            val e = sec.item(i) as Element
            if (e.getAttribute("id") == oldId) e.parentNode.removeChild(e)
        }
        presRels?.getElementsByTagName("Relationship")?.let { rl ->
            for (i in rl.length - 1 downTo 0) {
                val e = rl.item(i) as Element
                if (e.getAttribute("Id") == src.rid) e.parentNode.removeChild(e)
            }
        }
        val gone = arrayListOf(src.path)
        rels(pkg, src.path).values.filter { it.contains("notesSlides/") }.forEach { gone += it }
        val ct = pkg.doc("[Content_Types].xml")
        for (p in gone) {
            pkg.delete(p)
            pkg.delete(relsPath(p))
            ct?.getElementsByTagName("Override")?.let { ov ->
                for (i in ov.length - 1 downTo 0) {
                    val e = ov.item(i) as Element
                    if (e.getAttribute("PartName") == "/$p") e.parentNode.removeChild(e)
                }
            }
        }
        return pkg.bytes()
    }

    // ---------------------------------------------------------------- XLSX

    private fun sheetPaths(pkg: Pkg): List<String> {
        val wb = pkg.doc("xl/workbook.xml") ?: return emptyList()
        val r = rels(pkg, "xl/workbook.xml")
        val out = ArrayList<String>()
        val nl = wb.getElementsByTagName("sheet")
        for (i in 0 until nl.length) {
            val e = nl.item(i) as Element
            r[e.getAttribute("r:id")]?.let { out += it }
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) { if (c.isLetter()) n = n * 26 + (c.uppercaseChar() - 'A' + 1) else break }
        return n
    }

    private fun xlsx(pkg: Pkg, root: JsonObject) {
        val list = root["cells"] as? JsonArray ?: return
        val paths = sheetPaths(pkg)
        var droppedFormula = false
        for (cj in list) {
            val o = cj.jsonObject
            val key = o.str("c") ?: continue
            val v = o.str("v") ?: ""
            val si = key.substringBefore('|').toIntOrNull() ?: continue
            val ref = key.substringAfter('|')
            val path = paths.getOrNull(si) ?: continue
            val d = pkg.doc(path) ?: continue
            val data = d.getElementsByTagName("sheetData").item(0) as? Element ?: continue
            val rowNum = ref.dropWhile { it.isLetter() }.toIntOrNull() ?: continue
            val col = colIndex(ref)
            // qator
            var row: Element? = null
            var before: Element? = null
            for (r in data.kids()) {
                if (r.tagName != "row") continue
                val rn = r.getAttribute("r").toIntOrNull() ?: continue
                if (rn == rowNum) { row = r; break }
                if (rn > rowNum) { before = r; break }
            }
            if (row == null) {
                row = d.createElement("row").also { it.setAttribute("r", rowNum.toString()) }
                if (before != null) data.insertBefore(row, before) else data.appendChild(row)
            }
            row!!.removeAttribute("spans")
            // katak
            var cell: Element? = null
            var cb: Element? = null
            for (c in row.kids()) {
                if (c.tagName != "c") continue
                val ci = colIndex(c.getAttribute("r"))
                if (ci == col) { cell = c; break }
                if (ci > col) { cb = c; break }
            }
            if (cell == null) {
                cell = d.createElement("c").also { it.setAttribute("r", ref) }
                // ustun uslubi bo'lsa — saqlanadi (Excel o'zi qo'llaydi)
                if (cb != null) row.insertBefore(cell, cb) else {
                    val ext = row.child("extLst")
                    if (ext != null) row.insertBefore(cell, ext) else row.appendChild(cell)
                }
            }
            val c = cell!!
            c.kids().filter { it.tagName == "f" || it.tagName == "v" || it.tagName == "is" }.forEach {
                if (it.tagName == "f") droppedFormula = true
                c.removeChild(it)
            }
            val ext = c.child("extLst")
            fun put(e: Element) { if (ext != null) c.insertBefore(e, ext) else c.appendChild(e) }
            val num = number(v)
            when {
                v.isEmpty() -> c.removeAttribute("t")
                num != null -> {
                    c.removeAttribute("t")
                    put(d.createElement("v").also { it.textContent = num })
                }
                else -> {
                    c.setAttribute("t", "inlineStr")
                    val isEl = d.createElement("is")
                    val t = d.createElement("t")
                    t.setAttribute("xml:space", "preserve")
                    t.textContent = v
                    isEl.appendChild(t)
                    put(isEl)
                }
            }
        }
        if (droppedFormula) dropCalcChain(pkg)
    }

    /** Formulasi olib tashlangan katak bo'lsa, eski hisob zanjiri Excel'da xato beradi — o'chiramiz. */
    private fun dropCalcChain(pkg: Pkg) {
        if (!pkg.files.containsKey("xl/calcChain.xml")) return
        pkg.delete("xl/calcChain.xml")
        pkg.doc("xl/_rels/workbook.xml.rels")?.getElementsByTagName("Relationship")?.let { rl ->
            for (i in rl.length - 1 downTo 0) {
                val e = rl.item(i) as Element
                if (e.getAttribute("Target").endsWith("calcChain.xml")) e.parentNode.removeChild(e)
            }
        }
        pkg.doc("[Content_Types].xml")?.getElementsByTagName("Override")?.let { ov ->
            for (i in ov.length - 1 downTo 0) {
                val e = ov.item(i) as Element
                if (e.getAttribute("PartName") == "/xl/calcChain.xml") e.parentNode.removeChild(e)
            }
        }
        pkg.doc("xl/workbook.xml")?.getElementsByTagName("calcPr")?.item(0)?.let { (it as Element).setAttribute("fullCalcOnLoad", "1") }
    }

    /** Matnni Excel soniga aylantiradi: "1 234,5", "12%", "3.5e2", "01.02.2024" (sana). Aks holda null. */
    fun number(raw: String): String? {
        var s = raw.trim().replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        Regex("(\\d{1,2})[./](\\d{1,2})[./](\\d{4})").matchEntire(s)?.let { m ->
            val (dd, mm, yy) = m.destructured
            val cal = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(yy.toInt(), mm.toInt() - 1, dd.toInt())
            val base = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(1899, 11, 30) }
            if (mm.toInt() !in 1..12 || dd.toInt() !in 1..31) return null
            return ((cal.timeInMillis - base.timeInMillis) / 86_400_000L).toString()
        }
        var pct = false
        if (s.endsWith("%")) { pct = true; s = s.dropLast(1) }
        if (s.count { it == ',' } == 1 && !s.contains('.')) s = s.replace(',', '.')
        if (!Regex("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?").matches(s)) return null
        if (Regex("-?0\\d.*").matches(s)) return null // "007" — matn (kod, telefon)
        if (s.replace("-", "").replace(".", "").length > 15) return null // uzun raqamlar (karta, ID) — matn
        var d = s.toDouble()
        if (pct) d /= 100.0
        return if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()
    }
}
