package uz.kitobskaner.ocr

/** Matn namunasiga qarab kitob tilini (Tesseract tillar to'plamini) aniqlaydi. */
object LanguageDetector {

    private val UZ_LATIN_WORDS = setOf(
        "va", "bilan", "uchun", "bu", "ham", "edi", "bir", "deb", "emas", "lekin", "kabi", "esa",
        "bo'lib", "boʻlib", "o'z", "oʻz", "yoki", "har", "biz", "siz", "u", "endi", "keyin", "juda",
        "qilib", "bo'lgan", "boʻlgan", "hamda", "ular", "uning", "shu", "nima", "qanday", "men"
    )
    private val EN_WORDS = setOf(
        "the", "and", "of", "to", "is", "in", "that", "it", "was", "for", "with", "as", "on", "he",
        "she", "you", "this", "be", "are", "at", "by", "from", "his", "her", "they", "which", "have"
    )
    private val UZ_CYR_LETTERS = "ўқғҳЎҚҒҲ"
    private val RU_ONLY_LETTERS = "щыЩЫ"

    /** @return masalan "uzb+eng", "rus+eng", "uzb_cyrl+rus", "eng" yoki null (matn yetarli emas) */
    fun detect(text: String): String? {
        var cyr = 0
        var lat = 0
        var uzCyr = 0
        var ruOnly = 0
        for (c in text) {
            when {
                c in 'Ѐ'..'ӿ' -> {
                    cyr++
                    if (c in UZ_CYR_LETTERS) uzCyr++
                    if (c in RU_ONLY_LETTERS) ruOnly++
                }
                c in 'a'..'z' || c in 'A'..'Z' -> lat++
            }
        }
        if (cyr + lat < 60) return null

        if (cyr > lat) {
            val uzScore = uzCyr.toFloat() / cyr
            return if (uzCyr >= 3 && uzScore > 0.004f && uzCyr > ruOnly) "uzb_cyrl+rus" else "rus+eng"
        }

        val words = text.lowercase()
            .split(Regex("[^\\p{L}'ʻʼ‘’]+"))
            .filter { it.isNotEmpty() }
        var uz = 0
        var en = 0
        for (w in words) {
            if (w in UZ_LATIN_WORDS) uz++
            if (w in EN_WORDS) en++
            if (w.contains("o'") || w.contains("g'") || w.contains("oʻ") || w.contains("gʻ") ||
                w.contains("o‘") || w.contains("g‘")
            ) uz++
            if (w.startsWith("sh") || w.startsWith("ch") || w.endsWith("lar") || w.endsWith("ning")) uz++
            if (w.endsWith("ing") && !w.endsWith("ning") || w.endsWith("tion") || w.startsWith("th")) en++
        }
        return if (uz >= en) "uzb+eng" else "eng"
    }
}
