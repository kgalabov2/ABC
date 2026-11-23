package com.alphabetwriter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Loads the stroke assets once per process. */
object GlyphStore {
    @Volatile private var glyphs: Map<Char, Glyph>? = null

    fun get(context: Context): Map<Char, Glyph> = glyphs ?: synchronized(this) {
        glyphs ?: Script.entries.map { it.strokeAsset }.distinct()
            .flatMap { name ->
                context.assets.open(name).bufferedReader().useLines { StrokeData.parse(it).entries.toList() }
            }
            .associate { it.key to it.value }
            .also { glyphs = it }
    }
}

/** Loads the word lists and the photo credits once per process. */
object WordStore {
    private val words = java.util.concurrent.ConcurrentHashMap<Language, List<Word>>()
    @Volatile private var photos: Map<String, Photo>? = null

    /**
     * The Essentials plus, when the app was built with them, the five level lists of [language]. A word that is
     * in both keeps its Essentials entry (one progress key, one card). A level word whose photo is not in the
     * photo list shows no picture rather than a broken one.
     */
    fun words(context: Context, language: Language): List<Word> = words.getOrPut(language) {
        val essentials = context.assets.open(language.asset).bufferedReader().useLines { WordData.parse(language, it) }
        val levels = try {
            context.assets.open("vocab_${language.code}.tsv").bufferedReader().useLines { WordData.parseLevels(language, it) }
        } catch (_: java.io.IOException) {
            emptyList()
        }
        val photos = photos(context)
        (essentials + levels).distinctBy { it.key }
            .map { if (it.image.startsWith("p:") && it.image !in photos) it.copy(image = "") else it }
    }

    /** Picture key → photo: the bundled photos (keyed by emoji) and the downloadable ones (keyed "p:…"). */
    fun photos(context: Context): Map<String, Photo> = photos ?: synchronized(this) {
        photos ?: (read(context, "pictures/credits.tsv", PhotoData::parse) + read(context, "photos_remote.tsv", PhotoData::parseRemote))
            .also { photos = it }
    }

    private fun read(context: Context, asset: String, parse: (Sequence<String>) -> Map<String, Photo>): Map<String, Photo> = try {
        context.assets.open(asset).bufferedReader().useLines(parse)
    } catch (_: java.io.IOException) {
        emptyMap()
    }
}

/**
 * Which letters the user has marked as learned, and which are "hard": missed in an exam (or marked by hand)
 * and not answered correctly since. Texts are unique across all scripts, so they are used as keys.
 */
class Progress(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    /** Letter text → times missed since its last correct answer. Only hard letters have an entry. */
    private val examPrefs = context.getSharedPreferences("exam_misses", Context.MODE_PRIVATE)
    /** Word key → "interval,ease,due day" of its review schedule. */
    private val reviewPrefs = context.getSharedPreferences("reviews", Context.MODE_PRIVATE)

    /** Called on the main thread after every change, e.g. to update the exported progress file. */
    var onChange: (() -> Unit)? = null

    var learned by mutableStateOf(prefs.getStringSet(KEY, null)?.toSet() ?: emptySet())
        private set

    var hard by mutableStateOf(examPrefs.all.filterValues { (it as? Int ?: 0) > 0 }.keys.toSet())
        private set

    // Letters are keyed by their text, words by Word.key ("w:ja:いぬ"), so the two never clash.
    fun isLearned(letter: Letter) = isLearned(letter.text)

    fun isLearned(key: String) = key in learned

    fun isHard(letter: Letter) = isHard(letter.text)

    fun isHard(key: String) = key in hard

    fun misses(letter: Letter): Int = misses(letter.text)

    fun misses(key: String): Int = examPrefs.getInt(key, 0)

    fun toggle(letter: Letter) = toggle(letter.text)

    fun toggle(key: String) {
        learned = if (key in learned) learned - key else learned + key
        prefs.edit { putStringSet(KEY, learned) }
        onChange?.invoke()
    }

    /** Marks or unmarks a letter (or word) as hard by hand. */
    fun toggleHard(letter: Letter) = toggleHard(letter.text)

    fun toggleHard(key: String) {
        setMisses(key, if (isHard(key)) 0 else 1)
    }

    /** A wrong answer marks the letter (or word) hard; a right one clears that. */
    fun recordAnswer(letter: Letter, correct: Boolean) = recordAnswer(letter.text, correct)

    fun recordAnswer(key: String, correct: Boolean) {
        setMisses(key, if (correct) 0 else misses(key) + 1)
    }

    /** Review schedule of learned words, by word key. */
    var reviews by mutableStateOf(
        reviewPrefs.all.mapNotNull { (key, value) -> parseReview(value as? String)?.let { key to it } }.toMap(),
    )
        private set

    fun reviewState(key: String): ReviewState? = reviews[key]

    /** Records a review answer on [today] and schedules the word's next review. */
    fun recordReview(key: String, grade: Grade, today: Long) {
        val next = ReviewSchedule.next(reviews[key], grade, today)
        reviews = reviews + (key to next)
        reviewPrefs.edit { putString(key, formatReview(next)) }
        onChange?.invoke()
    }

    fun snapshot() = ProgressSnapshot(learned, hard.associateWith { examPrefs.getInt(it, 1) }, reviews)

    /** Replaces all progress with [snapshot] (from an imported file). */
    fun restore(snapshot: ProgressSnapshot) {
        learned = snapshot.learned
        prefs.edit { putStringSet(KEY, learned) }
        examPrefs.edit {
            clear()
            snapshot.hard.forEach { (text, misses) -> putInt(text, misses) }
        }
        hard = snapshot.hard.keys.toSet()
        reviews = snapshot.reviews
        reviewPrefs.edit {
            clear()
            snapshot.reviews.forEach { (key, r) -> putString(key, formatReview(r)) }
        }
        onChange?.invoke()
    }

    private fun formatReview(r: ReviewState) = "${r.intervalDays},${r.ease},${r.dueDay}"

    private fun parseReview(value: String?): ReviewState? {
        val parts = value?.split(',') ?: return null
        if (parts.size != 3) return null
        return ReviewState(parts[0].toIntOrNull() ?: return null, parts[1].toFloatOrNull() ?: return null, parts[2].toLongOrNull() ?: return null)
    }

    private fun setMisses(text: String, misses: Int) {
        if (misses == examPrefs.getInt(text, 0)) return // nothing changed: no save, no cloud upload
        if (misses > 0) {
            examPrefs.edit { putInt(text, misses) }
            hard = hard + text
        } else {
            examPrefs.edit { remove(text) }
            hard = hard - text
        }
        onChange?.invoke()
    }

    private companion object {
        const val KEY = "learned"
    }
}

