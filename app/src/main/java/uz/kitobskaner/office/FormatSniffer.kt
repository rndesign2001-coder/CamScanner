package uz.kitobskaner.office

/** Hujjat formati — fayl mazmunidan aniqlanadi (kengaytma faqat oxirgi chora). */
enum class Fmt(val editable: Boolean = false) {
    DOCX(true), XLSX(true), PPTX(true),
    ODT, ODS, ODP, RTF, DOC, XLS, PPT,
    PDF, CSV, TXT, HTML, EPUB, IMAGE,
    ENCRYPTED, UNKNOWN,
}

object FormatSniffer {

    private fun ByteArray.indexOf(s: String, from: Int = 0, to: Int = size): Int {
        val p = s.toByteArray(Charsets.ISO_8859_1)
        val end = minOf(to, size) - p.size
        var i = from
        while (i <= end) {
            var j = 0
            while (j < p.size && this[i + j] == p[j]) j++
            if (j == p.size) return i
            i++
        }
        return -1
    }

    private fun startsWith(b: ByteArray, vararg v: Int) = b.size >= v.size && v.indices.all { (b[it].toInt() and 0xFF) == v[it] }

    fun byExtension(name: String, mime: String?): Fmt {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> Fmt.PDF
            ext in setOf("docx", "docm", "dotx", "dotm") || mime?.contains("wordprocessingml") == true -> Fmt.DOCX
            ext in setOf("xlsx", "xlsm", "xltx", "xltm") || mime?.contains("spreadsheetml") == true -> Fmt.XLSX
            ext in setOf("pptx", "pptm", "ppsx", "ppsm", "potx", "potm") || mime?.contains("presentationml") == true -> Fmt.PPTX
            ext in setOf("odt", "ott") || mime?.contains("opendocument.text") == true -> Fmt.ODT
            ext in setOf("ods", "ots") || mime?.contains("opendocument.spreadsheet") == true -> Fmt.ODS
            ext in setOf("odp", "otp") || mime?.contains("opendocument.presentation") == true -> Fmt.ODP
            ext == "rtf" || mime == "application/rtf" || mime == "text/rtf" -> Fmt.RTF
            ext in setOf("doc", "dot") || mime == "application/msword" -> Fmt.DOC
            ext in setOf("xls", "xlt") || mime == "application/vnd.ms-excel" -> Fmt.XLS
            ext in setOf("ppt", "pps", "pot") || mime == "application/vnd.ms-powerpoint" -> Fmt.PPT
            ext == "epub" || mime == "application/epub+zip" -> Fmt.EPUB
            ext in setOf("html", "htm", "xhtml") || mime == "text/html" || mime == "application/xhtml+xml" -> Fmt.HTML
            ext == "csv" || mime == "text/csv" || mime == "text/comma-separated-values" -> Fmt.CSV
            ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif") || mime?.startsWith("image/") == true -> Fmt.IMAGE
            ext in setOf("txt", "md", "log", "json", "xml", "ini", "kt", "java", "py", "js", "css", "yaml", "yml") || mime?.startsWith("text/") == true -> Fmt.TXT
            else -> Fmt.UNKNOWN
        }
    }

    /** Mazmun bo'yicha aniqlash. [b] — faylning to'liq baytlari. */
    fun detect(b: ByteArray, name: String, mime: String?): Fmt {
        val byExt = byExtension(name, mime)
        if (b.size >= 5 && b.indexOf("%PDF-", 0, 1024) >= 0) return Fmt.PDF
        if (startsWith(b, 0x50, 0x4B, 0x03, 0x04) || startsWith(b, 0x50, 0x4B, 0x05, 0x06)) {
            // ODF / EPUB: birinchi yozuv "mimetype" (siqilmagan)
            val head = minOf(b.size, 200)
            if (b.indexOf("application/epub+zip", 0, head) >= 0) return Fmt.EPUB
            if (b.indexOf("opendocument.text", 0, head) >= 0) return Fmt.ODT
            if (b.indexOf("opendocument.spreadsheet", 0, head) >= 0) return Fmt.ODS
            if (b.indexOf("opendocument.presentation", 0, head) >= 0) return Fmt.ODP
            // OOXML: markaziy katalogdagi qism nomlari
            return when {
                b.indexOf("word/document.xml") >= 0 || b.indexOf("word/document2.xml") >= 0 -> Fmt.DOCX
                b.indexOf("xl/workbook.xml") >= 0 -> Fmt.XLSX
                b.indexOf("ppt/presentation.xml") >= 0 -> Fmt.PPTX
                b.indexOf("content.xml") >= 0 && byExt in setOf(Fmt.ODT, Fmt.ODS, Fmt.ODP) -> byExt
                else -> if (byExt in setOf(Fmt.DOCX, Fmt.XLSX, Fmt.PPTX, Fmt.EPUB)) byExt else Fmt.UNKNOWN
            }
        }
        if (Cfb.isCfb(b)) {
            return try {
                val c = Cfb(b)
                when {
                    c.has("EncryptedPackage") -> Fmt.ENCRYPTED
                    c.has("WordDocument") -> Fmt.DOC
                    c.has("Workbook") || c.has("Book") -> Fmt.XLS
                    c.has("PowerPoint Document") -> Fmt.PPT
                    else -> Fmt.UNKNOWN
                }
            } catch (_: Exception) {
                Fmt.UNKNOWN
            }
        }
        if (b.indexOf("{\\rtf", 0, 16) == 0 || (b.size > 8 && b.indexOf("{\\rtf", 0, 8) >= 0)) return Fmt.RTF
        when {
            startsWith(b, 0xFF, 0xD8, 0xFF) || startsWith(b, 0x89, 0x50, 0x4E, 0x47) || startsWith(b, 0x47, 0x49, 0x46, 0x38) ||
                startsWith(b, 0x42, 0x4D) && byExt == Fmt.IMAGE -> return Fmt.IMAGE
            b.size > 12 && b.indexOf("RIFF", 0, 4) == 0 && b.indexOf("WEBP", 8, 12) == 8 -> return Fmt.IMAGE
            b.size > 12 && b.indexOf("ftyp", 4, 8) == 4 && (b.indexOf("heic", 8, 12) == 8 || b.indexOf("mif1", 8, 12) == 8) -> return Fmt.IMAGE
        }
        if (looksText(b)) {
            val head = String(b, 0, minOf(b.size, 2048), Charsets.UTF_8).lowercase()
            if (head.contains("<!doctype html") || head.contains("<html")) return Fmt.HTML
            return if (byExt == Fmt.CSV || byExt == Fmt.HTML) byExt else Fmt.TXT
        }
        return byExt
    }

    /** Matn faylimi: boshqaruv belgilari kam. */
    private fun looksText(b: ByteArray): Boolean {
        if (b.isEmpty()) return true
        val n = minOf(b.size, 4096)
        var bad = 0
        for (i in 0 until n) {
            val c = b[i].toInt() and 0xFF
            if (c == 0) return startsWith(b, 0xFF, 0xFE) || startsWith(b, 0xFE, 0xFF)
            if (c < 0x09 || (c in 0x0E..0x1F)) bad++
        }
        return bad * 50 < n
    }
}
