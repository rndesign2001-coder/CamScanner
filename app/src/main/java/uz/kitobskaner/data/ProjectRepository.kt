package uz.kitobskaner.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import uz.kitobskaner.image.ImageUtils
import uz.kitobskaner.image.SpreadSplitter
import kotlin.math.max
import kotlin.math.min
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProjectRepository(private val context: Context, private val settings: SettingsStore) {

    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    /** OCR natijasi o'zgarganda oshadi (UI matnni qayta o'qishi uchun). */
    private val _ocrRevision = MutableStateFlow(0L)
    val ocrRevision: StateFlow<Long> = _ocrRevision.asStateFlow()

    private var loaded = false

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withLock
            val list = root.listFiles()?.mapNotNull { dir ->
                val f = File(dir, "project.json")
                if (!f.exists()) null else try {
                    json.decodeFromString(Project.serializer(), f.readText())
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            _projects.value = list.sortedByDescending { it.updatedAt }
            loaded = true
        }
    }

    fun projectFlow(id: String): Flow<Project?> = projects.map { list -> list.find { it.id == id } }
    fun get(id: String): Project? = _projects.value.find { it.id == id }

    fun projectDir(id: String) = File(root, id)
    fun pageFile(projectId: String, pageId: String) = File(projectDir(projectId), "pages/$pageId.jpg")
    fun thumbFile(projectId: String, pageId: String) = File(projectDir(projectId), "thumbs/$pageId.jpg")
    fun ocrFile(projectId: String, pageId: String) = File(projectDir(projectId), "ocr/$pageId.json")
    fun imagesDir(projectId: String) = File(projectDir(projectId), "images")

    private fun save(p: Project) {
        val dir = projectDir(p.id).apply { mkdirs() }
        val tmp = File(dir, "project.json.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), p))
        val target = File(dir, "project.json")
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    private suspend fun mutate(id: String, block: (Project) -> Project): Project? = mutex.withLock {
        val cur = _projects.value.find { it.id == id } ?: return@withLock null
        val next = block(cur).copy(updatedAt = System.currentTimeMillis())
        withContext(Dispatchers.IO) { save(next) }
        _projects.value = (_projects.value.filter { it.id != id } + next).sortedByDescending { it.updatedAt }
        next
    }

    /**
     * Rasmlardan yangi loyiha.
     * @param splitSpreads har bir surat kitob yoyilmasi — ikki sahifaga ajratiladi
     * @return yangi loyiha yoki null — birorta ham rasm o'qilmasa
     */
    suspend fun createProject(
        uris: List<Uri>,
        splitSpreads: Boolean = false,
        titlePrefix: String = "Kitob",
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Project? = createFrom(titlePrefix) { id -> importPages(id, uris, splitSpreads, onProgress) }

    /** PDF fayldan loyiha (har bir sahifa rasm sifatida — keyin OCR qilinadi). */
    suspend fun createFromPdf(uri: Uri, title: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): Project? =
        createFrom(title, exactTitle = true) { id -> importPdfPages(id, uri, onProgress) }

    private suspend fun createFrom(title: String, exactTitle: Boolean = false, pagesOf: suspend (String) -> List<PageInfo>): Project? {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val name = if (exactTitle) title else title + " " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(now))
        val p = Project(
            id = UUID.randomUUID().toString().take(12),
            title = name,
            createdAt = now,
            updatedAt = now,
            language = settings.value.defaultLanguage,
        )
        val pages = pagesOf(p.id)
        if (pages.isEmpty()) {
            withContext(Dispatchers.IO) { projectDir(p.id).deleteRecursively() }
            return null
        }
        val full = p.copy(pages = pages)
        mutex.withLock {
            withContext(Dispatchers.IO) { save(full) }
            _projects.value = (listOf(full) + _projects.value).sortedByDescending { it.updatedAt }
        }
        return full
    }

    /** @return qo'shilgan sahifalar soni */
    suspend fun addPages(
        id: String,
        uris: List<Uri>,
        splitSpreads: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Int {
        val pages = importPages(id, uris, splitSpreads, onProgress)
        if (pages.isNotEmpty()) mutate(id) { it.copy(pages = it.pages + pages) }
        return pages.size
    }

    private fun savePage(projectId: String, bmp: Bitmap): PageInfo {
        val pageId = UUID.randomUUID().toString().take(12)
        ImageUtils.saveJpeg(bmp, pageFile(projectId, pageId), 93)
        val thumb = ImageUtils.scaleDown(bmp.copy(Bitmap.Config.ARGB_8888, false), THUMB_SIDE)
        ImageUtils.saveJpeg(thumb, thumbFile(projectId, pageId), 85)
        thumb.recycle()
        return PageInfo(pageId)
    }

    /** Bitmapni saqlaydi; kerak bo'lsa yoyilmani ikki sahifaga ajratadi. */
    private fun saveMaybeSplit(projectId: String, bmp: Bitmap, split: Boolean): List<PageInfo> {
        if (!split) return listOf(savePage(projectId, bmp))
        val (l, r) = SpreadSplitter.split(bmp)
        val out = listOf(savePage(projectId, l), savePage(projectId, r))
        l.recycle(); r.recycle()
        return out
    }

    private suspend fun importPages(
        projectId: String,
        uris: List<Uri>,
        split: Boolean,
        onProgress: (Int, Int) -> Unit,
    ): List<PageInfo> = withContext(Dispatchers.IO) {
        val result = ArrayList<PageInfo>()
        uris.forEachIndexed { i, uri ->
            onProgress(i, uris.size)
            try {
                val bmp = ImageUtils.decodeUri(context, uri, if (split) MAX_SPREAD_SIDE else MAX_PAGE_SIDE)
                    ?: return@forEachIndexed
                result += saveMaybeSplit(projectId, bmp, split)
                if (!bmp.isRecycled) bmp.recycle()
            } catch (e: Throwable) {
                // o'qib bo'lmaydigan rasm o'tkazib yuboriladi
            }
        }
        onProgress(uris.size, uris.size)
        result
    }

    private suspend fun importPdfPages(projectId: String, uri: Uri, onProgress: (Int, Int) -> Unit): List<PageInfo> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<PageInfo>()
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        val n = renderer.pageCount
                        for (i in 0 until n) {
                            onProgress(i, n)
                            renderer.openPage(i).use { page ->
                                // ~300 dpi, lekin uzun tomoni 3400 px dan oshmaydi
                                val k = min(300f / 72f, MAX_PAGE_SIDE.toFloat() / max(page.width, page.height))
                                val bmp = Bitmap.createBitmap(
                                    (page.width * k).toInt().coerceAtLeast(1),
                                    (page.height * k).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                result += savePage(projectId, bmp)
                                bmp.recycle()
                            }
                        }
                        onProgress(n, n)
                    }
                }
            } catch (e: Throwable) {
                // shifrlangan yoki buzilgan PDF
            }
            result
        }

    /** Mavjud sahifani (yoyilmani) ikkiga bo'ladi. */
    suspend fun splitPage(id: String, pageId: String): Boolean {
        val newPages = withContext(Dispatchers.IO) {
            val bmp = ImageUtils.decodeFile(pageFile(id, pageId), MAX_SPREAD_SIDE) ?: return@withContext null
            val pages = saveMaybeSplit(id, bmp, true)
            bmp.recycle()
            pages
        } ?: return false
        mutate(id) { p ->
            val list = p.pages.toMutableList()
            val i = list.indexOfFirst { it.id == pageId }
            if (i < 0) return@mutate p
            list.removeAt(i)
            list.addAll(i, newPages)
            p.copy(pages = list)
        }
        withContext(Dispatchers.IO) {
            pageFile(id, pageId).delete(); thumbFile(id, pageId).delete(); ocrFile(id, pageId).delete()
        }
        _ocrRevision.value++
        return true
    }

    suspend fun rename(id: String, title: String) {
        mutate(id) { it.copy(title = title.trim().ifEmpty { it.title }) }
    }

    suspend fun setLanguage(id: String, language: String) {
        mutate(id) { it.copy(language = language) }
    }

    suspend fun setDetectedLanguage(id: String, language: String) {
        mutate(id) { it.copy(detectedLanguage = language) }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) { projectDir(id).deleteRecursively() }
            _projects.value = _projects.value.filter { it.id != id }
        }
    }

    suspend fun deletePages(id: String, pageIds: Set<String>) {
        mutate(id) { p -> p.copy(pages = p.pages.filter { it.id !in pageIds }) }
        withContext(Dispatchers.IO) {
            pageIds.forEach { pid ->
                pageFile(id, pid).delete(); thumbFile(id, pid).delete(); ocrFile(id, pid).delete()
                imagesDir(id).listFiles()?.filter { it.name.startsWith("${pid}_") }?.forEach { it.delete() }
            }
        }
        _ocrRevision.value++
    }

    suspend fun movePage(id: String, pageId: String, delta: Int) {
        mutate(id) { p ->
            val list = p.pages.toMutableList()
            val i = list.indexOfFirst { it.id == pageId }
            val j = i + delta
            if (i < 0 || j < 0 || j >= list.size) return@mutate p
            val item = list.removeAt(i)
            list.add(j, item)
            p.copy(pages = list)
        }
    }

    suspend fun rotatePage(id: String, pageId: String, degrees: Float) {
        withContext(Dispatchers.IO) {
            val f = pageFile(id, pageId)
            val bmp = ImageUtils.decodeFile(f, MAX_PAGE_SIDE) ?: return@withContext
            val rotated = ImageUtils.rotate(bmp, degrees)
            ImageUtils.saveJpeg(rotated, f, 93)
            val thumb = ImageUtils.scaleDown(rotated, THUMB_SIDE)
            ImageUtils.saveJpeg(thumb, thumbFile(id, pageId), 85)
            thumb.recycle()
            if (!rotated.isRecycled) rotated.recycle()
            ocrFile(id, pageId).delete()
            imagesDir(id).listFiles()?.filter { it.name.startsWith("${pageId}_") }?.forEach { it.delete() }
        }
        mutate(id) { p ->
            p.copy(pages = p.pages.map {
                if (it.id == pageId) it.copy(ocrDone = false, confidence = -1, revision = it.revision + 1) else it
            })
        }
        _ocrRevision.value++
    }

    suspend fun resetOcr(id: String, pageIds: Collection<String>) {
        withContext(Dispatchers.IO) {
            pageIds.forEach { pid ->
                ocrFile(id, pid).delete()
                imagesDir(id).listFiles()?.filter { it.name.startsWith("${pid}_") }?.forEach { it.delete() }
            }
        }
        val set = pageIds.toSet()
        mutate(id) { p -> p.copy(pages = p.pages.map { if (it.id in set) it.copy(ocrDone = false, confidence = -1) else it }) }
        _ocrRevision.value++
    }

    suspend fun loadOcr(projectId: String, pageId: String): OcrPage? = withContext(Dispatchers.IO) {
        val f = ocrFile(projectId, pageId)
        if (!f.exists()) null else try {
            json.decodeFromString(OcrPage.serializer(), f.readText())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveOcr(projectId: String, pageId: String, page: OcrPage) {
        withContext(Dispatchers.IO) {
            val f = ocrFile(projectId, pageId)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(OcrPage.serializer(), page))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
        mutate(projectId) { p ->
            p.copy(pages = p.pages.map { if (it.id == pageId) it.copy(ocrDone = true, confidence = page.confidence) else it })
        }
        _ocrRevision.value++
    }

    companion object {
        const val MAX_PAGE_SIDE = 3400
        const val MAX_SPREAD_SIDE = 5000
        const val THUMB_SIDE = 480
    }
}
