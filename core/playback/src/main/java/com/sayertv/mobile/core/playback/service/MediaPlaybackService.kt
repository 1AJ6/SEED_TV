/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.playback.service

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * M0 placeholder. In M2 this becomes the foreground playback service that
 * bridges PlayerEngine → Media3 MediaSession (notification, headset,
 * Bluetooth controls) per design doc §5.3. Registered in the app manifest.
 */
class MediaPlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = null
}
