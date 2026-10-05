package uz.kitobskaner.pdf

import uz.kitobskaner.data.Align
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.TextBlock
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Tahrirlanadigan Word (DOCX) hujjati: sarlavhalar, qalin/kursiv, she'rlar va rasmlar bilan. */
object DocxWriter {

    fun write(title: String, blocks: List<TextBlock>, imagesDir: File, out: OutputStream) {
        val images = ArrayList<Pair<String, File>>() // rId -> fayl
        val body = StringBuilder()

        body.append(paragraph("Title", "center", run(title, bold = true, italic = false)))
        var imageNo = 0
        for (b in blocks) {
            when (b.type) {
                BlockType.IMAGE -> {
                    val f = b.image?.let { File(imagesDir, it) } ?: continue
                    if (!f.exists()) continue
                    imageNo++
                    val rid = "rIdImg$imageNo"
                    images += rid to f
                    val cx = 5_400_000L
                    val cy = (cx / b.imageAspect.coerceAtLeast(0.1f)).toLong().coerceAtMost(7_800_000L)
                    val cxFit = if (cy == 7_800_000L) (cy * b.imageAspect).toLong() else cx
                    body.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r>")
                    body.append(drawing(rid, imageNo, cxFit, cy))
                    body.append("</w:r></w:p>")
                }
                else -> {
                    val style = when (b.type) {
                        BlockType.HEADING1 -> "Heading1"
                        BlockType.HEADING2 -> "Heading2"
                        BlockType.VERSE -> "Verse"
                        else -> "Normal"
                    }
                    val jc = when (b.align) {
                        Align.CENTER -> "center"
                        Align.RIGHT -> "right"
                        Align.LEFT -> "left"
                        Align.JUSTIFY -> if (b.type == BlockType.PARAGRAPH) "both" else "left"
                    }
                    val runs = StringBuilder()
                    for (s in b.spans) runs.append(run(s.text, s.bold, s.italic))
                    body.append(paragraph(style, jc, runs.toString()))
                }
            }
        }

        val zip = ZipOutputStream(out)
        fun put(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        put("[Content_Types].xml", CONTENT_TYPES)
        put("_rels/.rels", ROOT_RELS)
        put("word/styles.xml", STYLES)
        val rels = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
        )
        images.forEachIndexed { i, (rid, _) ->
            rels.append("<Relationship Id=\"$rid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image${i + 1}.jpeg\"/>")
        }
        rels.append("</Relationships>")
        put("word/_rels/document.xml.rels", rels.toString())
        put(
            "word/document.xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
                "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                "<w:body>" + body +
                "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
                "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"709\" w:footer=\"709\" w:gutter=\"0\"/>" +
                "</w:sectPr></w:body></w:document>"
        )
        images.forEachIndexed { i, (_, f) ->
            zip.putNextEntry(ZipEntry("word/media/image${i + 1}.jpeg"))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    private fun paragraph(style: String, jc: String, runs: String) =
        "<w:p><w:pPr><w:pStyle w:val=\"$style\"/><w:jc w:val=\"$jc\"/></w:pPr>$runs</w:p>"

    private fun run(text: String, bold: Boolean, italic: Boolean): String {
        val sb = StringBuilder("<w:r>")
        if (bold || italic) {
            sb.append("<w:rPr>")
            if (bold) sb.append("<w:b/><w:bCs/>")
            if (italic) sb.append("<w:i/><w:iCs/>")
            sb.append("</w:rPr>")
        }
        text.split('\n').forEachIndexed { i, part ->
            if (i > 0) sb.append("<w:br/>")
            sb.append("<w:t xml:space=\"preserve\">").append(escape(part)).append("</w:t>")
        }
        sb.append("</w:r>")
        return sb.toString()
    }

    private fun drawing(rid: String, id: Int, cx: Long, cy: Long) =
        "<w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
            "<wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:docPr id=\"$id\" name=\"Rasm $id\"/>" +
            "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"image$id.jpeg\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>" +
            "</a:graphicData></a:graphic></wp:inline></w:drawing>"

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> if (c >= ' ' || c == '\t') sb.append(c)
            }
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "</Types>"

    private const val ROOT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"

    private const val STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" w:cs=\"Times New Roman\"/>" +
            "<w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/><w:lang w:val=\"uz-UZ\"/></w:rPr></w:rPrDefault>" +
            "<w:pPrDefault><w:pPr><w:spacing w:after=\"0\" w:line=\"300\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/>" +
            "<w:pPr><w:ind w:firstLine=\"567\"/></w:pPr></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/>" +
            "<w:pPr><w:spacing w:before=\"1200\" w:after=\"600\"/><w:ind w:firstLine=\"0\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"44\"/></w:rPr></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/>" +
            "<w:pPr><w:keepNext/><w:spacing w:before=\"480\" w:after=\"240\"/><w:ind w:firstLine=\"0\"/><w:outlineLvl w:val=\"0\"/></w:pPr>" +
            "<w:rPr><w:b/><w:sz w:val=\"36\"/></w:rPr></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/>" +
            "<w:pPr><w:keepNext/><w:spacing w:before=\"300\" w:after=\"120\"/><w:ind w:firstLine=\"0\"/><w:outlineLvl w:val=\"1\"/></w:pPr>" +
            "<w:rPr><w:b/><w:sz w:val=\"30\"/></w:rPr></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Verse\"><w:name w:val=\"Verse\"/><w:basedOn w:val=\"Normal\"/>" +
            "<w:pPr><w:spacing w:before=\"160\" w:after=\"160\"/><w:ind w:left=\"1134\" w:firstLine=\"0\"/></w:pPr></w:style>" +
            "</w:styles>"
}
