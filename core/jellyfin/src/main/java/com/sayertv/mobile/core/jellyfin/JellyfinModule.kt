/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.util.UUID

@Module
@InstallIn(SingletonComponent::class)
object JellyfinModule {

    @Provides @Singleton
    fun jellyfin(@ApplicationContext context: Context): Jellyfin = createJellyfin {
        this.context = context
        clientInfo = ClientInfo(name = "SayerTV Mobile", version = BuildInfo.VERSION)
        deviceInfo = DeviceInfo(
            id = deviceId(context),
            name = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences("sayertv_device", Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString()
            .also { prefs.edit().putString("device_id", it).apply() }
    }
}

object BuildInfo {
    const val VERSION = "0.1.0-alpha24"

    /** Minimum supported server, per Ethan Sayer's decision (design doc §12). */
    const val MIN_SERVER_MAJOR = 10
    const val MIN_SERVER_MINOR = 11
}
