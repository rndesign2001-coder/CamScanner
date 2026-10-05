package uz.kitobskaner.pdf

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.kitobskaner.data.AppSettings
import uz.kitobskaner.data.BlockType
import uz.kitobskaner.data.OcrPage
import uz.kitobskaner.data.Project
import uz.kitobskaner.data.ProjectRepository
import java.io.File

enum class ExportFormat(val ext: String, val mime: String, val needsOcr: Boolean) {
    BOOK_PDF("pdf", "application/pdf", true),
    SEARCHABLE_PDF("pdf", "application/pdf", true),
    IMAGE_PDF("pdf", "application/pdf", false),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", true),
    TXT("txt", "text/plain", true),
}

class ExportResult(val file: File, val pages: Int)

class Exporter(private val context: Context, private val repo: ProjectRepository) {

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
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000) it.delete() }
        val file = File(dir, safeName(project.title) + "." + format.ext)
        val language = project.effectiveLanguage ?: "uzb+eng"
        val imagesDir = repo.imagesDir(project.id)
        val pages = file.outputStream().buffered().use { out ->
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
                    onProgress(1f)
                    0
                }
                ExportFormat.TXT -> {
                    out.write(plainText(project).toByteArray(Charsets.UTF_8))
                    onProgress(1f)
                    0
                }
            }
        }
        ExportResult(file, pages)
    }

    private fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().take(80).ifEmpty { "kitob" }
}
