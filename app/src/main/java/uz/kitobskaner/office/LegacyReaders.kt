package uz.kitobskaner.office

import uz.kitobskaner.office.Cfb.Companion.le16
import uz.kitobskaner.office.Cfb.Companion.le32

/**
 * Eski binar Office formatlari (Word 97-2003 .doc, Excel 97-2003 .xls, PowerPoint 97-2003 .ppt)
 * dan matn va jadval ma'lumotlarini o'qiydi. Formatlash (shriftlar, ranglar) o'qilmaydi —
 * matn tuzilishi (paragraflar, jadval kataklari, slaydlar, varaqlar) saqlanadi.
 */
object LegacyReaders {

    class EncryptedLegacy : Exception("encrypted")

    // ---------------------------------------------------------------- DOC

    /** Hujjat paragraflari; jadval qatorlari kataklari '\u0007' bilan ajratilgan holda. */
    fun docParagraphs(cfb: Cfb): List<String> {
        val w = cfb.stream("WordDocument") ?: throw IllegalArgumentException("no WordDocument")
        if (w.size < 0x40) throw IllegalArgumentException("bad FIB")
        val ident = le16(w, 0)
        if (ident != 0xA5EC) {
            // Word 6/95 (va undan eski): matn bitta bo'lak, 8-bitli kodlash
            if (ident !in setOf(0xA5DC, 0xA697, 0xA699)) throw IllegalArgumentException("bad FIB")
            if (le16(w, 0x0A) and 0x0100 != 0) throw EncryptedLegacy()
            val fcMin = le32(w, 0x18)
            val fcMac = le32(w, 0x1C)
            if (fcMin !in 0 until fcMac || fcMac > w.size) throw IllegalArgumentException("bad FIB")
            return cleanWordText(String(w, fcMin, fcMac - fcMin, charset("windows-1252")))
        }
        val flags = le16(w, 0x0A)
        if (flags and 0x0100 != 0) throw EncryptedLegacy()
        val table = cfb.stream(if (flags and 0x0200 != 0) "1Table" else "0Table") ?: throw IllegalArgumentException("no table")
        val csw = le16(w, 32)
        val lwStart = 34 + csw * 2 + 2
        val cslw = le16(w, 34 + csw * 2)
        val ccpText = le32(w, lwStart + 3 * 4)
        val fcStart = lwStart + cslw * 4 + 2
        val fcClx = le32(w, fcStart + 33 * 8)
        val lcbClx = le32(w, fcStart + 33 * 8 + 4)
        val sb = StringBuilder()
        if (lcbClx > 0 && fcClx >= 0 && fcClx + lcbClx <= table.size) {
            var pos = fcClx
            val end = fcClx + lcbClx
            while (pos < end) {
                val t = table[pos].toInt() and 0xFF
                if (t == 1) { pos += 3 + le16(table, pos + 1); continue }
                if (t != 2) break
                val lcb = le32(table, pos + 1)
                val plc = pos + 5
                val n = (lcb - 4) / 12
                for (i in 0 until n) {
                    val cp0 = le32(table, plc + i * 4)
                    val cp1 = le32(table, plc + (i + 1) * 4)
                    val pcd = plc + (n + 1) * 4 + i * 8
                    val fcRaw = le32(table, pcd + 2)
                    val cnt = (cp1 - cp0).coerceAtLeast(0)
                    if (fcRaw and 0x40000000 != 0) {
                        val off = (fcRaw and 0x3FFFFFFF) / 2
                        if (off + cnt <= w.size) sb.append(String(w, off, cnt, charset("windows-1252")))
                    } else {
                        if (fcRaw + cnt * 2 <= w.size) sb.append(String(w, fcRaw, cnt * 2, Charsets.UTF_16LE))
                    }
                    if (sb.length >= ccpText && ccpText > 0) break
                }
                break
            }
        } else {
            // juda eski (Word 6/95) fayllar: matn bir bo'lak, 8-bitli
            val fcMin = le32(w, 0x18)
            val fcMac = le32(w, 0x1C)
            if (fcMin in 0 until fcMac && fcMac <= w.size) sb.append(String(w, fcMin, fcMac - fcMin, charset("windows-1252")))
        }
        val text = if (ccpText in 1..sb.length) sb.substring(0, ccpText) else sb.toString()
        return cleanWordText(text)
    }