/**
 * Keeps a copy of the progress in a file the user picked through Android's file picker, which offers
 * Google Drive and other cloud storage apps. The file is rewritten after every change, so a reinstalled
 * app can read it back. Needs no account set-up or network permission: the storage app does the upload.
 */
class ProgressSync(context: Context, private val progress: Progress) {
    private val app = context.applicationContext
    /** Excluded from Android's own backup (see res/xml): a file permission does not survive a reinstall. */
    private val prefs = app.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    /** A burst of changes (a review session, a restore) becomes one write, shortly after the last change. */
    private val pendingSave = Runnable(::saveNow)
    private var savePending = false

    var target by mutableStateOf<Uri?>(null)
        private set
    var targetName by mutableStateOf<String?>(null)
        private set
    /** Result of the last save, for the backup screen. */
    var status by mutableStateOf<String?>(null)
        private set
    /** True while the cloud copy is out of date because the last save failed; the home screen warns about it. */
    var failing by mutableStateOf(false)
        private set

    init {
        // Keep the stored file only while the app still holds write access to it (lost e.g. when the user
        // revoked it, or the file was deleted).
        val stored = prefs.getString(KEY_URI, null)?.let(Uri::parse)
        val stillWritable = stored != null && app.contentResolver.persistedUriPermissions.any {
            it.uri == stored && it.isWritePermission
        }
        if (stillWritable) {
            target = stored
            targetName = prefs.getString(KEY_NAME, null)
        } else if (stored != null) {
            prefs.edit { clear() }
        }
        progress.onChange = ::save
    }

    /**
     * Starts keeping [uri] up to date instead of the previous file. Returns false, and changes nothing, when
     * the storage app does not give lasting write access. [saveNow] is false when the caller is about to
     * replace the progress anyway (restore), so the file is not first overwritten with the old progress.
     */
    fun setTarget(uri: Uri, saveNow: Boolean = true): Boolean {
        try {
            app.contentResolver.takePersistableUriPermission(uri, READ_WRITE)
        } catch (_: SecurityException) {
            return false
        }
        target?.takeIf { it != uri }?.let(::release)
        val name = displayName(uri)
        target = uri
        targetName = name
        failing = false
        status = null
        prefs.edit {
            putString(KEY_URI, uri.toString())
            putString(KEY_NAME, name)
        }
        if (saveNow) saveNow()
        return true
    }

    /** Writes the progress to [uri] once, for storage apps that do not allow keeping it updated. */
    fun saveOnce(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        val snapshot = progress.snapshot()
        io.execute {
            val result = runCatching { write(uri, ProgressFile.write(snapshot)) }
            main.post { onResult(result) }
        }
    }

    private fun release(uri: Uri) {
        try {
            app.contentResolver.releasePersistableUriPermission(uri, READ_WRITE)
        } catch (_: SecurityException) {
            // Already gone.
        }
    }

    fun stop() {
        savePending = false
        main.removeCallbacks(pendingSave)
        target?.let(::release)
        target = null
        targetName = null
        status = null
        failing = false
        prefs.edit { clear() }
    }

    /** Writes the current progress to the chosen file soon, off the main thread. */
    fun save() {
        savePending = true
        main.removeCallbacks(pendingSave)
        main.postDelayed(pendingSave, SAVE_DELAY_MS)
    }

    /** Writes a save that is still waiting, e.g. when the app goes to the background. */
    fun flush() {
        if (savePending) saveNow()
    }

