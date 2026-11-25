package com.kevin77688.jpflashcard

import com.kevin77688.jpflashcard.domain.Conjugation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConjugationTest {
    @Test fun splitsPoliteEndings() {
        assertEquals("でかけ" to "ます", Conjugation.splitPolite("でかけます"))
        assertEquals("たべ" to "ませんでした", Conjugation.splitPolite("たべませんでした"))
        assertNull(Conjugation.splitPolite("ます"))
        assertNull(Conjugation.splitPolite("かのじょ"))
    }

    @Test fun guessesDictionaryForms() {
        assertTrue("でかける" in Conjugation.dictionaryCandidates("でかけ"))
        assertTrue("およぐ" in Conjugation.dictionaryCandidates("およぎ"))
        assertTrue("かう" in Conjugation.dictionaryCandidates("かい"))
        assertTrue("はしる" in Conjugation.dictionaryCandidates("はしり"))
        assertTrue("くる" in Conjugation.dictionaryCandidates("き"))
        assertTrue("べんきょう" in Conjugation.dictionaryCandidates("べんきょうし"))
    }

    @Test fun conjugatesBackToMasuStem() {
        assertEquals("出かけ", Conjugation.masuStem("出かける", listOf("v1", "vi")))
        assertEquals("泳ぎ", Conjugation.masuStem("泳ぐ", listOf("v5g", "vi")))
        assertEquals("買い", Conjugation.masuStem("買う", listOf("v5u", "vt")))
        assertEquals("走り", Conjugation.masuStem("走る", listOf("v5r", "vi")))
        assertEquals("起き", Conjugation.masuStem("起きる", listOf("v1", "vi")))
        assertEquals("置き", Conjugation.masuStem("置く", listOf("v5k", "vt")))
        assertEquals("来", Conjugation.masuStem("来る", listOf("vk", "vi")))
        assertEquals("き", Conjugation.masuStem("くる", listOf("vk", "vi")))
        assertEquals("し", Conjugation.masuStem("する", listOf("vs-i")))
        assertEquals("勉強し", Conjugation.masuStem("勉強", listOf("n", "vs", "vt")))
        assertEquals("いらっしゃい", Conjugation.masuStem("いらっしゃる", listOf("v5aru")))
        assertNull(Conjugation.masuStem("彼女", listOf("pn", "n")))
    }
}
