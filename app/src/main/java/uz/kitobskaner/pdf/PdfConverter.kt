package uz.kitobskaner.pdf

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.kitobskaner.data.Align
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.TextBlock
import uz.kitobskaner.data.TextSpan
import java.io.File

enum class ConvertTarget(val ext: String) { DOCX("docx"), JPG("zip"), XLSX("xlsx"), TXT("txt") }

/** Matnli (skan bo'lmagan) PDF'ni boshqa formatlarga o'tkazish. */
class PdfConverter(private val context: Context, private val exporter: Exporter) {

    /**
     * @return yaratilgan fayllar. Matnli formatlar uchun PDF matn qatlamiga ega bo'lishi kerak
     * (aks holda chaqiruvchi OCR yo'lini tanlaydi).
     */
    suspend fun convert(
        uri: Uri,
        name: String,
        targets: Set<ConvertTarget>,
        dpi: Int,
        onProgress: (Float) -> Unit,
    ): List<File> = withContext(Dispatchers.Default) {
        val out = ArrayList<File>()
        val base = exporter.safeName(name)
        val textTargets = targets - ConvertTarget.JPG
        val pagesText = if (textTargets.isNotEmpty()) PdfTools.extractText(context, uri) else emptyList()
        var step = 0
        val steps = targets.size.coerceAtLeast(1)
        for (t in ConvertTarget.entries.filter { it in targets }) {
            val f = exporter.uniqueFile(base, t.ext)
            when (t) {
                ConvertTarget.JPG -> {
                    val tmpDir = File(context.cacheDir, "convert_jpg").apply { deleteRecursively(); mkdirs() }
                    val images = PdfTools.toImages(context, uri, dpi, 88, tmpDir, base) { p -> onProgress((step + p) / steps) }
                    PdfTools.zip(images, f)
                    if (Build.VERSION.SDK_INT >= 29) images.forEach { img -> exporter.saveBytesToGallery(img.readBytes(), img.name) }
                    tmpDir.deleteRecursively()
                }
                ConvertTarget.TXT -> f.writeText(pagesText.joinToString("\n\n") { it.trim() })
                ConvertTarget.DOCX -> f.outputStream().buffered().use { DocxWriter.write(name, blocksOf(pagesText), File(context.cacheDir, "none"), it) }
                ConvertTarget.XLSX -> f.outputStream().buffered().use { o ->
                    XlsxWriter.writeRows(pagesText.map { page ->
                        page.lines().filter { it.isNotBlank() }.map { line -> line.trim().split(Regex("\\s{2,}|\\t")) }
                    }, o)
                }
            }
            out += f
            step++
            onProgress(step.toFloat() / steps)
        }
        exporter.refreshFiles()
        out
    }

    /** Sahifa matnini xatboshilarga ajratadi (satr oxiridagi bo'g'in ko'chirishlarni ham ulaydi). */
    private fun blocksOf(pages: List<String>): List<TextBlock> {
        val blocks = ArrayList<TextBlock>()
        for (page in pages) {
            val sb = StringBuilder()
            fun flush() {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) {
                    val short = t.length < 70 && !t.endsWith('.') && t.count { it == ' ' } < 10
                    blocks += TextBlock(
                        if (short && t.any { it.isLetter() } && t.filter { it.isLetter() }.all { it.isUpperCase() }) BlockType.HEADING2 else BlockType.PARAGRAPH,
                        listOf(TextSpan(t)),
                        if (short) Align.LEFT else Align.JUSTIFY,
                    )
                }
                sb.setLength(0)
            }
            for (raw in page.lines()) {
                val line = raw.trim()
                if (line.isEmpty()) { flush(); continue }
                if (sb.isNotEmpty()) {
                    val last = sb.last()
                    if (last == '-' && line.first().isLowerCase()) sb.setLength(sb.length - 1) else sb.append(' ')
                }
                sb.append(line)
                if (line.length < 45 && line.last() in ".!?:") flush()
            }
            flush()
        }
        return blocks
    }
}
