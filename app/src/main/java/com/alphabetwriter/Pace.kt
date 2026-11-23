package com.alphabetwriter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * Spaces out photo requests to Wikimedia so the app is a polite, light client: never more than [PER_HOUR]
 * requests in any hour (counted across app restarts, in a small log file), and a random pause before each one,
 * longer for "Download all" than while browsing. Requests wait their turn in order; a request whose screen
 * went away while it waited (cancelled) is never sent.
 */
class Pace(
    private val log: File?,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val mutex = Mutex()
    private var recent: ArrayDeque<Long>? = null // request times within the last hour, oldest first
    private var last = 0L

    /** Suspends until the next request may be sent, then records it. [bulk]: part of "Download all". */
    suspend fun await(bulk: Boolean) = mutex.withLock {
        val times = recent ?: load().also { recent = it }
        val gap = if (bulk) random.nextLong(BULK_GAP_MS.first, BULK_GAP_MS.last) else random.nextLong(BROWSE_GAP_MS.first, BROWSE_GAP_MS.last)
        while (true) {
            val t = now()
            while (times.isNotEmpty() && t - times.first() >= HOUR_MS) times.removeFirst()
            val untilGap = last + gap - t
            val untilHourFrees = if (times.size >= PER_HOUR) times.first() + HOUR_MS - t else 0L
            val wait = maxOf(untilGap, untilHourFrees)
            if (wait <= 0) break
            delay(wait)
        }
        val t = now()
        times.addLast(t)
        last = t
        record(t)
    }

    /** Requests sent in the last hour. */
    suspend fun sentLastHour(): Int = mutex.withLock {
        val times = recent ?: load().also { recent = it }
        val t = now()
        times.count { t - it < HOUR_MS }
    }

    private suspend fun load(): ArrayDeque<Long> = withContext(Dispatchers.IO) {
        val t = now()
        val times = log?.takeIf { it.exists() }?.let { f ->
            runCatching { f.readLines().mapNotNull { it.toLongOrNull() }.filter { t - it in 0 until HOUR_MS }.sorted() }.getOrNull()
        }.orEmpty()
        // Keep the file small: rewrite it with the last hour only.
        log?.let { f -> runCatching { f.writeText(times.joinToString("") { "$it\n" }) } }
        ArrayDeque(times)
    }

    private suspend fun record(t: Long) {
        val f = log ?: return
        withContext(Dispatchers.IO) { runCatching { f.appendText("$t\n") } }
    }

    companion object {
        const val PER_HOUR = 1500
        const val HOUR_MS = 3_600_000L
        /** Pause before a request while browsing: like a person scrolling through pictures. */
        val BROWSE_GAP_MS = 400L..1_500L
        /** Pause before each photo of "Download all": about 850 an hour on average. */
        val BULK_GAP_MS = 2_500L..6_000L

        @Volatile private var shared: Pace? = null

        /** The app's one pace, logged in the app's files (not in the pictures folder, so never packed). */
        fun get(context: Context): Pace = shared ?: synchronized(this) {
            shared ?: Pace(File(context.applicationContext.filesDir, "photo-requests.log")).also { shared = it }
        }
    }
}
