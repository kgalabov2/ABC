package com.alphabetwriter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class GrammarTest {
    private val sample = """
        # A comment
        @lesson to-be
        title: The verb "to be"
        level: A1
        intro: Saying who and what things are.
        p: The verb **sein** changes with the person.
        table: Present tense
        | person | form |
        | ich | bin |
        | du | bist
        e: Ich bin müde. | | I am tired.
        p: After the table.
        q: Du ___ nett. | bist | bin; ist; sind | You are nice. | du takes bist.
        q: Ich ___ hier. | bin | bist; ist; sind | I am here. | | Ich bin hier!
        q: No blank here | bin | bist; ist; sind | Broken.
        @lesson second
        title: Second
        level: A2
        intro: More.
        e: こんにちは。 | konnichiwa. | Hello. | 今日は。
    """.trimIndent()

    @Test
    fun parsesBlocksInOrderAndSkipsBrokenQuestions() {
        val lessons = GrammarData.parse(sample.lineSequence())
        assertEquals(listOf("to-be", "second"), lessons.map { it.id })
        val first = lessons[0]
        assertEquals("The verb \"to be\"", first.title)
        assertEquals("A1", first.level)
        assertEquals(
            listOf(LessonBlock.Paragraph::class, LessonBlock.Table::class, LessonBlock.Example::class, LessonBlock.Paragraph::class),
            first.blocks.map { it::class },
        )
        val table = first.blocks[1] as LessonBlock.Table
        assertEquals("Present tense", table.caption)
        assertEquals(listOf(listOf("person", "form"), listOf("ich", "bin"), listOf("du", "bist")), table.rows)
        assertEquals(LessonBlock.Example("Ich bin müde.", "", "I am tired.", ""), first.blocks[2])
        assertEquals(2, first.questions.size) // the one without a blank is dropped
        assertEquals("Du bist nett.", first.questions[0].sentence)
        assertEquals("du takes bist.", first.questions[0].why)
        assertEquals("", first.questions[1].why)
        assertEquals("Ich bin hier!", first.questions[1].spoken)
        assertEquals("Du bist nett.", first.questions[0].spoken) // no speech text: the finished sentence
        assertEquals("今日は。", (lessons[1].blocks.single() as LessonBlock.Example).say)
        assertEquals("g:de:to-be", first.key(Language.GERMAN))
    }

    @Test
    fun practiceAsksEveryQuestionOnceWithTheAnswerAmongFourOptions() {
        val lesson = GrammarData.parse(sample.lineSequence()).first()
        val practice = GrammarPractice.build(lesson, Random(1))
        assertEquals(lesson.questions.toSet(), practice.map { it.question }.toSet())
        for (p in practice) {
            assertEquals(4, p.options.toSet().size)
            assertEquals(1, p.options.count { it == p.question.answer })
        }
        assertTrue(GrammarPractice.passed(8, 10))
        assertFalse(GrammarPractice.passed(7, 10))
        assertFalse(GrammarPractice.passed(0, 0))
    }

    @Test
    fun lessonFilesAreComplete() {
        val files = File("src/main/assets").listFiles { f -> f.name.matches(Regex("grammar_[a-z]{2}\\.txt")) }.orEmpty()
        assertEquals(setOf("bg", "de", "ja"), files.map { it.name.substring(8, 10) }.toSet())
        val kanji = Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]")
        for (file in files) {
            val code = file.name.substring(8, 10)
            val language = Language.entries.first { it.code == code }
            val lessons = file.useLines { GrammarData.parse(it) }
            val source = file.readLines().count { it.startsWith("@lesson") }
            assertEquals("$code: every lesson parses, ids unique", source, lessons.size)
            assertTrue("$code has ${lessons.size} lessons", lessons.size >= 10)
            for (lesson in lessons) {
                val name = "${language.code}/${lesson.id}"
                assertTrue("$name level ${lesson.level}", lesson.level in setOf("A1", "A2"))
                assertTrue("$name has no intro", lesson.intro.isNotBlank())
                assertTrue("$name has ${lesson.questions.size} questions", lesson.questions.size >= 6)
                val examples = lesson.blocks.filterIsInstance<LessonBlock.Example>()
                assertTrue("$name has ${examples.size} examples", examples.size >= 3)
                for (q in lesson.questions) {
                    assertEquals("$name: ${q.prompt} needs one gap of exactly ___", 1, Regex("(?<!_)___(?!_)").findAll(q.prompt).count())
                    assertFalse("$name: ${q.prompt} has a gap longer than ___", "____" in q.prompt)
                    assertEquals("$name: ${q.prompt} needs three wrong options", 3, q.wrong.size)
                    assertFalse("$name: ${q.prompt}", q.answer in q.wrong)
                    assertTrue("$name: ${q.prompt} has no English", q.meaning.isNotBlank())
                }
                if (language != Language.GERMAN) assertTrue("$name: an example without reading", examples.all { it.reading.isNotBlank() })
                if (language == Language.JAPANESE) {
                    val shown = examples.map { it.text } +
                        lesson.questions.flatMap { listOf(it.prompt, it.answer) + it.wrong } +
                        lesson.blocks.filterIsInstance<LessonBlock.Table>().flatMap { it.rows.flatten() }
                    assertTrue("$name shows kanji: ${shown.filter { kanji.containsMatchIn(it) }.take(3)}", shown.none { kanji.containsMatchIn(it) })
                }
            }
        }
    }
}
