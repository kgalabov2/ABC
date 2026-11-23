package com.alphabetwriter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer
import kotlin.random.Random

class WordsTest {
    private val words: Map<Language, List<Word>> = Language.entries.associateWith { language ->
        File("src/main/assets/${language.asset}").useLines { WordData.parse(language, it) }
    }
    private val glyphs: Map<Char, Glyph> = Script.entries.map { it.strokeAsset }.distinct().flatMap { name ->
        File("src/main/assets/$name").useLines { StrokeData.parse(it).entries.toList() }
    }.associate { it.key to it.value }

    @Test
    fun everyTopicHasEnoughWordsWithUniquePicturesAndTexts() {
        for ((language, list) in words) {
            for (topic in Topic.entries.take(10)) { // the Essentials topics; the level lists use the rest too
                val inTopic = list.filter { it.topic == topic }
                assertTrue("$language $topic has ${inTopic.size} words", inTopic.size >= 8)
                val images = inTopic.groupingBy { it.image }.eachCount().filterValues { it > 1 }
                assertTrue("$language $topic repeats pictures $images", images.isEmpty())
            }
            val texts = list.groupingBy { it.text }.eachCount().filterValues { it > 1 }
            assertTrue("$language repeats words $texts", texts.isEmpty())
            assertTrue(list.all { it.reading.isNotBlank() && it.meaning.isNotBlank() && it.image.isNotBlank() })
        }
    }

    @Test
    fun everyWrittenCharacterHasStrokesOrIsPunctuation() {
        for ((language, list) in words.filterKeys { it.scripts.isNotEmpty() }) {
            for (word in list) {
                val cells = WordGlyphs.of(word.text, glyphs)
                val drawn = cells.count { it.strokes.isNotEmpty() }
                val writable = word.text.count { it.isLetter() }
                assertEquals("$language ${word.text}: strokes for $drawn of $writable letters", writable, drawn)
                // A space keeps its own empty square, so the words of a phrase stay apart.
                assertEquals(word.text.count { it == ' ' }, cells.count { it.strokes.isEmpty() })
            }
        }
    }

    @Test
    fun accentedGreekVowelsAddTheAccentStrokeLast() {
        val alpha = glyphs.getValue('α')
        val accented = WordGlyphs.composed('ά', glyphs)!!
        assertEquals(alpha.strokes.size + 1, accented.strokes.size)
        assertTrue(accented.strokes.last().points.all { it.y < 47f }) // above the x-height
        assertEquals(glyphs.getValue('ι').strokes.size + 2, WordGlyphs.composed('ϊ', glyphs)!!.strokes.size)
    }

    @Test
    fun wordLettersLeadToExistingLetterPages() {
        for ((_, list) in words.filterKeys { it.scripts.isNotEmpty() }) {
            for (word in list) {
                val pages = WordLetters.of(word).map { it.second.text }
                for (c in word.text.filter { it.isLetter() }) {
                    val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).take(1)
                    assertTrue("${word.text}: no page for $c in $pages", c.toString() in pages || base in pages)
                }
            }
        }
        assertEquals(listOf("ご", "は", "ん"), WordLetters.of(words.getValue(Language.JAPANESE).first { it.text == "ごはん" }).map { it.second.text })
        assertEquals(listOf("κ", "α", "λ"), WordLetters.of(words.getValue(Language.GREEK).first { it.text == "καλά" }).map { it.second.text })
        val kana = WordLetters.of(words.getValue(Language.JAPANESE).first { it.text == "コーヒー" })
        assertEquals(listOf("コ", "ー", "ヒ"), kana.map { it.second.text })
        assertTrue(kana.all { it.first == Script.KATAKANA })
    }

    @Test
    fun quizChoicesAreFourDifferentPicturesAndWords() {
        val random = Random(7)
        for ((_, list) in words) {
            for (topic in Topic.entries.take(10)) {
                val inTopic = list.filter { it.topic == topic }
                for (answer in inTopic) {
                    val choices = WordQuiz.choices(answer, inTopic, list, random)
                    assertEquals(4, choices.size)
                    assertEquals(1, choices.count { it == answer })
                    assertEquals(4, choices.map { it.image }.toSet().size)
                    assertEquals(4, choices.map { it.text }.toSet().size)
                    assertEquals(4, choices.map { it.meaning }.toSet().size)
                    for (a in choices) for (b in choices) {
                        if (a != b) assertTrue("${a.image} and ${b.image} look alike", !WordQuiz.looksAlike(a.image, b.image))
                    }
                }
            }
        }
        val quiz = WordQuiz.build(
            words.getValue(Language.GREEK).filter { it.topic == Topic.FOOD }, words.getValue(Language.GREEK),
            10, setOf(WordQuestionType.PICK_PICTURE, WordQuestionType.PICK_WORD), random = random,
        )
        assertEquals(10, quiz.size)
        assertEquals(10, quiz.map { it.word }.toSet().size)
    }

    @Test
    fun listeningNeverRepeatsTheSameWordTwiceInARow() {
        val animals = words.getValue(Language.JAPANESE).filter { it.topic == Topic.ANIMALS }
        val random = Random(3)
        var previous: Word? = null
        repeat(200) {
            val next = WordQuiz.nextTarget(animals, previous, random = random)
            assertTrue(next != previous)
            assertTrue(next in animals)
            previous = next
        }
        // A single word is still offered (nothing else to choose).
        assertEquals(animals[0], WordQuiz.nextTarget(animals.take(1), animals[0], random = random))
    }

    @Test
    fun reviewedEntries() {
        val ja = words.getValue(Language.JAPANESE)
        assertEquals("ichiman", ja.first { it.image == "10000" }.reading)
        assertEquals("箸", ja.first { it.text == "はし" }.say) // spoken with its kanji: chopsticks, not bridge
        assertEquals("こんにちは", ja.first { it.text == "こんにちは" }.say)
        assertEquals(null, ja.first { it.text == "こんにちは" }.kanji)
        assertTrue(words.getValue(Language.GREEK).any { it.text == "χαίρω πολύ" })
    }

    @Test
    fun photoCreditsPointAtBundledFiles() {
        val credits = File("src/main/assets/pictures/credits.tsv")
        if (!credits.exists()) return // built without photos
        val photos = credits.useLines { PhotoData.parse(it) }
        val pictures = words.values.flatten().map { it.image }.toSet()
        for ((picture, photo) in photos) {
            assertTrue("photo for unused picture $picture", picture in pictures)
            assertTrue("missing ${photo.file}", File("src/main/assets/pictures/${photo.file}").exists())
            assertTrue("no licence for ${photo.file}", photo.license.isNotBlank())
        }
    }
}

