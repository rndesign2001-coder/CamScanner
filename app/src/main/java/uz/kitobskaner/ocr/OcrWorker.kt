package uz.kitobskaner.ocr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.kitobskaner.App
import uz.kitobskaner.R
import java.util.concurrent.atomic.AtomicInteger

class OcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT) ?: return Result.failure()
        val app = applicationContext as App
        val repo = app.repository
        repo.ensureLoaded()
        val project = repo.get(projectId) ?: return Result.failure()
        val settings = app.settings.value

        val todo = project.pages.filter { !it.ocrDone }.map { it.id }
        if (todo.isEmpty()) return Result.success()

        try {
            setForeground(foregroundInfo(0, todo.size))
        } catch (e: Exception) {
            // fon rejimida foreground ruxsat etilmasa ham ishlashda davom etamiz
        }

        withContext(Dispatchers.Default) { TessData.ensure(applicationContext) }

        // --- til aniqlash (avto rejim)
        val language: String = project.effectiveLanguage ?: run {
            val detected = detectLanguage(projectId, project.pages.map { it.id }) ?: "uzb+eng"
            repo.setDetectedLanguage(projectId, detected)
            detected
        }

        val cores = Runtime.getRuntime().availableProcessors()
        val maxMem = Runtime.getRuntime().maxMemory()
        val workers = when {
            todo.size < 2 -> 1
            cores >= 6 && maxMem >= 384L * 1024 * 1024 -> 2
            else -> 1
        }

        val done = AtomicInteger(0)
        val queueLock = Mutex()
        val queue = ArrayDeque(todo)
        val attempted = HashSet<String>(todo)
        val total = AtomicInteger(todo.size)
        val recognizer = PageRecognizer(applicationContext)
        val dispatcher = Dispatchers.Default.limitedParallelism(workers)

        coroutineScope {
            (0 until workers).map {
                async(dispatcher) {
                    OcrEngine(applicationContext, language).use { engine ->
                        while (true) {
                            ensureActive()
                            if (isStopped) break
                            val pageId = queueLock.withLock {
                                if (queue.isEmpty()) {
                                    // ish davomida qo'shilgan yangi sahifalar
                                    repo.get(projectId)?.pages
                                        ?.filter { !it.ocrDone && it.id !in attempted }
                                        ?.forEach { queue.addLast(it.id); total.incrementAndGet() }
                                }
                                queue.removeFirstOrNull()?.also { attempted += it }
                            } ?: break
                            val current = repo.get(projectId) ?: break
                            if (current.pages.none { it.id == pageId && !it.ocrDone }) continue
                            val file = repo.pageFile(projectId, pageId)
                            if (!file.exists()) continue
                            val result = try {
                                recognizer.recognize(engine, file, pageId, repo.imagesDir(projectId), settings)
                            } catch (e: OutOfMemoryError) {
                                System.gc()
                                null
                            } catch (e: Exception) {
                                null
                            }
                            if (result != null) repo.saveOcr(projectId, pageId, result)
                            val n = done.incrementAndGet()
                            setProgress(workDataOf(KEY_DONE to n, KEY_TOTAL to total.get()))
                            notifyProgress(n, total.get())
                        }
                    }
                }
            }.awaitAll()
        }
        return Result.success()
    }

    private suspend fun detectLanguage(projectId: String, pageIds: List<String>): String? =
        withContext(Dispatchers.Default) {
            val repo = (applicationContext as App).repository
            val recognizer = PageRecognizer(applicationContext)
            val sample = StringBuilder()
            OcrEngine(applicationContext, "uzb+rus").use { engine ->
                for (pid in pageIds.take(3)) {
                    val prepared = recognizer.prepare(repo.pageFile(projectId, pid), 1800) ?: continue
                    sample.append(engine.text(prepared.norm)).append('\n')
                    LanguageDetector.detect(sample.toString())?.let { if (sample.length > 400) return@withContext it }
                }
            }
            LanguageDetector.detect(sample.toString())
        }

    private fun notifyProgress(done: Int, total: Int) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        try {
            nm.notify(NOTIFICATION_ID, buildNotification(done, total))
        } catch (e: SecurityException) {
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 1)

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val n = buildNotification(done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, n)
    }

    private fun buildNotification(done: Int, total: Int): android.app.Notification {
        ensureChannel(applicationContext)
        return NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Matn aniqlanmoqda")
            .setContentText("$done / $total sahifa")
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val KEY_PROJECT = "project"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        private const val CHANNEL = "ocr"
        private const val NOTIFICATION_ID = 4101

        fun workName(projectId: String) = "ocr_$projectId"

        fun start(context: Context, projectId: String) {
            val req = OneTimeWorkRequestBuilder<OcrWorker>()
                .setInputData(workDataOf(KEY_PROJECT to projectId))
                .addTag("ocr")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(projectId), ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        fun cancel(context: Context, projectId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(projectId))
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "Matnni aniqlash", NotificationManager.IMPORTANCE_LOW)
                    )
                }
            }
        }
    }
}
