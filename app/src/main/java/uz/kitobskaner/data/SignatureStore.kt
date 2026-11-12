package uz.kitobskaner.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Saqlangan imzolar va muhrlar (shaffof PNG) kutubxonasi. */
class SignatureStore(context: Context) {
    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }
    private val _items = MutableStateFlow<List<File>>(emptyList())
    val items: StateFlow<List<File>> = _items.asStateFlow()

    init { refresh() }

    fun refresh() {
        _items.value = dir.listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** @param kind "sign" yoki "stamp" — fayl nomi boshida saqlanadi */
    suspend fun save(bmp: Bitmap, kind: String): File = withContext(Dispatchers.IO) {
        val f = File(dir, "${kind}_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        refresh()
        f
    }

    fun delete(f: File) {
        f.delete()
        refresh()
    }

    fun isStamp(f: File) = f.name.startsWith("stamp_")
}
