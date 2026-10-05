package uz.kitobskaner.pdf

import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.OcrPage
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.data.TextSpan

/** Sahifalarning bloklarini bitta oqimga yig'adi, sahifa chegarasida uzilgan xatboshilarni ulaydi. */
object BookAssembler {

    private const val HYPHENS = "-‐‑¬­"

    fun assemble(pages: List<OcrPage>): List<TextBlock> {
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
}
