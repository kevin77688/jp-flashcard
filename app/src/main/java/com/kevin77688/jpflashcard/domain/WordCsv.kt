package com.kevin77688.jpflashcard.domain

/**
 * CSV import/export of the word list (RFC 4180 quoting, UTF-8 with BOM so Excel and Numbers
 * read the Japanese correctly). SRS progress is not exported; imported words start fresh.
 */
object WordCsv {
    private val header = listOf("kanji", "kana", "meaning", "meaning_en", "display", "note", "pos")

    fun export(words: List<WordCard>): String = buildString {
        append('﻿')
        appendRow(header)
        words.forEach { w ->
            appendRow(listOf(w.kanji.orEmpty(), w.kana, w.meaning, w.meaningEn, w.display.name, w.note, w.pos.joinToString(" ")))
        }
    }

    data class ImportResult(val drafts: List<WordDraft>, val skipped: Int)

    /**
     * Columns are matched by header name, so a hand-made sheet can use any order or only some
     * columns. Without a recognised header the order is kanji, kana, meaning. The kana column may
     * also be typed in romaji.
     */
    fun import(text: String): ImportResult {
        val rows = parse(text.removePrefix("﻿")).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return ImportResult(emptyList(), 0)
        val names = rows.first().map { it.trim().lowercase() }
        val hasHeader = "kana" in names || "reading" in names
        fun col(vararg keys: String) = names.indexOfFirst { it in keys }.takeIf { hasHeader && it >= 0 }
        val iKanji = if (hasHeader) col("kanji", "word") else 0
        val iKana = if (hasHeader) col("kana", "reading") else 1
        val iMeaning = if (hasHeader) col("meaning", "中文", "意思") else 2
        val iEn = col("meaning_en", "english")
        val iDisplay = col("display")
        val iNote = col("note", "備註")
        val iPos = col("pos")
        var skipped = 0
        val drafts = (if (hasHeader) rows.drop(1) else rows).mapNotNull { row ->
            fun at(i: Int?) = i?.let { row.getOrNull(it) }?.trim().orEmpty()
            val kanji = at(iKanji)
            val rawKana = at(iKana)
            val kana = if (rawKana.any { it in 'a'..'z' || it in 'A'..'Z' }) Romaji.toKana(rawKana.lowercase()) else rawKana
            when {
                kana.isBlank() && kanji.isNotBlank() && Romaji.isKana(kanji) -> WordDraft(kana = kanji, meaning = at(iMeaning), display = DisplayMode.KANA)
                kana.isBlank() -> { skipped++; null }
                else -> WordDraft(
                    kanji = kanji,
                    kana = kana,
                    meaning = at(iMeaning),
                    meaningEn = at(iEn),
                    pos = at(iPos).split(' ').filter { it.isNotBlank() },
                    display = DisplayMode.entries.firstOrNull { it.name == at(iDisplay) }
                        ?: if (kanji.isBlank()) DisplayMode.KANA else DisplayMode.FURIGANA,
                    note = at(iNote),
                )
            }
        }
        return ImportResult(drafts, skipped)
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.forEachIndexed { i, c ->
            if (i > 0) append(',')
            if (c.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) append('"').append(c.replace("\"", "\"\"")).append('"') else append(c)
        }
        append("\r\n")
    }

    /** Minimal RFC 4180 parser: quoted fields may contain commas, quotes ("") and newlines. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> { row.add(cell.toString()); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString()); cell.clear(); rows.add(row); row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }
}
