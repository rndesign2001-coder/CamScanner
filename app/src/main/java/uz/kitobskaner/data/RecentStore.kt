package uz.kitobskaner.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Oxirgi ochilgan hujjatlar, sevimlilar va qo'shilgan papkalar (SAF ruxsatlari bilan). */
@Serializable
data class RecentDoc(val uri: String, val name: String, val time: Long, val size: Long = -1, val fav: Boolean = false)

@Serializable
data class SavedFolder(val uri: String, val name: String)

@Serializable
private data class RecentData(val recent: List<RecentDoc> = emptyList(), val folders: List<SavedFolder> = emptyList())

class RecentStore(private val context: Context) {
    private val file = java.io.File(context.filesDir, "recent.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _recent = MutableStateFlow<List<RecentDoc>>(emptyList())
    val recent: StateFlow<List<RecentDoc>> = _recent.asStateFlow()
    private val _folders = MutableStateFlow<List<SavedFolder>>(emptyList())
    val folders: StateFlow<List<SavedFolder>> = _folders.asStateFlow()

    init {
        try {
            if (file.exists()) json.decodeFromString<RecentData>(file.readText()).let { _recent.value = it.recent; _folders.value = it.folders }
        } catch (_: Exception) {
        }
    }

    private fun persist() {
        try {
            val tmp = java.io.File(file.parentFile, "recent.tmp")
            tmp.writeText(json.encodeToString(RecentData(_recent.value, _folders.value)))
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun opened(uri: Uri, name: String, size: Long) {
        // keyinroq qayta ochish uchun doimiy ruxsat (agar berilgan bo'lsa)
        if (uri.scheme == "content") try {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        val key = uri.toString()
        val old = _recent.value.firstOrNull { it.uri == key }
        _recent.value = (listOf(RecentDoc(key, name, System.currentTimeMillis(), size, old?.fav ?: false)) + _recent.value.filter { it.uri != key }).take(200)
        persist()
    }

    @Synchronized
    fun toggleFav(uri: String) {
        _recent.value = _recent.value.map { if (it.uri == uri) it.copy(fav = !it.fav) else it }
        persist()
    }

    @Synchronized
    fun setFav(uri: Uri, name: String, fav: Boolean) {
        val key = uri.toString()
        _recent.value = if (_recent.value.any { it.uri == key }) _recent.value.map { if (it.uri == key) it.copy(fav = fav) else it }
        else _recent.value + RecentDoc(key, name, 0L, -1, fav)
        persist()
    }

    fun isFav(uri: Uri) = _recent.value.any { it.uri == uri.toString() && it.fav }

    @Synchronized
    fun remove(uri: String) {
        _recent.value = _recent.value.filter { it.uri != uri }
        persist()
    }

    @Synchronized
    fun renamed(old: String, new: String, name: String) {
        _recent.value = _recent.value.map { if (it.uri == old) it.copy(uri = new, name = name) else it }
        persist()
    }

    @Synchronized
    fun addFolder(uri: Uri, name: String) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {
        }
        _folders.value = (_folders.value.filter { it.uri != uri.toString() } + SavedFolder(uri.toString(), name))
        persist()
    }

    @Synchronized
    fun removeFolder(uri: String) {
        try { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
        _folders.value = _folders.value.filter { it.uri != uri }
        persist()
    }
}
