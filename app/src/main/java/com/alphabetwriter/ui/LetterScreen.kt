package com.alphabetwriter.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphabetwriter.Alphabets
import com.alphabetwriter.Letter
import com.alphabetwriter.LetterInfos
import com.alphabetwriter.Progress
import com.alphabetwriter.Script
import com.alphabetwriter.Speaker

/** The practice screen: stroke-order animation on top, free writing pad below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterScreen(
    script: Script,
    letters: List<Letter>,
    index: Int,
    strokes: List<PlacedStroke>,
    ink: SnapshotStateList<List<Offset>>,
    progress: Progress,
    speaker: Speaker,
    showGuide: Boolean,
    showHint: Boolean,
    onShowGuide: (Boolean) -> Unit,
    onShowHint: (Boolean) -> Unit,
    onOpen: (Int) -> Unit,
    onDetails: () -> Unit,
    onBack: () -> Unit,
) {
    val letter = letters[index]
    val cells = letter.text.length
    val info = remember(script, letter) { LetterInfos.of(script, letter) }
    val group = remember(script, letter) { Alphabets.groupOf(script, letter) }
    val demo = remember(script, letter.text) { DemoState(strokes.size) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(letter.text, fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(letter.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${strokes.size} ${if (strokes.size == 1) "stroke" else "strokes"} · ${group?.title ?: script.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    // Red dot: mark the letter as hard (problematic); exams then ask it more often.
                    IconToggleButton(
                        checked = progress.isHard(letter),
                        onCheckedChange = { progress.toggleHard(letter) },
                        modifier = Modifier.semantics {
                            contentDescription = if (progress.isHard(letter)) "Hard letter: tap to unmark" else "Mark as hard"
                        },
                    ) {
                        if (progress.isHard(letter)) {
                            HardDot(size = 18.dp)
                        } else {
                            Box(
                                Modifier.size(18.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            )
                        }
                    }
                    FilledIconToggleButton(
                        checked = progress.isLearned(letter),
                        onCheckedChange = { progress.toggle(letter) },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Mark as learned")
                    }
                },
            )
        },
        bottomBar = { PrevNextBar(letters, index, onOpen) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            PronunciationBar(script, letter, info.summary, speaker, onDetails)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight
                val demoPanel = @Composable { modifier: Modifier ->
                    // Landscape phones are short: leave out the step thumbnails so the animation stays usable.
                    DemoPanel(strokes, cells, script, demo, showSteps = !wide, modifier)
                }
                val padPanel = @Composable { modifier: Modifier ->
                    PracticePanel(strokes, cells, script, ink, showGuide, showHint, onShowGuide, onShowHint, modifier)
                }
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        demoPanel(Modifier.weight(1f).fillMaxHeight())
                        padPanel(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        demoPanel(Modifier.weight(1f).fillMaxWidth())
                        padPanel(Modifier.weight(1.05f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** One line of pronunciation, a speaker button and the way into the details page. */
@Composable
private fun PronunciationBar(script: Script, letter: Letter, summary: String, speaker: Speaker, onDetails: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            val say = letter.say
            if (say != null && speaker.canSpeak(script)) {
                IconButton(onClick = { speaker.speak(script, say) }) {
                    Icon(AppIcons.VolumeUp, contentDescription = "Pronounce")
                }
            }
            FilledTonalButton(onClick = onDetails, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Details")
            }
        }
    }
}

@Composable
private fun DemoPanel(
    strokes: List<PlacedStroke>,
    cells: Int,
    script: Script,
    demo: DemoState,
    showSteps: Boolean,
    modifier: Modifier,
) {
    Column(modifier) {
        StrokeAnimation(strokes, cells, script.grid, demo, Modifier.weight(1f).fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = demo::replay) { Icon(AppIcons.Replay, contentDescription = "Replay") }
            IconButton(onClick = demo::togglePlay) {
                if (demo.playing) {
                    Icon(AppIcons.Pause, contentDescription = "Pause")
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                }
            }
            TextButton(onClick = demo::cycleSpeed) {
                Text(
                    when (demo.speed) {
                        0.5f -> "Slow"
                        2f -> "Fast"
                        else -> "Normal"
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Stroke ${demo.currentNumber} of ${strokes.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showSteps) StepStrip(strokes, cells, demo)
    }
}

@Composable
private fun PracticePanel(
    strokes: List<PlacedStroke>,
    cells: Int,
    script: Script,
    ink: SnapshotStateList<List<Offset>>,
    showGuide: Boolean,
    showHint: Boolean,
    onShowGuide: (Boolean) -> Unit,
    onShowHint: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Your turn",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FilterChip(selected = showHint, onClick = { onShowHint(!showHint) }, label = { Text("Hint") })
            FilterChip(selected = showGuide, onClick = { onShowGuide(!showGuide) }, label = { Text("Guide") })
            IconButton(onClick = { ink.removeLastOrNull() }, enabled = ink.isNotEmpty()) {
                Icon(AppIcons.Undo, contentDescription = "Undo last stroke")
            }
            IconButton(onClick = { ink.clear() }, enabled = ink.isNotEmpty()) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear")
            }
        }
        WritingPad(strokes, cells, script.grid, ink, showGuide, showHint, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun PrevNextBar(letters: List<Letter>, index: Int, onOpen: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onOpen(index - 1) }, enabled = index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous letter")
                Text(letters.getOrNull(index - 1)?.text.orEmpty(), fontSize = 20.sp)
            }
            Text(
                "${index + 1} / ${letters.size}",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onOpen(index + 1) }, enabled = index < letters.lastIndex) {
                Text(letters.getOrNull(index + 1)?.text.orEmpty(), fontSize = 20.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next letter")
            }
        }
    }
}
