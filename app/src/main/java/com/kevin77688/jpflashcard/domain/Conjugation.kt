package com.kevin77688.jpflashcard.domain

/**
 * Just enough verb conjugation to look up the polite (ます) forms that Duolingo teaches,
 * e.g. でかけます → 出かける → 出かけます. Works on hiragana readings and on kanji spellings,
 * since the inflecting ending is always kana.
 */
object Conjugation {
    private val politeSuffixes = listOf("ませんでした", "ましょう", "ました", "ません", "ます")

    private val iToU = mapOf(
        'い' to 'う', 'き' to 'く', 'ぎ' to 'ぐ', 'し' to 'す', 'ち' to 'つ',
        'に' to 'ぬ', 'び' to 'ぶ', 'み' to 'む', 'り' to 'る',
    )
    private val uToI = iToU.entries.associate { (i, u) -> u to i }

    /** Splits たべました into (たべ, ました); null when the word is not a polite verb form. */
    fun splitPolite(kana: String): Pair<String, String>? =
        politeSuffixes.firstOrNull { kana.endsWith(it) && kana.length > it.length }
            ?.let { kana.dropLast(it.length) to it }

    /** Dictionary-form readings that could produce this ます stem. Over-generates; callers verify. */
    fun dictionaryCandidates(stem: String): List<String> = buildList {
        add(stem + "る") // 一段: たべ → たべる
        iToU[stem.last()]?.let { add(stem.dropLast(1) + it) } // 五段: およぎ → およぐ
        if (stem.endsWith("し")) {
            add(stem.dropLast(1) + "する") // あいし → あいする
            if (stem.length > 1) add(stem.dropLast(1)) // べんきょうし → べんきょう (+する)
        }
        if (stem.endsWith("き")) add(stem.dropLast(1) + "くる") // き → くる
        if (stem.endsWith("い")) add(stem.dropLast(1) + "る") // いらっしゃい → いらっしゃる
    }.distinct()

    /**
     * The ます stem of a dictionary-form [word] (kana or kanji) given its JMdict part-of-speech tags,
     * or null if it is not a verb we know how to conjugate. Nouns tagged "vs" take する: 勉強 → 勉強し.
     */
    fun masuStem(word: String, pos: List<String>): String? {
        fun has(tag: String) = tag in pos
        return when {
            has("vk") && word.endsWith("くる") -> word.dropLast(2) + "き"
            has("vk") && (word.endsWith("来る") || word.endsWith("來る")) -> word.dropLast(1)
            (has("vs-i") || has("vs-s")) && word.endsWith("する") -> word.dropLast(2) + "し"
            pos.any { it == "v1" || it == "v1-s" } && word.endsWith("る") -> word.dropLast(1)
            has("v5aru") && word.endsWith("る") -> word.dropLast(1) + "い"
            pos.any { it.startsWith("v5") } -> uToI[word.last()]?.let { word.dropLast(1) + it }
            has("vs") && !word.endsWith("する") -> word + "し"
            else -> null
        }
    }
}