    /** Writes the current progress to the chosen file now, off the main thread. */
    fun saveNow() {
        savePending = false
        main.removeCallbacks(pendingSave)
        val uri = target ?: return
        val snapshot = progress.snapshot()
        io.execute {
            val result = runCatching { write(uri, ProgressFile.write(snapshot)) }
            main.post {
                if (uri != target) return@post // the user switched files meanwhile
                failing = result.isFailure
                status = result.fold(
                    onSuccess = { "Saved at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}" },
                    onFailure = { "Could not save: ${it.message ?: it.javaClass.simpleName}" },
                )
            }
        }
    }

    /** Reads a progress file off the main thread and hands the result back on the main thread. */
    fun read(uri: Uri, onResult: (Result<ProgressSnapshot>) -> Unit) {
        io.execute {
            val result = runCatching {
                val text = app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("The file could not be opened.")
                ProgressFile.read(text)
            }
            main.post { onResult(result) }
        }
    }

    private fun write(uri: Uri, text: String) {
        val bytes = text.toByteArray()
        // "wt" truncates; not every storage app supports it, and plain "w" may leave old bytes behind,
        // which the file format's closing "end" line makes harmless.
        val out = try {
            app.contentResolver.openOutputStream(uri, "wt")
        } catch (_: Exception) {
            null
        } ?: app.contentResolver.openOutputStream(uri, "w") ?: error("The file could not be opened for writing.")
        out.use { it.write(bytes) }
    }

    private fun displayName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val SAVE_DELAY_MS = 1_000L
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/**
 * Reads letters and words aloud with the phone's text-to-speech engine.
 *
 * The engine runs in another app and can be restarted by the system (low memory, updates); the old connection
 * then fails every call. So a failed call reconnects and retries once, and a language counts as missing only
 * when a live engine says so.
 */
class Speaker(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initializing = false
    private var generation = 0
    /** A speak request waiting for the engine to (re)connect. */
    private var pending: (() -> Unit)? = null

    /** Language tags the engine has no voice for. */
    var missing by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        connect()
    }

    private fun connect() {
        val gen = ++generation
        ready = false
        initializing = true
        tts?.shutdown()
        tts = null
        // onInit can run synchronously inside the constructor when binding fails, before `tts` is assigned.
        val engine = TextToSpeech(appContext) { status -> if (gen == generation) onInit(status) }
        if (gen == generation) tts = engine
    }

    private fun onInit(status: Int) {
        initializing = false
        ready = status == TextToSpeech.SUCCESS
        val request = pending
        pending = null
        if (!ready) return
        refresh()
        request?.invoke()
    }

    /** True while the engine answers calls; stop() fails only when the connection is gone. */
    private fun connected(engine: TextToSpeech) = engine.stop() != TextToSpeech.ERROR

    /** Re-checks the installed voices, e.g. after the user comes back from the voice settings. */
    fun refresh() {
        val engine = tts ?: return
        if (!ready) return
        if (!connected(engine)) {
            connect()
            return
        }
        missing = Script.entries.map { it.languageTag }.distinct().filter { tag ->
            val result = engine.isLanguageAvailable(Locale.forLanguageTag(tag))
            result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
        }.toSet()
    }

    /** False only when the engine is known to have no voice for the script's language. */
    fun canSpeak(script: Script) = canSpeak(script.languageTag)

    fun canSpeak(languageTag: String) = languageTag !in missing

    fun speak(script: Script, text: String, slow: Boolean = false) = speak(script.languageTag, text, slow)

    /** Reads [text] in the language [languageTag] (e.g. "ja-JP"). */
    fun speak(languageTag: String, text: String, slow: Boolean = false) = speak(languageTag, text, slow, retry = true)

    private fun speak(languageTag: String, text: String, slow: Boolean, retry: Boolean) {
        val engine = tts
        if (engine == null || !ready) {
            pending = { speak(languageTag, text, slow, retry = false) }
            if (!initializing) connect() // never connected, or the last attempt failed
            return
        }
        val locale = Locale.forLanguageTag(languageTag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (!connected(engine)) {
                if (retry) reconnectAndThen { speak(languageTag, text, slow, retry = false) }
                return
            }
            missing = missing + languageTag
            Toast.makeText(
                appContext,
                "No ${locale.getDisplayLanguage(Locale.ENGLISH)} voice on this phone. Install one in the phone's text-to-speech settings.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        engine.setSpeechRate(if (slow) 0.5f else 0.9f)
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "letter") == TextToSpeech.ERROR && retry) {
            reconnectAndThen { speak(languageTag, text, slow, retry = false) }
        }
    }

    private fun reconnectAndThen(request: () -> Unit) {
        pending = request
        connect()
    }

    fun shutdown() {
        generation++
        pending = null
        tts?.shutdown()
        tts = null
    }

    companion object {
        /** Opens the phone's text-to-speech settings, where voices for more languages can be downloaded. */
        fun openVoiceSettings(context: Context) {
            val candidates = listOf(
                Intent("com.android.settings.TTS_SETTINGS"),
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
                Intent(Settings.ACTION_SETTINGS),
            )
            for (intent in candidates) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (_: ActivityNotFoundException) {
                    // Try the next, more general screen.
                }
            }
        }
    }
}
