package uz.kitobskaner.pdf

import uz.kitobskaner.data.OcrPage
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Excel (XLSX): har bir sahifa alohida varaq. So'zlar satrlarga (asos chizig'i bo'yicha),
 * satrlar esa katta bo'shliqlar bo'yicha ustunlarga ajratiladi — jadvallar kataklarga tushadi.
 */
object XlsxWriter {

    private class Cell(val text: String, val x: Int, val bold: Boolean)

    fun write(pages: List<OcrPage>, out: OutputStream) = writeSheets(pages.map { rowsOf(it) }, out)

    /** Tayyor matn qatorlaridan (har bir qator — kataklar ro'yxati). */
    fun writeRows(sheets: List<List<List<String>>>, out: OutputStream) =
        writeSheets(sheets.map { rows -> rows.map { r -> r.mapIndexed { i, t -> Cell(t, i * 1000, false) } } }, out)

    private fun writeSheets(input: List<List<List<Cell>>>, out: OutputStream) {
        val sheets = input.ifEmpty { listOf(emptyList()) }
        val zip = ZipOutputStream(out)
        fun put(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val ct = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
        )
        sheets.indices.forEach {
            ct.append("<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        ct.append("</Types>")
        put("[Content_Types].xml", ct.toString())
        put(
            "_rels/.rels",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>"
        )
        val wb = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
        )
        val rels = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        )
        sheets.indices.forEach {
            wb.append("<sheet name=\"${it + 1}\" sheetId=\"${it + 1}\" r:id=\"rId${it + 1}\"/>")
            rels.append("<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>")
        }
        wb.append("</sheets></workbook>")
        rels.append("<Relationship Id=\"rIdS\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
        put("xl/workbook.xml", wb.toString())
        put("xl/_rels/workbook.xml.rels", rels.toString())
        put("xl/styles.xml", STYLES)
        sheets.forEachIndexed { i, rows -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(rows)) }
        zip.finish()
        zip.flush()
    }

    private fun rowsOf(page: OcrPage): List<List<Cell>> {
        if (page.words.isEmpty()) {
            // so'z koordinatalari yo'q (masalan, tahrirlangan) — bloklar bo'yicha
            return page.blocks.map { b -> listOf(Cell(b.plainText.trim(), 0, b.spans.all { it.bold })) }
        }
        val boldWords = HashSet<String>()
        page.blocks.forEach { b -> b.spans.filter { it.bold }.forEach { s -> s.text.split(' ').forEach { boldWords += it.trim() } } }
        val words = page.words.sortedWith(compareBy({ it.b }, { it.x }))
        val lines = ArrayList<MutableList<uz.kitobskaner.data.OcrWord>>()
        for (w in words) {
            val line = lines.lastOrNull()
            if (line != null && abs(line.last().b - w.b) <= max(3, w.s / 2)) line += w else lines += mutableListOf(w)
        }
        return lines.map { line ->
            line.sortBy { it.x }
            val cells = ArrayList<Cell>()
            val sb = StringBuilder()
            var startX = line.first().x
            var prevEnd = -1
            var bold = true
            for (w in line) {
                val gap = if (prevEnd < 0) 0 else w.x - prevEnd
                if (prevEnd >= 0 && gap > w.s * 1.3f) {
                    cells += Cell(sb.toString(), startX, bold)
                    sb.setLength(0); startX = w.x; bold = true
                }
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(w.t)
                bold = bold && w.t in boldWords
                prevEnd = w.x + w.w
            }
            cells += Cell(sb.toString(), startX, bold)
            cells
        }
    }

    private fun sheetXml(rows: List<List<Cell>>): String {
        // ustunlarni sahifadagi X pozitsiyasi bo'yicha moslash
        val starts = rows.flatMap { r -> r.map { it.x } }.sorted()
        val columns = ArrayList<Int>()
        for (x in starts) if (columns.isEmpty() || x - columns.last() > 40) columns += x
        fun colOf(x: Int): Int {
            var best = 0
            var d = Int.MAX_VALUE
            columns.forEachIndexed { i, c -> if (abs(c - x) < d) { d = abs(c - x); best = i } }
            return best
        }
        val sb = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
        )
        rows.forEachIndexed { ri, row ->
            val r = ri + 1
            sb.append("<row r=\"$r\">")
            var lastCol = -1
            for (cell in row) {
                var c = if (row.size == 1) 0 else colOf(cell.x)
                if (c <= lastCol) c = lastCol + 1
                lastCol = c
                val ref = colName(c) + r
                val style = if (cell.bold) " s=\"1\"" else ""
                val num = cell.text.replace(" ", "").replace(',', '.')
                if (num.matches(Regex("-?\\d{1,12}(\\.\\d+)?"))) {
                    sb.append("<c r=\"$ref\"$style><v>$num</v></c>")
                } else {
                    sb.append("<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">")
                        .append(escape(cell.text)).append("</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val m = (n - 1) % 26
            sb.insert(0, 'A' + m)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun escape(s: String): String {
        val sb = StringBuilder()
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> if (c >= ' ') sb.append(c)
        }
        return sb.toString()
    }

    private const val STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>" +
            "</styleSheet>"
}
