package com.alphabetwriter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.HttpEngine
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Word photos that are not bundled with the app: downloaded from Wikimedia the first time they are shown and
 * kept in the app's own storage for good. They can be packed into one file to move to another phone.
 */
object PictureStore {
    private const val DIR = "pictures"
    private const val PART = ".part"
    private const val USER_AGENT = "AlphabetWriter/1.0 (Android; personal language-learning app)"
    /** At most three downloads at once, so scrolling a grid does not flood the network (or Wikimedia). */
    private val gate = Semaphore(3)

    /**
     * Android's HTTP/2 client (Android 14+). Wikimedia refuses about half of all photo requests made over
     * HTTP/1.1, which is all the classic HttpURLConnection speaks, with "429 Too many requests"; over HTTP/2
     * they all succeed.
     */
    @RequiresApi(34)
    private object Http2 {
        @Volatile private var engine: HttpEngine? = null

        fun open(context: Context, url: String): HttpURLConnection {
            val e = engine ?: synchronized(this) {
                engine ?: HttpEngine.Builder(context.applicationContext).build().also { engine = it }
            }
            return e.openConnection(URL(url)) as HttpURLConnection
        }
    }

    private fun open(context: Context, url: String): HttpURLConnection =
        if (Build.VERSION.SDK_INT >= 34) Http2.open(context, url) else URL(url).openConnection() as HttpURLConnection

    /** "Download all" progress while it runs. [total] is null while the missing photos are being counted. */
    class Bulk(val done: Int, val total: Int?, val failed: Int)

    var bulk by mutableStateOf<Bulk?>(null)
        private set
    /** How the last "Download all" ended, for the backup screen. */
    var bulkResult by mutableStateOf<String?>(null)
        private set
    private var bulkJob: Job? = null

    /** An import of a pictures-and-progress file: running, or its outcome until the backup screen has shown it. */
    sealed interface ImportState {
        data object Running : ImportState
        class Done(val imported: Imported) : ImportState
        class Failed(val message: String) : ImportState
    }

