/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** App-custom icons (we don't ship the 12MB extended icon pack). */
object AppIcons {

    /** Torii gate — the anime/AniList entry icon (tinted by Icon). */
    val Torii: ImageVector by lazy {
        ImageVector.Builder(
            name = "Torii", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Top curved lintel
                moveTo(2f, 5.5f)
                curveTo(5f, 4f, 19f, 4f, 22f, 5.5f)
                lineTo(22f, 7.5f)
                curveTo(19f, 6.2f, 5f, 6.2f, 2f, 7.5f)
                close()
                // Second beam
                moveTo(4.5f, 9.5f); lineTo(19.5f, 9.5f); lineTo(19.5f, 11f); lineTo(4.5f, 11f); close()
                // Left pillar
                moveTo(5.5f, 7f); lineTo(7.5f, 7f); lineTo(8f, 21f); lineTo(5f, 21f); close()
                // Right pillar
                moveTo(16.5f, 7f); lineTo(18.5f, 7f); lineTo(19f, 21f); lineTo(16f, 21f); close()
            }
        }.build()
    }

    /** Two viewers side by side — the SyncPlay tab icon. */
    val WatchTogether: ImageVector by lazy {
        ImageVector.Builder(
            name = "WatchTogether", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Left head
                moveTo(8f, 6f)
                curveTo(9.66f, 6f, 11f, 7.34f, 11f, 9f)
                curveTo(11f, 10.66f, 9.66f, 12f, 8f, 12f)
                curveTo(6.34f, 12f, 5f, 10.66f, 5f, 9f)
                curveTo(5f, 7.34f, 6.34f, 6f, 8f, 6f)
                close()
                // Right head
                moveTo(16f, 6f)
                curveTo(17.66f, 6f, 19f, 7.34f, 19f, 9f)
                curveTo(19f, 10.66f, 17.66f, 12f, 16f, 12f)
                curveTo(14.34f, 12f, 13f, 10.66f, 13f, 9f)
                curveTo(13f, 7.34f, 14.34f, 6f, 16f, 6f)
                close()
                // Left body
                moveTo(2f, 18f)
                curveTo(2f, 15.5f, 5f, 14f, 8f, 14f)
                curveTo(9.2f, 14f, 10.4f, 14.25f, 11.4f, 14.7f)
                curveTo(10.05f, 15.5f, 9f, 16.6f, 9f, 18f)
                lineTo(9f, 19f)
                lineTo(2f, 19f)
                close()
                // Right body
                moveTo(11f, 18f)
                curveTo(11f, 15.5f, 14f, 14f, 16f, 14f)
                curveTo(18f, 14f, 22f, 15.5f, 22f, 18f)
                lineTo(22f, 19f)
                lineTo(11f, 19f)
                close()
            }
        }.build()
    }
}
