package uz.kitobskaner.ocr

/**
 * OCR natijasidagi tipik xatolarni tuzatadi:
 *  - bitta so'z ichida lotin va kirill harflari aralashib ketishi (Мocквa → Москва, Toшкент → ...);
 *  - harflar orasidagi 0 raqami (м0сква → москва);
 *  - o'zbek lotin yozuvidagi o' / g' apostroflari (oʻ, gʻ).
 */
object TextCleaner {

    private const val LAT = "aceopxyABCEHKMOPTX"
    private const val CYR = "асеорхуАВСЕНКМОРТХ"
    private val latToCyr = HashMap<Char, Char>().apply { for (i in LAT.indices) put(LAT[i], CYR[i]) }
    private val cyrToLat = HashMap<Char, Char>().apply { for (i in CYR.indices) put(CYR[i], LAT[i]) }
    private val UZ_APOSTROPHE = Regex("([oOgG])['‘’`ʼ´]")

    private fun isCyr(c: Char) = c in 'Ѐ'..'ӿ'
    private fun isLat(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /**
     * @param preferCyrillic kitob asosan kirill yozuvida (aralash so'zlarda shubha bo'lsa)
     */
    fun fixWord(word: String, preferCyrillic: Boolean, uzLatin: Boolean): String {
        var cyr = 0
        var lat = 0
        for (c in word) {
            if (isCyr(c)) cyr++ else if (isLat(c)) lat++
        }
        var w = word
        if (cyr > 0 && lat > 0) {
            val toCyr = cyr > lat || (cyr == lat && preferCyrillic)
            val map = if (toCyr) latToCyr else cyrToLat
            val wrong = w.filter { if (toCyr) isLat(it) else isCyr(it) }
            // faqat barcha "begona" harflar o'xshash juftlikka ega bo'lsa almashtiramiz
            if (wrong.all { map.containsKey(it) }) {
                w = buildString(w.length) { for (c in w) append(map[c] ?: c) }
            }
        }
        // harflar orasidagi 0 → o
        if (w.length >= 3 && w.contains('0')) {
            val letters = w.count { it.isLetter() }
            if (letters >= 2 && letters >= w.length - 2) {
                val cyrWord = w.count { isCyr(it) } >= w.count { isLat(it) }
                val sb = StringBuilder(w)
                for (i in sb.indices) {
                    if (sb[i] == '0') {
                        val prev = sb.getOrNull(i - 1)
                        val next = sb.getOrNull(i + 1)
                        if ((prev != null && prev.isLetter()) || (next != null && next.isLetter())) {
                            val upper = (prev?.isUpperCase() == true) && (next == null || next.isUpperCase())
                            sb[i] = if (cyrWord) (if (upper) 'О' else 'о') else (if (upper) 'O' else 'o')
                        }
                    }
                }
                w = sb.toString()
            }
        }
        if (uzLatin) w = UZ_APOSTROPHE.replace(w) { it.groupValues[1] + "ʻ" }
        return w
    }

    /** OCR shovqini: rasm qirralari, chiziqlar, dog'lar. */
    fun isNoise(text: String, confidence: Int): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        val alnum = t.count { it.isLetterOrDigit() }
        if (alnum == 0) {
            // tinish belgilarining o'zi (— , . «») — ishonch past bo'lsa shovqin
            return confidence < 40 && t.length > 2 || t.all { it in "|_~^=<>\\/*#@" }
        }
        if (confidence < 20 && t.length <= 2) return true
        if (confidence < 30 && alnum.toFloat() / t.length < 0.5f) return true
        return false
    }
}
