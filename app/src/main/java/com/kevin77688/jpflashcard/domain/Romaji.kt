package com.kevin77688.jpflashcard.domain

/**
 * Romaji ⇄ kana conversion used by the in-app input method.
 *
 * Accepts Hepburn and Kunrei spellings (shi/si, chi/ti, tsu/tu, fu/hu, ji/zi), `nn`/`n'` for ん,
 * doubled consonants for っ, `-` for ー, and `x`/`l` prefixes for small kana.
 */
object Romaji {

    private val table: Map<String, String> = buildMap {
        fun row(c: String, kana: String) {
            // kana holds a/i/u/e/o in order; "" means no such syllable.
            "aiueo".forEachIndexed { i, v -> kana.getOrNull(i)?.takeIf { it != '・' }?.let { put(c + v, it.toString()) } }
        }
        row("", "あいうえお")
        row("k", "かきくけこ"); row("g", "がぎぐげご")
        row("s", "さしすせそ"); row("z", "ざじずぜぞ")
        row("t", "たちつてと"); row("d", "だぢづでど")
        row("n", "なにぬねの"); row("h", "はひふへほ")
        row("b", "ばびぶべぼ"); row("p", "ぱぴぷぺぽ")
        row("m", "まみむめも"); row("r", "らりるれろ")
        row("y", "や・ゆ・よ"); row("w", "わ・・・を")
        put("shi", "し"); put("chi", "ち"); put("tsu", "つ"); put("fu", "ふ"); put("ji", "じ")
        put("ye", "いぇ"); put("wi", "うぃ"); put("we", "うぇ"); put("wo", "を")

        // Yōon: consonant + y + a/u/o (and e for loanwords).
        val youon = mapOf(
            "ky" to "き", "gy" to "ぎ", "sy" to "し", "zy" to "じ", "ty" to "ち", "dy" to "ぢ",
            "ny" to "に", "hy" to "ひ", "by" to "び", "py" to "ぴ", "my" to "み", "ry" to "り",
            "sh" to "し", "ch" to "ち", "cy" to "ち", "jy" to "じ",
        )
        val smallY = mapOf('a' to "ゃ", 'u' to "ゅ", 'o' to "ょ", 'e' to "ぇ")
        for ((c, base) in youon) for ((v, small) in smallY) put(c + v, base + small)
        put("ja", "じゃ"); put("ju", "じゅ"); put("jo", "じょ"); put("je", "じぇ")
        put("she", "しぇ"); put("che", "ちぇ")

        // Loanword extensions (mostly used in katakana).
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ"); put("fyu", "ふゅ")
        put("va", "ゔぁ"); put("vi", "ゔぃ"); put("vu", "ゔ"); put("ve", "ゔぇ"); put("vo", "ゔぉ")
        put("thi", "てぃ"); put("thu", "てゅ"); put("dhi", "でぃ"); put("dhu", "でゅ")
        put("twu", "とぅ"); put("dwu", "どぅ")
        put("tsa", "つぁ"); put("tsi", "つぃ"); put("tse", "つぇ"); put("tso", "つぉ")

        // Small kana.
        for (p in listOf("x", "l")) {
            put(p + "a", "ぁ"); put(p + "i", "ぃ"); put(p + "u", "ぅ"); put(p + "e", "ぇ"); put(p + "o", "ぉ")
            put(p + "ya", "ゃ"); put(p + "yu", "ゅ"); put(p + "yo", "ょ"); put(p + "wa", "ゎ")
            put(p + "tu", "っ"); put(p + "tsu", "っ"); put(p + "ka", "ゕ"); put(p + "ke", "ゖ")
        }
        put("nn", "ん"); put("n'", "ん"); put("xn", "ん")
    }

    private val maxKeyLength = table.keys.maxOf { it.length }
    private const val VOWELS = "aiueo"

    /**
     * Converts romaji to kana.
     * @param katakana output katakana instead of hiragana.
     * @param final when false (live typing), a trailing `n` and incomplete consonants stay as letters
     *   so the user can keep typing `na`, `kya`, etc.
     */
    fun toKana(input: String, katakana: Boolean = false, final: Boolean = true): String {
        val s = input.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)

            if (c == '-') { out.append('ー'); i++; continue }

