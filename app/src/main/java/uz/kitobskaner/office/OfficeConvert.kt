package uz.kitobskaner.office

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PdfPrint
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uz.kitobskaner.data.Align
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.data.TextSpan
import uz.kitobskaner.pdf.DocxWriter
import uz.kitobskaner.pdf.XlsxWriter
import java.io.File
import kotlin.coroutines.resume

/**
 * Hujjat konvertatsiyalari (faqat haqiqatan bajariladiganlari):
 * - har qanday ochiladigan hujjat → PDF (WebView chop etish dvigateli, matn vektor bo'lib qoladi);
 * - DOC, RTF, ODT, TXT, HTML, EPUB → DOCX (matn, sarlavhalar, qalin/kursiv, tekislash);
 * - CSV, XLS, ODS → XLSX (qiymatlar).
 */
object OfficeConvert {

    fun pdfSources(f: Fmt) = f in setOf(Fmt.DOCX, Fmt.XLSX, Fmt.PPTX, Fmt.DOC, Fmt.XLS, Fmt.PPT, Fmt.ODT, Fmt.ODS, Fmt.ODP, Fmt.RTF, Fmt.CSV, Fmt.TXT, Fmt.HTML, Fmt.EPUB)
    fun docxSources(f: Fmt) = f in setOf(Fmt.DOC, Fmt.RTF, Fmt.ODT, Fmt.TXT, Fmt.HTML, Fmt.EPUB)
    fun xlsxSources(f: Fmt) = f in setOf(Fmt.CSV, Fmt.XLS, Fmt.ODS)

    // ---------------------------------------------------------------- → PDF

    private const val PRINT_CSS = """<style>@media print{
html,body{background:#fff!important}
#docwrap{height:auto!important;overflow:visible!important}
#doc{transform:none!important;width:auto!important;margin:0!important;padding:0!important}
.page{width:auto!important;min-height:0!important;padding:0!important;margin:0!important;box-shadow:none!important}
.pgno,.tabs,#fbar,.bar,.qx,.no,#outline{display:none!important}
[data-brk=after]{break-after:page}[data-brk=before]{break-before:page}
.pb{display:block;break-after:page;border:none!important}
.sheet{display:block!important;break-after:page;box-shadow:none!important;overflow:visible!important}
table.x th{position:static!important}
.cards{display:none!important}
}
@page{margin:16mm 14mm}</style>"""

