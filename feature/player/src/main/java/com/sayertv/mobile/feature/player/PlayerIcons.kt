/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Minimal custom player icons — we deliberately don't ship the 12MB
 * material-icons-extended pack (30k classes of DEX weight for a handful
 * of glyphs). VLC-style simple geometry.
 */
object PlayerIcons {

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 4f); lineTo(10f, 4f); lineTo(10f, 20f); lineTo(6f, 20f); close()
                moveTo(14f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(14f, 20f); close()
            }
        }.build()
    }

    val LockOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "LockOpen", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                // body
                moveTo(5f, 10f); lineTo(17f, 10f); lineTo(17f, 21f); lineTo(5f, 21f); close()
                // open shackle
                moveTo(8f, 10f); lineTo(8f, 7f)
                curveTo(8f, 4.8f, 9.8f, 3f, 12f, 3f)
                curveTo(14.2f, 3f, 16f, 4.8f, 16f, 7f)
                lineTo(14f, 7f)
                curveTo(14f, 5.9f, 13.1f, 5f, 12f, 5f)
                curveTo(10.9f, 5f, 10f, 5.9f, 10f, 7f)
                lineTo(10f, 10f); close()
            }
        }.build()
    }

    val SkipNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipNext", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 18f); lineTo(14.5f, 12f); lineTo(6f, 6f); close()
                moveTo(16f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(16f, 18f); close()
            }
        }.build()
    }

    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipPrevious", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(18f, 6f); lineTo(9.5f, 12f); lineTo(18f, 18f); close()
                moveTo(6f, 6f); lineTo(8f, 6f); lineTo(8f, 18f); lineTo(6f, 18f); close()
            }
        }.build()
    }
}
