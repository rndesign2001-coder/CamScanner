package uz.kitobskaner.ocr

import uz.kitobskaner.data.Align
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.OcrWord
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.data.TextSpan
import kotlin.math.abs
import kotlin.math.max

class LayoutOptions(
    val removeHeaders: Boolean,
    val keepImages: Boolean,
    val fixUzbekApostrophe: Boolean,
)

sealed class LayoutItem {
    class Text(val block: TextBlock) : LayoutItem()
    class Image(val box: Box) : LayoutItem()
}

class LayoutResult(val items: List<LayoutItem>, val words: List<OcrWord>)

/**
 * Tesseract hOCR tuzilmasidan kitob tuzilmasini tiklaydi:
 * sarlavhalar, xatboshilar, she'rlar, tekislash, so'z bo'linishlarini (de-defis) birlashtirish,
 * sahifa raqami va kolontitullarni olib tashlash.
 */
object LayoutAnalyzer {

    private val PAGE_NUMBER = Regex("^[\\s\\-–—.·•(\\[]*(\\d{1,4}|[IVXLCDM]{1,7}|[ivxlcdm]{1,7})[\\s\\-–—.·•)\\]]*$")
    private val CHAPTER = Regex(
        "^(bob|qism|bo[ʻ'‘’`]?lim|fasl|глава|часть|раздел|бо[бў]|қисм|бўлим|фасл|chapter|part|section)\\b.*",
        RegexOption.IGNORE_CASE
    )
    private val UZ_APOSTROPHE = Regex("([oOgG])['‘’`ʼ´]")
    private const val HYPHENS = "-‐‑¬­"

    private class Para(val lines: MutableList<HLine>)

    fun analyze(page: HPage, language: String, opts: LayoutOptions): LayoutResult {
        val uzLatin = opts.fixUzbekApostrophe && language.split('+').contains("uzb")
        fun fix(s: String) = if (uzLatin) UZ_APOSTROPHE.replace(s) { it.groupValues[1] + "ʻ" } else s

        // so'zlarni tozalash
        page.blocks.forEach { b ->
            b.pars.forEach { p -> p.lines.forEach { l -> l.words.forEach { w -> w.text = fix(w.text) } } }
        }

        val allLines = page.blocks.filter { !it.isImage }.flatMap { b -> b.pars.flatMap { it.lines } }

        // qidiriladigan PDF uchun so'zlar (barcha satrlar)
        val words = ArrayList<OcrWord>()
        for (line in allLines) for (w in line.words) {
            val baseline = line.baselineAt(w.box.l).coerceIn(w.box.t, w.box.b + (line.size * 0.4f).toInt())
            words += OcrWord(w.text, w.box.l, w.box.t, w.box.w, w.box.h, baseline, max(1, line.size.toInt()))
        }

        val items = ArrayList<LayoutItem>()
        if (allLines.isEmpty()) {
            if (opts.keepImages) page.blocks.filter { it.isImage && isUsefulImage(it.box, page) }
                .forEach { items += LayoutItem.Image(it.box) }
            return LayoutResult(items, words)
        }

        // --- sahifa statistikasi
        val bodyLines = allLines.filter { it.words.size >= 3 }.ifEmpty { allLines }
        val bodySize = weightedMedian(bodyLines.map { it.size to it.words.size })
        val wideLines = allLines.filter { it.words.size >= 4 }.ifEmpty { allLines }
        val colLeft = percentile(wideLines.map { it.box.l.toFloat() }, 0.1f)
        val colRight = percentile(wideLines.map { it.box.r.toFloat() }, 0.9f)
        val colW = max(1f, colRight - colLeft)
        val colCenter = (colLeft + colRight) / 2f

        // --- kolontitul / sahifa raqami
        val removed = HashSet<HLine>()
        if (opts.removeHeaders) {
            val byTop = allLines.sortedBy { it.box.t }
            for (line in byTop.take(2)) {
                if (line.box.b > page.height * 0.12f) break
                if (isPageNumber(line) || isRunningHead(line, bodySize)) removed += line else break
            }
            for (line in byTop.reversed().take(2)) {
                if (line.box.t < page.height * 0.88f) break
                if (isPageNumber(line) || isRunningHead(line, bodySize)) removed += line else break
            }
        }

        // --- xatboshilar va rasmlar tartibi
        val flow = ArrayList<Any>() // Para yoki Box
        for (block in page.blocks) {
            if (block.isImage) {
                if (opts.keepImages && isUsefulImage(block.box, page)) flow += block.box
                continue
            }
            for (p in block.pars) {
                val lines = p.lines.filter { it !in removed }
                if (lines.isEmpty()) continue
                flow.addAll(splitParagraph(lines, bodySize, colLeft, colRight, colW))
            }
        }
        // ketma-ket xatboshilarni birlashtirish
        val merged = ArrayList<Any>()
        for (item in flow) {
            val prev = merged.lastOrNull()
            if (item is Para && prev is Para && shouldMerge(prev, item, bodySize, colLeft, colRight)) {
                prev.lines += item.lines
            } else merged += item
        }

        for (item in merged) {
            when (item) {
                is Box -> items += LayoutItem.Image(item)
                is Para -> items += LayoutItem.Text(classify(item, bodySize, colLeft, colRight, colW, colCenter))
            }
        }
        return LayoutResult(items, words)
    }

