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

    private class Pkg(val files: LinkedHashMap<String, ByteArray>, val images: Map<String, ByteArray> = emptyMap()) {
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
    fun apply(bytes: ByteArray, kind: String, json: String, images: Map<String, ByteArray> = emptyMap()): ByteArray {
        val root = Json.parseToJsonElement(json).jsonObject
        val pkg = Pkg(readZip(bytes), images)
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
        listOf("paras", "cells", "removed", "boxes", "tables").any { (o[it] as? JsonArray)?.isNotEmpty() == true } ||
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

        if (fl.docx) docxParaProps(pkg, p, pj)

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
                s.has("img") -> {
                    if (fl.docx) docxImageRun(pkg, doc, s.str("img") ?: "", s.num("w") ?: 200.0, s.num("h") ?: 150.0)?.let { add(it) }
                    lastKey = null
                }
                s.has("pb") -> {
                    if (fl.docx) {
                        val r = doc.createElement("w:r")
                        r.appendChild(doc.createElement("w:br").also { it.setAttribute("w:type", "page") })
                        add(r)
                    }
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
        s.str("ff")?.takeIf { it.isNotBlank() && it != "sans-serif" && it != "serif" }?.let { f ->
            rPr.setOrdered("w:rFonts", RPR_ORDER, mapOf("w:ascii" to f, "w:hAnsi" to f, "w:cs" to f, "w:eastAsia" to f))
        }
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
        s.str("ff")?.takeIf { it.isNotBlank() && it != "sans-serif" && it != "serif" }?.let { f ->
            rPr.kids().filter { it.tagName == "a:latin" || it.tagName == "a:cs" }.forEach { rPr.removeChild(it) }
            val lat = rPr.make("a:latin", "typeface" to f)
            val before = rPr.kids().firstOrNull { it.tagName in listOf("a:ea", "a:sym", "a:hlinkClick", "a:hlinkMouseOver", "a:rtl", "a:extLst") }
            if (before == null) rPr.appendChild(lat) else rPr.insertBefore(lat, before)
        }
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
        pkg.doc("word/document.xml", fl.tags)
        paragraphs(pkg, root, fl)
        (root["tables"] as? JsonArray)?.forEach { insertDocxTable(pkg, fl, it.jsonObject) }
        pkg.docs["word/document.xml"]?.let { ensureParagraphs(it, "w:tc", "w:p") }
    }

    // ---------------------------------------------------------------- DOCX: paragraf xususiyatlari, jadval, rasm

    private fun Element.ensure(name: String, order: List<String>): Element {
        child(name)?.let { return it }
        val e = ownerDocument.createElement(name)
        val idx = order.indexOf(name)
        val before = kids().firstOrNull { k -> order.indexOf(k.tagName).let { it >= 0 && it > idx } }
        if (before == null) appendChild(e) else insertBefore(e, before)
        return e
    }

    private fun docxParaProps(pkg: Pkg, p: Element, pj: JsonObject) {
        val l = pj.str("l")
        val ind = (pj["ind"] as? JsonPrimitive)?.intOrNull ?: 0
        val ls = pj.num("ls")
        val sa = pj.num("sa")
        if (l == null && ind == 0 && ls == null && sa == null) return
        val doc = p.ownerDocument
        val pPr = p.child("w:pPr") ?: doc.createElement("w:pPr").also { p.insertBefore(it, p.firstChild) }
        if (l != null) {
            pPr.kids().filter { it.tagName == "w:numPr" }.forEach { pPr.removeChild(it) }
            val numId = when (l) { "bullet" -> listNum(pkg, false); "number" -> listNum(pkg, true); else -> "0" }
            val np = pPr.ensure("w:numPr", PPR_ORDER)
            np.appendChild(doc.createElement("w:ilvl").also { it.setAttribute("w:val", "0") })
            np.appendChild(doc.createElement("w:numId").also { it.setAttribute("w:val", numId) })
            if (l == "none") pPr.child("w:ind")?.let { i -> i.removeAttribute("w:hanging") }
        }
        if (ind != 0) {
            val e = pPr.ensure("w:ind", PPR_ORDER)
            val cur = (e.getAttribute("w:left").ifEmpty { e.getAttribute("w:start") }).toIntOrNull() ?: if (l == "bullet" || l == "number") 720 else 0
            val v = (cur + ind * 720).coerceAtLeast(0)
            e.setAttribute("w:left", v.toString())
            if (e.hasAttribute("w:start")) e.setAttribute("w:start", v.toString())
        }
        if (ls != null || sa != null) {
            val e = pPr.ensure("w:spacing", PPR_ORDER)
            ls?.let { e.setAttribute("w:line", (it * 240).roundToLong().toString()); e.setAttribute("w:lineRule", "auto") }
            sa?.let { e.setAttribute("w:after", (it * 20).roundToLong().toString()) }
        }
    }

    private val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    /** Ro'yxat ta'rifi (numbering.xml): kerak bo'lsa yaratadi, numId qaytaradi. */
    private fun listNum(pkg: Pkg, numbered: Boolean): String {
        val path = "word/numbering.xml"
        var d = pkg.doc(path)
        if (d == null) {
            pkg.files[path] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:numbering xmlns:w=\"$W_NS\"/>".toByteArray()
            d = pkg.doc(path)!!
            addRel(pkg, "word/document.xml", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering", "numbering.xml")
            addOverride(pkg, "/word/numbering.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml")
        }
        val marker = if (numbered) "qo-number" else "qo-bullet"
        // avval yaratganimiz bo'lsa — qayta ishlatamiz
        val abs = d.getElementsByTagName("w:abstractNum")
        var absId: String? = null
        var maxAbs = 0
        for (i in 0 until abs.length) {
            val e = abs.item(i) as Element
            e.getAttribute("w:abstractNumId").toIntOrNull()?.let { if (it > maxAbs) maxAbs = it }
            if (e.child("w:name")?.getAttribute("w:val") == marker) absId = e.getAttribute("w:abstractNumId")
        }
        val nums = d.getElementsByTagName("w:num")
        var maxNum = 0
        for (i in 0 until nums.length) {
            val e = nums.item(i) as Element
            e.getAttribute("w:numId").toIntOrNull()?.let { if (it > maxNum) maxNum = it }
            if (absId != null && e.child("w:abstractNumId")?.getAttribute("w:val") == absId) return e.getAttribute("w:numId")
        }
        val root = d.documentElement
        if (absId == null) {
            absId = (maxAbs + 1).toString()
            val a = fragment(d, """<w:abstractNum w:abstractNumId="$absId"><w:multiLevelType w:val="hybridMultilevel"/><w:name w:val="$marker"/>""" +
                (0 until 9).joinToString("") { lvl ->
                    val left = 720 * (lvl + 1)
                    if (numbered) """<w:lvl w:ilvl="$lvl"><w:start w:val="1"/><w:numFmt w:val="${listOf("decimal", "lowerLetter", "lowerRoman")[lvl % 3]}"/><w:lvlText w:val="%${lvl + 1}."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="$left" w:hanging="360"/></w:pPr></w:lvl>"""
                    else """<w:lvl w:ilvl="$lvl"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="${listOf("•", "◦", "▪")[lvl % 3]}"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="$left" w:hanging="360"/></w:pPr></w:lvl>"""
                } + "</w:abstractNum>")
            val firstNum = root.kids().firstOrNull { it.tagName == "w:num" || it.tagName == "w:numIdMacAtCleanup" }
            if (firstNum != null) root.insertBefore(a, firstNum) else root.appendChild(a)
        }
        val numId = (maxNum + 1).toString()
        val n = fragment(d, """<w:num w:numId="$numId"><w:abstractNumId w:val="$absId"/></w:num>""")
        val cleanup = root.child("w:numIdMacAtCleanup")
        if (cleanup != null) root.insertBefore(n, cleanup) else root.appendChild(n)
        return numId
    }

    /** XML bo'lagini (prefikslar bilan) shu hujjatga import qiladi. */
    private fun fragment(d: Document, xml: String): Element {
        val f = parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?><qo-root>$xml</qo-root>".toByteArray(Charsets.UTF_8))
        val first = f.documentElement.kids().first()
        return d.importNode(first, true) as Element
    }

    private fun addRel(pkg: Pkg, source: String, type: String, target: String): String {
        val rp = relsPath(source)
        if (!pkg.files.containsKey(rp)) pkg.files[rp] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>".toByteArray()
        val d = pkg.doc(rp)!!
        val ids = HashSet<String>()
        val nl = d.getElementsByTagName("Relationship")
        for (i in 0 until nl.length) ids += (nl.item(i) as Element).getAttribute("Id")
        var k = ids.size + 1
        while ("rIdQ$k" in ids) k++
        val e = d.createElement("Relationship")
        e.setAttribute("Id", "rIdQ$k"); e.setAttribute("Type", type); e.setAttribute("Target", target)
        d.documentElement.appendChild(e)
        return "rIdQ$k"
    }

    private fun addOverride(pkg: Pkg, part: String, ct: String) {
        val d = pkg.doc("[Content_Types].xml") ?: return
        val nl = d.getElementsByTagName("Override")
        for (i in 0 until nl.length) if ((nl.item(i) as Element).getAttribute("PartName") == part) return
        val o = d.createElement("Override")
        o.setAttribute("PartName", part); o.setAttribute("ContentType", ct)
        d.documentElement.appendChild(o)
    }

    private fun addDefault(pkg: Pkg, ext: String, ct: String) {
        val d = pkg.doc("[Content_Types].xml") ?: return
        val nl = d.getElementsByTagName("Default")
        for (i in 0 until nl.length) if ((nl.item(i) as Element).getAttribute("Extension").equals(ext, true)) return
        val o = d.createElement("Default")
        o.setAttribute("Extension", ext); o.setAttribute("ContentType", ct)
        d.documentElement.insertBefore(o, d.documentElement.firstChild)
    }

    private fun ensureNs(root: Element, vararg ns: Pair<String, String>) {
        for ((k, v) in ns) if (!root.hasAttribute(k)) root.setAttribute(k, v)
    }

    /** Yangi rasm: word/media ga qo'shadi, munosabat yaratadi, w:drawing run qaytaradi. */
    private fun docxImageRun(pkg: Pkg, doc: Document, key: String, wPt: Double, hPt: Double): Element? {
        val bytes = pkg.images[key] ?: return null
        val png = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val ext = if (png) "png" else "jpeg"
        var n = 1
        while (pkg.files.containsKey("word/media/qo_image$n.$ext")) n++
        val path = "word/media/qo_image$n.$ext"
        pkg.files[path] = bytes
        addDefault(pkg, ext, if (png) "image/png" else "image/jpeg")
        val rid = addRel(pkg, "word/document.xml", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image", "media/qo_image$n.$ext")
        ensureNs(doc.documentElement,
            "xmlns:wp" to "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing",
            "xmlns:r" to R_NS,
            "xmlns:a" to "http://schemas.openxmlformats.org/drawingml/2006/main",
            "xmlns:pic" to "http://schemas.openxmlformats.org/drawingml/2006/picture")
        var maxDoc = 0
        val dp = doc.getElementsByTagName("wp:docPr")
        for (i in 0 until dp.length) (dp.item(i) as Element).getAttribute("id").toIntOrNull()?.let { if (it > maxDoc) maxDoc = it }
        val id = maxDoc + 1
        val cx = emu(wPt); val cy = emu(hPt)
        return fragment(doc, """<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:docPr id="$id" name="Picture $id"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$id" name="qo_image$n.$ext"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>""")
    }

    private fun insertDocxTable(pkg: Pkg, fl: Flavor, t: JsonObject) {
        val d = pkg.docs["word/document.xml"] ?: return
        val body = d.getElementsByTagName("w:body").item(0) as? Element ?: return
        val rows = t["rows"] as? JsonArray ?: return
        val cols = rows.maxOfOrNull { (it as? JsonArray)?.size ?: 0 } ?: 0
        if (cols == 0) return
        // jadval tanadagi (w:body) blokdan keyin qo'yiladi
        var anchor: Node? = id(t.str("after"))?.let { i -> pkg.el("word/document.xml", i.tag, i.n) }
        while (anchor != null && anchor.parentNode != null && anchor.parentNode !== body) anchor = anchor.parentNode
        if (anchor?.parentNode == null) anchor = null
        // sahifa sozlamalari va jadval o'lchami
        val sect = body.kids().lastOrNull { it.tagName == "w:sectPr" }
        val pgW = sect?.child("w:pgSz")?.getAttribute("w:w")?.toIntOrNull() ?: 11906
        val mar = sect?.child("w:pgMar")?.let { (it.getAttribute("w:left").toIntOrNull() ?: 1440) + (it.getAttribute("w:right").toIntOrNull() ?: 1440) } ?: 2880
        val colW = ((pgW - mar) / cols).coerceAtLeast(400)
        val sb = StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>")
        for (b in listOf("top", "left", "bottom", "right", "insideH", "insideV")) sb.append("<w:$b w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>")
        sb.append("</w:tblBorders><w:tblLook w:val=\"04A0\"/></w:tblPr><w:tblGrid>")
        repeat(cols) { sb.append("<w:gridCol w:w=\"$colW\"/>") }
        sb.append("</w:tblGrid>")
        for (r in rows) {
            sb.append("<w:tr>")
            val cells = r as? JsonArray ?: continue
            for (c in 0 until cols) sb.append("<w:tc><w:tcPr><w:tcW w:w=\"$colW\" w:type=\"dxa\"/></w:tcPr><w:p/></w:tc>")
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
        val tbl = fragment(d, sb.toString())
        // kataklar matni
        val trs = tbl.kids().filter { it.tagName == "w:tr" }
        rows.forEachIndexed { ri, r ->
            val tcs = trs.getOrNull(ri)?.kids()?.filter { it.tagName == "w:tc" } ?: return@forEachIndexed
            (r as? JsonArray)?.forEachIndexed { ci, cj ->
                val tc = tcs.getOrNull(ci) ?: return@forEachIndexed
                val paras = cj as? JsonArray ?: return@forEachIndexed
                val first = tc.child("w:p")!!
                var prev: Element = first
                paras.forEachIndexed { k, pj ->
                    val target = if (k == 0) first else d.createElement("w:p").also { insertAfter(prev, it) }
                    rebuild(pkg, fl, target, pj.jsonObject)
                    prev = target
                }
            }
        }
        if (anchor != null) insertAfter(anchor, tbl) else if (sect != null) body.insertBefore(tbl, sect) else body.appendChild(tbl)
        // jadvaldan keyin paragraf bo'lishi shart
        val next = tbl.nextSibling.let { var n = it; while (n != null && n !is Element) n = n.nextSibling; n as Element? }
        if (next == null || next.tagName != "w:p") insertAfter(tbl, d.createElement("w:p"))
    }

    // ---------------------------------------------------------------- DOCX: yangi hujjat, sahifa sozlamalari

    /** Bo'sh, Word/WPS/LibreOffice'da ochiladigan DOCX (A4, Calibri 11, sarlavha uslublari). */
    fun newDocx(): ByteArray {
        val files = LinkedHashMap<String, ByteArray>()
        val decl = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n"
        files["[Content_Types].xml"] = (decl + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>""").toByteArray()
        files["_rels/.rels"] = (decl + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>""").toByteArray()
        files["docProps/core.xml"] = (decl + """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:creator>QulayOfis</dc:creator></cp:coreProperties>""").toByteArray()
        files["docProps/app.xml"] = (decl + """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>QulayOfis</Application></Properties>""").toByteArray()
        files["word/_rels/document.xml.rels"] = (decl + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""").toByteArray()
        files["word/document.xml"] = (decl + """<w:document xmlns:w="$W_NS" xmlns:r="$R_NS" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body><w:p/><w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="850" w:bottom="1134" w:left="1701" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""").toByteArray()
        fun heading(id: String, name: String, sz: Int, lvl: Int) =
            """<w:style w:type="paragraph" w:styleId="$id"><w:name w:val="$name"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="80"/><w:outlineLvl w:val="$lvl"/></w:pPr><w:rPr><w:b/><w:color w:val="1F3864"/><w:sz w:val="$sz"/></w:rPr></w:style>"""
        files["word/styles.xml"] = (decl + """<w:styles xmlns:w="$W_NS"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri" w:eastAsia="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/><w:lang w:val="uz-Latn-UZ"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>""" +
            heading("Heading1", "heading 1", 32, 0) + heading("Heading2", "heading 2", 26, 1) + heading("Heading3", "heading 3", 24, 2) +
            """<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="120"/></w:pPr><w:rPr><w:sz w:val="56"/></w:rPr></w:style><w:style w:type="table" w:default="1" w:styleId="TableNormal"><w:name w:val="Normal Table"/><w:tblPr><w:tblInd w:w="0" w:type="dxa"/><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="108" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style></w:styles>""").toByteArray()
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z -> for ((k, v) in files) { z.putNextEntry(ZipEntry(k)); z.write(v); z.closeEntry() } }
        return bo.toByteArray()
    }

    /** Sahifa sozlamalari: yo'nalish, chegaralar (pt), sahifa raqamlari va yuqori kolontitul matni. */
    fun pageSetup(bytes: ByteArray, landscape: Boolean, marginPt: Double, pageNumbers: Boolean, header: String?): ByteArray {
        val pkg = Pkg(readZip(bytes))
        val d = pkg.doc("word/document.xml") ?: return bytes
        val body = d.getElementsByTagName("w:body").item(0) as? Element ?: return bytes
        val sect = body.kids().lastOrNull { it.tagName == "w:sectPr" } ?: d.createElement("w:sectPr").also { body.appendChild(it) }
        val order = listOf("w:headerReference", "w:footerReference", "w:footnotePr", "w:endnotePr", "w:type", "w:pgSz", "w:pgMar", "w:paperSrc", "w:pgBorders", "w:lnNumType", "w:pgNumType", "w:cols", "w:formProt", "w:vAlign", "w:noEndnote", "w:titlePg", "w:textDirection", "w:bidi", "w:rtlGutter", "w:docGrid", "w:printerSettings", "w:sectPrChange")
        val sz = sect.ensure("w:pgSz", order)
        var w = sz.getAttribute("w:w").toIntOrNull() ?: 11906
        var h = sz.getAttribute("w:h").toIntOrNull() ?: 16838
        if (landscape != (w > h)) { val t = w; w = h; h = t }
        sz.setAttribute("w:w", w.toString()); sz.setAttribute("w:h", h.toString())
        if (landscape) sz.setAttribute("w:orient", "landscape") else sz.removeAttribute("w:orient")
        val mar = sect.ensure("w:pgMar", order)
        val m = (marginPt * 20).roundToLong().toString()
        for (k in listOf("w:top", "w:bottom", "w:left", "w:right")) mar.setAttribute(k, m)
        if (!mar.hasAttribute("w:header")) mar.setAttribute("w:header", "708")
        if (!mar.hasAttribute("w:footer")) mar.setAttribute("w:footer", "708")
        if (!mar.hasAttribute("w:gutter")) mar.setAttribute("w:gutter", "0")
        ensureNs(d.documentElement, "xmlns:r" to R_NS)
        fun part(kind: String, xml: String) {
            var n = 1
            while (pkg.files.containsKey("word/${kind}_qo$n.xml")) n++
            val path = "word/${kind}_qo$n.xml"
            pkg.files[path] = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n" + xml).toByteArray()
            addOverride(pkg, "/$path", "application/vnd.openxmlformats-officedocument.wordprocessingml.$kind+xml")
            val rid = addRel(pkg, "word/document.xml", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/$kind", "${kind}_qo$n.xml")
            val refName = if (kind == "header") "w:headerReference" else "w:footerReference"
            sect.kids().filter { it.tagName == refName && it.getAttribute("w:type") == "default" }.forEach { sect.removeChild(it) }
            val ref = d.createElement(refName)
            ref.setAttribute("w:type", "default"); ref.setAttribute("r:id", rid)
            val before = sect.kids().firstOrNull { order.indexOf(it.tagName) > order.indexOf(refName) }
            if (before == null) sect.appendChild(ref) else sect.insertBefore(ref, before)
        }
        if (pageNumbers) part("footer", """<w:ftr xmlns:w="$W_NS" xmlns:r="$R_NS"><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:fldSimple w:instr=" PAGE "><w:r><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>""")
        if (!header.isNullOrBlank()) {
            val esc = header.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            part("header", """<w:hdr xmlns:w="$W_NS" xmlns:r="$R_NS"><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t xml:space="preserve">$esc</w:t></w:r></w:p></w:hdr>""")
        }
        return pkg.bytes()
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
            when {
                b.str("img") != null -> addPicture(pkg, path, d, b)
                b.str("prst") != null -> addShape(pkg, fl, d, b)
                else -> addTextBox(pkg, fl, d, b)
            }
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

    // ---------------------------------------------------------------- PPTX: yangi rasm/shakl, slayd amallari

    private fun maxShapeId(d: Document): Int {
        var maxId = 1
        val nl = d.getElementsByTagName("p:cNvPr")
        for (i in 0 until nl.length) (nl.item(i) as Element).getAttribute("id").toIntOrNull()?.let { if (it > maxId) maxId = it }
        return maxId
    }

    private fun appendToTree(d: Document, el: Element) {
        val tree = d.getElementsByTagName("p:spTree").item(0) as? Element ?: return
        val ext = tree.child("p:extLst")
        if (ext != null) tree.insertBefore(el, ext) else tree.appendChild(el)
    }

    private fun addPicture(pkg: Pkg, slidePath: String, d: Document, b: JsonObject) {
        val bytes = pkg.images[b.str("img") ?: return] ?: return
        val png = bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val ext = if (png) "png" else "jpeg"
        var n = 1
        while (pkg.files.containsKey("ppt/media/qo_image$n.$ext")) n++
        pkg.files["ppt/media/qo_image$n.$ext"] = bytes
        addDefault(pkg, ext, if (png) "image/png" else "image/jpeg")
        val rid = addRel(pkg, slidePath, "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image", "../media/qo_image$n.$ext")
        ensureNs(d.documentElement, "xmlns:r" to R_NS, "xmlns:a" to "http://schemas.openxmlformats.org/drawingml/2006/main")
        val id = maxShapeId(d) + 1
        appendToTree(d, fragment(d, """<p:pic><p:nvPicPr><p:cNvPr id="$id" name="Picture $id"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="${emu(b.num("x") ?: 0.0)}" y="${emu(b.num("y") ?: 0.0)}"/><a:ext cx="${emu(b.num("w") ?: 100.0)}" cy="${emu(b.num("h") ?: 100.0)}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>"""))
    }

    private fun addShape(pkg: Pkg, fl: Flavor, d: Document, b: JsonObject) {
        val prst = b.str("prst")?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) } ?: "rect"
        val fill = b.str("fill")?.takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) } ?: "4472C4"
        val id = maxShapeId(d) + 1
        val sp = fragment(d, """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="Shape $id"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="${emu(b.num("x") ?: 0.0)}" y="${emu(b.num("y") ?: 0.0)}"/><a:ext cx="${emu(b.num("w") ?: 100.0)}" cy="${emu(b.num("h") ?: 100.0)}"/></a:xfrm><a:prstGeom prst="$prst"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="${fill.uppercase()}"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr rtlCol="0" anchor="ctr"/><a:lstStyle/></p:txBody></p:sp>""")
        val tx = sp.child("p:txBody")!!
        (b["ps"] as? JsonArray)?.forEach { pj ->
            val p = d.createElement("a:p")
            tx.appendChild(p)
            rebuild(pkg, fl, p, pj.jsonObject)
            if (p.child("a:pPr") == null) p.insertBefore(d.createElement("a:pPr").also { it.setAttribute("algn", "ctr") }, p.firstChild)
        }
        if (tx.kids().none { it.tagName == "a:p" }) tx.appendChild(fragment(d, """<a:p><a:pPr algn="ctr"/></a:p>"""))
        appendToTree(d, sp)
    }

    class LayoutInfo(val path: String, val name: String)

    /** Birinchi master maketlari (PowerPoint'dagi tartibda). */
    fun pptxLayouts(bytes: ByteArray): List<LayoutInfo> = layouts(Pkg(readZip(bytes)))

    private fun layouts(pkg: Pkg): List<LayoutInfo> {
        val master = pkg.files.keys.filter { it.matches(Regex("ppt/slideMasters/slideMaster\\d+\\.xml")) }.minByOrNull { it.length * 100 + (it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) }
        val out = ArrayList<LayoutInfo>()
        if (master != null) {
            val r = rels(pkg, master)
            val md = pkg.doc(master)
            val ids = md?.getElementsByTagName("p:sldLayoutId")
            if (ids != null) for (i in 0 until ids.length) r[(ids.item(i) as Element).getAttribute("r:id")]?.let { p -> if (pkg.files.containsKey(p)) out += LayoutInfo(p, layoutName(pkg, p)) }
        }
        if (out.isEmpty()) pkg.files.keys.filter { it.matches(Regex("ppt/slideLayouts/slideLayout\\d+\\.xml")) }
            .sortedBy { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }.forEach { out += LayoutInfo(it, layoutName(pkg, it)) }
        return out
    }

    private fun layoutName(pkg: Pkg, path: String): String =
        (pkg.doc(path)?.getElementsByTagName("p:cSld")?.item(0) as? Element)?.getAttribute("name")?.ifEmpty { null }
            ?: path.substringAfterLast('/').removeSuffix(".xml")

    /** Yangi slaydni ro'yxatga qo'shadi ([after] dan keyin; -1 — oxiriga). */
    private fun registerSlide(pkg: Pkg, newPath: String, after: SlideRef?) {
        val pres = pkg.doc("ppt/presentation.xml")!!
        val presRels = pkg.doc(relsPath("ppt/presentation.xml"))!!
        addOverride(pkg, "/$newPath", SLIDE_CT)
        val rid = addRel(pkg, "ppt/presentation.xml", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide", newPath.removePrefix("ppt/"))
        @Suppress("UNUSED_VARIABLE") val unused = presRels
        ensureNs(pres.documentElement, "xmlns:r" to R_NS)
        val list = slides(pkg)
        var maxId = 255L
        list.forEach { it.sldId.getAttribute("id").toLongOrNull()?.let { v -> if (v > maxId) maxId = v } }
        val el = pres.createElement("p:sldId")
        el.setAttribute("id", (maxId + 1).toString())
        el.setAttribute("r:id", rid)
        val lst = pres.getElementsByTagName("p:sldIdLst").item(0) as? Element ?: pres.createElement("p:sldIdLst").also { l ->
            val anchor = pres.documentElement.child("p:notesMasterIdLst") ?: pres.documentElement.child("p:sldMasterIdLst")
            if (anchor != null) insertAfter(anchor, l) else pres.documentElement.insertBefore(l, pres.documentElement.firstChild)
        }
        if (after != null) insertAfter(after.sldId, el) else lst.appendChild(el)
        val sec = pres.getElementsByTagName("p14:sldId")
        if (after != null) for (i in 0 until sec.length) {
            val e = sec.item(i) as Element
            if (e.getAttribute("id") == after.sldId.getAttribute("id")) { insertAfter(e, (e.cloneNode(false) as Element).also { it.setAttribute("id", (maxId + 1).toString()) }); break }
        } else if (sec.length > 0) {
            val last = sec.item(sec.length - 1) as Element
            insertAfter(last, (last.cloneNode(false) as Element).also { it.setAttribute("id", (maxId + 1).toString()) })
        }
    }

    private fun newSlidePath(pkg: Pkg): String {
        val n = (pkg.files.keys.mapNotNull { Regex("ppt/slides/slide(\\d+)\\.xml").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        return "ppt/slides/slide$n.xml"
    }

    /** Tanlangan maket asosida yangi slayd (joy egalari bilan) — [after] dan keyin. */
    fun addSlide(bytes: ByteArray, after: Int, layoutIndex: Int): ByteArray {
        val pkg = Pkg(readZip(bytes))
        val lays = layouts(pkg)
        val lay = lays.getOrNull(layoutIndex) ?: lays.firstOrNull() ?: return bytes
        val ld = pkg.doc(lay.path) ?: return bytes
        val sb = StringBuilder()
        var id = 2
        val sps = ld.getElementsByTagName("p:sp")
        for (i in 0 until sps.length) {
            val sp = sps.item(i) as Element
            val ph = sp.getElementsByTagName("p:ph").item(0) as? Element ?: continue
            val type = ph.getAttribute("type")
            if (type in setOf("dt", "ftr", "sldNum", "pic", "chart", "tbl", "dgm", "media", "clipArt")) continue
            val attrs = listOf("type", "orient", "sz", "idx").filter { ph.hasAttribute(it) }.joinToString("") { " $it=\"${ph.getAttribute(it)}\"" }
            sb.append("""<p:sp><p:nvSpPr><p:cNvPr id="$id" name="Placeholder $id"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph$attrs/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="ru-RU" dirty="0"/></a:p></p:txBody></p:sp>""")
            id++
        }
        val path = newSlidePath(pkg)
        pkg.files[path] = ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n" +
            """<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="$R_NS" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>$sb</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>""").toByteArray()
        addRel(pkg, path, "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout", "../slideLayouts/" + lay.path.substringAfterLast('/'))
        registerSlide(pkg, path, slides(pkg).getOrNull(after))
        return pkg.bytes()
    }

    /** Slaydni [delta] pozitsiyaga suradi (-1 — yuqoriga, +1 — pastga). */
    fun moveSlide(bytes: ByteArray, index: Int, delta: Int): ByteArray {
        val pkg = Pkg(readZip(bytes))
        val list = slides(pkg)
        val src = list.getOrNull(index) ?: return bytes
        val dst = list.getOrNull(index + delta) ?: return bytes
        val parent = src.sldId.parentNode
        parent.removeChild(src.sldId)
        if (delta < 0) parent.insertBefore(src.sldId, dst.sldId) else insertAfter(dst.sldId, src.sldId)
        // bo'limlar ro'yxatida ham shu tartib
        val pres = pkg.doc("ppt/presentation.xml")!!
        val sec = pres.getElementsByTagName("p14:sldId")
        var a: Element? = null; var b: Element? = null
        for (i in 0 until sec.length) {
            val e = sec.item(i) as Element
            if (e.getAttribute("id") == src.sldId.getAttribute("id")) a = e
            if (e.getAttribute("id") == dst.sldId.getAttribute("id")) b = e
        }
        if (a != null && b != null) {
            a.parentNode.removeChild(a)
            if (delta < 0) b.parentNode.insertBefore(a, b) else insertAfter(b, a)
        }
        return pkg.bytes()
    }

    /** Slayd maketini almashtiradi (joy egalari yangi maketdagi joylashuvni oladi). */
    fun setSlideLayout(bytes: ByteArray, index: Int, layoutIndex: Int): ByteArray {
        val pkg = Pkg(readZip(bytes))
        val s = slides(pkg).getOrNull(index) ?: return bytes
        val lay = layouts(pkg).getOrNull(layoutIndex) ?: return bytes
        val rd = pkg.doc(relsPath(s.path)) ?: return bytes
        val nl = rd.getElementsByTagName("Relationship")
        for (i in 0 until nl.length) {
            val e = nl.item(i) as Element
            if (e.getAttribute("Type").endsWith("/slideLayout")) e.setAttribute("Target", "../slideLayouts/" + lay.path.substringAfterLast('/'))
        }
        return pkg.bytes()
    }

    /** Slayd fon rangi ([all] — barcha slaydlarga). */
    fun setSlideBackground(bytes: ByteArray, index: Int, hex: String, all: Boolean): ByteArray {
        val pkg = Pkg(readZip(bytes))
        val list = slides(pkg)
        val targets = if (all) list else listOfNotNull(list.getOrNull(index))
        val color = hex.removePrefix("#").uppercase().takeIf { it.matches(Regex("[0-9A-F]{6}")) } ?: return bytes
        for (s in targets) {
            val d = pkg.doc(s.path) ?: continue
            val cSld = d.getElementsByTagName("p:cSld").item(0) as? Element ?: continue
            cSld.kids().filter { it.tagName == "p:bg" }.forEach { cSld.removeChild(it) }
            cSld.insertBefore(fragment(d, """<p:bg><p:bgPr><a:solidFill><a:srgbClr val="$color"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>"""), cSld.firstChild)
        }
        return pkg.bytes()
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
        ensureNs(pres.documentElement, "xmlns:r" to R_NS)
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

    private class SheetRef(val el: Element, val name: String, val path: String)

    private fun sheetRefs(pkg: Pkg): List<SheetRef> {
        val wb = pkg.doc("xl/workbook.xml") ?: return emptyList()
        val r = rels(pkg, "xl/workbook.xml")
        val out = ArrayList<SheetRef>()
        val nl = wb.getElementsByTagName("sheet")
        for (i in 0 until nl.length) {
            val e = nl.item(i) as Element
            r[e.getAttribute("r:id")]?.let { out += SheetRef(e, e.getAttribute("name"), it) }
        }
        return out
    }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, 'A' + m); n = (n - 1) / 26 }
        return sb.toString()
    }

    /** "B12" → (11, 1) — 0 asosli qator va ustun. */
    private fun cellRC(ref: String): Pair<Int, Int>? {
        val m = Regex("\\$?([A-Za-z]{1,3})\\$?(\\d+)").matchEntire(ref.trim()) ?: return null
        return (m.groupValues[2].toInt() - 1) to (colIndex(m.groupValues[1]) - 1)
    }

    private val refRe = Regex("(\"(?:[^\"]|\"\")*\")|((?:'[^']+'|[A-Za-z_][\\w.]*)!)?(\\$?)([A-Za-z]{1,3})(\\$?)(\\d+)(?![\\w(])")

    /**
     * Formula ichidagi katak havolalarini o'zgartiradi. [own] — formula shu varaqdami;
     * [sheet] — o'zgarayotgan varaq nomi. [tr] (qator, ustun, $qator, $ustun) → yangi (qator, ustun).
     */
    private fun mapRefs(f: String, sheet: String?, own: Boolean, tr: (Int, Int, Boolean, Boolean) -> Pair<Int, Int>): String =
        refRe.replace(f) { m ->
            if (m.groupValues[1].isNotEmpty()) return@replace m.value
            val start = m.range.first
            if (start > 0 && (f[start - 1].isLetterOrDigit() || f[start - 1] == '_' || f[start - 1] == '.')) return@replace m.value
            val pre = m.groupValues[2]
            val applies = if (pre.isEmpty()) own else sheet != null && pre.removeSuffix("!").trim('\'').equals(sheet, true)
            if (!applies) return@replace m.value
            val absC = m.groupValues[3].isNotEmpty(); val absR = m.groupValues[5].isNotEmpty()
            val r = m.groupValues[6].toInt() - 1; val c = colIndex(m.groupValues[4]) - 1
            val (nr, nc) = tr(r, c, absR, absC)
            pre + m.groupValues[3] + colName(nc.coerceAtLeast(0)) + m.groupValues[5] + (nr.coerceAtLeast(0) + 1)
        }

    private fun translateF(f: String, dr: Int, dc: Int) = mapRefs(f, null, true) { r, c, ar, ac -> (if (ar) r else r + dr) to (if (ac) c else c + dc) }

    /** Umumiy (shared) formulalar guruhini oddiy formulalarga aylantiradi. */
    private fun unshare(d: Document, si: String) {
        val fs = d.getElementsByTagName("f")
        var master: Element? = null
        val list = ArrayList<Element>()
        for (i in 0 until fs.length) {
            val f = fs.item(i) as Element
            if (f.getAttribute("t") == "shared" && f.getAttribute("si") == si) { list += f; if (f.textContent.isNotEmpty() && master == null) master = f }
        }
        val m = master ?: return
        val mc = cellRC((m.parentNode as Element).getAttribute("r")) ?: return
        val text = m.textContent
        for (f in list) {
            val rc = cellRC((f.parentNode as Element).getAttribute("r")) ?: continue
            if (f !== m) f.textContent = translateF(text, rc.first - mc.first, rc.second - mc.second)
            f.removeAttribute("t"); f.removeAttribute("si"); f.removeAttribute("ref")
        }
    }

    // ---------------------------------------------------------------- XLSX: uslublar

    private class Styles(val d: Document) {
        val cache = HashMap<String, Int>()
    }

    private fun Element.countUpdate() { setAttribute("count", kids().size.toString()) }

    private val fontOrder = listOf("b", "i", "strike", "condense", "extend", "outline", "shadow", "u", "vertAlign", "sz", "color", "name", "family", "charset", "scheme")

    private fun xfFor(st: Styles, base: Int, fmt: JsonObject): Int {
        val key = "$base|$fmt"
        st.cache[key]?.let { return it }
        val d = st.d
        val root = d.documentElement
        val cellXfs = d.getElementsByTagName("cellXfs").item(0) as? Element ?: return base
        val xfs = cellXfs.kids().filter { it.tagName == "xf" }
        val xf = (xfs.getOrNull(base) ?: xfs.firstOrNull() ?: return base).cloneNode(true) as Element
        // shrift
        if (fmt.has("b") || fmt.has("i") || fmt.str("c") != null) {
            val fonts = d.getElementsByTagName("fonts").item(0) as? Element
            if (fonts != null) {
                val list = fonts.kids()
                val f = (list.getOrNull(xf.getAttribute("fontId").toIntOrNull() ?: 0) ?: list.firstOrNull())?.cloneNode(true) as? Element ?: d.createElement("font")
                fmt.bool("b")?.let { on -> f.setOrdered("b", fontOrder, if (on) emptyMap() else null) }
                fmt.bool("i")?.let { on -> f.setOrdered("i", fontOrder, if (on) emptyMap() else null) }
                fmt.str("c")?.let { c -> f.setOrdered("color", fontOrder, mapOf("rgb" to "FF" + c.removePrefix("#").uppercase())) }
                fonts.appendChild(f)
                fonts.countUpdate()
                xf.setAttribute("fontId", (fonts.kids().size - 1).toString())
                xf.setAttribute("applyFont", "1")
            }
        }
        fmt.str("bg")?.let { bg ->
            val fills = d.getElementsByTagName("fills").item(0) as? Element
            if (fills != null) {
                if (bg == "none") xf.setAttribute("fillId", "0") else {
                    val fill = fragment(d, """<fill><patternFill patternType="solid"><fgColor rgb="FF${bg.removePrefix("#").uppercase()}"/><bgColor indexed="64"/></patternFill></fill>""")
                    fills.appendChild(fill)
                    fills.countUpdate()
                    xf.setAttribute("fillId", (fills.kids().size - 1).toString())
                }
                xf.setAttribute("applyFill", "1")
            }
        }
        fmt.bool("bd")?.let { on ->
            val borders = d.getElementsByTagName("borders").item(0) as? Element
            if (borders != null) {
                if (!on) xf.setAttribute("borderId", "0") else {
                    val b = fragment(d, """<border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border>""")
                    borders.appendChild(b)
                    borders.countUpdate()
                    xf.setAttribute("borderId", (borders.kids().size - 1).toString())
                }
                xf.setAttribute("applyBorder", "1")
            }
        }
        fmt.str("al")?.let { al ->
            val a = xf.child("alignment") ?: d.createElement("alignment").also { xf.insertBefore(it, xf.firstChild) }
            a.setAttribute("horizontal", when (al) { "center" -> "center"; "right" -> "right"; "justify" -> "justify"; else -> "left" })
            xf.setAttribute("applyAlignment", "1")
        }
        fmt.str("nf")?.let { nf ->
            val id = when (nf) {
                "gen" -> 0; "int" -> 3; "num2" -> 4; "pct" -> 10
                "date" -> customNumFmt(d, root, "dd.mm.yyyy")
                "cur" -> customNumFmt(d, root, "#,##0.00\\ \"so'm\"")
                else -> null
            }
            if (id != null) { xf.setAttribute("numFmtId", id.toString()); xf.setAttribute("applyNumberFormat", "1") }
        }
        cellXfs.appendChild(xf)
        cellXfs.countUpdate()
        val idx = cellXfs.kids().size - 1
        st.cache[key] = idx
        return idx
    }

    private fun customNumFmt(d: Document, root: Element, code: String): Int {
        val nf = root.child("numFmts") ?: d.createElement("numFmts").also { root.insertBefore(it, root.firstChild) }
        var max = 163
        for (e in nf.kids()) {
            val id = e.getAttribute("numFmtId").toIntOrNull() ?: continue
            if (e.getAttribute("formatCode") == code) return id
            if (id > max) max = id
        }
        val e = d.createElement("numFmt")
        e.setAttribute("numFmtId", (max + 1).toString()); e.setAttribute("formatCode", code)
        nf.appendChild(e)
        nf.countUpdate()
        return max + 1
    }

    // ---------------------------------------------------------------- XLSX: kataklarni yozish

    private fun findCell(d: Document, ref: String, create: Boolean): Element? {
        val data = d.getElementsByTagName("sheetData").item(0) as? Element ?: return null
        val rowNum = ref.dropWhile { it.isLetter() }.toIntOrNull() ?: return null
        val col = colIndex(ref)
        var row: Element? = null
        var before: Element? = null
        for (r in data.kids()) {
            if (r.tagName != "row") continue
            val rn = r.getAttribute("r").toIntOrNull() ?: continue
            if (rn == rowNum) { row = r; break }
            if (rn > rowNum) { before = r; break }
        }
        if (row == null) {
            if (!create) return null
            row = d.createElement("row").also { it.setAttribute("r", rowNum.toString()) }
            if (before != null) data.insertBefore(row, before) else data.appendChild(row)
        }
        row!!.removeAttribute("spans")
        var cb: Element? = null
        for (c in row.kids()) {
            if (c.tagName != "c") continue
            val ci = colIndex(c.getAttribute("r"))
            if (ci == col) return c
            if (ci > col) { cb = c; break }
        }
        if (!create) return null
        val cell = d.createElement("c").also { it.setAttribute("r", ref) }
        // qator uslubi bo'lsa — katakka ham
        if (row.getAttribute("customFormat") == "1") row.getAttribute("s").takeIf { it.isNotEmpty() }?.let { cell.setAttribute("s", it) }
        if (cb != null) row.insertBefore(cell, cb) else {
            val ext = row.child("extLst")
            if (ext != null) row.insertBefore(cell, ext) else row.appendChild(cell)
        }
        return cell
    }

    private fun xlsx(pkg: Pkg, root: JsonObject) {
        val list = root["cells"] as? JsonArray ?: return
        val paths = sheetPaths(pkg)
        var touchedFormula = false
        val styles = pkg.doc("xl/styles.xml")?.let { Styles(it) }
        for (cj in list) {
            val o = cj.jsonObject
            val key = o.str("c") ?: continue
            val si = key.substringBefore('|').toIntOrNull() ?: continue
            val ref = key.substringAfter('|')
            val path = paths.getOrNull(si) ?: continue
            val d = pkg.doc(path) ?: continue
            val c = findCell(d, ref, true) ?: continue
            // umumiy formula guruhini avval ajratamiz (aks holda boshqa kataklar formulasiz qoladi)
            c.child("f")?.takeIf { it.getAttribute("t") == "shared" }?.let { unshare(d, it.getAttribute("si")) }
            (o["fmt"] as? JsonObject)?.let { fm ->
                if (styles != null) c.setAttribute("s", xfFor(styles, c.getAttribute("s").toIntOrNull() ?: 0, fm).toString())
            }
            if (!o.has("v") && !o.has("f")) continue
            val v = o.str("v") ?: ""
            val ext = c.child("extLst")
            fun put(e: Element) { if (ext != null) c.insertBefore(e, ext) else c.appendChild(e) }
            fun putValue(vt: String?) {
                when (vt) {
                    "n" -> { c.removeAttribute("t"); put(d.createElement("v").also { it.textContent = v }) }
                    "b" -> { c.setAttribute("t", "b"); put(d.createElement("v").also { it.textContent = if (v == "1" || v.equals("true", true)) "1" else "0" }) }
                    "e" -> { c.setAttribute("t", "e"); put(d.createElement("v").also { it.textContent = v }) }
                    "s" -> { c.setAttribute("t", "str"); put(d.createElement("v").also { it.textContent = v }) }
                    else -> c.removeAttribute("t")
                }
            }
            when {
                o.has("keepf") -> {
                    c.kids().filter { it.tagName == "v" || it.tagName == "is" }.forEach { c.removeChild(it) }
                    touchedFormula = true
                    putValue(o.str("vt"))
                }
                o.str("f") != null -> {
                    c.kids().filter { it.tagName == "f" || it.tagName == "v" || it.tagName == "is" }.forEach { c.removeChild(it) }
                    touchedFormula = true
                    put(d.createElement("f").also { it.textContent = o.str("f") })
                    putValue(o.str("vt"))
                }
                else -> {
                    c.kids().filter { it.tagName == "f" || it.tagName == "v" || it.tagName == "is" }.forEach {
                        if (it.tagName == "f") touchedFormula = true
                        c.removeChild(it)
                    }
                    val num = number(v)
                    when {
                        v.isEmpty() -> c.removeAttribute("t")
                        num != null -> { c.removeAttribute("t"); put(d.createElement("v").also { it.textContent = num }) }
                        v.equals("TRUE", true) || v.equals("FALSE", true) -> { c.setAttribute("t", "b"); put(d.createElement("v").also { it.textContent = if (v.equals("TRUE", true)) "1" else "0" }) }
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
            }
        }
        if (touchedFormula) recalcOnLoad(pkg)
    }

    /** Hisob zanjirini olib tashlaydi va Excel ochilganda qayta hisoblashni so'raydi. */
    private fun recalcOnLoad(pkg: Pkg) {
        dropCalcChain(pkg)
        val wb = pkg.doc("xl/workbook.xml") ?: return
        val root = wb.documentElement
        val cp = wb.getElementsByTagName("calcPr").item(0) as? Element ?: wb.createElement("calcPr").also { e ->
            val after = listOf("definedNames", "externalReferences", "functionGroups", "sheets")
            val anchor = after.firstNotNullOfOrNull { n -> root.child(n) }
            if (anchor != null) insertAfter(anchor, e) else root.appendChild(e)
        }
        cp.setAttribute("fullCalcOnLoad", "1")
    }

    // ---------------------------------------------------------------- XLSX: tuzilma amallari

    private val sheetOrder = listOf(
        "sheetPr", "dimension", "sheetViews", "sheetFormatPr", "cols", "sheetData", "sheetCalcPr", "sheetProtection", "protectedRanges",
        "scenarios", "autoFilter", "sortState", "dataConsolidate", "customSheetViews", "mergeCells", "phoneticPr", "conditionalFormatting",
        "dataValidations", "hyperlinks", "printOptions", "pageMargins", "pageSetup", "headerFooter", "rowBreaks", "colBreaks",
        "customProperties", "cellWatches", "ignoredErrors", "smartTags", "drawing", "legacyDrawing", "legacyDrawingHF", "picture",
        "oleObjects", "controls", "webPublishItems", "tableParts", "extLst",
    )

    private fun shiftRef(ref: String, rows: Boolean, at: Int, n: Int): String? {
        val parts = ref.split(':')
        val out = ArrayList<String>()
        for (p in parts) {
            val m = Regex("(\\$?)([A-Za-z]{1,3})(\\$?)(\\d+)").matchEntire(p) ?: return ref
            var r = m.groupValues[4].toInt() - 1
            var c = colIndex(m.groupValues[2]) - 1
            fun sh(x: Int) = if (n >= 0) (if (x >= at) x + n else x) else (if (x >= at - n) x + n else if (x >= at) at else x)
            if (rows) r = sh(r) else c = sh(c)
            out += m.groupValues[1] + colName(c.coerceAtLeast(0)) + m.groupValues[3] + (r.coerceAtLeast(0) + 1)
        }
        return out.joinToString(":")
    }

    /** Qator/ustun qo'shish ([n] > 0) yoki o'chirish ([n] < 0) — [at] dan boshlab (0 asosli). */
    private fun shiftSheet(pkg: Pkg, si: Int, rows: Boolean, at: Int, n: Int) {
        val refs = sheetRefs(pkg)
        val sh = refs.getOrNull(si) ?: return
        val d = pkg.doc(sh.path) ?: return
        // umumiy formulalarni ajratib olamiz (ref diapazonlari surilganda buzilmasin)
        val shared = HashSet<String>()
        val fs = d.getElementsByTagName("f")
        for (i in 0 until fs.length) (fs.item(i) as Element).takeIf { it.getAttribute("t") == "shared" }?.let { shared += it.getAttribute("si") }
        shared.forEach { unshare(d, it) }
        val data = d.getElementsByTagName("sheetData").item(0) as? Element ?: return
        val delFrom = at
        val delTo = at - n // o'chirishda: [at, at-n)
        for (row in data.kids().filter { it.tagName == "row" }) {
            val rn = (row.getAttribute("r").toIntOrNull() ?: continue) - 1
            if (rows) {
                if (n < 0 && rn in delFrom until delTo) { data.removeChild(row); continue }
                if (rn >= at) row.setAttribute("r", (rn + (if (n < 0) n else n) + 1).toString())
            }
            row.removeAttribute("spans")
            for (c in row.kids().filter { it.tagName == "c" }) {
                val rc = cellRC(c.getAttribute("r")) ?: continue
                var r = rc.first; var col = rc.second
                if (rows) { if (r >= at) r += n } else {
                    if (n < 0 && col in delFrom until delTo) { row.removeChild(c); continue }
                    if (col >= at) col += n
                }
                c.setAttribute("r", colName(col) + (r + 1))
            }
        }
        if (rows && n < 0) {
            // o'chirilgan qatordan keyingilar allaqachon surildi; tartib saqlanadi
        }
        // formulalar: shu varaq va boshqa varaqlardagi havolalar
        for (other in refs) {
            val od = pkg.doc(other.path) ?: continue
            val own = other.path == sh.path
            val fl = od.getElementsByTagName("f")
            for (i in 0 until fl.length) {
                val f = fl.item(i) as Element
                if (f.textContent.isEmpty()) continue
                f.textContent = mapRefs(f.textContent, sh.name, own) { r, c, _, _ ->
                    if (rows) (if (r >= at + (if (n < 0) -n else 0)) r + n else r) to c else r to (if (c >= at + (if (n < 0) -n else 0)) c + n else c)
                }
            }
        }
        // birlashtirilgan kataklar, o'lcham, filtr, gipervhavolalar, shartli format, tekshiruvlar
        val merges = d.getElementsByTagName("mergeCell")
        for (i in merges.length - 1 downTo 0) {
            val m = merges.item(i) as Element
            val ref = m.getAttribute("ref")
            val a = cellRC(ref.substringBefore(':')) ?: continue
            val b = cellRC(ref.substringAfter(':')) ?: a
            val lo = if (rows) a.first else a.second
            val hi = if (rows) b.first else b.second
            if (n < 0 && hi >= delFrom && lo < delTo) { m.parentNode.removeChild(m); continue }
            shiftRef(ref, rows, at, n)?.let { m.setAttribute("ref", it) }
        }
        (d.getElementsByTagName("mergeCells").item(0) as? Element)?.let { if (it.kids().isEmpty()) it.parentNode.removeChild(it) else it.countUpdate() }
        for (tag in listOf("dimension", "autoFilter", "hyperlink", "selection", "pane")) {
            val nl = d.getElementsByTagName(tag)
            for (i in 0 until nl.length) {
                val e = nl.item(i) as Element
                for (attr in listOf("ref", "sqref", "activeCell", "topLeftCell")) {
                    if (!e.hasAttribute(attr)) continue
                    e.setAttribute(attr, e.getAttribute(attr).split(' ').mapNotNull { shiftRef(it, rows, at, n) }.joinToString(" "))
                }
            }
        }
        for (tag in listOf("conditionalFormatting", "dataValidation")) {
            val nl = d.getElementsByTagName(tag)
            for (i in 0 until nl.length) {
                val e = nl.item(i) as Element
                if (e.hasAttribute("sqref")) e.setAttribute("sqref", e.getAttribute("sqref").split(' ').mapNotNull { shiftRef(it, rows, at, n) }.joinToString(" "))
            }
        }
        // ustun kengliklari
        if (!rows) {
            val cols = d.getElementsByTagName("col")
            for (i in cols.length - 1 downTo 0) {
                val c = cols.item(i) as Element
                var mn = (c.getAttribute("min").toIntOrNull() ?: continue) - 1
                var mx = (c.getAttribute("max").toIntOrNull() ?: continue) - 1
                if (n < 0) {
                    if (mn >= delFrom && mx < delTo) { c.parentNode.removeChild(c); continue }
                    if (mn >= delTo) mn += n
                    if (mx >= delTo) mx += n else if (mx >= delFrom) mx = delFrom - 1
                } else { if (mn >= at) mn += n; if (mx >= at) mx += n }
                if (mx < mn) { c.parentNode.removeChild(c); continue }
                c.setAttribute("min", (mn + 1).toString()); c.setAttribute("max", (mx + 1).toString())
            }
        }
        // nomlangan diapazonlar
        pkg.doc("xl/workbook.xml")?.getElementsByTagName("definedName")?.let { nl ->
            for (i in 0 until nl.length) {
                val e = nl.item(i) as Element
                e.textContent = mapRefs(e.textContent, sh.name, false) { r, c, _, _ ->
                    if (rows) (if (r >= at + (if (n < 0) -n else 0)) r + n else r) to c else r to (if (c >= at + (if (n < 0) -n else 0)) c + n else c)
                }
            }
        }
        recalcOnLoad(pkg)
    }

    private fun ensureSheetChild(d: Document, name: String): Element {
        val ws = d.documentElement
        ws.child(name)?.let { return it }
        val e = d.createElement(name)
        val idx = sheetOrder.indexOf(name)
        val before = ws.kids().firstOrNull { k -> sheetOrder.indexOf(k.tagName).let { it > idx } }
        if (before == null) ws.appendChild(e) else ws.insertBefore(e, before)
        return e
    }

    private fun sharedStrings(pkg: Pkg): List<String> {
        val d = pkg.doc("xl/sharedStrings.xml") ?: return emptyList()
        val out = ArrayList<String>()
        val nl = d.getElementsByTagName("si")
        for (i in 0 until nl.length) {
            val si = nl.item(i) as Element
            val ts = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (k in 0 until ts.length) {
                val t = ts.item(k) as Element
                if ((t.parentNode as Element).tagName != "rPh") sb.append(t.textContent)
            }
            out += sb.toString()
        }
        return out
    }

    private fun cellValue(c: Element, sst: List<String>): Any {
        val t = c.getAttribute("t")
        val v = c.child("v")?.textContent ?: ""
        return when (t) {
            "s" -> v.toIntOrNull()?.let { sst.getOrNull(it) } ?: ""
            "inlineStr" -> c.child("is")?.textContent ?: ""
            "str", "e" -> v
            "b" -> if (v == "1") 1.0 else 0.0
            else -> v.toDoubleOrNull() ?: v
        }
    }

    private fun sortRows(pkg: Pkg, si: Int, r0: Int, r1: Int, col: Int, asc: Boolean) {
        val sh = sheetRefs(pkg).getOrNull(si) ?: return
        val d = pkg.doc(sh.path) ?: return
        val shared = HashSet<String>()
        val fs = d.getElementsByTagName("f")
        for (i in 0 until fs.length) (fs.item(i) as Element).takeIf { it.getAttribute("t") == "shared" }?.let { shared += it.getAttribute("si") }
        shared.forEach { unshare(d, it) }
        val data = d.getElementsByTagName("sheetData").item(0) as? Element ?: return
        val sst = sharedStrings(pkg)
        val rows = data.kids().filter { it.tagName == "row" }.associateBy { (it.getAttribute("r").toIntOrNull() ?: 0) - 1 }
        val last = minOf(r1, rows.keys.maxOrNull() ?: r0)
        if (last <= r0) return
        val idx = (r0..last).toList()
        fun keyOf(r: Int): Any? = rows[r]?.kids()?.firstOrNull { it.tagName == "c" && cellRC(it.getAttribute("r"))?.second == col }?.let { cellValue(it, sst) }
        val cmp = Comparator<Int> { a, b ->
            val x = keyOf(a); val y = keyOf(b)
            val ex = x == null || x == ""; val ey = y == null || y == ""
            when {
                ex && ey -> 0; ex -> 1; ey -> -1 // bo'shlar oxirida
                x is Double && y is Double -> if (asc) x.compareTo(y) else y.compareTo(x)
                x is Double -> if (asc) -1 else 1
                y is Double -> if (asc) 1 else -1
                else -> if (asc) x.toString().compareTo(y.toString(), true) else y.toString().compareTo(x.toString(), true)
            }
        }
        val order = idx.sortedWith(cmp)
        val moved = order.mapIndexedNotNull { newPos, old -> rows[old]?.let { Triple(it, old, r0 + newPos) } }
        moved.forEach { data.removeChild(it.first) }
        for ((row, old, nw) in moved) {
            row.setAttribute("r", (nw + 1).toString())
            for (c in row.kids().filter { it.tagName == "c" }) {
                val rc = cellRC(c.getAttribute("r")) ?: continue
                c.setAttribute("r", colName(rc.second) + (nw + 1))
                c.child("f")?.let { f -> if (f.textContent.isNotEmpty()) f.textContent = translateF(f.textContent, nw - old, 0) }
            }
        }
        // qatorlarni tartib bilan qayta joylash
        val rest = data.kids().filter { it.tagName == "row" }
        val all = (rest + moved.map { it.first }).sortedBy { it.getAttribute("r").toIntOrNull() ?: 0 }
        rest.forEach { data.removeChild(it) }
        val ext = data.child("extLst")
        all.forEach { if (ext != null) data.insertBefore(it, ext) else data.appendChild(it) }
        recalcOnLoad(pkg)
    }

    /**
     * Excel tuzilma amallari. [op]: insRow, delRow, insCol, delCol (a — indeks, b — soni), merge/unmerge (diapazon),
     * sortAsc/sortDesc (a — ustun, r0..r1), freeze (a — qatorlar, b — ustunlar), colWidth (a — ustun, extra — kenglik),
     * addSheet/renameSheet (extra — nom), delSheet, filter (diapazon yoki bo'sh — olib tashlash).
     */
    fun xlsxOp(bytes: ByteArray, op: String, si: Int, r0: Int, c0: Int, r1: Int, c1: Int, extra: String? = null): ByteArray {
        val pkg = Pkg(readZip(bytes))
        val refs = sheetRefs(pkg)
        val sh = refs.getOrNull(si)
        when (op) {
            "insRowAbove" -> shiftSheet(pkg, si, true, r0, r1 - r0 + 1)
            "insRowBelow" -> shiftSheet(pkg, si, true, r1 + 1, r1 - r0 + 1)
            "delRow" -> shiftSheet(pkg, si, true, r0, -(r1 - r0 + 1))
            "insColLeft" -> shiftSheet(pkg, si, false, c0, c1 - c0 + 1)
            "insColRight" -> shiftSheet(pkg, si, false, c1 + 1, c1 - c0 + 1)
            "delCol" -> shiftSheet(pkg, si, false, c0, -(c1 - c0 + 1))
            "merge", "unmerge" -> {
                val d = pkg.doc(sh?.path ?: return bytes) ?: return bytes
                val ref = colName(c0) + (r0 + 1) + ":" + colName(c1) + (r1 + 1)
                val mc = ensureSheetChild(d, "mergeCells")
                // kesishgan eski birlashmalarni olib tashlaymiz
                for (m in mc.kids()) {
                    val a = cellRC(m.getAttribute("ref").substringBefore(':')) ?: continue
                    val b = cellRC(m.getAttribute("ref").substringAfter(':')) ?: a
                    if (a.first <= r1 && b.first >= r0 && a.second <= c1 && b.second >= c0) mc.removeChild(m)
                }
                if (op == "merge" && (r1 > r0 || c1 > c0)) mc.appendChild(d.createElement("mergeCell").also { it.setAttribute("ref", ref) })
                if (mc.kids().isEmpty()) mc.parentNode.removeChild(mc) else mc.countUpdate()
            }
            "sortAsc", "sortDesc" -> sortRows(pkg, si, r0, r1, c0, op == "sortAsc")
            "freeze" -> {
                val d = pkg.doc(sh?.path ?: return bytes) ?: return bytes
                val views = ensureSheetChild(d, "sheetViews")
                val view = views.child("sheetView") ?: d.createElement("sheetView").also { it.setAttribute("workbookViewId", "0"); views.appendChild(it) }
                view.kids().filter { it.tagName == "pane" || it.tagName == "selection" }.forEach { view.removeChild(it) }
                val rows = r0.coerceAtLeast(0); val cols = c0.coerceAtLeast(0)
                if (rows + cols > 0) {
                    val pane = d.createElement("pane")
                    if (cols > 0) pane.setAttribute("xSplit", cols.toString())
                    if (rows > 0) pane.setAttribute("ySplit", rows.toString())
                    pane.setAttribute("topLeftCell", colName(cols) + (rows + 1))
                    pane.setAttribute("activePane", if (rows > 0 && cols > 0) "bottomRight" else if (rows > 0) "bottomLeft" else "topRight")
                    pane.setAttribute("state", "frozen")
                    view.insertBefore(pane, view.firstChild)
                }
            }
            "colWidth" -> {
                val d = pkg.doc(sh?.path ?: return bytes) ?: return bytes
                val w = extra?.toDoubleOrNull() ?: return bytes
                val cols = ensureSheetChild(d, "cols")
                for (col in c0..c1) {
                    val n = col + 1
                    // mavjud oraliqni bo'lamiz
                    for (c in cols.kids()) {
                        val mn = c.getAttribute("min").toIntOrNull() ?: continue
                        val mx = c.getAttribute("max").toIntOrNull() ?: continue
                        if (n in mn..mx) {
                            if (mn < n) cols.insertBefore((c.cloneNode(true) as Element).also { it.setAttribute("max", (n - 1).toString()) }, c)
                            if (mx > n) insertAfter(c, (c.cloneNode(true) as Element).also { it.setAttribute("min", (n + 1).toString()) })
                            cols.removeChild(c)
                            break
                        }
                    }
                    val e = d.createElement("col")
                    e.setAttribute("min", n.toString()); e.setAttribute("max", n.toString())
                    e.setAttribute("width", w.toString()); e.setAttribute("customWidth", "1")
                    val before = cols.kids().firstOrNull { (it.getAttribute("min").toIntOrNull() ?: 0) > n }
                    if (before == null) cols.appendChild(e) else cols.insertBefore(e, before)
                }
            }
            "filter" -> {
                val d = pkg.doc(sh?.path ?: return bytes) ?: return bytes
                d.documentElement.kids().filter { it.tagName == "autoFilter" }.forEach { d.documentElement.removeChild(it) }
                if (r1 >= r0 && c1 >= c0 && extra != "off") ensureSheetChild(d, "autoFilter").setAttribute("ref", colName(c0) + (r0 + 1) + ":" + colName(c1) + (r1 + 1))
            }
            "addSheet" -> {
                val wb = pkg.doc("xl/workbook.xml") ?: return bytes
                var k = 1
                while (pkg.files.containsKey("xl/worksheets/sheet$k.xml")) k++
                val path = "xl/worksheets/sheet$k.xml"
                pkg.files[path] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$R_NS"><sheetViews><sheetView workbookViewId="0"/></sheetViews><sheetFormatPr defaultRowHeight="15"/><sheetData/><pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/></worksheet>""".toByteArray()
                addOverride(pkg, "/$path", "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml")
                val rid = addRel(pkg, "xl/workbook.xml", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet", "worksheets/sheet$k.xml")
                ensureNs(wb.documentElement, "xmlns:r" to R_NS)
                val sheets = wb.getElementsByTagName("sheets").item(0) as Element
                var maxId = 0
                for (e in sheets.kids()) e.getAttribute("sheetId").toIntOrNull()?.let { if (it > maxId) maxId = it }
                val names = refs.map { it.name.lowercase() }.toSet()
                var name = extra?.takeIf { it.isNotBlank() } ?: "Sheet${refs.size + 1}"
                var j = 2
                val base = name
                while (name.lowercase() in names) name = "$base ($j)".also { j++ }
                val e = wb.createElement("sheet")
                e.setAttribute("name", name.take(31)); e.setAttribute("sheetId", (maxId + 1).toString()); e.setAttribute("r:id", rid)
                sheets.appendChild(e)
            }
            "renameSheet" -> {
                val nm = extra?.replace(Regex("[\\\\/?*\\[\\]:]"), "")?.trim()?.take(31) ?: return bytes
                if (nm.isEmpty() || sh == null || refs.any { it !== sh && it.name.equals(nm, true) }) return bytes
                val old = sh.name
                sh.el.setAttribute("name", nm)
                val quote = { n: String -> if (Regex("^[A-Za-z_][\\w.]*$").matches(n)) n else "'" + n.replace("'", "''") + "'" }
                for (other in refs) {
                    val od = pkg.doc(other.path) ?: continue
                    val fl = od.getElementsByTagName("f")
                    for (i in 0 until fl.length) {
                        val f = fl.item(i) as Element
                        f.textContent = f.textContent.replace(Regex("(?i)(?<![\\w.])('" + Regex.escape(old.replace("'", "''")) + "'|" + Regex.escape(old) + ")!"), Regex.escapeReplacement(quote(nm) + "!"))
                    }
                }
                pkg.doc("xl/workbook.xml")?.getElementsByTagName("definedName")?.let { nl ->
                    for (i in 0 until nl.length) {
                        val e = nl.item(i) as Element
                        e.textContent = e.textContent.replace(Regex("(?i)(?<![\\w.])('" + Regex.escape(old.replace("'", "''")) + "'|" + Regex.escape(old) + ")!"), Regex.escapeReplacement(quote(nm) + "!"))
                    }
                }
            }
            "delSheet" -> {
                if (refs.size <= 1 || sh == null) return bytes
                val wb = pkg.doc("xl/workbook.xml") ?: return bytes
                val rid = sh.el.getAttribute("r:id")
                sh.el.parentNode.removeChild(sh.el)
                pkg.doc("xl/_rels/workbook.xml.rels")?.getElementsByTagName("Relationship")?.let { rl ->
                    for (i in rl.length - 1 downTo 0) { val e = rl.item(i) as Element; if (e.getAttribute("Id") == rid) e.parentNode.removeChild(e) }
                }
                pkg.delete(sh.path)
                pkg.delete(relsPath(sh.path))
                pkg.doc("[Content_Types].xml")?.getElementsByTagName("Override")?.let { ov ->
                    for (i in ov.length - 1 downTo 0) { val e = ov.item(i) as Element; if (e.getAttribute("PartName") == "/${sh.path}") e.parentNode.removeChild(e) }
                }
                // shu varaqqa tegishli nomlangan diapazonlar va faol varaq ko'rsatkichi
                val dn = wb.getElementsByTagName("definedName")
                for (i in dn.length - 1 downTo 0) {
                    val e = dn.item(i) as Element
                    val ls = e.getAttribute("localSheetId").toIntOrNull()
                    when {
                        ls == si -> e.parentNode.removeChild(e)
                        ls != null && ls > si -> e.setAttribute("localSheetId", (ls - 1).toString())
                        e.textContent.contains(sh.name + "!") || e.textContent.contains("'" + sh.name + "'!") -> e.parentNode.removeChild(e)
                    }
                }
                (wb.getElementsByTagName("definedNames").item(0) as? Element)?.let { if (it.kids().isEmpty()) it.parentNode.removeChild(it) }
                val views = wb.getElementsByTagName("workbookView")
                for (i in 0 until views.length) (views.item(i) as Element).let { it.setAttribute("activeTab", "0"); it.removeAttribute("firstSheet") }
                recalcOnLoad(pkg)
            }
            else -> return bytes
        }
        return pkg.bytes()
    }

    /** Bo'sh XLSX kitob (bitta varaq, standart uslublar). */
    fun newXlsx(sheetName: String = "Sheet1"): ByteArray {
        val files = LinkedHashMap<String, ByteArray>()
        val decl = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n"
        val sn = sheetName.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
        files["[Content_Types].xml"] = (decl + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>""").toByteArray()
        files["_rels/.rels"] = (decl + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>""").toByteArray()
        files["docProps/core.xml"] = (decl + """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:creator>QulayOfis</dc:creator></cp:coreProperties>""").toByteArray()
        files["docProps/app.xml"] = (decl + """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>QulayOfis</Application></Properties>""").toByteArray()
        files["xl/_rels/workbook.xml.rels"] = (decl + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""").toByteArray()
        files["xl/workbook.xml"] = (decl + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$R_NS"><bookViews><workbookView activeTab="0"/></bookViews><sheets><sheet name="$sn" sheetId="1" r:id="rId1"/></sheets><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""").toByteArray()
        files["xl/styles.xml"] = (decl + """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="1"><font><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/><scheme val="minor"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""").toByteArray()
        files["xl/worksheets/sheet1.xml"] = (decl + """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$R_NS"><sheetViews><sheetView tabSelected="1" workbookViewId="0"/></sheetViews><sheetFormatPr defaultRowHeight="15"/><sheetData/><pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/></worksheet>""").toByteArray()
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z -> for ((k, v) in files) { z.putNextEntry(ZipEntry(k)); z.write(v); z.closeEntry() } }
        return bo.toByteArray()
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