    /** Hujjatni PDF faylga aylantiradi. [html] — HtmlRenderer natijasi. Asosiy oqimda chaqirilmasin. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun htmlToPdf(context: Context, html: String, landscape: Boolean, out: File): Boolean {
        val dir = File(context.cacheDir, "convert").apply { mkdirs() }
        val page = File(dir, "doc_${System.currentTimeMillis()}.html")
        // chop etish uslubi <head> oxiriga qo'shiladi
        val withCss = if (html.contains("</head>")) html.replaceFirst("</head>", "$PRINT_CSS</head>") else PRINT_CSS + html
        withContext(Dispatchers.IO) { page.writeText(withCss) }
        out.parentFile?.mkdirs()
        val ok = withTimeout(120_000) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val wv = WebView(context)
                    wv.settings.javaScriptEnabled = true
                    wv.settings.allowFileAccess = true
                    wv.settings.blockNetworkLoads = true
                    var started = false
                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?) = true
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (started) return
                            started = true
                            // sahifa skriptlari (o'lchash) tugashi uchun qisqa kutish; so'ng chop etishga tayyorlash
                            wv.evaluateJavascript("try{if(window.QO)QO.view('mobile');if(window.pre)pre();}catch(e){}", null)
                            Handler(Looper.getMainLooper()).postDelayed({
                                val media = if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4
                                val attrs = PrintAttributes.Builder()
                                    .setMediaSize(media)
                                    .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                    .build()
                                try {
                                    PdfPrint(attrs).print(wv.createPrintDocumentAdapter("QulayOfis"), out) { res ->
                                        wv.destroy()
                                        if (cont.isActive) cont.resume(res)
                                    }
                                } catch (e: Throwable) {
                                    // ba'zi qurilmalarda yashirin API cheklovi — chaqiruvchi tizim oynasiga o'tadi
                                    try { wv.destroy() } catch (_: Exception) {}
                                    if (cont.isActive) cont.resume(false)
                                }
                            }, 700)
                        }
                    }
                    wv.loadUrl("file://" + page.absolutePath)
                    cont.invokeOnCancellation { try { wv.destroy() } catch (_: Exception) {} }
                }
            }
        }
        page.delete()
        return ok && out.length() > 0
    }

    suspend fun toPdf(context: Context, bytes: ByteArray, fmt: Fmt, title: String, out: File): Boolean {
        val html = withContext(Dispatchers.Default) {
            if (fmt == Fmt.TXT) textPage(HtmlRenderer.decodeText(bytes), title)
            else HtmlRenderer(::plainImage).render(fmt, bytes, title)
        } ?: return false
        val landscape = fmt == Fmt.PPTX || fmt == Fmt.PPT || fmt == Fmt.ODP
        return htmlToPdf(context, html, landscape, out)
    }

    private fun plainImage(b: ByteArray, name: String): String? {
        val lower = name.lowercase()
        if (lower.endsWith(".emf") || lower.endsWith(".wmf") || lower.endsWith(".tif") || lower.endsWith(".tiff")) return null
        val mime = when {
            b.size > 3 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() -> "image/png"
            b.size > 2 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "image/jpeg"
            b.size > 3 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() -> "image/gif"
            else -> return null
        }
        return "data:$mime;base64," + android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
    }

    private fun textPage(text: String, title: String): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>${HtmlRenderer.escStatic(title)}</title>" +
            "<style>body{font:11pt/1.45 'Noto Sans',sans-serif;white-space:pre-wrap;overflow-wrap:break-word;margin:0}</style></head><body>" +
            HtmlRenderer.escStatic(text) + "</body></html>"

    // ---------------------------------------------------------------- → DOCX

    suspend fun toDocx(context: Context, bytes: ByteArray, fmt: Fmt, title: String, out: File): Boolean = withContext(Dispatchers.Default) {
        val blocks = if (fmt == Fmt.TXT) {
            HtmlRenderer.decodeText(bytes).split('\n').map { TextBlock(BlockType.PARAGRAPH, listOf(TextSpan(it.trimEnd('\r'))), Align.LEFT) }
        } else {
            val html = HtmlRenderer { _, _ -> null }.render(fmt, bytes, title) ?: return@withContext false
            htmlToBlocks(html)
        }
        if (blocks.isEmpty()) return@withContext false
        out.parentFile?.mkdirs()
        out.outputStream().buffered().use { DocxWriter.write(title, blocks, File(context.cacheDir, "none"), it, withTitle = false) }
        true
    }

    private val blockRe = Regex("(?is)<(h[1-6]|p)\\b([^>]*)>(.*?)</\\1>")
    private val cellRe = Regex("(?is)<(td|th|li)\\b([^>]*)>((?:(?!</?(?:p|td|th|li)\\b).)*?)</\\1>")

    /** HtmlRenderer bergan HTML'dan bloklar (matn, sarlavha, qalin/kursiv, tekislash). */
    fun htmlToBlocks(html: String): List<TextBlock> {
        val body = Regex("(?is)<body[^>]*>(.*)</body>").find(html)?.groupValues?.get(1) ?: html
        val clean = body.replace(Regex("(?is)<script\\b.*?</script>"), "").replace(Regex("(?is)<style\\b.*?</style>"), "")
            // ichida paragraf bo'lmagan katak/ro'yxat bandi — o'zi paragraf
            .let { h -> cellRe.replace(h) { m -> "<p" + m.groupValues[2] + ">" + m.groupValues[3] + "</p>" } }
        val out = ArrayList<TextBlock>()
        for (m in blockRe.findAll(clean)) {
            val tag = m.groupValues[1].lowercase()
            val attrs = m.groupValues[2]
            val inner = m.groupValues[3]
            val spans = inlineSpans(inner)
            if (spans.isEmpty() || spans.all { it.text.isBlank() }) { if (tag == "p") out += TextBlock(BlockType.PARAGRAPH, emptyList(), Align.LEFT); continue }
            val type = when (tag) { "h1" -> BlockType.HEADING1; "h2", "h3" -> BlockType.HEADING2; else -> BlockType.PARAGRAPH }
            val style = Regex("(?i)style\\s*=\\s*\"([^\"]*)\"").find(attrs)?.groupValues?.get(1) ?: ""
            val align = when {
                style.contains("text-align:center") -> Align.CENTER
                style.contains("text-align:right") -> Align.RIGHT
                style.contains("text-align:justify") -> Align.JUSTIFY
                else -> Align.LEFT
            }
            val boldBlock = style.contains("font-weight:bold") || style.contains("font-weight:700")
            out += TextBlock(type, if (boldBlock) spans.map { it.copy(bold = true) } else spans, align)
        }
        return out
    }