    private fun isUsefulImage(box: Box, page: HPage): Boolean {
        val pageArea = page.width.toLong() * page.height
        return box.area > pageArea * 0.015 && box.w > page.width * 0.08 && box.h > page.height * 0.04
    }

    private fun isPageNumber(line: HLine) = PAGE_NUMBER.matches(line.text.trim())

    private fun isRunningHead(line: HLine, bodySize: Float): Boolean {
        val ws = line.words
        if (ws.size > 10 || line.size > bodySize * 1.15f) return false
        val first = ws.first().text.trim('.', '-', '–', '—')
        val last = ws.last().text.trim('.', '-', '–', '—')
        return ws.size >= 2 && (first.all { it.isDigit() } && first.isNotEmpty() || last.all { it.isDigit() } && last.isNotEmpty())
    }

    private fun splitParagraph(lines: List<HLine>, size: Float, colLeft: Float, colRight: Float, colW: Float): List<Para> {
        val wide = lines.count { it.box.w >= colW * 0.6f } >= (lines.size + 1) / 2
        if (!wide || lines.size < 2) return listOf(Para(lines.toMutableList()))
        val leftRef = percentile(lines.map { it.box.l.toFloat() }, 0.3f)
        val result = ArrayList<Para>()
        var cur = mutableListOf(lines[0])
        for (i in 1 until lines.size) {
            val prev = lines[i - 1]
            val line = lines[i]
            val indent = line.box.l - leftRef
            val prevShort = prev.box.r < colRight - size * 2.2f
            val prevEnds = endsClause(prev.text)
            val newPara = (indent > size * 0.6f && (prevEnds || prevShort)) || (prevShort && prevEnds)
            if (newPara) {
                result += Para(cur); cur = mutableListOf(line)
            } else cur += line
        }
        result += Para(cur)
        return result
    }

    private fun shouldMerge(a: Para, b: Para, size: Float, colLeft: Float, colRight: Float): Boolean {
        val last = a.lines.last()
        val first = b.lines.first()
        val gap = first.box.t - last.box.b
        if (gap > size * 1.3f || gap < -size) return false
        if (abs(avgSize(a) - avgSize(b)) > size * 0.15f) return false
        val lastText = last.text.trimEnd()
        val hyphenated = lastText.lastOrNull()?.let { it in HYPHENS } == true
        val fullWidth = last.box.r >= colRight - size * 1.5f
        val noIndent = first.box.l <= colLeft + size * 0.5f
        val lowerStart = startsLower(first.text)
        return fullWidth && noIndent && !endsTerminal(lastText) && (lowerStart || hyphenated)
    }