            // Sokuon: a doubled consonant (not n), or "tch".
            if (c.isLetter() && c !in VOWELS && c != 'n' && next == c) {
                out.append('っ'); i++; continue
            }
            if (c == 't' && next == 'c' && s.getOrNull(i + 2) == 'h') {
                out.append('っ'); i++; continue
            }

            // ん before a consonant (other than y / n) or at the end of a finished word.
            if (c == 'n') {
                if (next == 'n') {
                    // "nni" → んに (konnichiha), "nn" / "nnk" → ん.
                    val after = s.getOrNull(i + 2)
                    out.append('ん')
                    i += if (after != null && (after in VOWELS || after == 'y')) 1 else 2
                    continue
                }
                if (next == null) {
                    if (final) { out.append('ん'); i++; continue }
                } else if (next != 'y' && next != '\'' && next !in VOWELS) {
                    out.append('ん'); i++; continue
                }
            }

            var matched = false
            for (len in minOf(maxKeyLength, s.length - i) downTo 1) {
                val kana = table[s.substring(i, i + len)] ?: continue
                out.append(kana)
                i += len
                matched = true
                break
            }
            if (!matched) {
                out.append(
                    when (c) {
                        '.' -> '。'
                        ',' -> '、'
                        '?' -> '？'
                        '!' -> '！'
                        '~' -> '〜'
                        else -> c
                    },
                )
                i++
            }
        }
        val hira = out.toString()
        return if (katakana) toKatakana(hira) else hira
    }

    fun toKatakana(text: String): String = buildString {
        for (ch in text) append(if (ch in 'ぁ'..'ゖ') ch + 0x60 else ch)
    }

    fun toHiragana(text: String): String = buildString {
        for (ch in text) append(if (ch in 'ァ'..'ヶ') ch - 0x60 else ch)
    }

    fun isKana(text: String): Boolean = text.isNotEmpty() && text.all {
        it in 'ぁ'..'ゖ' || it in 'ァ'..'ヺ' || it == 'ー' || it == 'ゝ' || it == 'ゞ' || it == 'ヽ' || it == 'ヾ'
    }

    private val reverse: Map<String, String> by lazy {
        val preferred = mapOf(
            "し" to "shi", "ち" to "chi", "つ" to "tsu", "ふ" to "fu", "じ" to "ji", "ぢ" to "ji", "づ" to "zu",
            "を" to "o", "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho", "ちゃ" to "cha", "ちゅ" to "chu",
            "ちょ" to "cho", "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo", "ぢゃ" to "ja", "ぢゅ" to "ju",
            "ぢょ" to "jo", "しぇ" to "she", "ちぇ" to "che", "じぇ" to "je", "ん" to "n", "ゔ" to "vu",
            "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o", "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo",
        )
        val map = HashMap<String, String>()
        for ((k, v) in table) {
            if (k.startsWith("x") || k.startsWith("l") || k == "n'" || k == "nn") continue
            val existing = map[v]
            if (existing == null || k.length < existing.length) map[v] = k
        }
        map.putAll(preferred)
        map
    }

    /** Hepburn romaji for a kana string, used for display under cards and in the kana chart. */
    fun fromKana(kana: String): String {
        val s = toHiragana(kana)
        val out = StringBuilder()
        var i = 0
        var geminate = false
        var previousWasN = false
        while (i < s.length) {
            val ch = s[i]
            if (ch == 'っ') { geminate = true; i++; continue }
            if (ch == 'ー') {
                out.lastOrNull { it in VOWELS }?.let { out.append(it) }
                previousWasN = false
                i++; continue
            }
            val two = if (i + 1 < s.length) s.substring(i, i + 2) else null
            val roman = two?.let { reverse[it] }?.also { i += 2 } ?: reverse[ch.toString()]?.also { i++ } ?: run {
                i++; ch.toString()
            }
            if (geminate) {
                out.append(if (roman.startsWith("ch")) 't' else roman.first())
                geminate = false
            }
            // ん followed by a vowel or y is written n' to stay unambiguous (kin'en vs kinen).
            if (previousWasN && roman.firstOrNull()?.let { it in VOWELS || it == 'y' } == true) out.append('\'')
            out.append(roman)
            previousWasN = roman == "n"
        }
        if (geminate) out.append("'")
        return out.toString()
    }
}
