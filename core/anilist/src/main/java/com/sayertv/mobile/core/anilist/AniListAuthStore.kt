/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.anilist

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AniList account state: bearer token (long-lived JWT from the implicit
 * grant), plus the user's API client id and viewer identity.
 * Same self-healing pattern as the Jellyfin token store.
 */
@Singleton
class AniListAuthStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    private fun createPrefs(context: Context): SharedPreferences =
        try {
            build(context)
        } catch (first: Exception) {
            runCatching { context.deleteSharedPreferences(PREFS_NAME) }
            build(context)
        }

    private fun build(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var clientId: String?
        get() = runCatching { prefs.getString(KEY_CLIENT_ID, null) }.getOrNull()
        set(value) { prefs.edit().putString(KEY_CLIENT_ID, value).apply() }

    var accessToken: String?
        get() = runCatching { prefs.getString(KEY_TOKEN, null) }.getOrNull()
        set(value) { prefs.edit().putString(KEY_TOKEN, value).apply() }

    var viewerId: Int
        get() = prefs.getInt(KEY_VIEWER_ID, -1)
        set(value) { prefs.edit().putInt(KEY_VIEWER_ID, value).apply() }

    var viewerName: String?
        get() = runCatching { prefs.getString(KEY_VIEWER_NAME, null) }.getOrNull()
        set(value) { prefs.edit().putString(KEY_VIEWER_NAME, value).apply() }

    fun isLinked(): Boolean = !accessToken.isNullOrBlank()

    fun unlink() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_VIEWER_ID).remove(KEY_VIEWER_NAME).apply()
    }

    /** AniList implicit-grant authorize URL (token arrives in the redirect fragment). */
    fun authorizeUrl(): String? =
        clientId?.takeIf { it.isNotBlank() }?.let {
            "https://anilist.co/api/v2/oauth/authorize?client_id=$it&response_type=token"
        }

    private companion object {
        const val PREFS_NAME = "seedtv_anilist"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_VIEWER_ID = "viewer_id"
        const val KEY_VIEWER_NAME = "viewer_name"
    }
}
