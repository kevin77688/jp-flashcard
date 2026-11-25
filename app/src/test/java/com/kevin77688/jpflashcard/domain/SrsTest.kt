package com.kevin77688.jpflashcard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {
    private val now = 1_000_000_000_000L

    @Test fun newCardIntervalsGrowWithRating() {
        val fresh = SrsState()
        val days = (2..5).map { Srs.review(fresh, it, now).intervalDays }
        assertEquals(listOf(1.0, 2.0, 4.0, 7.0), days)
    }

    @Test fun forgottenCardComesBackInAMinute() {
        val learned = SrsState(intervalDays = 10.0, reps = 4, due = now)
        val next = Srs.review(learned, 1, now)
        assertEquals(now + Srs.RELEARN_MS, next.due)
        assertEquals(0.0, next.intervalDays, 0.0)
        assertEquals(1, next.lapses)
        assertTrue(next.ease < learned.ease)
    }

    @Test fun goodAnswersKeepGrowing() {
        var s = SrsState()
        val intervals = mutableListOf<Double>()
        repeat(5) {
            s = Srs.review(s, 4, now)
            intervals += s.intervalDays
        }
        assertTrue(intervals.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun easeStaysInBounds() {
        var s = SrsState()
        repeat(30) { s = Srs.review(s, 1, now) }
        assertEquals(Srs.MIN_EASE, s.ease, 1e-9)
        repeat(30) { s = Srs.review(s, 5, now) }
        assertEquals(Srs.MAX_EASE, s.ease, 1e-9)
        assertTrue(s.intervalDays <= 365.0)
    }

    @Test fun previewLabels() {
        val fresh = SrsState()
        assertEquals("1 分鐘", Srs.previewLabel(fresh, 1, now))
        assertEquals("1 天", Srs.previewLabel(fresh, 2, now))
        assertEquals("7 天", Srs.previewLabel(fresh, 5, now))
    }
}
