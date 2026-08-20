/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.anilist

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the OAuth implicit-grant callback deep link:
 *   seedtv://anilist-callback#access_token=...&token_type=Bearer&expires_in=...
 * (AniList puts the token in the URI FRAGMENT, not query parameters.)
 */
@Singleton
class AniListLinkHandler @Inject constructor(
    private val authStore: AniListAuthStore,
    private val api: AniListApi,
) {
    fun isCallback(uri: Uri?): Boolean =
        uri != null && uri.scheme == "seedtv" && uri.host == "anilist-callback"

    /** Returns the viewer name on success, null on failure. */
    suspend fun handle(uri: Uri): String? {
        val fragment = uri.fragment ?: uri.encodedQuery ?: return null
        val token = fragment.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "access_token" }
            ?.get(1)
            ?: return null
        authStore.accessToken = token
        val viewer = runCatching { api.fetchViewer(token) }.getOrNull()
        if (viewer != null) {
            authStore.viewerId = viewer.first
            authStore.viewerName = viewer.second
        }
        return viewer?.second ?: "AniList user"
    }
}
