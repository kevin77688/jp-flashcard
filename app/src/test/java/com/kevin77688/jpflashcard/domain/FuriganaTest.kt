package com.kevin77688.jpflashcard.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FuriganaTest {
    private fun s(k: String, r: String) = Furigana.split(k, r).joinToString("") { seg ->
        if (seg.ruby == null) seg.base else "${seg.base}(${seg.ruby})"
    }

    @Test fun okurigana() {
        assertEquals("食(た)べる", s("食べる", "たべる"))
        assertEquals("お茶(ちゃ)", s("お茶", "おちゃ"))
        assertEquals("取(と)り消(け)す", s("取り消す", "とりけす"))
        assertEquals("私(わたし)", s("私", "わたし"))
        assertEquals("日本語(にほんご)", s("日本語", "にほんご"))
    }

    @Test fun unmatchedFallsBackToWholeWord() {
        assertEquals("今日(きょう)", s("今日", "きょう"))
        assertEquals("ABC(えーびーしー)", s("ABC", "えーびーしー"))
    }
}
