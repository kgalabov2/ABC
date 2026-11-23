package com.alphabetwriter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {
    private val today = 20_000L

    @Test
    fun goodAnswersSpreadReviewsFurtherApart() {
        var state: ReviewState? = null
        val gaps = (1..5).map {
            state = ReviewSchedule.next(state, Grade.GOOD, today)
            state!!.intervalDays
        }
        assertEquals(1, gaps.first())
        assertTrue("gaps grow: $gaps", gaps.zipWithNext().all { (a, b) -> b > a })
        assertTrue("about a month after five good answers: $gaps", gaps.last() in 20..60)
    }

    @Test
    fun forgettingResetsToTomorrowAndLowersEase() {
        val learned = ReviewState(intervalDays = 30, ease = 2.5f, dueDay = today)
        val forgot = ReviewSchedule.next(learned, Grade.AGAIN, today)
        assertEquals(1, forgot.intervalDays)
        assertEquals(today + 1, forgot.dueDay)
        assertTrue(forgot.ease < learned.ease)
        assertTrue(ReviewSchedule.next(learned, Grade.EASY, today).intervalDays > ReviewSchedule.next(learned, Grade.GOOD, today).intervalDays)
        assertTrue(ReviewSchedule.next(learned, Grade.HARD, today).intervalDays < ReviewSchedule.next(learned, Grade.GOOD, today).intervalDays)
    }

    @Test
    fun dueListsLearnedWordsThatAreDueNewOnesFirst() {
        fun word(text: String) = Word(Language.GREEK, Topic.FOOD, "x$text", text, text, text, null, null)
        val fresh = word("a")
        val overdue = word("b")
        val later = word("c")
        val notLearned = word("d")
        val states = mapOf(overdue.key to ReviewState(3, 2.5f, today - 2), later.key to ReviewState(3, 2.5f, today + 2))
        val due = ReviewSchedule.due(listOf(later, overdue, notLearned, fresh), { it != notLearned }, { states[it.key] }, today)
        assertEquals(listOf(fresh, overdue), due)
    }

    @Test
    fun scheduleSurvivesTheBackupFile() {
        val snapshot = ProgressSnapshot(
            setOf("w:el:νερό"), emptyMap(),
            mapOf("w:el:νερό" to ReviewState(6, 2.35f, today + 6)),
        )
        assertEquals(snapshot, ProgressFile.read(ProgressFile.write(snapshot)))
    }
}
