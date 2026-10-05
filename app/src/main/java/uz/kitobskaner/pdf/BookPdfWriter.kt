package uz.kitobskaner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import uz.kitobskaner.data.Align
import uz.kitobskaner.data.AppSettings
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.image.ImageUtils
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Aniqlangan matnni qayta sahifalab, chiroyli kitob ko'rinishidagi PDF yaratadi.
 * Matn haqiqiy shrift bilan yoziladi — nusxalash va qidirish mumkin.
 */
class BookPdfWriter(private val context: Context, private val settings: AppSettings) {

    private companion object {
        /** Aniqroq joylashuv uchun ichki birlik: 1 pt = S birlik. */
        const val S = 4f
    }

    private class TocEntry(val title: String, val level: Int, val page: Int)

    private val fonts = Fonts.get(context, settings.font)
    private val pageW = settings.pageSize.widthPt
    private val pageH = settings.pageSize.heightPt
    private val scaleK = pageW / 420f // A5 ga nisbatan
    private val marginTop = 50f * scaleK
    private val marginBottom = 56f * scaleK
    private val marginSide = 46f * scaleK
    private val contentW = ((pageW - 2 * marginSide) * S).toInt()
    private val contentTop = marginTop * S
    private val contentBottom = (pageH - marginBottom) * S
    private val fontSize = settings.fontSize * (if (settings.pageSize.widthPt > 500) 1.08f else 1f) * S
    private val lineH = fontSize * settings.lineSpacing * 1.17f

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private var y = 0f
    private var physicalPages = 0
    private var bodyPages = 0
    private var locale: Locale = Locale.getDefault()
    private var language = ""
    private val toc = ArrayList<TocEntry>()

    fun write(
        title: String,
        language: String,
        blocks: List<TextBlock>,
        imagesDir: File,
        out: OutputStream,
        onProgress: (Float) -> Unit,
    ): Int {
        this.language = language
        locale = when {
            language.startsWith("rus") -> Locale("ru")
            language.startsWith("uzb") -> Locale("uz")
            else -> Locale.ENGLISH
        }
        doc = PdfDocument()
        try {
            if (settings.titlePage) drawTitlePage(title, blocks)
            var prevType: BlockType? = null
            blocks.forEachIndexed { i, block ->
                drawBlock(block, prevType, imagesDir)
                prevType = block.type
                if (i % 10 == 0) onProgress(i.toFloat() / max(1, blocks.size))
            }
            if (settings.tableOfContents && toc.size >= 2) drawToc()
            finishPage()
            if (physicalPages == 0) { newPage(); finishPage() }
            doc.writeTo(out)
            onProgress(1f)
            return physicalPages
        } finally {
            doc.close()
        }
    }

    // ---------------------------------------------------------------- sahifalar

    private fun newPage(numbered: Boolean = true) {
        finishPage()
        physicalPages++
        if (numbered) bodyPages++
        val info = PdfDocument.PageInfo.Builder(pageW.roundToInt(), pageH.roundToInt(), physicalPages).create()
        val p = doc.startPage(info)
        p.canvas.scale(1f / S, 1f / S)
        page = p
        pageNumbered = numbered
        y = contentTop
    }

    private var pageNumbered = true

    private fun finishPage() {
        val p = page ?: return
        if (settings.pageNumbers && pageNumbered) {
            val paint = textPaint(fontSize * 0.82f, fonts.regular).apply {
                color = Color.rgb(90, 90, 90)
                textAlign = Paint.Align.CENTER
            }
            p.canvas.drawText(bodyPages.toString(), pageW * S / 2f, (pageH - marginBottom * 0.45f) * S, paint)
        }
        doc.finishPage(p)
        page = null
    }

    private fun ensurePage() {
        if (page == null) newPage()
    }

    private val canvas get() = page!!.canvas
    private val atTop get() = y <= contentTop + 0.5f

    // ---------------------------------------------------------------- bloklar

    private fun textPaint(size: Float, tf: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = tf
        color = Color.rgb(20, 20, 24)
        flags = flags or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
        textLocale = locale
    }

