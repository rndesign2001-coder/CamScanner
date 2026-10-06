package uz.kitobskaner.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uz.kitobskaner.data.AppSettings
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.OcrPage
import uz.kitobskaner.data.Project
import uz.kitobskaner.data.ProjectRepository
import uz.kitobskaner.image.ImageProcessing
import uz.kitobskaner.image.ImageUtils
import java.io.File

enum class ExportFormat(val ext: String, val mime: String, val needsOcr: Boolean) {
    BOOK_PDF("pdf", "application/pdf", true),
    SEARCHABLE_PDF("pdf", "application/pdf", true),
    IMAGE_PDF("pdf", "application/pdf", false),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", true),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true),
    JPG("zip", "application/zip", false),
    TXT("txt", "text/plain", true),
}

class ExportResult(val file: File, val pages: Int, val mime: String)

fun mimeOf(file: File): String = when (file.extension.lowercase()) {
    "pdf" -> "application/pdf"
    "docx" -> ExportFormat.DOCX.mime
    "xlsx" -> ExportFormat.XLSX.mime
    "zip" -> "application/zip"
    "jpg", "jpeg" -> "image/jpeg"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}

class Exporter(private val context: Context, private val repo: ProjectRepository) {

    /** Ilova ichidagi "Mening hujjatlarim" papkasi. */
    val exportsDir: File get() = File(context.filesDir, "exports").apply { mkdirs() }

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    fun refreshFiles() {
        _files.value = exportsDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun delete(file: File) {
        file.delete()
        refreshFiles()
    }

    fun rename(file: File, newName: String): File {
        val target = uniqueFile(safeName(newName), file.extension)
        file.renameTo(target)
        refreshFiles()
        return target
    }

    fun uniqueFile(base: String, ext: String): File {
        var f = File(exportsDir, "$base.$ext")
        var i = 2
        while (f.exists()) { f = File(exportsDir, "$base ($i).$ext"); i++ }
        return f
    }

    suspend fun loadOcr(project: Project): List<OcrPage> =
        project.pages.mapNotNull { repo.loadOcr(project.id, it.id) }

    suspend fun plainText(project: Project): String = BookAssembler.assemble(loadOcr(project))
        .filter { it.type != BlockType.IMAGE }
        .joinToString("\n\n") { it.plainText.trim() }

    suspend fun export(
        project: Project,
        format: ExportFormat,
        settings: AppSettings,
        onProgress: (Float) -> Unit,
    ): ExportResult = withContext(Dispatchers.Default) {
        val file = uniqueFile(safeName(project.title), format.ext)
        val tmp = File(file.parentFile, file.name + ".tmp")
        val language = project.effectiveLanguage ?: "uzb+eng"
        val imagesDir = repo.imagesDir(project.id)
        val pages = try {
            tmp.outputStream().buffered().use { out ->
                when (format) {
                    ExportFormat.BOOK_PDF -> {
                        val blocks = BookAssembler.assemble(loadOcr(project))
                        BookPdfWriter(context, settings).write(project.title, language, blocks, imagesDir, out, onProgress)
                    }
                    ExportFormat.SEARCHABLE_PDF, ExportFormat.IMAGE_PDF -> {
                        val searchable = format == ExportFormat.SEARCHABLE_PDF
                        val items = project.pages.map { p ->
                            ImagePdfWriter.Item(
                                repo.pageFile(project.id, p.id),
                                if (searchable) repo.loadOcr(project.id, p.id) else null
                            )
                        }.filter { it.image.exists() }
                        ImagePdfWriter(context, settings).write(project.title, items, searchable, out, onProgress)
                    }
                    ExportFormat.DOCX -> {
                        val blocks = BookAssembler.assemble(loadOcr(project))
                        DocxWriter.write(project.title, blocks, imagesDir, out)
                        onProgress(1f); 0
                    }
                    ExportFormat.XLSX -> {
                        XlsxWriter.write(loadOcr(project), out)
                        onProgress(1f); 0
                    }
                    ExportFormat.JPG -> {
                        writeJpgZip(project, settings, out, onProgress)
                    }
                    ExportFormat.TXT -> {
                        out.write(plainText(project).toByteArray(Charsets.UTF_8))
                        onProgress(1f); 0
                    }
                }
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
        tmp.renameTo(file)
        refreshFiles()
        ExportResult(file, pages, format.mime)
    }

    private fun writeJpgZip(project: Project, settings: AppSettings, out: java.io.OutputStream, onProgress: (Float) -> Unit): Int {
        val zip = java.util.zip.ZipOutputStream(out)
        val base = safeName(project.title)
        project.pages.forEachIndexed { i, p ->
            val src = ImageUtils.decodeFile(repo.pageFile(project.id, p.id), settings.imageQuality.maxSide) ?: return@forEachIndexed
            val filtered = ImageProcessing.applyFilter(src, settings.imageFilter)
            zip.putNextEntry(java.util.zip.ZipEntry("${base}_${(i + 1).toString().padStart(3, '0')}.jpg"))
            zip.write(ImageUtils.jpegBytes(filtered, settings.imageQuality.jpeg))
            zip.closeEntry()
            if (filtered !== src) filtered.recycle()
            src.recycle()
            onProgress((i + 1f) / project.pages.size)
        }
        zip.finish()
        zip.flush()
        return project.pages.size
    }

    /** Sahifa rasmlarini galereyaga (Pictures/KitobSkaner) saqlaydi. @return saqlanganlar soni */
    suspend fun saveImagesToGallery(project: Project, settings: AppSettings, onProgress: (Float) -> Unit): Int =
        withContext(Dispatchers.IO) {
            var n = 0
            project.pages.forEachIndexed { i, p ->
                val src = ImageUtils.decodeFile(repo.pageFile(project.id, p.id), settings.imageQuality.maxSide) ?: return@forEachIndexed
                val filtered = ImageProcessing.applyFilter(src, settings.imageFilter)
                val bytes = ImageUtils.jpegBytes(filtered, settings.imageQuality.jpeg)
                if (filtered !== src) filtered.recycle()
                src.recycle()
                if (saveBytesToGallery(bytes, "${safeName(project.title)}_${i + 1}.jpg")) n++
                onProgress((i + 1f) / project.pages.size)
            }
            n
        }

    fun saveBytesToGallery(bytes: ByteArray, name: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KitobSkaner")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri != null && context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "KitobSkaner")
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
            true
        }
    } catch (e: Exception) {
        false
    }

    /**
     * Faylni "Yuklanmalar/KitobSkaner" papkasiga saqlaydi (Android 10+).
     * @return saqlangan joy URI yoki null (eski Android — tizim oynasi orqali saqlash kerak)
     */
    fun saveToDownloads(file: File, mime: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KitobSkaner")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            uri
        } catch (e: Exception) {
            null
        }
    }

    fun copyTo(file: File, uri: Uri): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
    } catch (e: Exception) {
        false
    }

    fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().take(80).ifEmpty { "kitob" }
}
