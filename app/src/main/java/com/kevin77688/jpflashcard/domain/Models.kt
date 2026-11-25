package com.kevin77688.jpflashcard.domain

/** How a word is shown on its card. Chosen per word, because e.g. わたし is often written in kana. */
enum class DisplayMode(val label: String) {
    KANJI("漢字"),
    FURIGANA("漢字＋注音"),
    KANA("只用假名"),
}

data class WordCard(
    val id: String,
    val kanji: String?,
    val kana: String,
    val meaning: String,
    val meaningEn: String = "",
    val pos: List<String> = emptyList(),
    val display: DisplayMode = DisplayMode.FURIGANA,
    val note: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val srs: SrsState = SrsState(),
) {
    val romaji: String get() = Romaji.fromKana(kana)

    /** The spelling shown when kanji display is wanted, falling back to kana. */
    val written: String get() = kanji?.takeIf { it.isNotBlank() } ?: kana

    fun effectiveDisplay(kanaOnly: Boolean): DisplayMode = when {
        kanaOnly || kanji.isNullOrBlank() -> DisplayMode.KANA
        else -> display
    }
}

/** Editable fields of a word, separate from the SRS progress. */
data class WordDraft(
    val kanji: String = "",
    val kana: String = "",
    val meaning: String = "",
    val meaningEn: String = "",
    val pos: List<String> = emptyList(),
    val display: DisplayMode = DisplayMode.FURIGANA,
    val note: String = "",
)

/** How a flashcard session asks the question. The answer side always shows everything. */
enum class ReviewMode(val label: String, val hint: String) {
    JP_TO_MEANING("日→意", "看日文想意思"),
    MEANING_TO_JP("意→日", "看意思想日文"),
    LISTENING("聽力", "聽發音想是哪個字"),
    CHOICE("選擇", "四選一"),
}
