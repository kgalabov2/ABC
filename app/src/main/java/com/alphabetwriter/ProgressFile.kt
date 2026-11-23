package com.alphabetwriter

/**
 * The user's progress: learned letters and words, hard ones with how many times each was missed, and the
 * review schedule of learned words.
 */
data class ProgressSnapshot(
    val learned: Set<String>,
    val hard: Map<String, Int>,
    val reviews: Map<String, ReviewState> = emptyMap(),
)

/**
 * Plain-text format of the exported progress file (kept simple so it can be read and fixed by hand):
 *
 *     # Alphabet Writer progress, version 1
 *     learned<TAB>あ
 *     hard<TAB>ぬ<TAB>2
 *     review<TAB>w:ja:いぬ<TAB>interval days<TAB>ease<TAB>due day
 *     end
 *
 * The closing `end` line marks a complete file; anything after it is ignored, because some cloud
 * storage apps cannot truncate a file when it is overwritten with shorter content.
 */
object ProgressFile {
    const val FILE_NAME = "abc-progress.txt"
    private const val HEADER = "# Alphabet Writer progress, version 1"
    private const val END = "end"

    fun write(snapshot: ProgressSnapshot): String = buildString {
        append(HEADER).append('\n')
        append("# learned<TAB>letter, or hard<TAB>letter<TAB>times missed in exams\n")
        snapshot.learned.sorted().forEach { append("learned\t").append(it).append('\n') }
        snapshot.hard.toSortedMap().forEach { (text, misses) ->
            append("hard\t").append(text).append('\t').append(misses).append('\n')
        }
        snapshot.reviews.toSortedMap().forEach { (key, r) ->
            append("review\t").append(key).append('\t').append(r.intervalDays).append('\t')
                .append(r.ease).append('\t').append(r.dueDay).append('\n')
        }
        append(END).append('\n')
    }

    /** Throws [IllegalArgumentException] with a readable message when [text] is not a complete progress file. */
    fun read(text: String): ProgressSnapshot {
        val lines = text.removePrefix("﻿").lines()
        require(lines.firstOrNull()?.trimEnd()?.startsWith("# Alphabet Writer progress") == true) {
            "This is not an ABC progress file."
        }
        val learned = LinkedHashSet<String>()
        val hard = LinkedHashMap<String, Int>()
        val reviews = LinkedHashMap<String, ReviewState>()
        for (raw in lines.drop(1)) {
            val line = raw.trimEnd('\r')
            if (line == END) return ProgressSnapshot(learned, hard, reviews)
            if (line.isBlank() || line.startsWith("#")) continue
            val fields = line.split('\t')
            when (fields[0]) {
                "learned" -> fields.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { learned += it }
                "review" -> {
                    val interval = fields.getOrNull(2)?.toIntOrNull()
                    val ease = fields.getOrNull(3)?.toFloatOrNull()
                    val due = fields.getOrNull(4)?.toLongOrNull()
                    val key = fields.getOrNull(1)
                    if (!key.isNullOrEmpty() && interval != null && ease != null && due != null) {
                        reviews[key] = ReviewState(interval, ease, due)
                    }
                }
                "hard" -> {
                    val misses = fields.getOrNull(2)?.toIntOrNull()
                    val letter = fields.getOrNull(1)
                    if (letter != null && letter.isNotEmpty() && misses != null && misses > 0) hard[letter] = misses
                }
            }
        }
        throw IllegalArgumentException("The progress file is incomplete (it has no closing \"end\" line).")
    }
}
