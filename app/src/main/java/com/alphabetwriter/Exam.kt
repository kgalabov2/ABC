package com.alphabetwriter

import kotlin.random.Random

enum class QuestionType { PICK, WRITE }

/**
 * One exam question about [letter]: the user sees [prompt] (a sound or letter name) and either picks the
 * letter from [choices] (four letters, [PICK][QuestionType.PICK]) or writes it ([WRITE][QuestionType.WRITE]).
 */
class Question(val letter: Letter, val type: QuestionType, val prompt: String, val choices: List<Letter>)

object Exam {
    private val promptCache = HashMap<Script, Map<String, String>>()

    /**
     * What a question asks for, keyed by letter text. Unique within a script, so a prompt never fits two
     * letters: small kana say "small", Greek says "small"/"capital", and remaining clashes (ヲ wo vs ウォ wo)
     * get their group name.
     */
    @Synchronized
    fun prompts(script: Script): Map<String, String> = promptCache.getOrPut(script) {
        val groups = Alphabets.groups(script)
        val base = groups.flatMap { g -> g.letters.map { Triple(it.text, g, basePrompt(script, g, it)) } }
        val counts = base.groupingBy { it.third }.eachCount()
        base.associate { (text, group, prompt) ->
            text to if (counts.getValue(prompt) > 1 && group !== groups.first()) "$prompt (${group.title.lowercase()})" else prompt
        }
    }

    private fun basePrompt(script: Script, group: LetterGroup, letter: Letter): String = when {
        script == Script.GREEK -> when {
            letter.text == "ς" -> "final sigma"
            group.title.startsWith("Capital") -> "capital ${letter.label}"
            else -> "small ${letter.label}"
        }
        letter.text == "ー" -> "long vowel mark"
        letter.text == "ぢ" || letter.text == "ヂ" -> "ji (di)"
        letter.text == "づ" || letter.text == "ヅ" -> "zu (du)"
        group.title == "Small kana" -> "small ${letter.label}"
        group.title == "Historical" -> "${letter.label} (old)"
        else -> letter.label
    }

    /**
     * Picks up to [count] different letters from [letters] (the learned ones), favouring heavier [weight]s
     * (hard letters), and turns each into a question of a type drawn from [types].
     */
    fun build(
        script: Script,
        letters: Collection<Letter>,
        count: Int,
        types: Set<QuestionType>,
        weight: (Letter) -> Int = { 1 },
        random: Random = Random.Default,
    ): List<Question> {
        require(types.isNotEmpty()) { "no question types" }
        val prompts = prompts(script)
        return weightedSample(letters, count, weight, random).map { letter ->
            val type = types.random(random)
            Question(
                letter, type, prompts.getValue(letter.text),
                if (type == QuestionType.PICK) choices(script, letter, random, preferred = letters) else emptyList(),
            )
        }
    }

    /**
     * The answer plus three other letters, shuffled. Look-alikes come first, then [preferred] letters (the
     * ones being tested, so the choices are letters the user has studied), then letters from the same group,
     * then any letter of the script, preferring ones of the same length (single kana vs combinations).
     * Kana that sound the same as the answer (じ and ぢ, あ and small ぁ) are never offered.
     */
    fun choices(
        script: Script,
        answer: Letter,
        random: Random = Random.Default,
        preferred: Collection<Letter> = emptyList(),
    ): List<Letter> {
        val lookAlikes = LetterInfos.of(script, answer).lookAlikes
            .mapNotNull { Alphabets.find(script, it.text) }
            .shuffled(random)
        val studied = preferred.shuffled(random)
        val sameGroup = Alphabets.groupOf(script, answer)?.letters.orEmpty().shuffled(random)
        val everything = Alphabets.all(script).shuffled(random)
        val candidates = (lookAlikes + studied + sameGroup + everything).filter {
            it != answer && !confusable(script, it, answer)
        }
        val picked = LinkedHashSet<Letter>()
        for (c in candidates.filter { it.text.length == answer.text.length } + candidates) {
            if (picked.size == 3) break
            picked += c
        }
        return (picked + answer).shuffled(random)
    }

    /**
     * True when [a] would also be a right answer for [b]'s prompt. Kana: same reading or same sound
     * (を and お are both [o]). Greek: only σ and ς, which are both a small sigma; letters that share a sound
     * (η, ι, υ) are still different letters with different names, so they stay fair choices.
     */
    private fun confusable(script: Script, a: Letter, b: Letter): Boolean {
        if (script == Script.GREEK) return setOf(a.text, b.text) == setOf("σ", "ς")
        if (a.label == b.label || (a.say != null && a.say == b.say)) return true
        val ipa = LetterInfos.of(script, a).ipa
        return ipa != null && ipa == LetterInfos.of(script, b).ipa
    }
}
