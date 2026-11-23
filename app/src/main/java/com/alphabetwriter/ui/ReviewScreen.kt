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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import com.alphabetwriter.ReviewSchedule
import com.alphabetwriter.Speaker
import com.alphabetwriter.Word
import java.time.LocalDate

/** Today as days since 1970-01-01, the unit of the review schedule. */
fun today(): Long = LocalDate.now().toEpochDay()

/**
 * One review session: the learned words due today. Each is shown as its picture and meaning; the user recalls
 * the word, reveals it and grades the recall. "Forgot" brings the word back a few cards later in the session.
 */
@Stable
class ReviewRun(val language: Language, words: List<Word>, private val progress: Progress, private val day: Long) {
    private val queue = mutableStateListOf<Word>().apply { addAll(words) }
    val total = words.size
    var shown by mutableStateOf(false)
        private set
    var recalled by mutableIntStateOf(0)
        private set

    val current: Word? get() = queue.firstOrNull()
    val remaining: Int get() = queue.size

    fun show() {
        shown = true
    }

    fun grade(grade: Grade) {
        val word = current ?: return
        if (!shown) return
        progress.recordReview(word.key, grade, day)
        queue.removeAt(0)
        if (grade == Grade.AGAIN) queue.add(minOf(3, queue.size), word) else recalled++
        shown = false
    }

    fun previewInterval(grade: Grade): String =
        current?.let { ReviewSchedule.describe(progress.reviewState(it.key), grade, day) }.orEmpty()
}

/** The "Review" card on the Words home: how many learned words are due, or how to get some. */
@Composable
fun ReviewCard(learned: Int, due: Int, onReview: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onReview,
        enabled = due > 0,
        shape = RoundedCornerShape(16.dp),
        color = if (due > 0) cs.primaryContainer else cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔁", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Review", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        learned == 0 -> "Mark words you know with ✓ on their card. They come back here to be recalled, at longer and longer gaps."
                        due > 0 -> "$due ${if (due == 1) "word" else "words"} to recall today"
                        else -> "Nothing due today · $learned ${if (learned == 1) "word" else "words"} in review"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(run: ReviewRun, photos: Map<String, Photo>, speaker: Speaker, onBack: () -> Unit) {
    val word = run.current
    val tag = run.language.languageTag
    val canSpeak = speaker.canSpeak(tag)
    // Say the word as soon as it is revealed.
    LaunchedEffect(word?.key, run.shown) { if (word != null && run.shown && canSpeak) speaker.speak(tag, word.say) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text("Review · ${run.language.title}") },
                actions = {
                    if (word != null) Text("${run.remaining} left", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
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
                Text("🎉", fontSize = 64.sp)
                Text("All done for today", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${run.recalled} ${if (run.recalled == 1) "word" else "words"} reviewed. Each comes back when it is due; " +
                        "the better you knew it, the longer the gap.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                Button(onClick = onBack) { Text("Done") }
                return@Column
            }
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            if (word.hasPicture) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    WordPicture(word, photos, Modifier.fillMaxWidth().height(minOf(maxWidth * 0.75f, 300.dp, screenHeight * 0.4f)), emojiSize = 96, animate = true)
                }
            }
            Text(word.meaning, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(word.topic.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!run.shown) {
                Text(
                    "Say or write the ${run.language.title} word, then check.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                Button(onClick = run::show, modifier = Modifier.fillMaxWidth()) { Text("Show the word") }
            } else {
                Surface(
                    onClick = { if (canSpeak) speaker.speak(tag, word.say) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(word.text, fontSize = 36.sp, textAlign = TextAlign.Center)
                        Text(word.reading, style = MaterialTheme.typography.titleMedium)
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
                Text("How well did you remember it?", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Grade.entries.forEach { g ->
                        val content: @Composable () -> Unit = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(g.label, maxLines = 1)
                                Text(run.previewInterval(g), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                        if (g == Grade.GOOD) {
                            Button(onClick = { run.grade(g) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { content() }
                        } else if (g == Grade.AGAIN) {
                            OutlinedButton(onClick = { run.grade(g) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { content() }
                        } else {
                            FilledTonalButton(onClick = { run.grade(g) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { content() }
                        }
                    }
                }
            }
        }
    }
}
