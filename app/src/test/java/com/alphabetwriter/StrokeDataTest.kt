package com.alphabetwriter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SvgPathTest {
    private fun assertPt(expected: Pt, actual: Pt) {
        assertEquals(expected.x, actual.x, 1e-3f)
        assertEquals(expected.y, actual.y, 1e-3f)
    }

    @Test
    fun relativeCubicsChainFromTheirEndPoints() {
        // KanjiVG style: implicit repeated "c" parameter sets, numbers glued by minus signs.
        val pts = SvgPath.flatten("M10,10c1,1,2,2,3,3c1-1,2-2,3-3")
        assertPt(Pt(10f, 10f), pts.first())
        assertPt(Pt(16f, 10f), pts.last())
        assertPt(Pt(16f, 10f), SvgPath.flatten("M10,10c1,1,2,2,3,3,1-1,2-2,3-3").last())
    }

    @Test
    fun absoluteLinesAndClose() {
        val pts = SvgPath.flatten("M0,0 L10,0 V10 H0 Z")
        assertEquals(listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f), Pt(0f, 10f), Pt(0f, 0f)), pts)
    }

    @Test
    fun implicitLineAfterMoveAndDecimalsWithoutLeadingZero() {
        val pts = SvgPath.flatten("m1.5.5 2,0")
        assertEquals(listOf(Pt(1.5f, 0.5f), Pt(3.5f, 0.5f)), pts)
    }

    @Test
    fun prefixStopsPartWayAlongASegment() {
        val stroke = PenStroke(listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f)), Pt(0f, 0f))
        assertEquals(20f, stroke.length, 1e-4f)
        assertPt(Pt(10f, 0f), stroke.pointAt(0.5f))
        assertPt(Pt(10f, 5f), stroke.pointAt(0.75f))
        assertPt(Pt(0f, 0f), stroke.pointAt(0f))
        assertEquals(stroke.points, stroke.prefix(1f))
    }
}

class AlphabetDataTest {
    private val glyphs: Map<Char, Glyph> = Script.entries.map { it.strokeAsset }.distinct().flatMap { name ->
        File("src/main/assets/$name").useLines { StrokeData.parse(it).entries.toList() }
    }.associate { it.key to it.value }

    @Test
    fun everyLetterHasStrokeDataInsideTheBox() {
        for (script in Script.entries) {
            for (letter in Alphabets.all(script)) {
                for (c in letter.text) {
                    val glyph = glyphs[c]
                    assertTrue("$script ${letter.text}: no strokes for '$c'", glyph != null && glyph.strokes.isNotEmpty())
                    for (s in glyph!!.strokes) {
                        assertTrue("$c has a zero-length stroke", s.length > 1f)
                        assertTrue("$c leaves the box", s.points.all { it.x in -2f..111f && it.y in -2f..111f })
                    }
                }
            }
        }
    }

    @Test
    fun strokeCountsMatchStandardStrokeOrder() {
        val expected = mapOf(
            'あ' to 3, 'い' to 2, 'う' to 2, 'え' to 2, 'お' to 3, 'き' to 4, 'ん' to 1, 'を' to 3, 'が' to 5,
            'ア' to 2, 'イ' to 2, 'ウ' to 3, 'エ' to 3, 'オ' to 3, 'シ' to 3, 'ツ' to 3, 'ン' to 2, 'ー' to 1,
            'Α' to 3, 'Ε' to 4, 'Ο' to 1, 'α' to 1, 'θ' to 2, 'π' to 3,
        )
        for ((c, n) in expected) assertEquals("strokes of $c", n, glyphs.getValue(c).strokes.size)
    }

    @Test
    fun tablesHaveTheExpectedSizesAndUniqueTexts() {
        fun group(script: Script, title: String) = Alphabets.groups(script).first { it.title == title }.letters.size
        assertEquals(46, group(Script.HIRAGANA, "Basic"))
        assertEquals(46, group(Script.KATAKANA, "Basic"))
        assertEquals(26, group(Script.HIRAGANA, "Voiced"))
        assertEquals(33, group(Script.KATAKANA, "Combinations"))
        assertEquals(25, group(Script.GREEK, "Small letters"))
        assertEquals(24, group(Script.GREEK, "Capital letters"))
        // Progress is keyed by text, so texts must be unique across all scripts.
        val all = Script.entries.flatMap { Alphabets.all(it) }.map { it.text }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun everyLetterHasPronunciationDetails() {
        for (script in Script.entries) {
            for (letter in Alphabets.all(script)) {
                val info = LetterInfos.of(script, letter)
                assertTrue("$script ${letter.text}: no sound description", info.sounds.isNotEmpty())
                assertTrue("$script ${letter.text}: empty summary", info.summary.isNotBlank())
                info.counterpart?.let { (s, t) ->
                    assertTrue("$script ${letter.text}: counterpart $t missing in $s", Alphabets.find(s, t) != null)
                }
            }
        }
    }

    @Test
    fun kanaPronunciationTables() {
        fun info(script: Script, text: String) = LetterInfos.of(script, Alphabets.find(script, text)!!)
        assertEquals("[ɕi]", info(Script.HIRAGANA, "し").ipa)
        assertEquals("ши", info(Script.HIRAGANA, "し").cyrillic)
        assertEquals("кьо", info(Script.HIRAGANA, "きょ").cyrillic)
        assertEquals("йо", info(Script.KATAKANA, "ヨ").cyrillic)
        assertEquals("джа", info(Script.KATAKANA, "ジャ").cyrillic)
        assertEquals("[o]", info(Script.HIRAGANA, "を").ipa)
        assertEquals(Script.KATAKANA to "カ", info(Script.HIRAGANA, "か").counterpart)
        assertEquals(Script.GREEK to "σ", info(Script.GREEK, "Σ").counterpart)
        assertEquals(Script.GREEK to "Σ", info(Script.GREEK, "ς").counterpart)
        // Small kana that merge with the kana before them add no beat; small ke is a full one.
        assertTrue(info(Script.HIRAGANA, "ゃ").rhythm!!.startsWith("No beat"))
        assertTrue(info(Script.KATAKANA, "ヶ").rhythm!!.startsWith("One beat"))
        assertTrue(info(Script.KATAKANA, "ヴ").origin!!.contains("v sound"))
        assertTrue(info(Script.KATAKANA, "ガ").origin!!.contains("k→g"))
        assertEquals("Comes from the whole kanji 二, simplified.", info(Script.KATAKANA, "ニ").origin)
        assertEquals("Comes from a part of the kanji 阿.", info(Script.KATAKANA, "ア").origin)
    }
}
