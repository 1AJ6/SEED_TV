/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Access tokens never touch Room — the DB stores an opaque [tokenRef];
 * the actual secret lives in EncryptedSharedPreferences. (Design doc §8/§9)
 *
 * SELF-HEALING: if the keyset can't be decrypted (classic cause: app data
 * restored after reinstall while the Android Keystore master key died with
 * the uninstall), we wipe the secrets file + master key and recreate them
 * instead of crashing. Users just have to log in again.
 */
@Singleton
class EncryptedTokenStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    private fun createPrefs(context: Context): SharedPreferences =
        try {
            build(context)
        } catch (first: Exception) {
            // Undecryptable keyset — wipe and start fresh.
            runCatching { context.deleteSharedPreferences(PREFS_NAME) }
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
            build(context)
        }

    private fun build(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun put(token: String): String {
        val ref = UUID.randomUUID().toString()
        prefs.edit().putString(ref, token).apply()
        return ref
    }

    fun get(ref: String): String? = runCatching { prefs.getString(ref, null) }.getOrNull()

    fun remove(ref: String) {
        runCatching { prefs.edit().remove(ref).apply() }
    }

    private companion object {
        const val PREFS_NAME = "sayertv_secrets"
    }
}
