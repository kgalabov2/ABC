package com.alphabetwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphabetwriter.GrammarPractice
import com.alphabetwriter.Language
import com.alphabetwriter.Lesson
import com.alphabetwriter.LessonBlock
import com.alphabetwriter.LessonQuestion
import com.alphabetwriter.PracticeQuestion
import com.alphabetwriter.Progress
import com.alphabetwriter.Speaker

/** "a **b** c" with b bold. Text with an unmatched ** is shown as written. */
internal fun boldMarkup(text: String, bold: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)): AnnotatedString {
    val parts = text.split("**")
    if (parts.size % 2 == 0) return AnnotatedString(text)
    return buildAnnotatedString {
        parts.forEachIndexed { i, part -> if (i % 2 == 1) withStyle(bold) { append(part) } else append(part) }
    }
}

/** A practice of one lesson: each question once. Finishing it marks the lesson done (80% or more right) or hard. */
@Stable
class GrammarPracticeRun(
    val language: Language,
    val lesson: Lesson,
    val questions: List<PracticeQuestion>,
    private val progress: Progress,
) {
    var index by mutableIntStateOf(0)
        private set
    var chosen by mutableStateOf<String?>(null)
        private set
    private val results = mutableStateListOf<Boolean>()

    val finished: Boolean get() = index >= questions.size
    val current: PracticeQuestion get() = questions[index]
    val revealed: Boolean get() = chosen != null
    val score: Int get() = results.count { it }
    val passed: Boolean get() = GrammarPractice.passed(score, questions.size)
    val missed: List<LessonQuestion> get() = questions.filterIndexed { i, _ -> results.getOrNull(i) == false }.map { it.question }

    /** [question] is the index the tapped option belonged to; a late tap on a replaced option is ignored. */
    fun pick(option: String, question: Int) {
        if (finished || revealed || question != index) return
        chosen = option
        results += option == current.question.answer
    }

    fun next() {
        if (finished || !revealed) return
        index++
        chosen = null
        if (finished) {
            val key = lesson.key(language)
            progress.recordAnswer(key, passed)
            if (passed && !progress.isLearned(key)) progress.toggle(key)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarHomeScreen(
    language: Language,
    onLanguage: (Language) -> Unit,
    lessons: List<Lesson>,
    languages: List<Language>,
    progress: Progress,
    onLesson: (Int) -> Unit,
    onBackup: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val learnedKeys = progress.learned
    val hardKeys = progress.hard
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grammar") },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Backup, pictures and moving phones") },
                                onClick = {
                                    menu = false
                                    onBackup()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { LanguagePicker(language, onLanguage) }
            if (lessons.isEmpty()) {
                item {
                    Text(
                        "There are no grammar lessons for ${language.title} yet." +
                            if (languages.isNotEmpty()) " These languages have them:" else "",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                items(languages, key = { it.code }) { l ->
                    OutlinedButton(onClick = { onLanguage(l) }) { Text("${l.flag}  ${l.nativeTitle} · ${l.title}") }
                }
            } else {
                item {
                    val done = lessons.count { it.key(language) in learnedKeys }
                    Text(
                        "$done of ${lessons.size} lessons done. Read a lesson, then practise it: 80% right marks it done.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(lessons, key = { _, l -> l.id }) { i, lesson ->
                    val key = lesson.key(language)
                    LessonRow(i + 1, lesson, done = key in learnedKeys, hard = key in hardKeys) { onLesson(i) }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(number: Int, lesson: Lesson, done: Boolean, hard: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (done) cs.primaryContainer else cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(Icons.Filled.Check, contentDescription = "Done", tint = cs.primary)
                } else {
                    Text("$number", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${lesson.level} · ${lesson.intro}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hard) HardDot(Modifier.padding(horizontal = 6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    language: Language,
    lesson: Lesson,
    number: Int,
    count: Int,
    progress: Progress,
    speaker: Speaker,
    onPractise: () -> Unit,
    onNext: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val tag = language.languageTag
    val canSpeak = speaker.canSpeak(tag)
    val done = progress.isLearned(lesson.key(language))
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = {
                    Column {
                        Text(lesson.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Lesson $number of $count · ${lesson.level}", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(lesson.intro, style = MaterialTheme.typography.titleMedium)
            lesson.blocks.forEach { block ->
                when (block) {
                    is LessonBlock.Paragraph -> Text(boldMarkup(block.text), style = MaterialTheme.typography.bodyLarge)
                    is LessonBlock.Table -> LessonTable(block)
                    is LessonBlock.Example -> ExampleCard(block, canSpeak) { speaker.speak(tag, block.say.ifBlank { block.text }) }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (done) {
                Text("Done ✓ — you passed this lesson's practice.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (lesson.questions.isNotEmpty()) {
                Button(onClick = onPractise, modifier = Modifier.fillMaxWidth()) {
                    Text((if (done) "Practise again" else "Practise") + " · ${lesson.questions.size} questions")
                }
            }
            if (onNext != null) OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next lesson") }
        }
    }
}

/** A table that keeps each cell on one line; a table wider than the screen scrolls sideways. */
@Composable
private fun LessonTable(table: LessonBlock.Table) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (table.caption.isNotEmpty()) Text(table.caption, style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Row(Modifier.horizontalScroll(rememberScrollState()).clip(shape).border(1.dp, cs.outlineVariant, shape)) {
            // Column by column, each as wide as its widest cell; one-line cells keep the rows level.
            for (c in table.rows.first().indices) {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    table.rows.forEachIndexed { r, row ->
                        val background = when {
                            r == 0 -> cs.surfaceContainerHigh
                            r % 2 == 0 -> cs.surfaceContainer
                            else -> Color.Transparent
                        }
                        Text(
                            boldMarkup(row[c]),
                            style = if (r == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (r == 0) FontWeight.Bold else null,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleCard(example: LessonBlock.Example, canSpeak: Boolean, onSpeak: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(example.text, fontSize = 22.sp)
                if (example.reading.isNotBlank()) {
                    Text(example.reading, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Text(example.meaning, style = MaterialTheme.typography.bodyMedium)
            }
            if (canSpeak) IconButton(onClick = onSpeak) { Icon(AppIcons.VolumeUp, contentDescription = "Hear it") }
        }
    }
}

/** The question's sentence with the gap shown as a line, or, once answered, filled with the answer. */
private fun gapText(question: LessonQuestion, revealed: Boolean, answerStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(question.prompt.substringBefore(LessonQuestion.BLANK))
    if (revealed) withStyle(answerStyle) { append(question.answer) } else append("＿＿＿")
    append(question.prompt.substringAfter(LessonQuestion.BLANK))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarPracticeScreen(run: GrammarPracticeRun, speaker: Speaker, onQuit: () -> Unit) {
    val practice = run.current
    val question = practice.question
    val tag = run.language.languageTag
    val canSpeak = speaker.canSpeak(tag)
    val cs = MaterialTheme.colorScheme
    val good = if (cs.surface.luminance() < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)

    // The whole sentence is said once it is answered (before, it would give the answer away).
    LaunchedEffect(run.index, run.revealed) {
        if (canSpeak && run.revealed) speaker.speak(tag, question.spoken)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onQuit) { Icon(Icons.Filled.Close, contentDescription = "Stop the practice") } },
                title = { Text("Question ${run.index + 1} of ${run.questions.size}") },
                actions = { Text("${run.score} right", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp)) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(progress = { run.index.toFloat() / run.questions.size }, modifier = Modifier.fillMaxWidth())
            Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Fill in the gap", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Text(gapText(question, run.revealed, SpanStyle(color = good, fontWeight = FontWeight.Bold)), fontSize = 24.sp)
                    Text(question.meaning, style = MaterialTheme.typography.bodyMedium)
                }
            }
            practice.options.forEach { option ->
                key(run.index, option) {
                    val border = when {
                        !run.revealed -> null
                        option == question.answer -> BorderStroke(4.dp, good)
                        option == run.chosen -> BorderStroke(4.dp, cs.error)
                        else -> null
                    }
                    val asked = run.index
                    Surface(
                        onClick = { run.pick(option, asked) },
                        enabled = !run.revealed,
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainerHigh,
                        border = border,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            option,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            if (run.revealed) {
                val right = run.chosen == question.answer
                Text(
                    if (right) "Right!" else "Not quite: the answer is ${question.answer}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (right) good else cs.error,
                )
                if (question.why.isNotBlank()) {
                    Text(boldMarkup(question.why), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (canSpeak) {
                        IconButton(onClick = { speaker.speak(tag, question.spoken) }) { Icon(AppIcons.VolumeUp, contentDescription = "Hear the sentence") }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = run::next) { Text(if (run.index == run.questions.lastIndex) "See results" else "Next") }
                }
            } else {
                Text("Tap the answer that fills the gap.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarResultsScreen(run: GrammarPracticeRun, onAgain: () -> Unit, onLesson: () -> Unit, onDone: () -> Unit) {
    val total = run.questions.size
    val percent = if (total == 0) 0 else run.score * 100 / total
    val cs = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onLesson) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the lesson") } },
                title = { Text("Practice results · ${run.lesson.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    percent == 100 -> "Perfect. Lesson done ✓"
                    run.passed -> "$percent% — lesson done ✓"
                    else -> "$percent% — ${GrammarPractice.PASS_PERCENT}% or more marks the lesson done."
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (run.missed.isNotEmpty()) {
                Text("To look at again:", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                run.missed.forEach { q ->
                    Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(gapText(q, revealed = true, SpanStyle(fontWeight = FontWeight.Bold)), fontSize = 20.sp)
                            Text(q.meaning, style = MaterialTheme.typography.bodyMedium)
                            if (q.why.isNotBlank()) Text(boldMarkup(q.why), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onLesson, modifier = Modifier.weight(1f)) { Text("Read the lesson") }
                Button(onClick = onAgain, modifier = Modifier.weight(1f)) { Text("Try again") }
            }
            TextButton(onClick = onDone) { Text("All lessons") }
        }
    }
}
