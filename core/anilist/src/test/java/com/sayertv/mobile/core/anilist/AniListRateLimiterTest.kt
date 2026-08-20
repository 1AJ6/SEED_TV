/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.anilist

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListRateLimiterTest {

    @Test
    fun `permits pass through and execute the block`() = runTest {
        val limiter = AniListRateLimiter()
        var calls = 0
        repeat(5) { limiter.withPermit { calls++ } }
        assertEquals(5, calls)
    }

    @Test
    fun `retry-after blocks subsequent acquisitions until deadline`() = runTest {
        val limiter = AniListRateLimiter()
        limiter.onResponse(remaining = null, retryAfterSeconds = 1)
        val start = testScheduler.currentTime
        limiter.withPermit { }
        // virtual time must have advanced by >= the Retry-After window
        assert(testScheduler.currentTime - start >= 1000)
    }
}