    private fun inlineSpans(s: String): List<TextSpan> {
        val out = ArrayList<TextSpan>()
        val stack = ArrayList<Pair<Boolean, Boolean>>() // (qalin, kursiv) har ochilgan teg uchun
        var bold = false
        var ital = false
        val tok = Regex("(?is)<(/?)([a-z0-9]+)([^>]*)>|([^<]+)")
        for (m in tok.findAll(s)) {
            val text = m.groupValues[4]
            if (text.isNotEmpty()) { out += TextSpan(decode(text), bold, ital); continue }
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2].lowercase()
            val attrs = m.groupValues[3]
            when {
                tag == "br" -> out += TextSpan("\n", bold, ital)
                tag in setOf("img", "hr", "input", "meta", "col") -> {}
                closing -> { stack.removeLastOrNull()?.let { (b, i) -> bold = b; ital = i } }
                else -> {
                    stack += bold to ital
                    if (tag == "b" || tag == "strong" || Regex("font-weight:\\s*(bold|[6-9]00)").containsMatchIn(attrs)) bold = true
                    if (tag == "i" || tag == "em" || attrs.contains("font-style:italic")) ital = true
                    if (attrs.contains("font-weight:normal")) bold = false
                    if (attrs.contains("contenteditable=\"false\"") && attrs.contains("bul")) {} // ro'yxat belgisi matn sifatida qoladi
                }
            }
        }
        // qo'shni bir xil uslubli bo'laklarni birlashtirish
        val merged = ArrayList<TextSpan>()
        for (sp in out) {
            val last = merged.lastOrNull()
            if (last != null && last.bold == sp.bold && last.italic == sp.italic) merged[merged.size - 1] = last.copy(text = last.text + sp.text) else merged += sp
        }
        return merged
    }

    private fun decode(s: String): String = s
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: "" }
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

    // ---------------------------------------------------------------- → XLSX

    suspend fun toXlsx(bytes: ByteArray, fmt: Fmt, out: File): Boolean = withContext(Dispatchers.Default) {
        val sheets: List<Pair<String, List<List<String>>>> = when (fmt) {
            Fmt.CSV -> listOf("Sheet1" to parseCsv(HtmlRenderer.decodeText(bytes)))
            Fmt.XLS -> LegacyReaders.xlsSheets(Cfb(bytes)).map { s -> s.name to grid(s.cells) }
            Fmt.ODS -> {
                // ODS: HtmlRenderer jadvalidan qiymatlarni olamiz
                val html = HtmlRenderer { _, _ -> null }.ods(bytes, "ods")
                Regex("(?is)<div class=\"sheet\"[^>]*data-name=\"([^\"]*)\"[^>]*>(.*?)</table>").findAll(html).map { m ->
                    val rows = Regex("(?is)<tr>(.*?)</tr>").findAll(m.groupValues[2]).drop(1).map { r ->
                        Regex("(?is)<td[^>]*>(.*?)</td>").findAll(r.groupValues[1]).map { decode(it.groupValues[1].replace(Regex("<[^>]+>"), "")) }.toList()
                    }.toList()
                    decode(m.groupValues[1]) to rows
                }.toList()
            }
            else -> return@withContext false
        }
        if (sheets.isEmpty()) return@withContext false
        out.parentFile?.mkdirs()
        out.outputStream().buffered().use { XlsxWriter.writeNamed(sheets, it) }
        true
    }

    private fun grid(cells: Map<Long, String>): List<List<String>> {
        if (cells.isEmpty()) return emptyList()
        val maxR = cells.keys.maxOf { (it / 1000).toInt() }
        val maxC = cells.keys.maxOf { (it % 1000).toInt() }
        return (0..maxR).map { r -> (0..maxC).map { c -> cells[r.toLong() * 1000 + c] ?: "" } }
    }

    fun parseCsv(text: String): List<List<String>> {
        val sep = listOf(',', ';', '\t').maxByOrNull { c -> text.lineSequence().take(5).sumOf { l -> l.count { it == c } } } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var q = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                q && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> q = !q
                !q && ch == sep -> { row += cell.toString(); cell.setLength(0) }
                !q && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0); rows += row; row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }
}
