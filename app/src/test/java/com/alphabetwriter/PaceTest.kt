package com.alphabetwriter

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PaceTest {
    @Test
    fun browsingRequestsArePausedAndCappedAtFifteenHundredAnHour() = runTest {
        val pace = Pace(log = null, now = { testScheduler.currentTime }, random = Random(1))
        val times = mutableListOf<Long>()
        repeat(Pace.PER_HOUR + 1) {
            pace.await(bulk = false)
            times += testScheduler.currentTime
        }
        // A pause of 0.4–1.5 s before every request.
        assertTrue(times.zipWithNext().all { (a, b) -> b - a >= Pace.BROWSE_GAP_MS.first })
        // 1500 requests fit in well under an hour; the next one waits until the first is an hour old.
        assertTrue(times[Pace.PER_HOUR - 1] - times[0] < Pace.HOUR_MS)
        assertTrue(times[Pace.PER_HOUR] - times[0] >= Pace.HOUR_MS)
        assertEquals(Pace.PER_HOUR, pace.sentLastHour())
    }

    @Test
    fun downloadAllWaitsLongerBetweenPhotos() = runTest {
        val pace = Pace(log = null, now = { testScheduler.currentTime }, random = Random(2))
        val times = mutableListOf<Long>()
        repeat(50) {
            pace.await(bulk = true)
            times += testScheduler.currentTime
        }
        val gaps = times.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it in Pace.BULK_GAP_MS })
        // On average well below the hourly cap.
        assertTrue(Pace.HOUR_MS / gaps.average() < Pace.PER_HOUR)
    }
}