    private fun classify(p: Para, bodySize: Float, colLeft: Float, colRight: Float, colW: Float, colCenter: Float): TextBlock {
        val lines = p.lines
        val size = avgSize(p)
        val allWords = lines.flatMap { it.words }
        val text = allWords.joinToString(" ") { it.text }
        val letters = allWords.sumOf { w -> w.text.count { it.isLetter() } }
        val boldLetters = allWords.filter { it.bold }.sumOf { w -> w.text.count { it.isLetter() } }
        val allBold = letters > 0 && boldLetters >= letters * 0.85f
        val centered = lines.all { abs(it.box.cx - colCenter) < colW * 0.07f && it.box.l > colLeft + colW * 0.06f }
        val upper = letters > 3 && text.filter { it.isLetter() }.all { it.isUpperCase() }
        val chapter = CHAPTER.matches(text.trim()) && allWords.size <= 8

        val big = size >= bodySize * 1.25f
        val heading = lines.size <= 3 && allWords.size <= 16 && (
            big ||
                (allBold && (centered || lines.size == 1) && allWords.size <= 12) ||
                (centered && upper && allWords.size <= 10) ||
                chapter
            )

        if (heading) {
            val level = if (size >= bodySize * 1.55f || chapter) BlockType.HEADING1 else BlockType.HEADING2
            return TextBlock(level, buildSpans(lines, verse = false), if (centered) Align.CENTER else Align.LEFT)
        }

        val shortLines = lines.dropLast(1).count { it.box.r < colRight - colW * 0.18f }
        val capStarts = lines.count { l -> l.text.firstOrNull { it.isLetter() || it in "—–-" }?.let { it.isUpperCase() || it in "—–-" } == true }
        val verse = lines.size >= 2 &&
            shortLines >= max(1, ((lines.size - 1) * 0.7f).toInt()) &&
            capStarts >= lines.size * 0.6f && colW > 0
        if (verse) {
            val align = if (centered) Align.CENTER else Align.LEFT
            return TextBlock(BlockType.VERSE, buildSpans(lines, verse = true), align)
        }

        val rightAligned = lines.all { it.box.l > colLeft + colW * 0.35f && abs(it.box.r - colRight) < bodySize * 1.5f }
        val align = when {
            centered && lines.size <= 4 -> Align.CENTER
            rightAligned -> Align.RIGHT
            else -> Align.JUSTIFY
        }
        return TextBlock(BlockType.PARAGRAPH, buildSpans(lines, verse = false), align)
    }

    private class Token(var text: String, val bold: Boolean, val italic: Boolean, val sep: String)

    private fun buildSpans(lines: List<HLine>, verse: Boolean): List<TextSpan> {
        val tokens = ArrayList<Token>()
        for ((li, line) in lines.withIndex()) {
            for ((wi, w) in line.words.withIndex()) {
                var sep = if (tokens.isEmpty()) "" else " "
                if (wi == 0 && li > 0) {
                    if (verse) sep = "\n"
                    else {
                        val prev = tokens.lastOrNull()
                        if (prev != null) {
                            val pt = prev.text
                            val last = pt.lastOrNull()
                            if (last != null && last in HYPHENS && pt.length > 1) {
                                if (pt[pt.length - 2].isLetter() && startsLower(w.text)) {
                                    prev.text = pt.dropLast(1)
                                    sep = ""
                                } else if (last != '-' && last != '‐') {
                                    prev.text = pt.dropLast(1) + "-"
                                    sep = ""
                                } else sep = ""
                            }
                        }
                    }
                }
                tokens += Token(w.text, w.bold, w.italic, sep)
            }
        }
        val spans = ArrayList<TextSpan>()
        val sb = StringBuilder()
        var curBold = false
        var curItalic = false
        fun flush() {
            if (sb.isNotEmpty()) spans += TextSpan(sb.toString(), curBold, curItalic)
            sb.setLength(0)
        }
        tokens.firstOrNull()?.let { curBold = it.bold; curItalic = it.italic }
        for (t in tokens) {
            sb.append(t.sep) // ajratgich oldingi uslubda qoladi
            if (t.bold != curBold || t.italic != curItalic) {
                flush()
                curBold = t.bold
                curItalic = t.italic
            }
            sb.append(t.text)
        }
        flush()
        return spans
    }

    private fun avgSize(p: Para): Float {
        var s = 0f
        var n = 0
        for (l in p.lines) { s += l.size * l.words.size; n += l.words.size }
        return if (n == 0) 0f else s / n
    }

    private fun startsLower(s: String): Boolean = s.firstOrNull { it.isLetter() }?.isLowerCase() == true

    private fun endsTerminal(s: String): Boolean {
        val t = s.trimEnd()
        if (t.isEmpty()) return false
        val c = t.last()
        if (c in ".!?…") return true
        if (c in "»\"”)'’" && t.length > 1 && t[t.length - 2] in ".!?…") return true
        return false
    }

    private fun endsClause(s: String): Boolean {
        val t = s.trimEnd()
        return endsTerminal(t) || t.lastOrNull()?.let { it in ":»\"”" } == true
    }

    private fun weightedMedian(values: List<Pair<Float, Int>>): Float {
        if (values.isEmpty()) return 1f
        val sorted = values.sortedBy { it.first }
        val total = sorted.sumOf { it.second }
        var acc = 0
        for ((v, w) in sorted) {
            acc += w
            if (acc * 2 >= total) return v
        }
        return sorted.last().first
    }

    private fun percentile(values: List<Float>, p: Float): Float {
        if (values.isEmpty()) return 0f
        val s = values.sorted()
        return s[((s.size - 1) * p).toInt().coerceIn(0, s.size - 1)]
    }
}
