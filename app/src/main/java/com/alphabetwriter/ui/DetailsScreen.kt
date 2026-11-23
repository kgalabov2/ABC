package com.alphabetwriter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphabetwriter.Alphabets
import com.alphabetwriter.Letter
import com.alphabetwriter.LetterInfos
import com.alphabetwriter.Script
import com.alphabetwriter.Speaker

/** Pronunciation and everything else worth knowing about one letter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    script: Script,
    letter: Letter,
    strokes: List<PlacedStroke>,
    speaker: Speaker,
    onBack: () -> Unit,
    onPractise: () -> Unit,
    onOpen: (Script, String) -> Unit,
) {
    val info = remember(script, letter) { LetterInfos.of(script, letter) }
    val group = remember(script, letter) { Alphabets.groupOf(script, letter) }
    val canSpeak = speaker.canSpeak(script)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("${letter.text}  ·  ${letter.label}") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(letter.text, fontSize = 72.sp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(letter.label, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(script.title, group?.title).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val say = letter.say
            if (say != null) {
                if (canSpeak) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { speaker.speak(script, say) }) {
                            Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Listen")
                        }
                        OutlinedButton(onClick = { speaker.speak(script, say, slow = true) }) { Text("Slowly") }
                    }
                } else {
                    Section("Listening") {
                        Text(
                            "The phone has no ${if (script == Script.GREEK) "Greek" else "Japanese"} voice yet. " +
                                "Install one in the text-to-speech settings (Samsung: Settings → General management → " +
                                "Text-to-speech → engine settings → Install voice data), then come back.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { Speaker.openVoiceSettings(context) }) { Text("Open voice settings") }
                    }
                }
            }

            Section("Pronunciation") {
                info.sounds.forEach { Bullet(it) }
                info.ipa?.let { Labeled("IPA", it) }
                info.cyrillic?.let { Labeled("In Cyrillic", it) }
                info.rhythm?.let { Labeled("Rhythm", it) }
            }

            if (info.examples.isNotEmpty()) {
                Section(if (info.examples.size == 1) "Example" else "Examples") {
                    info.examples.forEach { ex ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex.word, fontSize = 26.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex.reading, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(ex.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (canSpeak) {
                                IconButton(onClick = { speaker.speak(script, ex.word) }) {
                                    Icon(AppIcons.VolumeUp, contentDescription = "Say ${ex.reading}")
                                }
                            }
                        }
                    }
                }
            }

            Section("Writing") {
                Text(
                    (if (strokes.size == 1) "Written in one stroke. " else "${strokes.size} strokes, in this order. ") +
                        "The green square marks where each stroke starts.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                StepStrip(strokes, letter.text.length, demo = null)
                Button(onClick = onPractise) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Practise writing")
                }
            }

            if (info.lookAlikes.isNotEmpty()) {
                Section("Don't confuse with") {
                    info.lookAlikes.forEach { la ->
                        val target = Alphabets.find(script, la.text)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (target != null) Modifier.clickable { onOpen(script, la.text) } else Modifier)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(la.text, fontSize = 32.sp, modifier = Modifier.widthIn(min = 48.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(la.tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (target != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open ${la.text}")
                        }
                    }
                }
            }

            if (info.readingTips.isNotEmpty()) {
                Section("Reading tips") { info.readingTips.forEach { Bullet(it) } }
            }

            info.counterpart?.let { (otherScript, text) ->
                val title = if (otherScript == script) info.counterpartNote ?: "Other form" else "In ${otherScript.title}"
                Section(title) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(otherScript, text) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text, fontSize = 40.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (otherScript != script) info.counterpartNote ?: "Same sound in ${otherScript.title}." else "Same letter.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open $text")
                    }
                }
            }

            info.origin?.let { Section("Origin") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            letter.note?.let { Section("Note") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Labeled(label: String, value: String) {
    Row {
        Text("$label:  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