    /** Maydon kodlarini, rasm belgilarini olib tashlaydi; paragraflarga bo'ladi. */
    private fun cleanWordText(raw: String): List<String> {
        val sb = StringBuilder()
        var depth = 0
        val inCode = ArrayList<Boolean>()
        for (ch in raw) {
            when (ch) {
                '\u0013' -> { depth++; inCode += true }
                '\u0014' -> { if (inCode.isNotEmpty()) inCode[inCode.size - 1] = false }
                '\u0015' -> { if (inCode.isNotEmpty()) inCode.removeAt(inCode.size - 1); depth = maxOf(0, depth - 1) }
                '\u0001', '\u0008', '\u0005', '\u0002' -> {}
                else -> if (inCode.none { it }) sb.append(ch)
            }
        }
        return sb.toString().replace("\u000c", "\r\u000c\r").split('\r')
    }

    // ---------------------------------------------------------------- PPT

    class PptSlide(val title: String, val body: List<String>)

    fun pptSlides(cfb: Cfb): List<PptSlide> {
        if (cfb.has("EncryptedSummary")) throw EncryptedLegacy()
        val d = cfb.stream("PowerPoint Document") ?: throw IllegalArgumentException("no PowerPoint Document")
        val slides = ArrayList<PptSlide>()
        // 1) SlideListWithText (instance 0 — slaydlar)
        fun walk(start: Int, end: Int, depth: Int, visit: (type: Int, inst: Int, off: Int, len: Int, container: Boolean) -> Boolean) {
            var p = start
            while (p + 8 <= end) {
                val vi = le16(d, p)
                val type = le16(d, p + 2)
                val len = le32(d, p + 4)
                if (len < 0 || p + 8 + len > end) break
                val container = vi and 0xF == 0xF
                val goIn = visit(type, vi shr 4, p + 8, len, container)
                if (container && goIn && depth < 12) walk(p + 8, p + 8 + len, depth + 1, visit)
                p += 8 + len
            }
        }
        var cur: Pair<StringBuilder, ArrayList<String>>? = null
        var curType = 1
        val bySlwt = ArrayList<Pair<StringBuilder, ArrayList<String>>>()
        walk(0, d.size, 0) { type, inst, off, len, container ->
            if (container) return@walk type == 0x03E8 || (type == 0x0FF0 && inst == 0)
            when (type) {
                0x03F3 -> { cur = StringBuilder() to ArrayList(); bySlwt += cur!! }
                0x0F9F -> curType = le32(d, off)
                0x0FA0, 0x0FA8 -> cur?.let { c ->
                    val t = if (type == 0x0FA0) String(d, off, len, Charsets.UTF_16LE) else String(d, off, len, Charsets.ISO_8859_1)
                    val txt = t.replace('\u000b', '\n')
                    if (curType == 0 || curType == 6) { if (c.first.isNotEmpty()) c.first.append(' '); c.first.append(txt.replace('\r', ' ')) }
                    else c.second += txt.split('\r').filter { it.isNotBlank() }
                }
            }
            true
        }
        bySlwt.forEach { (t, b) -> slides += PptSlide(t.toString().trim(), b) }
        if (slides.any { it.title.isNotEmpty() || it.body.isNotEmpty() }) return slides
        val fromSlwt = ArrayList(slides)
        // 2) Zaxira: slayd konteynerlari (0x03EE) ichidagi barcha matn atomlari
        slides.clear()
        var p = 0
        while (p + 8 <= d.size) {
            val vi = le16(d, p)
            val type = le16(d, p + 2)
            val len = le32(d, p + 4)
            if (len < 0 || p + 8 + len > d.size) break
            if (type == 0x03EE && vi and 0xF == 0xF) {
                val texts = ArrayList<String>()
                walk(p + 8, p + 8 + len, 1) { t, _, off, l, container ->
                    if (!container && (t == 0x0FA0 || t == 0x0FA8)) {
                        val s = if (t == 0x0FA0) String(d, off, l, Charsets.UTF_16LE) else String(d, off, l, Charsets.ISO_8859_1)
                        texts += s.replace('\u000b', '\n').split('\r').filter { it.isNotBlank() }
                    }
                    true
                }
                slides += PptSlide(texts.firstOrNull() ?: "", texts.drop(1))
            }
            p += 8 + len
        }
        return if (slides.any { it.title.isNotEmpty() || it.body.isNotEmpty() } || fromSlwt.isEmpty()) slides else fromSlwt
    }

