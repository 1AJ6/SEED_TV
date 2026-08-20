/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SeedLogo(
    modifier: Modifier = Modifier,
    showText: Boolean = false // Default to false as per Note 2
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "S.E.E.D TV Logo",
            modifier = Modifier.size(56.dp) // Resized to fit top bar (Note 3)
        )
        if (showText) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "S.E.E.D",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Ultraslim app-wide top bar — 24dp of chrome with slightly LARGER text
 * and action icons (tester note: shorter bar, bigger title/icons).
 * Uses raw clickable icons instead of IconButton to escape Material's 48dp
 * minimum-touch-target inflation.
 */
@Composable
fun CompactTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                CompactBarAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(4.dp))
            } else {
                // Show logo if no back button and title is empty (Home)
                if (title.isEmpty()) {
                    SeedLogo(showText = false)
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
            
            if (title.isNotEmpty()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            actions()
        }
    }
}

/** Small 22dp tap target with a 18dp icon — for CompactTopBar action slots. */
@Composable
fun CompactBarAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(24.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
