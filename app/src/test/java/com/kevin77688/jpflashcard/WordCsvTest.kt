package com.kevin77688.jpflashcard

import com.kevin77688.jpflashcard.domain.DisplayMode
import com.kevin77688.jpflashcard.domain.WordCard
import com.kevin77688.jpflashcard.domain.WordCsv
import org.junit.Assert.assertEquals
import org.junit.Test

class WordCsvTest {
    @Test fun roundTripsTrickyText() {
        val words = listOf(
            WordCard("1", "買います", "かいます", "買；購買", "to buy, to purchase", listOf("v5u", "vt"), DisplayMode.FURIGANA, "他說：\"買\"\n第二行"),
            WordCard("2", null, "いつも", "總是", display = DisplayMode.KANA),
        )
        val back = WordCsv.import(WordCsv.export(words))
        assertEquals(0, back.skipped)
        assertEquals(words.map { it.kanji.orEmpty() to it.kana }, back.drafts.map { it.kanji to it.kana })
        assertEquals(words[0].note, back.drafts[0].note)
        assertEquals(listOf("v5u", "vt"), back.drafts[0].pos)
        assertEquals(DisplayMode.KANA, back.drafts[1].display)
    }

    @Test fun importsHeaderlessSheetWithRomaji() {
        val r = WordCsv.import("私,watashi,我\n,ashita,明天\n,,\n漢字だけ,,\n")
        assertEquals(listOf("わたし", "あした"), r.drafts.map { it.kana })
        assertEquals(DisplayMode.KANA, r.drafts[1].display)
        assertEquals(1, r.skipped)
    }

    @Test fun matchesColumnsByHeader() {
        val r = WordCsv.import("meaning,kana\n明天,あした\n")
        assertEquals("明天", r.drafts.single().meaning)
        assertEquals("あした", r.drafts.single().kana)
    }
}
