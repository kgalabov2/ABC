package com.alphabetwriter

import android.content.Context
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** One form of a word, e.g. label "du", form "gehst"; [reading] is blank for languages read as written. */
data class FormRow(val label: String, val form: String, val reading: String)

/** A titled group of a word's forms, e.g. "Present" with a row per person. */
data class FormSection(val title: String, val rows: List<FormRow>)

object FormsData {
    /**
     * Word → its form sections, from assets/forms_<code>.tsv (tools/build_forms.py): one row per form,
     * "word, section, label, form, reading", a word's rows together, sections and rows in display order.
     */
    fun parse(lines: Sequence<String>): Map<String, List<FormSection>> {
        val forms = LinkedHashMap<String, MutableList<FormSection>>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val f = line.split('\t')
            if (f.size < 4 || f[0].isBlank() || f[3].isBlank()) continue
            val sections = forms.getOrPut(f[0]) { mutableListOf() }
            val row = FormRow(f[2].trim(), f[3].trim(), f.getOrElse(4) { "" }.trim())
            val last = sections.lastOrNull()
            if (last != null && last.title == f[1]) {
                sections[sections.lastIndex] = last.copy(rows = last.rows + row)
            } else {
                sections += FormSection(f[1], listOf(row))
            }
        }
        return forms
    }
}

/** Loads each language's form tables once per process (off the main thread: they are large). */
object FormsStore {
    private val forms = ConcurrentHashMap<Language, Map<String, List<FormSection>>>()

    /** The tables if they are loaded already, so a card can show them in its first frame. */
    fun loaded(language: Language): Map<String, List<FormSection>>? = forms[language]

    // computeIfAbsent, not getOrPut: cards opened while the file loads wait for it instead of parsing it again.
    fun forms(context: Context, language: Language): Map<String, List<FormSection>> = forms.computeIfAbsent(language) {
        try {
            context.assets.open("forms_${language.code}.tsv").bufferedReader().useLines { FormsData.parse(it) }
        } catch (_: IOException) {
            emptyMap()
        }
    }
}
