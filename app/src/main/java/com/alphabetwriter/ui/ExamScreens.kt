package com.alphabetwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphabetwriter.Alphabets
import com.alphabetwriter.Exam
import com.alphabetwriter.Glyph
import com.alphabetwriter.Letter
import com.alphabetwriter.Progress
import com.alphabetwriter.Question
import com.alphabetwriter.QuestionType
import com.alphabetwriter.Script
import com.alphabetwriter.Speaker

enum class ExamMode(val label: String, val types: Set<QuestionType>) {
    MIXED("Mixed", setOf(QuestionType.PICK, QuestionType.WRITE)),
    PICK("Pick from 4", setOf(QuestionType.PICK)),
    WRITE("Write it", setOf(QuestionType.WRITE)),
}

/** One exam in progress. Each question gets exactly one result before the next one is shown. */
@Stable
class ExamRun(val script: Script, val questions: List<Question>, private val progress: Progress) {
    var index by mutableIntStateOf(0)
        private set
    /** True once a choice was picked or the written answer was revealed. */
    var revealed by mutableStateOf(false)
        private set
    var chosen by mutableStateOf<Letter?>(null)
        private set
    /** The user's writing for the current question, in pad-height units. */
    val ink = mutableStateListOf<List<Offset>>()
    private val results = mutableStateListOf<Boolean>()

    val finished: Boolean get() = index >= questions.size
    val current: Question get() = questions[index]
    val score: Int get() = results.count { it }
    val missed: List<Letter> get() = questions.filterIndexed { i, _ -> results.getOrNull(i) == false }.map { it.letter }

    fun pick(letter: Letter) {
        if (finished || revealed) return
        chosen = letter
        revealed = true
        record(letter == current.letter)
    }

    fun reveal() {
        if (!finished) revealed = true
    }

    /** The user's own verdict on a written answer; moves on to the next question. */
    fun selfEvaluate(correct: Boolean) {
        if (finished || !revealed || results.size > index) return
        record(correct)
        next()
    }

    fun next() {
        if (finished || results.size <= index) return // unanswered
        index++
        revealed = false
        chosen = null
        ink.clear()
    }

    private fun record(correct: Boolean) {
        results += correct
        progress.recordAnswer(current.letter, correct)
    }
}