    var importState by mutableStateOf<ImportState?>(null)
        private set

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, photo: Photo): File = File(dir(context), photo.cacheName)

    fun isOnPhone(context: Context, photo: Photo): Boolean = photo.file != null || file(context, photo).length() > 0

    /**
     * The photo's file on the phone, downloading it first if needed; null when offline or the download fails.
     * Every request waits for its turn in [Pace]; [bulk] is true for "Download all".
     */
    suspend fun fetch(context: Context, photo: Photo, bulk: Boolean = false): File? {
        val url = photo.url ?: return null
        val target = file(context, photo)
        if (target.length() > 0) return target
        val pace = Pace.get(context)
        repeat(3) { attempt ->
            pace.await(bulk)
            if (target.length() > 0) return target // fetched meanwhile by another screen or "Download all"
            when (val result = gate.withPermit { withContext(Dispatchers.IO) { download(context, url, target) } }) {
                Download.Done -> return target
                Download.Failed -> return null
                // Asked to slow down: wait as told without holding a download slot (and cancellably), then retry.
                is Download.SlowDown -> if (attempt < 2) delay(result.seconds * 1000) else return null
            }
        }
        return null
    }

    private sealed interface Download {
        data object Done : Download
        data object Failed : Download
        class SlowDown(val seconds: Long) : Download
    }

    /** One download attempt. */
    private fun download(context: Context, url: String, target: File): Download {
        if (target.length() > 0) return Download.Done
        var connection: HttpURLConnection? = null
        // Each download writes its own temporary file, so a grid and "Download all" fetching the same photo
        // at once cannot mix their bytes.
        var tmp: File? = null
        return try {
            connection = open(context, url)
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val part = File.createTempFile("dl-", PART, target.parentFile)
                    tmp = part
                    connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                    if (part.length() > 0 && part.renameTo(target)) Download.Done else Download.Failed
                }
                429, 503 -> Download.SlowDown(connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 60) ?: 5L)
                else -> Download.Failed
            }
        } catch (_: Exception) {
            Download.Failed // offline, timed out, or an address the phone cannot open
        } finally {
            tmp?.takeIf { it.exists() }?.delete()
            connection?.disconnect()
        }
    }

    /**
     * Downloads every photo in [photos] that is not on the phone yet, one after another, in [scope] (so it goes
     * on when the user leaves the screen). Photos that fail are counted and can be tried again later.
     */
    fun downloadAll(context: Context, photos: Collection<Photo>, scope: CoroutineScope) {
        if (bulkJob?.isActive == true) return
        val app = context.applicationContext
        bulkResult = null
        bulk = Bulk(0, null, 0)
        val job = scope.launch(Dispatchers.IO) {
            var done = 0
            var failed = 0
            try {
                // Leftovers of downloads cut short when the app was killed.
                dir(app).listFiles { f -> f.name.endsWith(PART) && f.lastModified() < System.currentTimeMillis() - 3_600_000 }
                    ?.forEach { it.delete() }
                // Checking thousands of files is slow: do it here, not on the main thread.
                val missing = photos.filter { it.url != null && !isOnPhone(app, it) }.distinctBy { it.cacheName }
                bulk = Bulk(0, missing.size, 0)
                for (photo in missing) {
                    ensureActive()
                    if (fetch(app, photo, bulk = true) == null) failed++
                    done++
                    bulk = Bulk(done, missing.size, failed)
                }
                bulkResult = when {
                    missing.isEmpty() -> "Every picture is already on this phone."
                    failed == 0 -> "All ${missing.size} pictures downloaded."
                    else -> "${done - failed} pictures downloaded; $failed could not be (no connection, or no longer on " +
                        "Wikimedia). Try again later."
                }
            } catch (e: CancellationException) {
                bulkResult = "Stopped after ${done - failed} pictures."
                throw e
            } catch (e: Exception) {
                bulkResult = "The download stopped: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                bulk = null
            }
        }
        bulkJob = job
        // Also when the job is cancelled before it starts (and so never runs its finally block).
        job.invokeOnCompletion { if (bulkJob === job) bulk = null }
    }

    fun stopDownloadAll() {
        bulkJob?.cancel()
    }

    /** Imports a file made by [pack] in [scope], so it finishes even if the user leaves the screen. */
    fun startImport(context: Context, uri: Uri, scope: CoroutineScope) {
        if (importState == ImportState.Running) return
        val app = context.applicationContext
        importState = ImportState.Running
        scope.launch {
            importState = try {
                ImportState.Done(unpack(app, uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportState.Failed(e.message ?: "The file could not be read.")
            }
        }
    }

    /** Called once the backup screen has shown the import's outcome. */
    fun importShown() {
        if (importState != ImportState.Running) importState = null
    }

    /** Number and total size of the photos downloaded to this phone. */
    fun stats(context: Context): Pair<Int, Long> {
        val files = dir(context).listFiles { f -> f.isFile && !f.name.endsWith(PART) }.orEmpty()
        return files.size to files.sumOf { it.length() }
    }

    /**
     * Packs the downloaded photos and [progressText] (the progress file) into one zip in the share cache,
     * for moving to another phone.
     */
    suspend fun pack(context: Context, progressText: String): File = withContext(Dispatchers.IO) {
        val out = File(shareDir(context), "abc-pictures-and-progress.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.setLevel(0) // photos are JPEGs already
            zip.putNextEntry(ZipEntry("progress.txt"))
            zip.write(progressText.toByteArray())
            zip.closeEntry()
            dir(context).listFiles { f -> f.isFile && !f.name.endsWith(PART) }.orEmpty().forEach { f ->
                zip.putNextEntry(ZipEntry("pictures/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        out
    }

    /** What an imported pack contained. */
    class Imported(val pictures: Int, val progress: ProgressSnapshot?)

    /** Unpacks a pack made by [pack]: photos not already on the phone, and the progress it carried (if any). */
    suspend fun unpack(context: Context, uri: Uri): Imported = withContext(Dispatchers.IO) {
        var pictures = 0
        var progress: ProgressSnapshot? = null
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("The file could not be opened.")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == "progress.txt" -> progress = runCatching { ProgressFile.read(zip.readBytes().decodeToString()) }.getOrNull()
                    name.startsWith("pictures/") && !entry.isDirectory -> {
                        // Only plain file names: never write outside the pictures folder.
                        val base = name.removePrefix("pictures/")
                        if (base.isNotEmpty() && '/' !in base && base != ".." && base != ".") {
                            val target = File(dir(context), base)
                            if (target.length() == 0L) {
                                val tmp = File(target.parentFile, "$base$PART")
                                tmp.outputStream().use { zip.copyTo(it) }
                                if (tmp.renameTo(target)) pictures++
                            }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        if (pictures == 0 && progress == null) throw IOException("This is not an ABC pictures file.")
        Imported(pictures, progress)
    }

    /** Copies the installed app into the share cache, so it can be sent to another phone. */
    suspend fun packApp(context: Context): File = withContext(Dispatchers.IO) {
        val out = File(shareDir(context), "ABC.apk")
        File(context.applicationInfo.sourceDir).inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }

    /** Opens the phone's share sheet (Quick Share, Bluetooth, Drive, …) for [file]. */
    fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Nothing can receive it.
        }
    }

    private fun shareDir(context: Context) = File(context.cacheDir, "share").apply {
        mkdirs()
        listFiles().orEmpty().forEach { it.delete() } // only the latest shared file is kept
    }
}
