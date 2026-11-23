package com.alphabetwriter

/** How the practice squares are ruled: a centre cross for Japanese, writing lines for alphabets. */
enum class GridStyle { SQUARE, LINES }

/** One tab of the app. [strokeAsset] holds the stroke data, [languageTag] picks the text-to-speech voice. */
enum class Script(
    val title: String,
    val nativeTitle: String,
    val languageTag: String,
    val grid: GridStyle,
    val strokeAsset: String,
) {
    HIRAGANA("Hiragana", "ひらがな", "ja-JP", GridStyle.SQUARE, "strokes_kana.txt"),
    KATAKANA("Katakana", "カタカナ", "ja-JP", GridStyle.SQUARE, "strokes_kana.txt"),
    GREEK("Greek", "Ελληνικά", "el-GR", GridStyle.LINES, "strokes_greek.txt"),
}

/**
 * One practice item: a single letter, or a combination such as きゃ that is written as two.
 * [label] is the short reading shown under it (romaji, or the Greek letter's name).
 * [say] is what text-to-speech reads aloud; null when it has no sound of its own.
 */
data class Letter(
    val text: String,
    val label: String,
    val note: String? = null,
    val say: String? = text,
)

/** A table section, laid out in [columns] columns; null cells are gaps (e.g. the missing yi and ye). */
class LetterGroup(val title: String, val subtitle: String, val columns: Int, val rows: List<List<Letter?>>) {
    val letters: List<Letter> = rows.flatten().filterNotNull()
}

object Alphabets {
    private val cache = HashMap<Script, List<LetterGroup>>()

    @Synchronized
    fun groups(script: Script): List<LetterGroup> = cache.getOrPut(script) {
        when (script) {
            Script.HIRAGANA -> Kana.groups(katakana = false)
            Script.KATAKANA -> Kana.groups(katakana = true)
            Script.GREEK -> Greek.groups()
        }
    }

    /** Every item of the script in table order, which is also the order of the previous/next buttons. */
    fun all(script: Script): List<Letter> = groups(script).flatMap { it.letters }

    fun groupOf(script: Script, letter: Letter): LetterGroup? = groups(script).firstOrNull { letter in it.letters }

    fun find(script: Script, text: String): Letter? = all(script).firstOrNull { it.text == text }

    fun toKatakana(hiragana: String): String = Kana.toKatakana(hiragana)
}

private object Kana {
    /** Hiragana → katakana: the two blocks are 0x60 code points apart. */
    fun toKatakana(hiragana: String): String = buildString {
        for (c in hiragana) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
    }

    // Notes keyed by the hiragana form. Shared notes apply to both scripts.
    private val sharedNotes = mapOf(
        "ぢ" to "Also romanised \"di\". Rare: most words use じ.",
        "づ" to "Also romanised \"du\". Rare: most words use ず.",
        "っ" to "Small tsu: doubles the next consonant, e.g. きって kitte. It has no sound of its own.",
        "ゎ" to "Small wa, used in a few words and loanwords.",
        "ゕ" to "Small ka, mostly seen in counters such as 一ヵ月 (ikkagetsu).",
        "ゖ" to "Small ke, but read ka in counters such as 三ヶ月 (sankagetsu) and ga in place names such as 関ヶ原 (Sekigahara).",
        "ゐ" to "Historical: no longer used in modern Japanese.",
        "ゑ" to "Historical: no longer used in modern Japanese.",
        "ん" to "The only kana that is a consonant on its own.",
    )
    private val hiraganaNotes = mapOf(
        "は" to "Read \"wa\" when it is the topic particle.",
        "へ" to "Read \"e\" when it is the direction particle.",
        "を" to "Pronounced \"o\". Used almost only as the object particle.",
        "ゔ" to "Rare in hiragana: ヴ is the usual form.",
    )
    private val katakanaNotes = mapOf(
        "を" to "Pronounced \"o\". Rarely used in katakana.",
        "ゔ" to "Used for the v sound in loanwords.",
    )

