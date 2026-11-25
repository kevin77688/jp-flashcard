package com.kevin77688.jpflashcard.domain

enum class KanaScript(val label: String, val prefix: String) {
    HIRAGANA("平假名", "h"),
    KATAKANA("片假名", "k"),
}

enum class KanaGroup(val label: String, val columns: Int) {
    BASIC("清音", 5),
    DAKUTEN("濁音・半濁音", 5),
    YOUON("拗音", 3),
}

/** One kana sound; [hiragana] is the canonical form, katakana is derived. */
data class KanaSound(val hiragana: String, val group: KanaGroup) {
    /** Chart romaji; を is shown as "wo" as in most kana charts, though it is pronounced "o". */
    val romaji: String = if (hiragana == "を") "wo" else Romaji.fromKana(hiragana)
    val katakana: String = Romaji.toKatakana(hiragana)
    fun text(script: KanaScript) = if (script == KanaScript.HIRAGANA) hiragana else katakana

    /** Unique per kana (romaji is not: じ/ぢ are both "ji"). */
    fun cardId(script: KanaScript) = "${script.prefix}-$hiragana"
}

object Kana {
    /** Grid rows; null is an empty cell (e.g. the gaps in the や and わ rows). */
    private val basicRows = listOf(
        "あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの",
        "はひふへほ", "まみむめも", "や・ゆ・よ", "らりるれろ", "わ・・・を", "ん・・・・",
    )
    private val dakutenRows = listOf("がぎぐげご", "ざじずぜぞ", "だぢづでど", "ばびぶべぼ", "ぱぴぷぺぽ")
    private val youonBases = listOf("き", "し", "ち", "に", "ひ", "み", "り", "ぎ", "じ", "び", "ぴ")

    val grids: Map<KanaGroup, List<List<KanaSound?>>> = mapOf(
        KanaGroup.BASIC to basicRows.map { row -> row.map { c -> if (c == '・') null else KanaSound(c.toString(), KanaGroup.BASIC) } },
        KanaGroup.DAKUTEN to dakutenRows.map { row -> row.map { c -> KanaSound(c.toString(), KanaGroup.DAKUTEN) } },
        KanaGroup.YOUON to youonBases.map { base -> listOf("ゃ", "ゅ", "ょ").map { KanaSound(base + it, KanaGroup.YOUON) } },
    )

    fun sounds(group: KanaGroup): List<KanaSound> = grids.getValue(group).flatten().filterNotNull()

    val all: List<KanaSound> = KanaGroup.entries.flatMap { sounds(it) }

    /** Look-alike pairs that beginners confuse; used by the 易混字 drill. */
    val confusables: List<Pair<String, String>> = listOf(
        "シ" to "ツ", "ソ" to "ン", "ク" to "ケ", "ワ" to "ウ", "チ" to "テ", "ノ" to "メ", "ヌ" to "ス",
        "コ" to "ユ", "マ" to "ム", "ぬ" to "め", "わ" to "れ", "ね" to "れ", "る" to "ろ", "は" to "ほ",
        "い" to "り", "さ" to "ち", "き" to "さ", "あ" to "お", "け" to "は", "ま" to "よ",
    )
}
