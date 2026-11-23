package com.alphabetwriter

import kotlin.math.max
import kotlin.math.roundToInt

/** How well the user remembered a word in a review. */
enum class Grade(val label: String) { AGAIN("Forgot"), HARD("Hard"), GOOD("Good"), EASY("Easy") }

/**
 * Spaced-repetition state of one learned word: it comes back for review on [dueDay] (days since 1970-01-01),
 * [intervalDays] after its last review. [ease] grows when the word is easy and shrinks when it is forgotten,
 * stretching or shortening the next gaps (a simplified SM-2, as used by Anki).
 */
data class ReviewState(val intervalDays: Int, val ease: Float, val dueDay: Long)

object ReviewSchedule {
    const val START_EASE = 2.5f
    private const val MIN_EASE = 1.3f

    /** The state after answering with [grade] on [today]. A word never reviewed before has [state] null. */
    fun next(state: ReviewState?, grade: Grade, today: Long): ReviewState {
        val ease = state?.ease ?: START_EASE
        val interval = state?.intervalDays ?: 0
        val newEase = when (grade) {
            Grade.AGAIN -> ease - 0.2f
            Grade.HARD -> ease - 0.15f
            Grade.GOOD -> ease
            Grade.EASY -> ease + 0.15f
        }.coerceAtLeast(MIN_EASE)
        val days = when {
            grade == Grade.AGAIN -> 1
            interval == 0 -> when (grade) {
                Grade.HARD, Grade.GOOD -> 1
                else -> 4
            }
            grade == Grade.HARD -> max(interval + 1, (interval * 1.2f).roundToInt())
            grade == Grade.GOOD -> max(interval + 1, (interval * ease).roundToInt())
            else -> max(interval + 2, (interval * ease * 1.3f).roundToInt())
        }
        return ReviewState(days, newEase, today + days)
    }

    /** "1 day", "4 days", "3 weeks", "2 months": the gap [grade] would give, for the grade buttons. */
    fun describe(state: ReviewState?, grade: Grade, today: Long): String {
        val days = next(state, grade, today).intervalDays
        return when {
            days == 1 -> "1 day"
            days < 14 -> "$days days"
            days < 60 -> "${(days / 7f).roundToInt()} weeks"
            days < 365 -> "${(days / 30f).roundToInt()} months"
            else -> "${"%.1f".format(days / 365f)} years"
        }
    }

    /**
     * Learned words due on [today], most overdue first; words learned but never reviewed come first of all.
     */
    fun due(words: List<Word>, isLearned: (Word) -> Boolean, stateOf: (Word) -> ReviewState?, today: Long): List<Word> =
        words.filter { isLearned(it) }
            .map { it to stateOf(it) }
            .filter { (_, s) -> s == null || s.dueDay <= today }
            .sortedBy { (_, s) -> s?.dueDay ?: Long.MIN_VALUE }
            .map { it.first }
}
