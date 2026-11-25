package com.kevin77688.jpflashcard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiTest {
    private fun k(s: String, final: Boolean = true) = Romaji.toKana(s, final = final)

    @Test fun basicWords() {
        assertEquals("たべる", k("taberu"))
        assertEquals("わたし", k("watashi"))
        assertEquals("わたし", k("watasi"))
        assertEquals("すし", k("sushi"))
        assertEquals("ちかてつ", k("chikatetsu"))
        assertEquals("ちかてつ", k("tikatetu"))
        assertEquals("ふじさん", k("fujisan"))
        assertEquals("ふじさん", k("huzisan"))
    }

    @Test fun nRules() {
        assertEquals("こんにちは", k("konnichiha"))
        assertEquals("こんにちは", k("konnnichiha"))
        assertEquals("ほん", k("hon"))
        assertEquals("ほん", k("honn"))
        assertEquals("きんえん", k("kin'en"))
        assertEquals("きねん", k("kinen"))
        assertEquals("せんせい", k("sensei"))
        assertEquals("おんな", k("onna"))
        assertEquals("こんや", k("kon'ya"))
        assertEquals("にゃ", k("nya"))
        assertEquals("さんぽ", k("sanpo"))
    }

    @Test fun liveTypingKeepsTrailingN() {
        assertEquals("ほn", k("hon", final = false))
        assertEquals("ほん", k("honn", final = false))
        assertEquals("かk", k("kak", final = false))
        assertEquals("きゃ", k("kya", final = false))
    }

    @Test fun sokuon() {
        assertEquals("がっこう", k("gakkou"))
        assertEquals("きって", k("kitte"))
        assertEquals("ざっし", k("zasshi"))
        assertEquals("まっちゃ", k("matcha"))
        assertEquals("まっちゃ", k("maccha"))
        assertEquals("いっぱい", k("ippai"))
    }

    @Test fun youonAndSmallKana() {
        assertEquals("きょう", k("kyou"))
        assertEquals("しゃしん", k("shashin"))
        assertEquals("しゃしん", k("syasin"))
        assertEquals("じゅぎょう", k("jugyou"))
        assertEquals("じゅぎょう", k("zyugyou"))
        assertEquals("ちゃ", k("cha"))
        assertEquals("ちゃ", k("tya"))
        assertEquals("ぁ", k("xa"))
        assertEquals("ぁ", k("la"))
        assertEquals("っ", k("xtsu"))
        assertEquals("ゃ", k("lya"))
    }

    @Test fun katakanaAndLongVowel() {
        assertEquals("コーヒー", Romaji.toKana("ko-hi-", katakana = true))
        assertEquals("パーティー", Romaji.toKana("pa-thi-", katakana = true))
        assertEquals("ファミリー", Romaji.toKana("famiri-", katakana = true))
        assertEquals("ヴァイオリン", Romaji.toKana("vaiorin", katakana = true))
        assertEquals("アメリカ", Romaji.toKana("AMERIKA", katakana = true))
    }

    @Test fun punctuation() {
        assertEquals("はい。", k("hai."))
        assertEquals("ええ、", k("ee,"))
    }

    @Test fun kanaToRomaji() {
        assertEquals("taberu", Romaji.fromKana("たべる"))
        assertEquals("konnichiha", Romaji.fromKana("こんにちは"))
        assertEquals("gakkou", Romaji.fromKana("がっこう"))
        assertEquals("matcha", Romaji.fromKana("まっちゃ"))
        assertEquals("shashin", Romaji.fromKana("しゃしん"))
        assertEquals("kin'en", Romaji.fromKana("きんえん"))
        assertEquals("kon'ya", Romaji.fromKana("こんや"))
        assertEquals("koohii", Romaji.fromKana("コーヒー"))
        assertEquals("tsu", Romaji.fromKana("つ"))
        assertEquals("o", Romaji.fromKana("を"))
        assertEquals("kyo", Romaji.fromKana("きょ"))
    }

    @Test fun classification() {
        assertTrue(Romaji.isKana("たべる"))
        assertTrue(Romaji.isKana("コーヒー"))
        assertFalse(Romaji.isKana("食べる"))
        assertEquals("たべる", Romaji.toHiragana("タベル"))
    }
}