class LevelListsTest {
    @Test
    fun everyLevelListParsesIntoFiveLevels() {
        for (language in Language.entries) {
            val file = File("src/main/assets/vocab_${language.code}.tsv")
            if (!file.exists()) continue
            val words = file.useLines { WordData.parseLevels(language, it) }
            assertTrue("$language has ${words.size} level words", words.size >= 5000)
            assertTrue(words.all { it.level in 1..5 && it.text.isNotBlank() && it.meaning.isNotBlank() })
            assertEquals("$language level 1", 2000, words.count { it.level == 1 })
            // Keys must be unique within a language, or progress of two words would be shared.
            assertEquals(words.size, words.map { it.key }.toSet().size)
        }
    }

    @Test
    fun photosWithTheSameFileNameGetDifferentCacheNames() {
        val a = Photo(null, "Apple", "A", "CC0", "https://commons.wikimedia.org/a", null,
            url = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Apple.jpg/500px-Apple.jpg")
        val b = a.copy(url = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Apple.jpg/500px-Apple.jpg")
        assertTrue(a.cacheName != b.cacheName)
        assertTrue(a.cacheName.endsWith("500px-Apple.jpg"))
        assertTrue(a.cacheName.all { it.isLetterOrDigit() || it in "._-" })
        val remote = File("src/main/assets/photos_remote.tsv")
        if (remote.exists()) {
            val photos = remote.useLines { PhotoData.parseRemote(it) }.values
            assertEquals(photos.size, photos.map { it.cacheName }.toSet().size)
        }
    }

    @Test
    fun levelPicturesPointAtDownloadablePhotos() {
        val remote = File("src/main/assets/photos_remote.tsv")
        if (!remote.exists()) return
        val photos = remote.useLines { PhotoData.parseRemote(it) }
        assertTrue(photos.values.all { it.url!!.startsWith("https://upload.wikimedia.org/") && it.license.isNotBlank() })
        for (language in Language.entries) {
            val file = File("src/main/assets/vocab_${language.code}.tsv")
            if (!file.exists()) continue
            val words = file.useLines { WordData.parseLevels(language, it) }
            // Downloadable photos are keyed "p:…"; other pictures are emoji, digits or colour swatches.
            val missing = words.filter { it.image.startsWith("p:") && it.image !in photos }
            assertTrue("$language pictures without a photo entry: ${missing.take(3)}", missing.isEmpty())
            // Emoji, digits and colours at least; reviewed photos add to this.
            assertTrue("$language has few pictures", words.count { it.hasPicture } > 250)
        }
    }
}

class AnimationsTest {
    @Test
    fun emojiCodeVariantsMatchNotoFileNames() {
        assertEquals(listOf("2708_fe0f", "2708"), Animations.codeVariants("✈️"))
        assertEquals(listOf("1f415", "1f415_fe0f"), Animations.codeVariants("🐕"))
    }

    @Test
    fun bundledAnimationsAreLottieFiles() {
        val files = File("src/main/assets/anim").listFiles().orEmpty()
        assertTrue("animations bundled: ${files.size}", files.size > 100)
        assertTrue(files.all { it.name.matches(Regex("[0-9a-f_]+\\.json")) && it.readText().contains("\"layers\"") })
    }
}
