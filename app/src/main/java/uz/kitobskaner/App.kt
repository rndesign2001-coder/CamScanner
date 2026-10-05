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

    val settings: StateFlow<AppSettings> get() = settingsStore.state

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsStore = SettingsStore(this)
        repository = ProjectRepository(this, settingsStore)
        exporter = Exporter(this, repository)
        OcrWorker.ensureChannel(this)
        // OCR modellarini oldindan tayyorlab qo'yamiz (birinchi skanerlash tezroq boshlanadi)
        Thread {
            try {
                TessData.ensure(this)
            } catch (_: Throwable) {
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
