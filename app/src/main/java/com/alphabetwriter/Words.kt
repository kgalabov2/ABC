package com.alphabetwriter

import java.text.Normalizer
import kotlin.random.Random

/**
 * A language whose vocabulary the Words tab teaches. Its letters are taught in [scripts] (none for languages
 * written in Cyrillic or Latin letters, which the user already reads; their words have no stroke animation).
 */
enum class Language(
    val title: String,
    val nativeTitle: String,
    val flag: String,
    val languageTag: String,
    val grid: GridStyle,
    val asset: String,
    val code: String,
) {
    JAPANESE("Japanese", "日本語", "🇯🇵", "ja-JP", GridStyle.SQUARE, "words_ja.tsv", "ja"),
    GREEK("Greek", "Ελληνικά", "🇬🇷", "el-GR", GridStyle.LINES, "words_el.tsv", "el"),
    BULGARIAN("Bulgarian", "Български", "🇧🇬", "bg-BG", GridStyle.LINES, "words_bg.tsv", "bg"),
    ITALIAN("Italian", "Italiano", "🇮🇹", "it-IT", GridStyle.LINES, "words_it.tsv", "it"),
    FRENCH("French", "Français", "🇫🇷", "fr-FR", GridStyle.LINES, "words_fr.tsv", "fr"),
    GERMAN("German", "Deutsch", "🇩🇪", "de-DE", GridStyle.LINES, "words_de.tsv", "de");

    val scripts: List<Script>
        get() = when (this) {
            JAPANESE -> listOf(Script.HIRAGANA, Script.KATAKANA)
            GREEK -> listOf(Script.GREEK)
            else -> emptyList()
        }
}

/** Topics, in display order. The level word lists use all of them; the Essentials use the first ten. */
enum class Topic(val id: String, val title: String, val emoji: String) {
    GREETINGS("greetings", "Casual conversation", "👋"),
    NUMBERS("numbers", "Numbers", "🔢"),
    SHOPPING("shopping", "Shopping and money", "🛍️"),
    FOOD("food", "Food and drink", "🍜"),
    TRAVEL("travel", "Travel and directions", "✈️"),
    TIME("time", "Time and days", "🕒"),
    FAMILY("family", "Family and people", "👪"),
    COLOURS("colours", "Colours", "🎨"),
    WEATHER("weather", "Weather", "🌦️"),
    ANIMALS("animals", "Animals", "🐾"),
    CLOTHING("clothing", "Clothes", "👕"),
    HOME("home", "Home and furniture", "🛋️"),
    BODY("body", "Body and health", "🩺"),
    PLACES("places", "Places and buildings", "🏙️"),
    NATURE("nature", "Nature and plants", "🌳"),
    WORK("work", "Work and school", "💼"),
    LEISURE("leisure", "Sport and free time", "⚽"),
    TECH("tech", "Technology", "💻"),
    FEELINGS("feelings", "Feelings and character", "💭"),
    VERBS("verbs", "Verbs (doing words)", "🏃"),
    ADJECTIVES("adjectives", "Adjectives (describing words)", "✨"),
    ADVERBS("adverbs", "Adverbs", "↗️"),
    SMALL_WORDS("small", "Small words (pronouns, prepositions…)", "🔤"),
    OTHER("other", "Other words", "📚"),
}

/**
 * Vocabulary levels: 0 is the hand-made Essentials; 1–5 are 2 000 words each by how common they are, labelled
 * with the CEFR (and JLPT) level they roughly correspond to.
 */
object Levels {
    val all = listOf(0, 1, 2, 3, 4, 5)
    private val cefr = mapOf(1 to "A1–A2", 2 to "B1", 3 to "B2", 4 to "C1", 5 to "C2")
    private val jlpt = mapOf(1 to "N5–N4", 2 to "N3", 3 to "N2", 4 to "N2–N1", 5 to "N1")

    fun title(level: Int, language: Language): String = when (level) {
        0 -> "Essentials"
        else -> "Level $level · ${cefr[level]}" + if (language == Language.JAPANESE) " · JLPT ${jlpt[level]}" else ""
    }

    fun subtitle(level: Int): String = when (level) {
        0 -> "About 125 hand-picked words with pictures"
        else -> "Words ${(level - 1) * 2000 + 1}–${level * 2000} by how common they are"
    }
}

/**
 * A word or short phrase. [image] is its picture: an emoji (also the key of a photo, when there is one) or
 * digits for numbers. [text] is how it is written (kana for Japanese), [reading] its romanisation.
 * [say] is what text-to-speech reads: usually [text], the kanji where bare kana would get the wrong pitch.
 */
data class Word(
    val language: Language,
    val topic: Topic,
    val image: String,
    val text: String,
    val reading: String,
    val meaning: String,
    val kanji: String?,
    val note: String?,
    val say: String = text,
    /** 0 for the Essentials, 1–5 for the levels. */
    val level: Int = 0,
) {
    /** True when the word has something to show as its picture (an emoji, digits or a photo). */
    val hasPicture: Boolean get() = image.isNotBlank()

    /** Progress key. Letters are keyed by their own text, so the "w:" prefix keeps the two apart. */
    val key: String = "w:${language.code}:$text"
}