    fun groups(katakana: Boolean): List<LetterGroup> {
        fun kana(hira: String, romaji: String, note: String? = null, say: String? = hira): Letter {
            val text = if (katakana) toKatakana(hira) else hira
            val scriptNote = (if (katakana) katakanaNotes else hiraganaNotes)[hira]
            val sayText = say?.let { if (katakana) toKatakana(it) else it }
            return Letter(text, romaji, note ?: scriptNote ?: sharedNotes[hira], sayText)
        }

        fun row(kanaList: String, romajiList: String): List<Letter?> {
            val ks = kanaList.split(' ')
            val rs = romajiList.split(' ')
            require(ks.size == rs.size) { "row mismatch: $kanaList / $romajiList" }
            return ks.zip(rs).map { (k, r) -> if (k == "_") null else kana(k, r) }
        }

        val basic = LetterGroup(
            "Basic", "The 46 basic sounds (gojūon)", 5,
            listOf(
                row("あ い う え お", "a i u e o"),
                row("か き く け こ", "ka ki ku ke ko"),
                row("さ し す せ そ", "sa shi su se so"),
                row("た ち つ て と", "ta chi tsu te to"),
                row("な に ぬ ね の", "na ni nu ne no"),
                row("は ひ ふ へ ほ", "ha hi fu he ho"),
                row("ま み む め も", "ma mi mu me mo"),
                row("や _ ゆ _ よ", "ya _ yu _ yo"),
                row("ら り る れ ろ", "ra ri ru re ro"),
                row("わ _ _ _ を", "wa _ _ _ wo"),
                row("ん _ _ _ _", "n _ _ _ _"),
            ),
        )
        val voiced = LetterGroup(
            "Voiced", "With dakuten ゛ and handakuten ゜", 5,
            listOf(
                row("が ぎ ぐ げ ご", "ga gi gu ge go"),
                row("ざ じ ず ぜ ぞ", "za ji zu ze zo"),
                row("だ ぢ づ で ど", "da ji zu de do"),
                row("ば び ぶ べ ぼ", "ba bi bu be bo"),
                row("ぱ ぴ ぷ ぺ ぽ", "pa pi pu pe po"),
                row("ゔ _ _ _ _", "vu _ _ _ _"),
            ),
        )
        val combos = LetterGroup(
            "Combinations", "An i-kana plus a small ゃ ゅ ょ (yōon)", 3,
            listOf(
                row("きゃ きゅ きょ", "kya kyu kyo"),
                row("しゃ しゅ しょ", "sha shu sho"),
                row("ちゃ ちゅ ちょ", "cha chu cho"),
                row("にゃ にゅ にょ", "nya nyu nyo"),
                row("ひゃ ひゅ ひょ", "hya hyu hyo"),
                row("みゃ みゅ みょ", "mya myu myo"),
                row("りゃ りゅ りょ", "rya ryu ryo"),
                row("ぎゃ ぎゅ ぎょ", "gya gyu gyo"),
                row("じゃ じゅ じょ", "ja ju jo"),
                row("びゃ びゅ びょ", "bya byu byo"),
                row("ぴゃ ぴゅ ぴょ", "pya pyu pyo"),
            ),
        )
        // Small kana sound like their full-size form when read alone, so TTS reads that form.
        fun smallVowel(small: String, big: String, v: String) = kana(
            small, v, say = big,
            note = "Small $v: changes the sound of the kana before it, mostly in loanwords (e.g. ファ fa).",
        )
        fun smallY(small: String, big: String, y: String) = kana(
            small, y, say = big,
            note = "Small $y: joins an i-kana into one syllable, e.g. き + ゃ = きゃ kya.",
        )
        val small = LetterGroup(
            "Small kana", "Written smaller, in the lower part of the square", 5,
            listOf(
                listOf(
                    smallVowel("ぁ", "あ", "a"), smallVowel("ぃ", "い", "i"), smallVowel("ぅ", "う", "u"),
                    smallVowel("ぇ", "え", "e"), smallVowel("ぉ", "お", "o"),
                ),
                listOf(smallY("ゃ", "や", "ya"), null, smallY("ゅ", "ゆ", "yu"), null, smallY("ょ", "よ", "yo")),
                listOf(
                    kana("っ", "tsu", say = null), kana("ゎ", "wa", say = "わ"),
                    kana("ゕ", "ka", say = "か"), kana("ゖ", "ke", say = "け"), null,
                ),
            ),
        )
        val historical = LetterGroup(
            "Historical", "No longer used in modern Japanese", 4,
            buildList {
                add(row("ゐ ゑ _ _", "wi we _ _"))
                if (katakana) {
                    // ヷ ヸ ヹ ヺ have no hiragana form, so they are listed directly.
                    add(
                        listOf("ヷ" to "va", "ヸ" to "vi", "ヹ" to "ve", "ヺ" to "vo").map { (k, r) ->
                            Letter(k, r, "Old spelling of $r: ヴ with a small vowel is used today.")
                        },
                    )
                }
            },
        )
        if (!katakana) return listOf(basic, voiced, combos, small, historical)

        val loanwords = LetterGroup(
            "Loanword sounds", "Extra combinations for foreign words", 4,
            listOf(
                row("ふぁ ふぃ ふぇ ふぉ", "fa fi fe fo"),
                row("ゔぁ ゔぃ ゔぇ ゔぉ", "va vi ve vo"),
                row("うぃ うぇ うぉ _", "wi we wo _"),
                row("てぃ でぃ とぅ どぅ", "ti di tu du"),
                row("しぇ じぇ ちぇ _", "she je che _"),
            ),
        )
        val marks = LetterGroup(
            "Long vowel mark", "Used with katakana", 4,
            listOf(
                listOf(
                    Letter(
                        "ー", "long",
                        "Makes the vowel before it long, e.g. コーヒー kōhī (coffee). Drawn vertically in vertical writing.",
                        say = null,
                    ),
                    null, null, null,
                ),
            ),
        )
        return listOf(basic, voiced, combos, small, loanwords, marks, historical)
    }
}

