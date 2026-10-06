package uz.kitobskaner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.kitobskaner.image.ImageUtils
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

enum class CompressLevel(val maxSide: Int, val quality: Float) {
    LOW(2400, 0.82f),
    MEDIUM(1800, 0.66f),
    HIGH(1300, 0.5f),
}

class CompressResult(val file: File, val before: Long, val after: Long) {
    val saved: Boolean get() = after < before
    val percent: Int get() = if (before <= 0) 0 else ((before - after) * 100 / before).toInt().coerceAtLeast(0)
}

/** Qurilmadagi har qanday PDF bilan ishlash: siqish, rasmga aylantirish, matnini olish. */
object PdfTools {

    fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
        } catch (_: Exception) {
        }
        return (name ?: uri.lastPathSegment ?: "document").substringBeforeLast('.')
    }

    fun size(context: Context, uri: Uri): Long = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    fun pageCount(file: File): Int = try {
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }
    } catch (e: Exception) {
        0
    }

    /** PDF birinchi sahifasining kichik rasmi. */
    fun thumbnail(file: File, width: Int): Bitmap? = try {
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) null else r.openPage(0).use { p ->
                    val h = (width.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * PDF ichidagi rasmlarni qayta siqadi (matn va vektor grafika saqlanib qoladi).
     */
    suspend fun compress(
        context: Context,
        uri: Uri,
        level: CompressLevel,
        out: File,
        onProgress: (Float) -> Unit,
    ): CompressResult = withContext(Dispatchers.Default) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val before = size(context, uri)
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("open")
        val doc = input.use { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)) }
        try {
            val done = HashMap<COSBase, PDImageXObject>()
            val n = doc.numberOfPages
            doc.pages.forEachIndexed { i, page ->
                processResources(doc, page.resources, level, done, HashSet())
                onProgress((i + 1f) / max(1, n))
            }
            out.parentFile?.mkdirs()
            out.outputStream().buffered().use { doc.save(it) }
        } finally {
            doc.close()
        }
        val after = out.length()
        if (before in 1 until after) {
            // siqib bo'lmadi — asl faylni saqlaymiz
            context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
        }
        CompressResult(out, before, min(after, if (before > 0) before else after))
    }

    private fun processResources(
        doc: PDDocument,
        res: PDResources?,
        level: CompressLevel,
        done: HashMap<COSBase, PDImageXObject>,
        visited: HashSet<COSBase>,
    ) {
        res ?: return
        for (name in res.xObjectNames.toList()) {
            val xo = try { res.getXObject(name) } catch (e: Exception) { null } ?: continue
            when (xo) {
                is PDImageXObject -> {
                    val key = xo.cosObject
                    val cached = done[key]
                    if (cached != null) {
                        res.put(name, cached)
                        continue
                    }
                    val replaced = recompress(doc, xo, level) ?: continue
                    done[key] = replaced
                    res.put(name, replaced)
                }
                is PDFormXObject -> {
                    if (visited.add(xo.cosObject)) processResources(doc, xo.resources, level, done, visited)
                }
            }
        }
    }

    private fun recompress(doc: PDDocument, img: PDImageXObject, level: CompressLevel): PDImageXObject? {
        return try {
            if (img.isStencil || img.softMask != null) return null
            if (img.width.toLong() * img.height < 160_000) return null
            val src = img.image ?: return null
            val scaled = ImageUtils.scaleDown(src, level.maxSide)
            val jpeg = JPEGFactory.createFromImage(doc, scaled, level.quality)
            scaled.recycle()
            val oldLen = img.cosObject.length
            if (jpeg.cosObject.length < oldLen * 0.95) jpeg else null
        } catch (e: Throwable) {
            null
        }
    }

    /** PDF'da haqiqiy matn qatlami bormi (skan emas). */
    suspend fun hasText(context: Context, uri: Uri): Boolean = withContext(Dispatchers.Default) {
        try {
            val t = extractText(context, uri, maxPages = 3)
            t.sumOf { p -> p.count { it.isLetter() } } > 80
        } catch (e: Exception) {
            false
        }
    }

    /** Har bir sahifa matni. */
    suspend fun extractText(context: Context, uri: Uri, maxPages: Int = Int.MAX_VALUE): List<String> =
        withContext(Dispatchers.Default) {
            PDFBoxResourceLoader.init(context.applicationContext)
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
            val doc = input.use { PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)) }
            try {
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val n = min(doc.numberOfPages, maxPages)
                (1..n).map { p ->
                    stripper.startPage = p
                    stripper.endPage = p
                    stripper.getText(doc)
                }
            } finally {
                doc.close()
            }
        }

    /** PDF sahifalarini JPG rasmlarga aylantiradi. */
    suspend fun toImages(
        context: Context,
        uri: Uri,
        dpi: Int,
        quality: Int,
        outDir: File,
        baseName: String,
        onProgress: (Float) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        outDir.mkdirs()
        val files = ArrayList<File>()
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { r ->
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { p ->
                        val k = min(dpi / 72f, 4200f / max(p.width, p.height))
                        val bmp = Bitmap.createBitmap((p.width * k).toInt().coerceAtLeast(1), (p.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val f = File(outDir, "${baseName}_${(i + 1).toString().padStart(3, '0')}.jpg")
                        ImageUtils.saveJpeg(bmp, f, quality)
                        bmp.recycle()
                        files += f
                    }
                    onProgress((i + 1f) / r.pageCount)
                }
            }
        }
        files
    }

    fun zip(files: List<File>, out: File) {
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            for (f in files) {
                z.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }
}
