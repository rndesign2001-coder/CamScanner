package uz.kitobskaner.ui.page

import uz.kitobskaner.data.Align
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.data.TextSpan

/**
 * Oddiy tahrirlash formati:
 *  "# " — katta sarlavha, "## " — kichik sarlavha, **qalin**, [rasm] — rasm o'rni,
 *  bloklar bo'sh qator bilan ajratiladi, blok ichidagi qator ko'chishi — she'r.
 */
object Markup {
    private const val IMAGE = "[rasm]"

    fun toMarkup(blocks: List<TextBlock>): String = blocks.joinToString("\n\n") { b ->
        when (b.type) {
            BlockType.IMAGE -> IMAGE
            BlockType.HEADING1 -> "# " + spans(b.spans, inHeading = true)
            BlockType.HEADING2 -> "## " + spans(b.spans, inHeading = true)
            else -> spans(b.spans, inHeading = false)
        }
    }

    private fun spans(spans: List<TextSpan>, inHeading: Boolean): String = buildString {
        for (s in spans) {
            if (s.bold && !inHeading && s.text.isNotBlank()) {
                val lead = s.text.takeWhile { it == ' ' }
                val trail = s.text.takeLastWhile { it == ' ' }
                append(lead).append("**").append(s.text.trim(' ')).append("**").append(trail)
            } else append(s.text)
        }
    }.trim()

    fun fromMarkup(text: String, old: List<TextBlock>): List<TextBlock> {
        val images = old.filter { it.type == BlockType.IMAGE }.toMutableList()
        val result = ArrayList<TextBlock>()
        val chunks = text.replace("\r", "").split(Regex("\\n[ \\t]*\\n+")).map { it.trim('\n') }.filter { it.isNotBlank() }
        for (chunk in chunks) {
            val c = chunk.trim()
            if (c == IMAGE) {
                if (images.isNotEmpty()) result += images.removeAt(0)
                continue
            }
            val prevSame = old.getOrNull(result.size)
            when {
                c.startsWith("## ") -> result += TextBlock(BlockType.HEADING2, parseSpans(c.removePrefix("## ").trim()), keepAlign(prevSame, BlockType.HEADING2, Align.CENTER))
                c.startsWith("# ") -> result += TextBlock(BlockType.HEADING1, parseSpans(c.removePrefix("# ").trim()), keepAlign(prevSame, BlockType.HEADING1, Align.CENTER))
                c.contains('\n') -> result += TextBlock(BlockType.VERSE, parseSpans(c), keepAlign(prevSame, BlockType.VERSE, Align.LEFT))
                else -> result += TextBlock(BlockType.PARAGRAPH, parseSpans(c), keepAlign(prevSame, BlockType.PARAGRAPH, Align.JUSTIFY))
            }
        }
        // ishlatilmagan rasmlar oxirida qoladi
        result += images
        return result
    }

    private fun keepAlign(old: TextBlock?, type: BlockType, default: Align) =
        if (old != null && old.type == type) old.align else default

    fun parseSpans(s: String): List<TextSpan> {
        val parts = s.split("**")
        val out = ArrayList<TextSpan>()
        parts.forEachIndexed { i, part ->
            if (part.isNotEmpty()) out += TextSpan(part, bold = i % 2 == 1)
        }
        return out
    }
}
