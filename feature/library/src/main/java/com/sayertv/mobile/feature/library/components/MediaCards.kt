/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.core.jellyfin.model.MediaKind

/** Portrait poster card — library grids, Latest rows, search results. */
@Composable
fun PosterCard(
    item: MediaItem,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val width = if (compact) 90.dp else 120.dp
    Column(modifier = modifier.width(width).clickable { onClick(item) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.primaryImageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
            item.unplayedCount?.takeIf { it > 0 }?.let { count ->
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(
            item.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        item.year?.let {
            Text(
                it.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Landscape card with progress bar — Continue Watching / Next Up rows. */
@Composable
fun ResumeCard(
    item: MediaItem,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val width = if (compact) 150.dp else 200.dp
    Column(modifier = modifier.width(width).clickable { onClick(item) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.backdropImageUrl ?: item.primaryImageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
            item.progressFraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                )
            }
        }
        Text(
            item.displayTitle(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        item.subtitleLine()?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun MediaItem.displayTitle(): String =
    if (kind == MediaKind.EPISODE) seriesName ?: name else name

fun MediaItem.subtitleLine(): String? = when (kind) {
    MediaKind.EPISODE -> buildString {
        seasonNumber?.let { append("S$it") }
        episodeNumber?.let { if (isNotEmpty()) append(":") ; append("E$it") }
        if (isNotEmpty()) append(" · ")
        append(name)
    }.takeIf { it.isNotBlank() }
    else -> year?.toString()
}

fun MediaItem.runtimeLabel(): String? = runtimeMs?.let { ms ->
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Horizontal list item — used in "List" layout. */
@Composable
fun MediaListItem(
    item: MediaItem,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(item) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(80.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.backdropImageUrl ?: item.primaryImageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
            item.progressFraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                )
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                item.displayTitle(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitleLine()?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
