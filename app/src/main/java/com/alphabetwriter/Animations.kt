package com.alphabetwriter

import android.content.Context

/**
 * Animated emoji bundled in assets/anim: Google's Noto Emoji animations (CC BY 4.0), as Lottie vector files named
 * by code point ("2708_fe0f.json" for ✈️). A word whose picture is one of these emoji shows the animation.
 */
object Animations {
    const val CREDIT = "Animation: Noto Emoji by Google · CC BY 4.0"
    const val SOURCE = "https://googlefonts.github.io/noto-emoji-animation/"

    @Volatile private var available: Set<String>? = null

    /** The asset path of [emoji]'s animation, or null when it has none (or is not an emoji). */
    fun assetFor(context: Context, emoji: String): String? {
        if (emoji.isBlank() || emoji.startsWith("p:") || emoji.all { it.isDigit() }) return null
        val names = available ?: synchronized(this) {
            available ?: context.assets.list("anim").orEmpty().map { it.removeSuffix(".json") }.toSet().also { available = it }
        }
        return codeVariants(emoji).firstOrNull { it in names }?.let { "anim/$it.json" }
    }

    /** "✈️" -> ["2708_fe0f", "2708"]: Noto names some animations with the variation selector, some without. */
    fun codeVariants(emoji: String): List<String> {
        val code = emoji.codePoints().toArray().joinToString("_") { Integer.toHexString(it) }
        val bare = code.split('_').filter { it != "fe0f" }.joinToString("_")
        return listOf(code, bare, "${bare}_fe0f").distinct()
    }
}
