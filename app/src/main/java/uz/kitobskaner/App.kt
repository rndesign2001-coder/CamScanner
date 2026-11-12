package uz.kitobskaner

import android.app.Application
import kotlinx.coroutines.flow.StateFlow
import uz.kitobskaner.data.AppSettings
import uz.kitobskaner.data.ProjectRepository
import uz.kitobskaner.data.SettingsStore
import uz.kitobskaner.ocr.OcrWorker
import uz.kitobskaner.ocr.TessData
import uz.kitobskaner.pdf.Exporter

class App : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var repository: ProjectRepository
        private set
    lateinit var exporter: Exporter
        private set
    lateinit var signatures: uz.kitobskaner.data.SignatureStore
        private set

    val settings: StateFlow<AppSettings> get() = settingsStore.state

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        settingsStore = SettingsStore(this)
        repository = ProjectRepository(this, settingsStore)
        exporter = Exporter(this, repository)
        signatures = uz.kitobskaner.data.SignatureStore(this)
        OcrWorker.ensureChannel(this)
        // OCR modellarini oldindan tayyorlab qo'yamiz (birinchi skanerlash tezroq boshlanadi)
        Thread {
            try {
                TessData.ensure(this)
            } catch (_: Throwable) {
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    val crashFile: java.io.File get() = java.io.File(filesDir, "last_crash.txt")

    /** Kutilmagan xatolik matnini faylga yozadi — keyingi ochilishda foydalanuvchi uni yubora oladi. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                crashFile.writeText(
                    "QulayOfis ${BuildConfig.VERSION_NAME} • Android ${android.os.Build.VERSION.RELEASE} • " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        java.util.Date().toString() + "\n\n" + sw.toString().take(12000)
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
