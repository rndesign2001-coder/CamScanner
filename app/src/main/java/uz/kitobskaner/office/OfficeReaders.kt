package uz.kitobskaner.office

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import uz.kitobskaner.data.Align
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.data.TextSpan
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Zip ichidagi kerakli XML fayllarni xotiraga o'qiydi (Office hujjatlari zip arxivdir). */
private fun readZip(input: InputStream, want: (String) -> Boolean): Map<String, ByteArray> {
    val out = HashMap<String, ByteArray>()
    ZipInputStream(input.buffered()).use { z ->
        while (true) {
            val e = z.nextEntry ?: break
            if (!e.isDirectory && want(e.name)) out[e.name] = z.readBytes()
            z.closeEntry()
        }
    }
    return out
}

private fun parser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    setInput(bytes.inputStream(), "UTF-8")
}

private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

private fun boolAttr(v: String?) = v == null || (v != "0" && v != "false")

// ---------------------------------------------------------------- Word (DOCX)

object DocxReader {
    /** Paragraflar, sarlavhalar, qalin/kursiv va jadvallar (qatorlar " | " bilan). */
    fun read(input: InputStream): List<TextBlock> {
        val files = readZip(input) { it == "word/document.xml" }
        val xml = files["word/document.xml"] ?: return emptyList()
        val p = parser(xml)
        val blocks = ArrayList<TextBlock>()
        var spans = ArrayList<TextSpan>()
        var style = ""
        var align = Align.LEFT
        var bold = false
        var italic = false
        var inRun = false
        var inText = false
        var tableDepth = 0
        var cellTexts = ArrayList<String>()
        val cell = StringBuilder()
        var inCell = false

        fun flushParagraph() {
            if (inCell) {
                val t = spans.joinToString("") { it.text }
                if (cell.isNotEmpty() && t.isNotEmpty()) cell.append(' ')
                cell.append(t)
                spans = ArrayList()
                return
            }
            if (spans.isEmpty()) {
                spans = ArrayList(); return
            }
            val type = when {
                style.startsWith("Heading1", true) || style.equals("Title", true) -> BlockType.HEADING1
                style.startsWith("Heading", true) || style.equals("Subtitle", true) -> BlockType.HEADING2
                else -> BlockType.PARAGRAPH
            }
            blocks += TextBlock(type, mergeSpans(spans), align)
            spans = ArrayList()
        }

        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "w:p" -> { spans = ArrayList(); style = ""; align = Align.JUSTIFY }
                    "w:pStyle" -> style = p.attr("w:val") ?: ""
                    "w:jc" -> align = when (p.attr("w:val")) {
                        "center" -> Align.CENTER
                        "right", "end" -> Align.RIGHT
                        "both", "distribute" -> Align.JUSTIFY
                        else -> Align.LEFT
                    }
                    "w:r" -> { inRun = true; bold = false; italic = false }
                    "w:b" -> if (inRun) bold = boolAttr(p.attr("w:val"))
                    "w:i" -> if (inRun) italic = boolAttr(p.attr("w:val"))
                    "w:t" -> inText = true
                    "w:tab" -> if (inRun) spans += TextSpan("\t", bold, italic)
                    "w:br" -> if (inRun) spans += TextSpan("\n", bold, italic)
                    "w:tbl" -> tableDepth++
                    "w:tr" -> if (tableDepth == 1) cellTexts = ArrayList()
                    "w:tc" -> if (tableDepth == 1) { inCell = true; cell.setLength(0) }
                }
                XmlPullParser.TEXT -> if (inText) spans += TextSpan(p.text, bold, italic)
                XmlPullParser.END_TAG -> when (p.name) {
                    "w:t" -> inText = false
                    "w:r" -> inRun = false
                    "w:p" -> flushParagraph()
                    "w:tc" -> if (tableDepth == 1) { cellTexts += cell.toString(); inCell = false }
                    "w:tr" -> if (tableDepth == 1 && cellTexts.isNotEmpty()) {
                        blocks += TextBlock(BlockType.PARAGRAPH, listOf(TextSpan(cellTexts.joinToString("  |  "))), Align.LEFT)
                    }
                    "w:tbl" -> tableDepth--
                }
            }
            p.next()
        }
        return blocks
    }

    private fun mergeSpans(spans: List<TextSpan>): List<TextSpan> {
        val out = ArrayList<TextSpan>()
        for (s in spans) {
            val last = out.lastOrNull()
            if (last != null && last.bold == s.bold && last.italic == s.italic) out[out.size - 1] = last.copy(text = last.text + s.text)
            else out += s
        }
        return out
    }
}