private object Greek {
    /** capital, small, English name, Greek name. How each one sounds is in LetterInfo. */
    private val letters = listOf(
        listOf("Α", "α", "alpha", "άλφα"),
        listOf("Β", "β", "beta", "βήτα"),
        listOf("Γ", "γ", "gamma", "γάμμα"),
        listOf("Δ", "δ", "delta", "δέλτα"),
        listOf("Ε", "ε", "epsilon", "έψιλον"),
        listOf("Ζ", "ζ", "zeta", "ζήτα"),
        listOf("Η", "η", "eta", "ήτα"),
        listOf("Θ", "θ", "theta", "θήτα"),
        listOf("Ι", "ι", "iota", "ιώτα"),
        listOf("Κ", "κ", "kappa", "κάππα"),
        listOf("Λ", "λ", "lambda", "λάμδα"),
        listOf("Μ", "μ", "mu", "μι"),
        listOf("Ν", "ν", "nu", "νι"),
        listOf("Ξ", "ξ", "xi", "ξι"),
        listOf("Ο", "ο", "omicron", "όμικρον"),
        listOf("Π", "π", "pi", "πι"),
        listOf("Ρ", "ρ", "rho", "ρο"),
        listOf("Σ", "σ", "sigma", "σίγμα"),
        listOf("Τ", "τ", "tau", "ταυ"),
        listOf("Υ", "υ", "upsilon", "ύψιλον"),
        listOf("Φ", "φ", "phi", "φι"),
        listOf("Χ", "χ", "chi", "χι"),
        listOf("Ψ", "ψ", "psi", "ψι"),
        listOf("Ω", "ω", "omega", "ωμέγα"),
    )

    fun groups(): List<LetterGroup> {
        val small = letters.flatMap { (_, s, name, greekName) ->
            val letter = Letter(s, name, "Its name in Greek is $greekName.", greekName)
            if (s == "σ") {
                listOf(
                    letter.copy(note = "Its name in Greek is $greekName. At the end of a word it is written ς."),
                    Letter("ς", "final sigma", "Sigma at the end of a word, e.g. λόγος. Sounds like s.", "τελικό σίγμα"),
                )
            } else {
                listOf(letter)
            }
        }
        val capitals = letters.map { (c, _, name, greekName) ->
            Letter(c, name, "Capital $name. Its name in Greek is $greekName.", greekName)
        }
        return listOf(
            LetterGroup("Small letters", "Used for almost all writing", 4, small.chunked(4).map(::padRow)),
            LetterGroup("Capital letters", "Start of sentences and names", 4, capitals.chunked(4).map(::padRow)),
        )
    }

    private fun padRow(row: List<Letter>): List<Letter?> = row + List(4 - row.size) { null }
}
