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
    private val SINGLE_WORDS = setOf("u", "U", "o", "a", "A", "и", "И", "в", "В", "с", "С", "к", "К", "о", "О", "у", "У", "я", "Я", "а", "А", "—", "–", "-")
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
        if (uzLatin) {
            w = UZ_APOSTROPHE.replace(w) { it.groupValues[1] + "ʻ" }
            w = w.replace(Regex("ʻ['‘’`ʼ´ʻ\"“]+"), "ʻ")
        }
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
        if (confidence < 45 && t.length <= 2 && t.none { it.isDigit() }) return true
        // yolg'iz bitta belgi (ustun chizig'i "I", "|", "l") — haqiqiy bir harfli so'zlar bundan mustasno
        if (t.length == 1 && confidence < 75 && t !in SINGLE_WORDS && !t[0].isDigit()) return true
        if (confidence < 25 && t.length <= 4) return true
        if (confidence < 40 && alnum.toFloat() / t.length < 0.5f) return true
        return false
    }

    /**
     * Lotin yozuvidagi kitobda yolg'iz kirill so'z (yoki aksincha) — deyarli doim OCR xatosi
     * (rasm, chiziq yoki kursiv matn qoldig'i).
     */
    fun isStrayScript(text: String, confidence: Int, cyrillicBook: Boolean): Boolean {
        var cyr = 0
        var lat = 0
        for (c in text) { if (isCyr(c)) cyr++ else if (isLat(c)) lat++ }
        val foreign = if (cyrillicBook) lat else cyr
        val native = if (cyrillicBook) cyr else lat
        if (foreign == 0 || native > 0) return false
        // ruscha kitobdagi inglizcha atamalar (yoki aksincha) — ishonch yuqori bo'lsa qoldiramiz
        return confidence < 75 || foreign <= 2
    }
}