    // ---------------------------------------------------------------- XLS

    class XlsSheet(val name: String, val cells: Map<Long, String>)

    fun xlsSheets(cfb: Cfb): List<XlsSheet> {
        val d = cfb.stream("Workbook") ?: cfb.stream("Book") ?: throw IllegalArgumentException("no Workbook")
        val names = ArrayList<Pair<String, Int>>() // nom, turi
        val sst = ArrayList<String>()
        val sheets = ArrayList<XlsSheet>()
        var cells: HashMap<Long, String>? = null
        var bofCount = 0
        var pendingStringCell: Long? = null
        var p = 0
        fun rec(at: Int) = le16(d, at) to le16(d, at + 2)
        while (p + 4 <= d.size) {
            val (id, len) = rec(p)
            val o = p + 4
            if (o + len > d.size) break
            if (id == 0x002F) throw EncryptedLegacy() // FILEPASS
            try { when (id) {
                0x0809 -> {
                    if (bofCount > 0) {
                        val idx = bofCount - 1
                        val dt = le16(d, o + 2)
                        cells = if (dt == 0x0010) HashMap() else null
                        if (cells != null) sheets += XlsSheet(names.getOrNull(idx)?.first ?: "Sheet${sheets.size + 1}", cells!!)
                    }
                    bofCount++
                }
                0x0085 -> { // BOUNDSHEET
                    val cch = d[o + 6].toInt() and 0xFF
                    val hi = (d[o + 7].toInt() and 1) == 1
                    val nm = if (hi) String(d, o + 8, minOf(cch * 2, len - 8), Charsets.UTF_16LE) else String(d, o + 8, minOf(cch, len - 8), Charsets.ISO_8859_1)
                    names += nm to (d[o + 5].toInt() and 0xFF)
                }
                0x00FC -> { // SST (+ CONTINUE)
                    val chunks = arrayListOf(d.copyOfRange(o, o + len))
                    var q = o + len
                    while (q + 4 <= d.size && le16(d, q) == 0x003C) {
                        val l = le16(d, q + 2)
                        if (q + 4 + l > d.size) break
                        chunks += d.copyOfRange(q + 4, q + 4 + l)
                        q += 4 + l
                    }
                    readSst(chunks, sst)
                }
                0x00FD -> cells?.put(key(d, o), sst.getOrElse(le32(d, o + 6)) { "" }) // LABELSST
                0x0203 -> cells?.put(key(d, o), num(java.lang.Double.longBitsToDouble(le64(d, o + 6)))) // NUMBER
                0x027E -> cells?.put(key(d, o), num(rk(le32(d, o + 6)))) // RK
                0x00BD -> { // MULRK
                    val row = le16(d, o)
                    val c0 = le16(d, o + 2)
                    val n = (len - 6) / 6
                    for (i in 0 until n) cells?.put(row.toLong() * 1000 + c0 + i, num(rk(le32(d, o + 4 + i * 6 + 2))))
                }
                0x0204 -> { // LABEL
                    val cch = le16(d, o + 6)
                    val hi = len > 8 && (d[o + 8].toInt() and 1) == 1
                    val s = if (len >= 9 + cch && !hi) String(d, o + 9, cch, Charsets.ISO_8859_1)
                    else if (hi) String(d, o + 9, minOf(cch * 2, len - 9), Charsets.UTF_16LE)
                    else String(d, o + 8, minOf(cch, len - 8), Charsets.ISO_8859_1)
                    cells?.put(key(d, o), s)
                }
                0x0205 -> if (d[o + 7].toInt() == 0) cells?.put(key(d, o), if (d[o + 6].toInt() != 0) "TRUE" else "FALSE") // BOOLERR
                0x0006 -> { // FORMULA
                    val k = key(d, o)
                    if ((d[o + 12].toInt() and 0xFF) == 0xFF && (d[o + 13].toInt() and 0xFF) == 0xFF) {
                        when (d[o + 6].toInt()) {
                            0 -> pendingStringCell = k
                            1 -> cells?.put(k, if (d[o + 8].toInt() != 0) "TRUE" else "FALSE")
                            2 -> cells?.put(k, "#ERR")
                        }
                    } else cells?.put(k, num(java.lang.Double.longBitsToDouble(le64(d, o + 6))))
                }
                0x0207 -> pendingStringCell?.let { k -> // STRING (formula natijasi)
                    val cch = le16(d, o)
                    val hi = (d[o + 2].toInt() and 1) == 1
                    cells?.put(k, if (hi) String(d, o + 3, minOf(cch * 2, len - 3), Charsets.UTF_16LE) else String(d, o + 3, minOf(cch, len - 3), Charsets.ISO_8859_1))
                    pendingStringCell = null
                }
            } } catch (_: IndexOutOfBoundsException) {
            } catch (_: IllegalArgumentException) {
            }
            p = o + len
        }
        return sheets
    }

