/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.common

/** Lightweight result wrapper used across repository boundaries. */
sealed interface SResult<out T> {
    data class Success<T>(val data: T) : SResult<T>
    data class Error(val error: AppError, val cause: Throwable? = null) : SResult<Nothing>
    data object Loading : SResult<Nothing>
}

enum class AppError {
    NETWORK,
    UNAUTHORIZED,          // 401 → session invalidated, re-auth required
    SERVER_UNSUPPORTED,    // Jellyfin < 10.11
    SERVER_UNREACHABLE,
    RATE_LIMITED,          // AniList Retry-After in effect
    NOT_FOUND,
    UNKNOWN,
}

inline fun <T, R> SResult<T>.map(transform: (T) -> R): SResult<R> = when (this) {
    is SResult.Success -> SResult.Success(transform(data))
    is SResult.Error -> this
    SResult.Loading -> SResult.Loading
}
