package uz.kitobskaner.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class PageSize(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", 595f, 842f),
    B5("B5", 499f, 709f),
    A5("A5", 420f, 595f),
    BOOK("6×9\"", 432f, 648f),
}

@Serializable
enum class FontChoice(val label: String) { SERIF("Serif (kitob)"), SANS("Sans (zamonaviy)") }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class Palette { INDIGO, OCEAN, EMERALD, SUNSET, ROSE, GRAPHITE, DYNAMIC }

@Serializable
enum class ScanMode { SINGLE, SPREAD }

@Serializable
enum class ImageFilter(val label: String) {
    ORIGINAL("Asl"),
    ENHANCED("Yorqin"),
    GRAY("Kulrang"),
    BW("Oq-qora"),
}

@Serializable
enum class ImageQuality(val label: String, val maxSide: Int, val jpeg: Int) {
    HIGH("Yuqori", 2600, 88),
    MEDIUM("O'rta", 1900, 80),
    LOW("Ixcham", 1400, 70),
}

@Serializable
data class AppSettings(
    val defaultLanguage: String = "auto",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.INDIGO,
    /** Ilova interfeysi tili: "system", "uz", "ru", "en". */
    val appLanguage: String = "system",
    val scanMode: ScanMode = ScanMode.SINGLE,
    // OCR
    val removeHeaders: Boolean = true,
    val keepImages: Boolean = true,
    val fixUzbekApostrophe: Boolean = true,
    /** Qalin matn sezgirligi: 0 (kam) .. 1 (ko'p). */
    val boldSensitivity: Float = 0.5f,
    // Kitob PDF
    val pageSize: PageSize = PageSize.A5,
    val font: FontChoice = FontChoice.SERIF,
    val fontSize: Float = 11f,
    val lineSpacing: Float = 1.3f,
    val titlePage: Boolean = true,
    val pageNumbers: Boolean = true,
    val tableOfContents: Boolean = true,
    // Rasmli PDF
    val imageFilter: ImageFilter = ImageFilter.ENHANCED,
    val imageQuality: ImageQuality = ImageQuality.MEDIUM,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    private fun load(): AppSettings = try {
        prefs.getString("json", null)?.let { json.decodeFromString<AppSettings>(it) } ?: AppSettings()
    } catch (e: Exception) {
        AppSettings()
    }

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        prefs.edit().putString("json", json.encodeToString(AppSettings.serializer(), next)).apply()
    }
}
