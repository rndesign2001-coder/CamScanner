package uz.kitobskaner.office

/**
 * OLE2 / Compound File Binary (CFB) o'quvchisi — eski Office fayllari (DOC, XLS, PPT) va
 * shifrlangan OOXML konteyneri shu formatda saqlanadi. Faqat o'qish.
 */
class Cfb(private val b: ByteArray) {
    class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Long
    private val fat: IntArray
    private val miniFat: IntArray
    val entries: List<Entry>
    private val miniStream: ByteArray

    init {
        require(isCfb(b)) { "not a compound file" }
        sectorSize = 1 shl u16(0x1E)
        miniSectorSize = 1 shl u16(0x20)
        miniCutoff = u32(0x38).toLong() and 0xFFFFFFFFL
        // FAT sektorlari ro'yxati (DIFAT)
        val fatSectors = ArrayList<Int>()
        for (i in 0 until 109) {
            val s = u32(0x4C + i * 4)
            if (s >= 0) fatSectors += s
        }
        var difat = u32(0x44)
        var guard = 0
        while (difat >= 0 && guard++ < 10000) {
            val off = sectorOffset(difat)
            if (off + sectorSize > b.size) break
            val per = sectorSize / 4 - 1
            for (i in 0 until per) { val s = u32(off + i * 4); if (s >= 0) fatSectors += s }
            difat = u32(off + per * 4)
        }
        val perSector = sectorSize / 4
        fat = IntArray(fatSectors.size * perSector) { i ->
            val off = sectorOffset(fatSectors[i / perSector]) + (i % perSector) * 4
            if (off + 4 <= b.size) u32(off) else -1
        }
        // katalog
        val dir = chain(u32(0x30))
        val list = ArrayList<Entry>()
        var i = 0
        while (i + 128 <= dir.size) {
            val nameLen = (dir[i + 64].toInt() and 0xFF) or ((dir[i + 65].toInt() and 0xFF) shl 8)
            val type = dir[i + 66].toInt() and 0xFF
            val n = (nameLen / 2 - 1).coerceIn(0, 31)
            val name = String(dir, i, n * 2, Charsets.UTF_16LE)
            val start = le32(dir, i + 116)
            val size = le32(dir, i + 120).toLong() and 0xFFFFFFFFL
            list += Entry(name, type, start, size)
            i += 128
        }
        entries = list
        val miniFatBytes = chain(u32(0x3C))
        miniFat = IntArray(miniFatBytes.size / 4) { le32(miniFatBytes, it * 4) }
        val root = list.firstOrNull { it.type == 5 }
        miniStream = if (root != null) chain(root.start, root.size) else ByteArray(0)
    }

    private fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(o: Int) = le32(b, o)
    private fun sectorOffset(s: Int) = (s + 1) * sectorSize

    private fun chain(start: Int, limit: Long = Long.MAX_VALUE): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s >= 0 && s < fat.size && guard++ < 2_000_000 && out.size() < limit) {
            val off = sectorOffset(s)
            if (off < 0 || off + sectorSize > b.size) {
                if (off < b.size && off >= 0) out.write(b, off, b.size - off)
                break
            }
            out.write(b, off, sectorSize)
            s = fat.getOrElse(s) { -2 }
        }
        val r = out.toByteArray()
        return if (limit < r.size) r.copyOf(limit.toInt()) else r
    }

    private fun miniChain(start: Int, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s >= 0 && guard++ < 2_000_000 && out.size() < size) {
            val off = s * miniSectorSize
            if (off + miniSectorSize > miniStream.size) break
            out.write(miniStream, off, miniSectorSize)
            s = miniFat.getOrElse(s) { -2 }
        }
        val r = out.toByteArray()
        return if (size < r.size) r.copyOf(size.toInt()) else r
    }

    fun has(name: String) = entries.any { it.type == 2 && it.name.equals(name, true) }

    /** Oqim (stream) mazmuni; topilmasa null. */
    fun stream(name: String): ByteArray? {
        val e = entries.firstOrNull { it.type == 2 && it.name.equals(name, true) } ?: return null
        return if (e.size < miniCutoff) miniChain(e.start, e.size) else chain(e.start, e.size)
    }

    companion object {
        fun isCfb(b: ByteArray) = b.size >= 512 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() &&
            b[2] == 0x11.toByte() && b[3] == 0xE0.toByte() && b[4] == 0xA1.toByte() && b[5] == 0xB1.toByte()

        fun le32(a: ByteArray, o: Int): Int =
            (a[o].toInt() and 0xFF) or ((a[o + 1].toInt() and 0xFF) shl 8) or ((a[o + 2].toInt() and 0xFF) shl 16) or (a[o + 3].toInt() shl 24)

        fun le16(a: ByteArray, o: Int): Int = (a[o].toInt() and 0xFF) or ((a[o + 1].toInt() and 0xFF) shl 8)
    }
}