/** Reads a words_*.tsv asset (see its header for the columns). */
object WordData {
    fun parse(language: Language, lines: Sequence<String>): List<Word> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val f = line.split('\t')
            require(f.size >= 5) { "bad word line: $line" }
            val topic = Topic.entries.firstOrNull { it.id == f[0] }
                ?: throw IllegalArgumentException("unknown topic '${f[0]}' in: $line")
            Word(
                language, topic, image = f[1], text = f[2], reading = f[3], meaning = f[4],
                kanji = f.getOrNull(5)?.takeIf { it.isNotBlank() },
                note = f.getOrNull(6)?.takeIf { it.isNotBlank() },
                say = f.getOrNull(7)?.takeIf { it.isNotBlank() } ?: f[2],
            )
        }
        .toList()

    /**
     * Reads a vocab_*.tsv asset (the level lists made by tools/build_vocab.py):
     * level, topic, word, reading, meaning, kanji, note, speech text, picture — the last five optional.
     */
    fun parseLevels(language: Language, lines: Sequence<String>): List<Word> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val f = line.split('\t')
            require(f.size >= 5) { "bad vocab line: $line" }
            val topic = Topic.entries.firstOrNull { it.id == f[1] } ?: Topic.OTHER
            Word(
                language, topic, image = f.getOrNull(8).orEmpty(), text = f[2], reading = f[3], meaning = f[4],
                kanji = f.getOrNull(5)?.takeIf { it.isNotBlank() },
                note = f.getOrNull(6)?.takeIf { it.isNotBlank() },
                say = f.getOrNull(7)?.takeIf { it.isNotBlank() } ?: f[2],
                level = f[0].toInt(),
            )
        }
        .toList()
}

/**
 * A photo for a picture, with the credit its licence asks for. [source] is its Commons page. Bundled photos
 * have a [file] in the app's assets; the rest have a [url] and are downloaded by the phone on first use.
 */
data class Photo(
    val file: String?,
    val title: String,
    val artist: String,
    val license: String,
    val source: String,
    val licenseUrl: String?,
    val url: String? = null,
) {
    /**
     * Name of the downloaded copy on the phone. Thumbnails of different photos can end in the same name
     * ("500px-Apple.jpg"), so the name starts with a hash of the whole address.
     */
    val cacheName: String by lazy {
        val source = url ?: file.orEmpty()
        val tail = source.substringAfterLast('/').substringBefore('?').replace(UNSAFE, "_").takeLast(60)
        "${sha1Hex(source, 8)}-$tail"
    }

    private companion object {
        val UNSAFE = Regex("[^A-Za-z0-9._-]")
        const val HEX = "0123456789abcdef"

        /** The first [bytes] bytes of the SHA-1 of [text], in hex. */
        fun sha1Hex(text: String, bytes: Int): String {
            val digest = java.security.MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            return buildString(bytes * 2) {
                for (i in 0 until bytes) {
                    val b = digest[i].toInt() and 0xff
                    append(HEX[b shr 4]).append(HEX[b and 0x0f])
                }
            }
        }
    }
}

object PhotoData {
    /** pictures/credits.tsv: picture, file, title, artist, licence, source page, licence link (optional). */
    fun parse(lines: Sequence<String>): Map<String, Photo> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { line ->
            val f = line.split('\t')
            require(f.size >= 6) { "bad photo line: $line" }
            f[0] to Photo(f[1], f[2], f[3], f[4], f[5], f.getOrNull(6)?.takeIf { it.isNotBlank() })
        }

    /** photos_remote.tsv: picture key, image URL, title, artist, licence, source page, licence link. */
    fun parseRemote(lines: Sequence<String>): Map<String, Photo> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { line ->
            val f = line.split('\t')
            require(f.size >= 6) { "bad remote photo line: $line" }
            f[0] to Photo(null, f[2], f[3], f[4], f[5], f.getOrNull(6)?.takeIf { it.isNotBlank() }, url = f[1])
        }
}

/** The strokes of a whole word, one glyph per written character. */
object WordGlyphs {
    /**
     * A space becomes an empty square, so the words of a phrase stay apart (γεια σου). Punctuation (the Greek
     * question mark ;) is skipped. Accented Greek vowels (ά, ϊ, ΐ) are built from the plain letter plus the
     * accent's strokes, which are written last.
     */
    fun of(text: String, glyphs: Map<Char, Glyph>): List<Glyph> = text.mapNotNull {
        if (it == ' ') Glyph(' ', emptyList()) else glyphs[it] ?: composed(it, glyphs)
    }

