package uz.kitobskaner.pdf

import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.OcrPage
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.data.TextSpan

/** Sahifalarning bloklarini bitta oqimga yig'adi, sahifa chegarasida uzilgan xatboshilarni ulaydi. */
object BookAssembler {

    private const val HYPHENS = "-‐‑¬­"

    fun assemble(input: List<OcrPage>): List<TextBlock> {
        val pages = removeRunningHeads(input)
        val out = ArrayList<TextBlock>()
        for (page in pages) {
            for ((i, block) in page.blocks.withIndex()) {
                val prev = out.lastOrNull()
                if (i == 0 && prev != null && canJoin(prev, block)) {
                    out[out.size - 1] = join(prev, block)
                } else out += block
            }
        }
        return out
    }

    private fun canJoin(a: TextBlock, b: TextBlock): Boolean {
        if (a.type != BlockType.PARAGRAPH || b.type != BlockType.PARAGRAPH) return false
        val at = a.plainText.trimEnd()
        val bt = b.plainText.trimStart()
        if (at.isEmpty() || bt.isEmpty()) return false
        val last = at.last()
        if (last in HYPHENS) return true
        if (last in ".!?…" || (last in "»\"”" && at.length > 1 && at[at.length - 2] in ".!?…")) return false
        val firstLetter = bt.firstOrNull { it.isLetter() } ?: return false
        return firstLetter.isLowerCase() || last == ','
    }

    private fun join(a: TextBlock, b: TextBlock): TextBlock {
        val spans = a.spans.toMutableList()
        val lastSpan = spans.removeAt(spans.size - 1)
        val lastText = lastSpan.text.trimEnd()
        val firstB = b.plainText.trimStart()
        val (newLast, sep) = if (lastText.isNotEmpty() && lastText.last() in HYPHENS &&
            lastText.length > 1 && lastText[lastText.length - 2].isLetter() &&
            firstB.firstOrNull()?.isLowerCase() == true
        ) lastText.dropLast(1) to "" else lastText to " "
        spans += TextSpan(newLast + sep, lastSpan.bold, lastSpan.italic)
        b.spans.forEachIndexed { i, s ->
            spans += if (i == 0) s.copy(text = s.text.trimStart()) else s
        }
        return a.copy(spans = spans)
    }

    private fun key(s: String) = s.lowercase().filter { it.isLetter() }

    /**
     * Kolontitullarni (har sahifada takrorlanadigan kitob/bob nomi) olib tashlaydi.
     * Sahifa boshidagi yoki oxiridagi qisqa bloklar boshqa sahifalardagiga o'xshasa — kolontitul.
     */
    fun removeRunningHeads(pages: List<OcrPage>): List<OcrPage> {
        if (pages.size < 2) return pages
        val candidates = ArrayList<String>()
        for (p in pages) {
            val texts = p.blocks.filter { it.type != BlockType.IMAGE }
            listOfNotNull(texts.firstOrNull(), texts.getOrNull(1), texts.lastOrNull()).forEach { b ->
                val k = key(b.plainText)
                if (k.length in 6..90) candidates += k
            }
        }
        val heads = ArrayList<String>()
        for (c in candidates.distinct()) {
            if (heads.any { similar(it, c) }) continue
            val pagesWith = pages.count { p -> p.blocks.any { b -> b.type != BlockType.IMAGE && containsHead(key(b.plainText), c) } }
            if (pagesWith >= 2 && pagesWith >= pages.size * 0.3) heads += c
        }
        if (heads.isEmpty()) return pages
        return pages.map { p ->
            val blocks = p.blocks.mapNotNull { b ->
                if (b.type == BlockType.IMAGE) return@mapNotNull b
                val k = key(b.plainText)
                val head = heads.firstOrNull { containsHead(k, it) } ?: return@mapNotNull b
                if (k.length <= head.length * 1.3) null else stripHead(b, head)
            }
            p.copy(blocks = blocks)
        }
    }

    /** Blok matni kolontitul bilan boshlanadimi (yoki o'zi kolontitulmi). */
    private fun containsHead(k: String, head: String): Boolean {
        if (k.length < head.length * 0.75) return false
        return similar(k.take(head.length), head)
    }

    private fun stripHead(b: TextBlock, head: String): TextBlock {
        // boshidan kolontitul uzunligidagi harflarni olib tashlaymiz (so'z chegarasigacha)
        val text = b.plainText
        var letters = 0
        var cut = 0
        while (cut < text.length && letters < head.length) {
            if (text[cut].isLetter()) letters++
            cut++
        }
        while (cut < text.length && !text[cut].isWhitespace()) cut++
        var remaining = cut
        val spans = ArrayList<TextSpan>()
        for (s in b.spans) {
            if (remaining >= s.text.length) { remaining -= s.text.length; continue }
            spans += s.copy(text = s.text.substring(remaining))
            remaining = 0
        }
        if (spans.isNotEmpty()) spans[0] = spans[0].copy(text = spans[0].text.trimStart(' ', ',', '.', ';', ':', '—', '-'))
        return b.copy(spans = spans.filter { it.text.isNotEmpty() })
    }

    private fun similar(a: String, b: String): Boolean {
        if (a == b) return true
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return true
        return levenshtein(a, b) <= maxLen * 0.2
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }
}
