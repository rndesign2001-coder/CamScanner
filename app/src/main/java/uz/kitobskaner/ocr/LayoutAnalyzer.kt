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
    private const val HYPHENS = "-‐‑¬­"

    private class Para(val lines: MutableList<HLine>)

    fun analyze(page: HPage, language: String, opts: LayoutOptions): LayoutResult {
        val langs = language.split('+')
        val uzLatin = opts.fixUzbekApostrophe && langs.contains("uzb")
        val preferCyr = langs.firstOrNull()?.let { it == "rus" || it == "uzb_cyrl" } == true
        val cyrLangs = langs.count { it == "rus" || it == "uzb_cyrl" }
        // aralash yozuvli kitob tanlanmagan bo'lsa, begona yozuvdagi yolg'iz so'zlar xato hisoblanadi
        val singleScript = cyrLangs == 0 || cyrLangs == langs.size || langs == listOf("rus", "eng") || langs == listOf("uzb_cyrl", "rus")

        // so'zlarni tozalash: aralash yozuv, 0→o, apostrof; shovqinni olib tashlash
        page.blocks.forEach { b ->
            b.pars.forEach { p ->
                // tik (90° burilgan) yozuvlar — hoshiyadagi kolontitul, ular matnga aralashmasin
                p.lines.removeAll { l -> isVertical(l) }
                p.lines.forEach { l ->
                    if (uzLatin) joinSplitApostrophes(l)
                    detectBullet(l)
                    l.words.forEach { w -> w.text = TextCleaner.fixWord(w.text, preferCyr, uzLatin) }
                    l.words.removeAll { TextCleaner.isNoise(it.text, it.conf) }
                    if (singleScript) l.words.removeAll { TextCleaner.isStrayScript(it.text, it.conf, cyrillicBook = preferCyr) }
                    trimTrailingGarbage(l)
                    l.refit()
                }
                // sahifa chetidagi (qo'shni varaq qoldig'i) tor, ishonchsiz satrlar
                p.lines.removeAll { l ->
                    l.words.isEmpty() || isEdgeNoise(l, page)
                }
            }
            b.pars.removeAll { it.lines.isEmpty() }
        }
        page.blocks.removeAll { !it.isImage && it.pars.isEmpty() }

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
                is Para -> {
                    val letters = item.lines.sumOf { l -> l.words.sumOf { w -> w.text.count { it.isLetter() } } }
                    val conf = item.lines.flatMap { it.words }.map { it.conf }.average()
                    // bitta-ikkita harfli yoki ishonchsiz qisqa qoldiqlar ("L", "- voy")
                    if (letters < 3 || (letters < 8 && conf < 70)) continue
                    items += LayoutItem.Text(classify(item, bodySize, colLeft, colRight, colW, colCenter))
                }
            }
        }
        return LayoutResult(items, words)
    }

    /** "Payg “ambarimiz" → "Paygʻambarimiz", "ko "pincha" → "koʻpincha" (o'zbek lotin). */
    private fun joinSplitApostrophes(l: HLine) {
        val ws = l.words
        var i = 0
        while (i < ws.size - 1) {
            val a = ws[i]
            val b = ws[i + 1]
            val last = a.text.lastOrNull()
            val bt = b.text
            if (last != null && last in "oOgG" && a.text.length >= 2 && bt.length >= 2 &&
                bt[0] in "“\"'‘’`ʼ´ʻ" && bt[1].isLowerCase()
            ) {
                ws[i] = HWord(a.text + "ʻ" + bt.substring(1), Box(a.box.l, minOf(a.box.t, b.box.t), b.box.r, maxOf(a.box.b, b.box.b)), minOf(a.conf, b.conf)).also {
                    it.bold = a.bold && b.bold; it.stroke = a.stroke
                }
                ws.removeAt(i + 1)
            } else i++
        }
    }

    private fun isVertical(l: HLine): Boolean {
        if (l.words.isEmpty()) return true
        if (l.box.h > l.box.w * 1.3f && l.words.sumOf { it.text.length } >= 2) return true
        val tall = l.words.count { it.box.h > it.box.w * 1.6f && it.text.length >= 3 }
        return tall * 2 > l.words.size
    }

    /**
     * Satr boshidagi bezak belgisi: Tesseract uni "si:", "ik", "#4", "“i" kabi mayda
     * ishonchsiz so'z sifatida o'qiydi. Olib tashlanadi va satr yangi band deb belgilanadi.
     */
    private fun detectBullet(l: HLine) {
        val ws = l.words
        if (ws.size < 2) return
        val first = ws[0]
        val next = ws[1]
        val t = first.text
        val nextStart = next.text.firstOrNull { it.isLetterOrDigit() } ?: return
        // ✓ belgisi odatda "v", "V" yoki "Vv" bo'lib o'qiladi (o'zbek/rus tilida bunday so'z yo'q)
        if (t in setOf("v", "V", "Vv", "vv", "✓", "✔", "√", "•", "●", "▪", "■", "*", "·")) {
            ws.removeAt(0); l.bullet = true
            return
        }
        val nextCap = nextStart.isUpperCase() || nextStart.isDigit()
        if (!nextCap) return
        if (t in setOf("-", "–", "—")) {
            if (t !in setOf("-", "–", "—") || next.box.l - first.box.r > first.box.w) {
                ws.removeAt(0); l.bullet = true
            }
            return
        }
        val letters = t.count { it.isLetter() }
        val hasUpper = t.any { it.isUpperCase() }
        val symbols = t.count { !it.isLetterOrDigit() }
        val medianH = ws.map { it.box.h }.sorted()[ws.size / 2]
        val oddShape = first.box.h > medianH * 1.15f || symbols > 0
        if (letters <= 4 && !hasUpper && t.length <= 5 &&
            (first.conf < 55 || (first.conf < 80 && oddShape))
        ) {
            ws.removeAt(0); l.bullet = true
        }
    }

    /** Satr oxiridagi ishonchsiz bir-ikki belgili qoldiqlar ("i", "o.", "=", "—"). */
    private fun trimTrailingGarbage(l: HLine) {
        while (l.words.size > 1) {
            val w = l.words.last()
            val t = w.text
            val alnum = t.count { it.isLetterOrDigit() }
            if ((alnum <= 1 && t.length <= 2 && w.conf < 70) || (alnum == 0 && w.conf < 85 && t.length <= 3 && t != "..." && t != "…")) {
                l.words.removeAt(l.words.size - 1)
            } else break
        }
    }

    private fun isEdgeNoise(l: HLine, page: HPage): Boolean {
        val conf = l.words.map { it.conf }.average()
        val nearEdge = l.box.l < page.width * 0.04f || l.box.r > page.width * 0.96f
        val narrow = l.box.w < page.width * 0.12f
        val letters = l.words.sumOf { w -> w.text.count { it.isLetter() } }
        return (nearEdge && narrow && conf < 70) || (conf < 35 && letters < 4)
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
        // bezakli bandlar har doim alohida xatboshi
        if (lines.size > 1 && lines.drop(1).any { it.bullet }) {
            val out = ArrayList<Para>()
            var cur = mutableListOf(lines[0])
            for (l in lines.drop(1)) {
                if (l.bullet) { out += splitParagraph(cur, size, colLeft, colRight, colW); cur = mutableListOf(l) } else cur += l
            }
            out += splitParagraph(cur, size, colLeft, colRight, colW)
            return out
        }
        // sarlavhasimon satr (markazda, qisqa, katta harf yoki qalin) bilan oddiy matn chegarasi
        if (lines.size > 1) {
            val colCenter = (colLeft + colRight) / 2f
            fun headingLike(l: HLine): Boolean {
                val letters = l.words.sumOf { w -> w.text.count { it.isLetter() } }
                val bold = l.words.isNotEmpty() && l.words.all { it.bold }
                val upper = letters >= 3 && l.words.all { w -> w.text.filter { it.isLetter() }.all { it.isUpperCase() } }
                val centeredShort = abs(l.box.cx - colCenter) < colW * 0.08f && l.box.w < colW * 0.7f
                return centeredShort && (bold || upper) && l.words.size <= 8
            }
            val idx = (1 until lines.size).firstOrNull { headingLike(lines[it - 1]) != headingLike(lines[it]) }
            if (idx != null) {
                return splitParagraph(lines.subList(0, idx), size, colLeft, colRight, colW) +
                    splitParagraph(lines.subList(idx, lines.size), size, colLeft, colRight, colW)
            }
        }
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
        if (first.bullet) return false
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
        val sameEdges = lines.size >= 2 &&
            lines.maxOf { it.box.l } - lines.minOf { it.box.l } < colW * 0.02f &&
            lines.maxOf { it.box.r } - lines.minOf { it.box.r } < colW * 0.02f
        val centered = !sameEdges &&
            lines.all {
                abs(it.box.cx - colCenter) < colW * 0.07f && it.box.l > colLeft + colW * 0.06f && it.box.r < colRight - colW * 0.06f
            }
        val upper = letters > 3 && text.filter { it.isLetter() }.all { it.isUpperCase() }
        val chapter = CHAPTER.matches(text.trim()) && allWords.size <= 8
        val bullet = lines.first().bullet

        val big = size >= bodySize * 1.25f
        val heading = (!bullet || (centered && (upper || allBold))) && lines.size <= 3 && allWords.size <= 16 && (
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
        val verse = !bullet && lines.size >= 2 &&
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
        val spans = buildSpans(lines, verse = false)
        return TextBlock(
            BlockType.PARAGRAPH,
            if (bullet) listOf(TextSpan("• ")) + spans else spans,
            if (bullet && align == Align.CENTER) Align.JUSTIFY else align,
        )
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
        // qalin bo'laklar orasida (satr chegarasida ham) qolib ketgan yolg'iz so'z — qalin
        for (i in 1 until tokens.size - 1) {
            val t = tokens[i]
            if (!t.bold && tokens[i - 1].bold && tokens[i + 1].bold) tokens[i] = Token(t.text, true, t.italic, t.sep)
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