// ---------------------------------------------------------------- Excel (XLSX)

class Sheet(val name: String, val rows: MutableList<MutableList<String>>)

object XlsxReader {
    fun read(input: InputStream): List<Sheet> {
        val files = readZip(input) {
            it == "xl/workbook.xml" || it == "xl/sharedStrings.xml" || it == "xl/_rels/workbook.xml.rels" ||
                (it.startsWith("xl/worksheets/") && it.endsWith(".xml"))
        }
        val shared = files["xl/sharedStrings.xml"]?.let { readShared(it) } ?: emptyList()
        // varaq nomlari va fayllari
        val rels = HashMap<String, String>()
        files["xl/_rels/workbook.xml.rels"]?.let { b ->
            val p = parser(b)
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.attr("Id"); val target = p.attr("Target")
                    if (id != null && target != null) rels[id] = "xl/" + target.removePrefix("/xl/").removePrefix("/")
                }
                p.next()
            }
        }
        val sheets = ArrayList<Pair<String, String>>()
        files["xl/workbook.xml"]?.let { b ->
            val p = parser(b)
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                    val rid = p.attr("r:id")
                    val path = rels[rid] ?: "xl/worksheets/sheet${sheets.size + 1}.xml"
                    sheets += (p.attr("name") ?: "${sheets.size + 1}") to path
                }
                p.next()
            }
        }
        if (sheets.isEmpty()) files.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted()
            .forEachIndexed { i, f -> sheets += "${i + 1}" to f }
        return sheets.map { (name, path) -> Sheet(name, files[path]?.let { readSheet(it, shared) } ?: mutableListOf()) }
    }

    private fun readShared(b: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = parser(b)
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> { inSi = true; sb.setLength(0) }; "t" -> inT = true }
                XmlPullParser.TEXT -> if (inSi && inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> { out += sb.toString(); inSi = false } }
            }
            p.next()
        }
        return out
    }

    fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) { if (c.isLetter()) n = n * 26 + (c.uppercaseChar() - 'A' + 1) else break }
        return n - 1
    }

    private fun readSheet(b: ByteArray, shared: List<String>): MutableList<MutableList<String>> {
        val rows = ArrayList<MutableList<String>>()
        val p = parser(b)
        var rowIdx = -1
        var col = 0
        var type: String? = null
        var inV = false
        var inIs = false
        val value = StringBuilder()
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> rowIdx = (p.attr("r")?.toIntOrNull()?.minus(1)) ?: (rowIdx + 1)
                    "c" -> {
                        col = p.attr("r")?.let { colIndex(it) } ?: (col + 1)
                        type = p.attr("t"); value.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inV = true
                }
                XmlPullParser.TEXT -> if (inV) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "is" -> inIs = false
                    "c" -> {
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: raw
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw.toDoubleOrNull()?.let { d -> if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else raw } ?: raw
                        }
                        if (rowIdx in 0..100_000 && col in 0..2000) {
                            while (rows.size <= rowIdx) rows += ArrayList<String>()
                            val r = rows[rowIdx]
                            while (r.size <= col) r += ""
                            r[col] = text
                        }
                    }
                }
            }
            p.next()
        }
        return rows
    }
}

// ---------------------------------------------------------------- PowerPoint (PPTX)

class Slide(val title: String, val lines: List<String>)

object PptxReader {
    fun read(input: InputStream): List<Slide> {
        val files = readZip(input) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
        val ordered = files.keys.sortedBy { it.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0 }
        return ordered.map { name ->
            val p = parser(files.getValue(name))
            val paragraphs = ArrayList<Pair<String, Boolean>>() // matn, sarlavhami
            var isTitle = false
            val sb = StringBuilder()
            var inT = false
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "p:sp" -> isTitle = false
                        "p:ph" -> { val t = p.attr("type"); if (t == "title" || t == "ctrTitle") isTitle = true }
                        "a:p" -> sb.setLength(0)
                        "a:t" -> inT = true
                    }
                    XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                    XmlPullParser.END_TAG -> when (p.name) {
                        "a:t" -> inT = false
                        "a:p" -> if (sb.isNotBlank()) paragraphs += sb.toString().trim() to isTitle
                    }
                }
                p.next()
            }
            val title = paragraphs.firstOrNull { it.second }?.first ?: paragraphs.firstOrNull()?.first ?: ""
            Slide(title, paragraphs.map { it.first }.filter { it != title })
        }
    }
}