/** Letters of the script that the user marked as learned, in table order. */
fun learnedLetters(script: Script, progress: Progress): List<Letter> =
    Alphabets.all(script).filter { progress.isLearned(it) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamSetupScreen(
    script: Script,
    progress: Progress,
    mode: ExamMode,
    onMode: (ExamMode) -> Unit,
    count: Int,
    onCount: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val learned = learnedLetters(script, progress)
    val hardCount = learned.count { progress.isHard(it) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Exam · ${script.title}") },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (learned.isEmpty()) {
                Text(
                    "The exam tests the letters you have marked as learned, and none are marked in ${script.title} yet. " +
                        "Open a letter and tap ✓ in its top bar once you know it.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    "Tests the ${learned.size} ${if (learned.size == 1) "letter" else "letters"} you marked as learned" +
                        (if (hardCount > 0) ", including $hardCount marked hard, which come up more often." else "."),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Text("Questions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExamMode.entries.forEach { m ->
                    FilterChip(selected = m == mode, onClick = { onMode(m) }, label = { Text(m.label) })
                }
            }
            Text(
                when (mode) {
                    ExamMode.PICK -> "You see a sound or a letter name and pick its letter out of four. Checked right away."
                    ExamMode.WRITE -> "You see a sound or a letter name and write the letter, then compare with the answer and say whether you knew it."
                    ExamMode.MIXED -> "A mix of picking from four and writing."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("How many", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20).forEach { n ->
                    FilterChip(selected = count == n, onClick = { onCount(n) }, label = { Text("$n") })
                }
                FilterChip(selected = count == 0, onClick = { onCount(0) }, label = { Text("All (${learned.size})") })
            }

            Text(
                "A wrong answer marks the letter hard (a red dot in the letter table); a right answer clears it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStart, enabled = learned.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Start exam")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamQuestionScreen(
    run: ExamRun,
    glyphs: Map<Char, Glyph>,
    speaker: Speaker,
    onQuit: () -> Unit,
) {
    val question = run.current
    val letter = question.letter
    val prompts = remember(run.script) { Exam.prompts(run.script) }
    val strokes = remember(letter.text) { placeStrokes(letter.text.mapNotNull { glyphs[it] }) }
    val cs = MaterialTheme.colorScheme
    val good = if (cs.surface.luminance() < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onQuit) { Icon(Icons.Filled.Close, contentDescription = "Stop the exam") }
                },
                title = { Text("Question ${run.index + 1} of ${run.questions.size}") },
                actions = {
                    Text("${run.score} right", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { run.index.toFloat() / run.questions.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (question.type == QuestionType.PICK) "Which one is" else "Write",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Text(question.prompt, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    }
                    val say = letter.say
                    if (say != null && speaker.canSpeak(run.script)) {
                        IconButton(onClick = { speaker.speak(run.script, say) }) {
                            Icon(AppIcons.VolumeUp, contentDescription = "Hear it")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (question.type == QuestionType.PICK) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    question.choices.chunked(2).forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { choice ->
                                val isAnswer = choice == letter
                                val (container, border) = when {
                                    !run.revealed -> cs.surfaceContainerHigh to null
                                    isAnswer -> good.copy(alpha = 0.18f) to BorderStroke(3.dp, good)
                                    choice == run.chosen -> cs.error.copy(alpha = 0.15f) to BorderStroke(3.dp, cs.error)
                                    else -> cs.surfaceContainerHigh to null
                                }
                                Surface(
                                    onClick = { run.pick(choice) },
                                    enabled = !run.revealed,
                                    shape = RoundedCornerShape(16.dp),
                                    color = container,
                                    border = border,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(choice.text, fontSize = if (choice.text.length > 1) 44.sp else 56.sp, color = cs.onSurface)
                                        if (run.revealed) {
                                            Text(
                                                prompts[choice.text].orEmpty(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = cs.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (run.revealed) {
                        val right = run.chosen == letter
                        Text(
                            if (right) "Right!" else "Not quite: it is ${letter.text}. Marked as hard.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (right) good else cs.error,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = run::next) { Text(if (run.index == run.questions.lastIndex) "See results" else "Next") }
                    } else {
                        Text(
                            "Tap the letter that matches.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                WritingPad(
                    strokes, letter.text.length, run.script.grid, run.ink,
                    showGuide = run.revealed, showHint = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (!run.revealed) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { run.ink.removeLastOrNull() }, enabled = run.ink.isNotEmpty()) {
                            Icon(AppIcons.Undo, contentDescription = "Undo last stroke")
                        }
                        IconButton(onClick = { run.ink.clear() }, enabled = run.ink.isNotEmpty()) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear")
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = run::reveal) { Text("Show answer") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(letter.text, fontSize = 40.sp, modifier = Modifier.widthIn(min = 56.dp))
                        Spacer(Modifier.width(8.dp))
                        StepStrip(strokes, letter.text.length, demo = null, modifier = Modifier.weight(1f))
                    }
                    Text(
                        "Compare with the faint letter on the pad and its stroke order. \"Not yet learned\" keeps " +
                            "the letter's ✓ and marks it hard, so it comes up more often.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = { run.selfEvaluate(false) }, modifier = Modifier.weight(1f)) {
                            Text("Not yet learned")
                        }
                        Button(onClick = { run.selfEvaluate(true) }, modifier = Modifier.weight(1f)) {
                            Text("I knew it")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultsScreen(
    run: ExamRun,
    onPractise: (Letter) -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val prompts = remember(run.script) { Exam.prompts(run.script) }
    val total = run.questions.size
    val percent = if (total == 0) 0 else run.score * 100 / total
    val missed = run.missed
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Done") }
                },
                title = { Text("Exam results · ${run.script.title}") },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${run.score} / $total", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium)
            Text(
                when {
                    percent == 100 -> "Perfect: every letter right."
                    percent >= 80 -> "$percent% — well done."
                    percent >= 50 -> "$percent% — getting there."
                    else -> "$percent% — keep practising."
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (missed.isNotEmpty()) {
                Text(
                    "Marked as hard (red dot in the table). Tap one to practise it:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                missed.forEach { l ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPractise(l) },
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            HardDot()
                            Spacer(Modifier.width(12.dp))
                            Text(l.text, fontSize = 32.sp, modifier = Modifier.widthIn(min = 56.dp))
                            Text(prompts[l.text].orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Practise ${l.text}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
                Button(onClick = onAgain, modifier = Modifier.weight(1f)) { Text("New exam") }
            }
        }
    }
}

/** The red "hard letter" marker used in the letter table and elsewhere. */
@Composable
fun HardDot(modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).background(hardColor(), CircleShape))
}

@Composable
fun hardColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFFF6B6B) else Color(0xFFD62828)
