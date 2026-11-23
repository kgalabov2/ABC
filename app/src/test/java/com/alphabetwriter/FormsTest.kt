package com.alphabetwriter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FormsTest {
    @Test
    fun rowsGroupIntoSectionsInFileOrder() {
        val forms = FormsData.parse(
            sequenceOf(
                "# header",
                "gehen\tPresent\tich\tgehe\t",
                "gehen\tPresent\tdu\tgehst",
                "gehen\tPerfect\ter/sie/es\tist gegangen\t",
                "たべる\tPolite\tます\tたべます\ttabemasu",
                "broken\tline",
            ),
        )
        assertEquals(setOf("gehen", "たべる"), forms.keys)
        assertEquals(listOf("Present", "Perfect"), forms.getValue("gehen").map { it.title })
        assertEquals(listOf(FormRow("ich", "gehe", ""), FormRow("du", "gehst", "")), forms.getValue("gehen")[0].rows)
        assertEquals("tabemasu", forms.getValue("たべる").single().rows.single().reading)
    }

    @Test
    fun formTablesBelongToWordsOfTheLists() {
        val kanji = Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]")
        for (language in Language.entries) {
            val file = File("src/main/assets/forms_${language.code}.tsv")
            if (!file.exists()) continue
            val forms = file.useLines { FormsData.parse(it) }
            val essentials = File("src/main/assets/${language.asset}").useLines { WordData.parse(language, it) }
            val levels = File("src/main/assets/vocab_${language.code}.tsv").let { f ->
                if (f.exists()) f.useLines { WordData.parseLevels(language, it) } else emptyList()
            }
            val words = (essentials + levels).map { it.text }.toSet()
            assertTrue("$language has forms for ${forms.size} words", forms.size >= 300)
            val unknown = forms.keys.filter { it !in words }
            assertTrue("$language forms for words not in the lists: ${unknown.take(5)}", unknown.isEmpty())
            for ((word, sections) in forms) {
                assertTrue("$language $word has one row", sections.sumOf { it.rows.size } >= 2)
                // A section title comes once per word: its rows are together.
                assertEquals("$language $word repeats a section", sections.size, sections.map { it.title }.toSet().size)
                if (language == Language.JAPANESE) {
                    val shown = sections.flatMap { s -> s.rows.map { it.form } }
                    assertTrue("$word shows kanji: $shown", shown.none { kanji.containsMatchIn(it) })
                    assertTrue("$word has a form without romaji", sections.all { s -> s.rows.all { it.reading.isNotBlank() } })
                }
            }
        }
    }
}
