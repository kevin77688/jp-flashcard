package com.kevin77688.jpflashcard.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Spaced-repetition progress for one card. Stored on its own so content edits never clobber it. */
data class SrsState(
    val due: Long = 0L,
    val intervalDays: Double = 0.0,
    val ease: Double = Srs.DEFAULT_EASE,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastRating: Int = 0,
    val lastReviewed: Long = 0L,
) {
    val isNew: Boolean get() = reps == 0
}

/**
 * SM-2 style scheduler driven by the user's 1–5 familiarity rating.
 *
 * 1 忘了：show again in this session, interval resets.
 * 2 模糊：tomorrow.
 * 3 想了一下：a little longer than last time.
 * 4 記得：interval × ease.
 * 5 秒答：interval × ease × bonus, and the card gets easier.
 */
object Srs {
    const val DEFAULT_EASE = 2.5
    const val MIN_EASE = 1.3
    const val MAX_EASE = 3.0
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val RELEARN_MS = 60_000L

    private val firstIntervals = mapOf(2 to 1.0, 3 to 2.0, 4 to 4.0, 5 to 7.0)

    fun review(state: SrsState, rating: Int, now: Long): SrsState {
        require(rating in 1..5) { "rating must be 1..5" }
        val ease = (state.ease + when (rating) {
            1 -> -0.20
            2 -> -0.15
            3 -> -0.05
            4 -> 0.0
            else -> 0.10
        }).coerceIn(MIN_EASE, MAX_EASE)

        val interval = when {
            rating == 1 -> 0.0
            state.isNew || state.intervalDays <= 0.0 -> firstIntervals.getValue(rating)
            rating == 2 -> 1.0
            rating == 3 -> max(2.0, state.intervalDays * 1.2)
            rating == 4 -> max(state.intervalDays + 1, state.intervalDays * ease)
            else -> max(state.intervalDays + 2, state.intervalDays * ease * 1.3)
        }.let { min(it, 365.0) }

        val due = if (rating == 1) now + RELEARN_MS else now + (interval * DAY_MS).roundToLong()
        return state.copy(
            due = due,
            intervalDays = interval,
            ease = ease,
            reps = state.reps + 1,
            lapses = state.lapses + if (rating == 1 && !state.isNew) 1 else 0,
            lastRating = rating,
            lastReviewed = now,
        )
    }

    /** Short label for the button, e.g. "1 分鐘", "3 天", "2 個月". */
    fun previewLabel(state: SrsState, rating: Int, now: Long): String {
        val next = review(state, rating, now)
        val ms = next.due - now
        val days = ms.toDouble() / DAY_MS
        return when {
            ms < 60 * 60 * 1000 -> "${max(1, ms / 60_000)} 分鐘"
            days < 1.5 -> "1 天"
            days < 30 -> "${days.roundToLong()} 天"
            days < 365 -> "${(days / 30).roundToLong()} 個月"
            else -> "1 年"
        }
    }

    /** 0..1 mastery used to colour kana chart cells and word lists. */
    fun mastery(state: SrsState?): Float {
        if (state == null || state.isNew) return 0f
        return (state.intervalDays / 21.0).toFloat().coerceIn(0.05f, 1f)
    }
}
