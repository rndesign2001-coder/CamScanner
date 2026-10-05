package uz.kitobskaner.data

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<PageInfo> = emptyList(),
    /** "auto" yoki Tesseract tillari, masalan "uzb+eng". */
    val language: String = "auto",
    /** Avto rejimda aniqlangan til. */
    val detectedLanguage: String? = null,
) {
    val effectiveLanguage: String?
        get() = if (language == "auto") detectedLanguage else language

    val ocrDoneCount: Int get() = pages.count { it.ocrDone }
}

@Serializable
data class PageInfo(
    val id: String,
    val ocrDone: Boolean = false,
    val confidence: Int = -1,
    /** Rasm o'zgarganda (burish) oshadi — keshni yangilash uchun. */
    val revision: Int = 0,
)

@Serializable
enum class BlockType { HEADING1, HEADING2, PARAGRAPH, VERSE, IMAGE }

@Serializable
enum class Align { JUSTIFY, LEFT, CENTER, RIGHT }

@Serializable
data class TextSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

@Serializable
data class TextBlock(
    val type: BlockType,
    val spans: List<TextSpan> = emptyList(),
    val align: Align = Align.JUSTIFY,
    /** IMAGE bloki uchun: images/ papkasidagi fayl nomi. */
    val image: String? = null,
    val imageAspect: Float = 1f,
) {
    val plainText: String get() = spans.joinToString("") { it.text }
}

/** Qidiriladigan PDF uchun so'z koordinatalari (OCR rasm pikselida). */
@Serializable
data class OcrWord(
    val t: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    /** Satr asos chizig'i (baseline) Y koordinatasi. */
    val b: Int,
    /** Satr balandligi (x_size). */
    val s: Int,
)

@Serializable
data class OcrPage(
    val width: Int,
    val height: Int,
    val language: String,
    val blocks: List<TextBlock>,
    val words: List<OcrWord> = emptyList(),
    val confidence: Int = -1,
    val edited: Boolean = false,
) {
    fun plainText(): String = blocks.filter { it.type != BlockType.IMAGE }
        .joinToString("\n\n") { it.plainText }
}
