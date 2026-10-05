package uz.kitobskaner.ocr

import android.content.Context
import java.io.File

/** Tesseract modellarini assets'dan ilova xotirasiga ko'chiradi (bir marta). */
object TessData {
    val LANGUAGES = listOf("eng", "rus", "uzb", "uzb_cyrl")

    @Volatile
    private var ready = false

    fun dataPath(context: Context): String = File(context.filesDir, "tesseract").absolutePath

    @Synchronized
    fun ensure(context: Context) {
        if (ready) return
        val dir = File(context.filesDir, "tesseract/tessdata").apply { mkdirs() }
        for (lang in LANGUAGES) {
            val name = "$lang.traineddata"
            val target = File(dir, name)
            val expected = try {
                context.assets.openFd("tessdata/$name").use { it.length }
            } catch (e: Exception) {
                -1L
            }
            if (target.exists() && (expected < 0 || target.length() == expected)) continue
            val tmp = File(dir, "$name.part")
            context.assets.open("tessdata/$name").use { input ->
                tmp.outputStream().use { input.copyTo(it, 1 shl 16) }
            }
            target.delete()
            tmp.renameTo(target)
        }
        ready = true
    }
}
