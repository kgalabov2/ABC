package com.alphabetwriter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphabetwriter.Alphabets
import com.alphabetwriter.Letter
import com.alphabetwriter.LetterGroup
import com.alphabetwriter.Progress
import com.alphabetwriter.R
import com.alphabetwriter.Script

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    script: Script,
    listState: LazyListState,
    progress: Progress,
    onScript: (Script) -> Unit,
    onOpen: (Letter) -> Unit,
    onExam: () -> Unit,
    onBackup: () -> Unit,
    backupFailing: Boolean,
    bottomBar: @Composable () -> Unit,
) {
    var showAbout by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Back up and restore") },
                                onClick = {
                                    menu = false
                                    onBackup()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    menu = false
                                    showAbout = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onExam,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("Exam") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = script.ordinal, edgePadding = 8.dp) {
                Script.entries.forEach { s ->
                    Tab(
                        selected = s == script,
                        onClick = { onScript(s) },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(s.nativeTitle)
                                Text(s.title, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                    )
                }
            }
            val groups = Alphabets.groups(script)
            val total = groups.sumOf { it.letters.size }
            val learned = groups.sumOf { g -> g.letters.count { progress.isLearned(it) } }
            val hard = groups.sumOf { g -> g.letters.count { progress.isHard(it) } }
            LazyColumn(
                state = listState,
                // Bottom room so the Exam button never covers the last row.
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (backupFailing) {
                    item(key = "backup-warning") {
                        Surface(
                            onClick = onBackup,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Text(
                                "Your progress file could not be updated. Tap to check the backup.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                item(key = "summary") {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$learned of $total learned" + if (hard > 0) "  ·  $hard hard" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (hard > 0) HardDot(Modifier.padding(start = 6.dp))
                    }
                }
                groups.forEach { group ->
                    item(key = "h-${group.title}") { GroupHeader(group) }
                    group.rows.forEachIndexed { i, row ->
                        item(key = "${group.title}-$i") { LetterRow(row, group.columns, progress, onOpen) }
                    }
                }
            }
        }
    }
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
private fun GroupHeader(group: LetterGroup) {
    Column(Modifier.padding(top = 16.dp, bottom = 6.dp)) {
        Text(group.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(group.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LetterRow(row: List<Letter?>, columns: Int, progress: Progress, onOpen: (Letter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0 until columns) {
            Box(Modifier.weight(1f)) {
                row.getOrNull(i)?.let { letter ->
                    LetterCell(letter, progress.isLearned(letter), progress.isHard(letter)) { onOpen(letter) }
                }
            }
        }
    }
}

@Composable
private fun LetterCell(letter: Letter, learned: Boolean, hard: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (learned) cs.primaryContainer else cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        Box {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(letter.text, fontSize = if (letter.text.length > 1) 24.sp else 28.sp, maxLines = 1)
                Text(
                    letter.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (learned) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Learned",
                    tint = cs.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(14.dp),
                )
            }
            if (hard) {
                HardDot(Modifier.align(Alignment.TopEnd).padding(6.dp).semantics { contentDescription = "Hard" })
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("About") },
        text = {
            Text(
                "Open a letter to watch how it is written stroke by stroke, then write it yourself on the pad " +
                    "below. Guide shows the letter faintly behind your writing; Hint shows the next stroke to write. " +
                    "Details explains how the letter is pronounced.\n\n" +
                    "Mark letters you know with ✓; Exam tests those. Letters you get wrong, or mark with the red dot " +
                    "yourself, are hard: they show a red dot and come up more often in exams.\n\n" +
                    "Japanese stroke-order data: KanjiVG, © Ulrich Apel, CC BY-SA 3.0 (kanjivg.tagaini.net).\n" +
                    "Greek stroke data was drawn for this app.\n" +
                    "Word levels: English Wiktionary (via kaikki.org), FrequencyWords (OpenSubtitles 2018) and, for " +
                    "Japanese, JMdict by the Electronic Dictionary Research and Development Group, all CC BY-SA.\n" +
                    "Word photos: lead images of Wikipedia articles from Wikimedia Commons, resized, each under its " +
                    "own licence. A word's card shows its photo's licence and author; tap that line to open the " +
                    "photo's page.\n" +
                    "Animated pictures: Noto Emoji animations by Google, CC BY 4.0 " +
                    "(googlefonts.github.io/noto-emoji-animation), played with Lottie (Apache 2.0).\n" +
                    "Grammar lessons were written for this app. Word forms (conjugation and declension): English " +
                    "Wiktionary via kaikki.org, CC BY-SA.\n\n" +
                    "Pronunciation uses the phone's text-to-speech voices. If the speaker button is missing, " +
                    "install the Japanese or Greek voice in the phone's text-to-speech settings.",
            )
        },
    )
}
