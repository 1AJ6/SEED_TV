/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.anilist

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Token-bucket limiter for the AniList GraphQL endpoint (design doc §6.2).
 * Nominal limit is 90 req/min but AniList currently runs degraded at 30/min,
 * so we default conservatively to 25/min and clamp dynamically from the
 * X-RateLimit-Remaining / Retry-After response headers.
 */
@Singleton
class AniListRateLimiter @Inject constructor() {

    private val mutex = Mutex()
    private var capacityPerMinute = DEFAULT_PER_MINUTE
    private var tokens = DEFAULT_PER_MINUTE.toDouble()
    private var lastRefillNanos = System.nanoTime()
    private var blockedUntilMillis = 0L

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        return block()
    }

    private suspend fun acquire() {
        while (true) {
            val waitMs = mutex.withLock {
                refill()
                val now = System.currentTimeMillis()
                when {
                    now < blockedUntilMillis -> blockedUntilMillis - now
                    tokens >= 1.0 -> { tokens -= 1.0; return }
                    else -> millisUntilNextToken()
                }
            }
            delay(waitMs)
        }
    }

    /** Called by the client after every response. */
    suspend fun onResponse(remaining: Int?, retryAfterSeconds: Long?) = mutex.withLock {
        if (retryAfterSeconds != null) {
            blockedUntilMillis = System.currentTimeMillis() + retryAfterSeconds * 1000
        }
        if (remaining != null && remaining < tokens) {
            // Server says we have less headroom than we thought — trust it.
            tokens = remaining.toDouble()
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedMinutes = (now - lastRefillNanos) / 60_000_000_000.0
        lastRefillNanos = now
        tokens = (tokens + elapsedMinutes * capacityPerMinute)
            .coerceAtMost(capacityPerMinute.toDouble())
    }

    private fun millisUntilNextToken(): Long {
        val deficit = 1.0 - tokens
        return (deficit / capacityPerMinute * 60_000).toLong().coerceAtLeast(50)
    }

    private companion object {
        const val DEFAULT_PER_MINUTE = 25
    }
}
