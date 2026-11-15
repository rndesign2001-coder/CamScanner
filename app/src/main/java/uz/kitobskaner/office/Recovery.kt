package uz.kitobskaner.office

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Avtosaqlash va tiklash: tahrir qilinayotgan hujjatning saqlanmagan holati ilova ichki
 * xotirasiga vaqti-vaqti bilan yoziladi. Ilova kutilmaganda yopilsa, keyingi ochilishda
 * shu holatni tiklashni taklif qilamiz. Asl fayl foydalanuvchi saqlamaguncha o'zgarmaydi.
 */
object Recovery {
    private fun dir(c: Context) = File(c.filesDir, "recovery").apply { mkdirs() }

    private fun key(uri: Uri): String = hash(uri.toString().toByteArray())

    fun hash(b: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }

    fun save(c: Context, uri: Uri, bytes: ByteArray) {
        val d = dir(c)
        val k = key(uri)
        val tmp = File(d, "$k.tmp")
        tmp.writeBytes(bytes)
        val dst = File(d, "$k.bin")
        if (!tmp.renameTo(dst)) { dst.writeBytes(bytes); tmp.delete() }
        File(d, "$k.time").writeText(System.currentTimeMillis().toString())
    }

    /** Saqlangan holat va uning vaqti (ms); bo'lmasa null. */
    fun load(c: Context, uri: Uri): Pair<ByteArray, Long>? {
        val d = dir(c)
        val k = key(uri)
        val f = File(d, "$k.bin")
        if (!f.exists() || f.length() == 0L) return null
        val t = File(d, "$k.time").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: f.lastModified()
        return try { f.readBytes() to t } catch (_: Exception) { null }
    }

    fun clear(c: Context, uri: Uri) {
        val d = dir(c)
        val k = key(uri)
        listOf("bin", "time", "tmp").forEach { File(d, "$k.$it").delete() }
    }
}