    fun composed(c: Char, glyphs: Map<Char, Glyph>): Glyph? {
        val parts = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        if (parts.length < 2) return null
        val base = glyphs[parts[0]] ?: return null
        val xs = base.strokes.flatMap { s -> s.points.map { it.x } }
        val cx = (xs.min() + xs.max()) / 2
        val marks = parts.drop(1).map { mark ->
            when (mark) {
                // Tonos: a short stroke above the letter, written from top right down to the left.
                '́' -> listOf(PenStroke(listOf(Pt(cx + 5f, 29f), Pt(cx - 1f, 40f)), Pt(cx + 7f, 32f)))
                // Dialytika: two dots.
                '̈' -> listOf(dot(cx - 7f, 39f), dot(cx + 7f, 39f))
                else -> return null
            }
        }
        return Glyph(c, base.strokes + marks.flatten())
    }

    private fun dot(x: Float, y: Float) = PenStroke(listOf(Pt(x, y - 1.2f), Pt(x, y + 1.2f)), Pt(x - 2f, y - 4f))
}

/** Links a word to the letter pages that teach its characters. */
object WordLetters {
    /**
     * Distinct letters of [word] in order, each with the tab that teaches it. A character with no page of
     * its own falls back to its base letter: Greek ά → α (kana such as が have their own pages).
     */
    fun of(word: Word): List<Pair<Script, Letter>> = word.text
        .filter { it.isLetter() }
        .mapNotNull { c ->
            find(word.language, c.toString())
                ?: find(word.language, Normalizer.normalize(c.toString(), Normalizer.Form.NFD).take(1))
        }
        .distinctBy { it.second.text }

    private fun find(language: Language, text: String): Pair<Script, Letter>? =
        language.scripts.firstNotNullOfOrNull { script -> Alphabets.find(script, text)?.let { script to it } }
}

enum class WordQuestionType {
    /** See and hear the word, pick its picture. */
    PICK_PICTURE,
    /** See the picture, pick the written word. */
    PICK_WORD,
    /** See and hear the word, pick its meaning (for words without a picture). */
    PICK_MEANING,
}

class WordQuestion(val word: Word, val type: WordQuestionType, val choices: List<Word>)

object WordQuiz {
    fun build(
        words: List<Word>,
        allWords: List<Word>,
        count: Int,
        types: Set<WordQuestionType>,
        weight: (Word) -> Int = { 1 },
        random: Random = Random.Default,
    ): List<WordQuestion> {
        require(types.isNotEmpty()) { "no question types" }
        return weightedSample(words, count, weight, random).map { word ->
            // Picture questions need pictures: words without one are asked by meaning instead.
            val type = types.random(random).let { if (it != WordQuestionType.PICK_MEANING && !word.hasPicture) WordQuestionType.PICK_MEANING else it }
            WordQuestion(word, type, choices(word, words, allWords, random, needPictures = type != WordQuestionType.PICK_MEANING))
        }
    }

    /**
     * The answer plus three other words with different pictures, texts and meanings, shuffled: from the same
     * quiz first, then any word of the language. Pictures that look alike are never offered together.
     */
    fun choices(
        answer: Word,
        pool: List<Word>,
        allWords: List<Word>,
        random: Random = Random.Default,
        needPictures: Boolean = true,
    ): List<Word> {
        val picked = ArrayList<Word>()
        for (w in pool.shuffled(random) + allWords.shuffled(random)) {
            if (picked.size == 3) break
            if (needPictures && !w.hasPicture) continue
            val clash = (picked + answer).any {
                it.image == w.image || it.text == w.text || it.meaning == w.meaning || looksAlike(it.image, w.image)
            }
            if (!clash) picked += w
        }
        return (picked + answer).shuffled(random)
    }

    /** Groups of pictures that are hard to tell apart in a quiz: three clocks, two calendars, two bags… */
    private val lookAlikePictures = listOf(
        setOf("⌚", "⏰", "🕐"),
        setOf("📅", "🗓️"),
        setOf("⏭️", "⏮️"),
        setOf("🌅", "🌄"),
        setOf("🌙", "🌃", "🌆"),
        setOf("💴", "💶", "🪙"),
        setOf("🛍️", "👜"),
        setOf("✈️", "🛫"),
        setOf("🚆", "🚉"),
    )

    fun looksAlike(a: String, b: String) = lookAlikePictures.any { a in it && b in it }

    /**
     * The next word for listening practice: drawn at random from [words] (hard ones weighted up by [weight]),
     * never the same word twice in a row.
     */
    fun nextTarget(words: List<Word>, previous: Word?, weight: (Word) -> Int = { 1 }, random: Random = Random.Default): Word {
        require(words.isNotEmpty()) { "no words" }
        val pool = if (words.size > 1) words.filter { it != previous } else words
        return weightedSample(pool, 1, weight, random).single()
    }
}

/** Picks up to [count] different items, each draw favouring heavier [weight]s (at least 1). */
fun <T> weightedSample(items: Collection<T>, count: Int, weight: (T) -> Int, random: Random): List<T> {
    val pool = items.distinct().toMutableList()
    val picked = ArrayList<T>()
    while (picked.size < count && pool.isNotEmpty()) {
        val weights = pool.map { weight(it).coerceAtLeast(1) }
        var r = random.nextInt(weights.sum())
        var i = 0
        while (r >= weights[i]) {
            r -= weights[i]
            i++
        }
        picked += pool.removeAt(i)
    }
    return picked
}