    private fun spannable(block: TextBlock, forceBold: Boolean): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (s in block.spans) {
            val start = sb.length
            sb.append(s.text)
            val bold = forceBold || s.bold
            if (bold || s.italic) {
                sb.setSpan(FontSpan(fonts.pick(bold, s.italic)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        // ortiqcha bo'sh joylar
        var i = sb.length
        while (i > 0 && sb[i - 1].isWhitespace() && sb[i - 1] != '\n') i--
        if (i < sb.length) sb.delete(i, sb.length)
        return sb
    }

    private fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        align: Layout.Alignment,
        justify: Boolean,
        spacing: Float,
        firstIndent: Int = 0,
        maxLines: Int = Int.MAX_VALUE,
    ): StaticLayout {
        val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setMaxLines(maxLines)
        if (firstIndent > 0) b.setIndents(intArrayOf(firstIndent, 0), null)
        if (justify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        return b.build()
    }

    private fun drawBlock(block: TextBlock, prevType: BlockType?, imagesDir: File) {
        when (block.type) {
            BlockType.IMAGE -> drawImage(block, imagesDir)
            BlockType.HEADING1, BlockType.HEADING2 -> drawHeading(block)
            BlockType.VERSE -> drawVerse(block)
            BlockType.PARAGRAPH -> drawParagraph(block, prevType)
        }
    }

    private fun drawParagraph(block: TextBlock, prevType: BlockType?) {
        val text = spannable(block, false)
        if (text.isBlank()) return
        val paint = textPaint(fontSize, fonts.regular)
        val special = block.align == Align.CENTER || block.align == Align.RIGHT
        val noIndent = prevType == null || prevType == BlockType.HEADING1 || prevType == BlockType.HEADING2 ||
            prevType == BlockType.IMAGE || special
        val indent = if (noIndent) 0 else (fontSize * 1.6f).toInt()
        val align = when (block.align) {
            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        if (special) addSpace(lineH * 0.4f)
        flow(text, 0f, minFirst = 2) { t, first, maxLines ->
            layout(t, paint, contentW, align, block.align == Align.JUSTIFY, settings.lineSpacing,
                if (first) indent else 0, maxLines)
        }
        if (special) addSpace(lineH * 0.4f)
    }

    private fun drawVerse(block: TextBlock) {
        val text = spannable(block, false)
        if (text.isBlank()) return
        val paint = textPaint(fontSize, fonts.regular)
        val center = block.align == Align.CENTER
        val probe = layout(text, paint, contentW, Layout.Alignment.ALIGN_NORMAL, false, settings.lineSpacing)
        var maxW = 0f
        for (i in 0 until probe.lineCount) maxW = max(maxW, probe.getLineWidth(i))
        val dx = if (center) 0f else max(0f, (contentW - maxW) / 2f) * 0.7f
        addSpace(lineH * 0.5f)
        flow(text, dx, minFirst = 2) { t, _, maxLines ->
            if (center) layout(t, paint, contentW, Layout.Alignment.ALIGN_CENTER, false, settings.lineSpacing, 0, maxLines)
            else layout(t, paint, (contentW - dx).toInt(), Layout.Alignment.ALIGN_NORMAL, false, settings.lineSpacing, 0, maxLines)
        }
        addSpace(lineH * 0.5f)
    }

    private fun drawHeading(block: TextBlock) {
        val h1 = block.type == BlockType.HEADING1
        val size = fontSize * if (h1) 1.55f else 1.22f
        val text = spannable(block, true)
        if (text.isBlank()) return
        val paint = textPaint(size, fonts.bold)
        val align = if (block.align == Align.CENTER || h1) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        val l = layout(text, paint, contentW, align, false, 1.15f)
        val before = if (h1) lineH * 2.2f else lineH * 1.2f
        val after = if (h1) lineH * 1.1f else lineH * 0.6f
        ensurePage()
        // sarlavha sahifa oxirida yolg'iz qolmasin
        val need = (if (atTop) 0f else before) + l.height + after + lineH * 3
        if (!atTop && y + need > contentBottom) newPage()
        if (!atTop) y += before
        val plain = text.toString().replace('\n', ' ').trim()
        toc += TocEntry(plain, if (h1) 1 else 2, bodyPages)
        drawWhole(l, 0f)
        y += after
    }

    private fun drawImage(block: TextBlock, imagesDir: File) {
        val name = block.image ?: return
        val file = File(imagesDir, name)
        if (!file.exists()) return
        ensurePage()
        val maxH = (contentBottom - contentTop) * 0.62f
        var w = contentW * 0.92f
        var h = w / block.imageAspect.coerceAtLeast(0.1f)
        if (h > maxH) { h = maxH; w = h * block.imageAspect }
        val gap = lineH * 0.6f
        if (!atTop && y + gap + h > contentBottom) newPage()
        if (!atTop) y += gap
        // ~200 dpi dan ortiq piksel kerak emas
        val targetPx = ceil(w / S / 72f * 200f).toInt().coerceIn(200, 2000)
        val bmp: Bitmap = ImageUtils.decodeFile(file, max(targetPx, (targetPx / block.imageAspect).toInt())) ?: return
        val left = marginSide * S + (contentW - w) / 2f
        canvas.drawBitmap(bmp, null, RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        bmp.recycle()
        y += h + gap
    }

    private fun addSpace(dy: Float) {
        if (page != null && !atTop) y = min(contentBottom, y + dy)
    }

    private fun drawWhole(l: StaticLayout, dx: Float) {
        canvas.save()
        canvas.translate(marginSide * S + dx, y)
        l.draw(canvas)
        canvas.restore()
        y += l.height
    }

    /**
     * Matnni sahifalar bo'ylab oqizadi. Sahifaga sig'gan qism maxLines bilan alohida maket
     * sifatida to'liq chiziladi (kesishsiz) — PDF'da takroriy yashirin matn qolmaydi va
     * tekislash (justify) uzilish joyida ham saqlanadi. Qolgan matn keyingi sahifada davom etadi.
     */
    private fun flow(
        text: CharSequence,
        dx: Float,
        minFirst: Int,
        build: (CharSequence, Boolean, Int) -> StaticLayout,
    ) {
        ensurePage()
        var rest: CharSequence = text
        var first = true
        while (rest.isNotEmpty()) {
            val l = build(rest, first, Int.MAX_VALUE)
            val count = l.lineCount
            val available = contentBottom - y
            var end = 0
            while (end < count && l.getLineBottom(end) <= available) end++
            if (end >= count) {
                drawWhole(l, dx)
                return
            }
            // yolg'iz satrlar (orphan/widow) nazorati
            if (first && end < min(minFirst, count) && !atTop) { newPage(); continue }
            if (count - end == 1 && end > 2) end--
            if (end == 0) {
                if (atTop) end = 1 else { newPage(); continue }
            }
            val part = build(rest, first, end)
            drawWhole(part, dx)
            var next = l.getLineStart(end)
            while (next < rest.length && rest[next] == ' ') next++
            rest = rest.subSequence(next, rest.length)
            first = false
            newPage()
        }
    }

    // ---------------------------------------------------------------- titul va mundarija

    private fun drawTitlePage(title: String, blocks: List<TextBlock>) {
        newPage(numbered = false)
        val c = canvas
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(79, 70, 229) }
        val titlePaint = textPaint(fontSize * 2.1f, fonts.bold)
        val tl = layout(title, titlePaint, contentW, Layout.Alignment.ALIGN_CENTER, false, 1.1f)
        val top = pageH * S * 0.34f
        c.save()
        c.translate(marginSide * S, top)
        tl.draw(c)
        c.restore()
        val lineY = top + tl.height + lineH
        c.drawRect(pageW * S / 2 - 40 * S * scaleK, lineY, pageW * S / 2 + 40 * S * scaleK, lineY + 1.6f * S, accent)
        val words = blocks.sumOf { b -> b.spans.sumOf { s -> s.text.count { it == ' ' } } }
        val sub = textPaint(fontSize * 0.9f, fonts.italic).apply {
            color = Color.rgb(100, 100, 110); textAlign = Paint.Align.CENTER
        }
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        c.drawText("≈ $words so'z  •  $date", pageW * S / 2, lineY + lineH * 2, sub)
        val brand = textPaint(fontSize * 0.75f, fonts.regular).apply {
            color = Color.rgb(140, 140, 150); textAlign = Paint.Align.CENTER
        }
        c.drawText("Kitob Skaner", pageW * S / 2, (pageH - marginBottom) * S, brand)
        finishPage()
    }

    private fun drawToc() {
        newPage()
        val heading = TextBlock(BlockType.HEADING1, listOf(uz.kitobskaner.data.TextSpan(tocTitle())), Align.CENTER)
        val size = fontSize * 1.55f
        val hl = layout(heading.plainText, textPaint(size, fonts.bold), contentW, Layout.Alignment.ALIGN_CENTER, false, 1.15f)
        drawWhole(hl, 0f)
        y += lineH * 1.2f
        val entries = toc.toList()
        for (e in entries) {
            if (y + lineH > contentBottom) newPage()
            val paint = textPaint(fontSize, if (e.level == 1) fonts.bold else fonts.regular)
            val indent = if (e.level == 1) 0f else fontSize * 1.4f
            val num = e.page.toString()
            val numW = paint.measureText(num)
            val avail = contentW - indent - numW - fontSize * 1.5f
            val t = TextUtils.ellipsize(e.title, paint, avail, TextUtils.TruncateAt.END).toString()
            val baseline = y + fontSize
            val x0 = marginSide * S + indent
            canvas.drawText(t, x0, baseline, paint)
            val tw = paint.measureText(t)
            val dots = textPaint(fontSize, fonts.regular).apply { color = Color.rgb(150, 150, 160) }
            val dotW = dots.measureText(" .")
            var dx = x0 + tw + fontSize * 0.4f
            val stop = marginSide * S + contentW - numW - fontSize * 0.4f
            val sb = StringBuilder()
            while (dx + dotW < stop) { sb.append(" ."); dx += dotW }
            canvas.drawText(sb.toString(), x0 + tw + fontSize * 0.2f, baseline, dots)
            canvas.drawText(num, marginSide * S + contentW - numW, baseline, paint)
            y += lineH * (if (e.level == 1) 1.35f else 1.1f)
        }
    }

    private fun tocTitle() = when {
        language.startsWith("uzb_cyrl") -> "Мундарижа"
        language.startsWith("uzb") -> "Mundarija"
        language.startsWith("rus") -> "Оглавление"
        else -> "Contents"
    }
}