    private fun key(d: ByteArray, o: Int) = le16(d, o).toLong() * 1000 + le16(d, o + 2)

    private fun le64(a: ByteArray, o: Int): Long = (le32(a, o).toLong() and 0xFFFFFFFFL) or (le32(a, o + 4).toLong() shl 32)

    private fun rk(v: Int): Double {
        val div = v and 1 != 0
        val d = if (v and 2 != 0) (v shr 2).toDouble() else java.lang.Double.longBitsToDouble((v.toLong() and 0xFFFFFFFCL) shl 32)
        return if (div) d / 100 else d
    }

    private fun num(d: Double): String {
        if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) return d.toLong().toString()
        val s = String.format(java.util.Locale.US, "%.10g", d).trimEnd('0').trimEnd('.')
        return if (s.contains('e')) d.toString() else s
    }

    /** SST qatorlari; CONTINUE chegarasida yangi bayroq bayti hisobga olinadi. */
    private fun readSst(chunks: List<ByteArray>, out: MutableList<String>) {
        var ci = 0
        var pos = 0
        fun ensure() { while (ci < chunks.size && pos >= chunks[ci].size) { ci++; pos = 0 } }
        fun u8(): Int { ensure(); if (ci >= chunks.size) throw IndexOutOfBoundsException(); return chunks[ci][pos++].toInt() and 0xFF }
        fun u16() = u8() or (u8() shl 8)
        fun u32() = u16() or (u16() shl 16)
        fun skip(n: Int) { var k = n; while (k > 0) { ensure(); if (ci >= chunks.size) return; val t = minOf(k, chunks[ci].size - pos); pos += t; k -= t } }
        try {
            u32() // jami
            val unique = u32()
            for (i in 0 until unique) {
                val cch = u16()
                val flags = u8()
                var hi = flags and 1 != 0
                val runs = if (flags and 8 != 0) u16() else 0
                val ext = if (flags and 4 != 0) u32() else 0
                val sb = StringBuilder(cch)
                var left = cch
                while (left > 0) {
                    if (ci < chunks.size && pos >= chunks[ci].size) {
                        ci++; pos = 0
                        if (ci >= chunks.size) break
                        hi = (chunks[ci][pos++].toInt() and 1) != 0 // yangi bo'lakda bayroq
                    }
                    if (ci >= chunks.size) break
                    if (hi) sb.append(u16().toChar()) else sb.append(u8().toChar())
                    left--
                }
                out += sb.toString()
                skip(runs * 4 + ext)
            }
        } catch (_: IndexOutOfBoundsException) {
        }
    }
}
