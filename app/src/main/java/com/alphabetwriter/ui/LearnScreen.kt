package com.alphabetwriter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphabetwriter.Grade
import com.alphabetwriter.Language
import com.alphabetwriter.Photo
import com.alphabetwriter.Progress
import com.alphabetwriter.Speaker
import com.alphabetwriter.Word

/** How many new words one Learn session shows. */
const val LEARN_BATCH = 20

/**
 * Meeting new words: the next words of a level that are not learned yet, most common first. "Learn it" adds the
 * word to the review queue (first review tomorrow); "Already know" adds it with a longer first gap.
 */
@Stable
class LearnRun(val language: Language, val title: String, private val words: List<Word>, private val progress: Progress) {
    var index by mutableIntStateOf(0)
        private set
    var learned by mutableIntStateOf(0)
        private set
    var known by mutableIntStateOf(0)
        private set
    val total: Int get() = words.size
    val current: Word? get() = words.getOrNull(index)

    fun answer(alreadyKnown: Boolean) {
        val word = current ?: return
        if (!progress.isLearned(word.key)) progress.toggle(word.key)
        progress.recordReview(word.key, if (alreadyKnown) Grade.EASY else Grade.GOOD, today())
        if (alreadyKnown) known++ else learned++
        index++
    }
}

@Composable
fun LearnCard(fresh: Int, levelTitle: String, onLearn: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onLearn,
        enabled = fresh > 0,
        shape = RoundedCornerShape(16.dp),
        color = if (fresh > 0) cs.tertiaryContainer else cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✨", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Learn new words", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (fresh > 0) "Next ${minOf(fresh, LEARN_BATCH)} of the $fresh new words in $levelTitle, most common first"
                    else "Every word in $levelTitle is in your review queue",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(run: LearnRun, photos: Map<String, Photo>, speaker: Speaker, onBack: () -> Unit) {
    val word = run.current
    val tag = run.language.languageTag
    val canSpeak = speaker.canSpeak(tag)
    LaunchedEffect(word?.key) { if (word != null && canSpeak) speaker.speak(tag, word.say) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text("Learn · ${run.title}") },
                actions = {
                    if (word != null) Text("${run.index + 1} / ${run.total}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (word == null) {
                Spacer(Modifier.height(48.dp))
                Text("🌱", fontSize = 64.sp)
                Text("${run.learned + run.known} new words added", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${run.learned} to learn and ${run.known} you already knew. They come back in Review: the ones to learn tomorrow.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                Button(onClick = onBack) { Text("Done") }
                return@Column
            }
            if (word.hasPicture) {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    WordPicture(word, photos, Modifier.fillMaxWidth().height(minOf(maxWidth * 0.75f, 300.dp, screenHeight * 0.4f)), emojiSize = 96, animate = true)
                }
            }
            Surface(
                onClick = { if (canSpeak) speaker.speak(tag, word.say) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(word.text, fontSize = 36.sp, textAlign = TextAlign.Center)
                    Text(word.reading, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    word.kanji?.let { Text("Kanji: $it", style = MaterialTheme.typography.bodyMedium) }
                    if (canSpeak) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tap to hear it again", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text(word.meaning, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            word.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            Text(word.topic.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { run.answer(alreadyKnown = true) }, modifier = Modifier.weight(1f)) { Text("Already know") }
                Button(onClick = { run.answer(alreadyKnown = false) }, modifier = Modifier.weight(1f)) { Text("Learn it") }
            }
        }
    }
}
