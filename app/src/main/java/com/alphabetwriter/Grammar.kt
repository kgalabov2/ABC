package com.alphabetwriter

import android.content.Context
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * A grammar lesson: explanation blocks in reading order, then practice questions. Read from
 * assets/grammar_<code>.txt, whose format tools/check_grammar.py describes and checks.
 */
data class Lesson(
    val id: String,
    val title: String,
    val level: String,
    val intro: String,
    val blocks: List<LessonBlock>,
    val questions: List<LessonQuestion>,
) {
    /** Progress key: learned once a practice is passed, hard while the last practice failed. */
    fun key(language: Language) = "g:${language.code}:$id"
}

sealed interface LessonBlock {
    /** English text; words between ** and ** are shown bold. */
    data class Paragraph(val text: String) : LessonBlock

    /** [rows] starts with the header row; every row has the same number of cells. */
    data class Table(val caption: String, val rows: List<List<String>>) : LessonBlock

    /** [say] is what the voice reads (Japanese with kanji, so it is read naturally); blank = [text]. */
    data class Example(val text: String, val reading: String, val meaning: String, val say: String) : LessonBlock
}

/**
 * A sentence with one [BLANK], its answer, the wrong options, the sentence's English, why the answer fits and
 * (Japanese) the finished sentence with kanji for the voice; blank [say] = [sentence].
 */
data class LessonQuestion(
    val prompt: String,
    val answer: String,
    val wrong: List<String>,
    val meaning: String,
    val why: String,
    val say: String = "",
) {
    val sentence: String get() = prompt.replace(BLANK, answer)
    val spoken: String get() = say.ifBlank { sentence }

    companion object {
        const val BLANK = "___"
    }
}

object GrammarData {
    private val KEYS = setOf("title", "level", "intro", "p", "table", "e", "q")

    /** Lines the format does not allow are skipped (tools/check_grammar.py reports them when the file is written). */
    fun parse(lines: Sequence<String>): List<Lesson> {
        val lessons = mutableListOf<Lesson>()
        var current: LessonBuilder? = null
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("#")) continue
            if (line.startsWith("@lesson")) {
                current?.let { lessons += it.build() }
                current = LessonBuilder(line.removePrefix("@lesson").trim())
                continue
            }
            val lesson = current ?: continue
            if (line.startsWith("|")) {
                lesson.addRow(line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() })
                continue
            }
            val key = line.substringBefore(':', "")
            if (key !in KEYS) continue
            lesson.add(key, line.substringAfter(':').trim())
        }
        current?.let { lessons += it.build() }
        return lessons.filter { it.id.isNotEmpty() }.distinctBy { it.id }
    }

    private class LessonBuilder(val id: String) {
        private var title = ""
        private var level = ""
        private var intro = ""
        private val blocks = mutableListOf<LessonBlock>()
        private val questions = mutableListOf<LessonQuestion>()
        private var tableCaption: String? = null
        private val tableRows = mutableListOf<List<String>>()

        fun addRow(cells: List<String>) {
            if (tableCaption != null) tableRows += cells
        }

        fun add(key: String, value: String) {
            closeTable()
            val fields = value.split('|').map { it.trim() }
            when (key) {
                "title" -> title = value
                "level" -> level = value
                "intro" -> intro = value
                "p" -> if (value.isNotEmpty()) blocks += LessonBlock.Paragraph(value)
                "table" -> tableCaption = value
                "e" -> if (fields.size >= 3 && fields[0].isNotEmpty()) {
                    blocks += LessonBlock.Example(fields[0], fields[1], fields[2], fields.getOrElse(3) { "" })
                }
                "q" -> if (fields.size >= 4) {
                    val wrong = fields[2].split(';').map { it.trim() }.filter { it.isNotEmpty() && it != fields[1] }.distinct()
                    if (fields[0].contains(LessonQuestion.BLANK) && fields[1].isNotEmpty() && wrong.isNotEmpty()) {
                        questions += LessonQuestion(fields[0], fields[1], wrong, fields[3], fields.getOrElse(4) { "" }, fields.getOrElse(5) { "" })
                    }
                }
            }
        }

        private fun closeTable() {
            val caption = tableCaption ?: return
            val width = tableRows.firstOrNull()?.size ?: 0
            // A row with too few or too many cells is padded or cut to the header's width.
            if (width > 0) blocks += LessonBlock.Table(caption, tableRows.map { row -> List(width) { row.getOrElse(it) { "" } } })
            tableCaption = null
            tableRows.clear()
        }

        fun build(): Lesson {
            closeTable()
            return Lesson(id, title.ifEmpty { id }, level, intro, blocks.toList(), questions.toList())
        }
    }
}

/** One practice question as asked: the lesson's question with its options in the order shown. */
data class PracticeQuestion(val question: LessonQuestion, val options: List<String>)

object GrammarPractice {
    /** A practice with at least this share right marks the lesson done. */
    const val PASS_PERCENT = 80

    /** Every question of the lesson once, in random order, each with its options shuffled. */
    fun build(lesson: Lesson, random: Random = Random.Default): List<PracticeQuestion> =
        lesson.questions.shuffled(random).map { q -> PracticeQuestion(q, (q.wrong + q.answer).distinct().shuffled(random)) }

    fun passed(score: Int, total: Int) = total > 0 && score * 100 >= PASS_PERCENT * total
}

/** Loads each language's lessons once per process; a language without a lesson file has none. */
object GrammarStore {
    private val lessons = ConcurrentHashMap<Language, List<Lesson>>()

    // computeIfAbsent, not getOrPut: two callers at once must not both parse the file.
    fun lessons(context: Context, language: Language): List<Lesson> = lessons.computeIfAbsent(language) {
        try {
            context.assets.open(asset(language)).bufferedReader().useLines { GrammarData.parse(it) }
        } catch (_: IOException) {
            emptyList()
        }
    }

    /** The languages that have lessons, in the language list's order. */
    fun languages(context: Context): List<Language> {
        val files = context.assets.list("").orEmpty().toSet()
        return Language.entries.filter { asset(it) in files }
    }

    fun asset(language: Language) = "grammar_${language.code}.txt"
}
