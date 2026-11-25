package com.kevin77688.jpflashcard.domain

/** A piece of a word: [base] text with optional [ruby] reading shown above it. */
data class RubySegment(val base: String, val ruby: String?)

object Furigana {
    /**
     * Splits a kanji spelling against its reading so okurigana are not covered by ruby:
     * 食べる/たべる → [食(た)][べる], お茶/おちゃ → [お][茶(ちゃ)].
     * Kana runs in the middle (e.g. 取り消す) split the word further when they can be located.
     */
    fun split(kanji: String, reading: String): List<RubySegment> {
        if (kanji.isEmpty()) return listOf(RubySegment(reading, null))
        val hiraReading = Romaji.toHiragana(reading)
        val parts = runs(kanji)
        val result = mutableListOf<RubySegment>()
        if (!match(parts, 0, hiraReading, 0, reading, result)) {
            return listOf(RubySegment(kanji, reading))
        }
        return result
    }

    private data class Run(val text: String, val isKana: Boolean)

    private fun runs(text: String): List<Run> {
        val out = mutableListOf<Run>()
        val sb = StringBuilder()
        var kana: Boolean? = null
        for (ch in text) {
            val isKana = Romaji.isKana(ch.toString())
            if (kana != null && isKana != kana) {
                out += Run(sb.toString(), kana); sb.clear()
            }
            sb.append(ch); kana = isKana
        }
        if (sb.isNotEmpty()) out += Run(sb.toString(), kana == true)
        return out
    }

    /** Backtracking match of runs against the reading. */
    private fun match(
        parts: List<Run>, pi: Int, hira: String, ri: Int, reading: String, acc: MutableList<RubySegment>,
    ): Boolean {
        if (pi == parts.size) return ri == hira.length
        val part = parts[pi]
        if (part.isKana) {
            val p = Romaji.toHiragana(part.text)
            if (!hira.startsWith(p, ri)) return false
            acc += RubySegment(part.text, null)
            if (match(parts, pi + 1, hira, ri + p.length, reading, acc)) return true
            acc.removeAt(acc.lastIndex)
            return false
        }
        // Kanji run: try every non-empty slice of the reading, shortest first.
        val maxEnd = if (pi == parts.lastIndex) hira.length else hira.length - 1
        for (end in ri + 1..maxEnd) {
            if (pi == parts.lastIndex && end != hira.length) continue
            acc += RubySegment(part.text, reading.substring(ri, end))
            if (match(parts, pi + 1, hira, end, reading, acc)) return true
            acc.removeAt(acc.lastIndex)
        }
        return false
    }
}
