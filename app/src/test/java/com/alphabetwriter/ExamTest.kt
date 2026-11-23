package com.alphabetwriter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExamTest {
    @Test
    fun promptsAreUniqueWithinEachScript() {
        for (script in Script.entries) {
            val prompts = Exam.prompts(script)
            assertEquals(Alphabets.all(script).size, prompts.size)
            val dupes = prompts.values.groupingBy { it }.eachCount().filterValues { it > 1 }
            assertTrue("$script duplicate prompts: $dupes", dupes.isEmpty())
        }
        assertEquals("small a", Exam.prompts(Script.HIRAGANA)["ぁ"])
        assertEquals("a", Exam.prompts(Script.HIRAGANA)["あ"])
        assertEquals("capital alpha", Exam.prompts(Script.GREEK)["Α"])
        assertEquals("small alpha", Exam.prompts(Script.GREEK)["α"])
    }

    @Test
    fun pickChoicesAreFourDistinctLettersWithOneAnswer() {
        val random = Random(42)
        for (script in Script.entries) {
            for (answer in Alphabets.all(script)) {
                val choices = Exam.choices(script, answer, random)
                assertEquals("${answer.text}: $choices", 4, choices.size)
                assertEquals(4, choices.toSet().size)
                assertEquals(1, choices.count { it == answer })
                val others = choices.filter { it != answer }
                if (script != Script.GREEK) {
                    // じ must never be offered when the answer is ぢ, nor あ for small ぁ, nor お for を.
                    assertFalse(others.any { it.label == answer.label })
                    val ipa = LetterInfos.of(script, answer).ipa
                    if (ipa != null) assertFalse(others.any { LetterInfos.of(script, it).ipa == ipa })
                } else if (answer.text == "σ" || answer.text == "ς") {
                    assertFalse(others.any { it.text == "σ" || it.text == "ς" })
                }
            }
        }
    }

    @Test
    fun buildUsesOnlyTheGivenLettersWithoutRepeats() {
        val pool = Alphabets.all(Script.HIRAGANA).take(12)
        val questions = Exam.build(Script.HIRAGANA, pool, 10, setOf(QuestionType.PICK, QuestionType.WRITE), random = Random(1))
        assertEquals(10, questions.size)
        assertEquals(10, questions.map { it.letter }.toSet().size)
        assertTrue(questions.all { it.letter in pool })
        assertTrue(questions.filter { it.type == QuestionType.WRITE }.all { it.choices.isEmpty() })
        assertTrue(questions.filter { it.type == QuestionType.PICK }.all { it.choices.size == 4 })
        // Asking for more questions than letters gives one question per letter.
        assertEquals(12, Exam.build(Script.HIRAGANA, pool, 50, setOf(QuestionType.WRITE), random = Random(2)).size)
    }

    @Test
    fun hardLettersComeFirstMoreOften() {
        val pool = Alphabets.all(Script.HIRAGANA).take(20)
        val hard = pool[7]
        val firsts = (0 until 200).count { seed ->
            Exam.build(
                Script.HIRAGANA, pool, 1, setOf(QuestionType.WRITE),
                weight = { if (it == hard) 4 else 1 }, random = Random(seed),
            ).single().letter == hard
        }
        // Weight 4 of 23 total → about 17% of first picks, versus 5% unweighted.
        assertTrue("picked first $firsts / 200 times", firsts in 20..60)
    }
}

class ProgressFileTest {
    @Test
    fun roundTrips() {
        val snapshot = ProgressSnapshot(setOf("あ", "きゃ", "Ω"), mapOf("ぬ" to 2, "ψ" to 1))
        assertEquals(snapshot, ProgressFile.read(ProgressFile.write(snapshot)))
    }

    @Test
    fun ignoresLeftoverBytesAfterTheEndLine() {
        val text = ProgressFile.write(ProgressSnapshot(setOf("あ"), emptyMap())) + "learned\tい\nhard\tう\t3\n"
        assertEquals(ProgressSnapshot(setOf("あ"), emptyMap()), ProgressFile.read(text))
    }

    @Test
    fun rejectsOtherOrIncompleteFiles() {
        assertThrows(IllegalArgumentException::class.java) { ProgressFile.read("hello\nworld\n") }
        val cut = ProgressFile.write(ProgressSnapshot(setOf("あ"), emptyMap())).removeSuffix("end\n")
        assertThrows(IllegalArgumentException::class.java) { ProgressFile.read(cut) }
    }
}
