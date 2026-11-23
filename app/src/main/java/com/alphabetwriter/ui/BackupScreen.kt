package com.alphabetwriter.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import com.alphabetwriter.Photo
import com.alphabetwriter.PictureStore
import com.alphabetwriter.Progress
import com.alphabetwriter.ProgressFile
import com.alphabetwriter.ProgressSnapshot
import com.alphabetwriter.ProgressSync

/** Save progress to a cloud file (Google Drive etc. through the system file picker) and restore it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    progress: Progress,
    sync: ProgressSync,
    downloadablePhotos: Collection<Photo>,
    appScope: CoroutineScope,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    var working by remember { mutableStateOf<String?>(null) }
    var pendingPackRestore by remember { mutableStateOf<ProgressSnapshot?>(null) }
    val bulk = PictureStore.bulk
    val importState = PictureStore.importState
    // Recounted every 50 photos of a bulk download, when it ends and after an import (not on every photo:
    // counting means listing thousands of files).
    var statsVersion by remember { mutableStateOf(0) }
    val bulkStep = bulk?.let { it.done / 50 } ?: -1
    val stats by produceState(0 to 0L, bulkStep, statsVersion) { value = withContext(Dispatchers.IO) { PictureStore.stats(context) } }
    var message by remember { mutableStateOf<String?>(null) }
    // An import runs in the app's scope; its outcome is shown here, also when the user left and came back.
    LaunchedEffect(importState) {
        when (importState) {
            is PictureStore.ImportState.Done -> {
                message = "${importState.imported.pictures} pictures added."
                pendingPackRestore = importState.imported.progress
                statsVersion++
                PictureStore.importShown()
            }
            is PictureStore.ImportState.Failed -> {
                message = importState.message
                PictureStore.importShown()
            }
            else -> Unit
        }
    }
    var pendingRestore by remember { mutableStateOf<Pair<ProgressSnapshot, Uri>?>(null) }

    /** Opens the system file picker; some phones (work profiles, Android Go) have none. */
    fun launch(open: () -> Unit) {
        try {
            open()
        } catch (_: ActivityNotFoundException) {
            message = "This phone has no file picker, so progress cannot be saved to a file here."
        }
    }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && !sync.setTarget(uri)) {
            sync.saveOnce(uri) { result ->
                message = if (result.isSuccess) {
                    "Saved once. This storage app does not let the app keep the file updated, so save again " +
                        "after learning more, or choose another place such as Google Drive."
                } else {
                    "Could not save: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }
    val openPack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) PictureStore.startImport(context, uri, appScope)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            sync.read(uri) { result ->
                result.fold(
                    onSuccess = { pendingRestore = it to uri },
                    onFailure = { message = it.message ?: "The file could not be read." },
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Backup, pictures, moving phones") },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Your progress: ${progress.learned.size} learned, ${progress.hard.size} hard.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Card("Keep a copy in the cloud") {
                Text(
                    "Choose where to save a progress file, e.g. in Google Drive (pick Drive in the file picker's menu). " +
                        "The app updates the file every time your progress changes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val name = sync.targetName
                if (sync.target != null) {
                    Text("Saving to: ${name ?: "the chosen file"}", style = MaterialTheme.typography.bodyMedium)
                    sync.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = sync::saveNow) { Text("Save now") }
                        TextButton(onClick = sync::stop) { Text("Stop updating") }
                    }
                    TextButton(onClick = { launch { createFile.launch(ProgressFile.FILE_NAME) } }) {
                        Text("Save to a different file…")
                    }
                } else {
                    Button(onClick = { launch { createFile.launch(ProgressFile.FILE_NAME) } }) { Text("Save to a file…") }
                }
            }

            Card("Restore") {
                Text(
                    "After reinstalling the app, open the saved file to bring your progress back. " +
                        "The app then keeps that file up to date again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { launch { openFile.launch(arrayOf("text/*", "application/octet-stream")) } }) {
                    Text("Restore from a file…")
                }
            }

            Card("Pictures on this phone") {
                Text(
                    "${stats.first} pictures downloaded (${stats.second / (1024 * 1024)} MB). Pictures download by themselves " +
                        "the first time a word is shown and stay on the phone. Download them all to have every picture offline.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (bulk != null) {
                    val total = bulk.total
                    if (total == null || total == 0) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { bulk.done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                total == null -> "Checking which pictures are missing…"
                                bulk.failed > 0 -> "${bulk.done} of $total · ${bulk.failed} failed"
                                else -> "${bulk.done} of $total"
                            },
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = PictureStore::stopDownloadAll) { Text("Stop") }
                    }
                } else {
                    PictureStore.bulkResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { PictureStore.downloadAll(context, downloadablePhotos, appScope) }) { Text("Download all pictures") }
                }
            }

            Card("Moving to another phone") {
                Text(
                    "Send the app, and a file with your pictures and progress, to the other phone (Quick Share, Bluetooth, " +
                        "Drive…). There, install the app and open the file with \"Import pictures and progress\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = working == null, onClick = {
                        working = "Preparing the app…"
                        uiScope.launch {
                            val apk = runCatching { PictureStore.packApp(context) }
                            working = null
                            apk.onSuccess { PictureStore.share(context, it, "application/vnd.android.package-archive", "Send ABC") }
                                .onFailure { message = "Could not prepare the app: ${it.message}" }
                        }
                    }) { Text("Share the app") }
                    Button(enabled = working == null, onClick = {
                        working = "Packing pictures and progress…"
                        uiScope.launch {
                            val pack = runCatching { PictureStore.pack(context, ProgressFile.write(progress.snapshot())) }
                            working = null
                            pack.onSuccess { PictureStore.share(context, it, "application/zip", "Send pictures and progress") }
                                .onFailure { message = "Could not pack the pictures: ${it.message}" }
                        }
                    }) { Text("Share pictures") }
                }
                val importing = importState == PictureStore.ImportState.Running
                OutlinedButton(enabled = working == null && !importing, onClick = { launch { openPack.launch(arrayOf("application/zip", "application/octet-stream")) } }) {
                    Text("Import pictures and progress…")
                }
                (working ?: "Importing…".takeIf { importing })?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            Card("Phone backup") {
                Text(
                    "If Google backup is on (Samsung: Settings → Accounts and backup → Back up data under Google Drive), " +
                        "Android also backs up this app about once a day and restores it when the app is installed again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    pendingPackRestore?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pendingPackRestore = null },
            title = { Text("Use the progress from the file?") },
            text = {
                Text(
                    "The file also has progress: ${snapshot.learned.size} learned and ${snapshot.hard.size} hard. " +
                        "Using it replaces the progress on this phone (${progress.learned.size} learned).",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    progress.restore(snapshot)
                    pendingPackRestore = null
                }) { Text("Use it") }
            },
            dismissButton = { TextButton(onClick = { pendingPackRestore = null }) { Text("Keep mine") } },
        )
    }
    pendingRestore?.let { (snapshot, uri) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore progress?") },
            text = {
                Text(
                    "The file has ${snapshot.learned.size} learned and ${snapshot.hard.size} hard letters. " +
                        "This replaces your current progress (${progress.learned.size} learned, ${progress.hard.size} hard).",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Switch to the restored file first, so the file kept until now is not overwritten
                    // with the restored progress; the restore itself then saves into the new file.
                    val switched = sync.setTarget(uri, saveNow = false)
                    progress.restore(snapshot)
                    if (!switched) {
                        message = if (sync.target != null) {
                            "Restored. Your progress is still saved to ${sync.targetName ?: "the file chosen before"}."
                        } else {
                            "Restored. To keep a copy updated, use \"Save to a file\" as well."
                        }
                    }
                    pendingRestore = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            text = { Text(it) },
        )
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
